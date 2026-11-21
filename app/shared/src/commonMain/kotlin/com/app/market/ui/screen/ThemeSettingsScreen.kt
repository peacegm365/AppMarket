package com.app.market.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.preference.HomePage
import com.app.market.resources.home_page
import com.app.market.resources.home_page_apps
import com.app.market.resources.home_page_downloads
import com.app.market.resources.home_page_games
import com.app.market.resources.home_page_settings
import com.app.market.resources.home_page_summary
import com.app.market.resources.theme_top_right_action
import com.app.market.resources.theme_top_right_action_summary
import com.app.market.resources.theme_top_right_action_page_summary
import com.app.market.resources.top_right_action_download
import com.app.market.resources.top_right_action_off
import com.app.market.resources.top_right_action_search
import com.app.market.resources.theme_section_startup
import com.app.market.domain.model.theme.BottomBarTab
import com.app.market.domain.model.theme.DownloadSourceBadge
import com.app.market.domain.model.theme.ThemeColorMode
import com.app.market.domain.model.theme.ThemeColorSource
import com.app.market.domain.model.theme.ThemeColorSpec
import com.app.market.domain.model.theme.ThemePaletteStyle
import com.app.market.platform.isBlurSettingSupported
import com.app.market.platform.isPredictiveBackSupported
import com.app.market.resources.Res
import com.app.market.resources.theme_download_source_badge
import com.app.market.resources.theme_download_source_badge_brand
import com.app.market.resources.theme_download_source_badge_off
import com.app.market.resources.theme_download_source_badge_plain
import com.app.market.resources.theme_download_source_badge_summary
import com.app.market.resources.nav_apps
import com.app.market.resources.nav_ranking
import com.app.market.resources.nav_downloads
import com.app.market.resources.nav_games
import com.app.market.resources.nav_search
import com.app.market.resources.nav_settings
import com.app.market.resources.nav_today
import com.app.market.resources.nav_updates
import com.app.market.resources.theme
import com.app.market.resources.theme_accent_color
import com.app.market.resources.theme_accent_default
import com.app.market.resources.theme_amoled
import com.app.market.resources.theme_bottom_bar
import com.app.market.resources.theme_color_spec
import com.app.market.resources.theme_color_spec_2021
import com.app.market.resources.theme_color_spec_2025
import com.app.market.resources.theme_color_spec_unsupported
import com.app.market.resources.theme_custom_color
import com.app.market.resources.theme_dark_mode
import com.app.market.resources.theme_dark_mode_dark
import com.app.market.resources.theme_dark_mode_light
import com.app.market.resources.theme_dark_mode_system
import com.app.market.resources.theme_enable_blur
import com.app.market.resources.theme_enable_blur_summary
import com.app.market.resources.theme_enable_glass
import com.app.market.resources.theme_enable_glass_summary
import com.app.market.resources.theme_floating_bottom_bar
import com.app.market.resources.theme_floating_bottom_bar_summary
import com.app.market.resources.theme_navigation_badge
import com.app.market.resources.theme_navigation_badge_summary
import com.app.market.resources.theme_page_scale
import com.app.market.resources.theme_page_scale_summary
import com.app.market.resources.theme_palette_content
import com.app.market.resources.theme_palette_expressive
import com.app.market.resources.theme_palette_fidelity
import com.app.market.resources.theme_palette_fruit_salad
import com.app.market.resources.theme_palette_monochrome
import com.app.market.resources.theme_palette_neutral
import com.app.market.resources.theme_palette_rainbow
import com.app.market.resources.theme_palette_style
import com.app.market.resources.theme_palette_tonal_spot
import com.app.market.resources.theme_palette_vibrant
import com.app.market.resources.theme_predictive_back
import com.app.market.resources.theme_predictive_back_summary
import com.app.market.resources.theme_section_appearance
import com.app.market.resources.theme_section_interaction
import com.app.market.resources.theme_use_monet
import com.app.market.ui.component.CardSegmentContainer
import com.app.market.ui.component.ColorPickerDialog
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.ui.component.ScaleDialog
import com.app.market.ui.component.SectionTitle
import com.app.market.viewmodel.ThemeSettingsUiState
import com.app.market.viewmodel.ThemeSettingsViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import com.app.market.ui.component.AppSpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import com.app.market.resources.liquid_style_off
import com.app.market.resources.liquid_style_classic
import com.app.market.resources.liquid_style_os4

