package com.app.market.ui.component

import com.app.market.ui.util.appSourceLetterColors
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.luminance
import com.app.market.domain.model.market.AppSource
import com.app.market.ui.util.appSourceLabelBackground
import com.app.market.ui.util.appSourceLabelBrush
import com.app.market.ui.util.toPlainStoreText
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay
import com.app.market.ui.theme.PopupTheme
import top.yukonga.miuix.kmp.glass.GlassDropdownPopup
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.isDownloadBlocked
import com.app.market.resources.Res
import com.app.market.resources.collapse_change_log
import com.app.market.resources.expand_change_log
import com.app.market.resources.ic_coolapk
import com.app.market.resources.ic_launcher
import com.app.market.resources.ic_lsposed
import com.app.market.resources.ignore_once
import com.app.market.resources.ignore_permanent
import com.app.market.resources.no_change_log
import com.app.market.resources.search_ad_tag
import com.app.market.resources.search_quickapp_tag
import com.app.market.ui.model.AppActionKind
import com.app.market.ui.util.allowColorSampling
import com.app.market.ui.util.appDisplayName
import com.app.market.ui.util.appSourceLabel
import com.app.market.ui.util.dominantImageColor
import com.app.market.ui.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import coil3.Image as CoilImage

@Composable
fun AppAsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
    onDominantColor: ((Color) -> Unit)? = null,
    onLoaded: ((CoilImage) -> Unit)? = null,
    placeholderMemoryCacheUrl: String? = null,
    showLoadingIndicator: Boolean = false,
    loadingIndicatorAlignment: Alignment = Alignment.Center,
    loadingIndicatorColor: Color = Color.Gray,
    errorFallback: String? = null,
) {
    val platformContext = LocalPlatformContext.current
    val model = remember(url, platformContext, onDominantColor != null, placeholderMemoryCacheUrl) {
        if (onDominantColor == null && placeholderMemoryCacheUrl == null) {
            url
        } else {
            ImageRequest.Builder(platformContext)
                .data(url)
                .apply {
                    if (onDominantColor != null) allowColorSampling()
                    if (placeholderMemoryCacheUrl != null) placeholderMemoryCacheKey(placeholderMemoryCacheUrl)
                }
                .build()
        }
    }
    var loading by remember(url) { mutableStateOf(true) }
    var loadFailed by remember(url) { mutableStateOf(false) }
    // onSuccess 在主线程，而取色要做整图位图拷贝
    var sampledImage by remember(url) { mutableStateOf<CoilImage?>(null) }
    val currentOnDominantColor by rememberUpdatedState(onDominantColor)
    val currentOnLoaded by rememberUpdatedState(onLoaded)
    LaunchedEffect(sampledImage) {
        val source = sampledImage ?: return@LaunchedEffect
        val color = withContext(Dispatchers.Default) { dominantImageColor(source) }
        color?.let { currentOnDominantColor?.invoke(it) }
    }
    val image = @Composable { imageModifier: Modifier ->
        if (loadFailed && errorFallback != null) {
            // 加载失败兜底占位图（如 LSPosed 图标获取失败用本地图标）
            AsyncImage(
                model = errorFallback,
                contentDescription = contentDescription,
                modifier = imageModifier,
                contentScale = contentScale,
                alignment = alignment,
                colorFilter = colorFilter,
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = imageModifier,
                contentScale = contentScale,
                alignment = alignment,
                colorFilter = colorFilter,
                onSuccess = { state ->
                    if (onDominantColor != null) sampledImage = state.result.image
                    currentOnLoaded?.invoke(state.result.image)
                    loading = false
                },
                onError = { state ->
                    loading = false
                    loadFailed = errorFallback != null
                    // 图标/图片加载失败必须留痕（酷安图床 567 等问题靠这条日志定位）
                    com.app.market.domain.diagnostic.AppLog.log(
                        "ImageLoad failed: $url -> ${state.result.throwable}",
                    )
                },
            )
        }
    }
    if (showLoadingIndicator) {
        Box(modifier = modifier, contentAlignment = loadingIndicatorAlignment) {
            image(Modifier.matchParentSize())
            if (loading) InfiniteProgressIndicator(color = loadingIndicatorColor)
        }
    } else {
        image(modifier)
    }
}

