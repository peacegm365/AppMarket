package com.app.market.ui.screen

import com.app.market.ui.util.toPlainStoreText
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.foundation.border
import top.yukonga.miuix.kmp.icon.extended.Back
import com.app.market.ui.component.AppCompactButton
import top.yukonga.miuix.kmp.icon.extended.Link
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import com.app.market.ui.component.AppGlassDropdownMenu
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.model.market.AppComment
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.AppScreenshot
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.AppVideo
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.MarketLink
import com.app.market.domain.model.market.ScreenshotOrientation
import com.app.market.domain.model.market.isDownloadBlocked
import com.app.market.domain.model.market.isReservation
import com.app.market.platform.ImageSaveResult
import com.app.market.platform.ShareResult
import com.app.market.platform.UiPlatform
import com.app.market.resources.download
import com.app.market.resources.save_as
import com.app.market.domain.repository.TranslationRepository
import com.app.market.ui.component.translateIconVector
import com.app.market.ui.util.detailTranslationSource
import com.app.market.ui.util.htmlToPlainText
import com.app.market.ui.util.translateHtmlInPlace
import com.app.market.ui.util.translateMarkdownInPlace
import com.app.market.resources.Res
import com.app.market.resources.age_rating
import com.app.market.resources.app_detail
import com.app.market.resources.app_info
import com.app.market.resources.app_size
import com.app.market.resources.app_update_time
import com.app.market.resources.app_version_name
import com.app.market.resources.app_version_code
import com.app.market.resources.cancel
import com.app.market.resources.cancel_download
import com.app.market.resources.confirm
import com.app.market.resources.manual_pick_version
import com.app.market.resources.manual_update_version_code
import com.app.market.resources.no_historical_versions
import com.app.market.resources.retry
import com.app.market.resources.comments_and_scores
import com.app.market.resources.date_full
import com.app.market.resources.download_count
import com.app.market.resources.historical_versions
import com.app.market.resources.historical_versions_summary
import com.app.market.resources.ic_star
import com.app.market.resources.ic_star_fill
import com.app.market.resources.image_save_failed
import com.app.market.resources.image_save_unsupported
import com.app.market.resources.image_saved
import com.app.market.resources.introduction
import com.app.market.resources.more_options
import com.app.market.resources.no_other_app_store
import com.app.market.resources.num_comments
import com.app.market.resources.open
import com.app.market.resources.open_in_other_app_store
import com.app.market.resources.open_link_failed
import com.app.market.resources.package_name
import com.app.market.resources.preview
import com.app.market.resources.privacy_policy
import com.app.market.resources.registration_num
import com.app.market.resources.reinstall
import com.app.market.resources.reserve
import com.app.market.resources.save_image
import com.app.market.resources.share
import com.app.market.resources.share_failed
import com.app.market.resources.share_link_copied
import com.app.market.resources.tap_to_open
import com.app.market.resources.update
import com.app.market.resources.video_play
import com.app.market.resources.view_more
import com.app.market.resources.view_offer
import com.app.market.ui.widget.DropdownWithStartMarginProvider
import com.app.market.ui.component.AppActionButton
import com.app.market.ui.component.ProgressButtonWidth
import com.app.market.ui.component.AppAsyncImage
import com.app.market.ui.component.AppButton
import com.app.market.ui.component.AppTextButton
import com.app.market.ui.component.AppIcon
import com.app.market.ui.component.AppVideoViewer
import com.app.market.ui.component.CollapsibleMarketScaffold
import com.app.market.ui.component.LoadingBox
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.ui.model.AppActionKind
import com.app.market.ui.model.actionKind
import com.app.market.ui.util.allowColorSampling
import com.app.market.ui.util.animatedRect
import com.app.market.ui.util.appDisplayName
import com.app.market.ui.util.formatCount
import com.app.market.ui.util.formatSize
import com.app.market.ui.util.installActionText
import com.app.market.ui.util.localDateOf
import com.app.market.ui.util.splitAppDisplayName
import com.app.market.ui.util.topRegionLuminance
import com.app.market.viewmodel.AppDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.number
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.app.market.ui.theme.PopupTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val PREVIEW_ITEM_HEIGHT = 200.dp
private const val PORTRAIT_SCREENSHOT_RATIO = 5f / 9f
private const val SCREENSHOT_RATIO_TIMEOUT_MS = 5_000L
private const val SCREENSHOT_RATIO_SAMPLE_PX = 256
private const val SCREENSHOT_RATIO_CONCURRENCY = 3

private const val SCREENSHOT_VIEWER_ANIM_MS = 400
private const val SCREENSHOT_CORNER_DP = 16f
private const val LANDSCAPE_SCREENSHOT_RATIO = 16f / 9f

private const val PROMOTION_EXPANDED_RATIO = 9f / 16f

// miuix sheet 把手区 24dp + 空标题行 18dp

