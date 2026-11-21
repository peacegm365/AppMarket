package com.app.market.data.remote.google.aurora

import com.app.market.data.remote.google.GoogleDeviceProperties
import com.app.market.data.remote.google.GoogleRegion
import com.app.market.domain.exception.MarketException

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Aurora GPlayAPI「网页通道」（GPL-3.0 移植）：`play.google.com/_/PlayStoreUi/data/batchexecute`，
 * Play 商店网页版的内部 RPC。无会话、无签名、无鉴权，每次调用一发即回。
 */
internal class AuroraWebClient(private val client: HttpClient) {
    suspend fun fetch(rpcRequests: List<String>): Map<String, Map<String, JsonElement>> {
        val response = client.post(UrlBatchexecute) {
            header("Origin", "https://play.google.com")
            // 设置-通用-设备信息接入：UA 拼设备指纹的 Build 字段（预置/从设备获取/自定义覆盖都生效）；
            // 语言按红线走 Accept-Language 头（不注入 hl）；商店地区选了具体国家才带 gl，
            // 「自动」不传——与 GooglePlayApi.applyRegion 同语义。
            header("User-Agent", webUserAgent())
            header("Accept-Language", "zh-CN,zh;q=0.9")
            if (regionCode.isNotEmpty()) url.parameters.append("gl", regionCode.uppercase())
            setBody(
                TextContent(
                    text = encodeFRequestBody(rpcRequests),
                    contentType = ContentType("application", "x-www-form-urlencoded;charset=utf-8"),
                )
            )
        }
        if (!response.status.isSuccess()) {
            throw MarketException("Aurora Google Play HTTP ${response.status.value}")
        }
        return AuroraRpcFrames.parse(response.bodyAsText()).also {
            if (it.isEmpty()) throw MarketException("Aurora Google Play 未返回有效的 RPC 响应")
        }
    }
}

private const val UrlBatchexecute = "https://play.google.com/_/PlayStoreUi/data/batchexecute"

/** 商店地区（设置-通用-设备信息）：`auto` 跟出口 IP 不传 gl；其余为 ISO-3166 国家码。 */
private val regionCode: String
    get() = GoogleRegion.trim().lowercase().takeIf { it.isNotEmpty() && it != "auto" } ?: ""

/** 网页通道 UA：Chrome 移动外壳 + 设备指纹 Build 字段，随「设备信息」设置实时变化。 */
private fun webUserAgent(): String {
    val props = GoogleDeviceProperties
    val release = props["Build.VERSION.RELEASE"]?.takeIf { it.isNotBlank() } ?: "15"
    val model = props["Build.MODEL"]?.takeIf { it.isNotBlank() } ?: "Pixel 9a"
    val build = props["Build.ID"]?.takeIf { it.isNotBlank() } ?: "BD4A.250405.003"
    return "Mozilla/5.0 (Linux; Android $release; $model Build/$build) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
}

/**
 * f.req 编码：Play 只接受这一种空白——载荷前换行+16 空格，收尾 `]]` 前换行+12 空格。
 * 每条 RPC 先 form 编码再逗号拼接（与 java.net.URLEncoder 语义一致：空格→+）。
 */
private fun encodeFRequestBody(rpcRequests: List<String>): String {
    val encoded = rpcRequests.joinToString(separator = ",") { formEncode(it) }
    return "f.req=[[\n" + " ".repeat(16) + encoded + "\n" + " ".repeat(12) + "]]"
}

internal fun formEncode(value: String): String {
    val hex = "0123456789ABCDEF"
    val out = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        when {
            ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' ||
                ch == '-' || ch == '_' || ch == '.' || ch == '*' -> out.append(ch)
            c == 0x20 -> out.append('+')
            else -> out.append('%').append(hex[c shr 4]).append(hex[c and 15])
        }
    }
    return out.toString()
}

/** 解析 batchexecute 响应（行向帧：`[\"wrb.fr\"...]` 行内第 2 位是二次编码的结果 JSON）。 */
internal object AuroraRpcFrames {
    private const val FrameMarker = "wrb.fr"
    private const val FrameLinePrefix = "[[\"$FrameMarker"

    fun parse(body: String): Map<String, Map<String, JsonElement>> {
        val result = mutableMapOf<String, MutableMap<String, JsonElement>>()
        for (line in body.lineSequence()) {
            if (!line.startsWith(FrameLinePrefix)) continue
            val elements = Json.parseToJsonElement(line) as? JsonArray ?: continue
            val fromLine = mutableMapOf<String, MutableMap<String, JsonElement>>()
            for (element in elements) {
                val frame = element as? JsonArray ?: continue
                addFrame(frame, fromLine)
            }
            for ((tag, byKey) in fromLine) {
                val merged = result[tag] ?: mutableMapOf<String, JsonElement>().also { result[tag] = it }
                for ((key, payload) in byKey) {
                    if (!merged.containsKey(key)) merged[key] = payload
                }
            }
        }
        return result
    }

