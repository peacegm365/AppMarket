package com.app.market.data.remote.google

import com.app.market.domain.diagnostic.AppLog
import com.app.market.data.platform.gunzip
import com.app.market.domain.exception.MarketException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

/**
 * Google Play fdfe 匿名客户端（Aurora 分发器 + gplayapi 同款协议，全部端点已实测）：
 *  - 鉴权 `POST https://auroraoss.com/api/auth`（设备指纹 JSON → OAuth token + gsfId + 设备令牌）
 *  - 批量详情/更新检查 `POST https://play.googleapis.com/fdfe/getItems`（X-PS-RH 签名 + field mask）
 *  - 搜索/详情 `GET https://android.clients.google.com/fdfe/search|details`（X-DFE-* 登录通道头）
 *  - 下载 `GET https://play.googleapis.com/fdfe/delivery?doc=&vc=&ot=1`（匿名头）
 */
@OptIn(ExperimentalTime::class)
internal class GooglePlayApi(
    private val client: HttpClient,
    private val json: Json,
    private val restoreSession: (() -> String?)? = null,
    private val persistSession: ((String) -> Unit)? = null,
) {
    /** Aurora 网页通道（batchexecute）：搜索/详情，免鉴权。 */
    internal val webApi = com.app.market.data.remote.google.aurora.AuroraWebApi(client)
    private var cachedSession: GooglePlaySession? = null
    private var cachedAt: Long = 0L
    /** 当前缓存 token 是用哪个地区配置签发的（地区变更后需要重新鉴权）。 */
    private var sessionRegionGeneration: Int = -1


    private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
    private val sessionMutex = Mutex()

    private fun g(key: String): String = GoogleDeviceProperties[key].orEmpty()

    /**
     * 商店地区：设置里选了具体国家就带上 `gl`，选「自动」则不传——
     * 此时 Google 按 token 绑定的地区 / 出口 IP 判定。
     */
    private val regionCode: String
        get() = GoogleRegion.trim().lowercase().takeIf { it.isNotEmpty() && it != "auto" } ?: ""

    private fun io.ktor.client.request.HttpRequestBuilder.applyRegion(appendGl: Boolean = true) {
        header("Accept-Language", "zh-CN,zh;q=0.9")
        if (appendGl && regionCode.isNotEmpty()) url.parameters.append("gl", regionCode.uppercase())
    }

    /** 分发器签发的会话；token 有效期数小时，缓存 6 小时，401 时强制刷新。加锁避免并发双取。 */
    private suspend fun session(forceRefresh: Boolean = false): GooglePlaySession = sessionMutex.withLock {
        val now = Clock.System.now().epochSeconds
        val current = cachedSession
        // 地区变了就必须换 token：token 里绑定的是签发时的国家与出口 IP，
        // 只改请求参数不改身份，Play 仍按旧国家返回（表现为「地区设置无效」）。
        val regionStale = current != null && sessionRegionGeneration != GoogleRegionGeneration
        if (regionStale) {
            com.app.market.domain.diagnostic.AppLog.log(
                "GoogleAuth: 地区变更（gen=$GoogleRegionGeneration），丢弃旧 token 重新鉴权",
            )
            cachedSession = null
            cachedAt = 0L
            runCatching { persistSession?.invoke("") }
        }
        if (!forceRefresh && cachedSession != null && now - cachedAt < 6 * 3600L) {
            return@withLock cachedSession!!
        }
        // 持久化会话：跨冷启动固定同一 gsfId（设备身份）——Google 按身份过滤结果集，
        // 每次重新鉴权换一个身份就会“Steam 时有时无、搜索每次不一样”。
        if (!forceRefresh && current == null) {
            val restored = restoreSession?.invoke()
            if (restored != null) {
                runCatching {
                    val o = json.parseToJsonElement(restored) as? JsonObject
                    if (o != null) {
                        val s = GooglePlaySession(
                            authToken = o["authToken"]?.jsonPrimitive?.content.orEmpty(),
                            gsfId = o["gsfId"]?.jsonPrimitive?.content.orEmpty(),
                            dfeCookie = o["dfeCookie"]?.jsonPrimitive?.content.orEmpty(),
                            deviceConfigToken = o["deviceConfigToken"]?.jsonPrimitive?.content.orEmpty(),
                            checkinConsistencyToken = o["checkinConsistencyToken"]?.jsonPrimitive?.content.orEmpty(),
                        )
                        if (s.authToken.isNotBlank() && s.gsfId.isNotBlank()) {
                            cachedSession = s
                            cachedAt = now
                            sessionRegionGeneration = GoogleRegionGeneration
                            return@withLock s
                        }
                    }
                }
            }
        }
        val payload = buildJsonObject {
            GoogleDeviceProperties.forEach { (key, value) -> put(key, value) }
        }.toString()
        val authResponse = client.post(DispenserUrl) {
            header("User-Agent", "com.aurora.store-4.6.2-48")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val text = authResponse.bodyAsText()
        // 分发器被拦时会直接回 HTML（Cloudflare/同意页）。原来直接丢给 json 解析，
        // 报出的是「Unexpected JSON token ... <!DOCTYPE html>」这种看不懂的信息。
        if (!authResponse.status.isSuccess() || !text.trimStart().startsWith("{")) {
            com.app.market.domain.diagnostic.AppLog.log(
                "GoogleAuth bad: HTTP ${authResponse.status.value} body=${text.take(60).replace('\n', ' ')}",
            )
            throw MarketException("Google Play 鉴权被拦截（HTTP ${authResponse.status.value}）")
        }
        val obj = json.parseToJsonElement(text) as? JsonObject
            ?: throw MarketException("Google Play 鉴权返回了无效数据")
        val session = GooglePlaySession(
            authToken = obj.string("authToken"),
            gsfId = obj.string("gsfId"),
            dfeCookie = obj.string("dfeCookie"),
            deviceConfigToken = obj.string("deviceConfigToken"),
            checkinConsistencyToken = obj.string("deviceCheckInConsistencyToken"),
        )
        if (session.authToken.isBlank() || session.gsfId.isBlank()) {
            throw MarketException("Google Play 鉴权失败：分发器未签发 token")
        }
        cachedSession = session
        cachedAt = now
        sessionRegionGeneration = GoogleRegionGeneration
        persistSession?.invoke(buildJsonObject {
            put("authToken", session.authToken)
            put("gsfId", session.gsfId)
            put("dfeCookie", session.dfeCookie)
            put("deviceConfigToken", session.deviceConfigToken)
            put("checkinConsistencyToken", session.checkinConsistencyToken)
        }.toString())
        com.app.market.domain.diagnostic.AppLog.log("GoogleAuth ok: gsfId=${session.gsfId.length}h cookie=${session.dfeCookie.length}")
        session
    }

    // ---------------- 请求头 ----------------

    /** 登录通道头（android.clients.google.com 的 search/details 用）。 */
    private fun dfeHeaders(s: GooglePlaySession): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${s.authToken}")
        put(
            "User-Agent",
            // 实测：UA 携带真实机型字段（device/model/hardware/product/buildId 等）时，
            // Google 会把该会话路由到「设备/电视浏览」结果集，对 chat 这类词只回设备
            // 推广位、不含应用列表（docs=0，App 端表现为「搜不出任何东西」）。
            // 去掉这些字段后返回完整应用搜索结果。仅保留版本号与平台信息。
            "Android-Finsky/${g("Vending.versionString")} (" +
                "api=3,versionCode=${g("Vending.version")}," +
                "isWideScreen=0,supportedAbis=${g("Platforms").replace(',', ';')})",
        )
        put("X-DFE-Device-Id", s.gsfId)
        put("Accept-Language", "zh-CN")
        put("X-DFE-Encoded-Targets", EncodedTargets)
        put("X-DFE-Phenotype", Phenotype)
        put("X-DFE-Client-Id", "am-android-google")
        put("X-DFE-Network-Type", "4")
        put("X-DFE-Content-Filters", "")
        put("X-Limit-Ad-Tracking-Enabled", "false")
        put("X-Ad-Id", "")
        put("X-DFE-UserLanguages", "zh_CN")
        put("X-DFE-Request-Params", "timeoutMs=4000")
        if (s.checkinConsistencyToken.isNotBlank()) {
            put("X-DFE-Device-Checkin-Consistency-Token", s.checkinConsistencyToken)
        }
        if (s.deviceConfigToken.isNotBlank()) put("X-DFE-Device-Config-Token", s.deviceConfigToken)
        if (s.dfeCookie.isNotBlank()) put("X-DFE-Cookie", s.dfeCookie)
        val mcc = g("SimOperator")
        if (mcc.isNotBlank()) put("X-DFE-MCCMNC", mcc)
    }

    /** 匿名通道头（play.googleapis.com 的 getItems/delivery 用）。 */
    private fun anonHeaders(s: GooglePlaySession): Map<String, String> = mapOf(
        "User-Agent" to
            "Dalvik/2.1.0 (Linux; U; Android ${g("Build.VERSION.SDK_INT")}; " +
            "${g("Build.BRAND")} ${g("Build.MODEL")} Build/${g("Build.ID")})",
        "accept-language" to "zh-CN",
        "x-dfe-userlanguages" to "zh-CN",
        "x-ps-rh" to psrh(s),
    )

    /** X-PS-RH：gzip64(RequestHeader proto)，对照 gplayapi PsRhSigner 逐字段重建。 */
    private fun psrh(s: GooglePlaySession): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val ts = PbW.varint(1, now / 1000) + PbW.varint(2, (now % 1000) * 1_000_000L)
        val tsContainer = PbW.msg(6, PbW.msg(1, PbW.msg(1, ts)) + PbW.msg(2, ts))
        val entry = PbW.str(1, "US") + PbW.msg(2, ts)
        val locality = PbW.varint(2, 1) + PbW.varint(3, 2) + PbW.str(4, "") +
            PbW.msg(8, entry) + PbW.msg(9, entry) + PbW.varint(11, 0)
        val androidMeta = PbW.varint(1, g("Build.VERSION.SDK_INT").toLongOrNull() ?: 34L) +
            PbW.str(2, g("Build.ID")) + PbW.str(3, g("Build.VERSION.RELEASE")) +
            PbW.varint(4, g("Vending.version").toLongOrNull() ?: 82201710L)
        val deviceMeta = PbW.msg(1, androidMeta) + PbW.msg(2, ByteArray(0)) + PbW.varint(3, 1)
        val userAgent = PbW.str(1, g("Build.DEVICE")) + PbW.str(2, g("Build.HARDWARE")) +
            PbW.str(3, g("Build.MODEL")) + PbW.str(4, "Finsky/${g("Vending.versionString")}") +
            PbW.str(5, g("Build.PRODUCT")) + PbW.varint(6, s.gsfId.toLongOrNull(16) ?: 0L) +
            PbW.str(7, g("Build.FINGERPRINT"))
        val uuid = PbW.str(1, randomUuid()) + PbW.varint(2, 2)
        val header = PbW.msg(1, PbW.str(1, gzipBase64(tsContainer))) +
            PbW.msg(11, PbW.str(1, gzipBase64(locality))) +
            PbW.msg(12, PbW.varint(1, 5)) +
            PbW.str(14, "") +
            PbW.msg(20, deviceMeta) +
            PbW.msg(21, userAgent) +
            PbW.msg(27, uuid)
        return gzipBase64(header)
    }

    /**
     * x-dfe-item-field-mask：FieldMask{3: RegionalMetadata 位集, 4: GenericMetadata 位集}，
     * 位号 = proto 字段号，标准 Base64 无 padding（不可用 urlsafe）。
     * slim=true 只请求版本/体积（搜索卡补数据用）：实测单条 23KB→389B、整响应 30 倍差距，
     * 版本/体积一字不差（已拿真实响应核对），搜索提速靠这个。
     */
    private fun itemFieldMask(slim: Boolean = false): String {
        val shared = ByteArray(17)
        val specific = ByteArray(17)
        fun set(arr: ByteArray, bit: Int) {
            arr[bit / 8] = (arr[bit / 8].toInt() or (1 shl (bit % 8))).toByte()
        }
        if (slim) {
            // RegionalMetadata：1=包名、3=GenericMetadata 容器
            set(shared, 1); set(shared, 3)
            // GenericMetadata：2=versionInfo、27/47=ApkMetadataWrapper（total_size/package_size）
            set(specific, 2); set(specific, 27); set(specific, 47)
        } else {
            for (bit in 1..128) {
                set(shared, bit)
                set(specific, bit)
            }
        }
        return B64.encode(PbW.msg(3, shared) + PbW.msg(4, specific), urlSafe = false, pad = false)
    }

    // ---------------- 端点 ----------------

    /** `/fdfe/getItems`：一次批量返回各包的版本/标题/图标（响应为裸 ItemResponse）。按 20 个一批。 */
