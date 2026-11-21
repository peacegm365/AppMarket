package com.app.market.ui.component

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.delay
import com.app.market.ui.theme.PopupTheme
import top.yukonga.miuix.kmp.glass.GlassDropdownPopup
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import com.app.market.domain.model.market.AppSource
import com.app.market.ui.util.appSourceLabel
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup
import kotlin.math.roundToInt

/**
 * 更新来源优先级选择框。
 *
 * 完全套用 miuix 下拉的组成（源码 popup/WindowDropdownPopup.kt）：
 * `WindowListPopup(Align.End)` + `ListPopupColumn` + `DropdownImpl` 同构行。
 * 行布局/字号/字重/颜色/内边距逐项取自 basic/Dropdown.kt 的 `DropdownImpl`，
 * 仅把尾部 Check 图标槽位换成 ≡ 拖动手柄；整行长按、或直接按住 ≡ 均可拖动排序。
 * 关闭即保存（与下拉选择即生效一致）。
 */
/** 与其他玻璃下拉同一套外壳：窗口级承载（无全局变灰蒙层）+ GlassDropdownPopup 弹出动画，锚定到设置行。 */
@Composable
private fun GlassWindowListPopup(
    show: Boolean,
    anchorBounds: Rect,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val mounted = remember { mutableStateOf(false) }
    val anchor = rememberGlassPopupAnchor()
    LaunchedEffect(show) {
        if (show) {
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
                    show = show,
                    onDismissRequest = onDismissRequest,
                    anchorBounds = anchorBounds,
                    backdrop = null,
                    anchor = anchor,
                ) {
                    content()
                }
            }
        }
    }
    }
}

