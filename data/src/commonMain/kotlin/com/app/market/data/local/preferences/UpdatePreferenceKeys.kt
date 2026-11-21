package com.app.market.data.local.preferences

import com.app.market.data.local.BooleanPreferenceKey
import com.app.market.data.local.StringPreferenceKey

object UpdatePreferenceKeys {
    private const val NS = "update_preferences"
    val ShowSystemUpdates = BooleanPreferenceKey(NS, "show_system_updates", true)
    val RemoveSearchAds = BooleanPreferenceKey(NS, "remove_search_ads")
    val FilterQuickGames = BooleanPreferenceKey(NS, "filter_quick_games")
    val FilterReservationApps = BooleanPreferenceKey(NS, "filter_reservation_apps")
        val StripAppNameSubtitle = BooleanPreferenceKey(NS, "strip_app_name_subtitle")
    val GooglePlayEngine = StringPreferenceKey(NS, "google_play_engine")
    val DetectClipboardLinks = BooleanPreferenceKey(NS, "detect_clipboard_links", true)
    /** 搜索只保留标题/包名命中的条目，丢掉商店推荐的无关项。 */
    val ExactSearch = BooleanPreferenceKey(NS, "exact_search", true)
    /** Release 来源（GitHub/GitLab/LSPosed）是否把预发布版本也当作可选更新。 */
    val IncludePrereleaseUpdates = BooleanPreferenceKey(NS, "include_prerelease_updates", false)
    /** 是否写入崩溃/诊断日志；关闭后不再在 Download 目录创建日志文件。 */
    val LogEnabled = BooleanPreferenceKey(NS, "log_enabled", true)
    /** 日志文件体积上限（字节，字符串存数字）；超限截断旧内容只留最新，`0` = 不限制。 */
    val LogMaxSize = StringPreferenceKey(NS, "log_max_size")
    /** 安装成功后自动删除已保存的安装包。 */
    val AutoDeletePackage = BooleanPreferenceKey(NS, "auto_delete_package", true)
    val LastClipboardLink = StringPreferenceKey(NS, "last_clipboard_link")
    /** 手动检查更新的最近查询：每行 `包名 版本号`，最多 8 条。 */
    val ManualUpdateRecents = StringPreferenceKey(NS, "manual_update_recents")
    /** Google Play 商店地区：auto=跟随出口 IP，其余为 ISO-3166 两位国家码。 */
    val GoogleRegion = StringPreferenceKey(NS, "google_region")
    /** Google Play 设备信息自定义覆盖：`key=value` 每行一条。 */
    val GoogleDeviceOverrides = StringPreferenceKey(NS, "google_device_overrides")
    /** Google Play 设备信息模板：模板名 → 字段覆盖。 */
    val GoogleDeviceTemplates = StringPreferenceKey(NS, "google_device_templates")
    /** Google Play device source: false=preset (Pixel 9a), true=from device (local Build). */
    val GoogleDeviceSource = BooleanPreferenceKey(NS, "google_device_source", false)
    val HomePage = StringPreferenceKey(NS, "home_page")
    val SearchSources = StringPreferenceKey(NS, "search_sources")

    /** 旧版游戏/应用分区来源键（v2.3.3）；仅为迁移保留，读写不再走这两个键。 */
    val LegacyGameSources = StringPreferenceKey(NS, "game_sources")
    val LegacyAppSources = StringPreferenceKey(NS, "app_sources")

    val TodaySource = StringPreferenceKey(NS, "today_source")
    val CommentSource = StringPreferenceKey(NS, "comment_source")
    /** 应用页来源（旧键 `category_source`，用于兼容已有安装）。 */
    val CategorySource = StringPreferenceKey(NS, "category_source")
    /** 应用页来源；与游戏页解耦后各存一份。 */
    val AppCategorySource = StringPreferenceKey(NS, "app_category_source")
    /** 游戏页来源；与游戏页解耦后各存一份。 */
    val GameCategorySource = StringPreferenceKey(NS, "game_category_source")
    val UpdateSource = StringPreferenceKey(NS, "update_source")
    val ShowUpdateSource = BooleanPreferenceKey(NS, "show_update_source", true)
    val PermanentIgnores = StringPreferenceKey(NS, "permanent_ignores")
    val OnceIgnores = StringPreferenceKey(NS, "once_ignores")
    val CachedUpdates = StringPreferenceKey(NS, "cached_updates")
    val PageCacheEnabled = BooleanPreferenceKey(NS, "page_cache_enabled", true)
    /** 单来源检查更新超时（秒），空/非法值回退默认值。 */
    val CheckTimeoutSeconds = StringPreferenceKey(NS, "check_timeout_seconds")

    /** 自更新是否接收 GitLab 预发布（Upcoming Release）版本。 */
    val ReceivePrerelease = BooleanPreferenceKey(NS, "receive_prerelease", false)

    /** 更新来源优先级：用户为「全部」来源自定义的检查顺序，空值回退默认顺序。 */
    val UpdateSourcePriority = StringPreferenceKey(NS, "update_source_priority")
}