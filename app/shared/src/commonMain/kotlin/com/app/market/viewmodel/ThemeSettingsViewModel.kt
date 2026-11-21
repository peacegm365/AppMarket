package com.app.market.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.model.theme.DownloadSourceBadge
import com.app.market.domain.model.theme.ThemeColorMode
import com.app.market.domain.model.theme.ThemeColorSource
import com.app.market.domain.model.theme.ThemeColorSpec
import com.app.market.domain.model.theme.ThemePaletteStyle
import com.app.market.domain.repository.ThemePreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ThemeSettingsUiState(
    val enableBlur: Boolean = false,
    val enableFloatingBottomBar: Boolean = false,
    val enableFloatingBottomBarBlur: Boolean = false,
    val liquidBottomBarStyle: String = "off",
    val enableNavigationBadge: Boolean = true,
    val enablePredictiveBack: Boolean = false,
    val pageScale: Float = 1f,
    /** 底栏外观：OS4 与「经典」各存一份（缩放 0.8~1.3，距底距离 8~48dp）。 */
    val bottomBarScaleOs4: Float = 1f,
    val bottomBarHeightOs4: Float = 24f,
    val bottomBarScaleClassic: Float = 1f,
    val bottomBarHeightClassic: Float = 24f,
    val colorMode: ThemeColorMode = ThemeColorMode.SYSTEM,
    val colorSource: ThemeColorSource = ThemeColorSource.DEFAULT,
    val seedColor: Long? = null,
    val paletteStyle: ThemePaletteStyle = ThemePaletteStyle.TONAL_SPOT,
    val colorSpec: ThemeColorSpec = ThemeColorSpec.SPEC_2021,
    val amoledDark: Boolean = false,
    val downloadSourceBadge: DownloadSourceBadge = DownloadSourceBadge.BRAND,
)

class ThemeSettingsViewModel(
    private val preferences: ThemePreferencesRepository,
    private val updatePreferences: com.app.market.domain.repository.UpdatePreferencesRepository,
) : ViewModel() {
    /** 启动页 = 应用启动后打开的第一个页面；与通用设置同一份存储。 */
    val homePage: StateFlow<com.app.market.domain.model.preference.HomePage> = updatePreferences.homePage
    fun setHomePage(value: com.app.market.domain.model.preference.HomePage) {
        viewModelScope.launch { updatePreferences.setHomePage(value) }
    }
    private val appearance = combine(preferences.enableBlur, preferences.enableFloatingBottomBar) { blur, floating ->
        blur to floating
    }

    private val glassStyle = combine(preferences.enableFloatingBottomBarBlur, preferences.liquidBottomBarStyle) { legacy, style ->
        legacy to style
    }
    private val bottomBarLook = combine(
        preferences.bottomBarScaleOs4,
        preferences.bottomBarHeightOs4,
        preferences.bottomBarScaleClassic,
        preferences.bottomBarHeightClassic,
    ) { scaleOs4, heightOs4, scaleClassic, heightClassic ->
        floatArrayOf(scaleOs4, heightOs4, scaleClassic, heightClassic)
    }
    private val appearanceState = combine(
        appearance,
        glassStyle,
        preferences.enableNavigationBadge,
        preferences.enablePredictiveBack,
    ) { appearance, glass, badge, predictiveBack ->
        ThemeSettingsUiState(
            enableBlur = appearance.first,
            enableFloatingBottomBar = appearance.second,
            enableFloatingBottomBarBlur = glass.first,
            liquidBottomBarStyle = glass.second.ifEmpty { if (glass.first) "os4" else "off" },
            enableNavigationBadge = badge,
            enablePredictiveBack = predictiveBack,
        )
    }
    private val baseState: StateFlow<ThemeSettingsUiState> = combine(
        appearanceState,
        preferences.pageScale,
        bottomBarLook,
    ) { base, scale, look ->
        base.copy(
            pageScale = scale,
            bottomBarScaleOs4 = look[0],
            bottomBarHeightOs4 = look[1],
            bottomBarScaleClassic = look[2],
            bottomBarHeightClassic = look[3],
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSettingsUiState())

    private val colorState: StateFlow<ThemeSettingsUiState> = combine(
        preferences.colorMode,
        preferences.colorSource,
        preferences.seedColor,
        preferences.paletteStyle,
        preferences.colorSpec,
    ) { mode, source, seed, style, spec ->
        ThemeSettingsUiState(
            colorMode = mode,
            colorSource = source,
            seedColor = seed,
            paletteStyle = style,
            colorSpec = spec,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSettingsUiState())

    val uiState: StateFlow<ThemeSettingsUiState> = combine(
        baseState,
        colorState,
        preferences.amoledDark,
        preferences.downloadSourceBadge,
    ) { base, color, amoled, badge ->
        base.copy(
            downloadSourceBadge = badge,
            colorMode = color.colorMode,
            colorSource = color.colorSource,
            seedColor = color.seedColor,
            paletteStyle = color.paletteStyle,
            colorSpec = color.colorSpec,
            amoledDark = amoled,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSettingsUiState())
    val topRightFunctions: StateFlow<Map<String, String>> = preferences.topRightFunctions
    val hiddenTabs: StateFlow<Set<String>> = preferences.hiddenTabs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val bottomBarTabOrder: StateFlow<List<String>> = preferences.bottomBarTabOrder
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun setEnableBlur(value: Boolean) = persist { preferences.setEnableBlur(value) }
    fun setEnableFloatingBottomBar(value: Boolean) = persist { preferences.setEnableFloatingBottomBar(value) }
    fun setEnableFloatingBottomBarBlur(value: Boolean) = persist { preferences.setEnableFloatingBottomBarBlur(value) }
    fun setLiquidBottomBarStyle(value: String) = persist { preferences.setLiquidBottomBarStyle(value) }
    fun setTopRightFunction(pageKey: String, value: String) = persist { preferences.setTopRightFunction(pageKey, value) }
    fun setEnableNavigationBadge(value: Boolean) = persist { preferences.setEnableNavigationBadge(value) }
    fun setTabHidden(key: String, hidden: Boolean) = persist { preferences.setTabHidden(key, hidden) }
    fun setBottomBarTabOrder(order: List<String>) = persist { preferences.setBottomBarTabOrder(order) }
    fun setEnablePredictiveBack(value: Boolean) = persist { preferences.setEnablePredictiveBack(value) }
    fun setPageScale(value: Float) = persist { preferences.setPageScale(value) }
    fun setBottomBarScale(style: String, value: Float) = persist { preferences.setBottomBarScale(style, value) }
    fun setBottomBarHeight(style: String, value: Float) = persist { preferences.setBottomBarHeight(style, value) }
    fun setColorMode(value: ThemeColorMode) = persist { preferences.setColorMode(value) }
    fun setColorSource(value: ThemeColorSource) = persist { preferences.setColorSource(value) }
    fun setSeedColor(value: Long?) = persist { preferences.setSeedColor(value) }
    fun setPaletteStyle(value: ThemePaletteStyle) = persist { preferences.setPaletteStyle(value) }
    fun setColorSpec(value: ThemeColorSpec) = persist { preferences.setColorSpec(value) }
    fun setAmoledDark(value: Boolean) = persist { preferences.setAmoledDark(value) }
    fun setDownloadSourceBadge(value: DownloadSourceBadge) =
        persist { preferences.setDownloadSourceBadge(value) }

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
