package com.app.market.data.install.storage
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.app.market.domain.diagnostic.AppLog
import com.app.market.domain.model.install.InstallRequest
import com.app.market.domain.repository.InstallerPreferencesRepository
import java.io.File

/**
 * 「另存应用安装包」：下载完成后把安装包副本写到用户指定文件夹，
 * 与「保存到 Download」（MediaStore）相互独立，专门用于把历史版本等安装包
 * 攒到自己选的目录。失败只记日志，不影响下载与安装。
 */
internal object ApkCopySaver {
    suspend fun save(
        context: Context,
        request: InstallRequest,
        staged: List<File?>,
        preferences: InstallerPreferencesRepository,
    ) {
        // 「另存」：任务带目标文档 uri（系统文件管理器所选位置）时直接写入，与开关/固定目录无关
        val saveUri = request.saveUri
        if (saveUri != null) {
            runCatching {
                val file = staged.firstOrNull { it?.isFile == true } ?: error("暂存文件缺失")
                val output = context.contentResolver.openOutputStream(Uri.parse(saveUri))
                    ?: error("无法打开目标位置")
                output.use { out -> file.inputStream().use { input -> input.copyTo(out) } }
                AppLog.log("ApkCopy saved to uri for ${request.packageName}")
            }.onFailure { error ->
                AppLog.log("ApkCopy saveUri fail ${request.packageName}: ${error.message}")
            }
            return
        }
        if (!preferences.apkCopyEnabled()) return
        val configured = preferences.apkCopyDirectory().trim()
        val dir = if (configured.isNotBlank()) {
            File(configured)
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AppMarketPackages",
            )
        }
        runCatching {
            if (!dir.isDirectory && !dir.mkdirs()) {
                error("无法创建目录 ${dir.absolutePath}")
            }
            val baseName = sanitize("${request.displayName}-${request.versionName}(${request.versionCode})")
            request.artifacts.forEachIndexed { index, artifact ->
                val file = staged.getOrNull(index) ?: return@forEachIndexed
                if (!file.isFile) return@forEachIndexed
                val target = if (request.artifacts.size == 1) {
                    File(dir, "$baseName.apk")
                } else {
                    File(dir, "$baseName-${sanitize(artifact.name)}.apk")
                }
                // 同名副本已存在（同版本重复下载）时直接跳过，不覆盖
                if (target.exists()) return@forEachIndexed
                file.copyTo(target, overwrite = false)
                AppLog.log("ApkCopy saved ${target.absolutePath}")
            }
        }.onFailure { error ->
            AppLog.log("ApkCopy fail ${request.packageName}: ${error.message}")
        }
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "app" }
}