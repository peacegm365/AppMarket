package com.app.market.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.market.domain.model.download.DownloadPhase
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.repository.DownloadRepository
import com.app.market.resources.Res
import com.app.market.resources.cancel
import com.app.market.resources.cancel_download_message
import com.app.market.resources.cancel_download_title
import com.app.market.resources.confirm
import com.app.market.resources.download_paused
import com.app.market.resources.downloading_short
import com.app.market.resources.install
import com.app.market.resources.download
import com.app.market.resources.installing_short
import com.app.market.ui.model.AppActionKind
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.app.market.ui.theme.PopupTheme
import top.yukonga.miuix.kmp.window.WindowDialog

// App-wide replacement for miuix Button/TextButton: same API surface, but labels render at
// body2 (14.sp) instead of the stock 17.sp button style, shrinking down to 8.sp before
// ellipsizing when the button is narrower than its text.
private val AppButtonMinFontSize = 8.sp

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
) {
    AppButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = cornerRadius,
        minWidth = minWidth,
        minHeight = minHeight,
        colors = colors,
        insideMargin = insideMargin,
    ) {
        AppButtonText(text)
    }
}
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = cornerRadius,
        minWidth = minWidth,
        minHeight = minHeight,
        colors = colors,
        insideMargin = insideMargin,
        content = content,
    )
}

@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    colors: TextButtonColors = ButtonDefaults.textButtonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    textStyle: TextStyle? = null,
) {
    val mappedColors = remember(colors) {
        ButtonColors(
            color = colors.color,
            disabledColor = colors.disabledColor,
            contentColor = colors.textColor,
            disabledContentColor = colors.disabledTextColor,
        )
    }
    AppButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = cornerRadius,
        minWidth = minWidth,
        minHeight = minHeight,
        colors = mappedColors,
        insideMargin = insideMargin,
    ) {
        AppButtonText(text, textStyle = textStyle)
    }
}

/** Standalone button label for custom [AppButton] content; color defaults to the button's content color. */
@Composable
fun AppButtonText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    textAlign: TextAlign = TextAlign.Start,
    autoSize: Boolean = true,
    // 百分比这类文本宁可溢出也不压成「96…」，所以允许调用方改成 Visible
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textStyle: TextStyle? = null,
) {
    val style = textStyle ?: MiuixTheme.textStyles.body2
    val maxFontSize = style.fontSize.takeUnless { it == TextUnit.Unspecified } ?: 14.sp
    val minFontSize = if (maxFontSize.value < AppButtonMinFontSize.value) maxFontSize else AppButtonMinFontSize
    Text(
        text = text,
        modifier = modifier,
        color = color ?: Color.Unspecified,
        textAlign = textAlign,
        // 自适应字号只用于「长文案塞进固定按钮」；百分比这类逐帧变化的文本必须关掉，
        // 否则每次数字变化都可能触发缩字号，字号来回跳变成抖动
        autoSize = if (autoSize) {
            DescendingSpAutoSize(minFontSize = minFontSize, maxFontSize = maxFontSize)
        } else {
            FixedSpAutoSize(maxFontSize)
        },
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = overflow,
    )
}

/** 固定字号（不缩放）：用于逐帧变化的文本（百分比等），避免 autoSize 反复重算造成字号抖动。 */
private data class FixedSpAutoSize(
    val size: TextUnit,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(
        constraints: Constraints,
        text: AnnotatedString,
    ): TextUnit = size
}

private data class DescendingSpAutoSize(
    val minFontSize: TextUnit,
    val maxFontSize: TextUnit,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(
        constraints: Constraints,
        text: AnnotatedString,
    ): TextUnit {
        var currentSp = maxFontSize.value
        while (currentSp > minFontSize.value) {
            val result = performLayout(constraints, text, currentSp.sp)
            val overflowed = result.hasVisualOverflow ||
                    (result.lineCount > 0 && result.isLineEllipsized(0))
            if (!overflowed) return currentSp.sp
            currentSp -= 0.5f
        }
        return minFontSize
    }
}

// Compact action-button metrics, matching the payload_extract_gui look.
private val ActionButtonMinHeight = 32.dp
private val ActionButtonInsideMargin = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
private val ActiveDownloadPhases = setOf(
    DownloadPhase.QUEUED,
    DownloadPhase.DOWNLOADING,
    DownloadPhase.PAUSED,
    DownloadPhase.INSTALLING,
    DownloadPhase.AWAITING_USER_ACTION,
)
private val CancelableDownloadPhases = setOf(
    DownloadPhase.QUEUED,
    DownloadPhase.DOWNLOADING,
    DownloadPhase.PAUSED,
    DownloadPhase.AWAITING_USER_ACTION,
)

