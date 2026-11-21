package com.app.market.data.install.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.app.market.domain.model.install.InstallRequest
import com.app.market.domain.model.installer.SavedPackage
import com.app.market.domain.model.installer.SavedPackageArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal class MediaStorePackageStore(
    private val context: Context,
    private val index: SavedPackageIndex,
    private val preferences: com.app.market.domain.repository.InstallerPreferencesRepository,
) {
    suspend fun createPending(request: InstallRequest): PendingSavedPackage = withContext(Dispatchers.IO) {
        val targets = mutableListOf<PendingArtifactTarget>()
        try {
            val configuredDir = preferences.packageSaveDirectory()
            request.artifacts.forEachIndexed { artifactIndex, artifact ->
                targets += createTarget(request, artifactIndex, artifact.name, configuredDir)
            }
            PendingSavedPackage(request, targets, context, index)
        } catch (error: Throwable) {
            targets.forEach { runCatching { deleteUri(it.uri) } }
            throw error
        }
    }

    suspend fun list(): List<SavedPackage> = withContext(Dispatchers.IO) {
        val valid = mutableListOf<SavedPackage>()
        index.all().forEach { saved ->
            if (saved.artifacts.all { canRead(Uri.parse(it.uri)) }) {
                valid += saved
            } else {
                index.remove(saved.id)
            }
        }
        valid.sortedByDescending { it.modifiedAt }
    }

    suspend fun get(id: String): SavedPackage? = withContext(Dispatchers.IO) {
        index.get(id)?.takeIf { saved -> saved.artifacts.all { canRead(Uri.parse(it.uri)) } }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val saved = index.get(id) ?: return@withContext
        saved.artifacts.forEach { artifact -> deleteUri(Uri.parse(artifact.uri)) }
        index.remove(id)
        cleanupEmptyDirs()
    }

    /** 清掉 Download/AppMarket 下的空子目录（split APK 分组目录在包删除后留空壳）。 */
    private fun cleanupEmptyDirs() {
        val root = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "AppMarket",
        )
        root.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                runCatching { if (dir.listFiles()?.isEmpty() == true) dir.delete() }
            }
        }
    }

    /**
     * 清理下载中途被杀留下的 pending 条目。只以 pending 为依据——索引读失败会静默返回空，
     * 若按"不在索引里"删，一次索引丢失就会清空用户下载目录里的全部安装包。
     */
    suspend fun sweepOrphans() = withContext(Dispatchers.IO) {
        runCatching { sweepInstalledPackages() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { sweepPendingEntries() }
            runCatching { sweepStrayInstallers() }
        } else {
            runCatching { sweepLegacyOrphans() }
        }
        runCatching { cleanupEmptyDirs() }
        Unit
    }

    /**
     * 不保留任何安装包：目标版本已装上的安装包一律清掉。
     * 自更新安装会杀掉本进程，安装成功后的清理来不及跑，靠下次启动这里兜底。
     */
    @Suppress("DEPRECATION")
    private suspend fun sweepInstalledPackages() {
        index.all().forEach { saved ->
            if (saved.versionCode <= 0L) return@forEach
            val installed = runCatching {
                val info = context.packageManager.getPackageInfo(saved.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    info.versionCode.toLong()
                }
            }.getOrNull() ?: return@forEach
            if (installed >= saved.versionCode) delete(saved.id)
        }
    }

    /** 索引之外的残留安装包（自更新残留、Google Play 分包目录里遗留的碎片等）一律删除。 */
    private suspend fun sweepStrayInstallers() {
        val known = index.all().flatMapTo(mutableSetOf()) { saved -> saved.artifacts.map { it.uri } }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.IS_PENDING} = 0",
            arrayOf("${Environment.DIRECTORY_DOWNLOADS}/AppMarket%"),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                if (!name.endsWith(".apk", ignoreCase = true) &&
                    !name.endsWith(".xapk", ignoreCase = true) &&
                    !name.endsWith(".apks", ignoreCase = true)
                ) {
                    continue
                }
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                if (uri.toString() !in known) runCatching { deleteUri(uri) }
            }
        }
    }

    private fun sweepPendingEntries() {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.IS_PENDING} = 1",
            arrayOf("${Environment.DIRECTORY_DOWNLOADS}/AppMarket%"),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                runCatching { deleteUri(ContentUris.withAppendedId(collection, cursor.getLong(0))) }
            }
        }
    }

    /** Q 以下没有 pending 概念，只能靠索引比对，因此索引为空时一律不动。 */
    private suspend fun sweepLegacyOrphans() {
        val known = index.all().flatMapTo(mutableSetOf()) { saved -> saved.artifacts.map { it.uri } }
        if (known.isEmpty()) return
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        File(base, "AppMarket").walkBottomUp().forEach { entry ->
            when {
                entry.isFile && Uri.fromFile(entry).toString() !in known -> entry.delete()
                entry.isDirectory && entry.listFiles()?.isEmpty() == true -> entry.delete()
            }
        }
    }

    fun openInput(uri: String): InputStream {
        val parsed = Uri.parse(uri)
        return when (parsed.scheme) {
            "content" -> context.contentResolver.openInputStream(parsed)
                ?: error("Unable to open saved package: $uri")

            "file", null -> File(requireNotNull(parsed.path)).inputStream()
            else -> error("Unsupported saved package URI: $uri")
        }
    }

    private fun createTarget(
        request: InstallRequest,
        index: Int,
        sessionName: String,
        configuredDir: String,
    ): PendingArtifactTarget {
        val fileName = displayFileName(request, index, sessionName)
        val subPath = buildSubPath(request)
        val relBase = mediaRelativePath(configuredDir)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && relBase != null) {
            val relativePath = if (subPath.isEmpty()) relBase else "$relBase/$subPath"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, APK_MIME)
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create download entry")
            PendingArtifactTarget(uri, fileName)
        } else {
            // 配置目录在公共下载域之外（或 Android 10 以下）：直接写文件到该目录
            val base = File(configuredDir)
            val directory = (if (subPath.isEmpty()) base else File(base, subPath)).apply { mkdirs() }
            val file = uniqueFile(directory, fileName)
            PendingArtifactTarget(Uri.fromFile(file), file.name)
        }
    }
    /** 配置路径映射为 MediaStore 相对路径（须在公共下载根之下）；域外返回 null，由 File 分支落位。 */
    private fun mediaRelativePath(configuredDir: String): String? {
        val norm = configuredDir.trim().trimEnd('/')
        if (norm.isEmpty()) return "${Environment.DIRECTORY_DOWNLOADS}/AppMarket"
        val downloadRoot = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .absolutePath.trimEnd('/')
        return when {
            norm == downloadRoot -> Environment.DIRECTORY_DOWNLOADS
            norm.startsWith("$downloadRoot/") ->
                "${Environment.DIRECTORY_DOWNLOADS}${norm.removePrefix(downloadRoot)}"
            else -> null
        }
    }
    /** split 包按包名-版本号分目录，单包直接放根目录。 */
    private fun buildSubPath(request: InstallRequest): String =
        if (request.artifacts.size == 1) ""
        else sanitizeFileName("${request.packageName}-${request.versionCode}")
    private fun displayFileName(request: InstallRequest, index: Int, sessionName: String): String {
        if (request.artifacts.size > 1) return sanitizeFileName(sessionName).ensureApkSuffix()
        val version = request.versionName.ifBlank { request.versionCode.toString() }
        return sanitizeFileName("${request.displayName}-$version").ensureApkSuffix()
    }

    private fun uniqueFile(directory: File, requestedName: String): File {
        val base = requestedName.removeSuffix(".apk")
        var candidate = File(directory, requestedName)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "$base-$suffix.apk")
            suffix += 1
        }
        return candidate
    }

    private fun canRead(uri: Uri): Boolean = runCatching {
        when (uri.scheme) {
            "content" -> context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            "file", null -> File(requireNotNull(uri.path)).isFile
            else -> false
        }
    }.getOrDefault(false)

    private fun deleteUri(uri: Uri) {
        when (uri.scheme) {
            "content" -> context.contentResolver.delete(uri, null, null)
            "file", null -> File(requireNotNull(uri.path)).delete()
        }
    }

    internal class PendingSavedPackage(
        private val request: InstallRequest,
        private val targets: List<PendingArtifactTarget>,
        private val context: Context,
        private val index: SavedPackageIndex,
    ) {
        fun openOutput(artifactIndex: Int): OutputStream {
            val uri = targets[artifactIndex].uri
            val raw = when (uri.scheme) {
                "content" -> context.contentResolver.openOutputStream(uri, "w")
                    ?: error("Unable to write download entry")

                "file", null -> File(requireNotNull(uri.path)).outputStream()
                else -> error("Unsupported download URI: $uri")
            }
            return raw.buffered(OUTPUT_BUFFER_SIZE)
        }

        suspend fun publish(): SavedPackage = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                targets.forEach { context.contentResolver.update(it.uri, values, null, null) }
            }
            val artifacts = targets.map { target ->
                val (name, size) = resolveMetadata(target)
                SavedPackageArtifact(target.uri.toString(), name, size)
            }
            val saved = SavedPackage(
                id = request.id,
                fileName = artifacts.first().fileName,
                packageName = request.packageName,
                displayName = request.displayName,
                versionName = request.versionName,
                versionCode = request.versionCode,
                size = artifacts.sumOf { it.size },
                modifiedAt = System.currentTimeMillis(),
                artifacts = artifacts,
                icon = request.icon,
                source = request.marketSource,
            )
            index.put(saved)
            saved
        }

        suspend fun rollback() = withContext(Dispatchers.IO) {
            targets.forEach { target ->
                when (target.uri.scheme) {
                    "content" -> context.contentResolver.delete(target.uri, null, null)
                    "file", null -> File(requireNotNull(target.uri.path)).delete()
                }
            }
        }

        private fun resolveMetadata(target: PendingArtifactTarget): Pair<String, Long> {
            if (target.uri.scheme != "content") {
                val file = File(requireNotNull(target.uri.path))
                return file.name to file.length()
            }
            context.contentResolver.query(
                target.uri,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0) to cursor.getLong(1)
                }
            }
            return target.fileName to 0L
        }

        private companion object {
            const val OUTPUT_BUFFER_SIZE = 128 * 1024
        }
    }

    internal data class PendingArtifactTarget(val uri: Uri, val fileName: String)

    private fun sanitizeFileName(value: String): String =
        value.ifBlank { "package" }
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .trim('_')
            .ifBlank { "package" }

    private fun String.ensureApkSuffix(): String =
        if (endsWith(".apk", ignoreCase = true)) this else "$this.apk"

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
