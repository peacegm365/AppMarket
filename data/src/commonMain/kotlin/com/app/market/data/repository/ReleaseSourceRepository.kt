package com.app.market.data.repository

import com.app.market.data.local.PreferencesDataSource
import com.app.market.data.local.StringPreferenceKey
import com.app.market.data.remote.fdroid.fdroidStableId
import com.app.market.data.remote.releases.ReleaseApi
import com.app.market.data.remote.releases.ReleaseApkInspector
import com.app.market.data.remote.releases.ReleaseAsset
import com.app.market.data.remote.releases.ReleaseRepositoryRef
import com.app.market.data.remote.releases.ReleaseSearchHit
import com.app.market.data.remote.releases.parseReleaseRepository
import com.app.market.domain.exception.MarketException
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.download.DownloadPart
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.ModuleAsset
import com.app.market.domain.model.market.ModuleRelease
import com.app.market.domain.model.market.SearchPage
import com.app.market.domain.model.update.ManualUpdateRequest
import com.app.market.domain.model.update.ManualUpdateResult
import com.app.market.domain.model.update.ManualUpdateStatus
import com.app.market.domain.repository.InstalledPackagesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Public release sources; package/repository associations survive process death. */
internal class ReleaseSourceRepository(
    private val api: ReleaseApi,
    private val inspector: ReleaseApkInspector,
    private val preferences: PreferencesDataSource,
    private val installed: InstalledPackagesRepository,
    private val cpuArchitecture: String = "arm64-v8a,armeabi-v7a",
) {
    private data class Resolved(val detail: AppDetail, val asset: ReleaseAsset, val fetchedAt: Long)
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Resolved>()
    private val refsById = mutableMapOf<Pair<AppSource, Long>, ReleaseRepositoryRef>()
    // Komi 已经返回仓库摘要，点击下载时复用，避免再串行请求一次 GitHub 仓库信息。
    private val rankingHits = mutableMapOf<String, ReleaseSearchHit>()
    private val resolveLocks = mutableMapOf<String, Mutex>()

    suspend fun githubRanking(option: String, page: Int): SearchPage {
        val (hits, hasMore) = kotlinx.coroutines.withTimeoutOrNull(30_000) { api.komiPage(option, page) }
            ?: throw MarketException("Komi 列表加载超时，请检查网络后重试")
        mutex.withLock { hits.forEach { rankingHits[it.ref.link] = it } }
        val items = hits.map { hit ->
            MarketAppInfo(
                appId = fdroidStableId(hit.ref.link),
                packageName = "github-repo:${hit.ref.path}",
                displayName = hit.ref.path.substringAfterLast('/'),
                publisherName = hit.ref.path.substringBeforeLast('/'),
                versionName = "", versionCode = 0L, icon = hit.icon, apkSize = 0L,
                ratingScore = 0.0, openLink = hit.ref.link, source = AppSource.GITHUB,
                summaryText = hit.description, downloadCount = hit.stars,
                releaseTimeText = hit.updatedAt.take(10),
            )
        }
        return SearchPage(items, hasMore)
    }

    suspend fun search(source: AppSource, keyword: String, page: Int): SearchPage {
        if (keyword.isBlank()) return SearchPage(emptyList(), false)
        val direct = parseReleaseRepository(source, keyword)
        if (keyword.trim().contains("://") && direct == null) throw MarketException("请填写该来源的公开仓库链接")
        val (refs, hasMore) = if (direct != null) {
            if (page > 0) return SearchPage(emptyList(), false)
            listOf(ReleaseSearchHit(direct)) to false
        } else api.search(source, keyword, page)
        val results = parallel(refs) { hit -> resolve(hit.ref, hit = hit) }
        val resolved = results.mapNotNull { it.getOrNull() }
        if (resolved.isEmpty()) results.firstOrNull { it.isFailure }?.getOrThrow()
        if (direct != null && resolved.isEmpty()) {
            if (!api.repoExists(direct)) throw MarketException("仓库 ${direct.path} 不存在，请检查 owner/仓库名拼写")
            throw MarketException("该仓库没有可用的正式版 APK Release")
        }
        if (refs.isNotEmpty() && resolved.isEmpty()) {
            throw MarketException("搜到的 ${refs.size} 个仓库都没有正式版 APK Release，换个关键词试试")
        }
        return SearchPage(resolved.map { it.detail.app }, hasMore)
    }

    /**
     * 增量搜索：先拿仓库列表（一次请求），再逐个解析 APK 信息，**每解析出一个就产出一次**，
     * 不用等所有仓库都解析完才上屏（GitHub 上每个仓库都要读一次 releases，慢的会拖很久）。
     */
    fun searchIncremental(source: AppSource, keyword: String, page: Int): Flow<SearchPage> = channelFlow {
        if (keyword.isBlank()) {
            send(SearchPage(emptyList(), false))
            return@channelFlow
        }
        val (hits, hasMore) = api.search(source, keyword, page)
        if (hits.isEmpty()) {
            send(SearchPage(emptyList(), false))
            return@channelFlow
        }
        val accumulated = mutableListOf<MarketAppInfo>()
        val lock = Mutex()
        hits.chunked(4).forEach { batch ->
            coroutineScope {
                val jobs = batch.map { hit ->
                    async {
                        runCatching { resolve(hit.ref, hit = hit) }.getOrNull()?.detail?.app
                    }
                }
                jobs.forEach { job ->
                    val app = job.await() ?: return@forEach
                    val snapshot = lock.withLock {
                        accumulated.add(app)
                        accumulated.toList()
                    }
                    send(SearchPage(snapshot, hasMore))
                }
            }
        }
        if (lock.withLock { accumulated.isEmpty() }) send(SearchPage(emptyList(), false))
    }

    suspend fun appDetail(source: AppSource, appId: Long, packageName: String): AppDetail {
        val repositoryPath = packageName.removePrefix("github-repo:").takeIf { packageName.startsWith("github-repo:") }
        val ref = repositoryPath?.let { parseReleaseRepository(source, it) }
            ?: mutex.withLock { refsById[source to appId] } ?: storedRef(source, packageName)
            ?: throw MarketException("请先搜索应用或仓库链接以建立来源关联")
        val detail = resolve(ref, expectedPackage = if (repositoryPath != null) null else packageName)?.detail
            ?: throw MarketException("该仓库没有对应应用的 APK")
        preferences.put(associationKey(source, detail.app.packageName), ref.link)
        // 详情页按仓库展示：项目简介 + README 原文（渲染后的 HTML）+ 仓库主页
        val readme = runCatching { api.readmeHtml(ref) }.getOrDefault("")
        return detail.copy(introductionHtml = readme, homepageUrl = ref.link)
    }

    suspend fun currentAsset(source: AppSource, app: MarketAppInfo): List<ModuleAsset> {
        val ref = parseReleaseRepository(source, app.openLink) ?: storedRef(source, app.packageName)
            ?: return emptyList()
        val repositoryPath = app.packageName.startsWith("github-repo:")
        val asset = resolve(ref, expectedPackage = if (repositoryPath) null else app.packageName)?.asset ?: return emptyList()
        return listOf(ModuleAsset(asset.name, asset.url, asset.size))
    }

    suspend fun downloadMeta(source: AppSource, app: MarketAppInfo): DownloadMeta {
        val ref = parseReleaseRepository(source, app.openLink) ?: storedRef(source, app.packageName)
            ?: throw MarketException("应用仓库关联已失效，请重新搜索")
        val repositoryPlaceholder = app.packageName.startsWith("github-repo:")
        val resolved = resolve(ref, expectedPackage = if (repositoryPlaceholder) null else app.packageName)
            ?: throw MarketException("该仓库没有对应应用的 APK")
        if (!repositoryPlaceholder && resolved.detail.app.versionCode != app.versionCode) throw MarketException("Release 版本已变化，请刷新应用详情")
        val resolvedApp = resolved.detail.app
        preferences.put(associationKey(source, resolvedApp.packageName), ref.link)
        val asset = resolved.asset
        return DownloadMeta(
            appId = resolvedApp.appId, packageName = resolvedApp.packageName, displayName = resolvedApp.displayName,
            versionName = resolvedApp.versionName, versionCode = resolvedApp.versionCode,
            url = asset.url, size = resolved.detail.app.apkSize,
            parts = listOf(DownloadPart(asset.name, "base", asset.url, resolved.detail.app.apkSize, asset.sha256)),
            icon = app.icon, changeLog = resolved.detail.changeLog, source = source,
        )
    }

    suspend fun checkUpdates(source: AppSource): List<MarketAppInfo> {
        val packages = installed.installed().filter { it.packageName.isNotBlank() && it.versionCode > 0L }
        val tracked = packages.mapNotNull { local -> storedRef(source, local.packageName)?.let { local to it } }
        val results = parallel(tracked) { (local, ref) ->
            val app = resolve(ref, local.packageName, refresh = true)?.detail?.app ?: return@parallel null
            app.takeIf { it.versionCode > local.versionCode }?.copy(
                installedVersionCode = local.versionCode, installedVersionName = local.versionName,
                installedSplits = local.splits, isSystemApp = local.isSystemApp,
            )
        }
        if (results.isNotEmpty() && results.none { it.isSuccess }) results.first().getOrThrow()
        return results.mapNotNull { it.getOrNull() }
    }

    suspend fun checkManualUpdate(source: AppSource, request: ManualUpdateRequest): ManualUpdateResult {
        val ref = storedRef(source, request.packageName) ?: return ManualUpdateResult(ManualUpdateStatus.NOT_FOUND)
        val app = resolve(ref, request.packageName, refresh = true)?.detail?.app
            ?: return ManualUpdateResult(ManualUpdateStatus.NOT_FOUND)
        if (app.versionCode <= request.versionCode) return ManualUpdateResult(ManualUpdateStatus.RECOGNIZED_NO_UPDATE)
        return ManualUpdateResult(ManualUpdateStatus.UPDATE_AVAILABLE, app.copy(
            installedVersionCode = request.versionCode, installedVersionName = request.versionName, installedSplits = request.splits,
        ))
    }

    private suspend fun resolve(
        ref: ReleaseRepositoryRef,
        expectedPackage: String? = null,
        refresh: Boolean = false,
        hit: ReleaseSearchHit? = null,
    ): Resolved? {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        // 开关状态参与缓存键：切「包含预览版更新」后不用等 5 分钟缓存过期
        val includePrereleases = runCatching {
            preferences.read(com.app.market.data.local.preferences.UpdatePreferenceKeys.IncludePrereleaseUpdates)
        }.getOrDefault(false)
        val cacheKey = ref.link + "#" + includePrereleases
        val resolveLock = mutex.withLock { resolveLocks.getOrPut(cacheKey) { Mutex() } }
        return resolveLock.withLock {
        val cached = mutex.withLock { cache[cacheKey] }
        if (!refresh && cached != null && now - cached.fetchedAt < 5 * 60_000 &&
            (expectedPackage == null || expectedPackage == cached.detail.app.packageName)) return cached
        val summary = hit ?: mutex.withLock { rankingHits[ref.link] }
        val release = api.latest(ref, includePrereleases, summary) ?: return null
        var firstFailure: Exception? = null
        for (asset in preferredReleaseAssets(release.assets, cpuArchitecture)) {
            val identity = try { inspector.inspect(asset.url, asset.size) }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { if (firstFailure == null) firstFailure = error; continue }
            if (expectedPackage != null && identity.packageName != expectedPackage) continue
            val app = MarketAppInfo(
                appId = fdroidStableId(ref.link + "#" + identity.packageName), packageName = identity.packageName,
                displayName = ref.path.substringAfterLast('/'), publisherName = ref.path.substringBeforeLast('/'),
                versionName = identity.versionName, versionCode = identity.versionCode,
                icon = release.icon, apkSize = identity.size.takeIf { it > 0 } ?: asset.size,
                ratingScore = 0.0, changeLog = release.notes, openLink = ref.link, source = ref.source,
                // 列表按 GitHub 仓库卡展示：简介 + 星标数 + 最后推送日期
                summaryText = release.description,
                downloadCount = release.stars,
                releaseTimeText = release.updatedAt.take(10),
            )
            val detail = AppDetail(
                app = app, brief = release.description, introduction = release.description,
                changeLog = release.notes, category = "", ageClassification = "", downloadCount = 0L,
                registrationNum = "", privacyUrl = "", screenshots = emptyList(), comments = emptyList(), sameDeveloperApps = emptyList(),
            )
            val resolved = Resolved(detail, asset, now)
            mutex.withLock {
                cache[cacheKey] = resolved
                refsById[ref.source to app.appId] = ref
            }
            return resolved
        }
        firstFailure?.let { throw MarketException("无法读取 Release APK 信息：${it.message}") }
        null
        }
    }

    /** 仓库的全部发布（详情页「版本」列表）。 */
    suspend fun releases(source: AppSource, app: MarketAppInfo): List<ModuleRelease> {
        val ref = parseReleaseRepository(source, app.openLink) ?: storedRef(source, app.packageName)
            ?: return emptyList()
        return api.releases(ref).map { release ->
            ModuleRelease(
                name = release.tagName.ifBlank { release.ref.path.substringAfterLast('/') },
                tagName = release.tagName,
                versionCode = 0L,
                publishedAt = runCatching {
                    kotlin.time.Instant.parse(release.updatedAt).toEpochMilliseconds()
                }.getOrDefault(0L),
                changeLog = release.notes,
                assets = release.assets.map { ModuleAsset(it.name, it.url, it.size) },
            )
        }
    }

    private suspend fun storedRef(source: AppSource, packageName: String): ReleaseRepositoryRef? =
        preferences.read(associationKey(source, packageName))?.let { parseReleaseRepository(source, it) }

    private fun associationKey(source: AppSource, packageName: String) =
        StringPreferenceKey("release_sources", "${source.token}.$packageName")

    private suspend fun <T, R> parallel(items: List<T>, block: suspend (T) -> R): List<Result<R>> = coroutineScope {
        items.chunked(4).flatMap { batch ->
            batch.map { item -> async {
                try { Result.success(block(item)) }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { Result.failure(error) }
            } }.map { it.await() }
        }
    }
}

