package com.app.market.data.remote.google

import android.content.res.Resources
import android.os.Build
import android.os.LocaleList

/**
 * 本机 Build 字段：设置里把「Google Play 设备指纹来源」切成「本机」时，
 * 用这些值覆盖预置的 Pixel 9a 指纹（机型 / 系统版本决定 Play 下发的兼容集）。
 */
internal actual fun localDeviceBuildProperties(): Map<String, String> = buildMap {
    fun putIfValid(key: String, value: String?) {
        val v = value?.trim().orEmpty()
        if (v.isNotEmpty() && v != "unknown") put(key, v)
    }
    // 只替换「机型身份」；ABI/屏幕/语言/系统版本等下发定向字段在
    // localDeviceDeliveryConfig() 里一律取真机值，保证 Play 发下来的 split 和真机兼容。
    putIfValid("Build.BRAND", Build.BRAND)
    putIfValid("Build.MANUFACTURER", Build.MANUFACTURER)
    putIfValid("Build.MODEL", Build.MODEL)
    putIfValid("Build.DEVICE", Build.DEVICE)
    putIfValid("Build.PRODUCT", Build.PRODUCT)
    putIfValid("Build.ID", Build.ID)
    putIfValid("Build.FINGERPRINT", Build.FINGERPRINT)
    putIfValid("UserReadableName", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
}


/**
 * 下发定向字段一律取真机值：Play 按这份配置发 split，配置不是真机就会拿到不兼容的 APKs
 * （ABI 对不上装不上、系统版本低于被发版本 minSdk、dpi 切片错位）。
 */
internal actual fun localDeviceDeliveryConfig(): Map<String, String> = buildMap {
    fun putIfValid(key: String, value: String?) {
        val v = value?.trim().orEmpty()
        if (v.isNotEmpty() && v != "unknown") put(key, v)
    }
    putIfValid("Platforms", Build.SUPPORTED_ABIS.joinToString(","))
    putIfValid("Build.VERSION.SDK_INT", Build.VERSION.SDK_INT.toString())
    putIfValid("Build.VERSION.RELEASE", Build.VERSION.RELEASE)
    val metrics = Resources.getSystem().displayMetrics
    putIfValid("Screen.Density", metrics.densityDpi.toString())
    // 与预置画像同一约定：按竖屏记录，宽 < 高
    putIfValid("Screen.Width", minOf(metrics.widthPixels, metrics.heightPixels).toString())
    putIfValid("Screen.Height", maxOf(metrics.widthPixels, metrics.heightPixels).toString())
    // 尺寸档位沿用预置画像的记法（仅 size 档：1 小 / 2 正常 / 3 大 / 4 超大）
    val swDp = minOf(metrics.widthPixels, metrics.heightPixels) / metrics.density
    putIfValid("ScreenLayout", when {
        swDp < 320f -> "1"
        swDp < 480f -> "2"
        swDp < 720f -> "3"
        else -> "4"
    })
    // 语言条目与预置画像同构：语言 + 语言_地区 各一条
    val locales = buildList {
        val list = LocaleList.getDefault()
        for (i in 0 until list.size()) {
            val locale = list[i]
            add(locale.language)
            add(locale.toString())
        }
    }.distinct()
    putIfValid("Locales", locales.joinToString(","))
}
