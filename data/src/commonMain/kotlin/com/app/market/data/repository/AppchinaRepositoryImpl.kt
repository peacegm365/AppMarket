package com.app.market.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore
import com.app.market.data.remote.appchina.AppchinaApi
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.market.AppComments
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.HistoricalVersion
import com.app.market.domain.model.market.HistoricalVersionPage
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.SearchPage
import com.app.market.domain.model.update.ManualUpdateRequest
import com.app.market.domain.model.update.ManualUpdateResult
import com.app.market.domain.model.update.ManualUpdateStatus
import com.app.market.domain.repository.AppchinaRepository
import com.app.market.domain.repository.InstalledPackagesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal class AppchinaRepositoryImpl(
    private val api: AppchinaApi,
    private val installedPackages: InstalledPackagesRepository,
) : AppchinaRepository {
    override suspend fun search(keyword: String, page: Int): SearchPage = withContext(Dispatchers.Default) {
        api.search(keyword, page)
    }

    override suspend fun appDetail(appId: Long, packageName: String): AppDetail = withContext(Dispatchers.Default) {
        api.appDetail(appId, packageName)
    }

    override suspend fun appComments(app: MarketAppInfo): AppComments = withContext(Dispatchers.Default) {
        api.appComments(app)
    }

    override suspend fun downloadMeta(app: MarketAppInfo): DownloadMeta = withContext(Dispatchers.Default) {
        api.downloadMeta(app)
    }

    override suspend fun historicalVersions(appId: Long, packageName: String, offset: Int): HistoricalVersionPage =
        withContext(Dispatchers.Default) {
            api.historicalVersions(appId, packageName, offset)
        }

    override fun historicalDownloadMeta(version: HistoricalVersion): DownloadMeta =
        api.historicalDownloadMeta(version)

    override suspend fun checkUpdates(): List<MarketAppInfo> = withContext(Dispatchers.Default) {
        val installed = installedPackages.installed()
            .filter { it.packageName.isNotBlank() && it.versionCode > 0L }
        if (installed.isEmpty()) return@withContext emptyList()
        // 应用汇没有批量更新接口，只能按包名逐个请求。已装应用常有三四百个，
        // 按批（chunked）跑等于把总耗时摊成 N/并发 倍，必然顶超时 —— 表现就是「永远超时、一个都不出」。
        // 改成：全部并发（信号量限流 24）+ 8 秒软预算。超预算的条目按「无更新」跳过，
        // 保住已查到的结果，而不是整轮失败。
        val deadline = kotlin.time.Clock.System.now().toEpochMilliseconds() + CheckBudgetMillis
        val gate = Semaphore(CheckConcurrency)
        val results: List<Result<MarketAppInfo?>> = coroutineScope {
            installed.map { local ->
                async {
                    if (kotlin.time.Clock.System.now().toEpochMilliseconds() > deadline) {
                        return@async Result.success(null)
                    }
                    gate.withPermit {
                        try {
                            if (kotlin.time.Clock.System.now().toEpochMilliseconds() > deadline) {
                                return@withPermit Result.success(null)
                            }
                            val found = latestFor(local.packageName)
                            if (found == null || found.versionCode <= local.versionCode) {
                                Result.success(null)
                            } else {
                                val latest = enrich(found)
                                Result.success(
                                    latest.takeIf { it.versionCode > local.versionCode }?.copy(
                                        installedVersionCode = local.versionCode,
                                        installedVersionName = local.versionName,
                                        installedSplits = local.splits,
                                        isSystemApp = local.isSystemApp,
                                    ),
                                )
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            Result.failure(error)
                        }
                    }
                }
            }.map { it.await() }
        }
        if (results.isNotEmpty() && results.none { it.isSuccess }) results.first().getOrThrow()
        results.mapNotNull { it.getOrNull() }
    }

    override suspend fun checkManualUpdate(request: ManualUpdateRequest): ManualUpdateResult =
        withContext(Dispatchers.Default) {
            val found = latestFor(request.packageName)
                ?: return@withContext ManualUpdateResult(ManualUpdateStatus.NOT_FOUND)
            val latest = enrich(found)
            if (latest.versionCode <= request.versionCode) {
                return@withContext ManualUpdateResult(ManualUpdateStatus.RECOGNIZED_NO_UPDATE)
            }
            ManualUpdateResult(
                ManualUpdateStatus.UPDATE_AVAILABLE,
                latest.copy(
                    installedVersionCode = request.versionCode,
                    installedVersionName = request.versionName,
                    installedSplits = request.splits,
                    isSystemApp = request.isSystemApp,
                ),
            )
        }

    private suspend fun latestFor(packageName: String): MarketAppInfo? =
        api.search(packageName, 0).items.firstOrNull {
            it.packageName.equals(packageName, ignoreCase = true) && it.versionCode > 0L
        }

    /** 搜索结果没有版本名与更新日志，命中更新后再读取一次详情；失败时退回搜索结果。 */
    private suspend fun enrich(app: MarketAppInfo): MarketAppInfo =
        try {
            api.appDetail(app.appId, app.packageName).app
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            app
        }

    private suspend fun <T, R> parallel(items: List<T>, block: suspend (T) -> R): List<Result<R>> = coroutineScope {
        items.chunked(8).flatMap { batch ->
            batch.map { item ->
                async {
                    try {
                        Result.success(block(item))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                }
            }.map { it.await() }
        }
    }
}

/** 一轮更新检查的软预算：超出后剩余条目按「无更新」跳过，避免整轮超时。 */
private const val CheckBudgetMillis = 8_000L

/** 并发请求数：应用汇是单包名查询，靠并发把总量压进预算。 */
private const val CheckConcurrency = 24
