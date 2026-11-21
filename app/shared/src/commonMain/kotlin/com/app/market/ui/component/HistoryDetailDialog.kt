package com.app.market.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.market.domain.model.installer.InstallerMode
import com.app.market.platform.UiPlatform
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 历史记录详情：对齐 InstallerX-Revived 的记录详情页——
 * 底部滑出面板、左上角 ✕ 关闭、每个字段「小灰标签 + 大号取值」竖排堆叠。
 * 用独立窗口实现，避免嵌在页面布局里被父容器高度压成 0 而点不开。
 */
@Composable
fun HistoryDetailDialog(
    title: String,
    packageName: String,
    rows: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    sourceLabel: String? = null,
    onOpenSource: (() -> Unit)? = null,
    /** 失败原因（原始报错全文）；为 null 表示这条记录是成功的，不显示错误区。 */
    failureReason: String? = null,
    /** 导出日志用的文件名前缀，例如 "install" / "update"。 */
    exportPrefix: String = "history",
) {
    val uiPlatform = koinInject<UiPlatform>()
    val scope = rememberCoroutineScope()
    // 入场动画：从屏幕下方滑入
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val slide by animateFloatAsState(
        targetValue = if (shown) 0f else 1f,
        animationSpec = tween(durationMillis = 240),
        label = "historySheetSlide",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 遮罩：点击关闭
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, (slide * 600).toInt()) }
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .background(
                        color = MiuixTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // 头部：左 ✕，右标题（InstallerX 同款，不用居中大标题）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = "关闭",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DetailField(label = "包名", value = packageName)
                    rows.forEach { (label, value) ->
                        if (value.isNotBlank()) DetailField(label = label, value = value)
                    }
                    if (!sourceLabel.isNullOrBlank() && onOpenSource != null) {
                        DetailField(
                            label = "来源",
                            value = "$sourceLabel · 查看应用详情 ›",
                            valueColor = MiuixTheme.colorScheme.primary,
                            onClick = onOpenSource,
                        )
                    }
                    if (failureReason != null) {
                        DetailField(
                            label = "失败原因",
                            value = failureReason.ifBlank { "未知错误（无错误信息）" },
                            valueColor = MiuixTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (failureReason != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        SelectionContainer {
                            Text(
                                text = "长按可复制",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        AppTextButton(
                            text = "导出日志",
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                val fileName = "$exportPrefix-$packageName-${System.currentTimeMillis()}.log"
                                scope.launch {
                                    val path = uiPlatform.exportLogFile(fileName, failureReason)
                                    uiPlatform.showToast(
                                        if (path != null) "日志已导出：$path" else "导出失败",
                                    )
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 一条详情字段：小号灰标签在上，正文取值在下，与 InstallerX-Revived 记录详情一致。 */
@Composable
private fun DetailField(
    label: String,
    value: String,
    valueColor: Color = MiuixTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = valueColor,
        )
    }
}

/** 安装方式 key → 中文标签（历史里只存 key，旧记录为空时返回空串）。 */
fun installerModeLabel(key: String): String = when (key) {
    InstallerMode.STANDARD.key -> "默认安装"
    InstallerMode.SYSTEM.key -> "系统权限静默安装"
    InstallerMode.ROOT.key -> "Root 静默安装"
    InstallerMode.SHIZUKU.key -> "Shizuku"
    InstallerMode.DHIZUKU.key -> "Dhizuku"
    InstallerMode.THIRD_PARTY.key -> "第三方安装器"
    else -> ""
}