package com.app.market.data.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 图标读写用的 Context 持有者。
 *
 * data 模块是全平台共享的，不能把 Context 一路塞进构造函数；由
 * `MarketApplication.onCreate` 在启动时注入一次即可。
 */
object AppIconContextHolder {
    @Volatile
    var context: Context? = null
}

actual suspend fun installedAppIconUrl(packageName: String): String? = withContext(Dispatchers.IO) {
    val ctx = AppIconContextHolder.context ?: return@withContext null
    val dir = File(ctx.cacheDir, "app-icons").apply { mkdirs() }
    val out = File(dir, packageName.replace('/', '_') + ".png")
    if (out.exists() && out.length() > 0L) return@withContext "file://" + out.absolutePath
    runCatching {
        val drawable = ctx.packageManager.getApplicationIcon(packageName)
        val bitmap = drawable.toSquareBitmap()
        out.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        "file://" + out.absolutePath
    }.getOrNull()
}

private fun Drawable.toSquareBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val width = intrinsicWidth.takeIf { it > 0 } ?: 192
    val height = intrinsicHeight.takeIf { it > 0 } ?: 192
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bitmap
}
actual suspend fun installedAppLabel(packageName: String): String? = withContext(Dispatchers.IO) {
    val ctx = AppIconContextHolder.context ?: return@withContext null
    runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() && it != packageName }
}
