package com.app.market.data.repository

import com.app.market.data.remote.google.GooglePlayApi
import com.app.market.data.remote.google.GoogleAppRecord
import com.app.market.data.remote.google.GoogleDelivery
import com.app.market.domain.exception.MarketException
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.download.DownloadPart
import com.app.market.domain.model.market.AppComment
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.AppHistoricalVersion
import com.app.market.domain.model.market.AppScreenshot
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.ScreenshotOrientation
import com.app.market.domain.model.market.SearchPage
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.update.ManualUpdateRequest
import com.app.market.domain.model.update.ManualUpdateResult
import com.app.market.domain.model.update.ManualUpdateStatus
import com.app.market.domain.repository.GooglePlayRepository
import com.app.market.domain.repository.InstalledPackagesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

internal class GooglePlayRepositoryImpl(
    private val api: GooglePlayApi,
    private val installedPackages: InstalledPackagesRepository,
    private val prefs: com.app.market.domain.repository.UpdatePreferencesRepository,
) : GooglePlayRepository {
    /** 「Google Play 来源设置」：aurorastore = Aurora 网页通道（免鉴权、单请求）。 */
    private fun auroraMode(): Boolean = prefs.googlePlayEngine.value == "aurorastore"
    override fun devicePropertiesSnapshot(): Map<String, String> =
        com.app.market.data.remote.google.GoogleDeviceProperties

    override fun currentGsfId(): String? = api.currentGsfId()
    override fun setGsfId(id: String) = api.setGsfId(id)

    override suspend fun resetGoogleIdentity() {
        api.resetSession()
        api.warmUp()
    }

    init {
        // 冷启动后台预建 Google 鉴权会话：首次搜索/更新免等握手
        GlobalScope.launch { runCatching { api.warmUp() } }
    }

    override suspend fun search(keyword: String, page: Int): SearchPage = withContext(Dispatchers.Default) {
        val raw = if (auroraMode()) api.webApi.search(keyword, page) else api.search(keyword, page)
        if (raw.isEmpty()) return@withContext SearchPage(items = emptyList(), hasMore = false)
        // 搜索卡要显示版本/体积：两条网页通道都不带这两项，用 getItems 批量补
        //（slim 掩码只取版本/体积两个字段，实测单条 23KB→389B，不拖慢搜索）
        val items = runCatching {
            val byPkg = api.getItems(raw.map { it.packageName }, fillSizes = false, slim = true).associateBy { it.packageName }
            raw.map { item ->
                val extra = byPkg[item.packageName] ?: return@map item
                item.copy(
                    versionCode = item.versionCode.takeIf { it > 0 } ?: extra.versionCode,
                    versionString = item.versionString.ifBlank { extra.versionString },
                    fileSize = item.fileSize.takeIf { it > 0 } ?: extra.fileSize,
                )
            }
        }.getOrDefault(raw)
        if (auroraMode()) {
            return@withContext SearchPage(items = items.map(::toMarketAppInfo), hasMore = items.isNotEmpty())
        }
        SearchPage(items = items.map(::toMarketAppInfo), hasMore = false)
    }

    override suspend fun webpageCategories(category: com.app.market.domain.model.market.AppCategory): List<com.app.market.domain.model.market.CategoryOption> =
        api.webCategories(category == com.app.market.domain.model.market.AppCategory.GAMES)

    override suspend fun webpageCategoryApps(optionId: String, page: Int): SearchPage {
        val all = api.webCategory(optionId, forceRefresh = page == 0)
        return SearchPage(all.drop(page * 20).take(20).map(::toMarketAppInfo), (page + 1) * 20 < all.size)
    }

    override suspend fun rankedApps(
        page: Int,
        chart: com.app.market.domain.model.market.ChartKind,
        category: com.app.market.domain.model.market.AppCategory,
    ): SearchPage = withContext(Dispatchers.Default) {
        if (auroraMode()) {
            // Aurora 榜单：一次 RPC 返回整榜，按页切片
            val categoryValue = if (category == com.app.market.domain.model.market.AppCategory.GAMES) "GAME" else "APPLICATION"
            val records = api.webApi.chart(chartTypeOf(chart), categoryValue, forceRefresh = page == 0)
            val pageItems = records.drop(page * 20).take(20)
            return@withContext SearchPage(
                items = pageItems.map(::toMarketAppInfo),
                hasMore = (page + 1) * 20 < records.size,
            )
        }
        val options = api.webCategories(category == com.app.market.domain.model.market.AppCategory.GAMES)
        val selected = options.firstOrNull() ?: throw MarketException("Google Play 未返回分类")
        val items = api.webCategory(selected.id, forceRefresh = page == 0)
        SearchPage(items = items.drop(page * 20).take(20).map(::toMarketAppInfo), hasMore = (page + 1) * 20 < items.size)
    }

    private fun chartTypeOf(chart: com.app.market.domain.model.market.ChartKind): String = when (chart) {
        com.app.market.domain.model.market.ChartKind.FREE -> "topselling_free"
        com.app.market.domain.model.market.ChartKind.PAID -> "topselling_paid"
        com.app.market.domain.model.market.ChartKind.GROSSING -> "topgrossing"
        com.app.market.domain.model.market.ChartKind.MOVERS -> "movers_shakers"
    }

    override suspend fun appDetail(packageName: String): AppDetail = withContext(Dispatchers.Default) {
        if (auroraMode()) {
            // 网页元数据（单请求）与 getItems 并行：元数据出文案/截图，getItems 补版本号/体积（slim 掩码）
            val web = async { api.webApi.details(packageName) }
            val item = async {
                try { api.getItems(listOf(packageName), slim = true).firstOrNull() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            }
            val w = web.await()
            val i = item.await()
            val record = when {
                w != null && i != null -> w.copy(
                    versionCode = i.versionCode.takeIf { it > 0 } ?: w.versionCode,
                    fileSize = i.fileSize.takeIf { it > 0 } ?: w.fileSize,
                    versionString = w.versionString.ifBlank { i.versionString },
                    downloadCountText = w.downloadCountText.ifBlank { i.downloadCountText },
                    ratingText = w.ratingText.ifBlank { i.ratingText },
                    recentChanges = w.recentChanges.ifBlank { i.recentChanges },
                    description = w.description.ifBlank { i.description },
                    subtitle = w.subtitle.ifBlank { i.subtitle },
                )
                w != null -> w
                else -> throw MarketException("AuroraStore 未返回该应用详情")
            }
            return@withContext toAppDetail(record)
        }
        val record = retryGoogle("应用详情") { api.details(packageName) }
        toAppDetail(record)
    }

    private fun toAppDetail(record: GoogleAppRecord): AppDetail {
        val app = toMarketAppInfo(record)
        return AppDetail(
            app = app,
            brief = record.subtitle.take(120),
            introduction = record.description,
            changeLog = record.recentChanges,
            category = "",
            ageClassification = "",
            downloadCount = parseDownloadCount(record.downloadCountText),
            registrationNum = "",
            updateTime = 0L,
            privacyUrl = "",
            screenshots = record.screenshots.map { AppScreenshot(it, ScreenshotOrientation.PORTRAIT) },
            commentCount = 0L,
            comments = emptyList<AppComment>(),
            sameDeveloperApps = emptyList(),
        )
    }

    override suspend fun downloadMeta(app: MarketAppInfo): DownloadMeta = withContext(Dispatchers.Default) {
        buildMeta(app)
    }

    override suspend fun downloadUpdateMeta(app: MarketAppInfo): DownloadMeta = downloadMeta(app)

    /** 历史版本目录：两种来源方案共用 AuroraStore 同款（Exodus）一套代码，失败直接抛给界面报错。 */
    override suspend fun historicalVersions(packageName: String): List<AppHistoricalVersion> =
        withContext(Dispatchers.Default) {
            api.historicalVersions(packageName)
                .map {
                    AppHistoricalVersion(
                        versionCode = it.versionCode,
                        versionName = it.versionName,
                        date = it.date,
                    )
                }
        }

    /**
     * 手动检查更新：[ManualUpdateRequest.versionCode] 是用户当前安装的版本号，
     * 返回商店里比它新的版本；包存在但已最新返回 RECOGNIZED_NO_UPDATE。
     * 与其余来源语义一致，不做「下载指定历史版本」——那是详情页的入口。
     */
    override suspend fun checkManualUpdate(request: ManualUpdateRequest): ManualUpdateResult =
        withContext(Dispatchers.Default) {
            val record = retryGoogle("手动更新检查") {
                api.getItems(listOf(request.packageName), fillSizes = false, strict = auroraMode())
            }.firstOrNull { it.packageName == request.packageName }
                ?: return@withContext ManualUpdateResult(
                    ManualUpdateStatus.NOT_FOUND,
                    message = "商店中查不到该应用（包名有误或已下架）",
                )
            if (record.versionCode > request.versionCode) {
                ManualUpdateResult(ManualUpdateStatus.UPDATE_AVAILABLE, toMarketAppInfo(record))
            } else {
                ManualUpdateResult(ManualUpdateStatus.RECOGNIZED_NO_UPDATE)
            }
        }

    /** delivery 直链短时缓存：同一版本短时间内重复解析下载地址时直接复用。 */
    private val deliveryCache = mutableMapOf<String, Pair<TimeSource.Monotonic.ValueTimeMark, GoogleDelivery>>()
    private val deliveryCacheLock = Mutex()

    private suspend fun cachedDelivery(packageName: String, versionCode: Long): GoogleDelivery {
        val key = "$packageName#$versionCode"
        deliveryCacheLock.withLock {
            deliveryCache.entries.removeAll { (_, value) -> value.first.elapsedNow() >= 2.minutes }
            deliveryCache[key]?.let { return it.second }
        }
        val delivery = retryGoogle("下载地址获取") { api.delivery(packageName, versionCode) }
        deliveryCacheLock.withLock {
            deliveryCache[key] = TimeSource.Monotonic.markNow() to delivery
        }
        return delivery
    }

    /**
     * Google Play 接口偶发 401（token 过期）/限流：统一重试（最多 3 次，间隔递增）。
     * [GooglePlayApi] 内部对 401 已有一次强制换 token 的自愈。
     */
    private suspend fun <T> retryGoogle(operation: String, block: suspend () -> T): T {
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                com.app.market.domain.diagnostic.AppLog.log(
                    "GoogleOp[$operation] attempt=$attempt fail: ${e::class.simpleName}: ${e.message}",
                )
                if (attempt < 3) delay(1000L * attempt)
            }
        }
        throw MarketException("Google Play${operation}多次重试仍失败：${lastError?.message ?: "未知错误"}", lastError)
    }

    override suspend fun checkUpdates(): List<MarketAppInfo> = withContext(Dispatchers.Default) {
        val installed = installedPackages.installed()
            .associate { it.packageName.lowercase() to it }
        if (installed.isEmpty()) return@withContext emptyList()
        // 已装应用动辄几百个，按 40 个一批分块请求。
        // 关键：这些批次必须并发 —— 串行时 8 批 × (请求 + 最多 2 次重试)
        // 叠加起来轻易超过更新检查的总超时，表现就是「Google 总是检查超时」。
        val fetched = coroutineScope {
            installed.keys
                .chunked(40)
                .map { chunk ->
                    async {
                        retryGoogle("更新检查") { api.getItems(chunk, fillSizes = false, strict = auroraMode()) }
                    }
                }
                .map { it.await() }
                .flatten()
        }
        // 同包名可能出现在多个批次里，按最大 versionCode 去重
        val byPackage = LinkedHashMap<String, GoogleAppRecord>()
        fetched.forEach { record ->
            val key = record.packageName.lowercase()
            val old = byPackage[key]
            if (old == null || record.versionCode > old.versionCode) byPackage[key] = record
        }
        // Google 自家应用（Google / Chrome / Play 服务这些）在 Play 上是按 ABI、按账号分批发布的：
        // 匿名批量接口偶尔只给出较低分支的 versionCode，而本机装的是更高分支，
        // 于是一比就被当成「已是最新版」。这几条改用登录通道的 details 再核一次，取更大的一边。
        val suspects = byPackage.values.filter { record ->
            val local = installed[record.packageName.lowercase()] ?: return@filter false
            needsVersionRecheck(record.packageName) && record.versionCode <= local.versionCode
        }
        if (suspects.isNotEmpty() && !auroraMode()) {
            // 条数少（十几条），限制并发避免把详情通道打到限流
            suspects.chunked(3).forEach { chunk ->
                val resolved = coroutineScope {
                    chunk.map { record ->
                        async {
                            runCatching { api.details(record.packageName) }.getOrNull()
                        }
                    }.awaitAll()
                }
                resolved.filterNotNull().forEach { detail ->
                    val key = detail.packageName.lowercase()
                    val old = byPackage[key]
                    if (old == null || detail.versionCode > old.versionCode) byPackage[key] = detail
                }
            }
        }
        val records = byPackage.values.toList()
        records
            .filter { record ->
                val local = installed[record.packageName.lowercase()] ?: return@filter false
                record.versionCode > local.versionCode
            }
            .map { record ->
                val local = installed[record.packageName.lowercase()]
                toMarketAppInfo(record).copy(
                    // 本机已装信息必须带上：更新页假更新剔除与「版本: x → y」显示都依赖它
                    installedVersionName = local?.versionName.orEmpty(),
                    installedVersionCode = local?.versionCode ?: 0L,
                    installedBaseApkPath = local?.baseApkPath.orEmpty(),
                    installedSplits = local?.splits ?: "0",
                    installedOldApkHash = local?.oldApkHash ?: "0",
                    isSystemApp = local?.isSystemApp ?: false,
                )
            }
            .sortedBy { it.displayName }
            // 体积只给真正有更新的几条补：delivery 一次请求，条数少不会拖慢检查
            .map { app ->
                if (app.apkSize > 0L) {
                    app
                } else {
                    val size = runCatching { api.delivery(app.packageName, app.versionCode).size }
                        .getOrDefault(0L)
                    if (size > 0L) app.copy(apkSize = size) else app
                }
            }
    }

    /**
     * 需要复核版本号的应用：这类在 Play 上按 ABI / 地区 / 账号多分支发布，
     * 匿名批量接口给出的 versionCode 可能低于本机所装分支，会被误判成「已是最新版」。
     */
    private fun needsVersionRecheck(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg.startsWith("com.google.") ||
            pkg.startsWith("com.android.chrome") ||
            pkg.startsWith("com.android.vending") ||
            pkg.startsWith("com.android.gms") ||
            pkg.startsWith("com.twitter.android") ||
            pkg.startsWith("com.facebook.") ||
            pkg.startsWith("com.whatsapp") ||
            pkg.startsWith("com.instagram.android")
    }

    private suspend fun buildMeta(app: MarketAppInfo): DownloadMeta {
        val local = if (app.versionCode > 0L) app else {
            val item = retryGoogle("版本获取") { api.getItems(listOf(app.packageName), fillSizes = false) }
                .firstOrNull { it.packageName.equals(app.packageName, ignoreCase = true) && it.versionCode > 0L }
                ?: throw MarketException("Google Play 未提供 ${app.packageName} 的可下载版本")
            app.copy(versionCode = item.versionCode, versionName = item.versionString)
        }
        val delivery = cachedDelivery(local.packageName, local.versionCode)
        // Google Play 按 ABI/DPI 分发 split APK：base + config.*，漏装任何一个都会
        // INSTALL_FAILED_MISSING_SPLIT。delivery 里的 splits 必须和 base 一起进 parts，
        // 安装侧会把全部 part 写进同一个 PackageInstaller session。
        val parts = buildList {
            add(DownloadPart("base", "base", delivery.url, delivery.size))
            delivery.splits.forEach { split ->
                add(DownloadPart(split.name, "split", split.url, split.size))
            }
        }
        return DownloadMeta(
            appId = local.appId,
            packageName = local.packageName,
            displayName = local.displayName,
            versionName = local.versionName,
            versionCode = local.versionCode,
            url = delivery.url,
            size = if (delivery.size > 0L) delivery.size else local.apkSize,
            parts = parts,
            installedBaseApkPath = local.installedBaseApkPath,
            icon = local.icon,
            changeLog = local.changeLog,
            // 不写 source 会落到默认值 XIAOMI，安装时 installer 会被写成小米商店
            source = AppSource.GOOGLE,
        )
    }
}

