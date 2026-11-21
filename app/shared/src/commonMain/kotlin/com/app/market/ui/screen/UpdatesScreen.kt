package com.app.market.ui.screen

import com.app.market.ui.util.appSourceLetterColors
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.withStyle
import com.app.market.ui.util.toPlainStoreText
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import com.app.market.resources.update_source_skipped
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.download.DownloadPhase
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.platform.UiPlatform
import com.app.market.resources.Res
import com.app.market.resources.app_list_permission_refresh_hint
import com.app.market.resources.app_list_permission_required
import com.app.market.resources.back
import com.app.market.resources.collapse_change_log
import com.app.market.resources.expand_change_log
import com.app.market.resources.go_to_settings
import com.app.market.resources.ignore_once
import com.app.market.resources.ignore_permanent
import com.app.market.resources.nav_updates
import com.app.market.resources.no_change_log
import com.app.market.resources.pull_refresh_done
import com.app.market.resources.pull_refresh_pull
import com.app.market.resources.pull_refresh_refreshing
import com.app.market.resources.pull_refresh_release
import com.app.market.resources.no_updates
import com.app.market.resources.num_updates_pending
import com.app.market.resources.test_version
import com.app.market.resources.update
import com.app.market.resources.update_all
import com.app.market.resources.update_check_failed
import com.app.market.resources.update_check_progress
import com.app.market.resources.updates_title
import com.app.market.resources.version_transition
import com.app.market.resources.version_transition_label
import com.app.market.ui.component.AppActionButton
import com.app.market.ui.component.SourceTag
import com.app.market.ui.component.rememberDownloadSourceBadge
import com.app.market.ui.component.AppButton
import com.app.market.ui.component.AppIcon
import com.app.market.ui.component.CardSegmentContainer
import com.app.market.ui.component.LoadingBox
import com.app.market.ui.component.ModuleReadmeText
import com.app.market.ui.component.MainTabScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.ui.model.AppActionKind
import com.app.market.ui.util.APP_MARKET_LABEL
import com.app.market.ui.util.appSourceBrandBrush
import com.app.market.domain.model.theme.DownloadSourceBadge
import com.app.market.ui.util.appSourceBrandColor
import com.app.market.ui.util.AppMarketBrandColor
import com.app.market.ui.util.appDisplayName
import com.app.market.ui.util.appMarketBrandBrush
import com.app.market.ui.util.appSourceLabel
import com.app.market.ui.util.formatSize
import com.app.market.ui.util.markdownToHtml
import com.app.market.viewmodel.SELF_UPDATE_APP_ID
import com.app.market.viewmodel.UpdatesViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.basic.Checkbox
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState

