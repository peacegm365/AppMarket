package com.app.market.ui.navigation

import com.app.market.domain.model.market.AppSource
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data class AppDetail(
        val appId: Long,
        val packageName: String,
        val displayName: String,
        val externalQuery: String? = null,
        val source: AppSource = AppSource.XIAOMI,
    ) : Route

    @Serializable
    data class HistoricalVersions(
        val appId: Long,
        val packageName: String,
        val displayName: String,
    ) : Route

    @Serializable
    data class TodayArticle(val rId: String) : Route

    @Serializable
    data object DeviceProfile : Route

    @Serializable
    data object IgnoredApps : Route
    @Serializable
    data object Search : Route

    @Serializable
    data object UpdateHistory : Route

    @Serializable
    data object InstallHistory : Route

    @Serializable
    data object SavedPackages : Route

    @Serializable
    data object DownloadingApps : Route

    @Serializable
    data object InstallerSettings : Route
    @Serializable
    data object MultiThreadSettings : Route

    @Serializable
    data object About : Route

    @Serializable
    data object ThemeSettings : Route
    /** 代理设置二级页（Clash 订阅 / 白黑名单 / 按来源开关）。 */
    @Serializable
    data object ProxySettings : Route
    /** 杂项二级页（名称精简 / 剪贴板识别 / 精确搜索 / 日志）。 */
    @Serializable
    data object MiscSettings : Route

    /** 设置-通用-翻译（引擎与 AI 端点配置）。 */
    @Serializable
    data object TranslationSettings : Route

    /** 设置二级菜单分类页（来源/更新/搜索/应用详情/下载与安装/通用）。 */
    @Serializable
    data class SettingsCategory(val category: String) : Route
}