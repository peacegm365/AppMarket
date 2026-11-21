package com.app.market.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlin.math.max

@Composable
internal fun StartupImageEditor(source: File, destination: File, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(source) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 4096 || bounds.outHeight / sample > 4096) sample *= 2
            BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (bitmap != null) {
                Canvas(Modifier.fillMaxSize().onSizeChanged { viewport = it }.pointerInput(bitmap, viewport) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        if (viewport.width > 0 && viewport.height > 0) {
                            val next = (zoom * gestureZoom).coerceIn(1f, 8f)
                            val ratio = next / zoom
                            val center = Offset(viewport.width / 2f, viewport.height / 2f)
                            val adjusted = (offset - (centroid - center)) * ratio + (centroid - center) + pan
                            val base = max(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
                            val limitX = ((bitmap.width * base * next - viewport.width) / 2f).coerceAtLeast(0f)
                            val limitY = ((bitmap.height * base * next - viewport.height) / 2f).coerceAtLeast(0f)
                            offset = Offset(adjusted.x.coerceIn(-limitX, limitX), adjusted.y.coerceIn(-limitY, limitY))
                            zoom = next
                        }
                    }
                }) {
                    val scale = max(size.width / bitmap.width, size.height / bitmap.height) * zoom
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(bitmap, null, RectF((size.width-w)/2+offset.x, (size.height-h)/2+offset.y, (size.width+w)/2+offset.x, (size.height+h)/2+offset.y), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    }
                }
            }
            Text("✕", color = Color.White, fontSize = 24.sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 40.dp).size(48.dp).background(Color(0x66000000), CircleShape).clickable(onClick = onDismiss).padding(11.dp))
            Text("✓", color = Color.White, fontSize = 28.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 40.dp).size(48.dp).background(Color(0x66000000), CircleShape).clickable {
                if (bitmap == null || viewport.width <= 0 || viewport.height <= 0) {
                    Toast.makeText(context, "无法读取图片", Toast.LENGTH_LONG).show()
                } else {
                    val result = runCatching {
                        val outputScale = minOf(1f, 2160f / max(viewport.width, viewport.height))
                        val width = (viewport.width * outputScale).toInt().coerceAtLeast(1)
                        val height = (viewport.height * outputScale).toInt().coerceAtLeast(1)
                        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = AndroidCanvas(output)
                        val scale = max(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height) * zoom
                        val w = bitmap.width * scale; val h = bitmap.height * scale
                        canvas.scale(outputScale, outputScale)
                        canvas.drawBitmap(bitmap, null, RectF((viewport.width-w)/2+offset.x, (viewport.height-h)/2+offset.y, (viewport.width+w)/2+offset.x, (viewport.height+h)/2+offset.y), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                        val tmp = File(destination.parentFile, "${destination.name}.saved")
                        try {
                            tmp.outputStream().use { check(output.compress(Bitmap.CompressFormat.PNG, 100, it)) { "图片保存失败" } }
                            check(tmp.renameTo(destination)) { "图片保存失败" }
                        } finally { tmp.delete(); output.recycle() }
                    }
                    result.onSuccess { onSaved() }.onFailure { Toast.makeText(context, it.message ?: "图片保存失败", Toast.LENGTH_LONG).show() }
                }
            }.padding(8.dp))
        }
    }
}