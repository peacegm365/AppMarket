package com.app.market.data.remote.aptoide

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

internal data class AptoideApiConfig(
    /** Aptoide 官方 Webservices v7；ws75 是当前默认入口。 */
    val baseUrl: String = "https://ws75.aptoide.com/api/7",
)

private const val AptoidePageSize = 20
private const val AptoideUserAgent =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

internal class AptoideApi(
    private val client: HttpClient,
    private val config: AptoideApiConfig = AptoideApiConfig(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(keyword: String, page: Int = 0): SearchPage {
        val query = keyword.trim()
        if (query.isBlank()) return SearchPage(emptyList(), false)
        val offset = page.coerceAtLeast(0) * AptoidePageSize
        val url = "${config.baseUrl}/apps/search/query=${query.encodeURLParameter()}" +
            "/limit=$AptoidePageSize/offset=$offset"
        val root = request(url) ?: return SearchPage(emptyList(), false)
        val datalist = root.aptObject("datalist")
        val entries = datalist.aptArray("list") ?: return SearchPage(emptyList(), false)
        val apps = entries.mapNotNull { toApp(it) }
        val total = datalist.aptLong("total")
        val hasMore = if (total > 0) offset + apps.size < total else entries.size >= AptoidePageSize
        return SearchPage(apps, hasMore)
    }

    /** 按包名或 Aptoide 站内 id 取单条详情（id 优先，取不到再回退包名）。 */
    suspend fun latest(appId: Long, packageName: String): MarketAppInfo? {
        if (appId > 0L) {
            val byId = request("${config.baseUrl}/getApp/app_id=$appId")?.aptDetailData()
            if (byId != null) return byId
        }
        if (packageName.isBlank()) return null
        return request("${config.baseUrl}/getApp/package_name=${packageName.encodeURLParameter()}")
            ?.aptDetailData()
    }

    suspend fun appDetail(appId: Long, packageName: String): AppDetail {
        val data = if (appId > 0L) {
            request("${config.baseUrl}/getApp/app_id=$appId")?.aptDetailDataRaw()
        } else {
            null
        } ?: request("${config.baseUrl}/getApp/package_name=${packageName.encodeURLParameter()}")?.aptDetailDataRaw()
                ?: throw MarketException("Aptoide 未收录该应用")

        val app = toApp(data) ?: throw MarketException("Aptoide 应用信息解析失败")
        val media = data.aptObject("media")
        val description = media.aptString("description")
        val screenshots = media.aptArray("screenshots").orEmpty().mapNotNull { shot ->
            val url = shot.aptString("url").ifBlank { shot.aptString("path") }
            if (url.isBlank()) null else AppScreenshot(
                url = url,
                orientation = if (shot.aptString("orientation").equals("landscape", true)) {
                    ScreenshotOrientation.LANDSCAPE
                } else {
                    ScreenshotOrientation.PORTRAIT
                },
            )
        }
        return AppDetail(
            app = app,
            brief = description.take(200),
            introduction = description,
            changeLog = "",
            category = data.aptObject("store").aptString("name"),
            ageClassification = data.aptObject("age").aptString("name"),
            downloadCount = data.aptObject("stats").aptObject("pdownloads").aptLong("total"),
            registrationNum = "",
            privacyUrl = data.aptObject("developer").aptString("privacy"),
            screenshots = screenshots,
            comments = emptyList(),
            sameDeveloperApps = emptyList(),
        )
    }

    /** 直链下载：file.path 是 Aptoide 官方 CDN 上的 APK。 */
    suspend fun downloadMeta(app: MarketAppInfo): DownloadMeta {
        val data = if (app.appId > 0L) {
            request("${config.baseUrl}/getApp/app_id=${app.appId}")?.aptDetailDataRaw()
        } else {
            null
        } ?: request("${config.baseUrl}/getApp/package_name=${app.packageName.encodeURLParameter()}")
            ?.aptDetailDataRaw()
            ?: throw MarketException("Aptoide 未收录该应用")
        val file = data.aptObject("file")
        val url = file.aptString("path")
        if (url.isBlank()) throw MarketException("Aptoide 未提供该版本的下载地址")
        val versionName = file.aptString("vername")
        val versionCode = file.aptLong("vercode")
        val size = file.aptLong("filesize")
        return DownloadMeta(
            appId = data.aptLong("id"),
            packageName = data.aptString("package").ifBlank { app.packageName },
            displayName = data.aptString("name").ifBlank { app.displayName },
            versionName = versionName,
            versionCode = versionCode,
            url = url,
            size = size,
            parts = listOf(DownloadPart(data.aptString("package"), "base", url, size, file.aptString("md5sum"))),
            icon = data.aptString("icon").ifBlank { app.icon },
            changeLog = data.aptObject("media").aptString("description").take(400),
            source = AppSource.APTOIDE,
        )
    }

    private suspend fun request(url: String): JsonObject? {
        val response = client.get(url) {
            header(HttpHeaders.UserAgent, AptoideUserAgent)
            header(HttpHeaders.Accept, "application/json")
        }
        if (!response.status.isSuccess()) {
            throw MarketException("Aptoide 请求失败：HTTP ${response.status.value}")
        }
        val text = response.bodyAsText()
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw MarketException("Aptoide 返回内容解析失败")
        val info = root.aptObject("info")
        val status = info.aptString("status")
        if (status.isNotBlank() && !status.equals("OK", true)) {
            throw MarketException("Aptoide 返回错误：${info.aptString("message").ifBlank { status }}")
        }
        return root
    }

    private fun JsonElement.aptDetailData(): MarketAppInfo? = aptDetailDataRaw()?.let { toApp(it) }

    private fun JsonElement.aptDetailDataRaw(): JsonObject? =
        aptObject("nodes")?.aptObject("meta")?.aptObject("data")

    private fun toApp(element: JsonElement): MarketAppInfo? {
        val obj = element as? JsonObject ?: return null
        val packageName = obj.aptString("package")
        if (packageName.isBlank()) return null
        val file = obj.aptObject("file")
        val versionCode = file.aptLong("vercode").let { if (it > 0L) it else obj.aptLong("vercode") }
        val versionName = file.aptString("vername").ifBlank { obj.aptString("vername") }
        val size = file.aptLong("filesize").let { if (it > 0L) it else obj.aptLong("size") }
        return MarketAppInfo(
            appId = obj.aptLong("id"),
            packageName = packageName,
            displayName = obj.aptString("name").ifBlank { packageName },
            publisherName = obj.aptObject("developer").aptString("name"),
            versionName = versionName,
            versionCode = versionCode,
            icon = obj.aptString("icon"),
            apkSize = size,
            ratingScore = obj.aptObject("stats").aptObject("rating").aptDouble("avg"),
            changeLog = "",
            openLink = obj.aptObject("urls").aptString("w").ifBlank {
                "https://en.aptoide.com/app/$packageName"
            },
            source = AppSource.APTOIDE,
        )
    }
}

private fun JsonElement?.aptObject(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

private fun JsonObject?.aptObject(key: String): JsonObject? = this?.get(key) as? JsonObject

private fun JsonElement?.aptArray(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray

private fun JsonObject?.aptArray(key: String): JsonArray? = this?.get(key) as? JsonArray

private fun JsonElement?.aptString(key: String): String =
    (this as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject?.aptString(key: String): String =
    this?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonElement?.aptLong(key: String): Long =
    (this as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

private fun JsonObject?.aptLong(key: String): Long =
    this?.get(key)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

private fun JsonObject?.aptDouble(key: String): Double =
    this?.get(key)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0