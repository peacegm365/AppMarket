package com.app.market.data.install.network

import com.app.market.data.download.splitToRanges
import com.app.market.domain.diagnostic.AppLog
import com.app.market.data.install.InstallPipelineException
import com.app.market.domain.model.install.InstallFailureCode
import com.app.market.domain.repository.InstallerPreferencesRepository
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.coroutineContext

/**
 * 字节范围分片并发下载：把文件切成若干范围分片，每分片独立连接、独立重试，
 * 写入共享目标文件的对应偏移；分片进度持久化在同目录状态文件中，进程被杀后可继续。
 * 设计参考 AB Download Manager 的 downloader/core（RangedPart / splitToRange / PartDownloader）。
 *
 * 返回 null 表示不满足分片条件（未启用、服务器不支持 Range、文件过小），
 * 调用方应回退单连接下载；暂存与远端不一致时抛 [StaleStagingException] 由调用方清空重下。
 */
internal class SegmentedDownloader(
    private val http: InstallHttpClient,
    private val preferences: InstallerPreferencesRepository,
) {
    private class Segment(val from: Long, @Volatile var to: Long, @Volatile var current: Long) {
        /** 是否已被某个 worker 占用；ab-dm 里一个 part 只对应一个 PartDownloader。 */
        @Volatile var claimed: Boolean = false
        /** 与 AB PartSplitSupport 一样，已预留读取的安全区不能被拆走。 */
        @Volatile var safeZone: Long = current - 1
        val isCompleted: Boolean get() = current > to
        // 被别的 worker 抢走后半段时 to 会往回缩，而正在跑的连接可能已经读过边界：
        // 不夹住就会把属于新分片的字节重复计入，进度/速度都虚高
        val done: Long get() = (minOf(current, to + 1) - from).coerceAtLeast(0L)
    }

    private class ContentRange(val from: Long, val total: Long)

    /** 分片状态落盘器：并发进度回调下串行写入，[saveIfDue] 按字节步长节流。 */
    private class StateFile(private val file: File, private val total: Long, private val segments: List<Segment>) {
        private val lock = Any()
        private var lastSavedBytes: Long? = null
        private var lastSavedAt = 0L

        fun saveIfDue(done: Long) {
            synchronized(lock) {
                val last = lastSavedBytes
                val now = System.currentTimeMillis()
                // 字节步长 + 最小时间间隔双节流：分片多时状态文件很大，写得太勤会把下载 IO 抢光
                if (last != null && done - last < PROGRESS_SAVE_STEP) return
                if (now - lastSavedAt < PROGRESS_SAVE_MIN_INTERVAL_MS) return
                lastSavedAt = now
                save()
            }
        }

        fun save() {
            synchronized(lock) {
                runCatching {
                    val snapshot = synchronized(segments) { segments.toList() }
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(
                        buildString {
                            appendLine(STATE_MAGIC)
                            appendLine("total=$total")
                            snapshot.forEach { appendLine("${it.from}-${it.to}:${it.current}") }
                        },
                    )
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        tmp.renameTo(file)
                    }
                    lastSavedBytes = snapshot.sumOf { it.done }
                }
            }
        }

        fun delete() {
            synchronized(lock) { file.delete() }
        }
    }

    suspend fun download(
        url: String,
        headers: Map<String, String>,
        target: File,
        expectedSize: Long,
        onBytes: suspend (Long) -> Unit,
    ): Long? {
        val stateFile = stateFile(target)
        val hasState = stateFile.isFile
        val configured = preferences.downloadConnections()
        val enabled = preferences.multiThreadDownloadEnabled() && configured != 1
        if (!enabled && !hasState) return null
        val total = probe(url, headers)
        if (total == null || total < MIN_SEGMENTED_SIZE) {
            // 服务器不支持 Range 或文件过小：丢弃分片残留，避免把稀疏文件当整包续传
            if (hasState) {
                target.delete()
                stateFile.delete()
            }
            return null
        }
        if (expectedSize > 0L && expectedSize != total) throw StaleStagingException()
        val connections = configured.coerceIn(2, MAX_PART_COUNT)
        // 三项都对齐 ab-dm：dynamicPartCreationMode / minPartSize / PART_MAX_TRIES
        val dynamicParts = preferences.dynamicPartCreationEnabled()
        val minPartSize = preferences.minPartSizeBytes()
        val maxRetries = preferences.partMaxRetries()
        val segments = (loadSegments(stateFile, total, target)
            ?: splitToRanges(total, minPartSize, connections).map { range ->
                Segment(range.first, range.last, range.first)
            }.also {
                target.delete()
                stateFile.delete()
            }).toMutableList()
        val state = StateFile(stateFile, total, segments)
        onBytes(segments.sumOf { it.done })
        try {
            downloadSegments(
                url = url,
                headers = headers,
                target = target,
                segments = segments,
                connections = connections,
                state = state,
                onBytes = onBytes,
                dynamicParts = dynamicParts,
                minPartSize = minPartSize,
                maxRetries = maxRetries,
            )
        } catch (error: Throwable) {
            withContext(NonCancellable) { state.save() }
            throw error
        }
        state.delete()
        return total
    }

    fun clearState(target: File) {
        stateFile(target).delete()
    }

    /**
     * part 调度：完全照搬 AB Download Manager 的 `HttpDownloadJob.beginDownloadParts()`
     *  + `PartSplitSupport`（动态分段）模型：
     *
     *  1. 一开始就按「连接数」把整包均分成 N 个 part（splitToRange），**不做任何测速/试探**；
     *  2. 每个 part 由一个 worker 独占下载，worker 数量 = 连接数；
     *  3. 某个 part 下完后，它的 worker **立刻**去认领下一个空闲 part；
     *     没有空闲 part 且允许动态分段时，把「剩余最大的那个 part」从当前位置对半劈开，新 part 立刻开下；
     *     劈不动（剩余小于 2×minPartSize）就停手 —— 这就是 ab-dm 保持满带宽的方式；
     *  4. 全部 part 完成才算结束；劈分时旧 part 的 `to` 会收缩，写入按新边界钳制，不会串写。
     */
    private suspend fun downloadSegments(
        url: String,
        headers: Map<String, String>,
        target: File,
        segments: MutableList<Segment>,
        connections: Int,
        state: StateFile,
        onBytes: suspend (Long) -> Unit,
        dynamicParts: Boolean,
        minPartSize: Long,
        maxRetries: Int,
    ) {
        val writeLock = Any()
        val workers = connections.coerceAtLeast(1)
        val startedAt = System.currentTimeMillis()
        AppLog.log("SegmentedDownload: start parts=${segments.size} connections=$workers")

        fun doneBytes(): Long = synchronized(segments) { segments.sumOf { it.done } }

        fun allCompleted(): Boolean = synchronized(segments) { segments.all { it.isCompleted } }

        /** 认领一个空闲 part；没有就动态劈分最大的在跑 part（ab-dm dynamicPartCreationMode）。 */
        fun claimPart(): Segment? = synchronized(writeLock) {
            synchronized(segments) {
                segments.firstOrNull { !it.isCompleted && !it.claimed }?.also { it.claimed = true }
            } ?: run {
                // 关闭「动态分段」后，分片下完就不再补位（等剩余分片自然跑完）
                if (!dynamicParts) return@synchronized null
                // AB 按 safeZone 后半段判断是否值得拆分，不对分片总数另设上限。
                val victim = synchronized(segments) {
                    segments.filter {
                        val delta = it.to - it.safeZone
                        !it.isCompleted && delta / 2 + delta % 2 >= minPartSize
                    }.maxByOrNull { it.to - it.current }
                } ?: return@synchronized null
                // AB PartSplitSupport：只拆 safeZone 之后的字节，奇数时前半段多一个字节。
                val delta = victim.to - victim.safeZone
                val splitEnd = victim.safeZone + delta / 2 + delta % 2
                if (splitEnd >= victim.to) return@synchronized null
                val stolen = Segment(splitEnd + 1, victim.to, splitEnd + 1).also { it.claimed = true }
                victim.to = splitEnd
                synchronized(segments) { segments.add(stolen) }
                AppLog.log("SegmentedDownload: split ${victim.from}-${victim.to} | ${stolen.from}-${stolen.to}")
                stolen
            }
        }

        RandomAccessFile(target, "rw").use { file ->
            coroutineScope {
                // 全部 part 完成的信号：字节已经齐了就以它为准收尾，不再等 worker 自己退出。
                // 之前 worker 用 while(true) 循环，某个连接卡在 socket 上时 coroutineScope 永远不返回，
                // 表现就是「进度条跑完了却一直停在那」（日志里缺 SegmentedDownload: done 那行）。
                val finished = kotlinx.coroutines.CompletableDeferred<Unit>()
                val workerJobs = ArrayList<kotlinx.coroutines.Job>(workers)

                fun markFinished() {
                    if (!finished.isCompleted) finished.complete(Unit)
                }

                repeat(workers) {
                    workerJobs += launch {
                        while (true) {
                            val part = claimPart()
                            if (part == null) {
                                if (allCompleted()) {
                                    markFinished()
                                    break
                                }
                                // 分片都被别人占着，稍等再看有没有空出来的
                                delay(PART_WAIT_MS)
                                continue
                            }
                            downloadSegment(url, headers, file, writeLock, part, segments, state, onBytes, maxRetries)
                        }
                    }
                }
                // 看门狗：既有「全线无进展」判定，也有「数据已齐但 worker 没退出」的兜底收尾
                val watchdog = launch {
                    var lastSeen = doneBytes()
                    var lastAt = System.currentTimeMillis()
                    var lastHeartbeatAt = lastAt
                    while (true) {
                        delay(PART_WAIT_MS)
                        val now = System.currentTimeMillis()
                        val done = doneBytes()
                        if (allCompleted()) {
                            markFinished()
                            break
                        }
                        if (done > lastSeen) {
                            lastSeen = done
                            lastAt = now
                        } else {
                            if (now - lastHeartbeatAt >= 1_000L) {
                                lastHeartbeatAt = now
                                onBytes(done)
                            }
                            if (now - lastAt > STALL_TOTAL_MS) {
                                throw IOException("Download stalled: no progress for ${now - lastAt}ms")
                            }
                        }
                    }
                }
                try {
                    finished.await()
                } finally {
                    // 数据齐全即收尾：把仍卡在 socket 上的 worker 直接取消，避免整体挂死
                    workerJobs.forEach { it.cancel() }
                    watchdog.cancel()
                }
            }
        }

        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        val done = doneBytes()
        // 收尾校验整包字节，避免「全部 part 完成但有洞」的假完成
        val expected = synchronized(segments) { segments.sumOf { it.to - it.from + 1 } }
        if (done != expected) {
            throw InstallPipelineException(
                InstallFailureCode.NETWORK,
                "Segmented download incomplete: $done / $expected",
            )
        }
        AppLog.log("SegmentedDownload: done bytes=$done elapsedMs=$elapsed avgKB/s=${done / elapsed}")
    }

    private suspend fun downloadSegment(
        url: String,
        headers: Map<String, String>,
        file: RandomAccessFile,
        writeLock: Any,
        segment: Segment,
        segments: List<Segment>,
        state: StateFile,
        onBytes: suspend (Long) -> Unit,
        maxRetries: Int,
    ) {
        var attempts = 0
        var lastError: Exception? = null
        val partStartedAt = System.currentTimeMillis()
        while (!segment.isCompleted) {
            val before = segment.current
            if (attempts > 0) {
                AppLog.log("Part ${segment.from}-${segment.to} retry=$attempts fail: ${lastError?.message}")
                delay(RETRY_DELAY_MS)
            }
            attempts++
            try {
                fetchRange(url, headers, file, writeLock, segment) {
                    val done = segments.sumOf { it.done }
                    onBytes(done)
                    state.saveIfDue(done)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: StaleStagingException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
            // 只要这一轮有实际进展就重置计数：慢但活着的连接不该被判死，只有真卡住才累计重试
            if (segment.current > before) attempts = 0
            if (!segment.isCompleted) {
                // 把当前进度事件补发一次：上层速度表据此归零，UI 不再显示卡住前的旧速度
                runCatching { onBytes(segments.sumOf { it.done }) }
            }
            if (!segment.isCompleted && attempts >= maxRetries) {
                throw InstallPipelineException(
                    InstallFailureCode.NETWORK,
                    "Segment ${segment.from}-${segment.to} incomplete after $attempts attempts",
                    lastError,
                )
            }
        }
        val tookMs = (System.currentTimeMillis() - partStartedAt).coerceAtLeast(1L)
        if (tookMs > 30_000L) {
            // 慢分片才留痕；正常分片完成不打日志（一个下载十几片，全是垃圾行）
            AppLog.log("Part ${segment.from}-${segment.to} done ${segment.done}B in ${tookMs}ms avg=${segment.done / tookMs}KB/s")
        }
    }

    private suspend fun fetchRange(
        url: String,
        headers: Map<String, String>,
        file: RandomAccessFile,
        writeLock: Any,
        segment: Segment,
        onProgress: suspend () -> Unit,
    ) {
        http.client.prepareGet(url) {
            applySourceHeaders(headers)
            header(HttpHeaders.Range, "bytes=${segment.current}-${segment.to}")
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw InstallPipelineException(
                    InstallFailureCode.NETWORK,
                    "Segment download failed: HTTP ${response.status.value}",
                )
            }
            if (response.request.url.protocol != URLProtocol.HTTPS) {
                throw InstallPipelineException(InstallFailureCode.NETWORK, "Package redirect left HTTPS")
            }
            // 服务器忽略 Range 返回全量：暂存已不可信
            if (response.status != HttpStatusCode.PartialContent) throw StaleStagingException()
            val range = parseContentRange(response.headers[HttpHeaders.ContentRange])
            if (range == null || range.from != segment.current) {
                throw InstallPipelineException(
                    InstallFailureCode.NETWORK,
                    "Server range mismatch: expected ${segment.current}-, got ${response.headers[HttpHeaders.ContentRange]}",
                )
            }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(IO_BUFFER_SIZE)
            while (!segment.isCompleted) {
                coroutineContext.ensureActive()
                // 与 AB howMuchCanRead 对齐：先在拆分锁内扩展安全区，再按安全区限制读取。
                val allowed = synchronized(writeLock) {
                    var remainingSafe = (segment.safeZone + 1 - segment.current).coerceAtLeast(0L)
                    if (remainingSafe < buffer.size && segment.current <= segment.to) {
                        val remaining = segment.to - segment.current + 1
                        segment.safeZone = (segment.safeZone + minOf(remaining, SAFE_ZONE_SIZE)).coerceAtMost(segment.to)
                        remainingSafe = (segment.safeZone + 1 - segment.current).coerceAtLeast(0L)
                    }
                    minOf(buffer.size.toLong(), remainingSafe).toInt()
                }
                if (allowed <= 0) break
                val read = withTimeoutOrNull(STALL_TIMEOUT_MS) {
                    channel.readAvailable(buffer, 0, allowed)
                } ?: throw IOException("Segment stalled: no data for ${STALL_TIMEOUT_MS}ms")
                if (read < 0) break
                if (read == 0) continue
                synchronized(writeLock) {
                    file.seek(segment.current)
                    file.write(buffer, 0, read)
                    segment.current += read
                }
                onProgress()
            }
        }
    }

    private suspend fun probe(url: String, headers: Map<String, String>): Long? =
        http.client.prepareGet(url) {
            applySourceHeaders(headers)
            header(HttpHeaders.Range, "bytes=0-0")
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw InstallPipelineException(
                    InstallFailureCode.NETWORK,
                    "Package download failed: HTTP ${response.status.value}",
                )
            }
            if (response.request.url.protocol != URLProtocol.HTTPS) {
                throw InstallPipelineException(InstallFailureCode.NETWORK, "Package redirect left HTTPS")
            }
            if (response.status != HttpStatusCode.PartialContent) return@execute null
            parseContentRange(response.headers[HttpHeaders.ContentRange])?.takeIf { it.total > 0L }?.total
        }

    private fun HttpRequestBuilder.applySourceHeaders(headers: Map<String, String>) {
        headers.forEach { (name, value) ->
            if (!name.equals(HttpHeaders.Host, ignoreCase = true) &&
                !name.equals(HttpHeaders.ContentLength, ignoreCase = true) &&
                !name.equals(HttpHeaders.Range, ignoreCase = true)
            ) {
                header(name, value)
            }
        }
        header(HttpHeaders.AcceptEncoding, "identity")
    }

    private fun parseContentRange(value: String?): ContentRange? {
        val match = value?.let { CONTENT_RANGE_REGEX.matchEntire(it.trim()) } ?: return null
        return ContentRange(
            from = match.groupValues[1].toLongOrNull() ?: return null,
            total = match.groupValues[3].toLongOrNull() ?: -1L,
        )
    }

    private fun stateFile(target: File): File = File(target.parentFile, "${target.name}.segments")

    private fun loadSegments(stateFile: File, total: Long, target: File): List<Segment>? {
        if (!stateFile.isFile || !target.isFile) return null
        if (target.length() > total) return null
        val lines = runCatching { stateFile.readLines() }.getOrNull() ?: return null
        if (lines.size < 3 || lines[0] != STATE_MAGIC) return null
        if (lines[1].removePrefix("total=").toLongOrNull() != total) return null
        val segments = lines.drop(2).map { line ->
            val match = SEGMENT_REGEX.matchEntire(line) ?: return null
            val from = match.groupValues[1].toLongOrNull() ?: return null
            val to = match.groupValues[2].toLongOrNull() ?: return null
            val current = match.groupValues[3].toLongOrNull() ?: return null
            if (from < 0 || to < from || to >= total || current < from || current > to + 1) return null
            Segment(from, to, current)
        }
        return segments
            .sortedBy { it.from }
            .takeIf { list ->
                list.isNotEmpty() && list.first().from == 0L && list.last().to == total - 1 &&
                    list.zipWithNext().all { (a, b) -> a.to + 1 == b.from }
            }
    }

    private companion object {
        const val STATE_MAGIC = "appmarket-segments-v1"
        const val MIN_SEGMENTED_SIZE = 4L * 1024 * 1024
        const val RETRY_DELAY_MS = 1_000L
        const val STALL_TIMEOUT_MS = 6_000L

        /** 整体停滞阈值：所有连接合计这么久没有新字节就判失败重来。 */
        const val STALL_TOTAL_MS = 30_000L


        /** worker 没拿到分片时的等待间隔。 */
        const val PART_WAIT_MS = 200L
        const val PROGRESS_SAVE_STEP = 8L * 1024 * 1024
        /** 状态文件最小落盘间隔。 */
        const val PROGRESS_SAVE_MIN_INTERVAL_MS = 1_000L
        const val IO_BUFFER_SIZE = 128 * 1024
        const val SAFE_ZONE_SIZE = 128L * 8192
        const val MAX_PART_COUNT = 16
        val CONTENT_RANGE_REGEX = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")
        val SEGMENT_REGEX = Regex("""(\d+)-(\d+):(\d+)""")
    }
}