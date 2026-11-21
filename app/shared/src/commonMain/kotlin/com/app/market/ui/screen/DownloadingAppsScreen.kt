package com.app.market.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.download.DownloadPhase
import com.app.market.domain.model.download.DownloadState
import com.app.market.resources.Res
import com.app.market.resources.cancel_all
import com.app.market.resources.cancel_all_message
import com.app.market.resources.cancel
import com.app.market.resources.cancel_download_title
import com.app.market.resources.confirm
import com.app.market.resources.download
import com.app.market.resources.download_failed
import com.app.market.resources.download_finished
import com.app.market.resources.download_paused
import com.app.market.resources.download_ready
import com.app.market.resources.downloading_apps
import com.app.market.resources.download_queue_title
import com.app.market.resources.downloading_apps_empty
import com.app.market.resources.downloading_short
import com.app.market.resources.installing_short
import com.app.market.resources.pause_all
import com.app.market.resources.start_all
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.app.market.resources.expand_change_log
import com.app.market.resources.version_transition
import com.app.market.resources.version_transition_label
import com.app.market.ui.component.AppActionButton
import com.app.market.ui.component.SourceTag
import com.app.market.ui.component.rememberDownloadSourceBadge
import com.app.market.ui.widget.DropdownWithStartMarginProvider
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.TabRow
import com.app.market.resources.more_options
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.glass.GlassDropdownPopup
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import com.app.market.ui.component.formatSpeed
import com.app.market.ui.component.AppGlassDropdownMenu
import top.yukonga.miuix.kmp.basic.IconButton
import com.app.market.ui.component.AppCompactButton
import com.app.market.domain.model.market.AppSource
import com.app.market.ui.component.AppIcon
import com.app.market.ui.component.LsposedModuleIconMarker
import com.app.market.ui.component.SelfAppIconMarker
import com.app.market.viewmodel.SELF_UPDATE_APP_ID
import com.app.market.ui.component.AppTextButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.app.market.ui.util.AppMarketBrandColor
import com.app.market.domain.model.theme.DownloadSourceBadge
import com.app.market.ui.util.appSourceBrandColor
import com.app.market.ui.util.appSourceLabel
import com.app.market.ui.util.appSourceLetterColors
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.luminance
import com.app.market.ui.component.CardSegmentContainer
import com.app.market.ui.component.MainTabScaffold
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.ui.model.AppActionKind
import androidx.compose.ui.graphics.lerp
import com.app.market.ui.util.appDisplayName
import com.app.market.ui.util.toPlainStoreText
import com.app.market.ui.util.appSourceBrandBrush
import com.app.market.ui.util.appSourceBrandColor
import com.app.market.viewmodel.DownloadingAppsViewModel
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.app.market.ui.theme.PopupTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun DownloadingAppsScreen(
    viewModel: DownloadingAppsViewModel,
    onOpenDetail: (DownloadState) -> Unit,
    onBack: (() -> Unit)? = null,
    bottomPadding: Dp = 0.dp,
) {
    val states by viewModel.downloadStates.collectAsStateWithLifecycle()
    // 按下载顺序展示：进行中在前（下载 → 安装 → 等待确认），其后是排队、暂停、失败、已完成
    val entries = remember(states) { states.values.sortedWith(DownloadOrder) }
    val installedVersions = remember { mutableStateOf(emptyMap<String, Long>()) }
    LaunchedEffect(entries) {
        if (entries.isNotEmpty()) {
            installedVersions.value = viewModel.installedVersions(entries.map { it.packageName })
        }
    }
    val downloadText = stringResource(Res.string.download)
    var bulkCancelConfirmVisible by remember { mutableStateOf(false) }
    val moreActions: @Composable RowScope.() -> Unit = {
        BulkActionsMenu(
            onResumeAll = viewModel::resumeAll,
            onPauseAll = viewModel::pauseAll,
            onCancelAll = { bulkCancelConfirmVisible = true },
        )
    }

    val listContent: @Composable (
        Dp,
        Modifier,
        androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    ) -> Unit = { topPad, backdropModifier, nestedScroll ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = topPad)
                .then(backdropModifier),
        ) {
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.downloading_apps_empty),
                        style = MiuixTheme.textStyles.main,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                val sourceBadge = rememberDownloadSourceBadge()
                val sourceBranded = sourceBadge == DownloadSourceBadge.BRAND
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(nestedScroll),
                    contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding + PageVerticalPadding),
                ) {
                    itemsIndexed(entries, key = { _, state -> state.packageName }) { index, state ->
                        CardSegmentContainer(
                            isFirst = index == 0,
                            isLast = index == entries.lastIndex,
                            modifier = Modifier.animateItem(placementSpec = null),
                            // 与更新卡片同一套品牌底：Google Play 四色淡渐变，其余来源单色品牌底
                            containerBrush = if (sourceBranded) {
                                run {
                                    val base = CardDefaults.defaultColors().color
                                    state.source?.let { appSourceBrandBrush(it, base) }
                                }
                            } else {
                                null
                            },
                            containerColor = if (!sourceBranded) {
                                CardDefaults.defaultColors().color
                            } else {
                                run {
                                    val base = CardDefaults.defaultColors().color
                                    val src = state.source
                                    if (src != null && appSourceBrandBrush(src, base) == null) {
                                        lerp(base, appSourceBrandColor(src), 0.22f)
                                    } else {
                                        null
                                    }
                                }
                            },
                        ) {
                            DownloadingAppRow(
                                state = state,
                                actionText = downloadText,
                                isUpdate = installedVersions.value[state.packageName]
                                    ?.let { state.versionCode > it } == true,
                                onOpenDetail = { onOpenDetail(state) },
                                onResume = { viewModel.resume(state) },
                                onInstallDownloaded = viewModel::installDownloaded,
                                onCancel = viewModel::pause,
                                sourceLabelTag = if (sourceBadge == DownloadSourceBadge.PLAIN) {
                                    state.source?.let { appSourceLabel(it) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (onBack != null) {
        MarketScaffold(
            title = stringResource(Res.string.download_queue_title),
            onBack = onBack,
            actions = moreActions,
        ) { innerPadding, backdropModifier, scrollBehavior ->
            listContent(
                innerPadding.calculateTopPadding(),
                backdropModifier,
                scrollBehavior.nestedScrollConnection,
            )
        }
    } else {
        MainTabScaffold(
            title = stringResource(Res.string.download_queue_title),
            actions = moreActions,
        ) { topPadding, backdropModifier, scrollBehavior ->
            listContent(topPadding, backdropModifier, scrollBehavior.nestedScrollConnection)
        }
    }
    PopupTheme { WindowDialog(
        show = bulkCancelConfirmVisible,
        title = stringResource(Res.string.cancel_all),
        onDismissRequest = { bulkCancelConfirmVisible = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.cancel_all_message),
                color = MiuixTheme.colorScheme.onSurface,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                AppTextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(Res.string.cancel),
                    onClick = { bulkCancelConfirmVisible = false },
                )
                Spacer(Modifier.width(16.dp))
                AppTextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(Res.string.confirm),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        bulkCancelConfirmVisible = false
                        viewModel.removeAll()
                    },
                )
            }
        }
    } }
}

