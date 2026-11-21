package com.app.market.data.remote.releases

import com.app.market.data.local.PreferencesDataSource
import com.app.market.data.local.StringPreferenceKey
import com.app.market.data.remote.fdroid.fdjArray
import com.app.market.data.remote.fdroid.fdjLong
import com.app.market.data.remote.fdroid.fdjObject
import com.app.market.data.remote.fdroid.fdjString
import com.app.market.domain.exception.MarketException
import com.app.market.domain.model.market.AppSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class ReleaseRepositoryRef(val source: AppSource, val path: String) {
    val link: String get() = "https://${if (source == AppSource.GITHUB) "github.com" else "gitlab.com"}/$path"
}

internal data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String = "")
internal data class RepositoryRelease(
    val ref: ReleaseRepositoryRef, val description: String, val notes: String,
    val icon: String, val assets: List<ReleaseAsset>,
    val stars: Long = 0L, val updatedAt: String = "",
    /** release 的 tag 名（版本号文本）。 */
    val tagName: String = "",
)

/** 一条搜索命中：仓库引用 + 搜索接口已给出的简介与图标，用于省掉额外的 /repos 请求。 */
internal data class ReleaseSearchHit(
    val ref: ReleaseRepositoryRef,
    val description: String = "",
    val icon: String = "",
    /** 仓库星标数（列表卡显示用）。 */
    val stars: Long = 0L,
    /** ISO8601 最后推送时间（列表卡显示用）。 */
    val updatedAt: String = "",
)