@Composable
fun AppDetailScreen(
    viewModel: AppDetailViewModel,
    appId: Long,
    packageName: String,
    externalQuery: String? = null,
    source: AppSource = AppSource.XIAOMI,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onOpenHistory: (MarketAppInfo) -> Unit,
    onBack: () -> Unit,
) {
    val uiPlatform = koinInject<UiPlatform>()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(appId, packageName, externalQuery, source) {
        viewModel.load(appId, packageName, externalQuery, source)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadStates = viewModel.downloadStates.collectAsStateWithLifecycle()

    val installText = installActionText()
    val updateText = stringResource(Res.string.update)
    val openText = stringResource(Res.string.open)
    val reserveText = stringResource(Res.string.reserve)
    val current = state.detail
    val currentActionKind = state.actionKind
    val currentPackageName = current?.app?.packageName
    // 进度每变 1% 就是新值：只传 State，顶层仅订阅离散布尔，读取下沉到按钮与菜单
    val currentDownloadState = remember(currentPackageName) {
        derivedStateOf { currentPackageName?.let { downloadStates.value[it] } }
    }
    val canRedownload = current != null && currentActionKind == AppActionKind.OPEN
    val canCancelDownload by remember(currentDownloadState) {
        derivedStateOf { currentDownloadState.value?.isComplete == false }
    }
    val actionKind = currentActionKind ?: current?.app?.actionKind()
    val actionText = when (actionKind) {
        AppActionKind.INSTALL -> installText
        AppActionKind.UPDATE -> updateText
        AppActionKind.OPEN -> openText
        AppActionKind.RESERVE -> reserveText
        null -> ""
    }
    val collapsedTitle = current?.app
        ?.let { appDisplayName(it) }
        ?: stringResource(Res.string.app_detail)

    val imageSaveScope = rememberCoroutineScope()
    // ---- 详情翻译：右上角按钮 → 弹层展示译文（不破坏原有渲染结构） ----
    val translations = koinInject<TranslationRepository>()
    val translationScope = rememberCoroutineScope()
    var translation by remember { mutableStateOf(DetailTranslationState()) }
    var detailTab by rememberSaveable(appId) { mutableIntStateOf(0) }
    val moduleReleases by viewModel.moduleReleases.collectAsStateWithLifecycle()
    // 浏览器插件式整页翻译：按当前页签把正文换成译文，再点一次恢复原文
    // 「已翻译」只认当前页签真实存在的译文：失败/没翻过/换页签都不算
    val hasTranslationForTab = if (detailTab == 1) {
        translation.latest.isNotBlank() || translation.logs.isNotEmpty()
    } else {
        translation.readme.isNotBlank() || translation.readmeHtml.isNotBlank()
    }
    val toggleTranslation: () -> Unit = {
        if (!translation.loading) if (hasTranslationForTab) {
            translation = DetailTranslationState()
        } else {
            val detail = current
            if (detail == null) {
                translation = DetailTranslationState(error = "还没有可翻译的内容")
            } else {
                translation = DetailTranslationState(active = true, loading = true)
                translationScope.launch {
                    runCatching {
                        if (detailTab == 1) {
                            // 更新日志是 Markdown：原地翻译，代码块/图片/链接原样保留
                            if (detail.changeLog.isNotBlank()) {
                                translation = translation.copy(
                                    latest = translateMarkdownInPlace(detail.changeLog) { translations.translate(it) },
                                )
                            }
                            val logs = linkedMapOf<String, String>()
                            moduleReleases.take(6).forEach { release ->
                                if (release.changeLog.isNotBlank()) {
                                    logs[release.tagName.ifBlank { release.name }] =
                                        translateMarkdownInPlace(release.changeLog) { translations.translate(it) }
                                    translation = translation.copy(logs = logs.toMap())
                                }
                            }
                        } else if (detail.introductionHtml.isNotBlank() &&
                            detail.app.source in AppSource.RepositorySources
                        ) {
                            // 仓库 README 是 HTML：原地翻译，图片/蓝色链接/排版原样保留
                            translation = translation.copy(
                                readmeHtml = translateHtmlInPlace(detail.introductionHtml) { translations.translate(it) },
                            )
                        } else {
                            // 只翻页面上那一块「应用介绍」：不要拼 brief（商店的一句话简介，
                            // 有时是另一段无关文案）和 changeLog，否则会把没显示在页面上的文字也翻出来
                            val raw = when {
                                detail.introductionHtml.isNotBlank() -> detail.introductionHtml
                                detail.introduction.isNotBlank() -> detail.introduction
                                else -> detail.brief
                            }
                            val src = htmlToPlainText(raw)
                            if (src.isNotBlank()) {
                                translation = translation.copy(readme = translations.translate(src))
                            }
                        }
                    }.onFailure { error ->
                        // 失败要留下可见痕迹：以前只写 error 没人显示，界面停在「已翻译」骗人
                        translation = DetailTranslationState(error = error.message ?: "翻译失败")
                        uiPlatform.showToast(error.message ?: "翻译失败")
                    }
                    translation = translation.copy(loading = false)
                }
            }
        }
    }
    // 未翻译 / 翻译中 / 已翻译 / 翻译失败：图标下一行小字（跟随当前页签的真实译文状态）
    val translationLabel = when {
        translation.loading -> "翻译中"
        hasTranslationForTab -> "已翻译"
        translation.error.isNotBlank() -> "翻译失败"
        else -> "翻译"
    }
    val translationButton: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { toggleTranslation() }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val iconWhite = if (darkTheme) Color.White else MiuixTheme.colorScheme.onSurface
            Icon(
                imageVector = remember(darkTheme, iconWhite) { translateIconVector(iconWhite) },
                contentDescription = translationLabel,
                tint = Color.Unspecified,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = translationLabel,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 9.sp, lineHeight = 10.sp),
                color = if (hasTranslationForTab || translation.loading) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                maxLines = 1,
            )
        }
    }
    val savedText = stringResource(Res.string.image_saved)
    val saveFailedText = stringResource(Res.string.image_save_failed)
    val saveUnsupportedText = stringResource(Res.string.image_save_unsupported)
    val noOtherAppStoreText = stringResource(Res.string.no_other_app_store)
    val canOpenOtherAppStore = uiPlatform.packageInstallationSupported
    val openInOtherAppStore: (String) -> Unit = { targetPackageName ->
        if (!uiPlatform.openInOtherAppStore(targetPackageName)) {
            uiPlatform.showToast(noOtherAppStoreText)
        }
    }
    val saveImage: (String, String) -> Unit = { url, fileName ->
        imageSaveScope.launch {
            val message = when (uiPlatform.saveImageToPictures(url, fileName)) {
                ImageSaveResult.Saved -> savedText
                ImageSaveResult.Failed -> saveFailedText
                ImageSaveResult.Unsupported -> saveUnsupportedText
            }
            uiPlatform.showToast(message)
        }
    }
    val shareCopiedText = stringResource(Res.string.share_link_copied)
    val shareFailedText = stringResource(Res.string.share_failed)
    val shareApp: () -> Unit = {
        current?.app?.let { app ->
            // 仅 TapTap 需要站内 id 才能还原，其余来源只带包名与来源
            val link = MarketLink(app.packageName, app.source, app.appId.takeIf { app.source == AppSource.TAPTAP } ?: 0L)
            when (uiPlatform.shareText("$collapsedTitle\n${link.toUnifiedUri()}")) {
                ShareResult.Shared -> Unit
                ShareResult.Copied -> uiPlatform.showToast(shareCopiedText)
                ShareResult.Failed, ShareResult.Unsupported -> uiPlatform.showToast(shareFailedText)
            }
        }
    }
    val phase = when {
        current != null -> DetailPhase.Content
        state.errorMessage.isNotEmpty() -> DetailPhase.Error
        else -> DetailPhase.Loading
    }
    // 预取截图真实宽高比；只要比例，故限解码尺寸与并发
    val platformContext = LocalPlatformContext.current
    val screenshotRatios = remember(current?.app?.appId) { mutableStateMapOf<String, Float>() }
    LaunchedEffect(current?.screenshots) {
        val screenshots = current?.screenshots ?: return@LaunchedEffect
        val imageLoader = SingletonImageLoader.get(platformContext)
        val slots = Semaphore(SCREENSHOT_RATIO_CONCURRENCY)
        for (shot in screenshots) {
            if (screenshotRatios.containsKey(shot.url)) continue
            launch {
                val image = slots.withPermit {
                    withTimeoutOrNull(SCREENSHOT_RATIO_TIMEOUT_MS.milliseconds) {
                        imageLoader.execute(
                            ImageRequest.Builder(platformContext)
                                .data(shot.url)
                                .size(SCREENSHOT_RATIO_SAMPLE_PX)
                                .build()
                        ).image
                    }
                }
                screenshotRatios[shot.url] = if (image != null && image.width > 0 && image.height > 0) {
                    image.width.toFloat() / image.height
                } else {
                    screenshotAspectRatio(shot.orientation)
                }
            }
        }
    }

    var expandedScreenshot by rememberSaveable(appId) { mutableIntStateOf(-1) }
    var showVersionPicker by rememberSaveable(appId) { mutableStateOf(false) }
    val screenshotCardBounds = remember { mutableStateMapOf<String, Rect>() }
    var playingVideo by remember(appId) { mutableStateOf<AppVideo?>(null) }
    val videoCardBounds = remember { mutableStateMapOf<String, Rect>() }

    SelectionContainer(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            CollapsibleMarketScaffold(
                title = collapsedTitle,
                onBack = onBack,
                alwaysShowActions = true,
                actions = {
                    if (current != null) {
                        translationButton()
                    }
                    if (current != null && current.app.source in AppSource.RepositorySources) {
                        val target = current.app.openLink.ifBlank { current.homepageUrl }
                        if (target.isNotBlank()) {
                            val openFailedText = stringResource(Res.string.open_link_failed)
                            IconButton(onClick = {
                                runCatching { uriHandler.openUri(target) }
                                    .onFailure { uiPlatform.showToast(openFailedText) }
                            }) {
                                Icon(
                                    imageVector = MiuixIcons.Link,
                                    contentDescription = "打开来源链接",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                },
                expandedContent = {
                    if (current != null && actionKind != null && current.app.source !in AppSource.RepositorySources) {
                        Box(Modifier.padding(bottom = 12.dp)) {
                            AppDetailHeading(
                                detail = current,
                                actionText = actionText,
                                actionKind = actionKind,
                                downloadStateProvider = currentDownloadState,
                                canOpenOtherAppStore = canOpenOtherAppStore,
                                canRedownload = canRedownload,
                                canCancelDownload = canCancelDownload,
                                onShare = shareApp,
                                onOpenOtherAppStore = { openInOtherAppStore(current.app.packageName) },
                                onRedownload = { viewModel.redownload(current.app) },
                                onClearDownload = { viewModel.clearDownload(current.app.packageName) },
                                onAction = {
                                    when (actionKind) {
                                        AppActionKind.OPEN -> viewModel.openApp(current.app)
                                        AppActionKind.INSTALL, AppActionKind.UPDATE -> viewModel.install(
                                            current.app
                                        )

                                        AppActionKind.RESERVE -> Unit
                                    }
                                },
                                onResumeDownload = {
                                    when (actionKind) {
                                        AppActionKind.OPEN -> viewModel.redownload(current.app)
                                        AppActionKind.INSTALL, AppActionKind.UPDATE -> viewModel.install(
                                            current.app
                                        )

                                        AppActionKind.RESERVE -> Unit
                                    }
                                },
                                onInstallDownloaded = viewModel::installDownloaded,
                                onCancel = viewModel::cancelDownload,
                                onSaveImage = saveImage,
                                canPickVersion = current.app.source == AppSource.GOOGLE || current.app.source == AppSource.WANDOUJIA,
                                onPickVersion = { showVersionPicker = true },
                            )
                            val wandoujiaHistory: com.app.market.viewmodel.HistoricalVersionsViewModel = koinViewModel()
                            val wandoujiaState by wandoujiaHistory.uiState.collectAsStateWithLifecycle()
                            val isWandoujia = current.app.source == AppSource.WANDOUJIA
                            if (showVersionPicker) {
                                LaunchedEffect(current.app.appId, current.app.packageName, isWandoujia) {
                                    if (isWandoujia) wandoujiaHistory.load(current.app.appId, current.app.packageName)
                                    else viewModel.loadHistoricalVersions(current.app.packageName)
                                }
                            }
                            val historicalVersions by viewModel.historicalVersions.collectAsStateWithLifecycle()
                            PickVersionDialog(
                                show = showVersionPicker,
                                versions = if (isWandoujia) wandoujiaState.items.map { version ->
                                    val timestamp = version.updatedAt.takeIf { it > 0L } ?: version.createdAt
                                    val date = if (timestamp > 0L) localDateOf(timestamp).toString() else ""
                                    com.app.market.domain.model.market.AppHistoricalVersion(version.versionId, version.versionName, date, version.versionCode)
                                } else historicalVersions.versions,
                                loading = if (isWandoujia) wandoujiaState.loading else historicalVersions.loading,
                                errorMessage = if (isWandoujia) { if (wandoujiaState.items.isEmpty()) wandoujiaState.errorMessage else "" } else historicalVersions.errorMessage,
                                onRetry = {
                                    if (isWandoujia) wandoujiaHistory.retry(current.app.appId, current.app.packageName)
                                    else viewModel.loadHistoricalVersions(current.app.packageName, force = true)
                                },
                                allowManual = !isWandoujia,
                                hasMore = isWandoujia && wandoujiaState.hasMore,
                                loadingMore = isWandoujia && wandoujiaState.loadingMore,
                                loadMoreError = if (isWandoujia && wandoujiaState.items.isNotEmpty()) wandoujiaState.errorMessage else "",
                                onLoadMore = { wandoujiaHistory.loadMore(current.app.appId, current.app.packageName) },
                                onDownload = { versionCode, versionName ->
                                    if (isWandoujia) {
                                        wandoujiaState.items.firstOrNull { it.versionId == versionCode }?.let { wandoujiaHistory.download(it) }
                                    } else viewModel.installVersion(current.app, versionCode, versionName)
                                    showVersionPicker = false
                                },
                                onSaveAs = { versionCode, versionName ->
                                    if (isWandoujia) {
                                        wandoujiaState.items.firstOrNull { it.versionId == versionCode }?.let { wandoujiaHistory.saveAs(it) }
                                    } else viewModel.saveVersionAs(current.app, versionCode, versionName)
                                    showVersionPicker = false
                                },
                                onDismiss = { showVersionPicker = false },
                            )
                        }
                    }
                },
            ) { innerPadding, backdropModifier, scrollBehavior ->
                val layoutDirection = LocalLayoutDirection.current
                val contentPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding() + PageVerticalPadding,
                    bottom = innerPadding.calculateBottomPadding() + PageVerticalPadding,
                )
                Crossfade(
                    targetState = phase,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface)
                        .then(backdropModifier),
                    label = "detail",
                ) { target ->
                    when (target) {
                        DetailPhase.Loading -> LoadingBox(Modifier.fillMaxSize())
                        DetailPhase.Error -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = state.errorMessage,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.main,
                                textAlign = TextAlign.Center,
                            )
                        }

                        DetailPhase.Content -> current?.let { detail ->
                            CompositionLocalProvider(
                                LocalTranslatedReadme provides translation.readme
                                    .takeIf { translation.active && it.isNotBlank() },
                                LocalTranslatedReadmeHtml provides translation.readmeHtml
                                    .takeIf { translation.active && it.isNotBlank() },
                                LocalTranslatedLatest provides translation.latest
                                    .takeIf { translation.active && it.isNotBlank() },
                                LocalTranslatedLogs provides if (translation.active) {
                                    translation.logs
                                } else {
                                    emptyMap()
                                },
                            ) {
                            AppDetailContent(
                                lspTab = detailTab,
                                onSelectLspTab = { detailTab = it },
                                current = detail,
                                downloadStates = downloadStates,
                                contentPadding = contentPadding,
                                scrollBehavior = scrollBehavior,
                                viewModel = viewModel,
                                uiPlatform = uiPlatform,
                                saveImage = saveImage,
                                onOpenDetail = onOpenDetail,
                                onOpenHistory = onOpenHistory,
                                screenshotRatios = screenshotRatios,
                                hiddenScreenshot = expandedScreenshot,
                                onExpandScreenshot = { expandedScreenshot = it },
                                onScreenshotCardPositioned = { url, bounds ->
                                    if (bounds == null) screenshotCardBounds.remove(url) else screenshotCardBounds[url] = bounds
                                },
                                onPlayVideo = { playingVideo = it },
                                playingVideoUrl = playingVideo?.url,
                                onVideoCardPositioned = { url, bounds ->
                                    if (bounds == null) videoCardBounds.remove(url) else videoCardBounds[url] = bounds
                                },
                            )
                            }
                        }
                    }
                }
            }

            current?.takeIf { expandedScreenshot >= 0 && it.screenshots.isNotEmpty() }?.let { detail ->
                ScreenshotViewer(
                    screenshots = detail.screenshots,
                    screenshotRatios = screenshotRatios,
                    cardBounds = screenshotCardBounds,
                    initialPage = expandedScreenshot.coerceIn(0, detail.screenshots.lastIndex),
                    onPageChange = { expandedScreenshot = it },
                    onClosed = { expandedScreenshot = -1 },
                )
            }

            playingVideo?.let { video ->
                AppVideoViewer(
                    video = video,
                    cardBounds = videoCardBounds[video.url],
                    onClosed = { playingVideo = null },
                )
            }
        }
    }
}

