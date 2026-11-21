package com.app.market.data.platform

import com.app.market.domain.model.market.AppSource

/**
 * 各来源的请求域名 → [AppSource] 映射。
 *
 * 代理只能按「目标 URI」判断走不走，所以要先由 host 反查来源，
 * 再按白/黑名单决定是否代理。只收录各来源实际会请求的主机；
 * 未命中（如图床 CDN、第三方统计）一律视为「无来源」，按直连处理。
 */
object ProxyHosts {
    private val table: List<Pair<String, AppSource>> = listOf(
        // 小米
        // F-Droid
        "f-droid.org" to AppSource.FDROID,
        "market.xiaomi.com" to AppSource.XIAOMI,
        "app.mi.com" to AppSource.XIAOMI,
        "updateinfo.market.xiaomi.com" to AppSource.XIAOMI,
        // vivo
        "vivo.com.cn" to AppSource.VIVO,
        // 豌豆荚
        "wandoujia.com" to AppSource.WANDOUJIA,
        // OPPO
        "heytapmobi.com" to AppSource.OPPO,
        "heytapmobile.com" to AppSource.OPPO,
        "oppomobile.com" to AppSource.OPPO,
        // 三星
        "samsungapps.com" to AppSource.SAMSUNG,
        // 荣耀
        "hihonorcloud.com" to AppSource.HONOR,
        "hihonorcdn.com" to AppSource.HONOR,
        // 华为
        "dbankcloud.com" to AppSource.HUAWEI,
        "hicloud.com" to AppSource.HUAWEI,
        // TapTap
        "taptapdada.com" to AppSource.TAPTAP,
        "taptap.cn" to AppSource.TAPTAP,
        // Google Play
        "clients.google.com" to AppSource.GOOGLE,
        "play.googleapis.com" to AppSource.GOOGLE,
        "play.google.com" to AppSource.GOOGLE,
        "googleusercontent.com" to AppSource.GOOGLE,
        "auroraoss.com" to AppSource.GOOGLE,
        // 酷安
        "coolapk.com" to AppSource.COOLAPK,
        // 快爆
        "3839.com" to AppSource.KUAIBAO,
        // 应用汇
        "appchina.com" to AppSource.APPCHINA,
        // 应用宝（腾讯）
        "qq.com" to AppSource.TENCENT,
        "myapp.com" to AppSource.TENCENT,
        // APKPure（网页端 + 下载直链）；图床在 winudf.com
        "apkpure.com" to AppSource.APKPURE,
        "winudf.com" to AppSource.APKPURE,
        // Aptoide（WebServices + 官方 CDN）
        "aptoide.com" to AppSource.APTOIDE,
        // GitHub
        "github.com" to AppSource.GITHUB,
        "githubusercontent.com" to AppSource.GITHUB,
        // LSPosed
        "lsposed.org" to AppSource.LSPOSED,
        // GitLab
        "gitlab.com" to AppSource.GITLAB,
    )

    /** 反查 host 归属的来源；未命中返回 null（按直连处理）。 */
    fun sourceOf(host: String?): AppSource? {
        val h = host?.lowercase() ?: return null
        if (h.isEmpty()) return null
        return table.firstOrNull { (domain, _) -> h == domain || h.endsWith(".$domain") }?.second
    }
}