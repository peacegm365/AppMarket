package com.app.market.data.remote.coolapk

import com.app.market.domain.exception.MarketException
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.MarketAppInfo
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 酷安（CoolAPK）匿名接口客户端。
 *
 * 端点均需 X-App-Token 签名（见 [CoolapkAuth]）；抓包确认的调用形态：
 *  - 搜索 `GET /v6/search?type=apk&cat=&sort=default&searchValue=<kw>&page=<n>`
 *  - 详情 `POST /v6/apk/detail?id=<pkg>&installed=1`
 *  - 分区 `GET /v6/apk/categoryList?apkType=1|2`
 *  - 更新 `POST /v6/apk/checkUpdate?coolmarket_beta=0`（multipart 字段 pkgs）
 *  - 下载 `POST /v6/apk/download?pn=&aid=&vc=&extra=` → 302 → `POST /v6/apk/downloadVerify` 取最终地址
 */
@OptIn(ExperimentalTime::class)
internal class CoolapkApi(
    private val client: HttpClient,
    private val json: Json,
) {
    // 固定身份：Koin 的 singleOf 会解析全部构造参数，默认值不生效，故全部走常量
    private val deviceCode: String = CoolapkDeviceCode
    private val appVersion: String = CoolapkAppVersion
    private val versionCode: Int = CoolapkVersionCode

    private fun nowSeconds(): Long = Clock.System.now().epochSeconds

    private fun token(): String = CoolapkAuth.token(deviceCode, versionCode, nowSeconds())

    /** 全部请求头；抓包确认 X-App-Code / X-App-Supported 必须等于 versionCode，否则 403。 */
    private fun headers(): Map<String, String> = mapOf(
        "User-Agent" to CoolapkUserAgent,
        "X-Requested-With" to "XMLHttpRequest",
        "X-App-Id" to CoolapkPackage,
        "X-App-Token" to token(),
        "X-App-Version" to appVersion,
        "X-App-Code" to versionCode.toString(),
        "X-Api-Version" to "16",
        "X-App-Device" to deviceCode,
        "X-Dark-Mode" to "0",
        "X-App-Channel" to "coolapk",
        "X-App-Mode" to "universal",
        "X-App-Supported" to versionCode.toString(),
        "X-Sdk-Int" to "37",
        "X-Sdk-Locale" to "zh-CN",
    )

    suspend fun search(keyword: String, page: Int): List<CoolapkAppRecord> {
        val query = mapOf(
            "type" to "apk",
            "cat" to "",
            "sort" to "default",
            "searchValue" to keyword,
            "page" to (page.coerceAtLeast(1)).toString(),
        )
        val root = requestJsonObject("搜索") {
            client.get("$ApiBase/v6/search") {
                headers().forEach { (k, v) -> header(k, v) }
                url.parameters.appendAll(Parameters.build {
                    query.forEach { (k, v) -> append(k, v) }
                })
            }
        }
        return root.array("data").orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::parseApp) }
            .filter { it.packageName.isNotBlank() }
    }

    suspend fun detail(packageName: String): CoolapkAppRecord {
        val root = requestJsonObject("应用详情") {
            // 抓包确认真实客户端为 installed=1（installed=0 对已装应用会拿不到可更新记录）
            client.post("$ApiBase/v6/apk/detail?id=$packageName&installed=1") {
                headers().forEach { (k, v) -> header(k, v) }
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("")
            }
        }
        val data = root.obj("data") ?: throw MarketException("酷安未返回应用详情")
        return parseApp(data) ?: throw MarketException("酷安未收录该应用")
    }

    /** 批量更新检查；返回包名 -> 最新版本记录。value = 版本号 to 已装 APK 的 md5（无则空串）。 */
    suspend fun checkUpdates(installed: Map<String, Pair<Long, String>>): Map<String, CoolapkAppRecord> {
        if (installed.isEmpty()) return emptyMap()
        val pkgs = installed.entries.joinToString(",", prefix = "{", postfix = "}") { (pkg, info) ->
            "\"$pkg\":\"0,${info.first},${info.second.ifBlank { "0" }}\""
        }
        val root = requestJsonObject("更新检查") {
            // 抓包确认真实客户端为 multipart/form-data（单字段 pkgs，base64 后的 JSON）
            client.submitFormWithBinaryData(
                url = "$ApiBase/v6/apk/checkUpdate?coolmarket_beta=0",
                formData = formData { append("pkgs", base64Encode(pkgs.encodeToByteArray())) },
            ) {
                headers().forEach { (k, v) -> header(k, v) }
            }
        }
        return root.array("data").orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::parseApp) }
            .filter { it.packageName.isNotBlank() }
            .associateBy { it.packageName.lowercase() }
    }

    /**
     * 下载地址解析需要读 302 的 Location（extra 参数即 CDN 直链），
     * 必须关闭自动跳转；固定复用同一个 client 配置，避免每次请求新建。
     */
    private val noRedirectClient: HttpClient by lazy { client.config { followRedirects = false } }

    /**
     * 取最终下载地址。`/v6/apk/download` 返回 302 到 dl.coolapk.com，
     * 目标 URL 的 `extra` 参数就是 CDN 直链（URL 编码），无需跟随跳转即可取出。
     */
    suspend fun downloadUrl(app: CoolapkAppRecord): String {
        val endpoint =
            "$ApiBase/v6/apk/download?pn=${app.packageName}&aid=${app.appId}&vc=${app.versionCode}&extra="
        val response = noRedirectClient.post(endpoint) {
            headers().forEach { (k, v) -> header(k, v) }
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("nd=0&extraAnalysisData=null")
        }
        val location = response.headers["Location"]
        if (location == null) {
            // 酷安限流/风控时返回 200/403 + JSON 而非 302：把服务端 message 带出来，
            // 否则只看到「未返回下载地址（HTTP 200）」无法定位
            val message = runCatching {
                (json.parseToJsonElement(response.bodyAsText()) as? JsonObject)?.string("message").orEmpty()
            }.getOrDefault("")
            throw MarketException(
                "酷安未返回下载地址（HTTP ${response.status.value}）" +
                    if (message.isBlank()) "" else "：$message"
            )
        }
        val extra = location.substringAfter("extra=", "")
            .substringBefore("&")
            .let { decodeUrlComponent(it) }
        return extra.ifBlank { location }
    }

    private suspend fun requestJsonObject(operation: String, block: suspend () -> HttpResponse): JsonObject {
        val response = block()
        if (!response.status.isSuccess()) {
            throw MarketException("酷安 ${operation}请求失败（HTTP ${response.status.value}）")
        }
        val text = response.bodyAsText()
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: throw MarketException("酷安 ${operation}返回了无效数据")
        val status = root.int("status")
        if (status != null && status != 0 && root["data"] == null) {
            throw MarketException("酷安 ${operation}失败：${root.string("message").ifBlank { "status=$status" }}")
        }
        return root
    }


    /** 应用讨论/点评列表（酷安把两者都放在 apkCommentList 下，靠 type 区分）。 */
    suspend fun comments(appId: Long, page: Int = 1): List<CoolapkCommentRecord> {
        val inner = "#/feed/apkCommentList?isIncludeTop=1&withSortCard=1&id=$appId&sort=lastupdate_desc"
        val root = requestJsonObject("评论") {
            client.get("$ApiBase/v6/page/dataList") {
                headers().forEach { (k, v) -> header(k, v) }
                url.parameters.appendAll(Parameters.build {
                    append("url", inner)
                    append("title", "讨论")
                    append("subTitle", "")
                    append("page", page.toString())
                })
            }
        }
        return root.array("data").orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.string("entityType") == "feed" || it.string("message").isNotBlank() }
            .mapNotNull { obj ->
                val content = obj.string("message").ifBlank { obj.string("content") }
                if (content.isBlank()) return@mapNotNull null
                CoolapkCommentRecord(
                    userName = obj.obj("userInfo")?.string("username").orEmpty()
                        .ifBlank { obj.string("username") },
                    content = content,
                    score = obj.double("score"),
                )
            }
    }

    private fun parseApp(obj: JsonObject): CoolapkAppRecord? {
        val packageName = obj.string("apkname").ifBlank { obj.string("packageName") }
        if (packageName.isBlank()) return null
        return CoolapkAppRecord(
            appId = obj.long("id").takeIf { it > 0L } ?: obj.long("entityId"),
            packageName = packageName,
            displayName = obj.string("title").ifBlank { packageName },
            publisherName = obj.string("developername"),
            versionName = obj.string("apkversionname").ifBlank { obj.string("version") },
            versionCode = obj.long("apkversioncode"),
            icon = httpsIcon(obj.string("logo")),
            apkSize = obj.long("apklength"),
            md5 = obj.string("apkmd5").lowercase(),
            ratingScore = obj.double("score"),
            changeLog = plainText(obj.string("changelog")),
            description = plainText(obj.string("description")),
            category = obj.string("catName"),
            downloadCount = obj.long("downnum"),
            commentCount = obj.long("commentnum"),
            subtitle = plainText(obj.string("subtitle")),
            fullDescription = plainText(
                obj.string("introduce")
                    .ifBlank { obj.string("apkDescription") }
                    .ifBlank { obj.string("description") },
            ),
            changeHistory = plainText(obj.string("changehistory")),
            beianNo = obj.string("app_beian_no"),
            privacyUrl = obj.string("privacy_url"),
            romVersion = obj.string("apkRomVersion"),
            screenshots = obj.string("screenshots").split(',')
                .map(String::trim)
                .filter { it.startsWith("http") }
                .map(::httpsIcon),
        )
    }
}

