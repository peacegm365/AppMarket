package com.app.market.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import com.app.market.resources.only_uninstalled
import top.yukonga.miuix.kmp.basic.Switch
import androidx.compose.foundation.layout.widthIn
import com.app.market.resources.nav_ranking
import com.app.market.resources.nav_games_bang
import com.app.market.resources.nav_apps_bang
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.market.AppCategory
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.AppSubCategory
import com.app.market.domain.model.market.GameRanking
import com.app.market.domain.model.market.GameSubCategory
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.resources.Res
import com.app.market.resources.ranking_paid
import com.app.market.resources.ranking_grossing
import com.app.market.resources.ranking_movers
import com.app.market.resources.pull_refresh_done
import com.app.market.resources.pull_refresh_pull
import com.app.market.resources.pull_refresh_refreshing
import com.app.market.resources.pull_refresh_release
import com.app.market.resources.game_category_all
import com.app.market.resources.game_category_strategy
import com.app.market.resources.game_category_action
import com.app.market.resources.game_category_racing
import com.app.market.resources.game_category_rpg
import com.app.market.resources.game_category_card
import com.app.market.resources.game_category_fighting
import com.app.market.resources.game_category_kids
import com.app.market.resources.game_category_casual
import com.app.market.resources.game_category_flight
import com.app.market.resources.game_category_runner
import com.app.market.resources.category_education
import com.app.market.resources.category_media
import com.app.market.resources.category_office
import com.app.market.resources.category_shopping
import com.app.market.resources.category_social
import com.app.market.resources.category_sports
import com.app.market.resources.category_tools
import com.app.market.resources.nav_apps
import com.app.market.resources.nav_games
import com.app.market.resources.no_results
import com.app.market.resources.open
import com.app.market.resources.reserve
import com.app.market.resources.ranking_free
import com.app.market.resources.ranking_hot
import com.app.market.resources.ranking_new
import com.app.market.resources.ranking_sell
import com.app.market.resources.retry
import com.app.market.resources.update
import com.app.market.ui.component.AppRow
import com.app.market.ui.component.LoadingBox
import com.app.market.ui.component.MainTabScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.ui.model.AppActionKind
import com.app.market.ui.model.actionKind
import com.app.market.ui.util.appSourceLabel
import com.app.market.ui.util.installActionText
import com.app.market.ui.widget.MiuixDropdown
import com.app.market.viewmodel.CategoryViewModel
import com.app.market.viewmodel.of
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 榜单页：游戏与应用合并成一个页签，页内用「游戏榜 / 软件榜」切换。
 * 内部复用 [CategoryTab] 的完整链路（来源下拉、子分类、分页、下拉刷新）。
 */
@Composable
fun RankingTab(
    viewModel: CategoryViewModel,
    bottomPadding: Dp,
    onOpenDetail: (MarketAppInfo) -> Unit,
    isCurrentPage: Boolean = true,
) {
    var category by rememberSaveable { mutableStateOf(AppCategory.GAMES) }
    CategoryTab(
        category = category,
        viewModel = viewModel,
        bottomPadding = bottomPadding,
        onOpenDetail = onOpenDetail,
        isCurrentPage = isCurrentPage,
        showCategoryToggle = true,
        onSelectCategory = { category = it },
        titleOverride = Res.string.nav_ranking,
    )
}

