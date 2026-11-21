package com.app.market.data.local.preferences

import com.app.market.data.local.BooleanPreferenceKey
import com.app.market.data.local.StringPreferenceKey

object ThemePreferenceKeys {
    private const val NS = "theme_preferences"
    val EnableBlur = BooleanPreferenceKey(NS, "enable_blur")
    val EnableFloatingBottomBar = BooleanPreferenceKey(NS, "enable_floating_bottom_bar")
    val EnableFloatingBottomBarBlur = BooleanPreferenceKey(NS, "enable_floating_bottom_bar_blur")
    /** 液态底栏三档：off=关闭 / classic=经典玻璃 / os4=OS4 液态玻璃。 */
    val LiquidBottomBarStyle = StringPreferenceKey(NS, "liquid_bottom_bar_style")
    /** 每个页面的右上角功能：pageKey -> off/search/download，序列化 key:value 逗号分隔。 */
    val TopRightFunctions = StringPreferenceKey(NS, "top_right_functions")
    val EnableNavigationBadge = BooleanPreferenceKey(NS, "enable_navigation_badge", true)
    val NavRailExpanded = BooleanPreferenceKey(NS, "nav_rail_expanded")
    val EnablePredictiveBack = BooleanPreferenceKey(NS, "enable_predictive_back")
    val PageScale = StringPreferenceKey(NS, "page_scale")
    val ColorMode = StringPreferenceKey(NS, "color_mode")
    val ColorSource = StringPreferenceKey(NS, "color_source")
    val SeedColor = StringPreferenceKey(NS, "seed_color")
    val PaletteStyle = StringPreferenceKey(NS, "palette_style")
    val ColorSpec = StringPreferenceKey(NS, "color_spec")
    val AmoledDark = BooleanPreferenceKey(NS, "amoled_dark")
    val HiddenTabs = StringPreferenceKey(NS, "hidden_tabs")
    /** 底栏标签顺序（BottomBarTab key 逗号分隔，空 = 默认顺序）。 */
    val BottomBarTabOrder = StringPreferenceKey(NS, "bottom_bar_tab_order")
    val DownloadSourceBadge = StringPreferenceKey(NS, "download_source_badge")
    /** 底栏外观：OS4 悬浮底栏与「经典」液态底栏各自独立存储（数值以字符串保存）。 */
    val BottomBarScaleOs4 = StringPreferenceKey(NS, "bottom_bar_scale_os4")
    val BottomBarHeightOs4 = StringPreferenceKey(NS, "bottom_bar_height_os4")
    val BottomBarScaleClassic = StringPreferenceKey(NS, "bottom_bar_scale_classic")
    val BottomBarHeightClassic = StringPreferenceKey(NS, "bottom_bar_height_classic")
}
