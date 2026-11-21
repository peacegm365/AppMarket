package com.app.market.domain.repository

import com.app.market.domain.model.theme.DownloadSourceBadge
import com.app.market.domain.model.theme.ThemeColorMode
import com.app.market.domain.model.theme.ThemeColorSource
import com.app.market.domain.model.theme.ThemeColorSpec
import com.app.market.domain.model.theme.ThemePaletteStyle
import kotlinx.coroutines.flow.StateFlow

/** Persisted appearance and navigation preferences shared by all UI targets. */
interface ThemePreferencesRepository {
    val initialized: StateFlow<Boolean>
    val enableBlur: StateFlow<Boolean>
    val enableFloatingBottomBar: StateFlow<Boolean>
    val enableFloatingBottomBarBlur: StateFlow<Boolean>
    val liquidBottomBarStyle: StateFlow<String>
    /** 每个页面的右上角功能：pageKey -> off / search / download。 */
    val topRightFunctions: StateFlow<Map<String, String>>
    val enableNavigationBadge: StateFlow<Boolean>
    val navRailExpanded: StateFlow<Boolean>
    val enablePredictiveBack: StateFlow<Boolean>
    val pageScale: StateFlow<Float>
    val colorMode: StateFlow<ThemeColorMode>
    val colorSource: StateFlow<ThemeColorSource>
    val seedColor: StateFlow<Long?>
    val paletteStyle: StateFlow<ThemePaletteStyle>
    val colorSpec: StateFlow<ThemeColorSpec>
    val amoledDark: StateFlow<Boolean>

    /** 更新 / 下载安装历史里的来源标记样式。 */
    val downloadSourceBadge: StateFlow<DownloadSourceBadge>

    /** 处于隐藏状态的底栏标签 key（见 BottomBarTab）。 */
    val hiddenTabs: StateFlow<Set<String>>
    /** 底栏标签顺序（BottomBarTab key 列表；空 = 默认顺序）。 */
    val bottomBarTabOrder: StateFlow<List<String>>
    suspend fun setEnableBlur(value: Boolean)
    suspend fun setEnableFloatingBottomBar(value: Boolean)
    suspend fun setEnableFloatingBottomBarBlur(value: Boolean)
    suspend fun setLiquidBottomBarStyle(value: String) {}
    suspend fun setTopRightFunction(pageKey: String, value: String)
    suspend fun setEnableNavigationBadge(value: Boolean)
    suspend fun setNavRailExpanded(value: Boolean)
    suspend fun setEnablePredictiveBack(value: Boolean)
    /** 底栏内容缩放：1f = 默认。OS4 与「经典」底栏各存一份。 */
    val bottomBarScaleOs4: StateFlow<Float>
    val bottomBarScaleClassic: StateFlow<Float>
    /** 底栏高度（dp）：OS4 与「经典」底栏各存一份。 */
    val bottomBarHeightOs4: StateFlow<Float>
    val bottomBarHeightClassic: StateFlow<Float>

    suspend fun setBottomBarScale(style: String, value: Float)
    suspend fun setBottomBarHeight(style: String, value: Float)
    suspend fun setPageScale(value: Float)
    suspend fun setColorMode(value: ThemeColorMode)
    suspend fun setColorSource(value: ThemeColorSource)
    suspend fun setSeedColor(value: Long?)
    suspend fun setPaletteStyle(value: ThemePaletteStyle)
    suspend fun setColorSpec(value: ThemeColorSpec)
    suspend fun setAmoledDark(value: Boolean)
    suspend fun setDownloadSourceBadge(value: DownloadSourceBadge)
    suspend fun setTabHidden(key: String, hidden: Boolean)
    suspend fun setBottomBarTabOrder(order: List<String>)
}
