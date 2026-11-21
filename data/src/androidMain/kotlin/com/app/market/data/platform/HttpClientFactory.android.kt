package com.app.market.data.platform

import com.app.market.data.proxy.TunnelDns
import com.app.market.data.proxy.TunnelSocketFactory
import com.app.market.domain.repository.ProxySettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/** Android uses the platform TLS stack required by vendor risk control. */
fun createAndroidHttpClient(proxy: ProxySettingsRepository): HttpClient = HttpClient(OkHttp) {
    installSharedHttp()
    engine {
        config {
            // 不再指向本地端口：命中代理规则的请求由 TunnelSocketFactory 直接走 AnyTLS 会话。
            // 域名用假地址透传给 connect()，让远端节点解析 DNS。
            dns(TunnelDns(proxy))
            socketFactory(TunnelSocketFactory(proxy))
        }
    }
}