@Composable
fun AppCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = appTintedButtonColors(),
    borderColor: Color? = null,
    minHeight: Dp = ActionButtonMinHeight,
    insideMargin: PaddingValues = ActionButtonInsideMargin,
) {
    AppButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        cornerRadius = 100.dp,
        minHeight = minHeight,
        insideMargin = insideMargin,
        modifier = if (borderColor != null) {
            modifier.border(1.dp, borderColor, RoundedCornerShape(100.dp))
        } else {
            modifier
        },
    )
}

@Composable
private fun tintedContainerColor(): Color =
    MiuixTheme.colorScheme.primary.copy(alpha = if (isSystemInDarkTheme()) 0.2f else 0.08f)

@Composable
private fun appTintedButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    color = tintedContainerColor(),
    contentColor = MiuixTheme.colorScheme.primary,
)

@Composable
fun AppActionButton(
    packageName: String,
    actionText: String,
    actionKind: AppActionKind,
    downloadState: DownloadState?,
    preparingDownload: Boolean = false,
    onCancelPreparing: () -> Unit = {},
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    minHeight: Dp = ActionButtonMinHeight,
    insideMargin: PaddingValues = ActionButtonInsideMargin,
    onAction: () -> Unit,
    onResumeDownload: () -> Unit,
    onInstallDownloaded: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    // 无账号体系可执行预约,按钮仅作状态展示。
    if (actionKind == AppActionKind.RESERVE) {
        AppCompactButton(
            text = actionText,
            onClick = {},
            enabled = false,
            modifier = modifier,
            minHeight = minHeight,
            insideMargin = insideMargin,
        )
        return
    }

    val visibleDownloadState = if (preparingDownload) DownloadState(
        appId = 0L, packageName = packageName, displayName = "", progress = null,
        phase = DownloadPhase.QUEUED,
    ) else downloadState
    val activePhase = visibleDownloadState?.phase in ActiveDownloadPhases
    // 安装中 / 等待安装：没有进度可展示，不走进度按钮，落回下面的纯文字按钮——
    // 与「下载」「安装」完全同框，点完只把字换成「安装中」，框大小位置都不跳
    val installing = visibleDownloadState != null && (
            visibleDownloadState.phase == DownloadPhase.INSTALLING ||
                    visibleDownloadState.phase == DownloadPhase.AWAITING_USER_ACTION)
    // Active transfer: the button itself becomes the progress indicator.
    if (visibleDownloadState != null && activePhase && !installing) {
        val downloads = koinInject<DownloadRepository>()
        var removeConfirmVisible by remember { mutableStateOf(false) }
        val percent = visibleDownloadState.progress
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            var progressButtonWidthPx by remember { mutableIntStateOf(0) }
            var buttonHeightPx by remember { mutableIntStateOf(0) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ActionCancelGap, Alignment.End),
            ) {
                    // 进度控件左侧：取消（移除任务）入口，需二次确认；无背景、图标小而灰，靠拢进度条
                    // 用 AnimatedVisibility 让「下载中 ↔ 安装中」切换时宽度变化走动画，按钮不瞬跳
                    AnimatedVisibility(
                        visible = !installing && visibleDownloadState.phase in CancelableDownloadPhases,
                        enter = fadeIn(tween(200)) + expandHorizontally(tween(200)),
                        exit = fadeOut(tween(150)) + shrinkHorizontally(tween(150)),
                    ) {
                        IconButton(
                            onClick = { removeConfirmVisible = true },
                            // 触控范围保持 28dp，仅视觉尺寸缩小
                            modifier = Modifier.size(ActionCancelTouchSize),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Close,
                                contentDescription = stringResource(Res.string.cancel_download_title),
                                tint = AppCancelGray,
                                modifier = Modifier.size(ActionCancelIconSize),
                            )
                        }
                    }
                    DownloadProgressButton(
                        label = when (visibleDownloadState.phase) {
                            DownloadPhase.PAUSED -> "继续"
                            DownloadPhase.INSTALLING -> stringResource(Res.string.installing_short)
                            DownloadPhase.AWAITING_USER_ACTION -> stringResource(Res.string.installing_short)
                            else -> stringResource(Res.string.downloading_short)
                        },
                        percentMode = visibleDownloadState.phase == DownloadPhase.DOWNLOADING && percent != null,
                        // 不再固定宽度：按钮尺寸与「更新」按钮一致，「80%」「100%」「安装中」都不会把按钮撑大
                        fixedLabelWidth = null,
                        fraction = if (installing) 1f else (percent ?: 0f).coerceIn(0f, 100f) / 100f,
                        onClick = {
                            if (installing) {
                                // 安装中无操作：进度条不可点击
                            } else if (visibleDownloadState.isPaused) {
                                onResumeDownload()
                            } else if (visibleDownloadState.phase in CancelableDownloadPhases) {
                                if (preparingDownload) onCancelPreparing() else onCancel(packageName)
                            }
                        },
                        progressColor = if (actionKind == AppActionKind.INSTALL) AppInstallGreen else MiuixTheme.colorScheme.primary,
                        modifier = Modifier.onSizeChanged {
                            progressButtonWidthPx = it.width
                            buttonHeightPx = it.height
                        },
                        minHeight = minHeight,
                        insideMargin = insideMargin,
                    )
                }
            // 速度只画一处：按钮正下方，跟着按钮宽度居中，不再用 Popup（Popup 锚点算错时会飘到屏幕中间）
            if (visibleDownloadState.phase == DownloadPhase.DOWNLOADING) {
                Text(
                    text = if (visibleDownloadState.speedBps > 0L) formatSpeed(visibleDownloadState.speedBps) else "0 B/S",
                    modifier = Modifier.widthIn(min = ProgressButtonWidth),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PopupTheme { WindowDialog(
            show = removeConfirmVisible,
            title = stringResource(Res.string.cancel_download_title),
            onDismissRequest = { removeConfirmVisible = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.cancel_download_message),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    AppTextButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(Res.string.cancel),
                        onClick = { removeConfirmVisible = false },
                    )
                    Spacer(Modifier.width(16.dp))
                    AppTextButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(Res.string.confirm),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            removeConfirmVisible = false
                            if (preparingDownload) onCancelPreparing() else downloads.clear(packageName)
                        },
                    )
                }
            }
        } }
        return
    }

    // 安装已经成功（actionKind 变成 OPEN）时，残留的「下载完成待安装」状态必须让位：
    // 否则按钮会一直停在「安装」，用户手快点两下就会重复触发安装器。
    val pendingInstall = downloadState?.isComplete == true && actionKind != AppActionKind.OPEN
    val buttonText = when {
        // 安装中：同款按钮只换字（「等待安装完成」文案已废弃，一律显示安装中）
        installing -> stringResource(Res.string.installing_short)
        // Downloaded and waiting for the user to install it.
        pendingInstall -> stringResource(Res.string.install)
        // 「安装」入口改成「下载」：点击后下载并自动安装
        actionKind == AppActionKind.INSTALL -> stringResource(Res.string.download)
        else -> actionText
    }
    val click: () -> Unit = when {
        installing -> ({ })
        pendingInstall -> ({ onInstallDownloaded(packageName) })
        else -> onAction
    }
    // "Open" (already installed) keeps the neutral look; install/更新 use outline style
    // (colored border + colored text, no fill): install green, update primary blue.
    val isInstallAction = actionKind == AppActionKind.INSTALL || pendingInstall
    val neutral = actionKind == AppActionKind.OPEN
    val outlineColor = when {
        isInstallAction -> AppInstallGreen
        actionKind == AppActionKind.UPDATE -> MiuixTheme.colorScheme.primary
        else -> null
    }
    val colors = when {
        neutral -> ButtonDefaults.buttonColors(color = Color.Transparent)
        outlineColor != null -> ButtonDefaults.buttonColors(
            color = Color.Transparent,
            contentColor = outlineColor,
        )
        else -> ButtonDefaults.buttonColorsPrimary()
    }
    AppCompactButton(
        text = buttonText,
        onClick = click,
        enabled = enabled,
        // 与进度按钮同宽：「下载/安装/安装中」和「25%」的框完全一致，切换只换字不换框
        modifier = modifier.width(ProgressButtonWidth),
        colors = colors,
        // 「打开」外圈灰色细边框、无背景；安装绿框、更新蓝框
        borderColor = if (neutral) AppOpenBorderGray else outlineColor,
        minHeight = minHeight,
        insideMargin = insideMargin,
    )
}

