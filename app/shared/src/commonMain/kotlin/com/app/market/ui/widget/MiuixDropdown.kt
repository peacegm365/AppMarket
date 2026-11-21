package com.app.market.ui.widget

// 照搬 ArpCut 的 MiuixDropdown（原自 miuix-kotlin-multiplatform contributors，Apache-2.0）
// SmallTitle 风格的排序下拉：粗体文字 + 上下双箭头，点击弹出选项菜单，选中项带勾。
// 弹出面板照抄 miuix-glass 官方 GlassDropdownPreference 的模式（InstallerX Revived 同款 OS4 交互）：
// MiuixPopupUtils.PopupLayout 窗口级承载 + GlassDropdownPopup（GlassMotion 弹出动画、value 淡出）。
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDropdownPopup
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.glassPopupAnchorValue
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.app.market.ui.theme.LocalOpaqueSurface
import com.app.market.ui.theme.LocalAppBackgroundImage
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils

/** 其他页面（应用详情/下载页）下拉弹出沿用的定位器：锚点左对齐、下方展开、贴边收敛。 */
internal val DropdownWithStartMarginProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowBounds: IntRect,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
        popupMargin: IntRect,
        alignment: PopupPositionProvider.Align,
    ): IntOffset {
        val offsetX = (if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left + 12.dp.value
        } else {
            anchorBounds.right - popupContentSize.width - 12.dp.value
        }).toInt()
        val offsetY = if (windowBounds.bottom - anchorBounds.bottom > popupContentSize.height) {
            anchorBounds.bottom + popupMargin.bottom
        } else if (anchorBounds.top - windowBounds.top > popupContentSize.height) {
            anchorBounds.top - popupContentSize.height - popupMargin.top
        } else {
            anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2
        }
        return IntOffset(
            x = offsetX.coerceIn(
                windowBounds.left,
                (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(windowBounds.left),
            ),
            y = offsetY.coerceIn(
                (windowBounds.top + popupMargin.top).coerceAtMost(
                    windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
                ),
                windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
            ),
        )
    }

    override fun getMargins(): PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
}

@Composable
fun MiuixDropdown(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "无选项",
) {
    val isDropdownExpanded = remember { mutableStateOf(false) }
    val mounted = remember { mutableStateOf(false) }
    val anchor = rememberGlassPopupAnchor()
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    val hapticFeedback = LocalHapticFeedback.current
    val itemsNotEmpty = items.isNotEmpty()
    val actualEnabled = enabled && itemsNotEmpty
    // 面板退场动画完成后再卸载弹出层（GlassDropdownPopup 要求组合保持到退场结束）
    LaunchedEffect(isDropdownExpanded.value) {
        if (!isDropdownExpanded.value && mounted.value) {
            delay(700)
            mounted.value = false
        }
    }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .onGloballyPositioned { rowBounds = it.boundsInWindow() }
                .glassPopupAnchorValue(anchor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = actualEnabled,
                    onClick = {
                        if (!isDropdownExpanded.value) mounted.value = true
                        isDropdownExpanded.value = !isDropdownExpanded.value
                        if (isDropdownExpanded.value) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }
                    },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val textColor = if (actualEnabled) {
                MiuixTheme.colorScheme.onBackgroundVariant
            } else {
                MiuixTheme.colorScheme.disabledOnSecondaryVariant
            }
            Text(
                text = if (itemsNotEmpty) items[selectedIndex] else placeholder,
                fontSize = MiuixTheme.textStyles.subtitle.fontSize,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            val iconColor = if (actualEnabled) {
                MiuixTheme.colorScheme.onSurfaceVariantActions
            } else {
                MiuixTheme.colorScheme.disabledOnSecondaryVariant
            }
            Image(
                modifier = Modifier.size(10.dp, 16.dp),
                imageVector = MiuixIcons.Basic.ArrowUpDown,
                colorFilter = ColorFilter.tint(iconColor),
                contentDescription = "展开选项",
            )
        }
        if (mounted.value) {
            val popupColors = if (LocalAppBackgroundImage.current != null) {
                MiuixTheme.colorScheme.copy(surface = LocalOpaqueSurface.current, background = LocalOpaqueSurface.current)
            } else MiuixTheme.colorScheme
            MiuixTheme(colors = popupColors) {
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
                        show = isDropdownExpanded.value,
                        onDismissRequest = { isDropdownExpanded.value = false },
                        anchorBounds = rowBounds,
                        backdrop = null,
                        anchor = anchor,
                    ) {
                        items.forEachIndexed { index, text ->
                            GlassPopupItem(
                                text = text,
                                selected = selectedIndex == index,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onSelectedIndexChange(index)
                                    isDropdownExpanded.value = false
                                },
                            )
                        }
            }
            }
        }
    }
}
    }
}