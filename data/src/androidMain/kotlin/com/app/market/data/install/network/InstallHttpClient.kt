package com.app.market.data.install.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import com.app.market.data.proxy.TunnelDns
import com.app.market.data.proxy.TunnelSocketFactory
import com.app.market.domain.repository.ProxySettingsRepository

internal class InstallHttpClient(private val proxySettings: ProxySettingsRepository) {
    // 用 OkHttp 走 Android 平台 TLS（NetworkSecurityTrustManager 的 3 参 checkServerTrusted）。
    // CIO 用的是自带 JSSE，只调 2 参 checkServerTrusted，遇到 network_security_config 里的
    // <domain-config> 会抛 "Domain specific configurations require that hostname aware
    // checkServerTrusted(X509Certificate[], String, String) is used"，导致下载失败。
    val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = 20_000L
            socketTimeoutMillis = 60_000L
        }
        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(maxRetries = 3)
            exponentialDelay(base = 2.0, maxDelayMs = 8_000)
        }
        engine {
            config {
                // 下载与 API 共用同一套代理规则（AnyTLS 隧道，无本地端口）
                dns(TunnelDns(proxySettings))
                socketFactory(TunnelSocketFactory(proxySettings))
            }
        }
    }
}
