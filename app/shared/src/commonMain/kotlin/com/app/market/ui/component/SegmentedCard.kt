package com.app.market.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CardDefaults
import com.app.market.ui.theme.LocalAppBackgroundImage
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.LocalContentColor

/**
 * One slice of a visual card that has been split into independent lazy items: the first
 * segment carries the top corners, the last one the bottom corners, and middle segments a
 * plain background, so consecutive items read as a single [top.yukonga.miuix.kmp.basic.Card].
 * Splitting keeps composition/measure incremental per row instead of one big card item.
 *
 * Uses [squircleSurface] (not `squircleBackground`) so full-row click indication is masked
 * to the rounded corners, matching how content is clipped inside a real Card.
 */
/**
 * 一片视觉卡片的分段：第一段带顶角、最后一段带底角，中间段为纯背景，
 * 连续多项读起来仍像一张 [top.yukonga.miuix.kmp.basic.Card]。
 * [containerColor] 非空时整面（含圆角）铺该色，品牌化卡片用。
 * [containerBrush] 非空时优先于 [containerColor]，铺一条横向渐变（同样含圆角）。
 */
@Composable
fun CardSegmentContainer(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
    containerColor: Color? = null,
    containerBrush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.defaultColors()
    val baseColor = containerColor ?: if (LocalAppBackgroundImage.current != null) Color.Transparent else colors.color
    val topCorner = if (isFirst) CardDefaults.CornerRadius else 0.dp
    val bottomCorner = if (isLast) CardDefaults.CornerRadius else 0.dp
    val surfaceModifier = when {
        topCorner == 0.dp && bottomCorner == 0.dp ->
            if (containerBrush != null) Modifier.background(containerBrush)
            else Modifier.background(baseColor)

        containerBrush != null ->
            // 渐变走 clip + 画刷铺底：squircle 系列只吃单色
            Modifier
                .squircleClip(topCorner, topCorner, bottomCorner, bottomCorner)
                .background(containerBrush)

        else ->
            Modifier.squircleSurface(baseColor, topCorner, topCorner, bottomCorner, bottomCorner)
    }
    CompositionLocalProvider(LocalContentColor provides colors.contentColor) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .then(surfaceModifier),
            content = content,
        )
    }
}