private enum class DetailPhase { Loading, Error, Content }

/** 整页翻译状态：按页签分别保存译文（README / 最新版正文 / 历史版本正文）。 */
private data class DetailTranslationState(
    val active: Boolean = false,
    val loading: Boolean = false,
    val readme: String = "",
    /** 仓库 README 的原地翻译（HTML），图片/链接/排版保留。 */
    val readmeHtml: String = "",
    val latest: String = "",
    val logs: Map<String, String> = emptyMap(),
    val error: String = "",
)
/** 译文下发给正文渲染点（不改子组件签名）。 */
internal val LocalTranslatedReadme = compositionLocalOf<String?> { null }
internal val LocalTranslatedReadmeHtml = compositionLocalOf<String?> { null }
internal val LocalTranslatedLatest = compositionLocalOf<String?> { null }
internal val LocalTranslatedLogs = compositionLocalOf<Map<String, String>> { emptyMap() }

@Composable
private fun AppDetailContent(
    lspTab: Int,
    onSelectLspTab: (Int) -> Unit,
    current: AppDetail,
    downloadStates: State<Map<String, DownloadState>>,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    viewModel: AppDetailViewModel,
    uiPlatform: UiPlatform,
    saveImage: (String, String) -> Unit,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onOpenHistory: (MarketAppInfo) -> Unit,
    screenshotRatios: Map<String, Float>,
    hiddenScreenshot: Int,
    onExpandScreenshot: (Int) -> Unit,
    onScreenshotCardPositioned: (String, Rect?) -> Unit,
    onPlayVideo: (AppVideo) -> Unit,
    playingVideoUrl: String?,
    onVideoCardPositioned: (String, Rect?) -> Unit,
) {
    val installText = installActionText()
    val reserveText = stringResource(Res.string.reserve)
    val previewTitle = stringResource(Res.string.preview)
    val appInfoTitle = stringResource(Res.string.app_info)

    // 评分/评价：取不到就传空串，空的一方在统计行里隐藏，剩下的居中
    val ratingValue = current.app.ratingScore
        .takeIf { !it.isNaN() && it > 0.0 }
        ?.let { formatRating(it) }
        ?: ""
    val commentCount = maxOf(current.commentCount, current.comments.size.toLong())
    val commentsLabel = if (commentCount > 0) {
        stringResource(
            Res.string.num_comments,
            formatCount(commentCount).ifBlank { "0" }
        )
    } else {
        ""
    }
    val commentsTitle = stringResource(Res.string.comments_and_scores)

    val ageLabel = stringResource(Res.string.age_rating)
    val downloadCountLabel = stringResource(Res.string.download_count)
    val registrationLabel = stringResource(Res.string.registration_num)
    val privacyLabel = stringResource(Res.string.privacy_policy)
    val packageNameLabel = stringResource(Res.string.package_name)
    val tapToOpenText = stringResource(Res.string.tap_to_open)
    val viewMoreText = stringResource(Res.string.view_more)
    val appSizeLabel = stringResource(Res.string.app_size)
    val versionNameLabel = stringResource(Res.string.app_version_name)
    val versionCodeLabel = stringResource(Res.string.app_version_code)
    val updateTimeLabel = stringResource(Res.string.app_update_time)
    val introduction = current.introduction.ifBlank { current.brief }
    val uriHandler = LocalUriHandler.current
    // LSPosed 模块详情：自述文件 / 版本 / 信息 三个页签（下标由上层持有，翻译按钮要用）
    var readmeDocUrl by rememberSaveable(current.app.appId) { mutableStateOf<String?>(null) }
    var readmeHistory by rememberSaveable(current.app.appId) { mutableStateOf<List<String>>(emptyList()) }
    val onReadmeBack: () -> Unit = {
        readmeDocUrl = readmeHistory.lastOrNull()
        if (readmeHistory.isNotEmpty()) readmeHistory = readmeHistory.dropLast(1)
    }
    val onOpenReadmeLink: (String) -> Unit = { link ->
        val internal = resolveReadmeInternalUrl(current.app.openLink, current.introductionHtml, link)
        if (internal != null) {
            if (readmeDocUrl != null) readmeHistory = readmeHistory + readmeDocUrl!!
            readmeDocUrl = internal
        } else {
            runCatching { uriHandler.openUri(link) }
        }
    }
    // README 里的 .md 链接：不弹窗，直接在当前页把正文换掉，返回键退回
    val readmeBackState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = readmeBackState,
        isBackEnabled = readmeDocUrl != null,
        onBackCompleted = { onReadmeBack() },
    )

    var showCommentsSheet by rememberSaveable(current.app.appId) { mutableStateOf(false) }
    var historyExpanded by rememberSaveable(current.app.openLink, current.app.packageName) { mutableStateOf(false) }
    val releaseHistory by viewModel.moduleReleases.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // LSPosed 模块没有真实评分/下载量（仓库接口不提供），把这一行整体省掉，
        // 大小改到下面「应用信息」最底部显示
        val isRepositorySource = current.app.source in AppSource.RepositorySources
        if (isRepositorySource) {
            item(key = "lspHeader") {
                if (current.app.source == AppSource.GITHUB) {
                    val ghDownloadState = remember(current.app.packageName) {
                        derivedStateOf { downloadStates.value[current.app.packageName] }
                    }
                    val ghActionKind = current.app.actionKind()
                    GitHubRepoHeader(
                        current = current,
                        actionKind = ghActionKind,
                        downloadState = ghDownloadState.value,
                        onAction = {
                            when (ghActionKind) {
                                AppActionKind.OPEN -> viewModel.openApp(current.app)
                                AppActionKind.INSTALL, AppActionKind.UPDATE -> viewModel.install(current.app)
                                AppActionKind.RESERVE -> Unit
                            }
                        },
                        onResumeDownload = { viewModel.install(current.app) },
                        onInstallDownloaded = viewModel::installDownloaded,
                        onCancel = viewModel::cancelDownload,
                    )
                } else {
                    LsposedModuleHeader(current)
                }
            }
            item(key = "lspTabs") {
                LsposedModuleTabRow(
                    selected = lspTab,
                    titles = if (current.app.source == AppSource.GITHUB) {
                        listOf("README", "Releases")
                    } else {
                        listOf("自述文件", "版本", "信息")
                    },
                    onSelect = onSelectLspTab,
                )
            }
        }
        if (!isRepositorySource) {
        item(key = "stats") {
            AppDetailStatsRow(
                rating = ratingValue,
                reviews = commentsLabel,
                downloads = current.downloadCount
                    .takeUnless { current.app.source == AppSource.SAMSUNG }
                    ?.let { formatCount(it).ifBlank { "0" } },
                downloadsLabel = downloadCountLabel,
                appSize = formatSize(current.app.apkSize).ifBlank { "--" },
                appSizeLabel = appSizeLabel,
                ageClassification = current.ageClassification,
                ageLabel = ageLabel,
                onReviewsClick = if (current.comments.isNotEmpty()) {
                    { showCommentsSheet = true }
                } else {
                    null
                },
            )
        }
        }

        val showReadmeTab = !isRepositorySource || lspTab == 0
        if (isRepositorySource && current.app.source == AppSource.GITHUB && lspTab == 0 &&
            current.introductionHtml.isBlank()
        ) {
            item(key = "readmeRetry") {
                RetryHintCard(
                    text = "README 没加载出来（GitHub 接口偶发失败）· 点此重试",
                    onClick = viewModel::retryLoad,
                )
            }
        }
        val openDocUrl = readmeDocUrl
        if (showReadmeTab && openDocUrl != null) {
            item(key = "readmeDoc") {
                LsposedReadmeDoc(
                    url = openDocUrl,
                    viewModel = viewModel,
                    onBack = onReadmeBack,
                    onOpenLink = onOpenReadmeLink,
                )
            }
        } else if (showReadmeTab && (current.videos.isNotEmpty() || current.screenshots.isNotEmpty() || introduction.isNotBlank())) {
            item(key = "preview") {
                PreviewAndIntroductionSection(
                    current = current,
                    screenshotsTitle = previewTitle,
                    showFullIntroduction = current.app.source in com.app.market.domain.model.market.AppSource.RepositorySources,
                    screenshotRatios = screenshotRatios,
                    hiddenScreenshot = hiddenScreenshot,
                    onExpandScreenshot = onExpandScreenshot,
                    onScreenshotCardPositioned = onScreenshotCardPositioned,
                    onPlayVideo = onPlayVideo,
                    playingVideoUrl = playingVideoUrl,
                    onVideoCardPositioned = onVideoCardPositioned,
                    introduction = introduction,
                    introductionHtml = current.introductionHtml,
                    onOpenReadmeLink = onOpenReadmeLink,
                    onSaveImage = saveImage,
                )
            }
        }

        item(key = "appinfo") {
            if (isRepositorySource && lspTab != 2) return@item
            if (isRepositorySource) {
                LsposedModuleInfo(
                    detail = current,
                    onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                )
                return@item
            }
            DetailSection(
                title = appInfoTitle,
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        InfoLine(packageNameLabel, current.app.packageName)
                        InfoLine(versionNameLabel, current.app.versionName)
                        InfoLine(versionCodeLabel, current.app.versionCode.toString())
                        if (current.updateTime > 0L) {
                            val date = localDateOf(current.updateTime)
                            InfoLine(
                                updateTimeLabel,
                                stringResource(
                                    Res.string.date_full,
                                    date.year,
                                    date.month.number,
                                    date.day
                                ),
                            )
                        }
                        if (current.registrationNum.isNotBlank()) {
                            InfoLine(registrationLabel, current.registrationNum)
                        }
                        if (current.privacyUrl.isNotBlank()) {
                            InfoLine(
                                label = privacyLabel,
                                value = tapToOpenText,
                                valueColor = MiuixTheme.colorScheme.primary,
                                onValueClick = { uriHandler.openUri(current.privacyUrl) },
                            )
                        }
                        if (isRepositorySource) {
                            // 模块详情不显示统计行，包大小放到应用信息最底下
                            InfoLine(appSizeLabel, formatSize(current.app.apkSize).ifBlank { "--" })
                        }
                        if (isRepositorySource) {
                            val assets by viewModel.moduleAssets.collectAsStateWithLifecycle()
                            val selectedAsset by viewModel.selectedAssetName.collectAsStateWithLifecycle()
                            if (assets.size > 1) {
                                // 一版多附件（国内版/国际版、arm64/arm 等）：点这里选要下载哪一份
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "模块附件",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    com.app.market.ui.widget.MiuixDropdown(
                                        items = assets.map { "${it.name} · ${formatSize(it.size)}" },
                                        selectedIndex = assets.indexOfFirst { it.name == selectedAsset }
                                            .coerceAtLeast(0),
                                        onSelectedIndexChange = { index ->
                                            viewModel.selectModuleAsset(assets[index].name)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isRepositorySource && lspTab == 1) {
            item(key = "lspVersions") {
                val moduleAction = current.app.actionKind()
                val moduleDownloadState = remember(current.app.packageName) {
                    derivedStateOf { downloadStates.value[current.app.packageName] }
                }
                val moduleActionText = when (moduleAction) {
                    AppActionKind.INSTALL -> installText
                    AppActionKind.UPDATE -> stringResource(Res.string.update)
                    AppActionKind.OPEN -> stringResource(Res.string.open)
                    AppActionKind.RESERVE -> reserveText
                }
                LsposedModuleVersions(
                    current = current,
                    viewModel = viewModel,
                    showBadges = current.app.source == AppSource.GITHUB,
                    actionText = moduleActionText,
                    actionKind = moduleAction,
                    downloadStateProvider = moduleDownloadState,
                    onAction = {
                        when (moduleAction) {
                            AppActionKind.OPEN -> viewModel.openApp(current.app)
                            AppActionKind.INSTALL, AppActionKind.UPDATE -> viewModel.install(current.app)
                            AppActionKind.RESERVE -> Unit
                        }
                    },
                    onInstallDownloaded = viewModel::installDownloaded,
                    onCancel = viewModel::cancelDownload,
                )
            }
        }

        if (isRepositorySource && lspTab == 1) {
            item(key = "lspHistory") {
                LsposedReleaseHistory(
                    viewModel = viewModel,
                    showBadges = current.app.source == AppSource.GITHUB,
                    expanded = historyExpanded,
                    onToggle = { historyExpanded = !historyExpanded },
                )
            }
            if (historyExpanded) {
                itemsIndexed(releaseHistory.drop(1), key = { index, release -> "history:$index:${release.tagName}" }) { _, release ->
                    ReleaseHistoryCard(current.app, release, viewModel, current.app.source == AppSource.GITHUB)
                }
            }
        }

        if (current.comments.isNotEmpty()) {
            item(key = "comments") {
                val displayedComments = current.comments.take(2)
                DetailSection(
                    title = commentsTitle,
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        cornerRadius = 16.dp,
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text(
                                    text = formatRating(current.app.ratingScore),
                                    color = MiuixTheme.colorScheme.onSurface,
                                    style = MiuixTheme.textStyles.title1.copy(lineHeight = 40.sp),
                                )
                                Text(
                                    text = commentsLabel,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.body2.copy(lineHeight = 18.sp),
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                displayedComments.forEach { comment ->
                                    CommentContent(comment)
                                }
                            }
                            if (current.comments.size > 2) {
                                Text(
                                    text = viewMoreText,
                                    color = MiuixTheme.colorScheme.primary,
                                    style = MiuixTheme.textStyles.main.copy(lineHeight = 22.sp),
                                    modifier = Modifier.clickable { showCommentsSheet = true },
                                )
                            }
                        }
                    }
                }
            }
        }

    }

    OverlayBottomSheet(
        show = showCommentsSheet,
        title = commentsTitle,
        onDismissRequest = { showCommentsSheet = false },
        enableNestedScroll = true,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "comments-summary") {
                Column {
                    Text(
                        text = formatRating(current.app.ratingScore),
                        color = MiuixTheme.colorScheme.onSurface,
                        style = MiuixTheme.textStyles.title1.copy(lineHeight = 40.sp),
                    )
                    Text(
                        text = commentsLabel,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2.copy(lineHeight = 18.sp),
                    )
                }
            }
            itemsIndexed(
                items = current.comments,
                key = { index, comment -> "${index}_${comment.userName}" },
            ) { _, comment ->
                CommentContent(comment)
            }
        }
    }

}

