package com.app.market.data.update

import com.app.market.data.install.network.InstallHttpClient
import com.app.market.domain.model.update.SelfUpdate
import com.app.market.domain.repository.SelfUpdateRepository
import com.app.market.domain.repository.UpdatePreferencesRepository
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import org.json.JSONArray
import org.json.JSONObject

/** 仓库为私有仓库，请求需携带访问令牌；令牌随客户端分发仅适用于个人自用场景。 */
internal class GitLabSelfUpdateRepository(
    private val http: InstallHttpClient,
    private val preferences: UpdatePreferencesRepository,
) : SelfUpdateRepository {
    override val projectUrl: String = PROJECT_URL
    override val authHeaders: Map<String, String> = mapOf("PRIVATE-TOKEN" to ACCESS_TOKEN)

    override suspend fun latestRelease(): SelfUpdate? {
        val includePrerelease = runCatching { preferences.receivePrerelease.value }.getOrDefault(false)
        val root = latestReleaseJson(includePrerelease) ?: return null
        val link = root.optJSONObject("assets")
            ?.optJSONArray("links")
            ?.let { links ->
                (0 until links.length()).mapNotNull { links.optJSONObject(it) }
                    .firstOrNull { it.optString("url").endsWith(".apk", ignoreCase = true) }
                    ?: links.optJSONObject(0)
            }
        val apkUrl = link?.optString("url").orEmpty()
        if (apkUrl.isBlank()) return null
        // 资产文件名形如 AppMarket-v2.3.6(225).apk，括号内为目标 versionCode（安装完整性校验用）
        val versionCode = link?.optString("name")
            ?.let { ASSET_VERSION_REGEX.find(it)?.groupValues?.get(1)?.toLongOrNull() }
            ?: 0L
        val size = probeSize(apkUrl)
        return SelfUpdate(
            tag = root.optString("tag_name"),
            apkUrl = apkUrl,
            notes = root.optString("description"),
            versionCode = versionCode,
            size = size,
            // GitLab 对未来 released_at 的发布标记 upcoming_release=true，
            // 这种包取回来后要在更新卡片上标「测试版」，别让用户当成正式版
            prerelease = root.optBoolean("upcoming_release", false),
        )
    }

    /**
     * 取候选 Release。
     *
     * - 默认（不接收测试版）：只认正式发布（upcoming_release 为 false）中 released_at 最新的一条；
     * - 勾选「接收测试版本」：released_at 最新的一条成为候选（Upcoming Release 也算）。
     *
     * 不能用 `releases/permalink/latest` 当「只认正式版」的通道：实测它会 302 跳到
     * Upcoming Release（v2.5.0 预发布被它当成 latest），关掉开关照样拿到测试版，
     * 所以两个开关状态统一走列表 + 显式过滤。
     */
    private suspend fun latestReleaseJson(includePrerelease: Boolean): JSONObject? {
        val response = http.client.get("$API_BASE/releases?per_page=20") {
            header("PRIVATE-TOKEN", ACCESS_TOKEN)
        }
        if (!response.status.isSuccess()) return null
        val array = runCatching { JSONArray(response.bodyAsText()) }.getOrNull() ?: return null
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .filter { it.optString("released_at").isNotBlank() }
            .filter { includePrerelease || !it.optBoolean("upcoming_release", false) }
            .maxByOrNull { it.optString("released_at") }
    }

    /**
     * 取资产大小。
     *
     * Release 资产链接是 302 跳转到对象存储的签名地址，HEAD 有时拿不到 Content-Length
     * （跳转后长度缺失或为 0），所以再退一步用 `Range: bytes=0-0` 探测，
     * 从 `Content-Range: bytes 0-0/总长` 里读总字节数。
     */
    private suspend fun probeSize(url: String): Long {
        val headSize = runCatching {
            val head = http.client.head(url) { header("PRIVATE-TOKEN", ACCESS_TOKEN) }
            head.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
        if (headSize > 0L) return headSize

        return runCatching {
            val ranged = http.client.get(url) {
                header("PRIVATE-TOKEN", ACCESS_TOKEN)
                header(HttpHeaders.Range, "bytes=0-0")
            }
            ranged.headers[HttpHeaders.ContentRange]
                ?.substringAfter('/', "")
                ?.trim()
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: ranged.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                ?: 0L
        }.getOrDefault(0L)
    }

    private companion object {
        const val PROJECT_URL = "https://gitlab.com/FengGuan/AppMarket"
        const val API_BASE = "https://gitlab.com/api/v4/projects/88215952"
        const val ACCESS_TOKEN = ""
        val ASSET_VERSION_REGEX = Regex("""\((\d+)\)""")
    }
}