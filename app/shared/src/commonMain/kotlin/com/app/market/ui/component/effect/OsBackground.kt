package com.app.market.ui.component.effect

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.Canvas
import kotlin.math.max
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * OS3/OS4 风格的光晕背景（Apache-2.0，配色数据照抄 InstallerX Revived / compose-miuix-ui 的
 * BgEffectConfig；原实现走 AGSL RuntimeShader，这里用多层径向渐变等价还原，全平台可用）。
 * 四个光斑按配置的位置/半径/颜色铺在 surface 底色上，形成柔和的彩色光晕。
 */
@Composable
fun OsBackground(
    modifier: Modifier = Modifier,
    isOs3: Boolean = true,
    alpha: Float = 1f,
    content: @Composable (BoxScope.() -> Unit),
) {
    val isDark = isSystemInDarkTheme()
    val preset = remember(isDark, isOs3) {
        BgEffectConfig.get(DeviceType.PHONE, isDark, isOs3)
    }
    val surface = MiuixTheme.colorScheme.surface
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(surface)
            if (alpha <= 0f) return@Canvas
            val maxDim = max(size.width, size.height)
            for (i in 0 until 4) {
                val px = preset.points[i * 3]
                val py = preset.points[i * 3 + 1]
                val pr = preset.points[i * 3 + 2]
                val cx = px * size.width
                val cy = py * size.height
                val radius = (0.35f + pr * 0.65f) * maxDim
                val r = preset.colors1[i * 4]
                val g = preset.colors1[i * 4 + 1]
                val b = preset.colors1[i * 4 + 2]
                val a = preset.colors1[i * 4 + 3]
                val c = Color(r, g, b, a * alpha)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c.copy(alpha = 0.55f), c.copy(alpha = 0f)),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }
        }
        content()
    }
}
