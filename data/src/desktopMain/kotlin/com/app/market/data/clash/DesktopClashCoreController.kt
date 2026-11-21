package com.app.market.data.clash

import com.app.market.domain.exception.MarketException
import com.app.market.domain.repository.ClashCoreController
import com.app.market.domain.repository.ClashState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 桌面端无内置 Clash 内核：保持不可用，UI 会提示。 */
internal class DesktopClashCoreController : ClashCoreController {
    private val _state = MutableStateFlow(ClashState.STOPPED)
    override val state: StateFlow<ClashState> = _state.asStateFlow()

    private val _mixedPort = MutableStateFlow(7890)
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

    override suspend fun start(subscriptionUrl: String) {
        _state.value = ClashState.FAILED
        _lastError.value = "当前平台不支持内置 Clash 内核"
        throw MarketException(_lastError.value)
    }

    override suspend fun stop() {
        _state.value = ClashState.STOPPED
        _nodes.value = emptyList()
        _selectedNode.value = ""
    }

    override suspend fun refreshNodes() = Unit

    override suspend fun selectNode(name: String) = Unit

    override suspend fun testDelays() = Unit

    override fun startDelayTest() = Unit

    override suspend fun setMixedPort(port: Int) = Unit
}