internal fun preferredReleaseAssets(assets: List<ReleaseAsset>, cpuArchitecture: String = "arm64-v8a,armeabi-v7a"): List<ReleaseAsset> = assets
    .filterNot { Regex("(?i)(debug|unsigned|unaligned)").containsMatchIn(it.name) }
    .filter { asset ->
        val name = asset.name.lowercase()
        if (name.contains("universal")) true
        else when {
            name.contains("arm64") || name.contains("aarch64") -> cpuArchitecture.contains("arm64") || cpuArchitecture.contains("aarch64")
            name.contains("armeabi") || name.contains("armv7") -> cpuArchitecture.contains("armeabi") || cpuArchitecture.contains("armv7")
            name.contains("x86_64") -> cpuArchitecture.contains("x86_64") || cpuArchitecture.contains("amd64")
            name.contains("x86") -> cpuArchitecture.contains("x86") || cpuArchitecture.contains("amd64")
            else -> true
        }
    }
    .sortedBy { asset ->
        val name = asset.name.lowercase()
        val architectureScore = when {
            name.contains("universal") -> 0
            name.contains("arm64") || name.contains("aarch64") -> 2
            name.contains("armeabi") || name.contains("armv7") -> 3
            name.contains("x86") -> 4
            else -> 1
        }
        architectureScore + if (name.contains("fdroid") || name.contains("f-droid")) 10 else 0
    }