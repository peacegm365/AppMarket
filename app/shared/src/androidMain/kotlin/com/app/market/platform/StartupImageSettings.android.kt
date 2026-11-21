package com.app.market.platform

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import java.io.File

private const val PREFS = "startup_image"
private const val IMAGE = "startup_image.bin"

@Composable
actual fun StartupImageSettings() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var hasImage by remember { mutableStateOf(File(context.filesDir, IMAGE).isFile) }
    var editing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val temp = File(context.filesDir, "$IMAGE.tmp")
            val result = runCatching {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法读取图片" }
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 20L * 1024 * 1024) { "图片不能超过 20 MB" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temp.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片格式无效" }
            }
            result.onSuccess { editing = true }.onFailure {
                temp.delete()
                Toast.makeText(context, it.message ?: "读取图片失败", Toast.LENGTH_LONG).show()
            }
        }
    }
    if (editing) {
        StartupImageEditor(
            source = File(context.filesDir, "$IMAGE.tmp"),
            destination = File(context.filesDir, IMAGE),
            onDismiss = { editing = false; File(context.filesDir, "$IMAGE.tmp").delete() },
            onSaved = { hasImage = true; editing = false; File(context.filesDir, "$IMAGE.tmp").delete(); prefs.edit().putLong("image_revision", System.currentTimeMillis()).apply() },
        )
    }
    Column {
        SwitchPreference(
            title = "自定义启动/背景图",
            summary = "自定义启动图片和背景图片",
            checked = enabled,
            onCheckedChange = { enabled = it; prefs.edit().putBoolean("enabled", it).apply() },
        )
        AnimatedVisibility(visible = enabled) {
            ArrowPreference(
                title = "选择启动/背景图片",
                summary = if (hasImage) "已选择图片" else "请选择图片",
                onClick = { picker.launch("image/*") },
            )
        }
    }
}