/**
 * Download button whose solid-primary fill grows left -> right with [fraction] over a translucent
 * base. The percentage label is rendered twice — once in the base-contrast color, once in the
 * fill-contrast color clipped to the filled region — so the text auto-splits between black/white
 * at the moving fill boundary. The fast-changing [fraction] is read only inside draw blocks.
 */
@Composable
private fun DownloadProgressButton(
    label: String,
    fraction: Float,
    percentMode: Boolean = false,
    fixedLabelWidth: Dp? = null,
    progressColor: Color = MiuixTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    minHeight: Dp = ActionButtonMinHeight,
    insideMargin: PaddingValues = ActionButtonInsideMargin,
    onClick: () -> Unit,
) {
    val base = progressColor.copy(alpha = if (isSystemInDarkTheme()) 0.2f else 0.08f)
    val baseTextColor = contrastColor(base.compositeOver(MiuixTheme.colorScheme.surface), progressColor)
    val fillTextColor = contrastColor(progressColor)
    val targetFraction = fraction.coerceIn(0f, 1f)
    // 过渡动画：条与数字共用同一动画值，平滑且永远同步
    val animatedFraction = animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 300),
        label = "downloadFill",
    )
    val shownFraction = animatedFraction.value
    Box(
        modifier = modifier
            // 宽度统一写死：「下载中」「继续」「25%」同框，状态切换不跳；
            // 百分比数字抖动（9% vs 100%）也被固定宽度吸收
            .then(Modifier.width(ProgressButtonWidth))
            .widthIn(min = ButtonDefaults.MinWidth)
            .heightIn(min = minHeight)
            .squircleSurface(color = base, cornerRadius = 100.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Solid fill (draw phase reads the animated fraction).
        Box(
            Modifier.matchParentSize().drawBehind {
                val filledWidth = size.width * shownFraction
                if (filledWidth > 0f) drawRect(
                    color = progressColor,
                    size = Size(filledWidth, size.height)
                )
            },
        )
        // 数字与条共用同一个动画值渲染，快下载时百分比与进度条永远同步
        val displayLabel = if (percentMode) {
            // 显示取整、进度条跟小数走：数字 45% 时条在 45.7% 位置
            "${(shownFraction * 100f).toInt().coerceIn(0, 100)}%"
        } else {
            label
        }
        // Base label — visible over the translucent (unfilled) area. Size-defining child.
        AppButtonText(
            text = displayLabel,
            color = baseTextColor,
            // 固定宽度时文字居中，避免百分比数字向左偏移
            textAlign = if (fixedLabelWidth != null) TextAlign.Center else TextAlign.Start,
            // 百分比逐帧变化：固定字号，避免 autoSize 反复缩放造成抖动
            autoSize = !percentMode,
            // 宽度已经由按钮外框固定，文字不再限宽，宁可溢出也不省略成「96…」
            overflow = if (percentMode) TextOverflow.Visible else TextOverflow.Ellipsis,
            modifier = Modifier.padding(insideMargin),
        )
        // Same label in the fill-contrast color, clipped to the filled region so it covers the
        // base label only where the fill has reached.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    clipRect(right = size.width * shownFraction) {
                        this@drawWithContent.drawContent()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AppButtonText(
                text = displayLabel,
                color = fillTextColor,
                textAlign = if (fixedLabelWidth != null) TextAlign.Center else TextAlign.Start,
                autoSize = !percentMode,
                overflow = if (percentMode) TextOverflow.Visible else TextOverflow.Ellipsis,
                modifier = Modifier.padding(insideMargin),
            )
}
    }
}
private val ProgressLabelFixedWidth = 64.dp

