package com.app.market.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.app.market.domain.model.download.DownloadPhase
import com.app.market.domain.model.market.AppSource
import com.app.market.viewmodel.SELF_UPDATE_APP_ID
import com.app.market.domain.model.download.DownloadState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏右上角的下载入口：
 * - 无下载：普通下载图标
 * - 有下载：最多 3 个应用图标「斜叠」显示（每个只露出左侧 1/4），共用**一条**进度环，
 *   环按应用数量均分角度段、各段用该应用图标主色画各自进度，下方显示总速度。
 */
@Composable
fun DownloadStatusButton(
    states: Collection<DownloadState>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 只显示一个图标：优先正在下载的那个，其次按相位优先级。
    // 暂停（PAUSED）不算「正在下载」——没有真正在跑的任务时回到普通下载图标，
    // 避免右上角一直挂着某个应用的图标（哪怕它早就暂停/放进队列了）。
    val active = remember(states) {
        states.filter { it.phase in RunningPhases }
            .sortedBy { RunningPhases.indexOf(it.phase) }
            .firstOrNull()
    }
    val totalSpeed = states.filter { it.phase == DownloadPhase.DOWNLOADING }.sumOf { it.speedBps }
    // 进度环统一用主题色，不再按应用图标取色
    val ringColor = MiuixTheme.colorScheme.primary

    Column(
        modifier = modifier
            // 点击时不出现灰色水波纹遮罩
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            // 宽度固定：速度文字出现/消失时图标不会左右漂移
            .width(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (active == null) {
            Icon(
                imageVector = MiuixIcons.Download,
                contentDescription = "下载",
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        } else {
            // 与静止态图标同一个 28dp 外框，有下载时不会突然变大
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 2.5.dp.toPx()
                    val inset = strokePx / 2f
                    val progress = ((active.progress ?: 0f) / 100f).coerceIn(0f, 1f)
                    if (progress > 0f) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - strokePx, size.height - strokePx),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round),
                        )
                    }
                }
                // 与下载管理页同一套图标规则：自更新/LSPosed 模块没有可用网络图标，
                // 统一换成内置图标（用户看到的就是下载管理里那个缩略图）
                AppIcon(
                    url = when {
                        active.appId == SELF_UPDATE_APP_ID || active.icon.isBlank() -> SelfAppIconMarker
                        active.source == AppSource.LSPOSED -> LsposedModuleIconMarker
                        else -> active.icon
                    },
                    contentDescription = active.displayName,
                    size = 20.dp,
                    modifier = Modifier.size(20.dp),
                    // 套在圆形进度环里，缩略图按正圆裁剪
                    circular = true,
                )
            }
        }
        if (totalSpeed > 0L) {
            // 速度单独一行，宽度自适应、不被图标框裁剪
            Text(
                text = formatSpeed(totalSpeed),
                fontSize = 9.sp,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 计入「正在下载」的相位：暂停不算，没有在跑的任务就不显示应用图标。 */
private val RunningPhases = listOf(
    DownloadPhase.DOWNLOADING,
    DownloadPhase.QUEUED,
    DownloadPhase.INSTALLING,
    DownloadPhase.AWAITING_USER_ACTION,
)

/** 计入「下载入口高亮」的相位（含暂停，供别处复用）。 */
private val ActivePhases = listOf(
    DownloadPhase.DOWNLOADING,
    DownloadPhase.QUEUED,
    DownloadPhase.PAUSED,
    DownloadPhase.INSTALLING,
    DownloadPhase.AWAITING_USER_ACTION,
)


/**
 * 顶栏右上角的搜索入口：与下载入口同尺寸的无水波纹图标按钮。
 */
@Composable
fun TopRightSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            // 点击时不出现灰色水波纹遮罩，与下载入口一致
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .width(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = MiuixIcons.Search,
            contentDescription = "搜索",
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
    }
}


/**
 * 多选模式右上角的「全选」图标按钮：与搜索入口同规格的无水波纹图标按钮。
 */
@Composable
fun TopRightSelectAllButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .width(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = MiuixIcons.SelectAll,
            contentDescription = "全选",
            // 未全选用普通色，全部选中后才变主题蓝
            tint = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.size(28.dp),
        )
    }
}