suspend fun getItems(packages: List<String>, fillSizes: Boolean = true, strict: Boolean = false, slim: Boolean = false): List<GoogleAppRecord> {
        val unique = packages.distinct()
        if (unique.isEmpty()) return emptyList()
        // 并行分片：Play 批量接口每片 20 个包名，24 条首屏就要 2 片，
        // 串行等于白等一个 RTT（实测两片串行 5.4s → 并行约 1.5s）。
        val chunks = unique.chunked(20)
        val perChunk = coroutineScope {
            chunks.map { chunk ->
                async {
                    if (strict) {
                        fetchItemsChunk(chunk, fillSizes, slim)
                    } else {
                        try {
                            fetchItemsChunk(chunk, fillSizes, slim)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            com.app.market.domain.diagnostic.AppLog.log("GoogleItems chunk fail: " + e.message)
                            emptyList<GoogleAppRecord>()
                        }
                    }
                }
            }.awaitAll()
        }
        return perChunk.flatten()
    }

    /** 单片（≤20 个包名）批量取详情；fillSizes=false 时跳过串行体积补齐（搜索首屏不值得等它）。 */
    private suspend fun fetchItemsChunk(chunk: List<String>, fillSizes: Boolean, slim: Boolean = false): List<GoogleAppRecord> {
        val results = ArrayList<GoogleAppRecord>()
        run {
            val s = session()
            val body = chunk
                .map { pkg -> PbW.msg(2, PbW.msg(1, PbW.msg(1, PbW.str(1, pkg)))) }
                .fold(ByteArray(0)) { acc, bytes -> acc + bytes }
            val bytes = try {
                postItems(s, body, slim)
            } catch (e: Exception) {
                // 仅 401（token 过期）才换 token 重试，其余直接抛（避免无谓的二次鉴权拖慢速度）
                if ("401" in (e.message ?: "")) postItems(session(forceRefresh = true), body, slim) else throw e
            }
            fun parseAll(raw: ByteArray): List<GoogleAppRecord> {
                val root = decodeBody(raw)
                val items = root.msgs(11)
                    .ifEmpty { root.flatMap { it.children }.filter { it.tag == 11 } }
                return items.mapNotNull { parseItem(it) }
            }
            var parsed = parseAll(bytes)
            // Play 偶发「降级」响应（无版本号/体积，实测随机出现）：整片降级就原样重取一次再解析
            if (parsed.isNotEmpty() && parsed.all { it.versionCode <= 0L && it.fileSize <= 0L }) {
                val again = try {
                    parseAll(postItems(s, body, slim))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (again != null && again.any { it.versionCode > 0L || it.fileSize > 0L }) parsed = again
            }
            if (parsed.isEmpty()) {
                // 批量拿不到条目时留存响应头部，离线即可定案
                com.app.market.domain.diagnostic.AppLog.log(
                    "GoogleItems empty: req=${chunk.size} bytes=${bytes.size} head=${headHex(bytes)}",
                )
            } else {
                if (parsed.all { it.icon.isBlank() }) {
                    val first = parsed.first()
                    com.app.market.domain.diagnostic.AppLog.log(
                        "GoogleItemsIconMiss: pkg=${first.packageName} title=${first.title.take(20)}",
                    )
                }
            }
            // 批量响应里 size 缺失时用网页详情页补齐。
            // 注意：必须串行且限量——并发一堆网页请求会触发 Play 风控，把后续 fdfe 请求打成 401。
            var filled = parsed
            val sizeDeadline = nowMillis() + SizeFillBudgetMs
            val missing = parsed.filter { it.fileSize <= 0L }.take(SizeFillMaxLookups)
            if (fillSizes && missing.isNotEmpty()) {
                val sizes = HashMap<String, Long>(missing.size)
                coroutineScope {
                    val gate = Semaphore(3)
                    missing.map { rec ->
                        async {
                            if (nowMillis() > sizeDeadline) return@async
                            gate.withPermit {
                                val delivered = runCatching {
                                    if (rec.versionCode > 0L) delivery(rec.packageName, rec.versionCode).size else 0L
                                }.getOrDefault(0L)
                                if (delivered > 0L) sizes[rec.packageName] = delivered
                            }
                        }
                    }.awaitAll()
                }
                filled = parsed.map { rec ->
                    val size = sizes[rec.packageName] ?: 0L
                    if (rec.fileSize <= 0L && size > 0L) rec.copy(fileSize = size) else rec
                }
            }
            results += filled
        }
        return results
    }

    /** 当前身份 ID（gsfId）：内存会话优先，其次持久化。 */
    fun currentGsfId(): String? {
        cachedSession?.gsfId?.takeIf { it.isNotBlank() }?.let { return it }
        val restored = restoreSession?.invoke() ?: return null
        return runCatching {
            (json.parseToJsonElement(restored) as? JsonObject)
                ?.get("gsfId")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** 手动设置身份 ID：只改 gsfId（令牌仍用当前的）；若失效会被重新鉴权覆盖。 */
    fun setGsfId(id: String) {
        val clean = id.trim()
        cachedSession = null
        cachedAt = 0L
        val restored = restoreSession?.invoke()
        val updated = runCatching {
            val o = (json.parseToJsonElement(restored ?: "{}") as? JsonObject) ?: JsonObject(emptyMap())
            buildJsonObject {
                o.forEach { (k, v) -> if (k != "gsfId") put(k, v) }
                put("gsfId", clean)
            }.toString()
        }.getOrDefault("{\"gsfId\":\"$clean\"}")
        persistSession?.invoke(updated)
    }

    /** 重置身份：丢弃当前 gsfId/会话并清持久化，下次请求重新随机一个身份。 */
    fun resetSession() {
        cachedSession = null
        cachedAt = 0L
        runCatching { persistSession?.invoke("") }
    }

    /** 预建鉴权会话：App 启动后台握手，首次搜索/更新零等待。 */
    suspend fun warmUp() {
        runCatching { session() }
    }

    private suspend fun postItems(s: GooglePlaySession, body: ByteArray, slim: Boolean = false): ByteArray {
        val response = client.post("$AnonymousBase/fdfe/getItems") {
            anonHeaders(s).forEach { (k, v) -> header(k, v) }
            if (regionCode.isNotEmpty()) url.parameters.append("gl", regionCode.uppercase())
            header("Authorization", "Bearer ${s.authToken}")
            header("x-dfe-item-field-mask", itemFieldMask(slim))
            contentType(ContentType.parse("application/x-protobuf"))
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            throw MarketException("Google Play 批量详情失败（HTTP ${response.status.value}）")
        }
        return response.readBytes()
    }

    // 搜索和分类列表直接解析 Play 网页；版本、下载与更新仍使用 Play 接口。

    suspend fun search(keyword: String, page: Int): List<GoogleAppRecord> {
        if (page > 0) return emptyList()
        val response = client.get("https://play.google.com/store/search") {
            url.parameters.append("q", keyword)
            url.parameters.append("c", "apps")
            applyRegion()
            header("User-Agent", WebUserAgent)
        }
        if (!response.status.isSuccess()) throw MarketException("Google Play 网页搜索失败（HTTP ${response.status.value}）")
        val results = GoogleHtmlCards.parse(response.bodyAsText())
        if (results.isEmpty()) throw MarketException("Google Play 网页未返回可解析的搜索结果")
        return results
    }

    private val webCategoryCache = mutableMapOf<String, List<GoogleAppRecord>>()

    suspend fun webCategories(games: Boolean): List<com.app.market.domain.model.market.CategoryOption> {
        val kind = if (games) "games" else "apps"
        val response = client.get("https://play.google.com/store/$kind") {
            applyRegion()
            header("User-Agent", WebUserAgent)
        }
        if (!response.status.isSuccess()) throw MarketException("Google Play 分类获取失败（HTTP ${response.status.value}）")
        val options = GoogleHtmlCards.collections(response.bodyAsText())
        if (options.isEmpty()) throw MarketException("Google Play 网页未返回可解析的分类")
        return options.map { (path, name) -> com.app.market.domain.model.market.CategoryOption(path, name) }
    }

    suspend fun webCategory(path: String, forceRefresh: Boolean): List<GoogleAppRecord> {
        require(path.startsWith("/store/apps/collection/cluster?gsr=") && !path.contains('#') && !path.contains('&')) { "Google Play 分类链接无效" }
        val key = "$regionCode|$path"
        if (!forceRefresh) webCategoryCache[key]?.let { return it }
        val gsr = path.substringAfter("?gsr=")
        val response = client.get("https://play.google.com/store/apps/collection/cluster") {
            url.encodedParameters.append("gsr", gsr)
            applyRegion()
            header("User-Agent", WebUserAgent)
        }
        if (!response.status.isSuccess()) throw MarketException("Google Play 分类加载失败（HTTP ${response.status.value}）")
        val items = GoogleHtmlCards.parse(response.bodyAsText())
        if (items.isEmpty()) throw MarketException("Google Play 分类未返回可解析的应用")
        webCategoryCache[key] = items
        return items
    }

    /** 本机指纹不可用：切回预置指纹并重置身份，让下一次请求用新 gsfId 重新鉴权。 */
    private fun switchToPresetDeviceProps(reason: String) {
        GoogleDevicePropsPresetFallback = true
        resetSession()
        com.app.market.domain.diagnostic.AppLog.log(
            "GoogleDeviceProps: 本机指纹不可用，已回退预置指纹（$reason）",
        )
    }

    suspend fun details(packageName: String): GoogleAppRecord {
        if (GoogleDeviceSourceFromSystem && !GoogleDevicePropsPresetFallback) {
            // 先探一次：本机指纹如果连详情都拿不到，切回预置指纹后继续走正常流程（不递归）
            val probed = runCatching { getItems(listOf(packageName)).firstOrNull() }.getOrNull()
            if (probed == null) switchToPresetDeviceProps("details $packageName")
        }
        val authed = runCatching { authedDetails(packageName) }
            .onFailure {
                com.app.market.domain.diagnostic.AppLog.log(
                    "GoogleDetails authed fail: ${it.message?.take(120)}",
                )
            }
            .getOrNull()
        if (authed != null) return authed
        // 兜底：匿名批量接口（不需要登录通道 token 的刷新）
        val item = runCatching { getItems(listOf(packageName)).firstOrNull() }
            .onFailure {
                com.app.market.domain.diagnostic.AppLog.log(
                    "GoogleDetails items fail: ${it.message?.take(120)}",
                )
            }
            .getOrNull() ?: throw MarketException("Google Play 未收录该应用")
        val web = if (item.description.isBlank() || item.recentChanges.isBlank() || item.fileSize <= 0L) {
            webDetails(packageName)
        } else {
            WebDetails()
        }
        return item.copy(
            title = item.title.ifBlank { packageName },
            description = item.description.ifBlank { web.description },
            recentChanges = item.recentChanges.ifBlank { web.recentChanges },
            fileSize = if (item.fileSize > 0L) item.fileSize else web.size,
        )
    }

    /** 登录通道详情（`/fdfe/details`）；被风控返回网页时抛异常，交给 [details] 兜底。 */
    private suspend fun authedDetails(packageName: String): GoogleAppRecord {
        suspend fun fetch(s: GooglePlaySession) = client.get("$AuthedBase/fdfe/details") {
            dfeHeaders(s).forEach { (k, v) -> header(k, v) }
            url.parameters.append("doc", packageName)
            if (regionCode.isNotEmpty()) url.parameters.append("gl", regionCode.uppercase())
        }
        var response = fetch(session())
        // 401 = token 被风控/过期：换新 token 再试一次；刷新本身失败也不要让它顶掉原响应
        if (response.status.value == 401) {
            response = runCatching { fetch(session(forceRefresh = true)) }.getOrElse { response }
        }
        if (!response.status.isSuccess()) {
            throw MarketException("Google Play 详情失败（HTTP ${response.status.value}）")
        }
        val bytes = response.readBytes()
        val head = bytes.decodeToString(0, minOf(bytes.size, 24)).trim().lowercase()
        if (head.startsWith("<")) {
            throw MarketException("Google Play 详情被拦截（返回网页）")
        }
        val docs = ArrayList<GoogleAppRecord>()
        collectDocs(decodeBody(bytes), docs)
        val doc = docs.firstOrNull() ?: throw MarketException("Google Play 未收录该应用")
        // 图标/截图由 getItems 补齐（失败不阻塞详情文本）
        val item = runCatching { getItems(listOf(doc.packageName)) }
            .getOrDefault(emptyList())
            .firstOrNull()
        return mergeItemData(doc, item)
    }

    /** Play 网页详情页补的文本/大小（fdfe 全挂时才有值）。 */
    private data class WebDetails(
        val description: String = "",
        val recentChanges: String = "",
        val size: Long = 0L,
    )

    /** Play 网页详情页：只取简介与大小，取不到就是空（不抛异常）。 */
    private suspend fun webDetails(packageName: String): WebDetails = runCatching {
        val html = client.get("https://play.google.com/store/apps/details") {
            url.parameters.append("id", packageName)
            applyRegion()
            header("User-Agent", WebUserAgent)
        }.bodyAsText()
        val desc = Regex("data-g-id=\"description\"[^>]*>(.*?)</div>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.getOrNull(1)
            ?.let(::stripHtml).orEmpty()
        WebDetails(description = desc, size = parseWebSize(html))
    }.getOrDefault(WebDetails())

    /** 去标签/反转义，给网页兜底文本用。 */
    private fun stripHtml(raw: String): String = raw
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", 34.toChar().toString())
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()

    /** Play 网页详情页兜底取 APK 大小（fdfe 响应里 size 常缺失）。 */
    private suspend fun webAppSize(packageName: String): Long = runCatching {
        val html = client.get("https://play.google.com/store/apps/details") {
            url.parameters.append("id", packageName)
            applyRegion()
            header("User-Agent", WebUserAgent)
        }.bodyAsText()
        parseWebSize(html)
    }.getOrDefault(0L)

    /** 从网页 HTML 取大小：优先页面文本节点里的「12 MB」，否则退到任意处第一处匹配。 */
    private fun parseWebSize(html: String): Long {
        val textNode = Regex(">([0-9.]+) (GB|MB|KB)<").find(html)
        val match = textNode ?: Regex("([0-9.]+) (GB|MB|KB)").find(html) ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val unit = when (match.groupValues[2]) {
            "GB" -> 1024L * 1024 * 1024
            "MB" -> 1024L * 1024
            else -> 1024L
        }
        return (value * unit).toLong()
    }

    /** `/fdfe/delivery`：解析下载直链（base APK；响应另含 split 分发，见 [GoogleDelivery.splits]）。 */
    suspend fun delivery(packageName: String, versionCode: Long): GoogleDelivery {
        val s = session()
        val response = client.get("$AnonymousBase/fdfe/delivery") {
            anonHeaders(s).forEach { (k, v) -> header(k, v) }
            url.parameters.append("doc", packageName)
            url.parameters.append("vc", versionCode.toString())
            url.parameters.append("ot", "1")
        }
        if (!response.status.isSuccess()) {
            throw MarketException("Google Play 下载解析失败（HTTP ${response.status.value}）")
        }
        val root = decodeBody(response.readBytes())
        // 正常信封 payload→delivery_response=21；退化信封下退而扫描 21 节点
        val deliveryResponse = root.msg(1)?.children?.msg(21)?.children
            ?: root.flatMap { it.children }.firstOrNull { it.tag == 21 }?.children
            ?: emptyList()
        val status = deliveryResponse.long(1).toInt()
        if (status != 1) {
            throw MarketException("Google Play 未提供下载（status=$status）")
        }
        val appDelivery = deliveryResponse.msg(2)?.children ?: emptyList()
        val url = appDelivery.str(3)
        if (url.isBlank()) throw MarketException("Google Play 未提供 ${packageName} 的下载地址")
        val cookies = appDelivery.msgs(5).mapNotNull { cookie ->
            val name = cookie.children.str(1)
            val value = cookie.children.str(2)
            if (name.isBlank()) null else name to value
        }
        val splits = appDelivery.msgs(15).mapNotNull { split ->
            val name = split.children.str(1)
            val splitUrl = split.children.str(5)
            if (name.isBlank() || splitUrl.isBlank()) null else GoogleSplit(name, splitUrl, split.children.long(2))
        }
        return GoogleDelivery(
            url = url,
            size = appDelivery.long(1),
            sha1 = appDelivery.str(2),
            sha256 = appDelivery.str(19),
            cookies = cookies,
            splits = splits,
        )
    }

    /**
     * 历史版本目录（Aurora Store 同款数据源，源自 Exodus 版本记录）：
     * `api.auroraoss.com/v1/exodus/{包名}/versions` → 版本名 / 版本号 / 日期。
     * AppMarket / AuroraStore 两种来源方案共用这一套实现，不按引擎分叉。
     * Exodus 未收录该应用（HTTP 404）返回空列表；其余失败抛 [MarketException]，不吞错。
     */
    suspend fun historicalVersions(packageName: String): List<GoogleVersionEntry> {
        val response = runCatching {
            client.get("$ExodusBase/$packageName/versions") {
                header("Accept", "application/json")
                header("User-Agent", "com.aurora.store-4.6.2-48")
            }
        }.getOrElse {
            if (it is kotlin.coroutines.cancellation.CancellationException) throw it
            com.app.market.domain.diagnostic.AppLog.log(
                "GoogleHistory: $packageName 请求失败: ${it.message?.take(120)}",
            )
            throw MarketException("历史版本获取失败：${it.message ?: "网络错误"}")
        }
        // 404 = Exodus 没有该应用的版本记录，属正常空结果，不算错误
        if (response.status.value == 404) return emptyList()
        if (!response.status.isSuccess()) {
            com.app.market.domain.diagnostic.AppLog.log("GoogleHistory: $packageName HTTP ${response.status.value}")
            throw MarketException("历史版本获取失败（HTTP ${response.status.value}）")
        }
        val text = response.bodyAsText()
        if (!text.trimStart().startsWith("{")) {
            com.app.market.domain.diagnostic.AppLog.log("GoogleHistory: $packageName 非 JSON 响应 head=${text.take(40)}")
            throw MarketException("历史版本返回了无效数据")
        }
        val root = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        val versions = root["versions"] as? JsonArray ?: return emptyList()
        return versions.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            GoogleVersionEntry(
                versionCode = (obj["versionCode"] as? JsonPrimitive)?.longOrNull ?: 0L,
                versionName = (obj["version"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                date = (obj["date"] as? JsonPrimitive)?.contentOrNull.orEmpty().take(10),
            )
        }.filter { it.versionCode > 0L }
    }

    // ---------------- 解析 ----------------

    /**
     * 响应解码：极少数路径上响应会带 gzip 而客户端未透明解压，
     * 按魔数识别后先行解包，其余情况原样解析。
     */
    private fun decodeBody(bytes: ByteArray): List<Pb> {
        val raw = if (bytes.size > 2 &&
            (bytes[0].toInt() and 0xFF) == 0x1F && (bytes[1].toInt() and 0xFF) == 0x8B
        ) {
            gunzip(bytes)
        } else {
            bytes
        }
        return Pb.parse(raw)
    }

    private fun headHex(bytes: ByteArray): String =
        bytes.take(8).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    /** 包名形态：字母开头、含点、无空格。 */
    private fun isPkgLike(value: String): Boolean =
        value.length in 3..120 && value.none { it.isWhitespace() } &&
            value.first().isLetter() && '.' in value && !value.startsWith("http")

    /** 任意信封下收集 DocV2 形态节点（1=包名、5=标题；开发者字段部分变体缺失），命中即不再下探（跳过相关推荐子文档）。 */
    private fun collectDocs(nodes: List<Pb>, out: MutableList<GoogleAppRecord>, depth: Int = 0) {
        // 深度保护：Google 响应层级约 8 层，超过 24 层必然是畸形数据，
        // 继续下探只会把线程栈耗尽（表现为 StackOverflowError: stack size xxxKB）。
        if (depth > 24) return
        for (node in nodes) {
            val children = node.children
            if (isPkgLike(children.str(1)) && children.str(5).isNotBlank()) {
                parseDoc(node)?.let { out.add(it) }
                continue
            }
            if (node.tag != 10) collectDocs(children, out, depth + 1)
        }
    }

    /**
     * 大小取数（对照 GPlayAPI ItemMapper / Item.proto 字段号，节点 = GenericMetadata）：
     * genericMetadata.27/47(ApkMetadataWrapper) → .4 total_size（等同 /details 的 infoDownloadSize(9)），
     * 或 → .1(ApkMetadata) → .7 package_size。之前挂在 item.3（另一个无关节点）上，永远取 0。
     */
    private fun itemFileSize(genericMetadata: List<Pb>): Long {
        for (field in intArrayOf(27, 47)) {
            val wrapper = genericMetadata.msg(field)?.children ?: continue
            val total = wrapper.long(4)
            if (total > 0L) return total
            val packageSize = wrapper.msg(1)?.children?.long(7) ?: 0L
            if (packageSize > 0L) return packageSize
        }
        return 0L
    }

    /** ItemResponse.item（getItems）：包名在条目级 1.1.1（批量响应里 generic.1 是变长数组，不是包名）；genericMetadata=3 → versionInfo=2。 */
    private fun parseItem(item: Pb): GoogleAppRecord? {
        val children = item.children
        val generic = children.msg(3)?.children ?: emptyList()
        val pkg = children.msg(1)?.children?.msg(1)?.children?.str(1).orEmpty()
            .ifBlank { generic.msg(1)?.children?.str(1).orEmpty() }
            .takeIf(::isPkgLike).orEmpty()
        if (pkg.isBlank()) return null
        val regional = children.msg(2)?.children ?: emptyList()
        // 版本信息的真实位置是 item.2(DocV2) → .3(appMetadata) → .2(versionInfo) → {1: versionCode, 2: versionString}。
        // 实测 item.2.3.2.1=1561310996 / item.2.3.2.2="21.45.82"。
        // 之前读的是 item.3（那是 field-mask 位图，一堆 1..128 的 varint），
        // 导致所有条目的 versionCode 都解析成 0 —— 更新检查永远比不出新版本。
        val meta = regional.msg(3)?.children ?: emptyList()
        val versionInfo = meta.msg(2)?.children
            ?: generic.msg(2)?.children
            ?: emptyList()
        // 更新日志在 appMetadata.9（{1: 文本, 2: 日期}）。实测 .15 是开发者联系方式，不是日志。
        val changeLog = meta.msg(9)?.children?.str(1).orEmpty()
        val description = regional.msg(8)?.children?.str(1).orEmpty()
        val title = regional.msg(2)?.children?.msg(1)?.children?.str(1).orEmpty()
        // 图标=条目内第一个「带尺寸」图片节点（3:{w,h}+6:{1:url} 形态）：
        // 账号头像等装饰图不带尺寸信息，天然被排除；先扫 regional（图标在此），再退全条目
        val dimensioned = ArrayList<String>()
        collectDimensionedImages(regional, dimensioned)
        if (dimensioned.isEmpty()) collectDimensionedImages(children, dimensioned)
        val icon = dimensioned.firstOrNull().orEmpty()
            .ifBlank {
                collectImageUrls(regional).firstOrNull {
                    "ACg8ocJtociE77" !in it && "/a-/ALV" !in it
                }.orEmpty()
            }
        // 截图：GenericMetadata.10 = {1: repeated 图}，每图 6.1 为图床 URL（1920x1080 等）
        val screenshots = meta.msg(10)?.children?.msgs(1)?.mapNotNull { shot ->
            shot.children.msg(6)?.children?.str(1)?.takeIf { it.isNotBlank() }
        }.orEmpty()
        return GoogleAppRecord(
            packageName = pkg,
            title = title.ifBlank { pkg },
            // 开发者 = GenericMetadata.45(developer_name，StringWrapper{1})
            publisher = meta.msg(45)?.children?.str(1).orEmpty(),
            icon = icon,
            versionCode = versionInfo.long(1),
            versionString = versionInfo.str(2),
            offerType = versionInfo.long(3).toInt().takeIf { it != 0 } ?: 1,
            fileSize = itemFileSize(meta),
            description = description,
            subtitle = "",
            recentChanges = changeLog,
            ratingText = "",
            // 下载量显示串 = GenericMetadata.8（StringWrapper{1}，如「超过100,000,000次下载」）
            downloadCountText = meta.msg(8)?.children?.str(1).orEmpty(),
            screenshots = screenshots,
        )
    }

    /** DocV2（search/details）：1 包名 / 5 标题 / 6 开发者 / 7 简介 / 27 副标题 / 13→1 应用元数据。图标与截图不在 DocV2 里，由 getItems 补齐。 */
    private fun parseDoc(doc: Pb): GoogleAppRecord? {
        val children = doc.children
        val pkg = children.str(1)
        if (pkg.isBlank()) return null
        val meta = children.msg(13)?.children?.msg(1)?.children ?: emptyList()
        val rating = children.msg(14)?.children?.str(17)?.toDoubleOrNull() ?: 0.0
        return GoogleAppRecord(
            packageName = pkg,
            title = children.str(5).ifBlank { pkg },
            publisher = children.str(6).ifBlank { meta.str(1) },
            icon = "",
            versionCode = meta.long(3),
            versionString = meta.str(4),
            offerType = 1,
            fileSize = meta.long(9),
            description = children.str(7),
            subtitle = children.str(27),
            // 更新日志在 appMetadata.9.1（.9 里还有 .2 = 发布日期）；.15 是开发者联系方式
            recentChanges = meta.msg(9)?.children?.str(1).orEmpty(),
            ratingText = if (rating > 0.0) rating.toString() else "",
            downloadCountText = meta.str(13).ifBlank { meta.str(61) },
            screenshots = emptyList(),
        )
    }

    /** 用 getItems 的结果补齐 DocV2 缺失的图标/截图/更新日志（getItems 是唯一带图的端点）。 */
    private fun mergeItemData(doc: GoogleAppRecord, item: GoogleAppRecord?): GoogleAppRecord {
        if (item == null) return doc
        return doc.copy(
            icon = doc.icon.ifBlank { item.icon },
            screenshots = if (doc.screenshots.isNotEmpty()) doc.screenshots else item.screenshots,
            recentChanges = doc.recentChanges.ifBlank { item.recentChanges },
            fileSize = if (doc.fileSize > 0L) doc.fileSize else item.fileSize,
            downloadCountText = doc.downloadCountText.ifBlank { item.downloadCountText },
        )
    }

    /** 收集图床 URL（play-lh / lh3 等 googleusercontent 域都可能出现）；跳过 field 10（相关推荐子文档，内含其他应用图标）。 */
    private fun collectImageUrls(children: List<Pb>): List<String> {
        val out = ArrayList<String>()
        fun walk(nodes: List<Pb>, depth: Int = 0) {
            if (depth > 24) return
            for (node in nodes) {
                if (node.tag == 10) continue
                val value = node.asString
                if (value.startsWith("https://") && value.contains("googleusercontent.com")) out.add(value)
                walk(node.children, depth + 1)
            }
        }
        walk(children)
        return out.distinct()
    }

    /** 收集「带尺寸」图片节点（3:{w,h} + 6:{1:url}）：应用图标与截图的真形态，头像/徽章等装饰图不带尺寸。 */
    private fun collectDimensionedImages(children: List<Pb>, out: MutableList<String>, depth: Int = 0) {
        if (depth > 24) return
        for (node in children) {
            val inner = node.children
            val dims = inner.msg(3)?.children
            val url = inner.msg(6)?.children?.str(1).orEmpty()
            if (url.isNotBlank() && dims != null && dims.msg(1) != null && dims.msg(2) != null) {
                out.add(url)
            }
            collectDimensionedImages(inner, out, depth + 1)
        }
    }

    private fun randomUuid(): String {
        val bytes = ByteArray(16)
        Random.nextBytes(bytes)
        bytes[6] = (bytes[6].toInt() and 0x0F or 0x40).toByte()
        bytes[8] = (bytes[8].toInt() and 0x3F or 0x80).toByte()
        val hex = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
}

internal class GooglePlaySession(
    val authToken: String,
    val gsfId: String,
    val dfeCookie: String,
    val deviceConfigToken: String,
    val checkinConsistencyToken: String,
)

internal data class GoogleSplit(
    val name: String,
    val url: String,
    val size: Long,
)

/** 历史版本目录条目（Exodus 版本记录）。 */
internal data class GoogleVersionEntry(
    val versionCode: Long,
    val versionName: String,
    val date: String,
)

internal data class GoogleDelivery(
    val url: String,
    val size: Long,
    val sha1: String,
    val sha256: String,
    val cookies: List<Pair<String, String>>,
    val splits: List<GoogleSplit>,
)

internal data class GoogleAppRecord(
    val packageName: String,
    val title: String,
    val publisher: String,
    val icon: String,
    val versionCode: Long,
    val versionString: String,
    val offerType: Int,
    val fileSize: Long,
    val description: String,
    val subtitle: String,
    val recentChanges: String,
    val ratingText: String,
    val downloadCountText: String,
    val screenshots: List<String>,
)

private const val DispenserUrl = "https://auroraoss.com/api/auth"
private const val ExodusBase = "https://api.auroraoss.com/v1/exodus"
private const val AnonymousBase = "https://play.googleapis.com"
private const val AuthedBase = "https://android.clients.google.com"
private const val PageSize = 20

/** 首屏期望条数：低于这个数就自动补页（Play 按 cluster 返回，命中精确匹配时首页可能只有 1 条）。 */
private const val MinSearchResults = 8

/** 网页版搜索首屏取多少条：Play 商店一屏就是这个量级，多余的会拖慢详情批量请求。 */
private const val WebPrimaryLimit = 24

/** 首屏最多自动补几页（每页 PageSize 条），防止对 Play 打太多请求被风控。 */
private const val MaxExtraPages = 2

/** 结果条数低于此值时不写搜索缓存，避免「只抓到 1 条」被缓存 5 分钟。 */
private const val MinResultsToCache = 3

/** 单次批量最多用网页兜底补几个大小，避免触发风控。 */
private const val MaxWebSizeLookups = 3

/** 榜单包名缓存有效期（毫秒）：Play 榜单不会分钟级变化。 */
private const val TopChartTtlMs = 600_000L
/** 搜索结果内存缓存有效期（毫秒）。 */
private const val SearchCacheTtlMs = 300_000L

/** 搜索页体积补齐的总时间预算（毫秒）：超了就放弃，先出结果。 */
private const val SizeFillBudgetMs = 2_500L

/** 一次批量里最多给多少条补体积（并发 3，24 条首屏基本一次补齐）。 */
private const val SizeFillMaxLookups = 12

private const val WebUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

/** 登录通道必需的两个常量负载（gplayapi DfeSigner 同值）。 */
private const val EncodedTargets =
    "CAESN/qigQYC2AMBFfUbyA7SM5Ij/CvfBoIDgxHqGP8R3xzIBvoQtBKFDZ4HAY4FrwSVMasHBO0O2Q8akgYRAQECAQO7AQEpKZ0CnwECAwRrAQYBr9PPAoK7sQMBAQMCBAkIDAgBAwEDBAICBAUZEgMEBAMLAQEBBQEBAcYBARYED+cBfS8CHQEKkAEMMxcBIQoUDwYHIjd3DQ4MFk0JWGYZEREYAQOLAYEBFDMIEYMBAgICAgICOxkCD18LGQKEAcgDBIQBAgGLARkYCy8oBTJlBCUocxQn0QUBDkkGxgNZQq0BZSbeAmIDgAEBOgGtAaMCDAOQAZ4BBIEBKUtQUYYBQscDDxPSARA1oAEHAWmnAsMB2wFyywGLAxol+wImlwOOA80CtwN26A0WjwJVbQEJPAH+BRDeAfkHK/ABASEBCSAaHQemAzkaRiu2Ad8BdXeiAwEBGBUBBN4LEIABK4gB2AFLfwECAdoENq0CkQGMBsIBiQEtiwGgA1zyAUQ4uwS8AwhsvgPyAcEDF27vApsBHaICGhl3GSKxAR8MC6cBAgItmQYG9QIeywLvAeYBDArLAh8HASI4ELICDVmVBgsY/gHWARtcAsMBpALiAdsBA7QBpAJmIArpByn0AyAKBwHTARIHAX8D+AMBcRIBBbEDmwUBMacCHAciNp0BAQF0OgQLJDuSAh54kwFSP0eeAQQ4M5EBQgMEmwFXywFo0gFyWwMcapQBBugBPUW2AVgBKmy3AR6PAbMBGQxrUJECvQR+8gFoWDsYgQNwRSczBRXQAgtRswEW0ALMAREYAUEBIG6yATYCRE8OxgER8gMBvQEDRkwLc8MBTwHZAUOnAXiiBakDIbYBNNcCIUmuArIBSakBrgFHKs0EgwV/G3AD0wE6LgECtQJ4xQFwFbUCjQPkBS6vAQqEAUZF3QIM9wEhCoYCQhXsBCyZArQDugIziALWAdIBlQHwBdUErQE6qQaSA4EEIvYBHir9AQVLmgMCApsCKAwHuwgrENsBAjNYswEVmgIt7QJnN4wDEnta+wGfAcUBxgEtEFXQAQWdAUAeBcwBAQM7rAEJATJ0LENrdh73A6UBhAE+qwEeASxLZUMhDREuH0CGARbd7K0GlQo"
private const val Phenotype =
    "H4sIAAAAAAAAAB3OO3KjMAAA0KRNuWXukBkBQkAJ2MhgAZb5u2GCwQZbCH_EJ77QHmgvtDtbv-Z9_H63zXXU0NVPB1odlyGy7751Q3CitlPDvFd8lxhz3tpNmz7P92CFw73zdHU2Ie0Ad2kmR8lxhiErTFLt3RPGfJQHSDy7Clw10bg8kqf2owLokN4SecJTLoSwBnzQSd652_MOf2d1vKBNVedzg4ciPoLz2mQ8efGAgYeLou-l-PXn_7Sna1MfhHuySxt-4esulEDp8Sbq54CPPKjpANW-lkU2IZ0F92LBI-ukCKSptqeq1eXU96LD9nZfhKHdtjSWwJqUm_2r6pMHOxk01saVanmNopjX3YxQafC4iC6T55aRbC8nTI98AF_kItIQAJb5EQxnKTO7TZDWnr01HVPxelb9A2OWX6poidMWl16K54kcu_jhXw-JSBQkVcD_fPsLSZu6joIBAAA"