@Composable
fun UpdatesTab(
    viewModel: UpdatesViewModel,
    bottomPadding: Dp,
    onOpenDetail: (MarketAppInfo) -> Unit,
) {
    val uiPlatform = koinInject<UiPlatform>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadStates = viewModel.downloadStates.collectAsStateWithLifecycle()
    val skippedSources by viewModel.skippedSources.collectAsStateWithLifecycle()
    // 有来源超时被跳过：弹一次提示后立即复位，避免重组时重复弹
    val skippedTemplate = stringResource(Res.string.update_source_skipped)
    val skippedNames = skippedSources.map { appSourceLabel(it) }.joinToString("、")
    LaunchedEffect(skippedSources) {
        if (skippedSources.isEmpty()) return@LaunchedEffect
        // 占位符 %1$s 里的 $ 在 Kotlin 字符串中是模板起点，用常量拼接规避
        val placeholder = "%1" + "$" + "s"
        uiPlatform.showToast(skippedTemplate.replace(placeholder, skippedNames))
        viewModel.clearSkippedSources()
    }
    val listState = rememberLazyListState()

    MainTabScaffold(
        title = stringResource(Res.string.nav_updates)
    ) { topPadding, backdropModifier, scrollBehavior ->
        val listPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = PageVerticalPadding,
            bottom = bottomPadding + PageVerticalPadding,
        )
        val content = when {
            state.updates.isNotEmpty() -> UpdatesContent.Cards
            state.loading -> UpdatesContent.Loading
            state.appListPermissionRequired -> UpdatesContent.PermissionRequired
            state.checkFailed -> UpdatesContent.Error
            else -> UpdatesContent.Empty
        }
        Column(Modifier.fillMaxSize().padding(top = topPadding)) {
            // Fade between the full-screen states and the card list; keyed on the state shape so a data
            // refresh doesn't re-fade the whole tree (cards animate individually via animateItem).
            Crossfade(
                targetState = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MiuixTheme.colorScheme.surface)
                    .then(backdropModifier), label = "updates"
            ) { target ->
            when (target) {
                // 检测中显示原本的全屏进度条
                UpdatesContent.Loading -> UpdatesCheckingProgress(
                    done = state.checkDone,
                    total = state.checkTotal,
                    modifier = Modifier.fillMaxSize().padding(listPadding),
                )
                UpdatesContent.PermissionRequired -> AppListPermissionRequiredState(
                    modifier = Modifier.fillMaxSize().padding(listPadding),
                    uiPlatform = uiPlatform,
                )

                // 「全部已是最新」和「检查失败」两个空态也要能下拉重新检查：
                // 之前只有卡片列表包了 PullToRefresh，没有更新时下拉完全没反应
                UpdatesContent.Error -> RefreshableHint(
                    text = stringResource(Res.string.update_check_failed),
                    padding = listPadding,
                    refreshing = false,
                    onRefresh = { viewModel.refresh() },
                )

                UpdatesContent.Empty -> RefreshableHint(
                    text = stringResource(Res.string.no_updates),
                    padding = listPadding,
                    refreshing = false,
                    onRefresh = { viewModel.refresh() },
                )

                UpdatesContent.Cards -> Column(Modifier.fillMaxSize()) {
                    val pullToRefreshState = rememberPullToRefreshState()
                    PullToRefresh(
                        // 检测中的进度只看卡片顶部那条进度（「检查更新中 x/y」），
                        // 不再让下拉刷新头部弹那个转圈动画；下拉手势仍然可用（松手就收回去）
                        isRefreshing = false,
                        onRefresh = { viewModel.refresh() },
                        pullToRefreshState = pullToRefreshState,
                        contentPadding = PaddingValues(),
                        // 下拉只作为「重新检查」的触发手势：不显示任何刷新文案，
                        // 进度统一由卡片顶部的检查进度条表达
                        refreshTexts = listOf("", "", "", ""),
                    ) {
                    UpdatesCardList(
                        updates = state.updates,
                        downloadStates = downloadStates,
                        checkDone = state.checkDone,
                        checkTotal = state.checkTotal,
                        listState = listState,
                        listPadding = listPadding,
                        viewModel = viewModel,
                        onOpenDetail = onOpenDetail,
                        modifier = Modifier
                            .fillMaxHeight()
                            .scrollEndHaptic()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    )
                    }
                }
            }
        }
        }
    }
}

private enum class UpdatesContent { Loading, PermissionRequired, Error, Empty, Cards }

