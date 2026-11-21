package com.app.market.ui.component

// 交互照抄 InstallerX Revived 的 InstallerMiuixSpinnerPreference（GPL-3.0，
// https://github.com/wxxsfxyzm/InstallerX-Revived）的 OS4 分支：
// 「下拉选择设置行」——点击设置行在原位展开玻璃选项面板（GlassDropdownPreference，
// miuix-glass 自带 OS4 GlassMotion 弹出动画、value 淡出、选中打勾）。
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import com.app.market.ui.theme.PopupTheme
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 通用「下拉选择设置行」。
 *
 * @param items 选项文案列表。
 * @param selectedIndex 当前选中下标。
 * @param title 设置行标题。
 * @param onSelectedIndexChange 选中回调。
 * @param summary 设置行副标题。
 * @param showValue 是否在行尾显示当前选中值。
 * @param backdrop 弹出面板的背景采样源（null 时不透明填充，仍保留边缘高光与阴影）。
 */
/**
 * 与 [AppSpinnerPreference] 同一套行排版（title/summary/尾部值 + 上下双箭头），
 * 点击行为外接——用于「更新来源优先级」这类打开自定义编辑器（排序面板）的设置行，
 * 保证与普通下拉设置行逐像素一致。
 */
@Composable
fun AppPreferenceRow(
    title: String,
    value: String,
    onRowClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    onBoundsChanged: ((Rect) -> Unit)? = null,
    anchorModifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    BasicComponent(
        modifier = if (onBoundsChanged != null) {
            modifier.onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
        } else {
            modifier
        },
        title = title,
        summary = summary,
        insideMargin = insideMargin,
        endActions = {
            Row(
                modifier = anchorModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (value.isNotEmpty()) {
                    Text(
                        text = value,
                        modifier = Modifier.padding(end = 8.dp).weight(1f, fill = false),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        textAlign = TextAlign.End,
                    )
                }
                DropdownArrowEndAction(
                    actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        },
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onRowClick() },
        enabled = enabled,
    )
}

@Composable
fun AppSpinnerPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    backdrop: Backdrop? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    startAction: @Composable (() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    // 展开期间冻结选项快照：外部列表实时刷新（如测速延迟陆续写入）不会重建面板导致它自动关闭
    var expanded by remember { mutableStateOf(false) }
    val displayItems = remember(expanded) { items }
    PopupTheme {
    GlassDropdownPreference(
        onSelectedIndexChange = onSelectedIndexChange,
        items = displayItems,
        selectedIndex = selectedIndex,
        title = title,
        backdrop = backdrop,
        modifier = modifier,
        enabled = enabled,
        showValue = showValue,
        summary = summary,
        insideMargin = insideMargin,
        startAction = startAction,
        sizing = if (maxHeight != null) GlassPopupSizing(maxHeight = maxHeight) else GlassPopupSizing(),
        // 照抄 InstallerX：style 决定玻璃材质与阴影调校，缺了阴影覆盖不全
        visuals = GlassPopupDefaults.visuals(style = GlassDefaults.style(isDark)),
        onExpandedChange = { expanded = it },
    )
    }
}