/** 「5亿+」/「500M+」/「500,000+」→ 数值。Aurora 网页通道给带单位的显示串，只抽数字会把 5亿 解析成 5。 */
private fun parseDownloadCount(text: String): Long {
    val cleaned = text.replace(",", "").replace(" ", "")
    val match = Regex("([0-9]+(?:\\.[0-9]+)?)([亿万千KkMmBb]?)").find(cleaned) ?: return 0L
    val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
    val multiplier = when (match.groupValues[2]) {
        "亿" -> 1e8
        "万" -> 1e4
        "千" -> 1e3
        "K", "k" -> 1e3
        "M", "m" -> 1e6
        "B", "b" -> 1e9
        else -> 1.0
    }
    return (value * multiplier).toLong()
}

private fun toMarketAppInfo(record: GoogleAppRecord): MarketAppInfo = MarketAppInfo(
    appId = 0L,
    packageName = record.packageName,
    displayName = record.title,
    publisherName = record.publisher,
    versionName = record.versionString,
    versionCode = record.versionCode,
    icon = record.icon,
    apkSize = record.fileSize,
    ratingScore = record.ratingText.toDoubleOrNull() ?: 0.0,
    changeLog = record.recentChanges,
    summaryText = record.description.take(400),
    source = AppSource.GOOGLE,
    category = "",
    downloadCount = parseDownloadCount(record.downloadCountText),
)