package com.app.market.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

val LocalOpaqueSurface = staticCompositionLocalOf { Color.White }
val LocalAppBackgroundImage = staticCompositionLocalOf<ImageBitmap?> { null }