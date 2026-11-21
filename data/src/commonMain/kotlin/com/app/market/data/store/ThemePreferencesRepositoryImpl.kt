package com.app.market.data.store

import com.app.market.data.local.PreferencesDataSource
import com.app.market.data.local.preferences.ThemePreferenceKeys
import com.app.market.data.platform.ThemePlatformPreferences
import com.app.market.data.platform.debugLog
import com.app.market.domain.model.theme.DownloadSourceBadge
import com.app.market.domain.model.theme.ThemeColorMode
import com.app.market.domain.model.theme.ThemeColorSource
import com.app.market.domain.model.theme.ThemeColorSpec
import com.app.market.domain.model.theme.ThemePaletteStyle
import com.app.market.domain.repository.ThemePreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ThemePreferencesRepositoryImpl(
    private val preferences: PreferencesDataSource,
    private val scope: CoroutineScope,
    private val platformPreferences: ThemePlatformPreferences,
) : ThemePreferencesRepository {
    private val _initialized = MutableStateFlow(false)
    override val initialized: StateFlow<Boolean> = _initialized.asStateFlow()
    private val _enableBlur = MutableStateFlow(ThemePreferenceKeys.EnableBlur.default)
    override val enableBlur: StateFlow<Boolean> = _enableBlur.asStateFlow()
    private val _enableFloatingBottomBar = MutableStateFlow(false)
    override val enableFloatingBottomBar: StateFlow<Boolean> = _enableFloatingBottomBar.asStateFlow()
    private val _enableFloatingBottomBarBlur = MutableStateFlow(false)
    override val enableFloatingBottomBarBlur: StateFlow<Boolean> = _enableFloatingBottomBarBlur.asStateFlow()
    private val _liquidBottomBarStyle = MutableStateFlow("")
    override val liquidBottomBarStyle: StateFlow<String> = _liquidBottomBarStyle.asStateFlow()
    private val _topRightFunctions = MutableStateFlow(DEFAULT_TOP_RIGHT_FUNCTIONS)
    override val topRightFunctions: StateFlow<Map<String, String>> = _topRightFunctions.asStateFlow()
    private val _enableNavigationBadge = MutableStateFlow(ThemePreferenceKeys.EnableNavigationBadge.default)
    override val enableNavigationBadge: StateFlow<Boolean> = _enableNavigationBadge.asStateFlow()
    private val _navRailExpanded = MutableStateFlow(ThemePreferenceKeys.NavRailExpanded.default)
    override val navRailExpanded: StateFlow<Boolean> = _navRailExpanded.asStateFlow()
    private val _enablePredictiveBack = MutableStateFlow(false)
    override val enablePredictiveBack: StateFlow<Boolean> = _enablePredictiveBack.asStateFlow()
    private val _pageScale = MutableStateFlow(1f)
    override val pageScale: StateFlow<Float> = _pageScale.asStateFlow()
    private val _bottomBarScaleOs4 = MutableStateFlow(1f)
    override val bottomBarScaleOs4: StateFlow<Float> = _bottomBarScaleOs4.asStateFlow()
    private val _bottomBarScaleClassic = MutableStateFlow(1f)
    override val bottomBarScaleClassic: StateFlow<Float> = _bottomBarScaleClassic.asStateFlow()
    private val _bottomBarHeightOs4 = MutableStateFlow(24f)
    override val bottomBarHeightOs4: StateFlow<Float> = _bottomBarHeightOs4.asStateFlow()
    private val _bottomBarHeightClassic = MutableStateFlow(24f)
    override val bottomBarHeightClassic: StateFlow<Float> = _bottomBarHeightClassic.asStateFlow()
    private val _colorMode = MutableStateFlow(ThemeColorMode.SYSTEM)
    override val colorMode: StateFlow<ThemeColorMode> = _colorMode.asStateFlow()
    private val _colorSource = MutableStateFlow(ThemeColorSource.CUSTOM)
    override val colorSource: StateFlow<ThemeColorSource> = _colorSource.asStateFlow()
    private val _seedColor = MutableStateFlow<Long?>(null)
    override val seedColor: StateFlow<Long?> = _seedColor.asStateFlow()
    private val _paletteStyle = MutableStateFlow(ThemePaletteStyle.FRUIT_SALAD)
    override val paletteStyle: StateFlow<ThemePaletteStyle> = _paletteStyle.asStateFlow()
    private val _colorSpec = MutableStateFlow(ThemeColorSpec.SPEC_2021)
    override val colorSpec: StateFlow<ThemeColorSpec> = _colorSpec.asStateFlow()
    private val _amoledDark = MutableStateFlow(false)
    override val amoledDark: StateFlow<Boolean> = _amoledDark.asStateFlow()
    private val _downloadSourceBadge = MutableStateFlow(DownloadSourceBadge.BRAND)
    override val downloadSourceBadge: StateFlow<DownloadSourceBadge> = _downloadSourceBadge.asStateFlow()
    private val _hiddenTabs = MutableStateFlow<Set<String>>(emptySet())
    override val hiddenTabs: StateFlow<Set<String>> = _hiddenTabs.asStateFlow()
    private val _bottomBarTabOrder = MutableStateFlow<List<String>>(emptyList())
    override val bottomBarTabOrder: StateFlow<List<String>> = _bottomBarTabOrder.asStateFlow()

    init {
        val arrivals = listOf(
            observe("enableBlur", preferences.observe(ThemePreferenceKeys.EnableBlur)) { _enableBlur.value = it },
            observe("enableFloatingBottomBar", preferences.observe(ThemePreferenceKeys.EnableFloatingBottomBar)) {
                _enableFloatingBottomBar.value = it
            },
            observe("enableFloatingBottomBarBlur", preferences.observe(ThemePreferenceKeys.EnableFloatingBottomBarBlur)) {
                _enableFloatingBottomBarBlur.value = it
            },
            observe("liquidBottomBarStyle", preferences.observe(ThemePreferenceKeys.LiquidBottomBarStyle)) { raw ->
                // 未设置时从旧布尔迁移：旧开关开 = os4 液态，关 = off
                _liquidBottomBarStyle.value = when (val v = raw.orEmpty()) {
                    "off", "classic", "os4" -> v
                    else -> if (_enableFloatingBottomBarBlur.value) "os4" else "off"
                }
            },
            observe("topRightFunctions", preferences.observe(ThemePreferenceKeys.TopRightFunctions)) {
                _topRightFunctions.value = parseTopRightFunctions(it.orEmpty())
            },
            observe("enableNavigationBadge", preferences.observe(ThemePreferenceKeys.EnableNavigationBadge)) {
                _enableNavigationBadge.value = it
            },
            observe("navRailExpanded", preferences.observe(ThemePreferenceKeys.NavRailExpanded)) {
                _navRailExpanded.value = it
            },
            observe("enablePredictiveBack", preferences.observe(ThemePreferenceKeys.EnablePredictiveBack)) {
                _enablePredictiveBack.value = it
                platformPreferences.setPredictiveBackEnabled(it)
            },
            observe("pageScale", preferences.observe(ThemePreferenceKeys.PageScale)) {
                _pageScale.value = it?.toFloatOrNull()?.coerceIn(0.8f, 1.1f) ?: 1f
            },
            observe("bottomBarScaleOs4", preferences.observe(ThemePreferenceKeys.BottomBarScaleOs4)) {
                _bottomBarScaleOs4.value = it?.toFloatOrNull()?.coerceIn(0.8f, 1.2f) ?: 1f
            },
            observe("bottomBarScaleClassic", preferences.observe(ThemePreferenceKeys.BottomBarScaleClassic)) {
                _bottomBarScaleClassic.value = it?.toFloatOrNull()?.coerceIn(0.8f, 1.2f) ?: 1f
            },
            observe("bottomBarHeightOs4", preferences.observe(ThemePreferenceKeys.BottomBarHeightOs4)) {
                _bottomBarHeightOs4.value = it?.toFloatOrNull()?.coerceIn(8f, 36f) ?: 24f
            },
            observe("bottomBarHeightClassic", preferences.observe(ThemePreferenceKeys.BottomBarHeightClassic)) {
                _bottomBarHeightClassic.value = it?.toFloatOrNull()?.coerceIn(8f, 36f) ?: 24f
            },
            observe("colorMode", preferences.observe(ThemePreferenceKeys.ColorMode)) {
                _colorMode.value = ThemeColorMode.fromKey(it)
            },
            observe("colorSource", preferences.observe(ThemePreferenceKeys.ColorSource)) {
                _colorSource.value = ThemeColorSource.fromKey(it)
            },
            observe("seedColor", preferences.observe(ThemePreferenceKeys.SeedColor)) { raw ->
                _seedColor.value = raw?.toLongOrNull()
            },
            observe("paletteStyle", preferences.observe(ThemePreferenceKeys.PaletteStyle)) {
                _paletteStyle.value = ThemePaletteStyle.fromKey(it)
            },
            observe("colorSpec", preferences.observe(ThemePreferenceKeys.ColorSpec)) {
                _colorSpec.value = ThemeColorSpec.fromKey(it)
            },
            observe("downloadSourceBadge", preferences.observe(ThemePreferenceKeys.DownloadSourceBadge)) {
                _downloadSourceBadge.value = DownloadSourceBadge.fromKey(it)
            },
            observe("amoledDark", preferences.observe(ThemePreferenceKeys.AmoledDark)) {
                _amoledDark.value = it
            },
            observe("hiddenTabs", preferences.observe(ThemePreferenceKeys.HiddenTabs)) { raw ->
                _hiddenTabs.value = raw?.split(',')?.mapTo(mutableSetOf(), String::trim).orEmpty()
                    .filterTo(mutableSetOf(), String::isNotEmpty)
            },
            observe("bottomBarTabOrder", preferences.observe(ThemePreferenceKeys.BottomBarTabOrder)) { raw ->
                _bottomBarTabOrder.value = raw?.split(',')?.map(String::trim).orEmpty()
                    .filter(String::isNotEmpty)
            },
        )
        scope.launch {
            arrivals.forEach { it.await() }
            _initialized.value = true
        }
    }

    private fun <T> observe(
        name: String,
        flow: Flow<T>,
        update: (T) -> Unit,
    ): CompletableDeferred<Unit> {
        val firstValue = CompletableDeferred<Unit>()
        scope.launch {
            try {
                flow.collect {
                    update(it)
                    firstValue.complete(Unit)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                debugLog("ThemePreferencesRepository") { "observe $name failed: $error" }
            } finally {
                firstValue.complete(Unit)
            }
        }
        return firstValue
    }

    override suspend fun setDownloadSourceBadge(value: DownloadSourceBadge) =
        preferences.put(ThemePreferenceKeys.DownloadSourceBadge, value.key)

    override suspend fun setEnableBlur(value: Boolean) = preferences.put(ThemePreferenceKeys.EnableBlur, value)
    override suspend fun setEnableFloatingBottomBar(value: Boolean) = preferences.put(ThemePreferenceKeys.EnableFloatingBottomBar, value)

    override suspend fun setEnableFloatingBottomBarBlur(value: Boolean) =
        preferences.put(ThemePreferenceKeys.EnableFloatingBottomBarBlur, value)
    override suspend fun setLiquidBottomBarStyle(value: String) {
        _liquidBottomBarStyle.value = value
        preferences.put(ThemePreferenceKeys.LiquidBottomBarStyle, value)
    }

    override suspend fun setTopRightFunction(pageKey: String, value: String) {
        val next = _topRightFunctions.value + (pageKey to value)
        _topRightFunctions.value = next
        preferences.put(ThemePreferenceKeys.TopRightFunctions, serializeTopRightFunctions(next))
    }

    override suspend fun setEnableNavigationBadge(value: Boolean) = preferences.put(ThemePreferenceKeys.EnableNavigationBadge, value)
    override suspend fun setNavRailExpanded(value: Boolean) = preferences.put(ThemePreferenceKeys.NavRailExpanded, value)
    override suspend fun setEnablePredictiveBack(value: Boolean) {
        platformPreferences.setPredictiveBackEnabled(value)
        preferences.put(ThemePreferenceKeys.EnablePredictiveBack, value)
    }

    override suspend fun setPageScale(value: Float) =
        preferences.put(ThemePreferenceKeys.PageScale, value.coerceIn(0.8f, 1.1f).toString())
    override suspend fun setBottomBarScale(style: String, value: Float) {
        val v = value.coerceIn(0.8f, 1.2f)
        val key = if (style == "classic") ThemePreferenceKeys.BottomBarScaleClassic else ThemePreferenceKeys.BottomBarScaleOs4
        preferences.put(key, v.toString())
    }
    override suspend fun setBottomBarHeight(style: String, value: Float) {
        val v = value.coerceIn(8f, 36f)
        val key = if (style == "classic") ThemePreferenceKeys.BottomBarHeightClassic else ThemePreferenceKeys.BottomBarHeightOs4
        preferences.put(key, v.toString())
    }

    override suspend fun setColorMode(value: ThemeColorMode) =
        preferences.put(ThemePreferenceKeys.ColorMode, value.key)

    override suspend fun setColorSource(value: ThemeColorSource) =
        preferences.put(ThemePreferenceKeys.ColorSource, value.key)

    override suspend fun setSeedColor(value: Long?) {
        if (value == null) {
            preferences.remove(ThemePreferenceKeys.SeedColor)
        } else {
            preferences.put(ThemePreferenceKeys.SeedColor, value.toString())
        }
    }

    override suspend fun setPaletteStyle(value: ThemePaletteStyle) =
        preferences.put(ThemePreferenceKeys.PaletteStyle, value.key)

    override suspend fun setColorSpec(value: ThemeColorSpec) =
        preferences.put(ThemePreferenceKeys.ColorSpec, value.key)

override suspend fun setAmoledDark(value: Boolean) = preferences.put(ThemePreferenceKeys.AmoledDark, value)
    override suspend fun setBottomBarTabOrder(order: List<String>) {
        val cleaned = order.map(String::trim).filter(String::isNotEmpty)
        _bottomBarTabOrder.value = cleaned
        preferences.put(ThemePreferenceKeys.BottomBarTabOrder, cleaned.joinToString(","))
    }

    override suspend fun setTabHidden(key: String, hidden: Boolean) {
        val updated = _hiddenTabs.value.toMutableSet().apply {
            if (hidden) add(key) else remove(key)
        }
        _hiddenTabs.value = updated
        preferences.put(ThemePreferenceKeys.HiddenTabs, updated.joinToString(","))
    }

    private companion object {
        /** 页面 key 与主界面 / 主题设置共用。 */
        val TOP_RIGHT_PAGES = listOf("today", "ranking", "updates", "search", "downloads", "settings")

        /** 默认全部为「下载」，「下载」页自身不允许下载按钮，退回关闭。 */
        val DEFAULT_TOP_RIGHT_FUNCTIONS: Map<String, String> =
            TOP_RIGHT_PAGES.associateWith { if (it == "downloads") "off" else "download" }

        fun parseTopRightFunctions(raw: String): Map<String, String> {
            val parsed = raw.split(',').mapNotNull { pair ->
                val idx = pair.indexOf(':')
                if (idx <= 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
            }.toMap()
            return DEFAULT_TOP_RIGHT_FUNCTIONS + parsed
        }

        fun serializeTopRightFunctions(map: Map<String, String>): String =
            map.entries.joinToString(",") { "${it.key}:${it.value}" }
    }
}
