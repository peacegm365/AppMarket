package com.app.market.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import com.app.market.ui.component.DownloadStatusButton
import com.app.market.ui.component.FloatingBottomBar
import com.app.market.ui.component.FloatingBottomBarItem
import com.app.market.ui.component.TopRightSearchButton
import com.app.market.ui.component.TopRightSelectAllButton
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.app.market.ui.theme.LocalAppBackgroundImage
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.preference.HomePage
import com.app.market.domain.model.theme.BottomBarTab
import com.app.market.domain.repository.ThemePreferencesRepository
import com.app.market.platform.UiPlatform
import com.app.market.resources.Res
import com.app.market.resources.downloading_apps
import com.app.market.resources.nav_search
import com.app.market.resources.nav_settings
import com.app.market.resources.nav_downloads
import com.app.market.resources.nav_today
import com.app.market.resources.nav_updates
import com.app.market.resources.nav_apps
import com.app.market.resources.nav_ranking
import com.app.market.resources.nav_games
import com.app.market.ui.component.blur.BlurredBar
import com.app.market.ui.component.blur.rememberBlurBackdrop
import com.app.market.ui.screen.CategoryTab
import com.app.market.ui.screen.RankingTab
import com.app.market.ui.screen.DownloadingAppsScreen
import com.app.market.ui.screen.SearchTab
import com.app.market.ui.screen.SettingsTab
import com.app.market.ui.screen.TodayTab
import com.app.market.ui.screen.UpdatesTab
import com.app.market.domain.model.market.AppCategory
import com.app.market.ui.theme.LocalEnableFloatingBottomBar
import com.app.market.ui.theme.LocalOpaqueSurface
import com.app.market.ui.theme.LocalLiquidBottomBarStyle
import com.app.market.ui.theme.LocalEnableNavigationBadge
import com.app.market.ui.util.rememberIsWideScreen
import com.app.market.viewmodel.CategoryViewModel
import com.app.market.viewmodel.DownloadingAppsViewModel
import com.app.market.viewmodel.InstallerSettingsViewModel
import com.app.market.viewmodel.SearchViewModel
import com.app.market.viewmodel.TodayViewModel
import com.app.market.viewmodel.UpdatesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassStyles
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import com.app.market.resources.ignore_once
import com.app.market.resources.ignore_permanent
import com.app.market.resources.multi_select_all
import com.app.market.ui.component.AppButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults

// 主页签集合按平台能力裁剪：桌面端（无法扫描已装应用 / 安装）不含「更新」。
private enum class MainTab { Today, Ranking, Updates, Search, Downloads, Settings }
private fun MainTab.barTabKey(): String? = when (this) {
    MainTab.Today -> BottomBarTab.TODAY.key
    MainTab.Ranking -> BottomBarTab.RANKING.key
    MainTab.Updates -> BottomBarTab.UPDATES.key
    MainTab.Search -> BottomBarTab.SEARCH.key
    MainTab.Downloads -> BottomBarTab.DOWNLOADS.key
    MainTab.Settings -> null
}

/** 右上角功能配置用的页面 key（与主题设置弹框里的行一一对应）。 */
private fun MainTab.topRightKey(): String = when (this) {
    MainTab.Today -> "today"
    MainTab.Ranking -> "ranking"
    MainTab.Updates -> "updates"
    MainTab.Search -> "search"
    MainTab.Downloads -> "downloads"
    MainTab.Settings -> "settings"
}