@Composable
fun AppIcon(
    url: String,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    /** 顶栏下载入口用：图标放在圆形进度环里，按正圆裁剪（默认仍是 squircle 应用图标样式） */
    circular: Boolean = false,
) {
    val iconModifier = if (circular) {
        modifier
            .size(size)
            .border(0.2.dp, Color.Gray, CircleShape)
            .clip(CircleShape)
    } else {
        modifier
            .size(size)
            .squircleBorder(0.2.dp, Color.Gray, size * 0.25f)
            .squircleClip(size * 0.25f)
    }
    if (url.isBlank()) {
        // 无网络图标（如 AppMarket 自更新行）：用本地启动图标兜底，避免空白
        Image(
            painter = painterResource(Res.drawable.ic_launcher),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = iconModifier,
        )
    } else if (url.contains(CoolapkSelfIconMarker)) {
        // 酷安自身的网络图标在部分设备无法解码（图床按 Accept 下发的扩展 WebP），
        // 直接用打包进应用的本地酷安图标，不走网络
        Image(
            painter = painterResource(Res.drawable.ic_coolapk),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = iconModifier,
        )
    } else if (url == LsposedModuleIconMarker) {
        // LSPosed 模块在仓库里没有真正的应用图标：图标字段要么是作者头像、要么是
        // README 里第一张图。统一换成内置的 LSPosed 图标，下载/安装/更新历史一致。
        Image(
            painter = painterResource(Res.drawable.ic_lsposed),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = iconModifier,
        )
    } else if (url == SelfAppIconMarker) {
        // AppMarket 自身的任务（自更新、自身历史记录）没有网络图标，用内置启动图标
        Image(
            painter = painterResource(Res.drawable.ic_launcher),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = iconModifier,
        )
    } else {
        AppAsyncImage(
            url = url,
            contentDescription = contentDescription,
            modifier = iconModifier,
            errorFallback = "file:///storage/emulated/0/Download/launcher.png",
        )
    }
}

/** 酷安（com.coolapk.market）自身图标的图床资源标识，命中即用本地内置图标。 */
private const val CoolapkSelfIconMarker = "icon_10401327_1611306222"

/** LSPosed 模块统一图标标记：命中即用内置 LSPosed 图标，不显示作者头像/README 首图。 */
const val LsposedModuleIconMarker = "lsposed://module"

/** AppMarket 自身（自更新任务、自身历史记录）的图标标记：命中即用内置启动图标。 */
const val SelfAppIconMarker = "appmarket://self"

@Composable
fun RelatedAppsList(
    apps: List<MarketAppInfo>,
    onOpenApp: (MarketAppInfo) -> Unit,
    title: (MarketAppInfo) -> String = { app -> app.displayName },
    summary: (MarketAppInfo) -> String = { app ->
        app.publisherName.ifBlank { app.packageName }
    },
    action: @Composable (MarketAppInfo) -> Unit,
) {
    RelatedAppsCard(
        apps = apps,
        onOpenApp = onOpenApp,
        title = title,
        summary = summary,
        action = action,
    )
}

@Composable
fun RelatedAppsCard(
    apps: List<MarketAppInfo>,
    onOpenApp: (MarketAppInfo) -> Unit,
    title: (MarketAppInfo) -> String = { app -> app.displayName },
    summary: (MarketAppInfo) -> String,
    colors: CardColors = CardColors(
        MiuixTheme.colorScheme.surface,
        MiuixTheme.colorScheme.onSurface
    ),
    action: @Composable (MarketAppInfo) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        colors = colors,
        insideMargin = PaddingValues(0.dp),
    ) {
        Column {
            apps.forEach { app ->
                RelatedAppRow(
                    app = app,
                    title = title(app),
                    summary = summary(app),
                    onOpenApp = { onOpenApp(app) },
                    action = { action(app) },
                )
            }
        }
    }
}

