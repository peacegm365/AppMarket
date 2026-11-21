package com.app.market.platform

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders

internal expect fun imageDiskCache(context: PlatformContext): DiskCache?

/**
 * Registers Coil's singleton loader with the Koin-managed data-layer HTTP client.
 */
/**
 * 酷安图床（image.coolapk.com / avatar.coolapk.com 等）按 User-Agent 拦截：
 * 不带 UA 或带 Android 默认 UA 一律返回 `567 Unknown Status`，只有浏览器 UA 才正常出图
 * （实测同一条 URL：无 UA=567，浏览器 UA=200）。所以图片加载单独补一个浏览器 UA。
 *
 * 这里从共享的 [httpClient] 派生一个只给 Coil 用的 client，不影响各来源 API 自己的 UA/签名。
 */
private const val ImageUserAgent =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"

/** 抓包确认酷安图床的真实客户端请求带 referer（api.coolapk.com），按域补发，其余域不带。 */
private const val CoolapkImageReferer = "https://api.coolapk.com/?ref=CoolMarket/16.7.1-2611091-universal"

/**
 * 图片请求修正：
 *  - 部分 CDN（应用宝 pp.myapp.com 等）按 Accept 协商格式（vary: Accept），声明 webp/avif
 *    会拿到设备 Skia 无法解码的变体（ImageDecoder 'unimplemented'，酷安图标即此问题，
 *    实测该 URL 的 PNG 原图可解码、协商出的 VP8X+ALPH WebP 不可解码）。
 *    强制只声明 PNG/JPEG，拿原始可解码格式。
 *  - 酷安图床按域补发 referer（抓包确认）。
 */
private val ImageRequestFixups = createClientPlugin("ImageRequestFixups") {
    onRequest { request, _ ->
        request.headers.set(HttpHeaders.Accept, "image/png,image/jpeg,image/*;q=0.8,*/*;q=0.5")
        if (request.url.host.endsWith("coolapk.com")) {
            request.headers.append(HttpHeaders.Referrer, CoolapkImageReferer)
        }
    }
}

fun setupImageLoader(httpClient: HttpClient) {
    val imageClient = httpClient.config {
        install(DefaultRequest) {
            // set 而非 append：避免与底层默认 UA 叠加出双 UA 头被图床拒（567）
            headers.set(HttpHeaders.UserAgent, ImageUserAgent)
        }
        install(ImageRequestFixups)
    }
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { imageClient })) }
            .apply { imageDiskCache(context)?.let(::diskCache) }
            .build()
    }
}
