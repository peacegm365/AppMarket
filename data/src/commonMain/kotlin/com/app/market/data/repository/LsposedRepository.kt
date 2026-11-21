package com.app.market.data.repository

import com.app.market.data.remote.fdroid.fdroidStableId
import com.app.market.data.remote.lsposed.LsposedApi
import com.app.market.data.remote.lsposed.LsposedAsset
import com.app.market.data.remote.lsposed.LsposedModule
import com.app.market.data.remote.lsposed.LsposedRelease
import com.app.market.data.remote.lsposed.LsposedSearchEntry
import com.app.market.data.remote.lsposed.latestStableRelease
import com.app.market.data.remote.releases.ReleaseApkInspector
import com.app.market.data.remote.releases.ReleaseAsset
import com.app.market.domain.exception.MarketException
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.download.DownloadPart
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.AppScreenshot
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.ModuleAsset
import com.app.market.domain.model.market.ScreenshotOrientation
import com.app.market.domain.model.market.SearchPage
import com.app.market.domain.model.update.ManualUpdateRequest
import com.app.market.domain.model.update.ManualUpdateResult
import com.app.market.domain.model.update.ManualUpdateStatus
import com.app.market.domain.repository.InstalledPackagesRepository
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Xposed Module Repository source.
 *
 * Search uses the site's compact index; search results are intentionally lightweight. Opening a
 * module resolves its latest stable release and reads the APK manifest through
 * [ReleaseApkInspector], so update checks compare real versionCode values rather than tag strings.
 */