/** 进度按钮固定宽度：放得下「100%」，且不会随数字变化抖动。 */
internal val ProgressButtonWidth = 68.dp
/** 取消（✕）入口：视觉尺寸与颜色收紧，触控范围保持不变。 */
private val ActionCancelIconSize = 14.dp
private val ActionCancelTouchSize = 28.dp
private val ActionCancelGap = 2.dp
private val AppCancelGray = Color(0xFF9E9E9E)
/** 「安装」按钮的实心绿（更新等其余操作保持主色蓝）。 */
private val AppInstallGreen = Color(0xFF22C55E)

/** 速度行定位：贴着锚点（按钮行）右下角正下方；滚动/布局变化时由 Popup 自动重算。 */
private class SpeedBelowAnchorProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        anchorBounds.right - popupContentSize.width,
        anchorBounds.bottom,
    )
}
/** 「打开」按钮的灰色描边。 */
private val AppOpenBorderGray = Color(0xFF9E9E9E)

/** 字节/秒 → "1.2 MB/S" 短文本；commonMain 无 String.format，整数截断保留一位小数。 */
fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1_000_000 -> "${bytesPerSecond / 100_000 / 10.0} MB/S"
    bytesPerSecond >= 1_000 -> "${bytesPerSecond / 100 / 10.0} KB/S"
    else -> "$bytesPerSecond B/S"
}

/** Black or white, whichever contrasts the given background luminance; light bg uses [lightForeground]. */
@Composable
private fun contrastColor(background: Color, lightForeground: Color = MiuixTheme.colorScheme.primary): Color =
    if (background.luminance() > 0.5f) lightForeground else Color.White