/** ⋮ 菜单定位：右缘与按钮右缘对齐、紧贴按钮下方弹出（去掉 miuix 默认的 12dp 偏移）。 */
private val MoreMenuPositionProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowBounds: IntRect,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
        popupMargin: IntRect,
        alignment: PopupPositionProvider.Align,
    ): IntOffset {
        val x = (windowBounds.right - popupContentSize.width + popupMargin.left).coerceAtLeast(windowBounds.left)
        val y = anchorBounds.bottom.coerceIn(
            windowBounds.top,
            (windowBounds.bottom - popupContentSize.height).coerceAtLeast(windowBounds.top),
        )
        return IntOffset(x, y)
    }

    override fun getMargins(): PaddingValues = PaddingValues(start = 12.dp)
}

/** 标题栏 ⋮：批量操作菜单，用 miuix 自带的下拉定位（弹在按钮正下方）。 */
@Composable
private fun BulkActionsMenu(
    onResumeAll: () -> Unit,
    onPauseAll: () -> Unit,
    onCancelAll: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    Box {
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.onGloballyPositioned { rowBounds = it.boundsInWindow() },
            backgroundColor = Color.Transparent,
            cornerRadius = 100.dp,
            minHeight = 32.dp,
            minWidth = 32.dp,
        ) {
            Icon(
                imageVector = MiuixIcons.More,
                contentDescription = stringResource(Res.string.more_options),
                tint = MiuixTheme.colorScheme.onSecondaryVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        // 统一走通用玻璃菜单壳（与其他菜单同一套代码，退场动画一致）
        AppGlassDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            anchorBounds = rowBounds,
            items = listOf(
                stringResource(Res.string.start_all) to onResumeAll,
                stringResource(Res.string.pause_all) to onPauseAll,
                stringResource(Res.string.cancel_all) to onCancelAll,
            ),
        )
    }
}