/**
 * 酷安图床的明文地址处理：
 *  - pp.myapp.com 支持 https，升级后走加密通道；
 *  - image.coolapk.com 不支持 https（会返回 567），保留 http，
 *    并在 Android 网络安全配置里为该域名单独放行明文。
 */
private fun httpsIcon(url: String): String =
    if (url.startsWith("http://pp.myapp.com/")) "https://" + url.removePrefix("http://") else url

internal data class CoolapkCommentRecord(
    val userName: String,
    val content: String,
    val score: Double,
)

internal data class CoolapkAppRecord(
    val appId: Long,
    val packageName: String,
    val displayName: String,
    val publisherName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: String,
    val apkSize: Long,
    val md5: String,
    val ratingScore: Double,
    val changeLog: String,
    val description: String,
    val category: String,
    val downloadCount: Long,
    val commentCount: Long,
    val subtitle: String = "",
    val fullDescription: String = "",
    val changeHistory: String = "",
    val beianNo: String = "",
    val privacyUrl: String = "",
    val romVersion: String = "",
    val screenshots: List<String> = emptyList(),
)

internal fun CoolapkAppRecord.toMarketAppInfo(): MarketAppInfo = MarketAppInfo(
    appId = appId,
    packageName = packageName,
    displayName = displayName,
    publisherName = publisherName,
    versionName = versionName,
    versionCode = versionCode,
    icon = icon,
    apkSize = apkSize,
    ratingScore = ratingScore,
    changeLog = changeLog,
    source = AppSource.COOLAPK,
    category = category,
    downloadCount = downloadCount,
)