@Composable
fun MainPage(
    navigator: Navigator,
    updatesViewModel: UpdatesViewModel,
    searchViewModel: SearchViewModel,
    installerSettingsViewModel: InstallerSettingsViewModel,
    todayViewModel: TodayViewModel,
    categoryViewModel: CategoryViewModel,
    downloadViewModel: DownloadingAppsViewModel,
    pendingSearchKeyword: String? = null,
    onPendingSearchConsumed: () -> Unit = {},
) {
    val rootThemePreferences = koinInject<ThemePreferencesRepository>()
    val rootHiddenTabs by rootThemePreferences.hiddenTabs.collectAsStateWithLifecycle()
    val rootTopRightFunctions by rootThemePreferences.topRightFunctions.collectAsStateWithLifecycle()
    val rootBottomBarTabOrder by rootThemePreferences.bottomBarTabOrder.collectAsStateWithLifecycle()
    // 记录最近所在页（跨热重载存活），底栏重建后停回原页
    var retainedPage by remember { mutableIntStateOf(-1) }
    // 底栏标签集合/顺序变化（手动隐藏、右上角功能接管、拖动排序）→ 整体重建主界面（等效退出重启），
    // 规避标签变化后导航点击状态残留卡死、点击无动画（历史修复的同一思路，覆盖到全部变化源）
    key(
        rootHiddenTabs.sorted().joinToString(",") + "|" +
            rootTopRightFunctions.values.sorted().joinToString(",") + "|" +
            rootBottomBarTabOrder.joinToString(","),
    ) {
    val uiPlatform = koinInject<UiPlatform>()
    val themePreferences = koinInject<ThemePreferencesRepository>()
    val preferencesInitialized by themePreferences.initialized.collectAsStateWithLifecycle()
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val liquidBottomBarStyle = LocalLiquidBottomBarStyle.current
    // 底栏外观：OS4 与「经典」各存一份（设置 → 主题 → 底栏自定义）
    val bottomBarScaleOs4 by themePreferences.bottomBarScaleOs4.collectAsStateWithLifecycle()
    val bottomBarHeightOs4 by themePreferences.bottomBarHeightOs4.collectAsStateWithLifecycle()
    val bottomBarScaleClassic by themePreferences.bottomBarScaleClassic.collectAsStateWithLifecycle()
    val bottomBarHeightClassic by themePreferences.bottomBarHeightClassic.collectAsStateWithLifecycle()
    val isClassicBar = liquidBottomBarStyle == "classic"
    val bottomBarScale = if (isClassicBar) bottomBarScaleClassic else bottomBarScaleOs4
    val bottomBarHeight = if (isClassicBar) bottomBarHeightClassic else bottomBarHeightOs4
    // 底栏放大用「密度放大」而不是缩放图层：矢量重新栅格，放大后不模糊
    val baseDensity = LocalDensity.current
    val barDensity = remember(baseDensity, bottomBarScale) {
        Density(baseDensity.density * bottomBarScale, baseDensity.fontScale * bottomBarScale)
    }
    val topRightFunctions by themePreferences.topRightFunctions.collectAsStateWithLifecycle()
    // 右上角功能开启的入口自动隐藏对应底栏标签
    val functionHiddenTabs = remember(topRightFunctions) {
        buildSet {
            if (topRightFunctions.values.any { it == "search" }) add(BottomBarTab.SEARCH.key)
            if (topRightFunctions.values.any { it == "download" }) add(BottomBarTab.DOWNLOADS.key)
        }
    }
    // 前后台：进前台强制拉一次更新；退后台取消本轮，避免回来就报“某某来源超时”。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    updatesViewModel.onEnterForeground()
                    categoryViewModel.refreshRepositoryInstalledState()
                }
                Lifecycle.Event.ON_STOP -> updatesViewModel.suspendCheck()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // 首次注册时若已经在前台（冷启动即 STARTED），不会收到 ON_START，这里补一次
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            updatesViewModel.onEnterForeground()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val updatesState by updatesViewModel.uiState.collectAsStateWithLifecycle()
    val multiSelectMode by updatesViewModel.multiSelect.collectAsStateWithLifecycle()
    val multiSelected by updatesViewModel.multiSelected.collectAsStateWithLifecycle()
    val appManagementSupported = uiPlatform.packageInstallationSupported
    val hiddenTabs by themePreferences.hiddenTabs.collectAsStateWithLifecycle()
    val bottomBarTabOrder by themePreferences.bottomBarTabOrder.collectAsStateWithLifecycle()
    val tabs = remember(appManagementSupported, hiddenTabs, bottomBarTabOrder, functionHiddenTabs) {
        val orderIndex = bottomBarTabOrder.withIndex().associate { (i, key) -> key to i }
        buildList {
            add(MainTab.Today)
            add(MainTab.Ranking)
            if (appManagementSupported) add(MainTab.Updates)
            add(MainTab.Search)
            add(MainTab.Downloads)
            add(MainTab.Settings)
        }
            .filterNot { it.barTabKey() in hiddenTabs }
            .filterNot { it.barTabKey() in functionHiddenTabs }
            // 按设置页拖动后保存的顺序排序；无记录的标签保持默认相对顺序垫底
            .sortedBy { tab -> tab.barTabKey()?.let { orderIndex[it] } ?: Int.MAX_VALUE }
    }
    val searchPage = tabs.indexOf(MainTab.Search)
    fun homeIndexOf(page: HomePage): Int =
        tabs.indexOf(page.toTab()).takeIf { it >= 0 } ?: 0

    // Initial page reads the seeded value once (no launch jump); reactive for the Back target.
    val homePage by updatesViewModel.homePage.collectAsStateWithLifecycle()
    // tab 集合变化（含隐藏底栏标签）时用 key() 重建 PagerState，页面 clamp 回新范围；
    // retainedPage 声明在 key 之外，热重载后据此恢复到切换前所在页。
    val tabsSignature = tabs.joinToString(separator = ",") { it.name }
    val pagerState = key(tabsSignature) {
        rememberPagerState(
            initialPage = if (retainedPage >= 0) {
                retainedPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
            } else {
                homeIndexOf(updatesViewModel.homePage.value)
            },
            pageCount = { tabs.size },
        )
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { retainedPage = it }
    }
    val mainPagerState = rememberMainPagerState(pagerState)
    val selectedPage = mainPagerState.selectedPage
    val isWideScreen = rememberIsWideScreen()
    var notificationPermissionRequested by remember { mutableStateOf(false) }
    var searchFocusRequestId by remember { mutableIntStateOf(0) }
    val onSearchTabClick = {
        if (searchPage < 0) {
            // 搜索标签已被隐藏：外部搜索入口退化为提示，由调用方保持当前页
        } else if (selectedPage == searchPage && !mainPagerState.isNavigating) {
            searchFocusRequestId += 1
        } else {
            mainPagerState.animateToPage(searchPage)
        }
    }
    val onTabClick: (Int, MainTab) -> Unit = { index, tab ->
        if (tab == MainTab.Search) onSearchTabClick() else mainPagerState.animateToPage(index)
    }
    val startInitialCheckAfterAppListPermission = {
        val launched = uiPlatform.requestInstalledAppsPermission {
            updatesViewModel.startInitialCheck()
        }
        if (!launched) updatesViewModel.startInitialCheck()
    }

    LaunchedEffect(pendingSearchKeyword) {
        val keyword = pendingSearchKeyword ?: return@LaunchedEffect
        if (searchPage >= 0) mainPagerState.animateToPage(searchPage)
        if (keyword.isNotEmpty()) searchViewModel.searchWith(keyword) else searchFocusRequestId += 1
        onPendingSearchConsumed()
    }

    // Keep selectedPage (nav-bar highlight + per-tab gating) in sync when the user swipes the pager,
    // not just when a tab is tapped (animateToPage).
    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    LaunchedEffect(selectedPage, tabs) {
        todayViewModel.setPageVisible(tabs.getOrNull(selectedPage) == MainTab.Today)
    }

    // Update checking (and its permission dance) only applies where app management is supported.
    LaunchedEffect(Unit) {
        if (appManagementSupported && !notificationPermissionRequested) {
            notificationPermissionRequested = true
            val launched = uiPlatform.requestPostNotificationsPermission {
                startInitialCheckAfterAppListPermission()
            }
            if (!launched) startInitialCheckAfterAppListPermission()
        }
    }

    MainScreenBackHandler(
        mainPagerState,
        navigator,
        homeIndexOf(homePage),
        multiSelectMode = multiSelectMode,
        onExitMultiSelect = { updatesViewModel.exitMultiSelect() },
    )

    val openDetail: (MarketAppInfo) -> Unit = { app ->
        // 搜索页可能给出 vivo 条目，详情要打对应源的接口
        navigator.push(Route.AppDetail(app.appId, app.packageName, app.displayName, source = app.source))
    }

    val pagerContent: @Composable (Modifier, Dp) -> Unit = { pagerModifier, bottomPadding ->
        HorizontalPager(
            modifier = pagerModifier,
            state = mainPagerState.pagerState,
            overscrollEffect = null,
            verticalAlignment = Alignment.Top,
        ) { page ->
            when (tabs[page]) {
                MainTab.Today -> TodayTab(
                    viewModel = todayViewModel,
                    updatesViewModel = updatesViewModel.takeIf { appManagementSupported },
                    bottomPadding = bottomPadding,
                    onClickViewUpdates = { tabs.indexOf(MainTab.Updates).takeIf { it >= 0 }?.let(mainPagerState::animateToPage) },
                    onClickArticle = { article ->
                        // 今日页条目一律先进卡片页（小米/ TapTap 的图文推荐、好游快爆的应用推荐都一样），
                        // 卡片里再点应用才进详情，行为统一。
                        if (article.rId.isNotBlank()) {
                            navigator.push(Route.TodayArticle(article.rId))
                        }
                    },
                )

                // 游戏与应用合并成一个「榜单」页：页内切换游戏榜 / 软件榜
                MainTab.Ranking -> RankingTab(
                    viewModel = categoryViewModel,
                    bottomPadding = bottomPadding,
                    onOpenDetail = openDetail,
                    isCurrentPage = selectedPage == tabs.indexOf(MainTab.Ranking),
                )

                MainTab.Updates -> UpdatesTab(
                    viewModel = updatesViewModel,
                    bottomPadding = bottomPadding,
                    onOpenDetail = openDetail,
                )

                MainTab.Search -> SearchTab(
                    viewModel = searchViewModel,
                    updatesViewModel = updatesViewModel,
                    bottomPadding = bottomPadding,
                    onOpenDetail = openDetail,
                    isCurrentPage = selectedPage == searchPage,
                    focusRequestId = searchFocusRequestId,
                )
                MainTab.Downloads -> DownloadingAppsScreen(
                    viewModel = downloadViewModel,
                    onOpenDetail = { state ->
                        val source = state.source
                            ?: updatesViewModel.searchSources.value.firstOrNull()
                            ?: AppSource.Default.first()
                        navigator.push(
                            Route.AppDetail(
                                state.appId,
                                state.packageName,
                                state.displayName,
                                source = source,
                            ),
                        )
                    },
                    bottomPadding = bottomPadding,
                )

                MainTab.Settings -> SettingsTab(
                    bottomPadding = bottomPadding,
                    appManagementSupported = appManagementSupported,
                    onNavigateCategory = { navigator.push(Route.SettingsCategory(it)) },
                    onNavigateTheme = { navigator.push(Route.ThemeSettings) },
                )
            }
        }
    }

    if (isWideScreen) {
        Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
            Row(Modifier.fillMaxSize()) {
                if (preferencesInitialized) {
                    val railState = rememberNavigationRailState(
                        initialValue = if (themePreferences.navRailExpanded.value) {
                            NavigationRailValue.Expanded
                        } else {
                            NavigationRailValue.Collapsed
                        },
                    )
                    LaunchedEffect(railState.currentValue) {
                        themePreferences.setNavRailExpanded(railState.isExpanded)
                    }
                    NavigationRail(state = railState) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationRailItem(
                                selected = selectedPage == index,
                                onClick = { onTabClick(index, tab) },
                                icon = tab.icon,
                                label = stringResource(tab.labelRes),
                            )
                        }
                    }
                }
                pagerContent(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // The rail already absorbed the start-side cutout/nav-bar insets; the end
                        // side has no rail, so pad it here (and mark it consumed for descendants).
                        .consumeWindowInsets(
                            WindowInsets.displayCutout.union(WindowInsets.navigationBars)
                                .only(WindowInsetsSides.Start),
                        )
                        .windowInsetsPadding(
                            WindowInsets.systemBars.union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.End),
                        ),
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                )
            }
        }
    } else {
        val blurBackdrop = rememberBlurBackdrop()
        val blurActive = blurBackdrop != null
        val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
        val surfaceColor = LocalOpaqueSurface.current
        val backgroundImage = LocalAppBackgroundImage.current
        val glassBackdrop = rememberLayerBackdrop {
            drawRect(surfaceColor)
            if (backgroundImage != null && size.width > 0f && size.height > 0f) {
                val scale = maxOf(size.width / backgroundImage.width, size.height / backgroundImage.height)
                val width = (backgroundImage.width * scale).roundToInt()
                val height = (backgroundImage.height * scale).roundToInt()
                drawImage(
                    image = backgroundImage,
                    dstOffset = IntOffset(((size.width - width) / 2f).roundToInt(), ((size.height - height) / 2f).roundToInt()),
                    dstSize = IntSize(width, height),
                )
            }
            drawContent()
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val classicBar: @Composable () -> Unit = {
                        BlurredBar(backdrop = blurBackdrop, blurActive = blurActive) {
                            NavigationBar(color = barColor) {
                                tabs.forEachIndexed { index, tab ->
                                    NavigationBarItem(
                                        modifier = Modifier.weight(1f),
                                        selected = selectedPage == index,
                                        onClick = { onTabClick(index, tab) },
                                        icon = tab.icon,
                                        label = stringResource(tab.labelRes),
                                        badge = navigationBadge(tab, updatesState.updates.size),
                                    )
                                }
                            }
                        }
                    }
                    // 多选模式：底栏变成「忽略本次 / 永久忽略」操作栏
                    if (multiSelectMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MiuixTheme.colorScheme.surface)
                                .navigationBarsPadding()
                                // 与悬浮底栏同高：底部再抬 12dp
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AppButton(
                                text = stringResource(Res.string.ignore_once),
                                onClick = { updatesViewModel.ignoreSelectedOnce() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            )
                            AppButton(
                                text = stringResource(Res.string.ignore_permanent),
                                onClick = { updatesViewModel.ignoreSelectedPermanent() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else if (!enableFloatingBottomBar || !isRuntimeShaderSupported()) {
                        classicBar()
                    } else if (liquidBottomBarStyle == "classic") {
                        // 经典 = 液态玻璃底栏（自绘玻璃条：折射高光 + 液滴形变）
                        // 宽度按内容给足，仅当整条要碰屏幕边缘时才压缩
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        // 线性压缩：只有总宽超过「屏宽 - 48dp」才按溢出距离等比缩，每项最窄 68dp
                        // 布局宽度按缩放反算：放大时布局变小、视觉不变，绝不溢屏
                        val baseWidth = linearBottomBarWidth(maxWidth, tabs.size)
                        val classicLayoutWidth = baseWidth / bottomBarScale
                        Box(
                            modifier = Modifier.padding(
                                // 底栏高度 = 距屏幕底部的距离
                                bottom = bottomBarHeight.dp + WindowInsets.navigationBars.asPaddingValues()
                                    .calculateBottomPadding(),
                            ),
                        ) {
                        CompositionLocalProvider(LocalDensity provides barDensity) {
                        FloatingBottomBar(
                            modifier = Modifier,
                            barWidth = classicLayoutWidth,
                            selectedIndex = mainPagerState.selectedPage,
                            onSelected = { index -> onTabClick(index, tabs[index]) },
                            backdrop = glassBackdrop,
                            tabsCount = tabs.size,
                            isBlurEnabled = true,
                            barHeight = 64.dp,
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                val label = stringResource(tab.labelRes)
                                FloatingBottomBarItem(
                                    modifier = Modifier.defaultMinSize(
                                        minWidth = 76.dp,
                                    ),
                                    selected = mainPagerState.selectedPage == index,
                                    onClick = { onTabClick(index, tab) },
                                ) {
                                    val badge = navigationBadge(tab, updatesState.updates.size)
                                    val itemIcon: @Composable () -> Unit = {
                                        Icon(
                                            modifier = Modifier.size(22.dp),
                                            imageVector = tab.icon,
                                            contentDescription = null,
                                        )
                                    }
                                    if (badge != null) {
                                        // 用 BadgedBox 让库自己算角标位置：手工 Box(align = TopEnd) 会把角标
                                        // 压在图标右上角内侧，看起来像整体左上偏移。
                                        BadgedBox(badge = { badge() }) { itemIcon() }
                                    } else {
                                        itemIcon()
                                    }
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        }
                        }
                        }
                    } else {
                        // 悬浮底栏本体不变：「液态玻璃」只换材质（Bionic 液态预设），高度与间距和 OS4 完全一致
                        // 底栏宽度按内容给足，只有整条要碰到屏幕边缘时才压缩；大小/高度来自「底栏自定义」
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                            // 限制了布局宽度后必须显式居中，否则 GlassNavigationBar 会贴左边
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                        val os4BaseWidth = linearBottomBarWidth(maxWidth, tabs.size)
                        val os4LayoutWidth = os4BaseWidth / bottomBarScale
                        Box(
                            modifier = Modifier
                                // 底栏高度 = 距屏幕底部的距离（基准 24dp）
                                .padding(bottom = (bottomBarHeight - 24f).dp),
                        ) {
                        CompositionLocalProvider(LocalDensity provides barDensity) {
                        var badgeAnchor by remember { mutableStateOf<Rect?>(null) }
                        var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
                        Box(
                            modifier = Modifier.width(os4LayoutWidth),
                        ) {
                            GlassNavigationBar(
                            items = tabs.map { tab ->
                                GlassNavigationItem(
                                    icon = tab.icon,
                                    label = stringResource(tab.labelRes),
                                    contentDescription = stringResource(tab.labelRes),
                                    badge = navigationBadge(tab, updatesState.updates.size) { badgeAnchor = it },
                                )
                            },
                            selectedIndex = mainPagerState.selectedPage,
                            onSelect = { index -> onTabClick(index, tabs[index]) },
                            backdrop = if (liquidBottomBarStyle == "os4") glassBackdrop else null,
                            style = when (liquidBottomBarStyle) {
                                // OS4 = 液态（仿生）材质；关闭 = 默认材质（经典走老版底栏分支）
                                "os4" -> GlassStyles.BionicMediumRegular
                                else -> GlassDefaults.Style
                            },
                            )
                            // 角标置顶层：长按时灰色按压胶囊会盖在库内角标上（缺一角），
                            // 占位角标只定尺寸，真角标画在整条底栏之上
                            badgeAnchor?.let { anchor ->
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .onGloballyPositioned { overlayOrigin = it.positionInWindow() },
                                ) {
                                    Box(
                                        Modifier.offset {
                                            IntOffset(
                                                (anchor.left - overlayOrigin.x).roundToInt(),
                                                (anchor.top - overlayOrigin.y).roundToInt(),
                                            )
                                        },
                                    ) {
                                        Badge {
                                            Text(updatesState.updates.size.toString())
                                        }
                                    }
                                }
                            }
                        }
                        }
                        }
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier),
            ) {
                pagerContent(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (enableFloatingBottomBar && liquidBottomBarStyle != "off") {
                                Modifier.layerBackdrop(glassBackdrop)
                            } else {
                                Modifier
                            },
                        ),
                    padding.calculateBottomPadding(),
                )
                // 多选模式：右上角功能变为「全选」按钮
                if (multiSelectMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 18.dp),
                    ) {
                        TopRightSelectAllButton(
                            onClick = { updatesViewModel.toggleMultiSelectAll() },
                            active = updatesState.updates.isNotEmpty() &&
                                multiSelected.size >= updatesState.updates.size,
                        )
                    }
                } else {
                // 右上角功能按当前页面配置显示：下载（图标 + 环形进度 + 总速度）/ 搜索 / 关闭
                val currentTab = tabs.getOrNull(mainPagerState.selectedPage)
                val currentTopRight = currentTab
                    ?.let { topRightFunctions[it.topRightKey()] } ?: "off"
                // 下载管理页自己也有一份速度（每个任务按钮下方），顶栏再画一个就成了两个速度，
                // 所以下载 tab 上不显示顶栏下载入口
                if ((currentTopRight == "download" || currentTopRight == "search") &&
                    currentTab != MainTab.Downloads
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .zIndex(1f)
                            .statusBarsPadding()
                            // 整体再往上挪一点；右侧多留一点空间给速度文字
                            .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 18.dp),
                    ) {
                        if (currentTopRight == "search") {
                            TopRightSearchButton(onClick = { navigator.push(Route.Search) })
                        } else {
                            val downloadStates by downloadViewModel.downloadStates.collectAsStateWithLifecycle()
                            DownloadStatusButton(
                                states = downloadStates.values,
                                onClick = { navigator.push(Route.DownloadingApps) },
                            )
                        }
                    }
                }
                }
            }
        }
    }
    }
}