@Composable
fun RelatedAppRow(
    app: MarketAppInfo,
    title: String? = null,
    summary: String,
    onOpenApp: () -> Unit,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenApp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            url = app.icon,
            contentDescription = appDisplayName(app),
            size = 48.dp,
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .widthIn(max = 195.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = title?.let { appDisplayName(it, app.source) } ?: appDisplayName(app),
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        action()
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    // Default fills width only (wrap height); pass Modifier.fillParentMaxSize() from a LazyColumn
    // item to truly center the spinner in the viewport instead of pinning it to the top.
    Box(modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
        InfiniteProgressIndicator()
    }
}

private const val CollapsedChangeLogMaxLines = 1

private fun formatRepoStars(value: Long): String = when {
    value >= 100_000 -> "${value / 1000}k"
    value >= 1000 -> {
        val tenth = value / 100
        val text = if (tenth % 10 == 0L) "${tenth / 10}" else "${tenth / 10}.${tenth % 10}"
        "${text}k"
    }
    else -> value.toString()
}

@Composable
private fun LsposedModuleRow(
    app: MarketAppInfo,
    actionKind: AppActionKind,
    modifier: Modifier = Modifier,
    onOpenDetail: () -> Unit,
) {
    // 仓库列表对齐 LSPosed Manager：去掉图标，只要 标题 / 包名 / 简介 / 星标数 / 需更新 / 更新时间
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onOpenDetail,
        onLongPress = null,
        showIndication = true,
        insideMargin = PaddingValues(all = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = appDisplayName(app),
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.summaryText.isNotBlank()) {
                Text(
                    text = app.summaryText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MiuixIcons.Favorites,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = app.downloadCount.toString(),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (actionKind == AppActionKind.UPDATE) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "需要更新",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = MiuixIcons.Recent,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = app.releaseTimeText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
fun AppRow(
    app: MarketAppInfo,
    modifier: Modifier = Modifier,
    actionText: String,
    actionKind: AppActionKind,
    downloadState: DownloadState?,
    onOpenDetail: () -> Unit,
    onAction: () -> Unit,
    onResumeDownload: () -> Unit = onAction,
    onInstallDownloaded: (String) -> Unit,
    onCancel: (String) -> Unit,
    onIgnoreOnce: (() -> Unit)? = null,
    onIgnorePermanent: (() -> Unit)? = null,
    showUpdateLog: Boolean = false,
    showSearchTags: Boolean = false,
    /** 单源没有歧义，只在多源时标来源。 */
    showSourceTag: Boolean = false,
    initialChangeLogExpanded: Boolean = false,
    grouped: Boolean = false,
    preparingDownload: Boolean = false,
    onCancelPreparing: () -> Unit = {},
) {
    // 标题 / 包名 / 简介 / 星标数 / 是否可更新 / 更新时间（对齐 LSPosed Manager 的仓库列表）
    if (app.source == AppSource.LSPOSED) {
        LsposedModuleRow(
            app = app,
            actionKind = actionKind,
            modifier = modifier,
            onOpenDetail = onOpenDetail,
        )
        return
    }
    val hasMenu = onIgnoreOnce != null && onIgnorePermanent != null
    val showMenu = remember { mutableStateOf(false) }
    var menuMounted by remember { mutableStateOf(false) }
    val onRowLongPress = if (hasMenu) {
        {
            menuMounted = true
            showMenu.value = true
        }
    } else null
    var changeLogExpanded by rememberSaveable(app.packageName, app.versionCode) {
        mutableStateOf(
            initialChangeLogExpanded
        )
    }
    val noChangeLogText = stringResource(Res.string.no_change_log)
    val versionSizeText = remember(app) { appVersionSizeText(app) }
    val spSize = with(LocalDensity.current) { 1.sp.toDp() }
    val content: @Composable () -> Unit = {
        var menuAnchorBounds by remember { mutableStateOf(Rect.Zero) }
        Box(
            modifier = (if (showUpdateLog && !changeLogExpanded) {
                Modifier.height(58 * spSize)
            } else {
                Modifier
            }).onGloballyPositioned { menuAnchorBounds = it.boundsInWindow() },
        ) {
            if (hasMenu && menuMounted) {
                IgnoreUpdateMenu(
                    show = showMenu.value,
                    onDismiss = { showMenu.value = false },
                    anchorBounds = menuAnchorBounds,
                    onIgnoreOnce = {
                        showMenu.value = false
                        onIgnoreOnce.invoke()
                    },
                    onIgnorePermanent = {
                        showMenu.value = false
                        onIgnorePermanent.invoke()
                    },
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (grouped) 8.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    url = app.icon,
                    contentDescription = appDisplayName(app),
                    size = if (grouped) 48.dp else 52 * spSize,
                    modifier = Modifier
                        .padding(start = if (grouped) 0.dp else 3 * spSize),
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (app.source == AppSource.GITHUB) {
                                "${app.publisherName}/${app.displayName}"
                            } else {
                                appDisplayName(app)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MiuixTheme.textStyles.headline1,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (showSourceTag) {
                            SearchTag(
                                text = appSourceLabel(app.source),
                                modifier = Modifier.padding(start = 6.dp),
                                source = app.source,
                            )
                        }
                        if (showSearchTags && app.isAd) {
                            SearchTag(
                                text = stringResource(Res.string.search_ad_tag),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        if (showSearchTags && app.type.equals("quickGame", ignoreCase = true)) {
                            SearchTag(
                                text = stringResource(Res.string.search_quickapp_tag),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    if (showUpdateLog) {
                        Text(
                            versionSizeText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        UpdateChangeLogText(
                            text = app.changeLog.toPlainStoreText().ifBlank { noChangeLogText },
                            expanded = changeLogExpanded,
                            onExpandedChange = { changeLogExpanded = it },
                            onOpenDetail = onOpenDetail,
                            onLongPress = onRowLongPress,
                        )
                    } else if (app.source == AppSource.GITHUB) {
                        Text(
                            app.summaryText.ifBlank { app.packageName },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = MiuixIcons.Favorites,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = formatRepoStars(app.downloadCount),
                                maxLines = 1,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            if (app.releaseTimeText.isNotBlank()) {
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    imageVector = MiuixIcons.Recent,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = app.releaseTimeText,
                                    maxLines = 1,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    } else {
                        Text(
                            app.publisherName.ifBlank { app.packageName },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            versionSizeText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                AppActionButton(
                    packageName = downloadState?.packageName ?: app.packageName,
                    actionText = actionText,
                    actionKind = actionKind,
                    downloadState = downloadState,
                    preparingDownload = preparingDownload && downloadState == null,
                    onCancelPreparing = onCancelPreparing,
                    enabled = actionKind == AppActionKind.OPEN || !app.isDownloadBlocked(),
                    onAction = onAction,
                    onResumeDownload = onResumeDownload,
                    onInstallDownloaded = onInstallDownloaded,
                    onCancel = onCancel,
                    minHeight = 34.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
    if (grouped) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpenDetail,
                    onLongClick = onRowLongPress,
                )
                .padding(13.dp),
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            onClick = onOpenDetail,
            onLongPress = onRowLongPress,
            showIndication = true,
            holdDownState = showMenu.value,
            insideMargin = PaddingValues(all = 13.dp),
        ) {
            content()
        }
    }
}

/** Small muted tag shown after the app name for search-result markers (promoted / quick app). */
@Composable
private fun SearchTag(
    text: String,
    modifier: Modifier = Modifier,
    source: AppSource? = null,
) {
    // Google Play 的标签逐字母上色（Google logo 的配色：G蓝 / o红 / o黄 / g蓝 / l绿 / e红），
    // 配同色系加实的底；其余来源保持原有灰底样式。
    // 深浅主题各用一套颜色：原色的黄/绿压在浅底上会糊成一团，必须换深色版才看得清。
    val dark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f
    val letterColors = source?.let { appSourceLetterColors(it, dark) }
    val labelBackground = source?.let { appSourceLabelBackground(it, dark) }
    val background = labelBackground
        ?: MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.15f)
    if (letterColors != null) {
        // Google Play：四色 Play 三角（蓝/红/黄/蓝）+ 文字，贴官方 logo 样式
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .squircleSurface(color = background, cornerRadius = 5.dp)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(width = 9.dp, height = 11.dp)) {
                val w = size.width
                val h = size.height
                val ax = 0f
                val ay = h / 2f
                fun tri(p1y: Float, p2y: Float, color: Color) {
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(ax, ay)
                            lineTo(w, p1y)
                            lineTo(w, p2y)
                            close()
                        },
                        color,
                    )
                }
                tri(0f, h / 3f, Color(0xFF4285F4))
                tri(h / 3f, h / 2f, Color(0xFFEA4335))
                tri(h / 2f, h * 2f / 3f, Color(0xFFFBBC05))
                tri(h * 2f / 3f, h, Color(0xFF4285F4))
            }
            Spacer(Modifier.width(3.dp))
            Text(
                text = text,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    } else {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote2,
            maxLines = 1,
            softWrap = false,
            modifier = modifier
                .squircleSurface(color = background, cornerRadius = 5.dp)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun UpdateChangeLogText(
    text: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenDetail: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    val textMeasurer = rememberTextMeasurer()
    val style = MiuixTheme.textStyles.body2
    val bodyColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val actionColor = MiuixTheme.colorScheme.primary
    val expandText = stringResource(Res.string.expand_change_log)
    val collapseText = stringResource(Res.string.collapse_change_log)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val hasMultiLinesOfText = remember(
            text,
            widthPx,
            style,
            bodyColor,
            textMeasurer,
        ) {
            widthPx > 0 && textMeasurer.measure(
                AnnotatedString(text, spanStyle = SpanStyle(color = bodyColor)),
                style = style,
                maxLines = CollapsedChangeLogMaxLines,
                constraints = Constraints(maxWidth = widthPx),
            ).hasVisualOverflow
        }

        when {
            !hasMultiLinesOfText -> {
                ChangeLogBodyText(
                    text = text,
                    style = style,
                    color = bodyColor,
                    maxLines = Int.MAX_VALUE,
                    onOpenDetail = onOpenDetail,
                    onLongPress = onLongPress,
                )
            }

            expanded -> {
                Column(Modifier.fillMaxWidth()) {
                    ChangeLogBodyText(
                        text = text,
                        style = style,
                        color = bodyColor,
                        maxLines = Int.MAX_VALUE,
                        onOpenDetail = onOpenDetail,
                        onLongPress = onLongPress,
                    )
                    ChangeLogActionText(
                        text = collapseText,
                        style = style,
                        color = actionColor,
                        onClick = { onExpandedChange(false) },
                        onLongPress = onLongPress,
                        modifier = Modifier.align(Alignment.Start),
                    )
                }
            }

            else -> {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChangeLogBodyText(
                        text = text,
                        style = style,
                        color = bodyColor,
                        maxLines = CollapsedChangeLogMaxLines,
                        onOpenDetail = onOpenDetail,
                        onLongPress = onLongPress,
                        modifier = Modifier.weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(24.dp).height(22.dp).background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MiuixTheme.colorScheme.surfaceVariant
                                    ),
                                ),
                            ),
                        )
                        ChangeLogActionText(
                            text = expandText,
                            style = style,
                            color = actionColor,
                            onClick = { onExpandedChange(true) },
                            onLongPress = onLongPress,
                            modifier = Modifier.background(MiuixTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeLogBodyText(
    text: String,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    onOpenDetail: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = if (maxLines == CollapsedChangeLogMaxLines) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier.pointerInput(text, onOpenDetail, onLongPress) {
            detectTapGestures(
                onLongPress = { onLongPress?.invoke() },
                onTap = { onOpenDetail() },
            )
        },
    )
}

@Composable
private fun ChangeLogActionText(
    text: String,
    style: TextStyle,
    color: Color,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = style.copy(color = color),
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier.pointerInput(text, onClick, onLongPress) {
            detectTapGestures(
                onLongPress = { onLongPress?.invoke() },
                onTap = { onClick() },
            )
        },
    )
}

private fun appVersionSizeText(app: MarketAppInfo) = buildAnnotatedString {
    val fullSize = formatSize(app.apkSize)
    val deltaSize = formatSize(app.deltaSize)
    // 体积缺失时也要把版本号亮出来（Google 网页通道的体积可能没补上）
    if (fullSize.isBlank()) {
        if (app.versionName.isNotBlank()) append(app.versionName)
        return@buildAnnotatedString
    }
    if (app.versionName.isNotBlank()) {
        append(app.versionName)
        append("  ")
    }
    if (deltaSize.isNotBlank() && app.deltaSize > 0L && app.deltaSize < app.apkSize) {
        append(deltaSize)
        append("  ")
        val start = length
        append(fullSize)
        addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, length)
    } else {
        append(fullSize)
    }
}

/** OS4 玻璃下拉菜单壳：窗口级承载（无全局变灰蒙层）+ GlassDropdownPopup 弹出动画。 */
@Composable
internal fun AppGlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect,
    items: List<Pair<String, () -> Unit>>,
) {
    val mounted = remember { mutableStateOf(false) }
    val anchor = rememberGlassPopupAnchor()
    LaunchedEffect(expanded) {
        if (expanded) {
            mounted.value = true
        } else if (mounted.value) {
            delay(700)
            mounted.value = false
        }
    }
    if (mounted.value) {
        PopupTheme {
        MiuixPopupUtils.PopupLayout(
            visible = mounted,
            enableWindowDim = false,
            enableBackHandler = true,
            enterTransition = EnterTransition.None,
            exitTransition = ExitTransition.None,
            renderInRootScaffold = false,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                GlassDropdownPopup(
                    show = expanded,
                    onDismissRequest = onDismissRequest,
                    anchorBounds = anchorBounds,
                    backdrop = null,
                    anchor = anchor,
                ) {
                    items.forEachIndexed { index, (text, action) ->
                        GlassPopupItem(
                            text = text,
                            onClick = {
                                onDismissRequest()
                                action()
                            },
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun IgnoreUpdateMenu(
    show: Boolean,
    onDismiss: () -> Unit,
    onIgnoreOnce: () -> Unit,
    onIgnorePermanent: () -> Unit,
    anchorBounds: Rect,
) {
    val onceText = stringResource(Res.string.ignore_once)
    val permanentText = stringResource(Res.string.ignore_permanent)
    AppGlassDropdownMenu(
        expanded = show,
        onDismissRequest = onDismiss,
        anchorBounds = anchorBounds,
        items = listOf(
            onceText to onIgnoreOnce,
            permanentText to onIgnorePermanent,
        ),
    )
}