@Composable
private fun AppDetailStatsRow(
    rating: String,
    reviews: String,
    downloads: String?,
    downloadsLabel: String,
    appSize: String,
    appSizeLabel: String,
    /** 空串表示没有分级信息，整格连同分隔线一起省掉。 */
    ageClassification: String,
    ageLabel: String,
    /** 无评论正文时传 null，否则点开只是空面板。 */
    onReviewsClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 评分/评价格：两者都拿不到时整格连分隔线一起省略；缺一个时另一个在格内居中
        var hasPrev = false
        if (rating.isNotBlank() || reviews.isNotBlank()) {
            DetailStat(
                value = rating,
                label = reviews,
                showStar = rating.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onReviewsClick != null) {
                            Modifier.clickable(
                                interactionSource = null,
                                indication = null,
                                onClick = onReviewsClick,
                            )
                        } else {
                            Modifier
                        }
                    ),
            )
            hasPrev = true
        }
        if (downloads != null) {
            if (hasPrev) DetailStatDivider()
            DetailStat(
                value = downloads,
                label = downloadsLabel,
                modifier = Modifier.weight(1f),
            )
            hasPrev = true
        }
        if (hasPrev) DetailStatDivider()
        DetailStat(
            value = appSize,
            label = appSizeLabel,
            modifier = Modifier.weight(1f),
        )
        if (ageClassification.isNotBlank()) {
            DetailStatDivider()
            DetailStat(
                value = ageClassification,
                label = ageLabel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DetailStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    showStar: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (value.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = value,
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.main.copy(lineHeight = 20.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showStar) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_star_fill),
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, lineHeight = 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailStatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    )
}