internal class ReleaseApi(
    private val client: HttpClient,
    private val preferences: PreferencesDataSource,
) {
    private val githubTokenKey = StringPreferenceKey("release_sources", "github.token")
    private suspend fun githubToken(): String = preferences.read(githubTokenKey).orEmpty().trim()
    private val json = Json { ignoreUnknownKeys = true }

    /** 用令牌换取 GitHub 登录名，令牌无效时抛出 [MarketException]。 */
    suspend fun viewerLogin(token: String): String {
        val response = client.get("$GITHUB_API/user") {
            header(HttpHeaders.UserAgent, "Trove")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.Authorization, "Bearer ${token.trim()}")
        }
        if (response.status.value == 401) throw MarketException("GitHub 令牌无效或已过期")
        if (response.status.value !in 200..299) throw MarketException("GitHub 登录失败：HTTP ${response.status.value}")
        val login = json.parseToJsonElement(response.bodyAsText()).fdjString("login").trim()
        if (login.isEmpty()) throw MarketException("GitHub 未返回登录名")
        return login
    }

    /** 查询令牌的剩余额度，用于设置页提示；查询失败返回 null。 */
    suspend fun rateLimit(token: String): Pair<Int, Int>? {
        val response = runCatching {
            client.get("$GITHUB_API/rate_limit") {
                header(HttpHeaders.UserAgent, "Trove")
                header(HttpHeaders.Accept, "application/vnd.github+json")
                if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer ${token.trim()}")
            }
        }.getOrNull() ?: return null
        if (response.status.value !in 200..299) return null
        val root = runCatching { json.parseToJsonElement(response.bodyAsText()) }.getOrNull() ?: return null
        val core = root.fdjObject("resources").fdjObject("core")
        return core.fdjLong("remaining").toInt() to core.fdjLong("limit").toInt()
    }

    suspend fun search(source: AppSource, keyword: String, page: Int): Pair<List<ReleaseSearchHit>, Boolean> {
        val root = if (source == AppSource.GITHUB) {
            get("https://api.github.com/search/repositories") {
                parameter("q", "$keyword android"); parameter("per_page", PAGE_SIZE); parameter("page", page + 1)
            }
        } else {
            get("https://gitlab.com/api/v4/projects") {
                parameter("search", keyword); parameter("simple", "true"); parameter("visibility", "public")
                parameter("order_by", "star_count"); parameter("sort", "desc")
                parameter("per_page", PAGE_SIZE); parameter("page", page + 1)
            }
        }
        val entries = if (source == AppSource.GITHUB) root.fdjArray("items").orEmpty() else (root as? JsonArray).orEmpty()
        // 搜索接口本身就带 description 与 owner 头像：一并带回，后续解析仓库时就不必再请求 /repos/{path}
        // （匿名额度只有 60 次/小时，每仓库省一次请求等于把可搜索仓库数翻倍）
        val refs = entries.mapNotNull { entry ->
            val ref = parseReleaseRepository(
                source,
                if (source == AppSource.GITHUB) entry.fdjString("html_url") else entry.fdjString("web_url"),
            ) ?: return@mapNotNull null
            ReleaseSearchHit(
                ref = ref,
                description = entry.fdjString("description"),
                icon = if (source == AppSource.GITHUB) {
                    entry.fdjObject("owner").fdjString("avatar_url")
                } else {
                    entry.fdjString("avatar_url")
                },
                stars = entry.fdjLong("stargazers_count"),
                updatedAt = entry.fdjString("pushed_at"),
            )
        }
        val hasMore = if (source == AppSource.GITHUB) (page + 1) * PAGE_SIZE < root.fdjLong("total_count")
            else entries.size >= PAGE_SIZE
        return refs to hasMore
    }

    /** Komi 后端榜单和发现流；不切换到其他来源。 */
    suspend fun komiPage(option: String, page: Int): Pair<List<ReleaseSearchHit>, Boolean> {
        val charts = mapOf("trending" to "trending", "releases" to "new-releases", "popular" to "most-popular")
        val category = charts[option]
        val root = if (category != null) {
            get("https://api.github-store.org/v1/categories/$category/android")
        } else {
            get("https://api.github-store.org/v1/feed") {
                parameter("platform", "android"); parameter("page", page + 1); parameter("limit", 20)
            }
        } ?: throw MarketException("Komi 榜单请求没有返回数据")
        val entries = if (category != null) {
            val all = (root as? JsonArray) ?: throw MarketException("Komi 榜单响应格式错误")
            all.drop(page * 20).take(20)
        } else root.fdjArray("items") ?: throw MarketException("Komi 发现响应格式错误")
        val topicCodes = when (option) {
            "ai" -> setOf("ai")
            "privacy" -> setOf("privacy", "security")
            "networking" -> setOf("networking", "browser", "messaging")
            "media" -> setOf("audio", "video", "photo")
            "social" -> setOf("social")
            "reading" -> setOf("reader", "notes")
            "tools" -> setOf("launcher", "backup", "self-hosted")
            else -> emptySet()
        }
        val hits = entries.filter { entry ->
            topicCodes.isEmpty() || entry.fdjArray("topicCodes").orEmpty().any { it.toString().trim('"').lowercase() in topicCodes }
        }.mapNotNull { entry ->
            val ref = parseReleaseRepository(AppSource.GITHUB, entry.fdjString("htmlUrl")) ?: return@mapNotNull null
            ReleaseSearchHit(ref, entry.fdjString("description"), entry.fdjObject("owner").fdjString("avatarUrl"),
                entry.fdjLong("stargazersCount"), entry.fdjString("pushedAt"))
        }
        val hasMore = if (category != null) (page + 1) * 20 < (root as JsonArray).size
            else root.fdjString("hasMore") == "true"
        return hits to hasMore
    }

    /** 仅用于判断 owner/repo 是否真实存在：仓库不存在时返回 null，限流等错误照常抛出。 */
    suspend fun repoExists(ref: ReleaseRepositoryRef): Boolean = repoMetadata(ref) != null
    private suspend fun repoMetadata(ref: ReleaseRepositoryRef): JsonElement? =
        if (ref.source == AppSource.GITHUB) get("$GITHUB_API/repos/${ref.path}")
        else get("https://gitlab.com/api/v4/projects/${ref.path.encodeURLParameter()}")
    /**
     * 取仓库最新可用发布。
     *
     * GitHub 单页默认只返回 30 条，很多仓库最近几个版本都是 prerelease 或只挂 zip，
     * 只读第一页会误判成「没有 APK」。这里按 100 条一页往后翻，直到找到第一个可用的正式版
     * （[includePrereleases] 为 true 时不再排除 prerelease）。
     */
    suspend fun latest(
        ref: ReleaseRepositoryRef,
        includePrereleases: Boolean = false,
        hit: ReleaseSearchHit? = null,
    ): RepositoryRelease? {
        val project = if (ref.source == AppSource.GITHUB) "https://api.github.com/repos/${ref.path}"
            else "https://gitlab.com/api/v4/projects/${ref.path.encodeURLParameter()}"
        val summary = hit ?: get(project)?.let { metadata ->
            ReleaseSearchHit(
                ref = ref,
                description = metadata.fdjString("description"),
                icon = if (ref.source == AppSource.GITHUB) metadata.fdjObject("owner").fdjString("avatar_url")
                else metadata.fdjString("avatar_url"),
                stars = metadata.fdjLong("stargazers_count"),
                updatedAt = metadata.fdjString("pushed_at"),
            )
        } ?: return null
        var page = 1
        var release: JsonElement? = null
        while (release == null) {
            val releases = get("$project/releases") {
                parameter("per_page", RELEASE_PAGE_SIZE); parameter("page", page)
            } as? JsonArray ?: return null
            release = releases.firstOrNull {
                if (ref.source == AppSource.GITHUB) {
                    it.fdjString("draft") != "true" &&
                        (includePrereleases || it.fdjString("prerelease") != "true") &&
                        githubAssets(it).isNotEmpty()
                } else {
                    it.fdjString("upcoming_release") != "true" && gitlabAssets(it).isNotEmpty() &&
                        (includePrereleases ||
                            !Regex("(?i)(alpha|beta|rc|nightly|snapshot)([._-]?\\d*)?(?:\\+[0-9a-z.-]+)?$")
                                .containsMatchIn(it.fdjString("tag_name")))
                }
            }
            if (release == null && releases.size < RELEASE_PAGE_SIZE) return null
            page++
        }
        return RepositoryRelease(
            ref = ref, description = summary.description,
            notes = release.fdjString(if (ref.source == AppSource.GITHUB) "body" else "description"),
            icon = summary.icon,
            stars = summary.stars,
            updatedAt = summary.updatedAt,
            tagName = release.fdjString("tag_name"),
            assets = if (ref.source == AppSource.GITHUB) githubAssets(release) else gitlabAssets(release),
        )
    }

    /** README 渲染后的 HTML（GitHub 用 vnd.github.html，直接是排好版的 HTML）；失败返回空串。 */
    /** README 正文缓存： GitHub 偶发 403/超时会让自述文件整页空白，缓存下来下次进页面直接出。 */
    private val readmeCache = mutableMapOf<String, String>()

    suspend fun readmeHtml(ref: ReleaseRepositoryRef): String {
        val cacheKey = ref.source.token + ":" + ref.path
        readmeCache[cacheKey]?.takeIf { it.isNotBlank() }?.let { return it }
        val url = if (ref.source == AppSource.GITHUB) {
            "$GITHUB_API/repos/${ref.path}/readme"
        } else {
            "https://gitlab.com/api/v4/projects/${ref.path.encodeURLParameter()}/repository/files/README.md/raw"
        }
        val token = if (url.startsWith("$GITHUB_API/")) githubToken() else ""
        // 失败（额度、超时、TLS 抖动）重试一次，仍失败才返回空
        repeat(2) { attempt ->
            val response = runCatching {
                client.get(url) {
                    header(HttpHeaders.UserAgent, "Trove")
                    header(
                        HttpHeaders.Accept,
                        if (ref.source == AppSource.GITHUB) "application/vnd.github.html" else "text/plain",
                    )
                    if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer ${token.trim()}")
                }
            }.getOrNull()
            if (response != null && response.status.value in 200..299) {
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                if (body.isNotBlank()) {
                    readmeCache[cacheKey] = body
                    return body
                }
            }
            if (attempt == 0) kotlinx.coroutines.delay(600)
        }
        return ""
    }

    /** 仓库的全部发布（最多 30 条），供详情页「版本」列表使用。 */
    /** 发布列表缓存：额度抖动时详情页不会变成「只有当前版本、没有历史」。 */
    private val releasesCache = mutableMapOf<String, List<RepositoryRelease>>()

    suspend fun releases(ref: ReleaseRepositoryRef): List<RepositoryRelease> {
        val cacheKey = ref.source.token + ":" + ref.path
        // 每次重新请求发布列表，避免旧缓存阻止刷新。
        val project = if (ref.source == AppSource.GITHUB) "$GITHUB_API/repos/${ref.path}"
        else "https://gitlab.com/api/v4/projects/${ref.path.encodeURLParameter()}"
        var entries: JsonArray? = null
        for (attempt in 0..1) {
            entries = get("$project/releases") {
                parameter("per_page", 100); parameter("page", 1)
            } as? JsonArray
            if (entries != null) break
            if (attempt == 0) kotlinx.coroutines.delay(600)
        }
        val parsed = (entries ?: return emptyList()).mapNotNull { entry ->
            val assets = if (ref.source == AppSource.GITHUB) githubAssets(entry) else gitlabAssets(entry)
            if (assets.isEmpty()) return@mapNotNull null
            RepositoryRelease(
                ref = ref,
                description = "",
                notes = entry.fdjString(if (ref.source == AppSource.GITHUB) "body" else "description"),
                icon = "",
                assets = assets,
                updatedAt = entry.fdjString("published_at"),
                tagName = entry.fdjString("tag_name"),
            )
        }
        if (parsed.isNotEmpty()) releasesCache[cacheKey] = parsed
        return parsed
    }

    private suspend fun get(url: String, configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}): JsonElement? {
        // 只有 GitHub 的 api.github.com 接受令牌；GitLab 匿名即可，避免把 GitHub 凭据发给第三方。
        val token = if (url.startsWith("$GITHUB_API/")) githubToken() else ""
        val response = client.get(url) {
            header(HttpHeaders.UserAgent, "Trove")
            header(HttpHeaders.Accept, "application/json")
            if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer ${token.trim()}")
            configure()
        }
        if (response.status.value == 404) return null
        if (!url.startsWith("$GITHUB_API/") && response.status.value !in 200..299) {
            throw MarketException("来源请求失败：HTTP ${response.status.value}")
        }
        // 403/429 的原因有三种，必须区分开，否则用户只会看到笼统的「额度用尽」：
        // 1) 真限流（body 里带 "rate limit"，可能是 core 也可能是 search 的独立配额）
        // 2) fine-grained 令牌没给 public 仓库读权限（body 里是 "Resource not accessible ..."）
        // 3) 其它拒绝（组织限制、仓库被禁等）
        if (response.status.value == 401 || response.status.value == 403 || response.status.value == 429) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            val message = runCatching { json.parseToJsonElement(body).fdjString("message") }.getOrDefault("")
            val remaining = response.headers["X-RateLimit-Remaining"]
            val limit = response.headers["X-RateLimit-Limit"]
            val resource = response.headers["X-RateLimit-Resource"] ?: "-"
            val used = response.headers["X-RateLimit-Used"] ?: "-"
            if (response.status.value == 401) {
                throw MarketException(
                    if (token.isNotBlank()) "GitHub 令牌已失效，请在设置中重新登录"
                    else "GitHub 拒绝了匿名请求，请在设置中登录 GitHub",
                )
            }
            if (message.contains("rate limit", ignoreCase = true)) {
                val hint = if (token.isNotBlank()) {
                    "该令牌在 $resource 上的配额已用尽"
                } else {
                    "请在设置中登录 GitHub 以提升额度（匿名仅 60 次/小时）"
                }
                throw MarketException("来源 API 请求次数已达上限（$resource 已用 $used/$limit），$hint")
            }
            if (message.contains("not accessible", ignoreCase = true) ||
                message.contains("Resource not accessible", ignoreCase = true)
            ) {
                throw MarketException("GitHub 令牌权限不足：请使用 classic token 并勾选 public_repo（当前令牌访问不了该仓库）")
            }
            if (message.isNotBlank()) throw MarketException("来源请求被拒绝：$message")
            throw MarketException("来源请求失败：HTTP ${response.status.value}")
        }
        if (response.status.value !in 200..299) throw MarketException("来源请求失败：HTTP ${response.status.value}")
        return json.parseToJsonElement(response.bodyAsText())
    }

    private companion object {
        // 每仓库在解析阶段要花 1 次 core 额度（releases 列表）；匿名额度 60 次/小时，
        // 一页取 12 个既够填满屏幕，又不会一次搜索就把整小时额度打光。
        const val PAGE_SIZE = 12
        const val RELEASE_PAGE_SIZE = 100
        const val GITHUB_API = "https://api.github.com"
    }
}

