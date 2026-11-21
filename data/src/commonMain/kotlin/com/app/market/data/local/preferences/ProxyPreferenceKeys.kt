package com.app.market.data.local.preferences

import com.app.market.data.local.BooleanPreferenceKey
import com.app.market.data.local.StringPreferenceKey

object ProxyPreferenceKeys {
    private const val NS = "proxy_preferences"

    /** 是否启用代理。 */
    val Enabled = BooleanPreferenceKey(NS, "enabled")
    /** 混合端口是否每次启动随机分配（关=固定用保存的端口）。 */
    val RandomPort = BooleanPreferenceKey(NS, "random_port")

    /** 代理类型：http / socks。 */
    val Type = StringPreferenceKey(NS, "type")

    /** 代理主机（如 127.0.0.1）。 */
    val Host = StringPreferenceKey(NS, "host")

    /** 代理端口。 */
    val Port = StringPreferenceKey(NS, "port")

    /**
     * 生效模式：
     *  - `whitelist`：只有 [Whitelist] 里的来源走代理，其余直连；
     *  - `blacklist`：只有 [Blacklist] 里的来源直连，其余走代理。
     *
     * 两套名单各自独立存储，切换模式不会互相覆盖。
     */
    val Mode = StringPreferenceKey(NS, "mode")

    /** 白名单来源 token，逗号分隔（与 AppSource.token 对应）。 */
    val Whitelist = StringPreferenceKey(NS, "whitelist")

    /** 黑名单来源 token，逗号分隔（与 AppSource.token 对应）。 */
    val Blacklist = StringPreferenceKey(NS, "blacklist")

    /** 内置内核的本地混合端口（默认 7890，可自定义避开端口冲突）。 */
    val MixedPort = StringPreferenceKey(NS, "mixed_port")

    /** Clash 订阅地址（用于拉取节点列表）。 */
    val SubscriptionUrl = StringPreferenceKey(NS, "subscription_url")

    /** 当前选中的订阅节点标识 name（空 = 使用手动填写的主机/端口）。 */
    val SelectedNode = StringPreferenceKey(NS, "selected_node")

    /**
     * 代理接入方式：
     *  - `manual`：用户手填类型/主机/端口；
     *  - `clash`：用订阅启动内置 mihomo 内核，代理指向其混合端口。
     */
    val Backend = StringPreferenceKey(NS, "backend")
}