    private fun addFrame(frame: JsonArray, result: MutableMap<String, MutableMap<String, JsonElement>>) {
        if ((frame.getOrNull(0) as? JsonPrimitive)?.takeIf { it.isString }?.content != FrameMarker) return
        val tagAndKey = (frame.getOrNull(6) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return
        val separator = tagAndKey.indexOf('@')
        if (separator < 0) return
        val tag = tagAndKey.substring(0, separator)
        val key = tagAndKey.substring(separator + 1)
        val encoded = (frame.getOrNull(2) as? JsonPrimitive)?.takeIf { it.isString }?.content
        val payload = if (encoded.isNullOrEmpty() || encoded == "null") {
            JsonArray(emptyList())
        } else {
            Json.parseToJsonElement(encoded)
        }
        val byKey = result[tag] ?: mutableMapOf<String, JsonElement>().also { result[tag] = it }
        byKey[key] = payload
    }
}

/** batchexecute 载荷是参差数组，按下标/键逐级下钻，任何一级不匹配即 null。 */
internal fun JsonElement?.dig(vararg keys: Any): JsonElement? {
    var current: JsonElement? = this
    for (key in keys) {
        current = when {
            current is JsonArray && key is Int -> current.getOrNull(key)
            current is JsonObject && key is String -> current[key]
            else -> return null
        }
    }
    return current
}

internal fun JsonElement?.digString(vararg keys: Any): String =
    (dig(*keys) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: ""

internal fun JsonElement?.digLong(vararg keys: Any): Long =
    (dig(*keys) as? JsonPrimitive)?.longOrNull ?: 0L

/** Play 可能下发 `5.0E9` 这类带小数点的数字（安装数/评分），整型解析会读成 0。 */
internal fun JsonElement?.digDouble(vararg keys: Any): Double =
    (dig(*keys) as? JsonPrimitive)?.doubleOrNull ?: 0.0

internal fun JsonElement?.digList(vararg keys: Any): List<JsonElement> =
    (dig(*keys) as? JsonArray) ?: emptyList()

/** RPC 条目双层 JSON 的转义（`["<rpc>","<参数 JSON>",null,"<tag>@<key>"]`）。 */
internal object AuroraRpcStrings {
    fun argument(value: String): String = content(content(value))

    fun outer(value: String): String = JsonPrimitive(value).toString()

    private fun content(value: String): String {
        val literal = JsonPrimitive(value).toString()
        return literal.substring(1, literal.length - 1)
    }
}

/** 首页 `lGYRle` / 翻页 `qnKhOb`；两者都按 query 作 key。 */
internal object AuroraSearchRpc {
    const val TAG: String = "AuroraSearchRpc"

    fun build(query: String, nextPageToken: String = ""): String =
        if (nextPageToken.isNotEmpty()) {
            """["qnKhOb","[[null,${AuroraRpcTokens.SEARCH_TOKEN},null,\"${AuroraRpcStrings.argument(nextPageToken)}\"]]",null,${AuroraRpcStrings.outer("$TAG@$query")}]"""
        } else {
            """["lGYRle","[[[],${AuroraRpcTokens.SEARCH_TOKEN},[\"${AuroraRpcStrings.argument(query)}\"],4,[null,1],null,null,[]]]",null,${AuroraRpcStrings.outer("$TAG@$query")}]"""
        }
}

/** 批量应用元数据 `Ws7gDc`。 */
internal object AuroraMetadataRpc {
    const val TAG: String = "AuroraMetadataRpc"

    fun build(packageName: String): String =
        """["Ws7gDc","[${AuroraRpcTokens.METADATA_TOKEN},[[\"${AuroraRpcStrings.argument(packageName)}\",7]]]",null,${AuroraRpcStrings.outer("$TAG@$packageName")}]"""
}

/** 榜单 `vyAe2`（GPlayAPI TopChartsBuilder）：chartType 去 `apps_` 前缀，category 如 APPLICATION/GAME。 */
internal object AuroraChartRpc {
    const val TAG: String = "AuroraChartRpc"

    fun build(category: String, chartType: String): String =
        """["vyAe2","[${AuroraRpcTokens.TOP_CHARTS_TOKEN},[2,\"${AuroraRpcStrings.argument(chartType)}\",\"${AuroraRpcStrings.argument(category)}\"]]]",null,${AuroraRpcStrings.outer("$TAG@$category$chartType")}]"""
}