@Composable
fun CategoryTab(
    category: AppCategory,
    viewModel: CategoryViewModel,
    bottomPadding: Dp,
    onOpenDetail: (MarketAppInfo) -> Unit,
    isCurrentPage: Boolean = true,
    /** 是否在列表顶部显示「游戏榜 / 软件榜」切换（榜单页用）。 */
    showCategoryToggle: Boolean = false,
    onSelectCategory: ((AppCategory) -> Unit)? = null,
    titleOverride: StringResource? = null,
) {
    val sections by viewModel.sectionStates.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val googlePlayEngine by viewModel.googlePlayEngine.collectAsStateWithLifecycle()
    val appCategorySource by viewModel.appCategorySource.collectAsStateWithLifecycle()
    val gameCategorySource by viewModel.gameCategorySource.collectAsStateWithLifecycle()
    /** 本页使用的来源，下拉的选中项以此为准。 */
    val pageSource = if (category == AppCategory.GAMES) gameCategorySource else appCategorySource
    /** Google Play 走 AuroraStore 引擎：榜单用 Aurora 方案（四种榜单 × 应用/游戏）。 */
    val auroraCharts = pageSource == AppSource.GOOGLE && googlePlayEngine == "aurorastore"
    val downloadStates = viewModel.downloadStates.collectAsStateWithLifecycle()
    val repositoryDownloadPackages by viewModel.repositoryDownloadPackages.collectAsStateWithLifecycle()
    val repositoryApps by viewModel.repositoryApps.collectAsStateWithLifecycle()
    val resolvingRepositories by viewModel.resolvingRepositories.collectAsStateWithLifecycle()
    // 切来源后旧来源的列表不能继续显示（key 里带 source，取不到就是空状态 → 走加载动画）
    val state = sections.of(category, selection, pageSource)
    // 切换游戏榜/软件榜或子分类时，新选择的结果还没到，列表会瞬间变空 → 整页白屏。
    // 这里保留上一份结果继续显示，新数据到了再替换（只在首次进入、真的没有内容时才显示全屏 loading）。
    val itemsKey = pageSource.name + "|" + category.name + "|" + selection.hashCode()
    var lastItemsByKey by remember {
        mutableStateOf<Map<String, List<com.app.market.ui.model.SearchResultItem>>>(emptyMap())
    }
    if (state.items.isNotEmpty()) {
        lastItemsByKey = lastItemsByKey + (itemsKey to state.items)
    }
    val lastItems = lastItemsByKey[itemsKey].orEmpty()
    // 只显示未安装：默认关闭，打开后把本机已安装（同版或更高版）的条目过滤掉
    var onlyUninstalled by rememberSaveable { mutableStateOf(false) }
    val sectionReady = viewModel.hasSection(category)
    // 首次切到某分类时还没有 section 状态，此时若直接用 state.items（空）会白一下；
    // 沿用上一份列表直到新数据到达。
    val rawItems = if (!sectionReady || (state.loading && state.items.isEmpty())) lastItems else state.items
    val displayItems = if (onlyUninstalled) {
        rawItems.filter { (repositoryApps[it.app.packageName] ?: it.app).installedVersionCode <= 0L }
    } else {
        rawItems
    }
    val listState = androidx.compose.runtime.key(pageSource, category) { rememberLazyListState() }
    val pullToRefreshState = rememberPullToRefreshState()
    val dynamicOptions = pageSource == AppSource.OPPO || (pageSource == AppSource.GOOGLE && !auroraCharts)
    val waitingForOptions = dynamicOptions && options[category] == null
    val optionList = options[category].orEmpty()

    LaunchedEffect(isCurrentPage, pageSource) {
        if (isCurrentPage && pageSource == AppSource.GITHUB) viewModel.refreshRepositoryInstalledState()
    }
    // 切到本页（或切换子分类 / 榜单 / 来源）时才发起首屏请求，避免未访问的页签空耗网络
    LaunchedEffect(category, selection, isCurrentPage, pageSource, googlePlayEngine) {
        if (isCurrentPage) viewModel.ensureLoaded(category)
    }
    LaunchedEffect(listState, category, selection, pageSource, isCurrentPage, state.loading, state.loadingMore, state.hasMore, state.errorMessage, state.items.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            isCurrentPage && !state.loading && !state.loadingMore && state.hasMore && state.errorMessage.isEmpty() &&
                lastVisible >= 0 && info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .collect { atBottom -> if (atBottom) viewModel.loadMoreAfterCurrent(category) }
    }
    // Komi 发现流按页本地筛选：一页可能只有少量命中，继续补页并逐页上屏。
    LaunchedEffect(state.loading, state.loadingMore, state.hasMore, state.items.size, state.errorMessage, pageSource, category, selection, isCurrentPage, sectionReady) {
        if (isCurrentPage && pageSource == AppSource.GITHUB && category == AppCategory.GAMES && sectionReady && !state.loading &&
            !state.loadingMore && state.errorMessage.isEmpty() && state.items.size < 15 && state.hasMore) {
            viewModel.loadMoreAfterCurrent(category)
        }
    }
    LaunchedEffect(state.epoch, selection) {
        if (state.epoch > 0) listState.scrollToItem(0)
    }

    val installText = installActionText()
    val updateText = stringResource(Res.string.update)
    val openText = stringResource(Res.string.open)
    val reserveText = stringResource(Res.string.reserve)
    val noResults = stringResource(Res.string.no_results)
    val retryText = stringResource(Res.string.retry)
    val title = stringResource(
        titleOverride ?: if (category == AppCategory.GAMES) Res.string.nav_games else Res.string.nav_apps,
    )

    MainTabScaffold(title = title) { topPadding, backdropModifier, scrollBehavior ->
        val contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = PageVerticalPadding,
            bottom = bottomPadding + PageVerticalPadding,
        )
        // 页签 + 来源快捷切换固定在标题下方，滚动列表时不会跟着滚走
        Column(Modifier.fillMaxSize().then(backdropModifier)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = topPadding + 2.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ① 来源快捷切换独占一行（原来和页签挤在一行，页签被压窄）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixDropdown(
                    items = AppSource.CategorySourceOptions.map { appSourceLabel(it) },
                    selectedIndex = AppSource.CategorySourceOptions.indexOf(pageSource).coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        val selectedSource = AppSource.CategorySourceOptions[index]
                        viewModel.setSource(category, selectedSource)
                        if (selectedSource == AppSource.GITHUB) {
                            viewModel.setSource(if (category == AppCategory.APPS) AppCategory.GAMES else AppCategory.APPS, selectedSource)
                        }
                    },
                )
            }
            // ② 游戏榜 / 软件榜移到切换按钮下面；「只显示未安装」放在软件榜右边
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (showCategoryToggle && onSelectCategory != null) {
                    RankingCategoryToggle(
                        selected = category,
                        onSelect = onSelectCategory,
                        isGithub = pageSource == AppSource.GITHUB,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.only_uninstalled),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Switch(
                            checked = onlyUninstalled,
                            onCheckedChange = { onlyUninstalled = it },
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            // 子分类/榜单 chips 固定在页签下方：原来放在 LazyColumn 里，
            // 切页签时整块被 Crossfade 重建，视觉上就是「标签闪一下」。
            if (pageSource == AppSource.GITHUB) {
                val choices = if (category == AppCategory.APPS) listOf(
                    "trending" to "热门", "releases" to "新发布", "popular" to "最受欢迎",
                ) else listOf(
                    "all" to "全部", "ai" to "人工智能", "privacy" to "隐私",
                    "networking" to "网络", "media" to "媒体", "social" to "社交",
                    "reading" to "阅读", "tools" to "工具",
                )
                val selectedId = (if (category == AppCategory.APPS) selection.appOption else selection.gameOption)
                    ?: choices.first().first
                SelectionChips(
                    items = choices,
                    selected = choices.firstOrNull { it.first == selectedId } ?: choices.first(),
                    label = { it.second },
                    onSelect = { viewModel.selectOption(category, it.first) },
                )
            } else if (auroraCharts) {
                // Aurora 榜单：四种榜单类型（免费/付费/畅销/飙升），应用/游戏由页签区分
                SelectionChips(
                    items = com.app.market.domain.model.market.ChartKind.entries,
                    selected = selection.chart,
                    label = {
                        when (it) {
                            com.app.market.domain.model.market.ChartKind.FREE -> stringResource(Res.string.ranking_free)
                            com.app.market.domain.model.market.ChartKind.PAID -> stringResource(Res.string.ranking_paid)
                            com.app.market.domain.model.market.ChartKind.GROSSING -> stringResource(Res.string.ranking_grossing)
                            com.app.market.domain.model.market.ChartKind.MOVERS -> stringResource(Res.string.ranking_movers)
                        }
                    },
                    onSelect = viewModel::selectChart,
                )
            } else if (category == AppCategory.APPS && !dynamicOptions && pageSource != AppSource.HUAWEI && pageSource != AppSource.GITHUB) {
                SelectionChips(
                    items = AppSubCategory.entries,
                    selected = selection.appSubCategory,
                    label = { stringResource(it.labelRes) },
                    onSelect = viewModel::selectSubCategory,
                )
            }
            if (dynamicOptions && optionList.isNotEmpty()) {
                val selectedId = if (category == AppCategory.APPS) selection.appOption else selection.gameOption
                SelectionChips(
                    items = optionList,
                    selected = optionList.firstOrNull { it.id == selectedId } ?: optionList.first(),
                    label = { it.name },
                    onSelect = { viewModel.selectOption(category, it.id) },
                )
            }
            if (category == AppCategory.GAMES && pageSource == AppSource.TAPTAP) {
                SelectionChips(
                    items = GameRanking.entries,
                    selected = selection.gameRanking,
                    label = { stringResource(it.labelRes) },
                    onSelect = viewModel::selectGameRanking,
                )
            }
            if (category == AppCategory.GAMES && pageSource == AppSource.XIAOMI) {
                SelectionChips(
                    items = GameSubCategory.entries,
                    selected = selection.gameSubCategory,
                    label = { stringResource(it.labelRes) },
                    onSelect = viewModel::selectGameSubCategory,
                )
            }
        }
        val loadingSection = state.items.isEmpty() && lastItems.isEmpty() && (state.loading || !sectionReady)
        val showLoading = (loadingSection && state.errorMessage.isEmpty()) || (waitingForOptions && state.errorMessage.isEmpty())
        val contentKey = if (showLoading) "loading-${category.name}" else "list-${category.name}-${selection.hashCode()}"
        val sectionIndex = when {
            pageSource == AppSource.GITHUB && category == AppCategory.GAMES ->
                listOf("all", "ai", "privacy", "networking", "media", "social", "reading", "tools")
                    .indexOf(selection.gameOption ?: "all").coerceAtLeast(0)
            pageSource == AppSource.GITHUB && category == AppCategory.APPS ->
                listOf("trending", "releases", "popular")
                    .indexOf(selection.appOption ?: "trending").coerceAtLeast(0)
            category == AppCategory.GAMES -> when {
                pageSource == AppSource.TAPTAP -> selection.gameRanking.ordinal
                selection.gameOption != null -> optionList.indexOfFirst { it.id == selection.gameOption }.coerceAtLeast(0) + 100
                else -> selection.gameSubCategory.ordinal + 100
            }
            else -> when {
                selection.appOption != null -> optionList.indexOfFirst { it.id == selection.appOption }.coerceAtLeast(0) + 100
                else -> selection.appSubCategory.ordinal
            }
        }
        // 切换动画：仅换来源/榜单类型时柔和淡入淡出；页签/分类切换小位移滑动（整块横滑会被看成「乱飞」）
        AnimatedContent(
            targetState = listOf(category.ordinal, sectionIndex, pageSource.ordinal, selection.chart.ordinal),
            transitionSpec = {
                val sameCategory = initialState[0] == targetState[0]
                val sameIndex = initialState[1] == targetState[1]
                val forward = if (sameCategory) {
                    targetState[1] >= initialState[1]
                } else {
                    targetState[0] == AppCategory.APPS.ordinal
                }
                if (sameCategory && sameIndex) {
                    fadeIn(tween(170)).togetherWith(fadeOut(tween(130)))
                } else {
                    (slideInHorizontally(tween(260)) { width -> if (forward) width / 4 else -width / 4 } +
                        fadeIn(tween(170)))
                        .togetherWith(
                            slideOutHorizontally(tween(200)) { width -> if (forward) -width / 6 else width / 6 } +
                                fadeOut(tween(130)),
                        )
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = contentKey,
        ) { _ ->
            if (showLoading) {
                LoadingBox(Modifier.fillMaxSize().padding(PaddingValues(PageVerticalPadding)))
            } else {
                PullToRefresh(
                    isRefreshing = state.refreshingByPull,
                    onRefresh = { viewModel.refreshFromPull(category) },
                    pullToRefreshState = pullToRefreshState,
                    contentPadding = PaddingValues(),
                    refreshTexts = listOf(
                        stringResource(Res.string.pull_refresh_pull),
                        stringResource(Res.string.pull_refresh_release),
                        stringResource(Res.string.pull_refresh_refreshing),
                        stringResource(Res.string.pull_refresh_done),
                    ),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollEndHaptic()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = contentPadding,
                    ) {
                        if (state.errorMessage.isNotEmpty()) {
                            item(key = "error") {
                                Text(
                                    text = state.errorMessage,
                                    color = MiuixTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                                Text(
                                    text = retryText,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp, vertical = 8.dp)
                                        .clickable { viewModel.refresh(category) },
                                )
                            }
                        } else if (!state.loading && displayItems.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    text = noResults,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                        itemsIndexed(displayItems, key = { _, it -> "${pageSource.name}:${category.name}:${it.app.packageName}" }) { index, item ->
                            val app = item.app
                            val resolvedApp = repositoryApps[app.packageName] ?: app
                            val effectiveItem = if (resolvedApp === app) item else item.copy(
                                app = resolvedApp,
                                actionKind = resolvedApp.actionKind(),
                            )
                            val packageName = app.packageName
                            val downloadPackageName = repositoryDownloadPackages[packageName] ?: packageName
                            val downloadState by remember(downloadPackageName, packageName in resolvingRepositories) {
                                derivedStateOf {
                                    if (packageName in resolvingRepositories) null else downloadStates.value[downloadPackageName]
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateItem()) {
                                Text(
                                    text = "${index + 1}",
                                    style = MiuixTheme.textStyles.headline1,
                                    color = if (index < 3) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.width(28.dp),
                                    textAlign = TextAlign.Center,
                                )
                                AppRow(
                                app = app,
                                modifier = Modifier.weight(1f),
                                actionText = when (effectiveItem.actionKind) {
                                    AppActionKind.INSTALL -> installText
                                    AppActionKind.UPDATE -> updateText
                                    AppActionKind.OPEN -> openText
                                    AppActionKind.RESERVE -> reserveText
                                },
                                actionKind = effectiveItem.actionKind,
                                downloadState = downloadState,
                                preparingDownload = packageName in resolvingRepositories,
                                onCancelPreparing = { viewModel.clearRepositoryDownload(packageName) },
                                onOpenDetail = { onOpenDetail(app) },
                                onAction = { if (effectiveItem.actionKind == AppActionKind.OPEN) viewModel.onAction(effectiveItem) else viewModel.onAction(item) },
                                onResumeDownload = { viewModel.download(app, item.actionKind == AppActionKind.UPDATE) },
                                onInstallDownloaded = viewModel::installDownloaded,
                                onCancel = viewModel::cancelDownload,
                                )
                            }
                        }
                        if (state.loading && displayItems.isNotEmpty()) {
                            // 切了分类、新数据还没到时：列表先保留旧内容，顶部提示正在加载
                            item(key = "loading-new-section") { LoadingBox() }
                        }
                        if (state.loadingMore) {
                            item(key = "loadmore") { LoadingBox() }
                        }
                    }
                }
            }
        }
        }
    }
}

/** 榜单页顶部的「游戏榜 / 软件榜」两个大号页签（截图同款：选中项下方一条指示条）。 */
@Composable
private fun RankingCategoryToggle(
    selected: AppCategory,
    onSelect: (AppCategory) -> Unit,
    modifier: Modifier = Modifier,
    isGithub: Boolean = false,
) {
    val positions = remember { mutableStateOf<Map<AppCategory, Pair<Int, Int>>>(emptyMap()) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val indicatorWidth = with(density) { 28.dp.roundToPx() }
    val selectedPosition = positions.value[selected]
    val targetX = selectedPosition?.let { (left, width) -> left + (width - indicatorWidth) / 2 } ?: 0
    val indicatorX by animateIntAsState(targetValue = targetX, animationSpec = tween(260), label = "ranking-indicator")
    Box(modifier = modifier.padding(start = 8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        (if (isGithub) listOf(AppCategory.GAMES, AppCategory.APPS) else AppCategory.entries).forEach { category ->
            val isSelected = category == selected
            Column(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInParent().x.toInt() to coordinates.size.width
                    if (positions.value[category] != position) positions.value = positions.value + (category to position)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isGithub) {
                        if (category == AppCategory.APPS) "排行榜" else "发现"
                    } else stringResource(
                        if (category == AppCategory.GAMES) Res.string.nav_games_bang else Res.string.nav_apps_bang,
                    ),
                    style = MiuixTheme.textStyles.headline1,
                    color = if (isSelected) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable(interactionSource = null, indication = null) { onSelect(category) }
                        .padding(vertical = 6.dp),
                )
                Box(Modifier.padding(top = 2.dp).width(28.dp).height(3.dp))
            }
        }
    }
    if (selectedPosition != null) {
        Box(
            Modifier.align(Alignment.BottomStart)
                .offset { IntOffset(indicatorX, 0) }
                .width(28.dp).height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MiuixTheme.colorScheme.primary),
        )
    }
    }
}