private const val ApiBase = "https://api.coolapk.com"
private const val CoolapkPackage = "com.coolapk.market"

/** 匿名设备码（固定串即可，服务端只校验格式与 token 绑定关系）。 */
internal const val CoolapkDeviceCode = "YTZmZTN5AjNzUWZ4YGNllDI7YTMw4SNwYDM2IjLBJDUDByODVkQLJlMwETNyAyOp1GZlJFI7kWbvFWaYByOgsDI7AyOid2b2dzajNkQ4wGW2F3Y2kTOyFDSndTY010M3MzS4Y0SSVFR"
internal const val CoolapkAppVersion = "16.7.1"
internal const val CoolapkVersionCode = 2611091

private val HtmlBreak = Regex("<br\\s*[^>]*>", RegexOption.IGNORE_CASE)
private val HtmlTag = Regex("<[^>]+>")

/** 去掉酷安文案里的 HTML 标签与实体，避免详情页排版错乱。 */
private fun plainText(value: String): String = value
    .replace(Regex("</p>\\s*<p>", RegexOption.IGNORE_CASE), "\n")
    .replace(HtmlBreak, "\n")
    .replace(HtmlTag, "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")
    .trim()

/** URL 解码（只处理百分号编码，足够覆盖下载地址里的 extra 参数）。 */
private fun decodeUrlComponent(value: String): String {
    if ('%' !in value && '+' !in value) return value
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        when (val char = value[index]) {
            '%' -> {
                if (index + 2 >= value.length) break
                val hex = value.substring(index + 1, index + 3)
                val parsed = hex.toIntOrNull(16) ?: break
                bytes.add(parsed.toByte())
                index += 3
            }
            '+' -> {
                bytes.add(' '.code.toByte())
                index += 1
            }
            else -> {
                bytes.addAll(char.toString().encodeToByteArray().toList())
                index += 1
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}
private const val CoolapkUserAgent =
    "Dalvik/2.1.0 (Linux; U; Android 17; 25102RKBEC Build/CP2A.260605.016) " +
            "(#Build; Redmi; 25102RKBEC; CP2A.260605.016; HyperOS_4.0; 4.0.6.0) +CoolMarket/16.7.1-2611091-universal"

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray
private fun JsonObject.string(name: String): String = primitive(name)?.contentOrNull.orEmpty()
private fun JsonObject.long(name: String): Long = primitive(name)?.longOrNull ?: 0L
private fun JsonObject.int(name: String): Int? = primitive(name)?.contentOrNull?.toIntOrNull()
private fun JsonObject.double(name: String): Double = primitive(name)?.doubleOrNull ?: 0.0
private fun JsonObject.bool(name: String): Boolean? = primitive(name)?.booleanOrNull
private fun JsonObject.primitive(name: String) = this[name]?.let {
    runCatching { it.jsonPrimitive }.getOrNull()
}