@Composable
private fun PreviewAndIntroductionSection(
    current: AppDetail,
    screenshotsTitle: String,
    showFullIntroduction: Boolean = false,
    screenshotRatios: Map<String, Float>,
    hiddenScreenshot: Int,
    onExpandScreenshot: (Int) -> Unit,
    onScreenshotCardPositioned: (String, Rect?) -> Unit,
    onPlayVideo: (AppVideo) -> Unit,
    playingVideoUrl: String?,
    onVideoCardPositioned: (String, Rect?) -> Unit,
    introduction: String,
    introductionHtml: String = "",
    onOpenReadmeLink: (String) -> Unit = {},
    onSaveImage: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {

        if (current.videos.isNotEmpty() || current.screenshots.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTitle(
                    text = screenshotsTitle,
                    insideMargin = PaddingValues(horizontal = 28.dp, vertical = 0.dp),
                )
                val readyPrefix = current.screenshots
                    .indexOfFirst { !screenshotRatios.containsKey(it.url) }
                    .let { if (it < 0) current.screenshots.size else it }
                LazyRow(
                    modifier = Modifier.height(PREVIEW_ITEM_HEIGHT),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    itemsIndexed(
                        current.videos,
                        key = { _, video -> video.url },
                    ) { _, video ->
                        VideoPreviewCard(
                            video = video,
                            hidden = video.url == playingVideoUrl,
                            onClick = { onPlayVideo(video) },
                            onPositioned = { bounds -> onVideoCardPositioned(video.url, bounds) },
                        )
                    }
                    itemsIndexed(
                        current.screenshots.take(readyPrefix),
                        key = { _, shot -> shot.url }) { index, shot ->
                        SaveableImageBox(
                            url = shot.expandedUrl,
                            fileName = "${current.app.packageName}_screenshot_${index + 1}",
                            onClick = { onExpandScreenshot(index) },
                            onSaveImage = onSaveImage,
                        ) {
                            // 滚出视口即移除记录，收回动画据此判断目标是否在场
                            DisposableEffect(shot.url) {
                                onDispose { onScreenshotCardPositioned(shot.url, null) }
                            }
                            Screenshot(
                                screenshot = shot,
                                ratio = screenshotRatios.getValue(shot.url),
                                modifier = Modifier
                                    // positionInRoot 不受父容器裁剪影响；boundsInRoot 会把半可见卡片裁成半张
                                    .onGloballyPositioned {
                                        onScreenshotCardPositioned(shot.url, Rect(it.positionInRoot(), it.size.toSize()))
                                    }
                                    .graphicsLayer { alpha = if (index == hiddenScreenshot) 0f else 1f }
                                    .squircleClip(16.dp),
                            )
                        }
                    }
                }
            }
        }

        if (introduction.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current.app.source != com.app.market.domain.model.market.AppSource.GITHUB) {
                    SmallTitle(
                        text = if (current.app.source in com.app.market.domain.model.market.AppSource.RepositorySources) {
                            "\u6a21\u5757\u4ecb\u7ecd"
                        } else {
                            stringResource(Res.string.introduction)
                        },
                        insideMargin = PaddingValues(horizontal = 28.dp, vertical = 0.dp),
                    )
                }
                if (showFullIntroduction) {
                    // LSPosed 模块：README 本来就是 Markdown（仓库给的是渲染好的 HTML），
                    // 按标题/列表/代码块的结构渲染，而不是拍平成一大段纯文本。
                    val translatedReadmeHtml = LocalTranslatedReadmeHtml.current
                    val translatedReadme = LocalTranslatedReadme.current
                    if (translatedReadmeHtml != null) {
                        // 原地翻译的 README：HTML 原样渲染，图片/蓝色链接/排版都在
                        com.app.market.ui.component.ModuleReadmeText(
                            html = translatedReadmeHtml,
                            modifier = Modifier.padding(horizontal = 28.dp),
                            onOpenLink = onOpenReadmeLink,
                            linkBaseUrl = readmeRawBaseUrl(current.app.openLink),
                        )
                    } else if (translatedReadme != null) {
                        com.app.market.ui.component.ModuleReadmeText(
                            html = markdownToHtml(translatedReadme),
                            modifier = Modifier.padding(horizontal = 28.dp),
                            onOpenLink = onOpenReadmeLink,
                            linkBaseUrl = readmeRawBaseUrl(current.app.openLink),
                        )
                    } else if (introductionHtml.isNotBlank()) {
                        com.app.market.ui.component.ModuleReadmeText(
                            html = introductionHtml,
                            modifier = Modifier.padding(horizontal = 28.dp),
                            onOpenLink = onOpenReadmeLink,
                            linkBaseUrl = readmeRawBaseUrl(current.app.openLink),
                        )
                    } else {
                        Text(
                            text = introduction,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 28.dp),
                        )
                    }
                } else {
                    // 普通来源的「应用介绍」：翻译开启时把文字换成译文，
                    // 卡片/字体/排版与原文完全一致（以前译文走裸 Text，白卡片和字体会变）
                    val translatedIntro = LocalTranslatedReadme.current
                    IntroductionSummary(
                        rawText = introduction,
                        overrideText = translatedIntro,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewCard(
    video: AppVideo,
    hidden: Boolean,
    onClick: () -> Unit,
    onPositioned: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(video.url) {
        onDispose { onPositioned(null) }
    }
    Box(
        modifier = modifier
            .height(PREVIEW_ITEM_HEIGHT)
            .aspectRatio(screenshotAspectRatio(video.orientation))
            .onGloballyPositioned {
                onPositioned(Rect(it.positionInRoot(), it.size.toSize()))
            }
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
            .squircleSurface(
                color = MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = 16.dp,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppAsyncImage(
            url = video.coverUrl,
            contentDescription = video.title.takeIf { it.isNotBlank() },
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .squircleBackground(
                    color = Color.Black.copy(alpha = 0.35f),
                    cornerRadius = 22.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.padding(start = 2.dp),
                imageVector = MiuixIcons.Play,
                contentDescription = stringResource(Res.string.video_play),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun IntroductionSummary(
    rawText: String,
    /** 翻译后的纯文本译文：直接用它渲染（同一张卡片、同一字体），不再过 HTML 拍平 */
    overrideText: String? = null,
) {
    // 商店返回的简介是 HTML 片段，先把 <br> 之类转成换行，否则界面上会出现字面量标签
    val text = remember(rawText, overrideText) { overrideText ?: rawText.toPlainStoreText() }
    // 应用介绍默认展开，不再折叠成 3 行
    var expanded by remember(text) { mutableStateOf(true) }
    var hasVisualOverflow by remember(text) { mutableStateOf(false) }
    val showMore = !expanded && hasVisualOverflow
    val descriptionStyle = MiuixTheme.textStyles.paragraph.copy(lineHeight = 22.sp)
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
    ) {
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            val surface = MiuixTheme.colorScheme.surfaceContainer
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = text,
                    modifier = Modifier.fillMaxWidth(),
                    color = MiuixTheme.colorScheme.onSurface,
                    style = descriptionStyle,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Clip,
                    onTextLayout = { result ->
                        if (!expanded) hasVisualOverflow = result.hasVisualOverflow
                    },
                )
                if (showMore) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .height(IntrinsicSize.Min)
                            .clickable { expanded = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(58.dp)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        0f to Color.Transparent,
                                        1f to surface,
                                    ),
                                ),
                        )
                        Text(
                            text = stringResource(Res.string.view_more),
                            modifier = Modifier.background(surface),
                            color = MiuixTheme.colorScheme.primary,
                            style = descriptionStyle,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Zero vertical inside margin: the list's 20.dp item spacing and this column's 8.dp gap
        // own the vertical rhythm, matching the app-wide SectionTitle look.
        SmallTitle(
            text = title,
            insideMargin = PaddingValues(horizontal = 28.dp, vertical = 0.dp),
        )
        content()
    }
}

@Composable
private fun AppDetailHeading(
    detail: AppDetail,
    actionText: String,
    actionKind: AppActionKind,
    downloadStateProvider: State<DownloadState?>,
    canOpenOtherAppStore: Boolean,
    canRedownload: Boolean,
    canCancelDownload: Boolean,
    onShare: () -> Unit,
    onOpenOtherAppStore: () -> Unit,
    onAction: () -> Unit,
    onResumeDownload: () -> Unit,
    onInstallDownloaded: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRedownload: () -> Unit,
    onClearDownload: () -> Unit,
    onSaveImage: (String, String) -> Unit,
    canPickVersion: Boolean = false,
    onPickVersion: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .padding(horizontal = 12.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaveableImageBox(
            url = detail.app.icon,
            fileName = "${detail.app.packageName}_icon",
            onSaveImage = onSaveImage,
        ) {
            AppIcon(
                url = detail.app.icon,
                contentDescription = appDisplayName(detail.app),
                size = 96.dp,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start,
        ) {
            Column {
                Text(
                    text = appDisplayName(detail.app),
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.title2.copy(lineHeight = 30.sp),
                    maxLines = 1,
                )
                Text(
                    text = detail.app.publisherName.ifBlank { detail.app.packageName },
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.body2.copy(lineHeight = 18.sp),
                    maxLines = 1,
                )
            }
            Row(
                // 固定高度 + 顶对齐：点「下载」后按钮下方会多出速度行，高度变化不能顶着按钮上下跳
                modifier = Modifier.height(60.dp).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                DetailActionButton(
                    packageName = detail.app.packageName,
                    actionText = actionText,
                    actionKind = actionKind,
                    downloadStateProvider = downloadStateProvider,
                    enabled = actionKind == AppActionKind.OPEN || !detail.app.isDownloadBlocked(),
                    onAction = onAction,
                    onResumeDownload = onResumeDownload,
                    onInstallDownloaded = onInstallDownloaded,
                    onCancel = onCancel,
                    minHeight = 32.dp,
                )
                DetailMoreMenu(
                    canOpenOtherAppStore = canOpenOtherAppStore,
                    canRedownload = canRedownload,
                    canCancelDownload = canCancelDownload,
                    canPickVersion = canPickVersion,
                    onShare = onShare,
                    onOpenOtherAppStore = onOpenOtherAppStore,
                    onRedownload = onRedownload,
                    onClearDownload = onClearDownload,
                    onPickVersion = onPickVersion,
                )
            }
        }
    }
}

/** 顶栏与展开头部共用的 ⋮ 菜单按钮，弹窗锚定在按钮处。 */
@Composable
private fun DetailMoreMenu(
    canOpenOtherAppStore: Boolean,
    canRedownload: Boolean,
    canCancelDownload: Boolean,
    canPickVersion: Boolean = false,
    onShare: () -> Unit,
    onOpenOtherAppStore: () -> Unit,
    onRedownload: () -> Unit,
    onClearDownload: () -> Unit,
    onPickVersion: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    Box(modifier) {
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
        AppGlassDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            anchorBounds = rowBounds,
            items = buildList {
                add(stringResource(Res.string.share) to onShare)
                if (canOpenOtherAppStore) {
                    add(stringResource(Res.string.open_in_other_app_store) to onOpenOtherAppStore)
                }
                if (canRedownload) {
                    add(stringResource(Res.string.reinstall) to onRedownload)
                }
                if (canPickVersion) {
                    add(stringResource(Res.string.manual_pick_version) to onPickVersion)
                }
            },
        )
    }
}

/** Google Play 详情页「下载指定版本」：历史版本目录（可搜索过滤）+ 手动输入版本号下载。 */
@Composable
private fun PickVersionDialog(
    show: Boolean,
    versions: List<com.app.market.domain.model.market.AppHistoricalVersion>,
    loading: Boolean,
    errorMessage: String = "",
    onRetry: () -> Unit = {},
    allowManual: Boolean = true,
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    loadMoreError: String = "",
    onLoadMore: () -> Unit = {},
    onDownload: (Long, String) -> Unit,
    onSaveAs: (Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember(show) { mutableStateOf("") }
    // 点击历史版本只是选中（高亮），不自动下载；点「下载」/「另存」才动作
    var selected by remember(show) { mutableStateOf<Long?>(null) }
    val filtered = remember(filter, versions) {
        if (filter.isBlank()) versions
        else versions.filter {
            it.displayVersionCode.toString().startsWith(filter) || it.versionName.contains(filter, ignoreCase = true)
        }
    }
    val targetVersion = selected ?: if (allowManual) filter.toLongOrNull() else null
    // 目标版本名：列表选中用条目版本名；手输版本号时用版本号本身
    val targetVersionName = targetVersion?.let { code ->
        versions.firstOrNull { it.versionCode == code }?.versionName ?: code.toString()
    }
    PopupTheme { WindowDialog(
        show = show,
        title = stringResource(Res.string.manual_pick_version),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = filter,
                onValueChange = { value -> filter = value.trim() },
                label = stringResource(Res.string.manual_update_version_code),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    InfiniteProgressIndicator(size = 18.dp)
                }
                errorMessage.isNotEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = errorMessage,
                        color = MiuixTheme.colorScheme.onErrorContainer,
                        style = MiuixTheme.textStyles.body2,
                    )
                    AppTextButton(
                        text = stringResource(Res.string.retry),
                        onClick = onRetry,
                        textStyle = MiuixTheme.textStyles.body2.copy(fontSize = 17.sp),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                filtered.isEmpty() && !hasMore -> Text(
                    text = stringResource(Res.string.no_historical_versions),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                ) {
                    itemsIndexed(filtered) { _, entry ->
                        val isSelected = selected == entry.versionCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else MiuixTheme.colorScheme.primary.copy(alpha = 0f)
                                )
                                .clickable { selected = entry.versionCode }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = entry.versionName.ifBlank { "-" },
                                    style = MiuixTheme.textStyles.body2,
                                    color = if (isSelected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${entry.displayVersionCode} · ${entry.date}",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                    if (hasMore) {
                        item {
                            AppTextButton(
                                text = if (loadingMore) "加载中…" else if (loadMoreError.isNotBlank()) "加载失败，点击重试" else "加载更多",
                                enabled = !loadingMore,
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                AppTextButton(
                    text = stringResource(Res.string.save_as),
                    enabled = targetVersion != null,
                    onClick = {
                        val code = targetVersion
                        val name = targetVersionName
                        if (code != null && name != null) onSaveAs(code, name)
                    },
                    textStyle = MiuixTheme.textStyles.body2.copy(fontSize = 17.sp),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                AppTextButton(
                    text = stringResource(Res.string.download),
                    enabled = targetVersion != null,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        val code = targetVersion
                        val name = targetVersionName
                        if (code != null && name != null) onDownload(code, name)
                    },
                    textStyle = MiuixTheme.textStyles.body2.copy(fontSize = 17.sp),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } }
}

@Composable
private fun DetailActionButton(
    packageName: String,
    actionText: String,
    actionKind: AppActionKind,
    downloadStateProvider: State<DownloadState?>,
    enabled: Boolean = true,
    onAction: () -> Unit,
    onResumeDownload: () -> Unit,
    onInstallDownloaded: (String) -> Unit,
    onCancel: (String) -> Unit,
    minHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val downloadState = downloadStateProvider.value
    if (downloadState == null) {
        AppButton(
            // 「安装」入口统一显示「下载」（与 AppActionButton 的文案一致）
            text = if (actionKind == AppActionKind.INSTALL) stringResource(Res.string.download) else actionText,
            onClick = onAction,
            enabled = enabled && actionKind != AppActionKind.RESERVE,
            colors = if (actionKind == AppActionKind.OPEN || actionKind == AppActionKind.RESERVE) {
                ButtonDefaults.buttonColors()
            } else {
                ButtonDefaults.buttonColorsPrimary()
            },
            cornerRadius = 100.dp,
            minHeight = minHeight,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            // 与进度按钮同宽：空闲 → 下载中 切换不跳框
            modifier = modifier.width(ProgressButtonWidth),
        )
    } else {
        AppActionButton(
            packageName = packageName,
            actionText = actionText,
            actionKind = actionKind,
            downloadState = downloadState,
            enabled = enabled && actionKind != AppActionKind.RESERVE,
            onAction = onAction,
            onResumeDownload = onResumeDownload,
            onInstallDownloaded = onInstallDownloaded,
            onCancel = onCancel,
            minHeight = minHeight,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier = modifier,
        )
    }
}

private fun Modifier.saveImageOnLongPress(
    url: String,
    fileName: String,
    onClick: (() -> Unit)? = null,
    onSave: (String, String) -> Unit,
): Modifier = if (onClick == null) {
    pointerInput(url, fileName) {
        detectTapGestures(onLongPress = { onSave(url, fileName) })
    }
} else {
    combinedClickable(
        onClick = onClick,
        onLongClick = { onSave(url, fileName) },
    )
}

@Composable
private fun SaveableImageBox(
    url: String,
    fileName: String,
    onClick: (() -> Unit)? = null,
    onSaveImage: (String, String) -> Unit,
    content: @Composable () -> Unit,
) {
    var showMenu by remember(url, fileName) { mutableStateOf(false) }
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier = Modifier
            .saveImageOnLongPress(
                url = url,
                fileName = fileName,
                onClick = onClick,
                onSave = { _, _ -> showMenu = true },
            )
            .onGloballyPositioned { rowBounds = it.boundsInWindow() },
    ) {
        content()
        AppGlassDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            anchorBounds = rowBounds,
            items = listOf(
                stringResource(Res.string.save_image) to { onSaveImage(url, fileName) },
            ),
        )
    }
}

@Composable
private fun Screenshot(
    screenshot: AppScreenshot,
    ratio: Float,
    modifier: Modifier = Modifier,
) {
    AppAsyncImage(
        url = screenshot.url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .height(PREVIEW_ITEM_HEIGHT)
            .aspectRatio(ratio),
    )
}

// 全屏截图查看层：变换阶段绘制独立图层，浏览阶段是纯 Pager，二者在进度端点处像素对齐无缝切换
@Composable
private fun ScreenshotViewer(
    screenshots: List<AppScreenshot>,
    screenshotRatios: Map<String, Float>,
    cardBounds: Map<String, Rect>,
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    onClosed: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { screenshots.size }
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { currentOnPageChange(it) }
    }

    // 0 = 卡片位置，1 = 全屏
    val progress = remember { Animatable(0f) }
    var browsing by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(SCREENSHOT_VIEWER_ANIM_MS))
        browsing = true
    }
    val animationScope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            browsing = false
            animationScope.launch {
                progress.animateTo(0f, tween(SCREENSHOT_VIEWER_ANIM_MS))
                onClosed()
            }
        }
    }

    val backState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = !closing,
        onBackCompleted = dismiss,
    )

    // 卡片位置以根坐标系上报，需换算到查看层自身坐标系（宽屏侧栏等场景下两者原点不同）
    var viewerOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { viewerOrigin = it.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures { dismiss() } },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(Color.Black),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (browsing) 1f else 0f },
            userScrollEnabled = browsing,
        ) { page ->
            val shot = screenshots[page]
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppAsyncImage(
                    url = shot.expandedUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    placeholderMemoryCacheUrl = shot.url,
                    modifier = Modifier.aspectRatio(
                        screenshotRatios[shot.url] ?: screenshotAspectRatio(shot.orientation)
                    ),
                )
            }
        }

        if (!browsing) {
            val shot = screenshots[pagerState.currentPage]
            val ratio = screenshotRatios[shot.url] ?: screenshotAspectRatio(shot.orientation)
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()
            val fitWidth = minOf(containerWidth, containerHeight * ratio)
            val fitRect = Rect(
                Offset((containerWidth - fitWidth) / 2f, (containerHeight - fitWidth / ratio) / 2f),
                Size(fitWidth, fitWidth / ratio),
            )
            val cardRect = cardBounds[shot.url]?.translate(-viewerOrigin)
                ?: offscreenCardRect(screenshots, pagerState.currentPage, cardBounds, containerWidth, viewerOrigin)
                ?: fitRect
            // 圆角量化：squircleClip 每次半径变化都要重建笔刷
            val cornerDp by remember(progress) {
                derivedStateOf { (SCREENSHOT_CORNER_DP * (1f - progress.value)).roundToInt() }
            }
            AppAsyncImage(
                url = shot.expandedUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholderMemoryCacheUrl = shot.url,
                modifier = Modifier
                    .animatedRect { lerp(cardRect, fitRect, progress.value) }
                    .squircleClip(cornerDp.dp),
            )
        }
    }
}

