package com.app.market.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.history.InstallHistoryAction
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.history.InstallHistoryEntry
import com.app.market.ui.component.AppIcon
import com.app.market.ui.component.AppTextButton
import com.app.market.ui.component.CardSegmentContainer
import com.app.market.ui.component.HistoryDetailDialog
import com.app.market.ui.component.HistoryRecordRow
import com.app.market.ui.component.LsposedModuleIconMarker
import com.app.market.ui.component.SelfAppIconMarker
import com.app.market.viewmodel.SELF_UPDATE_APP_ID
import com.app.market.ui.component.installerModeLabel
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.ui.component.SectionTitle
import com.app.market.ui.util.appDisplayName
import com.app.market.ui.util.appSourceLabel
import com.app.market.ui.util.appSourceBrandColor
import com.app.market.ui.util.currentLocalDate
import com.app.market.ui.util.formatDateTimeFull
import com.app.market.ui.util.formatSize
import com.app.market.ui.util.formatTimeHm
import com.app.market.ui.util.localDateOf
import com.app.market.viewmodel.InstallHistoryViewModel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.app.market.ui.theme.PopupTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 成功绿 / 失败红（与项目状态色语义一致）。 */
private val SuccessGreen = Color(0xFF22C55E)
private val FailureRed = Color(0xFFDF3F55)

@Composable
fun InstallHistoryScreen(
    viewModel: InstallHistoryViewModel,
    onOpenDetail: (InstallHistoryEntry) -> Unit = {},
    onBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    // 点卡片看这次记录的详情（对齐 InstallerX-Revived），不再直接跳应用详情页
    var detailEntry by remember { mutableStateOf<InstallHistoryEntry?>(null) }

    MarketScaffold(
        title = "安装历史",
        onBack = onBack,
        actions = {
            if (entries.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "清空记录",
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { innerPadding, backdropModifier, scrollBehavior ->
        val groups = remember(entries) {
            entries.groupBy { localDateOf(it.timestamp) }
                .map { (date, items) -> date to items }
        }
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdropModifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                top = innerPadding.calculateTopPadding() + PageVerticalPadding,
                bottom = innerPadding.calculateBottomPadding() + PageVerticalPadding,
            ),
        ) {
            if (entries.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "暂无安装记录",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.main,
                        )
                    }
                }
            }
            groups.forEachIndexed { groupIndex, (date, items) ->
                item(key = "day-$date") {
                    SectionTitle(
                        text = installDayLabel(date),
                        topPadding = if (groupIndex == 0) 0.dp else 20.dp,
                    )
                }
                itemsIndexed(
                    items,
                    key = { _, e -> "${e.packageName}:${e.action}:${e.timestamp}" },
                ) { index, entry ->
                    CardSegmentContainer(
                        isFirst = index == 0,
                        isLast = index == items.lastIndex,
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { detailEntry = entry },
                        ) {
                            InstallHistoryRow(entry = entry)
                        }
                    }
                }
            }
        }
    }

    PopupTheme { WindowDialog(
        show = showClearDialog,
        title = "清空安装历史",
        summary = "所有安装记录都会被删除，不可恢复。",
        onDismissRequest = { showClearDialog = false },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            AppTextButton(
                modifier = Modifier.weight(1f),
                text = "取消",
                onClick = { showClearDialog = false },
            )
            Spacer(Modifier.width(16.dp))
            AppTextButton(
                modifier = Modifier.weight(1f),
                text = "确定",
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    viewModel.clear()
                    showClearDialog = false
                },
            )
        }
    } }

    detailEntry?.let { entry ->
        val actionLabel = if (entry.action == InstallHistoryAction.INSTALL) "安装" else "下载"
        HistoryDetailDialog(
            title = appDisplayName(entry.displayName, entry.source, entry.localLabel),
            packageName = entry.packageName,
            rows = listOf(
                "操作类型" to actionLabel,
                "状态" to if (entry.success) "成功" else "失败",
                "版本名称" to entry.versionName.takeIf { it.isNotBlank() }?.let { "v$it" }.orEmpty(),
                "大小" to if (entry.apkSize > 0L) formatSize(entry.apkSize) else "",
                "时间" to formatDateTimeFull(entry.timestamp),
                "安装方式" to installerModeLabel(entry.installerMode),
            ),
            onDismiss = { detailEntry = null },
            sourceLabel = entry.source?.let { appSourceLabel(it) },
            onOpenSource = {
                detailEntry = null
                onOpenDetail(entry)
            },
            failureReason = if (entry.success) null else entry.detail,
            exportPrefix = "install",
        )
    }
}

@Composable
private fun installDayLabel(date: LocalDate): String {
    val today = currentLocalDate()
    return when (date) {
        today -> "今天"
        today.minus(1, DateTimeUnit.DAY) -> "昨天"
        else -> "${date.year}年${date.month.number}月${date.day}日"
    }
}

@Composable
private fun InstallHistoryRow(entry: InstallHistoryEntry) {
    val actionLabel = when (entry.action) {
        InstallHistoryAction.DOWNLOAD -> "下载"
        InstallHistoryAction.INSTALL -> "安装"
    }
    val statusLabel = if (entry.success) "${actionLabel}成功" else "${actionLabel}失败"
    HistoryRecordRow(
        // LSPosed 模块在仓库里没有应用图标（取到的是作者头像 / README 首图），统一内置图标
        icon = when {
            // AppMarket 自己的记录（自更新）没有网络图标，用内置启动图标
            entry.appId == SELF_UPDATE_APP_ID -> SelfAppIconMarker
            entry.source == AppSource.LSPOSED -> LsposedModuleIconMarker
            else -> entry.icon
        },
        title = appDisplayName(entry.displayName, entry.source, entry.localLabel),
        //副标题给版本号 / 体积 / 详情；名字已经在标题上，别再重复包名
        subtitle = buildString {
            if (entry.versionName.isNotBlank()) append("v").append(entry.versionName)
            if (entry.apkSize > 0L) {
                if (isNotEmpty()) append(" · ")
                append(formatSize(entry.apkSize))
            }
            if (entry.detail.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(entry.detail)
            }
            if (isEmpty()) append(entry.packageName)
        },
        time = formatTimeHm(entry.timestamp),
        sourceLabel = entry.source?.let { appSourceLabel(it) },
        sourceColor = entry.source?.let { appSourceBrandColor(it) } ?: MiuixTheme.colorScheme.primary,
        status = statusLabel,
        statusColor = if (entry.success) SuccessGreen else FailureRed,
    )
}