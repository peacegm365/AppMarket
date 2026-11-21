package com.app.market.ui.theme

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PopupTheme(content: @Composable () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val opaque = LocalOpaqueSurface.current
    val popupColors = if (LocalAppBackgroundImage.current != null) colors.copy(surface = opaque, background = opaque) else colors
    MiuixTheme(colors = popupColors, content = content)
}