// 目标卡片已滚出视口时的收回终点：按最近已知卡片推算，飞向对应一侧屏幕外
private fun offscreenCardRect(
    screenshots: List<AppScreenshot>,
    targetIndex: Int,
    cardBounds: Map<String, Rect>,
    containerWidth: Float,
    viewerOrigin: Offset,
): Rect? {
    val known = screenshots.withIndex().mapNotNull { (index, shot) ->
        cardBounds[shot.url]?.let { index to it.translate(-viewerOrigin) }
    }
    val nearest = known.minByOrNull { abs(it.first - targetIndex) } ?: return null
    val reference = nearest.second
    val left = if (targetIndex > nearest.first) containerWidth else -reference.width
    return Rect(Offset(left, reference.top), Size(reference.width, reference.height))
}

internal fun screenshotAspectRatio(orientation: ScreenshotOrientation): Float =
    when (orientation) {
        ScreenshotOrientation.PORTRAIT -> PORTRAIT_SCREENSHOT_RATIO
        ScreenshotOrientation.LANDSCAPE -> LANDSCAPE_SCREENSHOT_RATIO
    }

@Composable
private fun CommentContent(comment: AppComment) {
    val body = remember(comment.content) {
        comment.content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CommentHeader(comment)
        Text(
            text = body,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.paragraph.copy(lineHeight = 22.sp),
        )
    }
}