class LsposedRepository internal constructor(
    private val api: LsposedApi,
    private val inspector: ReleaseApkInspector,
    private val installed: InstalledPackagesRepository,
    private val cpuArchitecture: String = "arm64-v8a,armeabi-v7a",
) {
    private data class Resolved(val detail: AppDetail, val asset: LsposedAsset, val fetchedAt: Long)

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Resolved>()

    /** README 中相对链接指向的文档（README_EN.md 等）的原始文本。 */
    suspend fun fileText(url: String): String? = api.rawText(url)

    suspend fun search(keyword: String, page: Int): SearchPage {
        val needle = keyword.trim().lowercase()
        if (needle.isEmpty()) return SearchPage(emptyList(), hasMore = false)
        val matches = api.searchIndex().mapNotNull { entry ->
            val haystack = listOf(entry.name, entry.description, entry.summary, entry.readmeExcerpt, entry.authors)
                .joinToString(" ").lowercase()
            if (needle !in haystack) return@mapNotNull null
            val score = when {
                entry.name.lowercase() == needle -> 4
                entry.name.lowercase().contains(needle) -> 3
                entry.description.lowercase().contains(needle) -> 2
                entry.summary.lowercase().contains(needle) -> 1
                else -> 0
            }
            entry to score
        }.sortedWith(compareByDescending<Pair<LsposedSearchEntry, Int>> { it.second }.thenBy { it.first.name })
        val start = (page * PAGE_SIZE).coerceAtLeast(0)
        if (start >= matches.size) return SearchPage(emptyList(), hasMore = false)
        val end = (start + PAGE_SIZE).coerceAtMost(matches.size)
        val page = matches.subList(start, end).map { searchApp(it.first) }
        return SearchPage(enrich(page), hasMore = end < matches.size)
    }

    suspend fun appDetail(packageName: String): AppDetail {
        // 快路径：仓库 JSON 里已经有包名（模块名就是包名）、tag 版本、附件大小，
        // 不必再下载整个 APK 去 inspect —— 那是详情页「转很久」的唯一原因。
        runCatching { fastDetail(packageName) }.getOrNull()?.let { return it }
        val resolved = resolve(packageName) ?: throw MarketException("LSPosed 模块仓库未收录该模块的正式版 APK")
        return resolved.detail
    }

    /** 只用仓库 JSON 拼详情（0 次 APK 下载）。取不到关键信息时返回 null，交给慢路径。 */
    private suspend fun fastDetail(packageName: String): AppDetail? {
        val module = api.module(packageName) ?: return null
        val release = module.latestStableRelease() ?: return null
        val assets = release.releaseAssets.filter { it.isApk && it.preferredUrl.isSafeAssetUrl() }
        if (assets.isEmpty()) return null
        val asset = preferredReleaseAssets(
            assets.map { ReleaseAsset(it.name, it.preferredUrl, it.size) },
            cpuArchitecture,
        ).mapNotNull { preferred -> assets.firstOrNull { it.name == preferred.name && it.preferredUrl == preferred.url } }
            .firstOrNull() ?: assets.first()
        val app = module.toApp(
            release = release,
            asset = asset,
            packageName = packageName,
            versionName = release.versionFromTag(),
            versionCode = inferredVersionCode(release, asset) ?: 0L,
            size = asset.size,
        )
        val detail = AppDetail(
            app = app,
            brief = module.summary.ifBlank { module.description },
            introduction = htmlToText(module.readmeHTML).ifBlank { module.summary.ifBlank { module.description } },
            changeLog = htmlToText(release.descriptionHTML),
            updateTime = runCatching { kotlin.time.Instant.parse(release.createdAt) }
                .getOrNull()?.toEpochMilliseconds() ?: 0L,
            category = "Xposed",
            ageClassification = "",
            downloadCount = 0L,
            registrationNum = "",
            privacyUrl = "",
            screenshots = emptyList(),
            comments = emptyList(),
            sameDeveloperApps = emptyList(),
            // README 本身是 Markdown 渲染出的 HTML，交给详情页按富文本展示
            introductionHtml = module.readmeHTML,
            homepageUrl = module.homepageUrl ?: module.sourceUrl.orEmpty(),
            collaborators = module.collaborators.map {
                com.app.market.domain.model.market.AppCollaborator(login = it.login, name = it.name)
            },
        )
        val now = Clock.System.now().toEpochMilliseconds()
        mutex.withLock { cache[packageName] = Resolved(detail, asset, now) }
        return detail
    }

    /** 当前正式版里所有可下载的 APK 附件（国内版/国际版、不同 ABI 都会各占一条）。 */
    suspend fun assets(packageName: String): List<ModuleAsset> {
        val module = api.module(packageName) ?: return emptyList()
        val release = module.latestStableRelease() ?: return emptyList()
        return release.releaseAssets
            .filter { it.isApk && it.preferredUrl.isSafeAssetUrl() }
            .map { ModuleAsset(name = it.name, url = it.preferredUrl, size = it.size) }
    }

    suspend fun downloadMeta(app: MarketAppInfo, assetName: String? = null): DownloadMeta {
        // 不走 resolve（它要先下载一遍 APK 读信息）：asset 的下载地址/大小仓库 JSON 里都有，
        // 直接取即可 —— 也绕开旧版「versionCode 必须与 APK 实际值相等」的强校验
        // （更新列表里的 versionCode 是推断值，对不上就被误拦成「模块版本已变化」）。
        val module = api.module(app.packageName) ?: throw MarketException("LSPosed 模块仓库未收录该模块")
        val release = module.latestStableRelease() ?: throw MarketException("LSPosed 模块仓库未收录该模块的正式版 APK")
        val usable = release.releaseAssets.filter { it.isApk && it.preferredUrl.isSafeAssetUrl() }
        // 指定附件（用户在详情页选的那份）优先；没指定就按 CPU 架构挑最合适的一份
        val asset = assetName?.let { name -> usable.firstOrNull { it.name == name } }
            ?: usable.firstOrNull()
            ?: throw MarketException("LSPosed 模块仓库未收录该模块的正式版 APK")
        val latest = module.toApp(
            release = release,
            asset = asset,
            packageName = app.packageName,
            versionName = release.versionFromTag(),
            versionCode = maxOf(inferredVersionCode(release, asset) ?: 0L, app.versionCode),
            size = asset.size,
        )
        return DownloadMeta(
            appId = app.appId,
            packageName = latest.packageName,
            displayName = latest.displayName,
            versionName = latest.versionName,
            versionCode = latest.versionCode,
            url = asset.preferredUrl,
            size = latest.apkSize,
            parts = listOf(DownloadPart(asset.name, "base", asset.preferredUrl, latest.apkSize)),
            icon = latest.icon,
            changeLog = htmlToText(release.descriptionHTML),
            source = AppSource.LSPOSED,
        )
    }

    suspend fun checkUpdates(): List<MarketAppInfo> {
        val packages = installed.installed()
            .filter { it.packageName.isNotBlank() && it.versionCode > 0L }
        if (packages.isEmpty()) return emptyList()
        val repositoryPackages = api.searchIndex().mapTo(HashSet()) { it.name }
        val modules = packages.filter { it.packageName in repositoryPackages }
        if (modules.isEmpty()) return emptyList()
        val results = parallel(modules) { local ->
            val module = api.module(local.packageName) ?: return@parallel null
            val release = module.latestStableRelease() ?: return@parallel null
            if (!releaseTagMayBeNewer(release.tagName, local.versionName)) return@parallel null
            // tag 已判定有新版本 —— 直接报告，不再下载 APK 精确校验：
            // 下载整个 APK 读 versionCode 是 LSPosed 更新检查超时的根源
            // （模块 APK 数 MB 起步，单来源超时窗口内根本下不完 → 永远显示「检查更新超时」）。
            // versionCode 从 asset 名/tag 推断；真正安装时 checkManualUpdate 走 resolve 精确校验。
            val asset = release.releaseAssets.firstOrNull { it.isApk && it.preferredUrl.isSafeAssetUrl() }
                ?: return@parallel null
            val inferred = inferredVersionCode(release, asset)
            if (!releaseTagMayBeNewer(release.tagName, local.versionName)) {
                // versionName 一致 —— 同构建才不推；不同构建（国际版/国内版这种
                // versionName 相同但 versionCode 不同的渠道包）仍要推送，由用户决定装不装。
                if (inferred != null && inferred == local.versionCode) return@parallel null
            }
            // 到这里有新版本/新构建 —— 直接报告，不下载 APK 精确校验（下载是超时根源）。
            val app = module.toApp(
                release = release,
                asset = asset,
                packageName = local.packageName,
                versionName = release.versionFromTag(),
                versionCode = maxOf(inferred ?: 0L, local.versionCode + 1),
                size = asset.size,
            )
            app.copy(
                installedVersionCode = local.versionCode,
                installedVersionName = local.versionName,
                installedSplits = local.splits,
                isSystemApp = local.isSystemApp,
            )
        }
        if (results.isNotEmpty() && results.none { it.isSuccess }) results.first().getOrThrow()
        return results.mapNotNull { it.getOrNull() }
    }

    suspend fun checkManualUpdate(request: ManualUpdateRequest): ManualUpdateResult {
        // 不走 resolve（它要下载整个 APK —— GitHub 下载超时会让「更新」按钮点了没反应）；
        // 用仓库 JSON 的 tag/asset 推断版本判定，下载时直接取 asset URL。
        val module = api.module(request.packageName) ?: return ManualUpdateResult(ManualUpdateStatus.NOT_FOUND)
        val release = module.latestStableRelease() ?: return ManualUpdateResult(ManualUpdateStatus.NOT_FOUND)
        val asset = release.releaseAssets.firstOrNull { it.isApk && it.preferredUrl.isSafeAssetUrl() }
            ?: return ManualUpdateResult(ManualUpdateStatus.NOT_FOUND)
        val inferred = inferredVersionCode(release, asset)
        val nameNewer = releaseTagMayBeNewer(release.tagName, request.versionName)
        if (!nameNewer && inferred != null && inferred == request.versionCode) {
            // versionName 一致且同构建 —— 无更新
            return ManualUpdateResult(ManualUpdateStatus.RECOGNIZED_NO_UPDATE)
        }
        val app = module.toApp(
            release = release,
            asset = asset,
            packageName = request.packageName,
            versionName = release.versionFromTag(),
            versionCode = maxOf(inferred ?: 0L, request.versionCode + 1),
            size = asset.size,
        )
        return ManualUpdateResult(
            ManualUpdateStatus.UPDATE_AVAILABLE,
            app.copy(
                installedVersionCode = request.versionCode,
                installedVersionName = request.versionName,
                installedSplits = request.splits,
                isSystemApp = request.isSystemApp,
            ),
        )
    }

    private suspend fun resolve(packageName: String, refresh: Boolean = false): Resolved? {
        val now = Clock.System.now().toEpochMilliseconds()
        mutex.withLock { cache[packageName] }?.takeIf {
            !refresh && now - it.fetchedAt < CACHE_TTL_MILLIS && it.detail.app.packageName == packageName
        }?.let { return it }

        val module = api.module(packageName) ?: return null
        val release = module.latestStableRelease() ?: return null
        val assets = release.releaseAssets.filter { it.isApk && it.preferredUrl.isSafeAssetUrl() }
        if (assets.isEmpty()) return null

        val candidates = preferredReleaseAssets(
            assets.map { ReleaseAsset(it.name, it.preferredUrl, it.size) },
            cpuArchitecture,
        ).mapNotNull { preferred ->
            assets.firstOrNull { it.name == preferred.name && it.preferredUrl == preferred.url }
        }
        var firstFailure: Exception? = null
        for (asset in candidates) {
            val identity = try {
                inspector.inspect(asset.preferredUrl, asset.size)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (firstFailure == null) firstFailure = error
                continue
            }
            if (identity.packageName != packageName) continue
            val app = module.toApp(release, asset, identity.packageName, identity.versionName, identity.versionCode, identity.size)
            val detail = AppDetail(
                app = app,
                brief = module.summary.ifBlank { module.description },
                introduction = htmlToText(module.readmeHTML).ifBlank { module.summary.ifBlank { module.description } },
                changeLog = htmlToText(release.descriptionHTML),
            updateTime = runCatching { kotlin.time.Instant.parse(release.createdAt) }
                .getOrNull()?.toEpochMilliseconds() ?: 0L,
                category = "Xposed",
                ageClassification = "",
                downloadCount = 0L,
                registrationNum = "",
                privacyUrl = "",
                screenshots = emptyList(),
                comments = emptyList(),
                sameDeveloperApps = emptyList(),
                // README 本身是 Markdown 渲染出的 HTML，交给详情页按富文本展示
                introductionHtml = module.readmeHTML,
                homepageUrl = module.homepageUrl ?: module.sourceUrl.orEmpty(),
                collaborators = module.collaborators.map {
                    com.app.market.domain.model.market.AppCollaborator(login = it.login, name = it.name)
                },
            )
            val resolved = Resolved(detail, asset, now)
            mutex.withLock { cache[packageName] = resolved }
            return resolved
        }
        firstFailure?.let { throw MarketException("无法读取模块 APK 信息：${it.message}") }
        return null
    }

    private fun searchApp(entry: LsposedSearchEntry): MarketAppInfo = MarketAppInfo(
        appId = fdroidStableId("lsposed:${entry.name}"),
        packageName = entry.name,
        displayName = entry.description.ifBlank { entry.name },
        publisherName = entry.authors,
        versionName = "",
        versionCode = 0L,
        icon = avatarForAuthor(entry.authors),
        apkSize = 0L,
        ratingScore = 0.0,
        openLink = "$SITE/module/${entry.name}/",
        source = AppSource.LSPOSED,
        category = "Xposed",
        summaryText = entry.summary,
    )

    /** 列表补星标数 / 更新时间 / README 图标：并发拉模块 JSON，失败保持原样。 */
    private suspend fun enrich(apps: List<MarketAppInfo>): List<MarketAppInfo> {
        if (apps.isEmpty()) return apps
        val now = Clock.System.now()
        val results = parallel(apps) { app ->
            val module = api.module(app.packageName) ?: return@parallel app
            app.copy(
                downloadCount = module.stargazerCount,
                icon = extractReadmeIcon(module.readmeHTML) ?: app.icon,
                summaryText = app.summaryText.ifBlank { module.summary },
                releaseTimeText = relativeTime(module.latestReleaseTime, now),
            )
        }
        return results.mapIndexed { index, result -> result.getOrNull() ?: apps[index] }
    }

    /** 「2026-11-07T05:32:01Z」→「3天前」。解析失败返回空串。 */
    private fun relativeTime(iso: String?, now: kotlin.time.Instant): String {
        if (iso.isNullOrBlank()) return ""
        val then = runCatching { kotlin.time.Instant.parse(iso) }.getOrNull() ?: return ""
        val minutes = (now - then).inWholeMinutes
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            minutes < 60 * 24 -> "${minutes / 60}小时前"
            minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)}天前"
            minutes < 60 * 24 * 365 -> "${minutes / (60 * 24 * 30)}个月前"
            else -> "${minutes / (60 * 24 * 365)}年前"
        }
    }

    private fun LsposedModule.toApp(
        release: LsposedRelease,
        asset: LsposedAsset,
        packageName: String,
        versionName: String,
        versionCode: Long,
        size: Long,
    ): MarketAppInfo {
        val publisher = collaborators.joinToString(", ") { it.name.ifBlank { it.login } }
            .ifBlank { homepageOwner().orEmpty() }
        return MarketAppInfo(
            appId = fdroidStableId("lsposed:$packageName"),
            packageName = packageName,
            displayName = description.ifBlank { name },
            publisherName = publisher,
            versionName = versionName.ifBlank { release.versionFromTag() },
            versionCode = versionCode,
            icon = extractReadmeIcon(readmeHTML) ?: avatarForHomepage(homepageUrl) ?: avatarForAuthor(publisher),
            apkSize = size.takeIf { it > 0L } ?: asset.size,
            ratingScore = 0.0,
            changeLog = htmlToText(release.descriptionHTML),
            openLink = "$SITE/module/$name/",
            source = AppSource.LSPOSED,
            category = "Xposed",
        )
    }

    private fun LsposedRelease.versionFromTag(): String =
        tagName.substringAfter('-', tagName).ifBlank { tagName }

    private fun LsposedModule.homepageOwner(): String? =
        homepageUrl?.let { GITHUB_OWNER.find(it)?.groupValues?.getOrNull(1) }

    /** 拉模块 JSON 提取 README 首图作为图标（搜索列表懒加载用，不解析 APK）。 */
    suspend fun readmeIcon(packageName: String): String? = runCatching {
        val module = api.module(packageName) ?: return@runCatching null
        module.readmeHTML?.let { extractReadmeIcon(it) }
    }.getOrNull()

    private fun extractReadmeIcon(readmeHtml: String): String? =
        README_IMG.findAll(readmeHtml)
            .map { it.groupValues[1].replace("&amp;", "&") }
            .firstOrNull { url ->
                // 过滤 shields.io/badgen 等徽章图，只取真正的图片资源（通常第一张是模块图标）
                !url.contains("shields.io", ignoreCase = true) &&
                    !url.contains("badgen.net", ignoreCase = true) &&
                    !url.contains("badge", ignoreCase = true)
            }

    private fun avatarForHomepage(url: String?): String? {
        val owner = url?.let { GITHUB_OWNER.find(it)?.groupValues?.getOrNull(1) } ?: return null
        return "https://github.com/$owner.png?size=200"
    }

    private fun avatarForAuthor(authors: String): String {
        val first = authors.substringBefore(',').substringBefore(" (").trim()
        return if (first.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})"))) {
            "https://github.com/$first.png?size=200"
        } else {
            "https://github.com/LSPosed.png?size=200"
        }
    }
    /**
     * 不下载 APK 时的 versionCode 推断值。
     *
     * 优先从 asset 名解析（LSPosed 系 asset 名常带 versionCode，如 LSPosed-v1.9.2-7024-release.apk），
     * 其次从 tag 数字段取尾段；都取不到就用「本地 +1」占位 —— 只要保证比本地大，
     * 更新列表的判定就能通过，真正安装时 checkManualUpdate 会走 resolve 精确校验。
     */
    /**
     * 不下载 APK 时从 asset 名/tag 推断远端 versionCode；解析不出返回 null。
     *
     * 只用于「等值比较」（判断是否同一构建），不做大小比较 —— 渠道包之间（国际版/国内版）
     * 的 versionCode 体系可能不同，大小没有可比性。
     */
    private fun inferredVersionCode(release: LsposedRelease, asset: LsposedAsset): Long? {
        // 仓库 tag 形如「2112-2.11.2」：开头那串数字就是 versionCode，必须优先取，
        // 否则会落到最后一段（2.11.2 的 2），版本号就显示成「2-2.11.2」。
        tagLeadingCode(release.tagName)?.let { return it }
        Regex("\\d{3,}").findAll(asset.name)
            .mapNotNull { it.value.toLongOrNull() }
            .lastOrNull()
            ?.let { return it }
        versionSegments(release.tagName)
            ?.firstOrNull { it >= 1000L }
            ?.let { return it }
        versionSegments(release.tagName)?.lastOrNull()
            ?.let { return it }
        return null
    }

    /** tag 开头的大数字段（>=1000）就是 versionCode。 */
    private fun tagLeadingCode(tagName: String): Long? =
        Regex("^(\\d{3,})").find(tagName.trim())?.groupValues?.get(1)?.toLongOrNull()

    /** 历史发布记录（新到旧）；失败时返回空列表。 */
    suspend fun releases(packageName: String): List<com.app.market.domain.model.market.ModuleRelease> =
        runCatching {
            val module = api.module(packageName) ?: return@runCatching emptyList()
            module.releases
                .filter { !it.isDraft }
                .map { release ->
                    val assets = release.releaseAssets
                        .filter { it.isApk && it.preferredUrl.isSafeAssetUrl() }
                        .map {
                            com.app.market.domain.model.market.ModuleAsset(
                                name = it.name,
                                url = it.preferredUrl,
                                size = it.size,
                            )
                        }
                    com.app.market.domain.model.market.ModuleRelease(
                        name = release.versionFromTag(),
                        tagName = release.tagName,
                        versionCode = tagLeadingCode(release.tagName) ?: 0L,
                        publishedAt = runCatching { kotlin.time.Instant.parse(release.createdAt) }
                            .getOrNull()?.toEpochMilliseconds() ?: 0L,
                        changeLog = htmlToText(release.descriptionHTML),
                        assets = assets,
                    )
                }
                .filter { it.assets.isNotEmpty() }
        }.getOrDefault(emptyList())

    /**
     * 仓库 release 的 tag 是否可能比已安装版本新。
     *
     * 取模块详情要下载整个 APK 才能读出 versionCode，代价极高；而 release tag 通常就带版本号。
     * 能解析出数字且不比本地新 → 直接判定无更新，省掉一次 APK 下载。
     * 解析不出数字时保守放行（走完整校验），宁可慢也不漏报。
     */
    private fun releaseTagMayBeNewer(tagName: String, installedVersionName: String): Boolean {
        val remote = versionSegments(tagName) ?: return false
        val local = versionSegments(installedVersionName) ?: return false
        if (remote.isEmpty() || local.isEmpty()) return false
        // 本地版本名的数字段是远端的连续子序列 → 同版本（远端多出的是 build号）
        for (start in 0..remote.size - local.size) {
            if (remote.subList(start, start + local.size) == local) return false
        }
        // 反向：远端是本地的连续子序列 → 也同版本（本地版本名带 build号后缀，如 2.7.5-1234）
        for (start in 0..local.size - remote.size) {
            if (local.subList(start, start + remote.size) == remote) return false
        }
        return true
    }

    /** 「v2.1.13-beta」→ [2, 1, 13]；没有数字或含超大数字返回 null。 */
    private fun versionSegments(name: String): List<Long>? {
        val runs = Regex("\\d+").findAll(name).map { it.value.toLongOrNull() }.toList()
        if (runs.isEmpty() || runs.any { it == null }) return null
        return runs.filterNotNull()
    }

    private suspend fun <T, R> parallel(items: List<T>, block: suspend (T) -> R): List<Result<R>> = coroutineScope {
        // 并发度 6：模块数量本来就不多，而每个任务可能要先下载 APK，
        // 4 路串行分批会把总耗时拖成 N/4 倍，是 LSPosed 更新检查超时的另一半原因。
        items.chunked(6).flatMap { batch ->
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

    private companion object {
        const val SITE = "https://modules.lsposed.org"
        const val PAGE_SIZE = 20

        /**
         * 模块详情缓存时长。
         *
         * 取一次详情要下载整个 APK 才能读出 versionCode，代价很高；
         * 原来的 5 分钟等于「每次更新检查都全部重下」。放宽到 30 分钟后，
         * 同一会话内的重复检查（切页面、手动刷新）直接命中缓存。
         */
        const val CACHE_TTL_MILLIS = 30 * 60_000L
        val GITHUB_OWNER = Regex("^https://github\\.com/([A-Za-z0-9_.-]+)(?:/|$)")
        val README_IMG = Regex("<img[^>]+src=[\"']([^\"']+)[\"']")
    }
}

private fun String.isSafeAssetUrl(): Boolean = runCatching {
    val url = Url(this)
    url.protocol.name == "https" &&
        url.user.isNullOrEmpty() &&
        url.password.isNullOrEmpty() &&
        (url.host == "github.com" || url.host == "assets.lsposed.org")
}.getOrDefault(false)

internal fun htmlToText(raw: String): String = raw
    .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
    .replace(Regex("(?i)<br\\s*/?>"), "\n")
    .replace(Regex("(?i)</(p|div|li|h[1-6]|tr)>"), "\n")
    .replace(Regex("<[^>]+>"), "")
    .let(::decodeHtmlEntities)
    .replace(Regex("[ \t]+\n"), "\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
    .take(MAX_DESCRIPTION_CHARS)

private fun extractScreenshots(html: String): List<AppScreenshot> =
    Regex("(?i)<img[^>]+src=[\"']([^\"']+)[\"']").findAll(html)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .filter { it.startsWith("https://", ignoreCase = true) }
        .filterNot { "shields.io" in it || "badge" in it || "avatar" in it }
        .distinct()
        .take(8)
        .map { AppScreenshot(it, ScreenshotOrientation.PORTRAIT) }
        .toList()

private fun decodeHtmlEntities(value: String): String {
    val named = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    return Regex("&#(x?)([0-9A-Fa-f]+);").replace(named) { match ->
        val code = match.groupValues[2].toIntOrNull(if (match.groupValues[1].isEmpty()) 10 else 16)
        code?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: match.value
    }
}

private const val MAX_DESCRIPTION_CHARS = 30_000