@Composable
fun UpdateSourcePriorityPicker(
    show: Boolean,
    order: List<AppSource>,
    anchorBounds: Rect,
    onCommit: (List<AppSource>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val localOrder = remember(show) { mutableStateListOf<AppSource>().apply { addAll(order) } }
    var draggingSource by remember(show) { mutableStateOf<AppSource?>(null) }
    var dragOffset by remember(show) { mutableFloatStateOf(0f) }
    var rowHeightPx by remember(show) { mutableFloatStateOf(0f) }

    // 拖动位移累计；越过一行高度即与相邻行交换
    fun applyDrag(deltaY: Float) {
        dragOffset += deltaY
        val source = draggingSource ?: return
        val current = localOrder.indexOf(source)
        if (current < 0) return
        if (dragOffset > rowHeightPx && current < localOrder.lastIndex) {
            localOrder[current] = localOrder[current + 1]
            localOrder[current + 1] = source
            dragOffset -= rowHeightPx
        } else if (dragOffset < -rowHeightPx && current > 0) {
            localOrder[current] = localOrder[current - 1]
            localOrder[current - 1] = source
            dragOffset += rowHeightPx
        }
    }

    fun startDrag(source: AppSource) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        draggingSource = source
        dragOffset = 0f
    }

    fun endDrag() {
        draggingSource = null
        dragOffset = 0f
    }

    // 与 WindowDropdownPopup 的组装逐项一致（默认锚点/默认 200dp 最小宽度/Align.End）
    GlassWindowListPopup(
        show = show,
        anchorBounds = anchorBounds,
        onDismissRequest = {
            onCommit(localOrder.toList())
            onDismissRequest()
        },
    ) {
        ListPopupColumn {
            localOrder.forEach { source ->
                key(source) {
                    val dragging = draggingSource == source
                    val colors = DropdownDefaults.dropdownColors()
                    val topPadding =
                        if (source == localOrder.first()) DropdownDefaults.FirstLastVerticalPadding
                        else DropdownDefaults.MiddleVerticalPadding
                    val bottomPadding =
                        if (source == localOrder.last()) DropdownDefaults.FirstLastVerticalPadding
                        else DropdownDefaults.MiddleVerticalPadding
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .onSizeChanged { rowHeightPx = it.height.toFloat() }
                            .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
                            .pointerInput(source) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { startDrag(source) },
                                    onDragEnd = { endDrag() },
                                    onDragCancel = { endDrag() },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        applyDrag(amount.y)
                                    },
                                )
                            }
                            .drawBehind { drawRect(colors.containerColor) }
                            .padding(horizontal = DropdownDefaults.InsideHorizontalPadding)
                            .padding(top = topPadding, bottom = bottomPadding),
                    ) {
                        // 文本块与 DropdownImpl 的 innerRow 一致（限宽 216dp）
                        Row(
                            modifier = Modifier.widthIn(max = DropdownDefaults.MaxItemTextWidth),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            Text(
                                text = appSourceLabel(source),
                                fontSize = MiuixTheme.textStyles.body1.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = colors.contentColor,
                            )
                        }
                        // 尾部 ≡ 手柄：与 DropdownImpl 的 Check 图标同槽位
                        //（padding(start=12dp) + size(20dp)），按住即可拖动（无需长按）
                        Box(
                            modifier = Modifier
                                .padding(start = DropdownDefaults.CheckIconStartPadding)
                                .size(DropdownDefaults.CheckIconSize)
                                .pointerInput(source) {
                                    detectDragGestures(
                                        onDragStart = { startDrag(source) },
                                        onDragEnd = { endDrag() },
                                        onDragCancel = { endDrag() },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            applyDrag(amount.y)
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "≡",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = colors.summaryColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
/**
 * 更新来源多选面板。
 *
 * 与 [UpdateSourcePriorityPicker] 共用同一套玻璃弹层外壳与行样式，
 * 只是尾部的拖动柄换成勾选标记：点一行即切换该来源是否参与更新检查，关闭面板时提交。
 */
@Composable
fun UpdateSourceSelectionPicker(
    show: Boolean,
    options: List<AppSource>,
    selected: Set<AppSource>,
    anchorBounds: Rect,
    onCommit: (Set<AppSource>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val localSelected = remember(show) { mutableStateListOf<AppSource>().apply { addAll(selected) } }
    GlassWindowListPopup(
        show = show,
        anchorBounds = anchorBounds,
        onDismissRequest = {
            // 至少保留一个来源，否则更新检查没有可跑的源
            onCommit(localSelected.toSet().ifEmpty { selected })
            onDismissRequest()
        },
    ) {
        ListPopupColumn {
            options.forEach { source ->
                key(source) {
                    val checked = source in localSelected
                    val colors = DropdownDefaults.dropdownColors()
                    val topPadding =
                        if (source == options.first()) DropdownDefaults.FirstLastVerticalPadding
                        else DropdownDefaults.MiddleVerticalPadding
                    val bottomPadding =
                        if (source == options.last()) DropdownDefaults.FirstLastVerticalPadding
                        else DropdownDefaults.MiddleVerticalPadding
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (checked) localSelected.remove(source) else localSelected.add(source)
                            }
                            .drawBehind { drawRect(colors.containerColor) }
                            .padding(horizontal = DropdownDefaults.InsideHorizontalPadding)
                            .padding(top = topPadding, bottom = bottomPadding),
                    ) {
                        Row(
                            modifier = Modifier.widthIn(max = DropdownDefaults.MaxItemTextWidth),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            // 字号/字重与原生下拉项一致（DropdownImpl 用 headline1 + Medium），
                            // 选中项文字取主题色，和系统下拉的选中态完全一样
                            Text(
                                text = appSourceLabel(source),
                                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = if (checked) MiuixTheme.colorScheme.primary else colors.contentColor,
                            )
                        }
                        // 尾部勾选槽位与 DropdownImpl 的 Check 图标同规格：
                        // 用主题色画勾（两条圆头线），避免「✓」字体在不同机型上大小/基线乱跳
                        Box(
                            modifier = Modifier
                                .padding(start = DropdownDefaults.CheckIconStartPadding)
                                .size(DropdownDefaults.CheckIconSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (checked) {
                                val checkColor = MiuixTheme.colorScheme.primary
                                androidx.compose.foundation.Canvas(Modifier.size(DropdownDefaults.CheckIconSize)) {
                                    val w = size.width
                                    val h = size.height
                                    val stroke = w * 0.11f
                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(w * 0.20f, h * 0.52f)
                                        lineTo(w * 0.42f, h * 0.74f)
                                        lineTo(w * 0.80f, h * 0.28f)
                                    }
                                    drawPath(
                                        path = path,
                                        color = checkColor,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = stroke,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                        ),
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