internal fun parseReleaseRepository(source: AppSource, input: String): ReleaseRepositoryRef? {
    if (source != AppSource.GITHUB && source != AppSource.GITLAB) return null
    val host = if (source == AppSource.GITHUB) "github.com" else "gitlab.com"
    val candidate = input.trim()
    val path = if (candidate.startsWith("https://", true)) {
        val url = runCatching { Url(candidate) }.getOrNull() ?: return null
        if (!url.host.equals(host, true) || url.port != 443 || !url.user.isNullOrEmpty() || !url.password.isNullOrEmpty()) return null
        url.encodedPath.trim('/')
    } else {
        if (candidate.contains("://") || candidate.startsWith('/')) return null
        candidate.removePrefix("$host/").trim('/')
    }
    var parts = path.split('/').takeWhile { it != "-" }
    if (source == AppSource.GITHUB) parts = parts.take(2)
    if (parts.size < 2) return null
    parts = parts.dropLast(1) + parts.last().removeSuffix(".git")
    if (parts.any { !Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*").matches(it) || it == "." || it == ".." }) return null
    return ReleaseRepositoryRef(source, parts.joinToString("/"))
}

internal fun githubAssets(release: JsonElement): List<ReleaseAsset> = release.fdjArray("assets").orEmpty().mapNotNull {
    val name = it.fdjString("name")
    val url = it.fdjString("browser_download_url")
    if (!name.endsWith(".apk", true) || !isPublicAssetUrl(url) || it.fdjString("state", "uploaded") != "uploaded") return@mapNotNull null
    val digest = it.fdjString("digest").removePrefix("sha256:")
    ReleaseAsset(name, url, it.fdjLong("size"), digest.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }.orEmpty())
}

internal fun gitlabAssets(release: JsonElement): List<ReleaseAsset> = release.fdjObject("assets").fdjArray("links").orEmpty().mapNotNull {
    val url = it.fdjString("direct_asset_url").ifBlank { it.fdjString("url") }
    val name = it.fdjString("name").takeIf { it.endsWith(".apk", true) }
        ?: runCatching { Url(url).encodedPath.substringAfterLast('/') }.getOrNull().orEmpty()
    if (!name.endsWith(".apk", true) || !isPublicAssetUrl(url)) return@mapNotNull null
    ReleaseAsset(name, url, 0L)
}

private fun isPublicAssetUrl(value: String): Boolean = runCatching {
    val url = Url(value)
    url.protocol.name == "https" && url.user.isNullOrEmpty() && url.password.isNullOrEmpty() && url.host.isNotBlank()
}.getOrDefault(false)