@Composable
private fun CommentHeader(comment: AppComment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = comment.userName,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.headline1.copy(lineHeight = 22.sp),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (comment.score > 0) {
            Row {
                // 无 key 的 remember 在 item 位置复用时会保留上一条评论的评分
                val filled = remember(comment.score) { comment.score.roundToInt().coerceIn(1, 5) }
                repeat(5) { index ->
                    if (index < filled) {
                        Icon(
                            painter = painterResource(
                                Res.drawable.ic_star_fill
                            ),
                            contentDescription = null,
                            tint = Color(0xFFFFC100),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                Res.drawable.ic_star
                            ),
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface.copy(0.2f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
    valueColor: Color = MiuixTheme.colorScheme.onSurface,
    onValueClick: (() -> Unit)? = null,
) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.headline1.copy(lineHeight = 22.sp),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onValueClick != null) {
                        Modifier.clickable(
                            onClick = onValueClick,
                            indication = null,
                            interactionSource = null,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                text = value,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                color = valueColor,
                style = MiuixTheme.textStyles.body2.copy(lineHeight = 18.sp),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
    }
}

private fun formatRating(score: Double): String =
    ((score * 10).roundToInt() / 10.0).toString()
/**
 * LSPosed 模块详情头部：应用名 / 包名 / 已安装版本 / 需要更新（对齐 LSPosed Manager 的仓库页）。
 */
@Composable
private fun LsposedModuleHeader(current: AppDetail) {
    val app = current.app
    val installed = app.installedVersionCode > 0L
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = appDisplayName(app),
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "包名：" + app.packageName,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (installed) {
                    LsposedChip(
                        text = "✓ " + "已安装 " + app.installedVersionName.ifBlank { app.installedVersionCode.toString() },
                        highlight = false,
                    )
                }
                if (installed && app.installedVersionCode < app.versionCode) {
                    LsposedChip(text = "需要更新", highlight = true)
                }
            }
        }
    }
}

@Composable
private fun LsposedChip(text: String, highlight: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = if (highlight) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(shape)
            .then(
                if (highlight) {
                    Modifier.border(1.dp, MiuixTheme.colorScheme.primary, shape)
                } else {
                    Modifier.background(MiuixTheme.colorScheme.surfaceContainerHigh)
                },
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 自述文件 / 版本 / 信息 三段页签。 */
@Composable
private fun LsposedModuleTabRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    titles: List<String> = listOf("自述文件", "版本", "信息"),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        titles.forEachIndexed { index, title ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) {
                            MiuixTheme.colorScheme.secondaryVariant
                        } else {
                            MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                        },
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}





private fun String.isMarkdownDoc(): Boolean {
    val clean = substringBefore('#').lowercase()
    return clean.endsWith(".md") || clean.endsWith(".markdown")
}

private fun escapeHtmlText(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

/** 极简 markdown → HTML：只覆盖 README 常见语法，输出交给 ModuleReadmeText 渲染。 */
internal fun markdownToHtml(markdown: String): String {
    val out = StringBuilder()
    val definitions = Regex("(?m)^\\s*\\[([^]\\n]+)]\\s*:\\s*<?([^\\s>]+)>?[^\\n]*$")
    val refs = definitions.findAll(markdown).associate { it.groupValues[1].trim().lowercase() to it.groupValues[2] }
    var source = definitions.replace(markdown, "")
    source = Regex("!\\[([^]\\n]*)]\\[([^]\\n]*)]\\[([^]\\n]*)]").replace(source) { m ->
        val image = refs[m.groupValues[2].ifBlank { m.groupValues[1] }.lowercase()]
        val target = refs[m.groupValues[3].ifBlank { m.groupValues[1] }.lowercase()]
        if (image != null && target != null) "<a href=\"$target\"><img src=\"$image\" alt=\"${m.groupValues[1]}\"></a>" else m.value
    }
    source = Regex("(?<!!)\\[([^]\\n]+)]\\[([^]\\n]*)]").replace(source) { m ->
        val target = refs[m.groupValues[2].ifBlank { m.groupValues[1] }.lowercase()]
        if (target != null) "[${m.groupValues[1]}]($target)" else m.value
    }
    var inCode = false
    var inList = false
    var inTable = false
    val lines = source.replace("\r\n", "\n").lines()
    for ((lineIndex, raw) in lines.withIndex()) {
        val line = raw.trimEnd()
        val trimmed = line.trim()
        val separator = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$")
        val isTableRow = trimmed.startsWith("|") && trimmed.endsWith("|")
        if (!inCode && isTableRow && (inTable || (lineIndex + 1 < lines.size && separator.matches(lines[lineIndex + 1].trim())))) {
            if (separator.matches(trimmed)) continue
            if (!inTable) { if (inList) { out.append("</ul>"); inList = false }; out.append("<table>"); inTable = true }
            out.append("<tr>")
            trimmed.removePrefix("|").removeSuffix("|").split('|').forEach { cell ->
                out.append("<td>").append(inlineMarkdown(cell.trim())).append("</td>")
            }
            out.append("</tr>")
            continue
        }
        if (inTable) { out.append("</table>"); inTable = false }
        if (trimmed.startsWith("```")) {
            out.append(if (inCode) "</pre>" else "<pre>")
            inCode = !inCode
            continue
        }
        if (inCode) {
            out.append(escapeHtmlText(line)).append('\n')
            continue
        }
        if (trimmed.isEmpty()) {
            if (inList) {
                out.append("</ul>")
                inList = false
            }
            continue
        }
        val heading = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
        val bullet = Regex("^[-*+]\\s+(.*)$").find(trimmed)
        when {
            heading != null -> {
                if (inList) { out.append("</ul>"); inList = false }
                val level = heading.groupValues[1].length
                out.append("<h").append(level).append('>')
                    .append(inlineMarkdown(heading.groupValues[2]))
                    .append("</h").append(level).append('>')
            }

            bullet != null -> {
                if (!inList) {
                    out.append("<ul>")
                    inList = true
                }
                out.append("<li>").append(inlineMarkdown(bullet.groupValues[1])).append("</li>")
            }

            trimmed.startsWith(">") -> {
                if (inList) { out.append("</ul>"); inList = false }
                out.append("<blockquote>")
                    .append(inlineMarkdown(trimmed.removePrefix(">").trim()))
                    .append("</blockquote>")
            }

            trimmed.all { it == '-' || it == '*' || it == '_' } && trimmed.length >= 3 -> {
                if (inList) { out.append("</ul>"); inList = false }
                out.append("<hr/>")
            }

            else -> {
                if (inList) { out.append("</ul>"); inList = false }
                out.append("<p>").append(inlineMarkdown(trimmed)).append("</p>")
            }
        }
    }
    if (inTable) out.append("</table>")
    if (inList) out.append("</ul>")
    if (inCode) out.append("</pre>")
    return out.toString()
}

/** 行内语法：图片 / 链接 / 粗体 / 斜体 / 行内代码。 */
private fun inlineMarkdown(text: String): String {
    var s = text
    s = Regex("!\\[([^\\]]*)]\\(([^)\\s]+)[^)]*\\)").replace(s) { m ->
        val alt = m.groupValues[1]
        val url = m.groupValues[2]
        "<img src=\"" + url + "\" alt=\"" + alt + "\">"
    }
    s = Regex("\\[([^\\]]+)]\\(([^)\\s]+)[^)]*\\)").replace(s) { m ->
        val label = m.groupValues[1]
        val url = m.groupValues[2]
        "<a href=\"" + url + "\">" + label + "</a>"
    }
    s = Regex("\\*\\*([^*]+)\\*\\*").replace(s) { "<strong>" + it.groupValues[1] + "</strong>" }
    s = Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)").replace(s) { "<em>" + it.groupValues[1] + "</em>" }
    s = Regex("`([^`]+)`").replace(s) { "<code>" + it.groupValues[1] + "</code>" }
    return s
}

/**
 * README 里的链接 → 可在应用内渲染的 raw 文本地址。
 * 相对路径按当前仓库 main 分支补全；github.com/.../blob/... 转 raw.githubusercontent.com；
 * 其它外部链接返回 null（交给系统浏览器）。
 */
private fun resolveReadmeInternalUrl(pageUrl: String, readmeHtml: String, link: String): String? {
    if (link.isBlank()) return null
    val isDoc = Regex("\\.(md|markdown|txt)(#.*)?$", RegexOption.IGNORE_CASE).containsMatchIn(link)
    if (link.startsWith("http://", true) || link.startsWith("https://", true)) {
        if (!isDoc) return null
        if (link.contains("github.com/") && link.contains("/blob/")) {
            return link.replace("https://github.com/", "https://raw.githubusercontent.com/")
                .replace("/blob/", "/")
        }
        return link
    }
    if (!isDoc) return null
    // 相对路径：按仓库 raw 根 + README 所在目录补全
    val base = readmeRawBaseUrl(pageUrl).ifBlank { return null }
    val docDir = Regex("(?is)data-path=\"([^\"]+)\"").find(readmeHtml)
        ?.groupValues?.get(1)
        ?.substringBeforeLast('/', "")
        .orEmpty()
    val dir = if (docDir.isBlank()) "" else "$docDir/"
    val relative = link.removePrefix("./").removePrefix("/")
    return base + dir + relative
}

/** github.com/owner/repo → raw.githubusercontent.com/owner/repo/main/ */
private fun readmeRawBaseUrl(openLink: String): String {
    val repo = openLink.trimEnd('/').removeSuffix(".git")
    if (repo.isBlank()) return ""
    if (repo.contains("github.com/")) {
        return repo.replace("https://github.com/", "https://raw.githubusercontent.com/") + "/main/"
    }
    return ""
}

/** 详情页的轻量提示卡：加载失败时给一条可点的重试入口。 */
@Composable
private fun RetryHintCard(text: String, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(16.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** GitHub 仓库详情顶部：就是普通应用卡（图标 + 应用名 + 版本 + 下载按钮 + 已安装标记）。 */
@Composable
private fun GitHubRepoHeader(
    current: AppDetail,
    actionKind: AppActionKind,
    downloadState: DownloadState?,
    onAction: () -> Unit,
    onResumeDownload: () -> Unit,
    onInstallDownloaded: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    val app = current.app
    val installed = app.installedVersionCode > 0L
    val repoPath = (app.publisherName + "/" + app.displayName).trim('/')
    val actionText = when (actionKind) {
        AppActionKind.INSTALL -> "安装"
        AppActionKind.UPDATE -> "更新"
        AppActionKind.OPEN -> "打开"
        AppActionKind.RESERVE -> "预约"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                url = app.icon,
                contentDescription = repoPath,
                size = 52.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = repoPath,
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.summaryText.ifBlank { app.packageName },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "v" + app.versionName.ifBlank { "--" }.removePrefix("v"),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (installed) {
                        Spacer(Modifier.width(8.dp))
                        LsposedChip(
                            text = "已安装 " + app.installedVersionName.ifBlank { app.installedVersionCode.toString() },
                            highlight = false,
                        )
                    }
                    if (installed && app.installedVersionCode < app.versionCode) {
                        Spacer(Modifier.width(6.dp))
                        LsposedChip(text = "需要更新", highlight = true)
                    }
                }
            }
            AppActionButton(
                packageName = app.packageName,
                actionText = actionText,
                actionKind = actionKind,
                downloadState = downloadState,
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

/** Latest（绿框）/ Pre-release（黄框）。 */
@Composable
private fun ReleaseBadge(text: String, prerelease: Boolean) {
    val color = if (prerelease) {
        androidx.compose.ui.graphics.Color(0xFFB56C00)
    } else {
        MiuixTheme.colorScheme.primary
    }
    val shape = RoundedCornerShape(100.dp)
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = color,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, color, shape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private fun isPrereleaseTag(tag: String): Boolean {
    val t = tag.lowercase()
    return listOf("beta", "alpha", "rc", "nightly", "preview", "dev").any { t.contains(it) }
}

/** 版本页签：对齐 LSPosed Manager 的发布卡（版本号 / 日期 / What's Changed / 附件 / 下载按钮）。 */
@Composable
private fun LsposedModuleVersions(
    current: AppDetail,
    viewModel: AppDetailViewModel,
    showBadges: Boolean = false,
    actionText: String,
    actionKind: AppActionKind,
    downloadStateProvider: State<DownloadState?>,
    onAction: () -> Unit,
    onInstallDownloaded: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    val app = current.app
    val assets by viewModel.moduleAssets.collectAsStateWithLifecycle()
    val selectedAsset by viewModel.selectedAssetName.collectAsStateWithLifecycle()
    val assetName = selectedAsset ?: assets.firstOrNull()?.name ?: if (app.source == AppSource.GITHUB || app.source == AppSource.GITLAB) "--" else (app.packageName + ".apk")
    val assetSize = assets.firstOrNull { it.name == assetName }?.size ?: app.apkSize
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            // 页签行与卡片之间的间距比上面大一截：往上收 6dp 对齐
            .offset(y = (-6).dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "v" + app.versionName.ifBlank { "--" }.removePrefix("v"),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                if (showBadges) {
                    Spacer(Modifier.width(8.dp))
                    ReleaseBadge(text = "Latest", prerelease = false)
                } else if (showBadges) {
                    Spacer(Modifier.width(8.dp))
                }
                Spacer(Modifier.weight(1f))
                if (current.updateTime > 0L) {
                    val date = localDateOf(current.updateTime)
                    Text(
                        text = stringResource(
                            Res.string.date_full,
                            date.year,
                            date.month.number,
                            date.day,
                        ),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Text(
                text = app.versionCode.toString() + "-" + app.versionName,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            val translatedLatest = LocalTranslatedLatest.current
            val changeLogText = translatedLatest ?: current.changeLog
            if (changeLogText.isNotBlank()) {
                Text(
                    text = "What's Changed",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                )
                com.app.market.ui.component.ModuleReadmeText(
                    html = markdownToHtml(changeLogText),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.2f)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assetName,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatSize(assetSize).ifBlank { "--" },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                DetailActionButton(
                    packageName = app.packageName,
                    actionText = actionText,
                    actionKind = actionKind,
                    downloadStateProvider = downloadStateProvider,
                    enabled = actionKind == AppActionKind.OPEN || !app.isDownloadBlocked(),
                    onAction = onAction,
                    onResumeDownload = onAction,
                    onInstallDownloaded = onInstallDownloaded,
                    onCancel = onCancel,
                    minHeight = 32.dp,
                )
            }
            if (assets.size > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "附件",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f),
                    )
                    com.app.market.ui.widget.MiuixDropdown(
                        items = assets.map { it.name + " · " + formatSize(it.size) },
                        selectedIndex = assets.indexOfFirst { it.name == assetName }.coerceAtLeast(0),
                        onSelectedIndexChange = { index -> viewModel.selectModuleAsset(assets[index].name) },
                    )
                }
            }
        }
    }
}

/** 历史版本：默认折叠，点「显示历史版本」展开成紧凑卡片。 */
@Composable
private fun LsposedReleaseHistory(
    viewModel: AppDetailViewModel,
    showBadges: Boolean = false,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val releases by viewModel.moduleReleases.collectAsStateWithLifecycle()
    val releasesLoading by viewModel.moduleReleasesLoading.collectAsStateWithLifecycle()
    val older = releases.drop(1)
    if (older.isEmpty()) {
        if (releasesLoading) return
        if (showBadges) {
            RetryHintCard(
                text = if (releases.isEmpty()) "历史版本没取到（GitHub 接口偶发失败）· 点此重试" else "暂无更早版本",
                onClick = if (releases.isEmpty()) viewModel::retryLoad else null,
            )
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 与上一张版本卡的 20dp 间距压一截：按钮太靠下
                .offset(y = (-10).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (expanded) "收起历史版本" else "显示历史版本",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ReleaseHistoryCard(
    app: MarketAppInfo,
    release: com.app.market.domain.model.market.ModuleRelease,
    viewModel: AppDetailViewModel,
    showBadges: Boolean,
) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "v" + release.name.ifBlank { release.tagName }.removePrefix("v"),
                                style = MiuixTheme.textStyles.title3,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            if (showBadges) {
                                Spacer(Modifier.width(8.dp))
                                ReleaseBadge(
                                    text = if (isPrereleaseTag(release.tagName.ifBlank { release.name })) {
                                        "Pre-release"
                                    } else {
                                        "Latest"
                                    },
                                    prerelease = isPrereleaseTag(release.tagName.ifBlank { release.name }),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (release.publishedAt > 0L) {
                                val date = localDateOf(release.publishedAt)
                                Text(
                                    text = stringResource(
                                        Res.string.date_full,
                                        date.year,
                                        date.month.number,
                                        date.day,
                                    ),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        val releaseLog = LocalTranslatedLogs.current[release.tagName.ifBlank { release.name }]
                            ?: release.changeLog
                        if (releaseLog.isNotBlank()) {
                            com.app.market.ui.component.ModuleReadmeText(
                                html = remember(releaseLog) { markdownToHtml(releaseLog) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (release.assets.isNotEmpty()) {
                            // 与最新版卡片同款分割线：正文与附件行之间隔开
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .height(1.dp)
                                    .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.2f)),
                            )
                        }
                        release.assets.forEach { asset ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = asset.name,
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = formatSize(asset.size).ifBlank { "--" },
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                AppButton(
                                    text = stringResource(Res.string.download),
                                    onClick = {
                                        viewModel.downloadReleaseAsset(
                                            app = app,
                                            versionName = release.name.ifBlank { release.tagName },
                                            versionCode = release.versionCode,
                                            asset = asset,
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                    cornerRadius = 100.dp,
                                    minHeight = 32.dp,
                                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    modifier = Modifier.width(ProgressButtonWidth),
                                )
                            }
                        }
                    }
                }
}

/** 信息页签：对齐 LSPosed Manager（主页链接 + 协作者）。 */
@Composable
private fun LsposedModuleInfo(
    detail: AppDetail,
    onOpenUrl: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (detail.homepageUrl.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                cornerRadius = 16.dp,
                insideMargin = PaddingValues(16.dp),
                onClick = { onOpenUrl(detail.homepageUrl) },
                showIndication = true,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "主页",
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = detail.homepageUrl,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (detail.collaborators.isNotEmpty()) {
            Text(
                text = "协作者",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                cornerRadius = 16.dp,
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    detail.collaborators.forEach { person ->
                        Column {
                            Text(
                                text = person.name.ifBlank { person.login },
                                style = MiuixTheme.textStyles.title3,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            if (person.login.isNotBlank()) {
                                Text(
                                    text = person.login,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** README 里 .md 链接的应用内查看器：直接替换当前页面内容，返回键/返回箭头退回 README。 */
@Composable
private fun LsposedReadmeDoc(
    url: String,
    viewModel: AppDetailViewModel,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit = {},
) {
    var text by remember(url) { mutableStateOf<String?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        text = viewModel.cachedDocText(url)
        val loaded = viewModel.loadDocText(url)
        if (loaded == null) failed = true else if (loaded != text) text = loaded
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .clickable { onBack() }
                .padding(vertical = 4.dp, horizontal = 4.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Back,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "返回 · " + url.substringAfterLast('/'),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
            )
        }
        val loaded = text
        if (loaded == null || !url.isMarkdownDoc()) {
            Text(
                text = loaded ?: if (failed) "无法加载该文档" else "加载中…",
                style = MiuixTheme.textStyles.body2.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp,
                ),
                color = MiuixTheme.colorScheme.onSurface,
            )
        } else {
            // .md 原文转成 HTML 再交给 README 渲染器（和 GitHub 网页一样的排版）
            val html = remember(loaded) { markdownToHtml(loaded) }
            com.app.market.ui.component.ModuleReadmeText(
                html = html,
                onOpenLink = onOpenLink,
                linkBaseUrl = url.substringBeforeLast('/') + "/",
            )
        }
    }
}