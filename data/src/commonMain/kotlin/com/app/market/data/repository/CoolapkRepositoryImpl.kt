package com.app.market.data.repository

import com.app.market.data.remote.coolapk.CoolapkApi
import com.app.market.data.remote.coolapk.CoolapkAppRecord
import com.app.market.data.remote.coolapk.toMarketAppInfo
import com.app.market.domain.exception.MarketException
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.AppComment
import com.app.market.domain.model.market.AppScreenshot
import com.app.market.domain.model.market.ScreenshotOrientation
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.SearchPage
import com.app.market.domain.repository.CoolapkRepository
import com.app.market.domain.repository.InstalledPackagesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class CoolapkRepositoryImpl(
    private val api: CoolapkApi,
    private val installedPackages: InstalledPackagesRepository,
) : CoolapkRepository {

    override suspend fun search(keyword: String, page: Int): SearchPage = withContext(Dispatchers.Default) {
        val items = api.search(keyword, page + 1)
        SearchPage(items = items.map(CoolapkAppRecord::toMarketAppInfo), hasMore = items.isNotEmpty())
    }

    override suspend fun appDetail(packageName: String): AppDetail = withContext(Dispatchers.Default) {
        val record = retryMarket("应用详情") { api.detail(packageName) }
        val app = record.toMarketAppInfo()
        AppDetail(
            app = app,
            brief = record.subtitle.ifBlank { record.description }.take(120),
            introduction = record.fullDescription.ifBlank { record.description },
            changeLog = record.changeLog.ifBlank { record.changeHistory },
            category = record.category,
            ageClassification = record.romVersion,
            downloadCount = record.downloadCount,
            registrationNum = record.beianNo,
            updateTime = 0L,
            privacyUrl = record.privacyUrl,
            screenshots = record.screenshots.map { AppScreenshot(it, ScreenshotOrientation.PORTRAIT) },
            commentCount = record.commentCount,
            comments = runCatching {
                api.comments(record.appId).map {
                    AppComment(userName = it.userName, content = it.content, score = it.score)
                }
            }.getOrDefault(emptyList()),
            sameDeveloperApps = emptyList(),
        )
    }

    override suspend fun downloadMeta(app: MarketAppInfo): DownloadMeta = withContext(Dispatchers.Default) {
        val record = retryMarket("应用详情") { api.detail(app.packageName) }
        buildMeta(record, app)
    }

    /**
     * 酷安接口偶发限流/风控/网络抖动：统一重试（最多 3 次，间隔逐次递增）。
     * MarketException 之外的网络 IO 异常同样重试——此前只抓 MarketException，
     * Ktor 连接超时等一律直接失败，正是「点更新报错、再点又成功」的来源。
     */
    private suspend fun <T> retryMarket(operation: String, block: suspend () -> T): T {
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < 3) delay(1000L * attempt)
            }
        }
        throw MarketException("酷安${operation}多次重试仍失败：${lastError?.message ?: "未知错误"}", lastError)
    }

    override suspend fun downloadUpdateMeta(app: MarketAppInfo): DownloadMeta = downloadMeta(app)

    override suspend fun checkUpdates(): List<MarketAppInfo> = withContext(Dispatchers.Default) {
        val installed = installedPackages.installed()
            .associate { it.packageName.lowercase() to it }
        if (installed.isEmpty()) return@withContext emptyList()
        retryMarket("更新检查") {
            api.checkUpdates(installed.mapValues { (_, pkg) -> pkg.versionCode to pkg.oldApkHash })
        }.values
            .filter { record ->
                val local = installed[record.packageName.lowercase()] ?: return@filter false
                record.versionCode > local.versionCode
            }
            .map { record ->
                val local = installed[record.packageName.lowercase()]
                record.toMarketAppInfo().copy(
                    // 必须带上本机已装信息：缺了会显示「版本: - → x」，
                    // 且更新页的假更新剔除（版本名/版本号比对）会全部失效
                    installedVersionName = local?.versionName.orEmpty(),
                    installedVersionCode = local?.versionCode ?: 0L,
                    installedBaseApkPath = local?.baseApkPath.orEmpty(),
                    installedSplits = local?.splits ?: "0",
                    installedOldApkHash = local?.oldApkHash ?: "0",
                    isSystemApp = local?.isSystemApp ?: false,
                )
            }
            .sortedBy { it.displayName }
    }

    private suspend fun buildMeta(record: CoolapkAppRecord, local: MarketAppInfo): DownloadMeta {
        val url = retryMarket("下载地址获取") { api.downloadUrl(record) }
        if (url.isBlank()) throw MarketException("酷安未提供 ${record.displayName} 的下载地址")
        return DownloadMeta(
            appId = record.appId,
            packageName = record.packageName,
            displayName = record.displayName,
            versionName = record.versionName,
            versionCode = record.versionCode,
            url = url,
            size = record.apkSize,
            installedBaseApkPath = local.installedBaseApkPath,
            icon = record.icon,
            changeLog = record.changeLog,
        )
    }
}