private fun HomePage.toTab(): MainTab = when (this) {
    HomePage.TODAY -> MainTab.Today
    HomePage.RANKING -> MainTab.Ranking
    HomePage.UPDATES -> MainTab.Updates
    HomePage.SEARCH -> MainTab.Search
    HomePage.DOWNLOADS -> MainTab.Downloads
    HomePage.SETTINGS -> MainTab.Settings
}

private val MainTab.icon
    get() = when (this) {
        MainTab.Today -> MiuixIcons.Create
        MainTab.Ranking -> MiuixIcons.Play
        // 应用列表用宫格图标，与「今日」的 Create 区分开
        MainTab.Updates -> MiuixIcons.Update
        MainTab.Search -> MiuixIcons.Search
        MainTab.Downloads -> MiuixIcons.Download
        MainTab.Settings -> MiuixIcons.Settings
    }

private val MainTab.labelRes
    get() = when (this) {
        MainTab.Today -> Res.string.nav_today
        MainTab.Ranking -> Res.string.nav_ranking
        MainTab.Updates -> Res.string.nav_updates
        MainTab.Search -> Res.string.nav_search
        MainTab.Downloads -> Res.string.nav_downloads
        MainTab.Settings -> Res.string.nav_settings
    }

@Composable
private fun navigationBadge(
    tab: MainTab,
    updateCount: Int,
    onAnchor: ((Rect) -> Unit)? = null,
): (@Composable () -> Unit)? {
    if (!LocalEnableNavigationBadge.current || tab != MainTab.Updates || updateCount <= 0) return null
    return {
        if (onAnchor == null) {
            Badge {
                Text(updateCount.toString())
            }
        } else {
            // 占位角标：只定尺寸与锚点位置，真角标由底栏顶层浮层绘制，
            // 避免长按时被灰色按压胶囊盖住一角
            Box(
                Modifier
                    .onGloballyPositioned { onAnchor(it.boundsInWindow()) }
                    .alpha(0f),
            ) {
                Badge {
                    Text(updateCount.toString())
                }
            }
        }
    }
}

