package com.app.market.data.remote.apkpure

import com.app.market.domain.exception.MarketException
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.download.DownloadPart
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.AppScreenshot
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.ScreenshotOrientation
import com.app.market.domain.model.market.SearchPage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.absoluteValue

internal data class ApkPureApiConfig(
    /** APKPure 网页端（搜索/详情）与下载直链（d.apkpure.com）。 */
    val siteBaseUrl: String = "https://apkpure.com",
    val downloadBaseUrl: String = "https://d.apkpure.com",
)

private const val ApkPurePageSize = 20
private const val ApkPureUserAgent =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/** 搜索结果里 <a href="https://apkpure.com/{slug}/{package}"> 的链接。 */
private val ApkPureLinkPattern =
    Regex("""href="https://apkpure\.com/([^"/?#]+)/([A-Za-z0-9_.\-]+)""")
private val ApkPureNamePattern = Regex("""<p class="p1">([^<]+)</p>""")
private val ApkPureDevPattern = Regex("""<p class="p2">([^<]+)</p>""")
private val ApkPureIconPattern = Regex("""<img[^>]+?src="([^"]+)"""")
private val ApkPureLdJsonPattern = Regex("""<script type="application/ld\+json">([\s\S]*?)</script>""")
private val ApkPureVersionCodePattern = Regex(""""versionCode":"?(\d+)""")
private val ApkPureVersionNamePattern = Regex(""""versionName":"([^"]+)"""")
private val ApkPureHtmlTag = Regex("<[^>]+>")

internal class ApkPureApi(
    private val client: HttpClient,
    private val config: ApkPureApiConfig = ApkPureApiConfig(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(keyword: String, page: Int = 0): SearchPage {
        val query = keyword.trim()
        if (query.isBlank()) return SearchPage(emptyList(), false)
        val targetPage = (page + 1).coerceAtLeast(1)
        val html = fetchHtml("${config.siteBaseUrl}/search?q=${query.encodeURLParameter()}&page=$targetPage")
        val apps = mutableListOf<MarketAppInfo>()
        val seen = mutableSetOf<String>()
        for (match in ApkPureLinkPattern.findAll(html)) {
            val packageName = match.groupValues[2]
            if (!seen.add(packageName.lowercase())) continue
            val block = html.substring(match.range.first, minOf(html.length, match.range.first + 900))
            val name = ApkPureNamePattern.find(block)?.groupValues?.get(1)?.cleanHtml() ?: continue
            if (name.isBlank()) continue
            val developer = ApkPureDevPattern.find(block)?.groupValues?.get(1)?.cleanHtml().orEmpty()
            val icon = ApkPureIconPattern.find(block)?.groupValues?.get(1).orEmpty()
            apps.add(
                MarketAppInfo(
                    appId = apkPureStableId(packageName),
                    packageName = packageName,
                    displayName = name,
                    publisherName = developer,
                    versionName = "",
                    versionCode = 0L,
                    icon = icon,
                    apkSize = 0L,
                    ratingScore = 0.0,
                    openLink = "${config.siteBaseUrl}/${match.groupValues[1]}/$packageName",
                    source = AppSource.APKPURE,
                ),
            )
            if (apps.size >= ApkPurePageSize) break
        }
        return SearchPage(apps, apps.size >= ApkPurePageSize)
    }

    suspend fun appDetail(packageName: String, hintUrl: String = ""): AppDetail {
        val detailUrl = hintUrl.ifBlank { resolveDetailUrl(packageName) }
            ?: throw MarketException("APKPure 未收录该应用")
        val html = fetchHtml(detailUrl)
        val ld = parseApplicationLdJson(html)
        val versionName = ApkPureVersionNamePattern.find(html)?.groupValues?.get(1)
            ?: ld.aptString("version")
        val versionCode = ApkPureVersionCodePattern.find(html)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val description = ld.aptString("description").cleanHtml()
        val app = MarketAppInfo(
            appId = apkPureStableId(packageName),
            packageName = packageName,
            displayName = ld.aptString("name").ifBlank { packageName },
            publisherName = ld.aptObject("publisher").aptString("name"),
            versionName = versionName,
            versionCode = versionCode,
            icon = ld.aptArray("screenshot").orEmpty().firstOrNull()?.aptString("url").orEmpty(),
            apkSize = 0L,
            ratingScore = ld.aptObject("aggregateRating").aptString("ratingValue").toDoubleOrNull() ?: 0.0,
            changeLog = "",
            openLink = detailUrl,
            source = AppSource.APKPURE,
        )
        val screenshots = ld.aptArray("screenshot").orEmpty().mapNotNull { shot ->
            val url = shot.aptString("url")
            if (url.isBlank()) null else AppScreenshot(url, ScreenshotOrientation.PORTRAIT)
        }
        return AppDetail(
            app = app,
            brief = description.take(200),
            introduction = description,
            changeLog = "",
            category = ld.aptString("applicationCategory"),
            ageClassification = "",
            downloadCount = 0L,
            registrationNum = "",
            privacyUrl = "",
            screenshots = screenshots,
            comments = emptyList(),
            sameDeveloperApps = emptyList(),
        )
    }

    /** 官方直链：d.apkpure.com/b/APK/{package}?version=latest（会 302 到 CDN）。 */
    suspend fun downloadMeta(app: MarketAppInfo): DownloadMeta {
        val url = "${config.downloadBaseUrl}/b/APK/${app.packageName}?version=latest"
        val size = runCatching {
            val response = client.head(url) { header(HttpHeaders.UserAgent, ApkPureUserAgent) }
            response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
        return DownloadMeta(
            appId = app.appId,
            packageName = app.packageName,
            displayName = app.displayName,
            versionName = app.versionName,
            versionCode = app.versionCode,
            url = url,
            size = size,
            parts = listOf(DownloadPart(app.packageName, "base", url, size)),
            icon = app.icon,
            changeLog = app.changeLog,
            source = AppSource.APKPURE,
        )
    }

    private suspend fun resolveDetailUrl(packageName: String): String? {
        val html = fetchHtml("${config.siteBaseUrl}/search?q=${packageName.encodeURLParameter()}")
        for (match in ApkPureLinkPattern.findAll(html)) {
            if (match.groupValues[2].equals(packageName, ignoreCase = true)) {
                return "${config.siteBaseUrl}/${match.groupValues[1]}/${match.groupValues[2]}"
            }
        }
        return null
    }

    private fun parseApplicationLdJson(html: String): JsonObject {
        for (block in ApkPureLdJsonPattern.findAll(html)) {
            val parsed = runCatching { json.parseToJsonElement(block.groupValues[1]) }.getOrNull() ?: continue
            val candidates: List<JsonElement> = if (parsed is JsonArray) parsed.toList() else listOf(parsed)
            for (candidate in candidates) {
                val obj = candidate as? JsonObject ?: continue
                if (obj.aptString("@type").contains("Application", true)) return obj
            }
        }
        return JsonObject(emptyMap())
    }

    private suspend fun fetchHtml(url: String): String {
        val response = client.get(url) {
            header(HttpHeaders.UserAgent, ApkPureUserAgent)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
        }
        if (!response.status.isSuccess()) {
            throw MarketException("APKPure 请求失败：HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }
}

/** APKPure 无站内数字 id，用包名稳定哈希充当 appId（与 F-Droid / Samsung 同策略）。 */
internal fun apkPureStableId(packageName: String): Long {
    val hash = packageName.fold(1125899906842597L) { acc, char -> acc * 31 + char.code }
    return hash.absoluteValue.coerceAtLeast(1L)
}

private fun String.cleanHtml(): String = ApkPureHtmlTag.replace(this, "").trim()

private fun JsonElement?.aptObject(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

private fun JsonObject?.aptObject(key: String): JsonObject? = this?.get(key) as? JsonObject

private fun JsonElement?.aptArray(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray

private fun JsonObject?.aptArray(key: String): JsonArray? = this?.get(key) as? JsonArray

private fun JsonElement?.aptString(key: String): String =
    (this as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject?.aptString(key: String): String =
    this?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()