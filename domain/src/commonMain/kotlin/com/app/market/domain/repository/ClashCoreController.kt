package com.app.market.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 代理内核（mihomo）的本地/远端状态。 */
data class ClashCoreInfo(
    /** 本地是否已下载内核（Download/AppMarket/mihomo）。 */
    val installed: Boolean = false,
    /** 本地内核版本，如 1.19.34；未知时为空。 */
    val localVersion: String = "",
    /** 远端最新版本；查询失败时为空。 */
    val latestVersion: String = "",
    /** 远端有更新版本。 */
    val updateAvailable: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val error: String = "",
)

private val EmptyClashCoreInfo = MutableStateFlow(ClashCoreInfo())
private val EmptySubscriptionInfo = MutableStateFlow<List<String>>(emptyList())
private val EmptyTimeouts = MutableStateFlow<Set<String>>(emptySet())

/** 内置 Clash（mihomo）内核运行状态。 */
enum class ClashState {
    STOPPED,
    STARTING,
    RUNNING,
    FAILED,
}

/**
 * 内置 Clash 内核控制。
 *
 * 最精简能力：用订阅内容生成配置 → 起本地 mihomo 进程 → 暴露混合端口（HTTP/SOCKS 同端口）。
 * 之后把代理主机/端口指向 127.0.0.1:混合端口，由 [ProxySettingsRepository] 的来源规则决定谁走它。
 */
interface ClashCoreController {
    val state: StateFlow<ClashState>

    /** 内核本地混合端口（HTTP/SOCKS 共用）。 */
    val mixedPort: StateFlow<Int>

    /** 最近一次错误信息（启动失败原因）。 */
    val lastError: StateFlow<String>
    /** 内核里可切换的节点名（全局模式的 GLOBAL 组）。 */
    val nodes: StateFlow<List<String>>
    /** 当前生效的节点名。 */
    val selectedNode: StateFlow<String>
    /** 各节点最近一次测速的延迟（毫秒）；不可用节点不在表里。 */
    val delays: StateFlow<Map<String, Int>>
    /** 是否正在测速。 */
    val testing: StateFlow<Boolean>
    /** 设置混合端口（下次启动内核生效）。 */
    suspend fun setMixedPort(port: Int)
    /** 用订阅地址生成配置并启动内核；失败抛异常。 */
    suspend fun start(subscriptionUrl: String)
    /** 停止内核。 */
    suspend fun stop()
    /** 从内核拉取最新节点列表与当前节点。 */
    suspend fun refreshNodes()
    /** 切换节点（即时生效，无需重启内核）。 */
    suspend fun selectNode(name: String)
    /** 对所有节点并发测速，结果写入 [delays]。 */
    suspend fun testDelays()

    /** 触发一次全量测速：在控制器自己的作用域里跑完，不受调用方（页面）生命周期影响。 */
    fun startDelayTest()

    /** 测速失败 / 超时的节点名（面板里显示红色「超时」）。 */
    val timeouts: StateFlow<Set<String>> get() = EmptyTimeouts

    /** 机场写进订阅里的套餐信息（剩余流量 / 到期时间等），仅做展示。 */
    val subscriptionInfo: StateFlow<List<String>> get() = EmptySubscriptionInfo

    /** 内核下载状态（未下载 / 有新版 / 下载中）。 */
    val core: StateFlow<ClashCoreInfo> get() = EmptyClashCoreInfo

    /** 重新检测本地内核版本与远端最新版本。 */
    suspend fun refreshCore() = Unit

    /** 下载 / 更新内核（.so 存应用私有目录，可执行副本放 /data/local/tmp）。 */
    suspend fun downloadCore() = Unit

    /** 卸载本地内核（删存档与可执行副本）。 */
    suspend fun uninstallCore() = Unit
}