@Stable
class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return
        navJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true
        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.springAnimateToPage(targetIndex)
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) selectedPage = pagerState.currentPage
                }
            }
        }
    }

    /** Sync [selectedPage] to the pager after a manual swipe (when not animating a tab tap). */
    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) selectedPage =
            pagerState.currentPage
    }
}

private suspend fun PagerState.springAnimateToPage(target: Int) {
    if (target !in 0 until pageCount) return
    var shouldSnapToTarget = false
    scroll(MutatePriority.UserInput) {
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val distance = target - currentPage - currentPageOffsetFraction
        val scrollPixels = distance * pageSize
        if (abs(scrollPixels) <= 0.5f) return@scroll

        var consumedScroll = 0f
        var skipScroll = false
        Animatable(0f).animateTo(
            targetValue = scrollPixels,
            animationSpec = PagerNavigationSpringSpec,
        ) {
            if (skipScroll) return@animateTo

            val delta = value - consumedScroll
            if (abs(delta) > 0.5f) {
                val consumed = scrollBy(delta)
                consumedScroll += consumed
                if (abs(delta - consumed) > 0.1f) {
                    shouldSnapToTarget = true
                    skipScroll = true
                }
            } else {
                consumedScroll = value
            }

            if (abs(velocity) < 0.1f && abs(scrollPixels - consumedScroll) < 1.0f) {
                skipScroll = true
            }
        }

        val remaining = scrollPixels - consumedScroll
        if (abs(remaining) > 0.5f) {
            scrollBy(remaining)
        }
    }

    if (shouldSnapToTarget || currentPage != target) {
        scrollToPage(target)
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MainPagerState =
    remember(pagerState, coroutineScope) { MainPagerState(pagerState, coroutineScope) }

@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navigator: Navigator,
    homeIndex: Int,
    multiSelectMode: Boolean = false,
    onExitMultiSelect: () -> Unit = {},
) {
    val isPagerBackHandlerEnabled by remember(homeIndex, multiSelectMode) {
        derivedStateOf {
            navigator.current() is Route.Main &&
                    navigator.backStackSize() == 1 &&
                    (multiSelectMode || mainState.selectedPage != homeIndex)
        }
    }
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            // 多选模式：返回只退出多选，不回首页
            if (multiSelectMode) onExitMultiSelect() else mainState.animateToPage(homeIndex)
        },
    )
}

/**
 * 底栏宽度的线性压缩：
 * 默认每项 76dp；只有整条宽度超过「屏宽 - 48dp」时，才按溢出距离线性收缩，每项最窄 68dp。
 */
private fun linearBottomBarWidth(maxWidth: androidx.compose.ui.unit.Dp, tabCount: Int): androidx.compose.ui.unit.Dp {
    val perItemMax = 76.dp
    val perItemMin = 68.dp
    val natural = perItemMax * tabCount + 16.dp
    val allowed = maxWidth - 48.dp
    val overflow = natural - allowed
    if (overflow <= 0.dp) return natural
    val maxCompress = (perItemMax - perItemMin) * tabCount
    val compress = if (overflow < maxCompress) overflow else maxCompress
    return natural - compress
}