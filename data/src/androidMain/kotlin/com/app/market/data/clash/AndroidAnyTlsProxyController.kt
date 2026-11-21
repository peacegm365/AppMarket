package com.app.market.data.clash

import android.content.Context
import com.app.market.data.platform.debugLog
import com.app.market.data.platform.writeDebugLog
import com.app.market.data.proxy.AnyTlsNode
import com.app.market.data.proxy.AnyTlsRuntime
import com.app.market.data.proxy.AnyTlsSession
import com.app.market.data.proxy.AnyTlsSubscription
import com.app.market.domain.exception.MarketException
import com.app.market.domain.repository.ClashCoreController
import com.app.market.domain.repository.ClashState
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * AnyTLS 直连代理控制器：没有内核、没有本地端口。
 *
 * 起「代理」= 解析订阅、选一个节点挂在 [AnyTlsRuntime] 上；
 * 之后 OkHttp 建连时按来源规则决定是否在这条 TLS 会话上开一条 Stream 出去。
 */
internal class AndroidAnyTlsProxyController(
    private val context: Context,
    @Suppress("unused") private val clientProvider: () -> Any,
) : ClashCoreController {

    private val _state = MutableStateFlow(ClashState.STOPPED)
    override val state: StateFlow<ClashState> = _state.asStateFlow()

    /** 没有本地端口了，保留字段只为兼容旧界面。 */
    private val _mixedPort = MutableStateFlow(0)
    override val mixedPort: StateFlow<Int> = _mixedPort.asStateFlow()

    private val _lastError = MutableStateFlow("")
    override val lastError: StateFlow<String> = _lastError.asStateFlow()

    private val _nodes = MutableStateFlow<List<String>>(emptyList())
    override val nodes: StateFlow<List<String>> = _nodes.asStateFlow()

    private val _selectedNode = MutableStateFlow("")
    override val selectedNode: StateFlow<String> = _selectedNode.asStateFlow()

    private val _delays = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val delays: StateFlow<Map<String, Int>> = _delays.asStateFlow()

    private val _testing = MutableStateFlow(false)
    override val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _timeouts = MutableStateFlow<Set<String>>(emptySet())
    override val timeouts: StateFlow<Set<String>> = _timeouts.asStateFlow()

    private val _subscriptionInfo = MutableStateFlow<List<String>>(emptyList())
    override val subscriptionInfo: StateFlow<List<String>> = _subscriptionInfo.asStateFlow()

    private val probeScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO,
    )

    private var subscription: String = ""
    private var parsedNodes: List<AnyTlsNode> = emptyList()

    /** 订阅下载必须直连：它自己要用来建立代理，不能依赖代理。 */
    private val directClient = OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun log(message: () -> String) = writeDebugLog("AnyTlsProxy", message())

    private companion object {
        const val PROBE_CONCURRENCY = 8
        const val PROBE_TIMEOUT_MS = 6_000L
    }

    override suspend fun setMixedPort(port: Int) = Unit

    override suspend fun start(subscriptionUrl: String) = withContext(Dispatchers.IO) {
        val url = subscriptionUrl.trim()
        if (url.isEmpty()) throw MarketException("订阅地址为空")
        _state.value = ClashState.STARTING
        _lastError.value = ""
        try {
            val text = fetchSubscription(url)
            subscription = url
            applySubscription(text)
            _state.value = ClashState.RUNNING
        } catch (error: Throwable) {
            _state.value = ClashState.FAILED
            _lastError.value = error.message ?: error.toString()
            log { "start failed: $error" }
            throw if (error is MarketException) error else MarketException(error.message ?: "启动代理失败")
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        AnyTlsRuntime.stop()
        _state.value = ClashState.STOPPED
        _nodes.value = emptyList()
        _selectedNode.value = ""
        parsedNodes = emptyList()
        Unit
    }

    override suspend fun refreshNodes() = withContext(Dispatchers.IO) {
        if (subscription.isBlank()) return@withContext
        runCatching {
            val text = fetchSubscription(subscription)
            applySubscription(text)
        }.onFailure { log { "refresh nodes failed: $it" } }
        Unit
    }

    override suspend fun selectNode(name: String) = withContext(Dispatchers.IO) {
        val node = parsedNodes.firstOrNull { it.name == name } ?: return@withContext
        AnyTlsRuntime.setNode(node)
        _selectedNode.value = node.name
        log { "select node " + node.name + " (" + node.server + ":" + node.port + ")" }
        Unit
    }

    override suspend fun testDelays() {
        val targets = parsedNodes
        if (targets.isEmpty()) return
        _testing.value = true
        try {
            val semaphore = Semaphore(PROBE_CONCURRENCY)
            val collected = java.util.concurrent.ConcurrentHashMap<String, Int>()
            val failed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            _delays.value = emptyMap()
            _timeouts.value = emptySet()
            coroutineScope {
                targets.map { node ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val latency = measure(node)
                            // 每测完一个就刷新，界面不必等全部测完
                            if (latency != null) {
                                failed.remove(node.name)
                                collected[node.name] = latency
                                _delays.value = collected.toMap()
                                _timeouts.value = failed.toSet()
                            } else {
                                failed.add(node.name)
                                _timeouts.value = failed.toSet()
                            }
                        }
                    }
                }.awaitAll()
            }
            log { "delay test done: " + collected.size + "/" + targets.size }
        } finally {
            _testing.value = false
        }
    }

    override fun startDelayTest() {
        if (_testing.value) return
        probeScope.launch { runCatching { testDelays() } }
    }

    private fun applySubscription(text: String) {
        val parsed = AnyTlsSubscription.parseAll(text)
        if (parsed.nodes.isEmpty()) throw MarketException("订阅里没有 anytls 节点")
        parsedNodes = parsed.nodes
        _nodes.value = parsed.nodes.map { it.name }
        _subscriptionInfo.value = parsed.info
        val target = parsed.nodes.firstOrNull { it.name == _selectedNode.value } ?: parsed.nodes.first()
        AnyTlsRuntime.setNode(target)
        _selectedNode.value = target.name
        log { "subscription parsed: " + parsed.nodes.size + " nodes, info=" + parsed.info.size + ", using " + target.name }
    }

    private fun fetchSubscription(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "AppMarket/2.4.4").build()
        directClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw MarketException("订阅获取失败：HTTP " + response.code)
            return response.body?.string().orEmpty()
        }
    }

    /**
     * 节点测速：建 TLS → 发认证 → 发 settings → 等服务器应答。
     *
     * 只测 TLS 握手是不够的：认证失败、节点掉线时握手照样能成功，
     * 所以必须等到服务端 frames（cmdServerSettings / padding 方案）才算可用。
     */
    private suspend fun measure(node: AnyTlsNode): Int? {
        val start = System.currentTimeMillis()
        val session = AnyTlsSession(node)
        return try {
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                session.open()
                session.awaitReady()
                (System.currentTimeMillis() - start).toInt()
            }.also { if (it == null) log { "probe timeout: " + node.name } }
        } catch (error: Throwable) {
            log { "probe failed " + node.name + ": " + (error.message ?: error.toString()) }
            null
        } finally {
            runCatching { session.close() }
        }
    }

}