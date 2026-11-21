package com.app.market.domain.repository

import com.app.market.domain.model.market.AppSource
import kotlinx.coroutines.flow.StateFlow

/** 代理生效模式。 */
enum class ProxyMode {
    /** 只有选中的来源走代理。 */
    WHITELIST,

    /** 只有选中的来源直连，其余走代理。 */
    BLACKLIST,
    ;

    val token: String get() = name.lowercase()

    companion object {
        fun fromToken(value: String?): ProxyMode =
            entries.firstOrNull { it.token == value } ?: WHITELIST
    }
}

/** Clash 订阅里的一个节点。 */
data class ProxyNode(
    val name: String,
    val type: String,
    val host: String,
    val port: String,
)

/** 网络代理设置。 */
interface ProxySettingsRepository {
    val enabled: StateFlow<Boolean>

    /** 代理类型：http / socks。 */
    val type: StateFlow<String>
    val host: StateFlow<String>
    val port: StateFlow<String>
    val mode: StateFlow<ProxyMode>
    /** 白名单：只有这些来源走代理（与黑名单互不影响，各存一份）。 */
    val whitelist: StateFlow<Set<AppSource>>
    /** 黑名单：这些来源直连（与白名单互不影响，各存一份）。 */
    val blacklist: StateFlow<Set<AppSource>>
    /** 内置内核的本地混合端口，允许自定义（默认 7890）。 */
    val mixedPort: StateFlow<String>
    /** 混合端口是否每次启动随机分配（关=固定用保存的端口）。 */
    val randomPort: StateFlow<Boolean>

    val subscriptionUrl: StateFlow<String>

    /** 最近一次拉取到的订阅节点（不持久化，重进页面需重拉）。 */
    val nodes: StateFlow<List<ProxyNode>>
    val selectedNode: StateFlow<String>
    /** 持久化设置是否已从存储加载完成（启动时自动更新节点要用）。 */
    val loaded: StateFlow<Boolean>
    /** 拉取订阅中的节点列表；失败抛异常。 */
    suspend fun fetchSubscription(url: String): List<ProxyNode>

    suspend fun setEnabled(value: Boolean)
    suspend fun setType(value: String)
    suspend fun setHost(value: String)
    suspend fun setPort(value: String)
    suspend fun setMixedPort(value: String)
    suspend fun setRandomPort(value: Boolean)
    suspend fun setMode(value: ProxyMode)
    suspend fun setSourceIncluded(source: AppSource, included: Boolean)
    suspend fun setSubscriptionUrl(value: String)
    suspend fun setSelectedNode(value: String)
}