private const val DEFAULT_SEED_COLOR = 0xFF4285F4L

@Composable
fun ThemeSettingsScreen(
    viewModel: ThemeSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hiddenTabs by viewModel.hiddenTabs.collectAsStateWithLifecycle()
    val bottomBarTabOrder by viewModel.bottomBarTabOrder.collectAsStateWithLifecycle()
    val topRightFunctions by viewModel.topRightFunctions.collectAsStateWithLifecycle()
    // 右上角功能开启的入口自动隐藏对应底栏标签
    val functionHiddenTabs = remember(topRightFunctions) {
        buildSet {
            if (topRightFunctions.values.any { it == "search" }) add(BottomBarTab.SEARCH.key)
            if (topRightFunctions.values.any { it == "download" }) add(BottomBarTab.DOWNLOADS.key)
        }
    }
    // 右上角功能弹框展开态；长按拖动底栏标签时自动收起
    var topRightExpanded by remember { mutableStateOf(false) }
    val homePage by viewModel.homePage.collectAsStateWithLifecycle()
    ThemeSettingsContent(
        state = state,
        onBack = onBack,
        hiddenTabs = hiddenTabs,
        homePage = homePage,
        onHomePageChange = viewModel::setHomePage,
        onEnableBlur = viewModel::setEnableBlur,
        onEnableFloatingBottomBar = viewModel::setEnableFloatingBottomBar,
        onLiquidBottomBarStyleChange = viewModel::setLiquidBottomBarStyle,
        topRightFunctions = topRightFunctions,
        onTopRightFunctionChange = viewModel::setTopRightFunction,
        functionHiddenTabs = functionHiddenTabs,
        topRightExpanded = topRightExpanded,
        onTopRightExpandedChange = { topRightExpanded = it },
        onEnableNavigationBadge = viewModel::setEnableNavigationBadge,
        onTabHidden = viewModel::setTabHidden,
        bottomBarTabOrder = bottomBarTabOrder,
        onBottomBarTabOrderChange = viewModel::setBottomBarTabOrder,
        onEnablePredictiveBack = viewModel::setEnablePredictiveBack,
        onPageScale = viewModel::setPageScale,
        onBottomBarScale = viewModel::setBottomBarScale,
        onBottomBarHeight = viewModel::setBottomBarHeight,
        onColorMode = viewModel::setColorMode,
        onColorSource = viewModel::setColorSource,
        onSeedColor = viewModel::setSeedColor,
        onPaletteStyle = viewModel::setPaletteStyle,
        onColorSpec = viewModel::setColorSpec,
        onAmoledDark = viewModel::setAmoledDark,
        onDownloadSourceBadge = viewModel::setDownloadSourceBadge,
        blurSupported = isBlurSettingSupported(),
        predictiveBackSupported = isPredictiveBackSupported(),
        modifier = modifier,
    )
}

/**
 * 底栏标签：开关显隐 + 长按拖动排序。
 * 拖起的行浮在最上、其余行按目标位置上下挤压让位；松手落位并持久化顺序。
 */