@Composable
private fun DownloadingAppRow(
    state: DownloadState,
    actionText: String,
    isUpdate: Boolean,
    onOpenDetail: () -> Unit,
    onResume: () -> Unit,
    onInstallDownloaded: (String) -> Unit,
    onCancel: (String) -> Unit,
    sourceLabelTag: String? = null,
) {
    // 与更新卡片同一套品牌水印：Google Play 逐字母四色（logo 做法），其余来源单色
    val sourceBadge = rememberDownloadSourceBadge()
    Box(modifier = Modifier.fillMaxWidth()) {
        val brandText = if (sourceBadge != DownloadSourceBadge.BRAND) {
            ""
        } else {
            state.source?.let { appSourceLabel(it) }.orEmpty()
        }
        if (brandText.isNotEmpty()) {
            val textMeasurer = rememberTextMeasurer()
            val brandColor = if (sourceBadge == DownloadSourceBadge.BRAND) {
                state.source?.let { appSourceBrandColor(it) } ?: AppMarketBrandColor
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            }
            val dark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f
            val letterColors = if (sourceBadge == DownloadSourceBadge.BRAND) {
                state.source?.let { appSourceLetterColors(it, dark) }
            } else {
                null
            }
            // 彩色（逐字母）比单色更容易被底色吃掉，透明度给得更高一档
            val brandAlpha = if (letterColors != null) 0.30f else 0.14f
            val brandTextLayout = remember(brandText, letterColors) {
                textMeasurer.measure(
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
                drawText(
                    textLayoutResult = brandTextLayout,
                    color = if (letterColors != null) Color.Unspecified else brandColor.copy(alpha = brandAlpha),
                    topLeft = Offset(
                        x = size.width - brandTextLayout.size.width - 20.dp.toPx(),
                        y = size.height - brandTextLayout.size.height - 4.dp.toPx(),
                    ),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDetail)
                .padding(16.dp),
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
                        // 自更新任务没有网络图标（icon 为空），统一用内置 AppMarket 启动图标；
                        // LSPosed 模块统一内置图标：仓库里那个字段是作者头像 / README 首图。
                        // 更新任务里来源有时记录的不是 LSPOSED，所以再看一眼图标是不是 GitHub
                        // 头像/附件图 —— 是的话同样换成本地图标。
                        url = when {
                            state.appId == SELF_UPDATE_APP_ID || state.icon.isBlank() -> SelfAppIconMarker
                            state.source == AppSource.LSPOSED ||
                                (state.installedVersionName.isNotBlank() && looksLikeLsposedRepoImage(state.icon)) ->
                                LsposedModuleIconMarker
                            else -> state.icon
                        },
                        contentDescription = appDisplayName(state.displayName, state.source),
                        size = 48.dp,
                    )
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = appDisplayName(state.displayName, state.source),
                                modifier = Modifier.weight(1f, fill = false),
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MiuixTheme.textStyles.headline1.copy(
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 22.sp,
                                ),
                            )
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
                                text = if (state.installedVersionName.isBlank()) {
                                    // 全新下载：没有旧版本可对比，直接显示要下载的版本
                                    state.versionName.ifBlank { "-" }
                                } else {
                                    stringResource(
                                        Res.string.version_transition,
                                        state.installedVersionName,
                                        state.versionName.ifBlank { "-" },
                                    )
                                },
                                modifier = Modifier.padding(start = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MiuixTheme.textStyles.body2.copy(lineHeight = 18.sp),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Text(
                            text = downloadSizeText(state),
                            maxLines = 1,
                            style = MiuixTheme.textStyles.body2.copy(lineHeight = 18.sp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                AppActionButton(
                    packageName = state.packageName,
                    actionText = actionText,
                    actionKind = if (isUpdate) AppActionKind.UPDATE else AppActionKind.INSTALL,
                    downloadState = state,
                    onAction = onResume,
                    onResumeDownload = onResume,
                    onInstallDownloaded = onInstallDownloaded,
                    onCancel = onCancel,
                    minHeight = 34.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (state.changeLog.isNotBlank()) {
                var expanded by remember { mutableStateOf(false) }
                // 只有单行显示被截断（内容比一行长）时才需要「展开」按钮，
                // 一行放得下的更新日志不该出现一个点了也没变化的箭头
                var overflows by remember(state.changeLog) { mutableStateOf(false) }
                val changeLog = remember(state.changeLog) { state.changeLog.toPlainStoreText() }
                // 与更新页同款：折叠态把换行压平成单行连续文字，首行不会只剩「近期更新:」这种小标题
                val collapsedChangeLog = remember(changeLog) {
                    changeLog.replace(WhitespaceRegex, " ").trim()
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                ) {
                    SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (expanded) changeLog else collapsedChangeLog,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = if (overflows || expanded) 44.dp else 0.dp),
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Clip,
                            onTextLayout = { result ->
                                if (!expanded) overflows = result.hasVisualOverflow || result.lineCount > 1
                            },
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                    if (overflows || expanded) {
                        Text(
                            text = if (expanded) "收起" else stringResource(Res.string.expand_change_log),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .clickable(interactionSource = null, indication = null) { expanded = !expanded },
                            color = MiuixTheme.colorScheme.primary,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }
        }
    }
}

/** 大小行：与更新卡片的「大小」同款式。 */
private fun downloadSizeText(state: DownloadState): String = com.app.market.ui.util.formatSize(state.apkSize)

private val PhaseRank = mapOf(
    DownloadPhase.DOWNLOADING to 0,
    DownloadPhase.INSTALLING to 1,
    DownloadPhase.AWAITING_USER_ACTION to 2,
    DownloadPhase.QUEUED to 3,
    DownloadPhase.PAUSED to 4,
    DownloadPhase.FAILED to 5,
    DownloadPhase.DOWNLOADED to 6,
)

@Composable
private fun statusText(state: DownloadState): String = when (state.phase) {
    DownloadPhase.QUEUED -> stringResource(Res.string.downloading_short)
    DownloadPhase.DOWNLOADING -> state.progress?.let { com.app.market.domain.model.download.formatPercent(it) }
        ?: stringResource(Res.string.downloading_short)
    DownloadPhase.PAUSED -> state.progress
        ?.let { "${stringResource(Res.string.download_paused)} · ${com.app.market.domain.model.download.formatPercent(it)}" }
        ?: stringResource(Res.string.download_paused)
    DownloadPhase.INSTALLING -> stringResource(Res.string.installing_short)
    DownloadPhase.AWAITING_USER_ACTION -> stringResource(Res.string.installing_short)
    DownloadPhase.DOWNLOADED -> if (state.isComplete) {
        stringResource(Res.string.download_ready)
    } else {
        stringResource(Res.string.download_finished)
    }

    DownloadPhase.FAILED -> state.errorMessage.ifBlank { stringResource(Res.string.download_failed) }
}

/** 下载顺序：进行中的排前面，已结束的沉底；同相位按提交顺序（列表即插入顺序）保持稳定。 */
private val DownloadOrder = compareBy<DownloadState> { PhaseRank[it.phase] ?: Int.MAX_VALUE }

/** LSPosed 模块从仓库拿到的“图标”往往是 GitHub 头像或 README 里的附件图。 */
private fun looksLikeLsposedRepoImage(url: String): Boolean =
    url.contains("avatars.githubusercontent.com") ||
        url.contains("raw.githubusercontent.com") ||
        url.contains("/user-attachments/")
