package com.app.market.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.theme.DownloadSourceBadge
import com.app.market.domain.repository.ThemePreferencesRepository
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 历史记录卡片行——对齐 InstallerX Revived 的记录页排版：
 * 第一行「应用名 + 来源标记」，第二行「版本/包名」，右侧「状态 + 时间」。
 *
 * 来源标记的展示方式由「设置 → 主题 → 外观 → 下载来源显示」控制：
 * 关闭 / 普通（应用名右侧小圆点）/ 品牌卡片（品牌色胶囊）。
 */
@Composable
fun HistoryRecordRow(
    icon: String,
    title: String,
    subtitle: String,
    time: String,
    modifier: Modifier = Modifier,
    sourceLabel: String? = null,
    sourceColor: Color = MiuixTheme.colorScheme.primary,
    status: String? = null,
    statusColor: Color = MiuixTheme.colorScheme.primary,
) {
    val badgeStyle = rememberDownloadSourceBadge()
    val hasSource = !sourceLabel.isNullOrBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon.isBlank()) {
            // 没有图标（例如从安装记录恢复出来的条目）时用首字母占位，
            // 而不是退回 AppMarket 自己的启动图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.trim().take(1).ifBlank { "?" },
                    style = MiuixTheme.textStyles.headline1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            AppIcon(
                url = icon,
                contentDescription = title,
                size = 44.dp,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // 普通模式：应用名右边一个浅色来源标签
                if (badgeStyle == DownloadSourceBadge.PLAIN && hasSource) {
                    Spacer(Modifier.width(6.dp))
                    SourceTag(text = sourceLabel.orEmpty())
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 品牌卡片模式：来源品牌色胶囊
                if (badgeStyle == DownloadSourceBadge.BRAND && hasSource) {
                    Text(
                        text = sourceLabel.orEmpty(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = sourceColor,
                        maxLines = 1,
                        modifier = Modifier
                            .background(
                                color = sourceColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (!status.isNullOrBlank()) {
                Text(
                    text = status,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                    maxLines = 1,
                )
            }
            Text(
                text = time,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 全局「来源显示」设置：关闭 / 普通 / 品牌卡片。更新、下载、历史等页面共用。 */
@Composable
fun rememberDownloadSourceBadge(): DownloadSourceBadge = koinInject<ThemePreferencesRepository>()
    .downloadSourceBadge
    .collectAsStateWithLifecycle()
    .value

/**
 * 应用名右侧的「来源」小标签（普通模式）：浅主题色胶囊，不跟随来源品牌色。
 */
@Composable
fun SourceTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.primary,
) {
    if (text.isBlank()) return
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