/** 只有一个榜单时的单个标签：样式与选中态一致，但不可点。 */
@Composable
private fun RankingSingleTab(label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.headline1,
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(28.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MiuixTheme.colorScheme.primary),
        )
    }
}

// 分类按钮横向排列、可左右滑动（榜单页顶栏同款）
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SelectionChips(
    items: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            Text(
                text = label(item),
                style = MiuixTheme.textStyles.body1,
                color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                modifier = Modifier
                    .squircleSurface(
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer,
                        cornerRadius = 14.dp,
                    )
                    .clickable { onSelect(item) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private val GameRanking.labelRes: StringResource
    get() = when (this) {
        GameRanking.HOT -> Res.string.ranking_hot
        GameRanking.NEW -> Res.string.ranking_new
        GameRanking.SELL -> Res.string.ranking_sell
    }

private val AppSubCategory.labelRes: StringResource
    get() = when (this) {
        AppSubCategory.TOOLS -> Res.string.category_tools
        AppSubCategory.MEDIA -> Res.string.category_media
        AppSubCategory.SOCIAL -> Res.string.category_social
        AppSubCategory.OFFICE -> Res.string.category_office
        AppSubCategory.EDUCATION -> Res.string.category_education
        AppSubCategory.SHOPPING -> Res.string.category_shopping
        AppSubCategory.SPORTS -> Res.string.category_sports
    }

private val GameSubCategory.labelRes: StringResource
    get() = when (this) {
        GameSubCategory.ALL -> Res.string.game_category_all
        GameSubCategory.STRATEGY -> Res.string.game_category_strategy
        GameSubCategory.ACTION -> Res.string.game_category_action
        GameSubCategory.RACING -> Res.string.game_category_racing
        GameSubCategory.RPG -> Res.string.game_category_rpg
        GameSubCategory.CARD -> Res.string.game_category_card
        GameSubCategory.FIGHTING -> Res.string.game_category_fighting
        GameSubCategory.KIDS -> Res.string.game_category_kids
        GameSubCategory.CASUAL -> Res.string.game_category_casual
        GameSubCategory.FLIGHT -> Res.string.game_category_flight
        GameSubCategory.RUNNER -> Res.string.game_category_runner
    }