@Composable
private fun UpdatesCardList(
    updates: List<MarketAppInfo>,
    downloadStates: State<Map<String, DownloadState>>,
    checkDone: Int,
    checkTotal: Int,
    listState: LazyListState,
    listPadding: PaddingValues,
    viewModel: UpdatesViewModel,
    onOpenDetail: (MarketAppInfo) -> Unit,
    modifier: Modifier
) {
    val showUpdateSource by viewModel.showUpdateSource.collectAsStateWithLifecycle()
    val updateActionText = stringResource(Res.string.update)
    val totalUpdateSize = remember(updates) {
        updates.sumOf { app -> app.deltaSize.takeIf { it > 0 } ?: app.apkSize }
    }
    val sourceBadge = rememberDownloadSourceBadge()
    val sourceBranded = sourceBadge == DownloadSourceBadge.BRAND
    val multiSelectMode by viewModel.multiSelect.collectAsStateWithLifecycle()
    val multiSelected by viewModel.multiSelected.collectAsStateWithLifecycle()
    // 自更新卡片是否来自 GitLab 预发布（Upcoming Release）：是的话打「测试版」标签
    val selfUpdateUi by viewModel.selfUpdateState.collectAsStateWithLifecycle()
    val selfUpdatePrerelease = selfUpdateUi.update?.prerelease == true
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = listPadding,
    ) {
        item(key = "summary") {
            var cardLeftX by remember { mutableStateOf(0f) }
            // 与刚进更新页弹出的检测进度条同一口径：已拉取到的更新来源 / 更新来源总数
            val batchProgress = if (checkTotal > 0) checkDone.toFloat() / checkTotal else 0f
            // 进度色块：检测期间蓝色铺底，满了回到白卡
            val filling = batchProgress > 0f && batchProgress < 1f
            Card(
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .fillMaxWidth(),
            ) {
BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().onGloballyPositioned { cardLeftX = it.localToWindow(Offset.Zero).x },
                ) {
                    // 进度块只从左侧铺到 batchProgress 位置：这个宽度就是「蓝色已经盖到哪里」
                    val barWidth = maxWidth * batchProgress.coerceIn(0f, 1f)
                    if (filling) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        0f to AppMarketBrandColor,
                                        batchProgress to AppMarketBrandColor,
                                        batchProgress to Color.Transparent,
                                        1f to Color.Transparent,
                                    )
                                ),
                        )
                    }
                    val checking = checkTotal > 0 && checkDone < checkTotal
                    // 检查完成：标题「向下 + 变大 + 平移居中」一个连续动画；检查中保持原样
                    val titleAnim by animateFloatAsState(
                        targetValue = if (checking) 0f else 1f,
                        animationSpec = tween(durationMillis = 400, easing = EaseInOutCubic),
                    )
                    // 文字颜色只做黑白两色：被蓝色进度块盖住的部分画白字，没盖住的部分画常规字色。
                    // 具体哪些字被盖住按是否被蓝块盖过大半决定（不做补色混合，也不叠图）。
                    val onFillColor = Color.White
                    val normalColor = MiuixTheme.colorScheme.onSurface
                    val normalSubColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    val density = LocalDensity.current
                    // 蓝块右缘在窗口坐标系里的 x：每段文字自己量出自己的 x，两者相减就是
                    // 「这段文字被蓝块盖住了多少像素」——不依赖 16dp 内距或列宽推算。
                    val barEndX = cardLeftX + with(density) { barWidth.toPx() }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BoxWithConstraints(modifier = Modifier.weight(1f)) {
                            val titleText = stringResource(Res.string.num_updates_pending, updates.size)
                            // 高度仅由 titleAnim 一条动画驱动（不要再叠 animateContentSize，双动画会打架抽搐）
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = lerp(72f, 38f, titleAnim).dp),
                            ) {
                                // 居中组只含「待更新」标题 + 检查进度行：这两行的中间 = 内容区中心
                                Column {
                                    ToneText(
                                        text = titleText,
                                        baseColor = normalColor,
                                        fillColor = onFillColor,
                                        barEndX = barEndX,
                                        active = checking,
                                        style = MiuixTheme.textStyles.body1.copy(
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 26.sp,
                                        ),
                                    )
                                    if (checking) {
                                        ToneText(
                                            text = stringResource(Res.string.update_check_progress, checkDone, checkTotal),
                                            baseColor = normalSubColor,
                                            fillColor = onFillColor,
                                            barEndX = barEndX,
                                            active = checking,
                                            style = MiuixTheme.textStyles.body2.copy(lineHeight = 16.sp),
                                        )
                                    }
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppButton(
                                text = stringResource(Res.string.update_all),
                                onClick = { viewModel.downloadAll(updates) },
                                cornerRadius = 100.dp,
                                minHeight = 34.dp,
                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            )
                            // 更新总大小：常显在「全部更新」下方（原来挂在检查进度里，检查完会消失）
                            if (totalUpdateSize > 0L) {
                                Spacer(Modifier.height(6.dp))
                                ToneText(
                                    text = formatSize(totalUpdateSize),
                                    baseColor = normalSubColor,
                                    fillColor = onFillColor,
                                    barEndX = barEndX,
                                    active = checking,
                                    style = MiuixTheme.textStyles.body2.copy(lineHeight = 16.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
        itemsIndexed(updates, key = { _, app -> app.packageName }) { index, app ->
            val packageName = app.packageName
            // LSPosed 模块：用本地已安装图标的 PNG 覆盖（仓库接口不带图标）
            val iconOverrides by viewModel.iconOverrides.collectAsStateWithLifecycle()
            val rowApp = iconOverrides[packageName]?.let { app.copy(icon = it) } ?: app
            val downloadState by remember(packageName, app.versionCode) {
                derivedStateOf {
                    // 换源后旧来源的 DownloadState 仍按 packageName 留在 map 里，
                    // 新来源同 packageName 但不同 versionCode 的条目不能 join 到旧进度，
                    // 否则会误显示 100%（旧任务已完成的状态）。
                    downloadStates.value[packageName]?.takeIf { it.versionCode == app.versionCode }
                }
            }
            CardSegmentContainer(
                isFirst = index == 0,
                isLast = index == updates.lastIndex,
                modifier = Modifier.animateItem(placementSpec = null),
                horizontalPadding = 0.dp,
                // 自更新走 AppMarket 品牌渐变；Google Play 用图标四色渐变；其余来源铺单色品牌底
                containerBrush = if (!sourceBranded) {
                    null
                } else {
                    run {
                        val base = CardDefaults.defaultColors().color
                        if (app.appId == SELF_UPDATE_APP_ID) {
                            appMarketBrandBrush(base)
                        } else {
                            appSourceBrandBrush(app.source, base)
                        }
                    }
                },
                containerColor = if (!sourceBranded) {
                    CardDefaults.defaultColors().color
                } else {
                    run {
                        val base = CardDefaults.defaultColors().color
                        if (app.appId == SELF_UPDATE_APP_ID || appSourceBrandBrush(app.source, base) != null) {
                            null
                        } else {
                            lerp(base, appSourceBrandColor(app.source), 0.22f)
                        }
                    }
                },
            ) {
                UpdateItemRow(
                    app = rowApp,
                    downloadState = downloadState,
                    onOpenDetail = { if (app.appId != SELF_UPDATE_APP_ID) onOpenDetail(app) },
                    onAction = { viewModel.download(app) },
                    onInstallDownloaded = viewModel::installDownloaded,
                    showSource = sourceBranded,
                    sourceBranded = sourceBranded,
                    // 自更新拿到的是 GitLab 预发布 → 打黄色「测试版」标签
                    testVersion = app.appId == SELF_UPDATE_APP_ID && selfUpdatePrerelease,
                    // 自更新不属于任何商店来源，不能打「小米」这类来源标签
                    sourceLabelTag = if (app.appId == SELF_UPDATE_APP_ID) {
                        null
                    } else if (sourceBadge == DownloadSourceBadge.PLAIN) {
                        appSourceLabel(app.source)
                    } else {
                        null
                    },
                    onCancel = viewModel::cancelDownload,
                    onIgnoreOnce = { viewModel.ignoreOnce(app) },
                    onIgnorePermanent = { viewModel.ignorePermanently(app) },
                    actionText = updateActionText,
                    multiSelectMode = multiSelectMode,
                    multiSelected = app.packageName in multiSelected,
                    onToggleSelect = { viewModel.toggleMultiSelected(app.packageName) },
                    onLongPress = {
                        if (!multiSelectMode) {
                            viewModel.enterMultiSelect(app.packageName)
                        } else {
                            viewModel.toggleMultiSelected(app.packageName)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun UpdatesHeader(onBack: () -> Unit) {
    val layoutDirection = LocalLayoutDirection.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Back,
                contentDescription = stringResource(Res.string.back),
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                    },
            )
        }
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.updates_title),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.title1.copy(lineHeight = 40.sp),
            )
        }
    }
}

// 也被更新历史页复用：折叠态把日志空白压平成单行
/** 更新卡片展开动画：正文高度变化与操作行出入场共用一个时长。 */
private val changeLogExpandSpec = tween<IntSize>(durationMillis = 200)
private val changeLogFadeSpec = tween<Float>(durationMillis = 200)

/** 收起只有一段：卡片高度从「忽略」那一行起平滑向上收缩，不再有第二次跳变。 */
private val changeLogCollapseSpec = tween<IntSize>(durationMillis = 260, easing = FastOutSlowInEasing)
private val changeLogCollapseFadeSpec = tween<Float>(durationMillis = 200)

internal val WhitespaceRegex = Regex("\\s+")

@Composable
private fun UpdateItemRow(
    app: MarketAppInfo,
    downloadState: DownloadState?,
    actionText: String,
    onOpenDetail: () -> Unit,
    onAction: () -> Unit,
    onInstallDownloaded: (String) -> Unit,
    onCancel: (String) -> Unit,
    onIgnoreOnce: () -> Unit,
    onIgnorePermanent: () -> Unit,
    multiSelectMode: Boolean = false,
    multiSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongPress: () -> Unit = {},
    showSource: Boolean = true,
    sourceBranded: Boolean = true,
    sourceLabelTag: String? = null,
    /** 该条目是测试版（GitLab 预发布自更新）：应用名右侧打黄色「测试版」标签。 */
    testVersion: Boolean = false,
) {
    var expanded by remember(app.packageName, app.versionCode) { mutableStateOf(false) }
    // 测试版标签的黄色：浅色主题用深黄保对比度，深色主题用亮黄
    val testVersionTagColor = if (isSystemInDarkTheme()) Color(0xFFFDD663) else Color(0xFFB06000)
    val changeLog = app.changeLog.toPlainStoreText().ifBlank { stringResource(Res.string.no_change_log) }
    // 折叠态把换行等空白压平成单行连续文字，避免首行只是「新增」之类的小标题
    val collapsedChangeLog = remember(changeLog) {
        changeLog.replace(WhitespaceRegex, " ").trim()
    }
    // 「展开」除了展开日志还负责露出忽略操作，因此折叠态始终显示，不做溢出检测
    val showMore = !expanded
    val changeLogStyle = MiuixTheme.textStyles.body2
    val isSelfUpdate = app.appId == SELF_UPDATE_APP_ID
    val uriHandler = LocalUriHandler.current
    // 品牌水印：来源名放大铺在卡片右下角，14% 透明度压在品牌底色上；showSource=false 时隐藏
    // 自更新不来自任何市场源，水印直接写品牌名，避免出现「小米」
    val brandText = when {
        !showSource -> ""
        app.appId == SELF_UPDATE_APP_ID -> APP_MARKET_LABEL
        else -> appSourceLabel(app.source)
    }
    // 水印色：自更新用品牌蓝，其余来源用各自品牌色
    val brandColor = when {
        !sourceBranded -> MiuixTheme.colorScheme.onSurfaceVariantActions
        app.appId == SELF_UPDATE_APP_ID -> AppMarketBrandColor
        else -> appSourceBrandColor(app.source)
    }
    val textMeasurer = rememberTextMeasurer()
    // Google Play 的水印用 Play 图标那种四色连续渐变（蓝→绿→黄→红）的混色，
    // 不是单色 —— 单色铺上去就是一团蓝，跟品牌识别对不上。
    val dark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f
    val letterColors = if (sourceBranded) appSourceLetterColors(app.source, dark) else null
    val isGooglePlay = sourceBranded && app.appId != SELF_UPDATE_APP_ID && app.source == AppSource.GOOGLE
    // 彩色（渐变/逐字母）比单色更容易被底色吃掉，透明度给得更高一档
    val brandAlpha = if (isGooglePlay || letterColors != null) 0.30f else 0.14f
    val brandTextLayout = remember(brandText, letterColors) {
        if (brandText.isEmpty()) null
        else textMeasurer.measure(
            text = buildAnnotatedString {
                when {
                    letterColors != null -> brandText.forEachIndexed { index, ch ->
                        withStyle(
                            SpanStyle(
                                color = letterColors.getOrElse(index) { letterColors[0] }
                                    .copy(alpha = brandAlpha),
                            ),
                        ) {
                            append(ch)
                        }
                    }
                    else -> append(brandText)
                }
            },
            style = TextStyle(
                fontWeight = FontWeight.Black,
                fontSize = 40.sp,
                letterSpacing = 2.sp,
            ),
        )
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        if (brandTextLayout != null) {
        // 品牌字放大排在卡片右下，半透明水印感；裁切保证不越出卡片
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(
                    with(androidx.compose.ui.platform.LocalDensity.current) {
                        brandTextLayout.size.height.toDp() + 8.dp
                    }
                )
                .clipToBounds(),
        ) {
            val textTop = size.height - brandTextLayout.size.height - 4.dp.toPx()
            val textLeft = size.width - brandTextLayout.size.width - 20.dp.toPx()
            if (isGooglePlay) {
                drawText(
                    textLayoutResult = brandTextLayout,
                    color = brandColor.copy(alpha = 0.18f),
                    topLeft = Offset(x = textLeft, y = textTop),
                )
            } else {
                drawText(
                    textLayoutResult = brandTextLayout,
                    // 逐字母配色时颜色已经写进 AnnotatedString，这里让 drawText 用文字自带的颜色
                    color = if (letterColors != null) {
                        androidx.compose.ui.graphics.Color.Unspecified
                    } else {
                        brandColor.copy(alpha = 0.14f)
                    },
                    topLeft = Offset(x = textLeft, y = textTop),
                )
            }
        }
        }
    // 多选模式下收紧行内边距：卡片本体缩小时卡片间距也一起缩
    val rowPadding by animateDpAsState(
        targetValue = if (multiSelectMode) 10.dp else 16.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "rowPadding",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 长按进入多选；多选模式下点击卡片即勾选
            .combinedClickable(
                onClick = { if (multiSelectMode) onToggleSelect() else onOpenDetail() },
                onLongClick = onLongPress,
            )
            .padding(rowPadding)
            // 整张卡的高度统一由这一处动画驱动：正文与操作行不再各自动高度互相抢
            .animateContentSize(
                animationSpec = if (expanded) changeLogExpandSpec else changeLogCollapseSpec,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(
                    url = app.icon,
                    contentDescription = appDisplayName(app),
                    size = 48.dp,
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = appDisplayName(app),
                            modifier = Modifier.weight(1f, fill = false),
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MiuixTheme.textStyles.headline1.copy(
                                fontWeight = FontWeight.Medium,
                                lineHeight = 22.sp,
                            ),
                        )
                        if (testVersion) {
                            Spacer(Modifier.width(6.dp))
                            // 测试版标签：和来源标签同一套胶囊样式，黄色区分
                            SourceTag(
                                text = stringResource(Res.string.test_version),
                                color = testVersionTagColor,
                                modifier = Modifier.widthIn(max = 72.dp),
                            )
                        }
                        if (!sourceLabelTag.isNullOrBlank()) {
                            Spacer(Modifier.width(6.dp))
                            // 来源标签自己限宽+省略，绝不把应用名挤掉
                            SourceTag(
                                text = sourceLabelTag,
                                modifier = Modifier.widthIn(max = 96.dp),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.version_transition_label),
                            maxLines = 1,
                            style = MiuixTheme.textStyles.body2.copy(lineHeight = 18.sp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = stringResource(
                                Res.string.version_transition,
                                app.installedVersionName.ifBlank { "-" },
                                app.versionName,
                            ),
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .basicMarquee(iterations = Int.MAX_VALUE),
                            maxLines = 1,
                            style = MiuixTheme.textStyles.body2.copy(lineHeight = 18.sp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Text(
                        text = updateSizeText(app),
                        maxLines = 1,
                        style = MiuixTheme.textStyles.body2.copy(lineHeight = 18.sp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            if (multiSelectMode) {
                // 多选模式：更新按钮变成勾选框
                Checkbox(
                    state = if (multiSelected) ToggleableState.On else ToggleableState.Off,
                    onClick = { onToggleSelect() },
                    modifier = Modifier.padding(end = 16.dp),
                )
            } else {
                AppActionButton(
                    packageName = app.packageName,
                    actionText = actionText,
                    actionKind = AppActionKind.UPDATE,
                    downloadState = downloadState,
                    onAction = onAction,
                    onResumeDownload = onAction,
                    onInstallDownloaded = onInstallDownloaded,
                    onCancel = onCancel,
                    minHeight = 34.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        // 展开有动画；收起是瞬时的，不等正文淡出，卡片白底立刻收掉
        Box(Modifier.fillMaxWidth()) {
            Crossfade(
                targetState = expanded,
                // 正文切换要瞬时：一是收起时不能先淡出（淡出期间高度不变 = 分段感），
                // 二是高度统一由根布局的 animateContentSize 驱动，只剩一段平滑收缩
                animationSpec = if (expanded) {
                    tween(durationMillis = 200, easing = FastOutSlowInEasing)
                } else {
                    snap()
                },
                label = "changeLogContent",
            ) { isExpanded ->
                // 不加 SelectionContainer：长按卡片应该进多选，不该弹出选词/选择文本
                if (isSelfUpdate && isExpanded) {
                    // 只有展开后才按 Markdown 渲染整篇发布说明；收起时和普通卡片一样只留一行
                    ModuleReadmeText(
                        html = remember(changeLog) { markdownToHtml(changeLog) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 44.dp),
                        onOpenLink = { url -> runCatching { uriHandler.openUri(url) } },
                    )
                } else {
                    Text(
                        text = if (isExpanded) changeLog else collapsedChangeLog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 44.dp),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Clip,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        style = changeLogStyle,
                    )
                }
            }
            if (showMore) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .height(IntrinsicSize.Min)
                        .clickable(interactionSource = null, indication = null) {
                            expanded = true
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.expand_change_log),
                        color = MiuixTheme.colorScheme.primary,
                        style = changeLogStyle,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)),
            // 退出必须瞬时：fadeOut 期间这一行仍占高度，卡片要等它结束才能收缩 → 就是那个「分段」
            exit = ExitTransition.None,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(Res.string.ignore_once),
                        modifier = Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = onIgnoreOnce,
                        ),
                        color = MiuixTheme.colorScheme.primary,
                        style = changeLogStyle,
                    )
                    Text(
                        text = stringResource(Res.string.ignore_permanent),
                        modifier = Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = onIgnorePermanent,
                        ),
                        color = MiuixTheme.colorScheme.primary,
                        style = changeLogStyle,
                    )
                }
                Text(
                    text = stringResource(Res.string.collapse_change_log),
                    modifier = Modifier.clickable(interactionSource = null, indication = null) {
                        expanded = false
                    },
                    color = MiuixTheme.colorScheme.primary,
                    style = changeLogStyle,
                )
            }
        }
    }
    }
}
internal fun updateSizeText(app: MarketAppInfo) = buildAnnotatedString {
    val fullSize = formatSize(app.apkSize)
    val hasDelta = app.deltaSize > 0L && app.deltaSize < app.apkSize
    if (!hasDelta) {
        append(fullSize)
        return@buildAnnotatedString
    }

    append(formatSize(app.deltaSize))
    append("  ")
    val fullSizeStart = length
    append(fullSize)
    addStyle(
        SpanStyle(textDecoration = TextDecoration.LineThrough),
        fullSizeStart,
        length,
    )
}

/** 更新检查进度条：确定态进度 + 「检查更新中 x/y」。total<=0 时无进度可报，不显示。 */
@Composable
private fun UpdateCheckProgressBar(done: Int, total: Int, modifier: Modifier = Modifier) {
    val progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(Res.string.update_check_progress, done, total),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

/** 还没有任何结果时的全屏检查中状态。 */
@Composable
private fun UpdatesCheckingProgress(done: Int, total: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UpdateCheckProgressBar(done = done, total = total, modifier = Modifier.fillMaxWidth())
    }
}

/** 空态提示 + 下拉刷新：提示文案本身不可滚动，套一层 verticalScroll 才能把下拉手势交给 PullToRefresh。 */
@Composable
private fun RefreshableHint(
    text: String,
    padding: PaddingValues,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefresh(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        pullToRefreshState = pullToRefreshState,
        contentPadding = PaddingValues(),
        refreshTexts = listOf("", "", "", ""),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            UpdatesHint(text, padding)
        }
    }
}

@Composable
private fun UpdatesHint(text: String, padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.main,
        )
    }
}