@Composable
private fun ReorderableBottomBarTabs(
    order: List<String>,
    hiddenTabs: Set<String>,
    functionHiddenTabs: Set<String>,
    topRightFunctions: Map<String, String>,
    onTopRightFunctionChange: (String, String) -> Unit,
    onTabHidden: (String, Boolean) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onDragStart: () -> Unit = {},
) {
    // 外部顺序为空 = 默认顺序；拖动期间用本地状态，松手后一次性提交
    var tabs by remember(order, functionHiddenTabs) {
        mutableStateOf(
            BottomBarTab.entries
                .filterNot { it.key in functionHiddenTabs }
                .sortedBy { tab ->
                    order.indexOf(tab.key).takeIf { it >= 0 } ?: Int.MAX_VALUE
                },
        )
    }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(1f) }
    val lastIndex = tabs.lastIndex
    Column {
        tabs.forEachIndexed { index, tab ->
            val from = draggingIndex
            val dragging = index == from
            val to = from?.let { start ->
                (start + (dragOffsetY / rowHeightPx).roundToInt()).coerceIn(0, lastIndex)
            }
            val shiftPx = if (dragging) {
                dragOffsetY
            } else if (from != null && to != null && ((index > from && index <= to) || (index < from && index >= to))) {
                if (index > from) -rowHeightPx else rowHeightPx
            } else {
                0f
            }
            Box(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = shiftPx }
                    .onSizeChanged { rowHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                    .pointerInput(tab.key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                onDragStart()
                                draggingIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffsetY += amount.y
                            },
                            onDragEnd = {
                                val from = draggingIndex
                                val to = from?.let { start ->
                                    (start + (dragOffsetY / rowHeightPx).roundToInt()).coerceIn(0, lastIndex)
                                }
                                if (from != null && to != null && from != to) {
                                    val mutable = tabs.toMutableList()
                                    val moved = mutable.removeAt(from)
                                    mutable.add(to, moved)
                                    tabs = mutable
                                    onOrderChange(mutable.map { it.key })
                                }
                                draggingIndex = null
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffsetY = 0f
                            },
                        )
                    },
            ) {
                CardSegmentContainer(
                    // 整个板块共一张分段卡：第一行负责上方圆角，
                    // 下方圆角由末尾「设置」段（isLast）收底，中间行首尾都不圆
                    isFirst = index == 0,
                    isLast = false,
                    horizontalPadding = 0.dp,
                ) {
                    Column {
                        SwitchPreference(
                            title = bottomBarTabTitle(tab),
                            checked = tab.key !in hiddenTabs,
                            onCheckedChange = { onTabHidden(tab.key, !it) },
                        )
                        // 右上角功能设置拆分到各底栏条目下方（下载无右上角功能）
                        val pageKey = topRightPageKey(tab)
                        if (pageKey != null) {
                            val values = listOf("off", "search", "download")
                                .filterNot { it == "search" && pageKey == "search" }
                            val labels = values.map { value ->
                                stringResource(
                                    when (value) {
                                        "search" -> Res.string.top_right_action_search
                                        "download" -> Res.string.top_right_action_download
                                        else -> Res.string.top_right_action_off
                                    },
                                )
                            }
                            AppSpinnerPreference(
                                items = labels,
                                selectedIndex = values
                                    .indexOf(topRightFunctions[pageKey] ?: "off")
                                    .coerceAtLeast(0),
                                title = stringResource(Res.string.theme_top_right_action),
                                onSelectedIndexChange = { i ->
                                    onTopRightFunctionChange(pageKey, values[i])
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
private fun topRightPageKey(tab: BottomBarTab): String? = when (tab) {
    BottomBarTab.TODAY -> "today"
    BottomBarTab.RANKING -> "ranking"
    BottomBarTab.UPDATES -> "updates"
    BottomBarTab.SEARCH -> "search"
    BottomBarTab.DOWNLOADS -> null
}

@Composable
private fun bottomBarTabTitle(tab: BottomBarTab): String = when (tab) {
    BottomBarTab.TODAY -> stringResource(Res.string.nav_today)
    BottomBarTab.RANKING -> stringResource(Res.string.nav_ranking)
    BottomBarTab.UPDATES -> stringResource(Res.string.nav_updates)
    BottomBarTab.SEARCH -> stringResource(Res.string.nav_search)
    BottomBarTab.DOWNLOADS -> stringResource(Res.string.nav_downloads)
}

@Composable
private fun ThemeSettingsContent(
    state: ThemeSettingsUiState,
    onBack: () -> Unit,
    hiddenTabs: Set<String>,
    homePage: HomePage,
    onHomePageChange: (HomePage) -> Unit,
    onEnableBlur: (Boolean) -> Unit,
    onEnableFloatingBottomBar: (Boolean) -> Unit,
    onLiquidBottomBarStyleChange: (String) -> Unit,
    topRightFunctions: Map<String, String>,
    onTopRightFunctionChange: (String, String) -> Unit,
    functionHiddenTabs: Set<String>,
    topRightExpanded: Boolean,
    onTopRightExpandedChange: (Boolean) -> Unit,
    onEnableNavigationBadge: (Boolean) -> Unit,
    onTabHidden: (String, Boolean) -> Unit,
    bottomBarTabOrder: List<String>,
    onBottomBarTabOrderChange: (List<String>) -> Unit,
    onEnablePredictiveBack: (Boolean) -> Unit,
    onPageScale: (Float) -> Unit,
    onBottomBarScale: (String, Float) -> Unit,
    onBottomBarHeight: (String, Float) -> Unit,
    onColorMode: (ThemeColorMode) -> Unit,
    onColorSource: (ThemeColorSource) -> Unit,
    onSeedColor: (Long?) -> Unit,
    onPaletteStyle: (ThemePaletteStyle) -> Unit,
    onColorSpec: (ThemeColorSpec) -> Unit,
    onAmoledDark: (Boolean) -> Unit,
    onDownloadSourceBadge: (DownloadSourceBadge) -> Unit,
    blurSupported: Boolean,
    predictiveBackSupported: Boolean,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    var sliderValue by remember(state.pageScale) { mutableFloatStateOf(state.pageScale) }
    var showScaleDialog by rememberSaveable { mutableStateOf(false) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }

    MarketScaffold(
        title = stringResource(Res.string.theme),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding, backdropModifier, scrollBehavior ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdropModifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
                top = innerPadding.calculateTopPadding() + PageVerticalPadding,
                bottom = innerPadding.calculateBottomPadding() + PageVerticalPadding,
            ),
        ) {
            item(key = "section-startup") {
                SectionTitle(
                    text = stringResource(Res.string.theme_section_startup),
                    topPadding = 0.dp,
                )
            }
            item(key = "home-page") {
                val homePageOptions = HomePage.entries
                    // 手动隐藏 / 右上角功能接管的标签不作为可选首页
                    .filterNot { it.barTabKey() in hiddenTabs }
                    .filterNot { it.barTabKey() in functionHiddenTabs }
                CardSegmentContainer(isFirst = true, isLast = true, horizontalPadding = 0.dp) {
                    AppSpinnerPreference(
                        items = homePageOptions.map { stringResource(it.labelRes) },
                        selectedIndex = homePageOptions.indexOf(homePage).coerceAtLeast(0),
                        title = stringResource(Res.string.home_page),
                        summary = stringResource(Res.string.home_page_summary),
                        onSelectedIndexChange = { index ->
                            onHomePageChange(homePageOptions[index])
                        },
                    )
                    // 自定义启动图/背景图：未完成，懒得写了，暂时隐藏入口。
                    // com.app.market.platform.StartupImageSettings()
                }
            }
            item(key = "section-appearance") {
                SectionTitle(
                    text = stringResource(Res.string.theme_section_appearance),
                    topPadding = 20.dp,
                )
            }
            val monetEnabled = state.colorSource != ThemeColorSource.DEFAULT
            item(key = "dark-mode") {
                CardSegmentContainer(isFirst = true, isLast = false, horizontalPadding = 0.dp) {
                    AppSpinnerPreference(
                        items = listOf(
                            stringResource(Res.string.theme_dark_mode_system),
                            stringResource(Res.string.theme_dark_mode_light),
                            stringResource(Res.string.theme_dark_mode_dark),
                        ),
                        selectedIndex = colorModeIndex(state.colorMode),
                        title = stringResource(Res.string.theme_dark_mode),
                        onSelectedIndexChange = { index ->
                            onColorMode(colorModeAt(index))
                        },
                    )
                }
            }
            item(key = "amoled") {
                CardSegmentContainer(isFirst = false, isLast = false, horizontalPadding = 0.dp) {
                    SwitchPreference(
                        title = stringResource(Res.string.theme_amoled),
                        checked = state.amoledDark,
                        onCheckedChange = onAmoledDark,
                    )
                }
            }
            item(key = "download-source-badge") {
                CardSegmentContainer(isFirst = false, isLast = false, horizontalPadding = 0.dp) {
                    AppSpinnerPreference(
                        items = DownloadSourceBadge.entries.map { stringResource(downloadSourceBadgeRes(it)) },
                        selectedIndex = DownloadSourceBadge.entries.indexOf(state.downloadSourceBadge)
                            .coerceAtLeast(0),
                        title = stringResource(Res.string.theme_download_source_badge),
                        summary = stringResource(Res.string.theme_download_source_badge_summary),
                        onSelectedIndexChange = { index ->
                            onDownloadSourceBadge(DownloadSourceBadge.entries[index])
                        },
                    )
                }
            }
            item(key = "use-monet") {
                CardSegmentContainer(isFirst = false, isLast = false, horizontalPadding = 0.dp) {
                    SwitchPreference(
                        title = stringResource(Res.string.theme_use_monet),
                        checked = monetEnabled,
                        onCheckedChange = { on ->
                            onColorSource(
                                if (on) {
                                    if (state.seedColor != null) ThemeColorSource.CUSTOM else ThemeColorSource.MONET
                                } else {
                                    ThemeColorSource.DEFAULT
                                }
                            )
                        },
                    )
                }
            }

                item(key = "palette-style") {
                AnimatedVisibility(
                    visible = monetEnabled,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
                ) {
                    CardSegmentContainer(isFirst = false, isLast = false, horizontalPadding = 0.dp) {
                        AppSpinnerPreference(
                            items = ThemePaletteStyle.entries.map { stringResource(paletteStyleRes(it)) },
                            selectedIndex = ThemePaletteStyle.entries.indexOf(state.paletteStyle),
                            title = stringResource(Res.string.theme_palette_style),
                            startAction = { PaletteDots() },
                            onSelectedIndexChange = { index ->
                                onPaletteStyle(ThemePaletteStyle.entries[index])
                            },
                        )
                    }
                
                }
            }
                item(key = "color-spec") {
                AnimatedVisibility(
                    visible = monetEnabled,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
                ) {
                    CardSegmentContainer(isFirst = false, isLast = false, horizontalPadding = 0.dp) {
                        AppSpinnerPreference(
                            items = listOf(
                                stringResource(Res.string.theme_color_spec_2021),
                                stringResource(Res.string.theme_color_spec_2025),
                            ),
                            selectedIndex = colorSpecIndex(state.colorSpec),
                            title = stringResource(Res.string.theme_color_spec),
                            summary = if (state.paletteStyle.supportsSpec2025) {
                                null
                            } else {
                                stringResource(Res.string.theme_color_spec_unsupported)
                            },
                            enabled = state.paletteStyle.supportsSpec2025,
                            onSelectedIndexChange = { index ->
                                onColorSpec(colorSpecAt(index))
                            },
                        )
                    }
                
                }
            }
                item(key = "accent-color") {
                AnimatedVisibility(
                    visible = monetEnabled,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
                ) {
                    val seedColor = state.seedColor
                    CardSegmentContainer(isFirst = false, isLast = false, horizontalPadding = 0.dp) {
                        ArrowPreference(
                            title = stringResource(Res.string.theme_accent_color),
                            startAction = { PaletteDots() },
                            endActions = {
                                Text(
                                    text = seedColor?.let { "#%06X".format((it and 0xFFFFFF).toInt()) }
                                        ?: stringResource(Res.string.theme_accent_default),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = { showColorPicker = true },
                        )
                    }
                
                }
            }
            
            if (blurSupported) {
                item(key = "blur") {
                    CardSegmentContainer(
                        isFirst = false,
                        isLast = false,
                        horizontalPadding = 0.dp,
                    ) {
                        SwitchPreference(
                            title = stringResource(Res.string.theme_enable_blur),
                            summary = stringResource(Res.string.theme_enable_blur_summary),
                            checked = state.enableBlur,
                            onCheckedChange = onEnableBlur,
                        )
                    }
                }
            }
            item(key = "floating-bottom-bar") {
                CardSegmentContainer(
                    isFirst = false,
                    isLast = false,
                    horizontalPadding = 0.dp,
                ) {
                    SwitchPreference(
                        title = stringResource(Res.string.theme_floating_bottom_bar),
                        summary = stringResource(Res.string.theme_floating_bottom_bar_summary),
                        checked = state.enableFloatingBottomBar,
                        onCheckedChange = onEnableFloatingBottomBar,
                    )
                }
            }
            if (blurSupported) {
                item(key = "floating-bottom-bar-glass") {
                    AnimatedVisibility(
                        visible = state.enableFloatingBottomBar,
                        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
                    ) {
                        CardSegmentContainer(
                            isFirst = false,
                            isLast = false,
                            horizontalPadding = 0.dp,
                        ) {
                            // 液态底栏从开关改为三档选项：关闭 / 经典 / OS4
                            val liquidValues = listOf("off", "classic", "os4")
                            AppSpinnerPreference(
                                items = listOf(
                                    stringResource(Res.string.liquid_style_off),
                                    stringResource(Res.string.liquid_style_classic),
                                    stringResource(Res.string.liquid_style_os4),
                                ),
                                selectedIndex = liquidValues
                                    .indexOf(state.liquidBottomBarStyle)
                                    .coerceAtLeast(0),
                                title = stringResource(Res.string.theme_enable_glass),
                                summary = stringResource(Res.string.theme_enable_glass_summary),
                                onSelectedIndexChange = { i ->
                                    onLiquidBottomBarStyleChange(liquidValues[i])
                                },
                            )
                        }
                    }
                }
            }
            // 底栏自定义：跟随悬浮底栏一起出现/收起（等价于「设置-通用-代理」里的黑白名单折叠行）
            if (state.enableFloatingBottomBar && state.liquidBottomBarStyle != "off") {
                item(key = "bottom-bar-customize") {
                    CardSegmentContainer(
                        isFirst = false,
                        isLast = false,
                        horizontalPadding = 0.dp,
                    ) {
                        BottomBarCustomizeBlock(
                            style = state.liquidBottomBarStyle,
                            scale = if (state.liquidBottomBarStyle == "classic") state.bottomBarScaleClassic else state.bottomBarScaleOs4,
                            height = if (state.liquidBottomBarStyle == "classic") state.bottomBarHeightClassic else state.bottomBarHeightOs4,
                            onScale = { onBottomBarScale(state.liquidBottomBarStyle, it) },
                            onHeight = { onBottomBarHeight(state.liquidBottomBarStyle, it) },
                        )
                    }
                }
            }
            item(key = "navigation-badge") {
                CardSegmentContainer(
                    isFirst = false,
                    isLast = true,
                    horizontalPadding = 0.dp,
                ) {
                    SwitchPreference(
                        title = stringResource(Res.string.theme_navigation_badge),
                        summary = stringResource(Res.string.theme_navigation_badge_summary),
                        checked = state.enableNavigationBadge,
                        onCheckedChange = onEnableNavigationBadge,
                    )
                }
            }
            item(key = "section-bottom-bar") {
                SectionTitle(text = stringResource(Res.string.theme_bottom_bar))
            }
            item(key = "bottom-bar-tabs") {
                Column {
                ReorderableBottomBarTabs(
                    order = bottomBarTabOrder,
                    hiddenTabs = hiddenTabs,
                    functionHiddenTabs = functionHiddenTabs,
                    topRightFunctions = topRightFunctions,
                    onTopRightFunctionChange = onTopRightFunctionChange,
                    onTabHidden = onTabHidden,
                    onOrderChange = onBottomBarTabOrderChange,
                    onDragStart = { onTopRightExpandedChange(false) },
                )
                    // 设置不在可排序标签里，其右上角功能固定挂在末尾
                    CardSegmentContainer(
                        isFirst = false,
                        isLast = true,
                        horizontalPadding = 0.dp,
                    ) {
                        val settingsValues = listOf("off", "search", "download")
                        AppSpinnerPreference(
                            items = settingsValues.map { value ->
                                stringResource(
                                    when (value) {
                                        "search" -> Res.string.top_right_action_search
                                        "download" -> Res.string.top_right_action_download
                                        else -> Res.string.top_right_action_off
                                    },
                                )
                            },
                            selectedIndex = settingsValues
                                .indexOf(topRightFunctions["settings"] ?: "off")
                                .coerceAtLeast(0),
                            // 主标题=设置，副标题=右上角功能
                            title = stringResource(Res.string.nav_settings),
                            summary = stringResource(Res.string.theme_top_right_action),
                            onSelectedIndexChange = { i ->
                                onTopRightFunctionChange("settings", settingsValues[i])
                            },
                        )
                    }
                }
            }
            item(key = "section-interaction") {
                SectionTitle(text = stringResource(Res.string.theme_section_interaction))
            }
            if (predictiveBackSupported) {
                item(key = "predictive-back") {
                    CardSegmentContainer(
                        isFirst = true,
                        isLast = false,
                        horizontalPadding = 0.dp,
                    ) {
                        SwitchPreference(
                            title = stringResource(Res.string.theme_predictive_back),
                            summary = stringResource(Res.string.theme_predictive_back_summary),
                            checked = state.enablePredictiveBack,
                            onCheckedChange = onEnablePredictiveBack,
                        )
                    }
                }
            }
            item(key = "page-scale") {
                CardSegmentContainer(
                    isFirst = !predictiveBackSupported,
                    isLast = true,
                    horizontalPadding = 0.dp,
                ) {
                    BasicComponent(
                        title = stringResource(Res.string.theme_page_scale),
                        summary = stringResource(Res.string.theme_page_scale_summary),
                        endActions = { Text("${(sliderValue * 100).toInt()}%", color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                        onClick = { showScaleDialog = !showScaleDialog },
                        holdDownState = showScaleDialog,
                        bottomAction = {
                            Slider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                onValueChangeFinished = { onPageScale(sliderValue) },
                                valueRange = 0.8f..1.1f,
                                showKeyPoints = true,
                                keyPoints = listOf(0.8f, 0.9f, 1f, 1.1f),
                                magnetThreshold = 0.01f,
                                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                            )
                        },
                    )
                }
            }
        }
        ScaleDialog(
            show = showScaleDialog,
            onDismissRequest = { showScaleDialog = false },
            scaleProvider = { state.pageScale },
            onScaleChange = onPageScale,
        )
        ColorPickerDialog(
            show = showColorPicker,
            initialColor = Color(state.seedColor ?: DEFAULT_SEED_COLOR),
            title = stringResource(Res.string.theme_custom_color),
            onDismissRequest = { showColorPicker = false },
            onConfirm = { color ->
                showColorPicker = false
                onSeedColor(color.toArgb().toLong() and 0xFFFFFFFFL)
            },
            onResetRequest = {
                showColorPicker = false
                onSeedColor(null)
            },
        )
    }
}

internal fun colorModeIndex(mode: ThemeColorMode): Int = ThemeColorMode.entries.indexOf(mode)

internal fun colorModeAt(index: Int): ThemeColorMode =
    ThemeColorMode.entries[index.coerceIn(0, ThemeColorMode.entries.lastIndex)]

internal fun colorSpecIndex(spec: ThemeColorSpec): Int = ThemeColorSpec.entries.indexOf(spec)

internal fun colorSpecAt(index: Int): ThemeColorSpec =
    ThemeColorSpec.entries[index.coerceIn(0, ThemeColorSpec.entries.lastIndex)]

internal fun downloadSourceBadgeRes(style: DownloadSourceBadge): StringResource = when (style) {
    DownloadSourceBadge.OFF -> Res.string.theme_download_source_badge_off
    DownloadSourceBadge.PLAIN -> Res.string.theme_download_source_badge_plain
    DownloadSourceBadge.BRAND -> Res.string.theme_download_source_badge_brand
}

internal fun paletteStyleRes(style: ThemePaletteStyle): StringResource = when (style) {
    ThemePaletteStyle.TONAL_SPOT -> Res.string.theme_palette_tonal_spot
    ThemePaletteStyle.NEUTRAL -> Res.string.theme_palette_neutral
    ThemePaletteStyle.VIBRANT -> Res.string.theme_palette_vibrant
    ThemePaletteStyle.EXPRESSIVE -> Res.string.theme_palette_expressive
    ThemePaletteStyle.RAINBOW -> Res.string.theme_palette_rainbow
    ThemePaletteStyle.FRUIT_SALAD -> Res.string.theme_palette_fruit_salad
    ThemePaletteStyle.MONOCHROME -> Res.string.theme_palette_monochrome
    ThemePaletteStyle.FIDELITY -> Res.string.theme_palette_fidelity
    ThemePaletteStyle.CONTENT -> Res.string.theme_palette_content
}

@Composable
private fun PaletteDots(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            MiuixTheme.colorScheme.primary,
            MiuixTheme.colorScheme.secondary,
            MiuixTheme.colorScheme.tertiaryContainer,
        ).forEach { color ->
            Box(
                Modifier
                    .size(10.dp)
                    .background(color, CircleShape),
            )
        }
    }
}


/** 首页候选项与底栏页签的对应关系；null 表示该页签不可被隐藏（如设置）。 */
internal fun HomePage.barTabKey(): String? = when (this) {
    HomePage.TODAY -> BottomBarTab.TODAY.key
    HomePage.RANKING -> BottomBarTab.RANKING.key
    HomePage.UPDATES -> BottomBarTab.UPDATES.key
    HomePage.SEARCH -> BottomBarTab.SEARCH.key
    HomePage.DOWNLOADS -> BottomBarTab.DOWNLOADS.key
    HomePage.SETTINGS -> null
}

internal val HomePage.labelRes
    get() = when (this) {
        HomePage.TODAY -> Res.string.nav_today
        HomePage.RANKING -> Res.string.nav_ranking
        HomePage.UPDATES -> Res.string.nav_updates
        HomePage.SEARCH -> Res.string.nav_search
        HomePage.DOWNLOADS -> Res.string.home_page_downloads
        HomePage.SETTINGS -> Res.string.home_page_settings
    }
/**
 * 底栏自定义：默认收起，展开后是「底栏大小 / 底栏高度」两个滑块。
 *
 * 数据按底栏风格（os4 / classic）分开存；滑块不画刻度白点，行尾不带箭头。
 */
@Composable
private fun BottomBarCustomizeBlock(
    style: String,
    scale: Float,
    height: Float,
    onScale: (Float) -> Unit,
    onHeight: (Float) -> Unit,
) {
    var expanded by rememberSaveable(style) { mutableStateOf(false) }
    val actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    val colorFilter = remember(actionColor) { ColorFilter.tint(actionColor) }
    // 与「设置-通用-代理」的折叠行一致：收起时箭头朝下、展开时朝上（同一枚右箭头旋转）
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 270f else 90f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "bottomBarCustomizeArrow",
    )
    BasicComponent(
        title = "底栏自定义",
        endActions = {
            Image(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(width = 10.dp, height = 16.dp)
                    .rotate(arrowRotation)
                    .align(Alignment.CenterVertically),
                imageVector = MiuixIcons.Basic.ArrowRight,
                colorFilter = colorFilter,
                contentDescription = null,
            )
        },
        onClick = { expanded = !expanded },
    )
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
        exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BottomBarSliderRow(
                title = "底栏大小",
                value = scale,
                range = 0.8f..1.2f,
                defaultValue = 1f,
                isPercent = true,
                onValueChange = onScale,
            )
            BottomBarSliderRow(
                title = "底栏高度",
                value = height,
                range = 8f..36f,
                defaultValue = 24f,
                isPercent = false,
                onValueChange = onHeight,
            )
        }
    }
}

@Composable
private fun BottomBarSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    isPercent: Boolean,
    onValueChange: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    // 数值为默认值时直接显示「默认」；字号与「界面缩放」行一致（不覆盖 fontSize）
    val valueText = if (kotlin.math.abs(value - defaultValue) < 0.001f) {
        "默认"
    } else if (isPercent) {
        "${(value * 100).toInt()}%"
    } else {
        "${value.toInt()}dp"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = title,
            endActions = {
                Text(
                    text = valueText,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
            onClick = { showDialog = true },
        )
        Slider(
            modifier = Modifier.padding(horizontal = 16.dp),
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onValueChange(current) },
            valueRange = range,
            showKeyPoints = false,
            magnetThreshold = 0f,
        )
    }
    if (showDialog) {
        ScaleDialog(
            show = true,
            onDismissRequest = { showDialog = false },
            scaleProvider = { current },
            onScaleChange = {
                current = it
                onValueChange(it)
            },
            title = title,
            summary = if (isPercent) {
                "${(range.start * 100).toInt()}% - ${(range.endInclusive * 100).toInt()}%"
            } else {
                "${range.start.toInt()}dp - ${range.endInclusive.toInt()}dp"
            },
            range = if (isPercent) {
                (range.start * 100).toInt()..(range.endInclusive * 100).toInt()
            } else {
                range.start.toInt()..range.endInclusive.toInt()
            },
            suffix = if (isPercent) "%" else "dp",
            valueFactor = if (isPercent) 100f else 1f,
            resetValue = defaultValue,
        )
    }
}