package com.app.market.data.store

import com.app.market.data.local.PreferencesDataSource
import com.app.market.data.local.StringPreferenceKey
import com.app.market.data.local.preferences.TranslationPreferenceKeys
import com.app.market.domain.model.translation.TranslationProvider
import com.app.market.domain.repository.TranslationRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 详情页翻译实现：
 * - Google：translate.googleapis.com 免密钥端点
 * - 微软：Bing 网页翻译 ttranslatev3（edge/translate/auth 鉴权端点已被微软下线）
 * - AI：任意 OpenAI 兼容 /chat/completions 端点
 * 长文本按段落切块逐段翻译后再拼回去（接口对单次长度有限制）。
 */
internal class TranslationRepositoryImpl(
    private val preferences: PreferencesDataSource,
    private val scope: CoroutineScope,
    private val json: Json,
    private val http: () -> HttpClient,
) : TranslationRepository {

    private val _provider = MutableStateFlow(TranslationProvider.GOOGLE)
    override val provider: StateFlow<TranslationProvider> = _provider.asStateFlow()
    private val _aiBaseUrl = MutableStateFlow("")
    override val aiBaseUrl: StateFlow<String> = _aiBaseUrl.asStateFlow()
    private val _aiApiKey = MutableStateFlow("")
    override val aiApiKey: StateFlow<String> = _aiApiKey.asStateFlow()
    private val _aiModel = MutableStateFlow("")
    override val aiModel: StateFlow<String> = _aiModel.asStateFlow()
    private val _aiPrompt = MutableStateFlow("")
    override val aiPrompt: StateFlow<String> = _aiPrompt.asStateFlow()
    override val defaultAiPrompt: String get() = DEFAULT_PROMPT

    /** 微软翻译会话（b 网页翻译）：IG + 防滥用 key/token，约 1 小时过期，失效自动重抓。 */
    private data class BingSession(val ig: String, val iid: String, val key: String, val token: String, val fetchedAt: Long)

    private var bing: BingSession? = null

    init {
        scope.launch {
            runCatching { preferences.observe(TranslationPreferenceKeys.Provider).collect { _provider.value = TranslationProvider.parse(it) } }
        }
        scope.launch {
            runCatching { preferences.observe(TranslationPreferenceKeys.AiBaseUrl).collect { _aiBaseUrl.value = it.orEmpty() } }
        }
        scope.launch {
            runCatching { preferences.observe(TranslationPreferenceKeys.AiApiKey).collect { _aiApiKey.value = it.orEmpty() } }
        }
        scope.launch {
            runCatching { preferences.observe(TranslationPreferenceKeys.AiModel).collect { _aiModel.value = it.orEmpty() } }
        }
        scope.launch {
            runCatching { preferences.observe(TranslationPreferenceKeys.AiPrompt).collect { _aiPrompt.value = it.orEmpty() } }
        }
    }

    override suspend fun setProvider(provider: TranslationProvider) {
        _provider.value = provider
        preferences.put(TranslationPreferenceKeys.Provider, provider.name)
    }

    override suspend fun setAiBaseUrl(value: String) {
        _aiBaseUrl.value = value
        preferences.put(TranslationPreferenceKeys.AiBaseUrl, value)
    }

    override suspend fun setAiApiKey(value: String) {
        _aiApiKey.value = value
        preferences.put(TranslationPreferenceKeys.AiApiKey, value)
    }

    override suspend fun setAiModel(value: String) {
        _aiModel.value = value
        preferences.put(TranslationPreferenceKeys.AiModel, value)
    }

    override suspend fun setAiPrompt(value: String) {
        _aiPrompt.value = value
        preferences.put(TranslationPreferenceKeys.AiPrompt, value)
    }

    /** 拉取模型列表：OpenAI 兼容的 /models 接口。 */
    override suspend fun fetchModels(): List<String> {
        val base = _aiBaseUrl.value.trim().trimEnd('/')
        if (base.isEmpty()) error("请先填写接口地址")
        val endpoint = if (base.endsWith("/models")) base else "$base/models"
        val key = _aiApiKey.value.trim()
        val response = http().get(endpoint) {
            if (key.isNotEmpty()) header(HttpHeaders.Authorization, "Bearer $key")
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) error("拉取模型列表失败(${response.status.value})：" + body.take(160))
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: error("模型列表返回异常：" + body.take(160))
        val ids = root["data"]?.jsonArray?.mapNotNull {
            runCatching { it.jsonObject["id"]?.jsonPrimitive?.content }.getOrNull()
        }.orEmpty()
        if (ids.isEmpty()) error("模型列表为空：" + body.take(160))
        return ids
    }

    /** 设置页「测试连接」：用当前引擎翻一句话，成功返回可展示的说明。 */
    override suspend fun testConnection(): String = when (_provider.value) {
        TranslationProvider.GOOGLE -> google("Hello, world!")
        TranslationProvider.MICROSOFT -> microsoft("Hello, world!")
        TranslationProvider.AI -> ai("Hello, world!")
    }

    override suspend fun translate(text: String): String {
        val content = text.trim()
        if (content.isEmpty()) return ""
        val engine = _provider.value
        val identity = listOf(engine.name, if (engine == TranslationProvider.AI) _aiBaseUrl.value else "", if (engine == TranslationProvider.AI) _aiModel.value else "", if (engine == TranslationProvider.AI) _aiPrompt.value else "", content).joinToString("\u0000")
        val cacheKey = StringPreferenceKey("translation_cache", "v1_${identity.hashCode().toUInt()}")
        val cacheEnabled = preferences.read(com.app.market.data.local.preferences.UpdatePreferenceKeys.PageCacheEnabled)
        val cached = if (cacheEnabled) runCatching { preferences.read(cacheKey) }.getOrNull()?.let {
            runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull()
        } else null
        if (cached?.get("input")?.jsonPrimitive?.contentOrNull == identity) {
            cached["output"]?.jsonPrimitive?.contentOrNull?.let { return it }
        }
        var complete = true
        // 按段落逐段翻译：整篇丢给接口时，长文本经常只回来一部分译文（后面的段落原样返回）
        val parts = mutableListOf<String>()
        for (chunkText in chunk(content, MAX_CHUNK_CHARS)) {
            val translated: String = runCatching<String> {
                when (engine) {
                    TranslationProvider.GOOGLE -> google(chunkText)
                    TranslationProvider.MICROSOFT -> microsoft(chunkText)
                    TranslationProvider.AI -> ai(chunkText)
                }
            }.getOrElse { error ->
                // 第一段就失败：把原因抛给界面；后续段失败则保留原文继续
                if (parts.isEmpty()) throw error else { complete = false; chunkText }
            }
            if (translated.isNotBlank()) parts += translated
        }
        val result = parts.joinToString("\n\n").trim()
        if (complete && result.isNotBlank() && preferences.read(com.app.market.data.local.preferences.UpdatePreferenceKeys.PageCacheEnabled)) {
            val entry = buildJsonObject { put("input", identity); put("output", result) }.toString()
            runCatching { preferences.put(cacheKey, entry) }
        }
        return result
    }

    private suspend fun google(text: String): String {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$GOOGLE_TARGET&dt=t&q=" +
                text.encodeURLParameter()
        val body = http().get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }.bodyAsText()
        val segments = runCatching { json.parseToJsonElement(body).jsonArray[0].jsonArray }.getOrNull()
            ?: error("Google翻译返回异常：" + body.take(120))
        val out = buildString {
            segments.forEach { seg ->
                // 有些分段是 [null, ""]（无可译内容）：用 contentOrNull 跳过而不是抛异常
                seg.jsonArray.firstOrNull()?.jsonPrimitive?.contentOrNull?.let(::append)
            }
        }
        return out.trim().ifBlank { error("Google翻译没有返回内容") }
    }

    private suspend fun bingSession(force: Boolean = false): BingSession {
        val cached = bing
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        if (!force && cached != null && now - cached.fetchedAt < BING_SESSION_TTL_MS) return cached
        // edge.microsoft.com/translate/auth 已被微软下线（404），改走 Bing 网页翻译同款链路：
        // 从翻译页拿 IG 与防滥用 key/token，再调 ttranslatev3
        val html = http().get("https://www.bing.com/translator") {
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.bodyAsText()
        val ig = Regex("IG:\"([A-F0-9]+)\"").find(html)?.groupValues?.get(1)
            ?: error("微软翻译会话获取失败：页面里没有 IG")
        val ap = Regex("params_AbusePreventionHelper\\s*=\\s*\\[\\s*(\\d+)\\s*,\\s*\"([^\"]+)\"").find(html)
            ?: error("微软翻译会话获取失败：页面里没有防滥用参数")
        val iid = Regex("data-iid=\"([^\"]+)\"").find(html)?.groupValues?.get(1) ?: "translator.5026"
        return BingSession(
            ig = ig,
            iid = iid,
            key = ap.groupValues[1],
            token = ap.groupValues[2],
            fetchedAt = now,
        ).also { bing = it }
    }
    private suspend fun microsoft(text: String): String {
        var attempt = 0
        while (true) {
            val session = bingSession(force = attempt > 0)
            val response = http().post(
                "https://www.bing.com/ttranslatev3?isVertical=1&&IG=${session.ig}&IID=${session.iid}.1",
            ) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header("Referer", "https://www.bing.com/translator")
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    "fromLang=auto-detect&to=$MS_TARGET" +
                        "&text=${text.encodeURLParameter()}" +
                        "&key=${session.key.encodeURLParameter()}" +
                        "&token=${session.token.encodeURLParameter()}",
                )
            }
            val body = response.bodyAsText()
            val ok = response.status.isSuccess() && body.trimStart().startsWith("[")
            if (ok) {
                val out = runCatching {
                    json.parseToJsonElement(body).jsonArray
                        .first().jsonObject["translations"]!!.jsonArray
                        .first().jsonObject["text"]!!.jsonPrimitive.content
                }.getOrNull()
                return out?.trim().orEmpty().ifBlank { error("微软翻译没有返回内容：" + body.take(120)) }
            }
            // 205/4xx：会话过期可能，换一次会话重试；再失败把原因抛给界面
            if (attempt++ >= 1) error("微软翻译失败(${response.status.value})：" + body.take(160))
        }
    }

    private suspend fun ai(text: String): String {
        val base = _aiBaseUrl.value.trim().trimEnd('/')
        if (base.isEmpty()) error("请先在设置-通用-翻译里填写 AI 接口地址")
        val model = _aiModel.value.trim().ifEmpty { DEFAULT_AI_MODEL }
        val endpoint = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val key = _aiApiKey.value.trim()
        val payload = buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            put(
                "messages",
                buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put(
                            "content",
                            _aiPrompt.value.trim().ifEmpty {
                                DEFAULT_PROMPT
                            },
                        )
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", text)
                    })
                },
            )
        }.toString()
        val body = http().post(endpoint) {
            contentType(ContentType.Application.Json)
            if (key.isNotEmpty()) header(HttpHeaders.Authorization, "Bearer $key")
            setBody(payload)
        }.bodyAsText()
        val out = runCatching {
            json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray
                .first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        }.getOrNull()
        return out?.trim().orEmpty().ifBlank { error("AI翻译失败：" + body.take(160)) }
    }

    /** 按段落切块，尽量不把一段话劈开。 */
    /** 按空行切段，再把段落打包到 [limit] 以内：每段都单独发，避免长文本只翻一部分。 */
    private fun chunk(text: String, limit: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) {
                out += buffer.toString()
                buffer.clear()
            }
        }
        text.split(Regex("\n{2,}")).forEach { paragraph ->
            if (paragraph.isBlank()) return@forEach
            val piece = paragraph.trim()
            if (buffer.length + piece.length + 2 > limit) flush()
            if (piece.length > limit) {
                piece.chunked(limit).forEach { part ->
                    if (buffer.isNotEmpty()) flush()
                    out += part
                }
            } else {
                if (buffer.isNotEmpty()) buffer.append("\n\n")
                buffer.append(piece)
            }
        }
        flush()
        return out.ifEmpty { listOf(text) }
    }

    private companion object {
        const val GOOGLE_TARGET = "zh-CN"
        const val MS_TARGET = "zh-Hans"
        const val DEFAULT_AI_MODEL = "gpt-4o-mini"
        const val MAX_CHUNK_CHARS = 1200
        const val DEFAULT_PROMPT =
            "你是专业的软件介绍翻译引擎。把用户给出的内容翻译成简体中文，保留 Markdown 结构、代码块和链接地址，不要解释、不要添加任何前后缀。"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppMarket"
        const val BING_SESSION_TTL_MS = 55 * 60 * 1000L
    }
}