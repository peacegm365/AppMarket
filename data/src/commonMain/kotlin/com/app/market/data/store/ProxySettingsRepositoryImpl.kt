package com.app.market.data.store

import com.app.market.data.local.PreferencesDataSource
import com.app.market.data.local.preferences.ProxyPreferenceKeys
import com.app.market.data.platform.debugLog
import com.app.market.domain.exception.MarketException
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.repository.ProxyMode
import com.app.market.domain.repository.ProxyNode
import com.app.market.domain.repository.ProxySettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ProxySettingsRepositoryImpl(
    private val preferences: PreferencesDataSource,
    private val scope: CoroutineScope,
    /**
     * 延迟取 HttpClient：全局 client 的构造本身要读代理设置（ProxySelector），
     * 若这里直接注入就会形成 Koin 依赖环导致启动崩溃。订阅只在用户点击时拉取，
     * 那时容器已就绪，延迟解析即可。
     */
    private val clientProvider: () -> HttpClient,
) : ProxySettingsRepository {

    private val _enabled = MutableStateFlow(false)
    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _type = MutableStateFlow("http")
    override val type: StateFlow<String> = _type.asStateFlow()

    private val _host = MutableStateFlow("")
    override val host: StateFlow<String> = _host.asStateFlow()

    private val _port = MutableStateFlow("")
    override val port: StateFlow<String> = _port.asStateFlow()

    private val _mode = MutableStateFlow(ProxyMode.WHITELIST)
    override val mode: StateFlow<ProxyMode> = _mode.asStateFlow()

    private val _whitelist = MutableStateFlow<Set<AppSource>>(emptySet())
    override val whitelist: StateFlow<Set<AppSource>> = _whitelist.asStateFlow()

    private val _blacklist = MutableStateFlow<Set<AppSource>>(emptySet())
    override val blacklist: StateFlow<Set<AppSource>> = _blacklist.asStateFlow()

    private val _mixedPort = MutableStateFlow("")
    override val mixedPort: StateFlow<String> = _mixedPort.asStateFlow()
    private val _randomPort = MutableStateFlow(true)
    override val randomPort: StateFlow<Boolean> = _randomPort.asStateFlow()

    private val _subscriptionUrl = MutableStateFlow("")
    override val subscriptionUrl: StateFlow<String> = _subscriptionUrl.asStateFlow()

    private val _nodes = MutableStateFlow<List<ProxyNode>>(emptyList())
    override val nodes: StateFlow<List<ProxyNode>> = _nodes.asStateFlow()

    private val _selectedNode = MutableStateFlow("")
    override val selectedNode: StateFlow<String> = _selectedNode.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    override val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        val arrivals = listOf(
            observe("enabled", preferences.observe(ProxyPreferenceKeys.Enabled)) { _enabled.value = it },
            observe("type", preferences.observe(ProxyPreferenceKeys.Type)) { _type.value = it?.ifBlank { "http" } ?: "http" },
            observe("host", preferences.observe(ProxyPreferenceKeys.Host)) { _host.value = it.orEmpty() },
            observe("port", preferences.observe(ProxyPreferenceKeys.Port)) { _port.value = it.orEmpty() },
            observe("mode", preferences.observe(ProxyPreferenceKeys.Mode)) { _mode.value = ProxyMode.fromToken(it) },
            observe("whitelist", preferences.observe(ProxyPreferenceKeys.Whitelist)) { raw ->
                _whitelist.value = parseSources(raw)
            },
            observe("blacklist", preferences.observe(ProxyPreferenceKeys.Blacklist)) { raw ->
                _blacklist.value = parseSources(raw)
            },
            observe("mixedPort", preferences.observe(ProxyPreferenceKeys.MixedPort)) {
                _mixedPort.value = it?.takeIf { v -> v.isNotBlank() } ?: ""
            },
            observe("randomPort", preferences.observe(ProxyPreferenceKeys.RandomPort)) {
                _randomPort.value = it
            },
            observe("subscriptionUrl", preferences.observe(ProxyPreferenceKeys.SubscriptionUrl)) {
                _subscriptionUrl.value = it.orEmpty()
            },
            observe("selectedNode", preferences.observe(ProxyPreferenceKeys.SelectedNode)) {
                _selectedNode.value = it.orEmpty()
            },
        )
        scope.launch {
            arrivals.forEach { it.await() }
            _loaded.value = true
        }
    }

    private fun <T> observe(
        name: String,
        flow: Flow<T>,
        update: (T) -> Unit,
    ): CompletableDeferred<Unit> {
        val firstValue = CompletableDeferred<Unit>()
        scope.launch {
            try {
                flow.collect {
                    update(it)
                    firstValue.complete(Unit)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                debugLog("ProxySettingsRepository") { "observe $name failed: $error" }
            } finally {
                firstValue.complete(Unit)
            }
        }
        return firstValue
    }

    override suspend fun fetchSubscription(url: String): List<ProxyNode> {
        val target = url.trim()
        if (target.isEmpty()) throw MarketException("订阅地址为空")
        val response = clientProvider().get(target) { }
        if (!response.status.isSuccess()) {
            throw MarketException("订阅拉取失败（HTTP ${response.status.value}）")
        }
        val text = decodeMaybeBase64(response.bodyAsText())
        val parsed = parseClashProxies(text)
        if (parsed.isEmpty()) throw MarketException("订阅里没有解析到节点")
        _nodes.value = parsed
        return parsed
    }

    /** Clash 订阅常见返回 base64 编码的 YAML；能解就解，解不出则按原文处理。 */
    private fun decodeMaybeBase64(text: String): String {
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return text
        val looksBase64 = compact.length % 4 == 0 && compact.matches(Regex("[A-Za-z0-9+/=_-]+"))
        if (!looksBase64) return text
        return runCatching {
            kotlin.io.encoding.Base64.decode(compact)
                .decodeToString()
        }.getOrElse { text }
    }

    /**
     * 极简 Clash YAML 解析：只要 `proxies:` 列表里的 name/type/server/port。
     * 订阅体量小、结构固定，用逐行扫描足够，不引入 YAML 依赖。
     */
    private fun parseClashProxies(yaml: String): List<ProxyNode> {
        val nodes = ArrayList<ProxyNode>()
        var inProxies = false
        var name = ""
        var type = ""
        var server = ""
        var port = ""

        fun flush() {
            if (name.isNotBlank() && server.isNotBlank() && port.isNotBlank()) {
                nodes.add(ProxyNode(name = name, type = type, host = server, port = port))
            }
            name = ""; type = ""; server = ""; port = ""
        }

        for (raw in yaml.lineSequence()) {
            if (!inProxies) {
                if (raw.trim().startsWith("proxies:")) inProxies = true
                continue
            }
            // proxies 列表结束后遇到「顶格的新键」就退出（列表项都以空格缩进或 - 开头）
            if (raw.isNotEmpty() && !raw.startsWith(" ") && !raw.startsWith("-") && raw.contains(':')) break
            val line = raw.trim()
            if (line.startsWith("- ")) flush()
            val body = line.removePrefix("- ").trim()
            when {
                body.startsWith("name:") -> name = body.substringAfter(':').trim().trim('\'', '"')
                body.startsWith("type:") -> type = body.substringAfter(':').trim().trim('\'', '"')
                body.startsWith("server:") -> server = body.substringAfter(':').trim().trim('\'', '"')
                body.startsWith("port:") -> port = body.substringAfter(':').trim().trim('\'', '"')
            }
        }
        flush()
        return nodes
    }

    override suspend fun setEnabled(value: Boolean) = preferences.put(ProxyPreferenceKeys.Enabled, value)
    override suspend fun setType(value: String) = preferences.put(ProxyPreferenceKeys.Type, value)
    override suspend fun setHost(value: String) = preferences.put(ProxyPreferenceKeys.Host, value)
    override suspend fun setPort(value: String) = preferences.put(ProxyPreferenceKeys.Port, value)
    override suspend fun setMixedPort(value: String) = preferences.put(ProxyPreferenceKeys.MixedPort, value)
    override suspend fun setRandomPort(value: Boolean) = preferences.put(ProxyPreferenceKeys.RandomPort, value)
    override suspend fun setMode(value: ProxyMode) = preferences.put(ProxyPreferenceKeys.Mode, value.token)

    /** 只改当前生效模式对应的那份名单，另一份原样保留。 */
    override suspend fun setSourceIncluded(source: AppSource, included: Boolean) {
        val isWhitelist = _mode.value == ProxyMode.WHITELIST
        val current = (if (isWhitelist) _whitelist.value else _blacklist.value).toMutableSet()
        if (included) current.add(source) else current.remove(source)
        val encoded = current.joinToString(",") { it.token }
        preferences.put(
            if (isWhitelist) ProxyPreferenceKeys.Whitelist else ProxyPreferenceKeys.Blacklist,
            encoded,
        )
    }

    private fun parseSources(raw: String?): Set<AppSource> = raw.orEmpty()
        .split(',')
        .mapNotNull { token -> AppSource.entries.firstOrNull { it.token == token.trim() } }
        .toSet()

    override suspend fun setSubscriptionUrl(value: String) = preferences.put(ProxyPreferenceKeys.SubscriptionUrl, value)
    override suspend fun setSelectedNode(value: String) = preferences.put(ProxyPreferenceKeys.SelectedNode, value)
}