@Composable
private fun AppListPermissionRequiredState(
    modifier: Modifier = Modifier,
    uiPlatform: UiPlatform,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.app_list_permission_required),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            style = MiuixTheme.textStyles.main,
        )
        Text(
            stringResource(Res.string.app_list_permission_refresh_hint),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            style = MiuixTheme.textStyles.body2,
        )
        AppButton(
            text = stringResource(Res.string.go_to_settings),
            onClick = { uiPlatform.openAppSettings() },
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 100.dp,
            colors = ButtonDefaults.buttonColorsPrimary(),
        )
    }
}

/**
 * 逐像素反色文字：常规色把整句画出来，再用同一句白字「只画被蓝块盖住的那一段宽度」
 * （裁剪是硬边界，颜色就是纯 White，不经任何混合、渐变、蒙版插值）。
 *
 * 两层是同一个字符串、同一套排版，位置完全重合，所以不会错位也不会变虚。
 * 被盖住多少像素 = 蓝块右缘的窗口 x - 这段文字自己的窗口 x。
 */
@Composable
private fun ToneText(
    text: String,
    baseColor: Color,
    fillColor: Color,
    barEndX: Float,
    active: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    var selfX by remember { mutableStateOf(0f) }
    Box(modifier = modifier.onGloballyPositioned { selfX = it.localToWindow(Offset.Zero).x }) {
        BasicText(text = text, style = style.copy(color = baseColor), maxLines = 1)
        val right = barEndX - selfX
        if (active && right > 0f) {
            BasicText(
                text = text,
                style = style.copy(color = fillColor),
                maxLines = 1,
                modifier = Modifier.drawWithContent {
                    val content = this
                    if (right >= size.width) {
                        drawContent()
                    } else {
                        clipRect(0f, 0f, right, size.height) { content.drawContent() }
                    }
                },
            )
        }
    }
}
