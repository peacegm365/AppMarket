package com.app.market.platform
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
/**
 * 崩溃与启动过程日志。（骨架照 2.4.2；公共目录落盘走 MediaStore）
 *
 * Android 11+ 下 File API 直写公共 Download 目录会被 FUSE 静默拦截
 * （canWrite 误报 true、appendText 不报错也不落盘），因此公共目录用
 * MediaStore 追加写；MediaStore 不可用时退回应用私有目录，日志不丢。
 */
object CrashLogger {
    private val lock = Any()
    private var appContext: Context? = null
    private var file: File? = null
    private var publicUri: android.net.Uri? = null
    /** 「安装包保存路径」：非空时日志跟随安装包落位目录。 */
    @Volatile
    private var baseOverride: String? = null
    private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    /** 截断/对齐这类重 IO 全走后台单线程，绝不在主线程（尤其启动路径）上碰 MediaStore。 */
    private val ioExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "log-io") }
    /** 设置里的日志开关：关闭时不写日志。 */
    @Volatile
    var enabled: Boolean = true
    /** 日志文件体积上限（字节）；0 = 不限制。超限时截断旧内容，只保留尾部一半（最新日志）。 */
    @Volatile
    var maxBytes: Int = 1024 * 1024
    /** 已写字节估算（后台对齐一次真实大小；只用于触发限额检查，允许少量偏差）。 */
    @Volatile
    private var writtenBytes = 0L
    /** 单条消息长度上限：一条 StackOverflow 堆栈能写 30MB，必须掐头。 */
    private const val MESSAGE_CHAR_CAP = 8000
    fun init(context: Context) {
        appContext = context.applicationContext
        val public = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "AppMarket",
        )
        runCatching { public.mkdirs() }
        val target = File(public, "appmarket.log")
        file = if (target.canWrite() || target.parentFile?.canWrite() == true) {
            target
        } else {
            runCatching { context.getExternalFilesDir(null)?.let { File(it, "appmarket.log") } }.getOrNull()
        }
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            log("FATAL on thread '${thread.name}': ${error.stackTraceToString()}")
            // 交给默认处理器，保留系统崩溃上报与进程终止语义
            System.exit(2)
        }
        log("=== process start pid=${Process.myPid()} ===")
        com.app.market.domain.diagnostic.AppLog.sink = { log(it) }
        // 对齐已有日志的真实大小并按需截断：放后台线程。MediaStore 元数据可能陈旧
        // （外部改写后 SIZE 不刷新），对齐值只是估算，真正截断用流式读尾部，不依赖它。
        ioExecutor.execute {
            synchronized(lock) {
                writtenBytes = runCatching {
                    val resolver = appContext?.contentResolver
                    val uri = publicUri ?: resolver?.let { findOrCreatePublic(it)?.also { u -> publicUri = u } }
                    if (uri != null) {
                        resolver?.query(uri, arrayOf(MediaStore.Downloads.SIZE), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getLong(0) else 0L
                        } ?: 0L
                    } else {
                        file?.length() ?: 0L
                    }
                }.getOrDefault(0L)
                enforceLimitLocked()
            }
        }
    }
    fun log(message: String) {
        if (!enabled) return
        // 单条截断：递归异常的堆栈一条就能顶几十 MB（重灾区就是它）
        val capped =
            if (message.length > MESSAGE_CHAR_CAP) message.take(MESSAGE_CHAR_CAP) + "\n…[单条日志过长已截断]"
            else message
        val line = "${format.format(Date())} $capped\n"
        synchronized(lock) {
            runCatching {
                // 公共目录：MediaStore 追加（File 写公共目录在 Android 11+ 会被静默拦截）
                val context = appContext
                val wrote = context != null && appendPublic(context, line)
                if (!wrote) {
                    // 退应用私有目录兜底，日志不丢
                    val target = file ?: return
                    target.parentFile?.mkdirs()
                    target.appendText(line)
                }
                writtenBytes += line.length
                // 截断是重 IO，丢给后台线程做；写入路径本身只做追加
                if (maxBytes > 0 && writtenBytes > maxBytes) {
                    ioExecutor.execute {
                        synchronized(lock) { enforceLimitLocked() }
                    }
                }
            }
        }
    }
    /** 超过体积上限时截断：丢掉旧的，保留最新的尾部一半。调用方须持有 [lock]。 */
    private fun enforceLimitLocked() {
        val limit = maxBytes
        if (limit <= 0) return
        if (writtenBytes <= limit) return
        val keep = (limit / 2).toLong().coerceAtLeast(8 * 1024)
        val context = appContext
        if (context != null && publicUri != null && trimPublic(context, keep)) {
            writtenBytes = keep
            return
        }
        val target = file ?: return
        trimFile(target, keep)
        writtenBytes = keep
    }
    /** 流式读取尾部 [keep] 字节：不依赖文件大小元数据，SIZE 陈旧也不会读空。 */
    private fun readTail(input: InputStream, keep: Long): ByteArray {
        val chunk = ByteArray(64 * 1024)
        var ring = ByteArray(0)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            val merged = ring + chunk.copyOfRange(0, n)
            ring = if (merged.size <= keep) {
                merged
            } else {
                merged.copyOfRange(merged.size - keep.toInt(), merged.size)
            }
        }
        return ring
    }
    private fun trimFile(target: File, keep: Long) {
        runCatching {
            if (!target.exists()) return
            val tail = target.inputStream().use { readTail(it, keep) }
            target.writeText("${format.format(Date())} === log truncated (over size limit) ===\n")
            target.appendBytes(tail)
        }
    }
    /** MediaStore 版截断：流式读尾部 → 删旧建新 → 写回尾部。 */
    private fun trimPublic(context: Context, keep: Long): Boolean {
        val resolver = context.contentResolver
        val uri = publicUri ?: return false
        val tail = runCatching {
            resolver.openInputStream(uri)?.use { readTail(it, keep) }
        }.getOrNull() ?: return false
        runCatching { resolver.delete(uri, null, null) }
        publicUri = null
        val recreated = findOrCreatePublic(resolver) ?: return false
        publicUri = recreated
        return runCatching {
            resolver.openOutputStream(recreated, "wa")?.use {
                it.write("${format.format(Date())} === log truncated (over size limit) ===\n".toByteArray())
                it.write(tail)
            } != null
        }.getOrDefault(false)
    }
    /** 「安装包保存路径」跟随：切换日志落位目录（与安装包同目录）；切换后重建公共写入点。 */
    fun setBaseDirectory(path: String) {
        val normalized = path.trim().trimEnd('/')
        if (normalized.isEmpty()) return
        synchronized(lock) {
            baseOverride = normalized
            publicUri = null
            val target = File(normalized, "appmarket.log")
            runCatching { target.parentFile?.mkdirs() }
            file = target
            writtenBytes = 0L
        }
        ioExecutor.execute {
            synchronized(lock) {
                writtenBytes = file?.length() ?: 0L
                enforceLimitLocked()
            }
        }
    }
    /** 配置目录 → MediaStore 相对路径；公共下载域外返回 null（MediaStore 表达不了，写入退 file 兜底）。 */
    private fun mediaRelativeDir(): String? {
        val base = baseOverride?.trimEnd('/')
            ?: return "${Environment.DIRECTORY_DOWNLOADS}/AppMarket"
        val root = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .absolutePath.trimEnd('/')
        return when {
            base == root -> Environment.DIRECTORY_DOWNLOADS
            base.startsWith("$root/") -> "${Environment.DIRECTORY_DOWNLOADS}${base.removePrefix(root)}"
            else -> null
        }
    }
    /** MediaStore 追加写公共日志（默认 Download/AppMarket/appmarket.log，跟随安装包路径）。MIME 用未知类型，防止被强加 .txt 改名。 */
    private fun appendPublic(context: Context, line: String): Boolean {
        val resolver = context.contentResolver
        val uri = publicUri ?: findOrCreatePublic(resolver).also { publicUri = it } ?: return false
        val ok = runCatching {
            resolver.openOutputStream(uri, "wa")?.use { it.write(line.toByteArray()) } != null
        }.getOrDefault(false)
        if (ok) return true
        // URI 失效（被删/回收）时重建一次再写
        publicUri = null
        val recreated = findOrCreatePublic(resolver) ?: return false
        publicUri = recreated
        return runCatching {
            resolver.openOutputStream(recreated, "wa")?.use { it.write(line.toByteArray()) } != null
        }.getOrDefault(false)
    }
    private fun findOrCreatePublic(resolver: android.content.ContentResolver): android.net.Uri? {
        val rel = mediaRelativeDir() ?: return null
        // 先按名字前缀找已有文件（MediaStore 会对已知 MIME 改名，所以按前缀匹配）
        runCatching {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE 'appmarket.log%' AND " +
                    "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf("$rel%"),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(0),
                    )
                }
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "appmarket.log")
            put(MediaStore.Downloads.MIME_TYPE, "application/x-log")
            put(MediaStore.Downloads.RELATIVE_PATH, rel)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val created = runCatching {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null
        runCatching { resolver.openOutputStream(created, "wa")?.use { } }
        runCatching {
            resolver.update(created, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        }
        return created
    }
}
