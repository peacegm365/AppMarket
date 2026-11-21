package com.app.market.data.repository

import com.app.market.data.remote.aptoide.AptoideApi
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.SearchPage
import com.app.market.domain.model.update.ManualUpdateRequest
import com.app.market.domain.model.update.ManualUpdateResult
import com.app.market.domain.model.update.ManualUpdateStatus
import com.app.market.domain.repository.AptoideRepository
import com.app.market.domain.repository.InstalledPackagesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal class AptoideRepositoryImpl(
    private val api: AptoideApi,
    private val installedPackages: InstalledPackagesRepository,
) : AptoideRepository {
    override suspend fun search(keyword: String, page: Int): SearchPage = withContext(Dispatchers.Default) {
        api.search(keyword, page)
    }

    override suspend fun appDetail(packageName: String): AppDetail = withContext(Dispatchers.Default) {
        api.appDetail(0L, packageName)
    }

    override suspend fun downloadMeta(app: MarketAppInfo): DownloadMeta = withContext(Dispatchers.Default) {
        api.downloadMeta(app)
    }

    override suspend fun checkUpdates(): List<MarketAppInfo> = withContext(Dispatchers.Default) {
        val installed = installedPackages.installed()
            .filter { it.packageName.isNotBlank() && it.versionCode > 0L }
        if (installed.isEmpty()) return@withContext emptyList()
        // Aptoide 没有批量版本接口，只能逐包 getApp。已装应用常有三四百个，
        // 按批串行必然顶超时，因此全部并发 + 信号量限流 + 软预算，超预算的条目按「无更新」跳过。
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
                            val latest = api.latest(0L, local.packageName)
                            if (latest == null || latest.versionCode <= local.versionCode) {
                                Result.success(null)
                            } else {
                                Result.success(
                                    latest.copy(
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
            val latest = api.latest(0L, request.packageName)
                ?: return@withContext ManualUpdateResult(ManualUpdateStatus.NOT_FOUND)
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
}

/** 一轮更新检查的软预算：超出后剩余条目按「无更新」跳过，避免整轮超时。 */
private const val CheckBudgetMillis = 10_000L

/** 并发请求数：Aptoide 是单包名查询，靠并发把总量压进预算。 */
private const val CheckConcurrency = 16