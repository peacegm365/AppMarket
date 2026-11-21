package com.app.market.data.proxy

import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred

/**
 * AnyTLS 节点（订阅里 anytls:// 一条）。
 *
 * @param password 订阅里的 userinfo，实质是账号密码（uuid 形式）。
 * @param sni TLS 握手的 SNI；节点通常要求伪装成某个域名。
 * @param insecure 是否跳过证书校验（订阅里的 insecure=1）。
 */
internal data class AnyTlsNode(
    val name: String,
    val server: String,
    val port: Int,
    val password: String,
    val sni: String,
    val insecure: Boolean,
)

/** 订阅解析结果：真节点 + 机场塞在节点列表里的信息条目（剩余流量/到期时间等）。 */
internal data class ParsedSubscription(
    val nodes: List<AnyTlsNode>,
    val info: List<String>,
)

/** 解析订阅：既支持 base64 编码的链接列表，也支持明文（v2ray 订阅格式）。 */
internal object AnyTlsSubscription {

    /** 这些名字不是节点，是机场把套餐信息伪装成节点塞进来的。 */
    private val INFO_KEYWORDS = listOf("剩余流量", "距离下次", "套餐到期", "到期时间", "重置剩余", "官网", "过期")

    fun parse(raw: String): List<AnyTlsNode> = parseAll(raw).nodes

    fun parseAll(raw: String): ParsedSubscription {
        val text = decode(raw)
        val names = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val info = names.mapNotNull { line ->
            val fragment = line.substringAfter('#', "")
            val name = decodeComponent(fragment)
            if (name.isNotBlank() && INFO_KEYWORDS.any { name.contains(it) }) name else null
        }.distinct()
        val nodes = names
            .mapNotNull { parseLine(it) }
            .filterNot { node -> INFO_KEYWORDS.any { node.name.contains(it) } }
            .distinctBy { it.server + ":" + it.port + "#" + it.name }
        return ParsedSubscription(nodes, info)
    }

    private fun decode(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.contains("://")) return trimmed
        return runCatching {
            String(Base64.getMimeDecoder().decode(trimmed), Charsets.UTF_8)
        }.getOrDefault(trimmed)
    }

    private fun parseLine(line: String): AnyTlsNode? {
        if (!line.startsWith("anytls://", ignoreCase = true)) return null
        var body = line.substring("anytls://".length)
        val hash = body.indexOf('#')
        val fragment = if (hash >= 0) body.substring(hash + 1) else ""
        if (hash >= 0) body = body.substring(0, hash)
        val q = body.indexOf('?')
        val query = if (q >= 0) body.substring(q + 1) else ""
        if (q >= 0) body = body.substring(0, q)
        val at = body.lastIndexOf('@')
        if (at < 0) return null
        val password = decodeComponent(body.substring(0, at))
        val hostPort = body.substring(at + 1)
        val colon = hostPort.lastIndexOf(':')
        if (colon <= 0) return null
        val server = hostPort.substring(0, colon).trim('[', ']')
        val port = hostPort.substring(colon + 1).toIntOrNull() ?: return null
        val params = query.split('&')
            .mapNotNull { item ->
                val i = item.indexOf('=')
                if (i <= 0) null else decodeComponent(item.substring(0, i)) to decodeComponent(item.substring(i + 1))
            }
            .toMap()
        val sni = params["sni"]?.takeIf { it.isNotBlank() } ?: server
        val insecure = params["insecure"] == "1" || params["allowInsecure"] == "1"
        val name = decodeComponent(fragment).ifBlank { "$server:$port" }
        if (server.isBlank() || port !in 1..65535) return null
        return AnyTlsNode(name, server, port, password, sni, insecure)
    }

    private fun decodeComponent(text: String): String =
        runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
}

/** AnyTLS 会话：一条 TLS 连接上复用多条 Stream。 */
internal class AnyTlsSession(private val node: AnyTlsNode) {

    private lateinit var socket: SSLSocket
    private lateinit var input: InputStream
    private lateinit var output: java.io.OutputStream
    private val writeLock = Any()
    private val streams = ConcurrentHashMap<Int, AnyTlsStream>()
    private val seq = AtomicInteger(1)

    /** 收到服务端 settings（或 padding 方案下发）就说明认证通过、链路可用。 */
    private val readySignal = CompletableDeferred<Unit>()

    /** 等服务器确认会话可用；超时/失败抛异常。 */
    suspend fun awaitReady() = readySignal.await()

    @Volatile
    var alive: Boolean = false
        private set

    @Volatile
    var lastError: String = ""
        private set

    fun open() {
        val raw = Socket()
        raw.tcpNoDelay = true
        raw.connect(InetSocketAddress(node.server, node.port), CONNECT_TIMEOUT_MS)
        raw.soTimeout = READ_TIMEOUT_MS
        val context = SSLContext.getInstance("TLS")
        context.init(null, if (node.insecure) arrayOf<TrustManager>(TrustAllManager) else null, SecureRandom())
        val ssl = context.socketFactory.createSocket(raw, node.sni, node.port, true) as SSLSocket
        ssl.sslParameters = ssl.sslParameters.apply {
            serverNames = listOf(SNIHostName(node.sni))
            if (!node.insecure) endpointIdentificationAlgorithm = "HTTPS"
        }
        ssl.startHandshake()
        socket = ssl
        input = ssl.inputStream
        output = ssl.outputStream
        // 认证：sha256(password) + padding0 长度 + padding0
        val digest = MessageDigest.getInstance("SHA-256").digest(node.password.toByteArray())
        val padding = ByteArray(PADDING0_LENGTH)
        writeRaw(digest + byteArrayOf(0, PADDING0_LENGTH.toByte()) + padding)
        // 协议要求：每次新会话的第一个帧必须是 settings
        writeFrame(CMD_SETTINGS, 0, SETTINGS.toByteArray())
        alive = true
        thread(isDaemon = true, name = "anytls-reader") { readLoop() }
    }

    fun openStream(host: String, port: Int): AnyTlsStream {
        if (!alive) throw IOException("AnyTLS 会话已关闭")
        val id = seq.getAndIncrement()
        val stream = AnyTlsStream(this, id)
        streams[id] = stream
        synchronized(writeLock) {
            // 包 1：SYN + 目标地址（SocksAddr）一起发，避免多一次 TLS 记录
            output.write(frameBytes(CMD_SYN, id, EMPTY) + frameBytes(CMD_PSH, id, socksAddress(host, port)))
            output.flush()
        }
        return stream
    }

    fun writeFrame(command: Int, streamId: Int, data: ByteArray) {
        synchronized(writeLock) {
            output.write(frameBytes(command, streamId, data))
            output.flush()
        }
    }

    fun close() {
        alive = false
        streams.values.forEach { it.onFin() }
        streams.clear()
        runCatching { socket.close() }
    }

    private fun writeRaw(bytes: ByteArray) {
        synchronized(writeLock) {
            output.write(bytes)
            output.flush()
        }
    }

    private fun frameBytes(command: Int, streamId: Int, data: ByteArray): ByteArray {
        require(data.size <= MAX_FRAME_SIZE) { "帧过大" }
        val out = ByteArray(7 + data.size)
        out[0] = command.toByte()
        out[1] = (streamId ushr 24).toByte()
        out[2] = (streamId ushr 16).toByte()
        out[3] = (streamId ushr 8).toByte()
        out[4] = streamId.toByte()
        out[5] = (data.size ushr 8).toByte()
        out[6] = data.size.toByte()
        data.copyInto(out, 7)
        return out
    }

    private fun socksAddress(host: String, port: Int): ByteArray {
        val hostBytes = host.toByteArray()
        val head = if (isIpv4(host)) {
            byteArrayOf(ATYP_IPV4) + host.split('.').map { it.toInt().toByte() }.toByteArray()
        } else {
            byteArrayOf(ATYP_DOMAIN, hostBytes.size.toByte()) + hostBytes
        }
        return head + byteArrayOf((port ushr 8).toByte(), port.toByte())
    }

    private fun isIpv4(host: String): Boolean =
        host.count { it == '.' } == 3 && host.split('.').all { part ->
            part.toIntOrNull()?.let { it in 0..255 } == true
        }

    private fun readLoop() {
        val header = ByteArray(7)
        try {
            while (true) {
                readFully(header, 7)
                val command = header[0].toInt() and 0xFF
                val streamId = ((header[1].toInt() and 0xFF) shl 24) or
                    ((header[2].toInt() and 0xFF) shl 16) or
                    ((header[3].toInt() and 0xFF) shl 8) or
                    (header[4].toInt() and 0xFF)
                val length = ((header[5].toInt() and 0xFF) shl 8) or (header[6].toInt() and 0xFF)
                val data = if (length > 0) ByteArray(length).also { readFully(it, length) } else EMPTY
                when (command) {
                    CMD_WASTE -> Unit
                    CMD_PSH -> streams[streamId]?.onData(data)
                    CMD_FIN -> streams.remove(streamId)?.onFin()
                    CMD_SYNACK -> if (data.isNotEmpty()) {
                        streams.remove(streamId)?.onError(String(data))
                    }
                    CMD_ALERT -> {
                        lastError = String(data)
                        readySignal.completeExceptionally(IOException(String(data)))
                        break
                    }
                    CMD_SERVER_SETTINGS, CMD_UPDATE_PADDING -> {
                        if (!readySignal.isCompleted) readySignal.complete(Unit)
                    }
                    CMD_HEART_REQUEST -> writeFrame(CMD_HEART_RESPONSE, 0, EMPTY)
                    else -> Unit
                }
            }
        } catch (error: Throwable) {
            lastError = error.message ?: error.toString()
            if (!readySignal.isCompleted) readySignal.completeExceptionally(error)
        } finally {
            alive = false
            streams.values.forEach { it.onFin() }
            streams.clear()
            runCatching { socket.close() }
        }
    }

    private fun readFully(target: ByteArray, length: Int) {
        var read = 0
        while (read < length) {
            val count = input.read(target, read, length - read)
            if (count < 0) throw IOException("AnyTLS 连接被关闭")
            read += count
        }
    }

    private object TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val PADDING0_LENGTH = 30
        private const val MAX_FRAME_SIZE = 16_384
        private const val ATYP_IPV4 = 1.toByte()
        private const val ATYP_DOMAIN = 3.toByte()
        private const val CMD_WASTE = 0
        private const val CMD_SYN = 1
        private const val CMD_PSH = 2
        private const val CMD_FIN = 3
        private const val CMD_SETTINGS = 4
        private const val CMD_ALERT = 5
        private const val CMD_UPDATE_PADDING = 6
        private const val CMD_SYNACK = 7
        private const val CMD_HEART_REQUEST = 8
        private const val CMD_HEART_RESPONSE = 9
        private const val CMD_SERVER_SETTINGS = 10
        private val EMPTY = ByteArray(0)
        private const val SETTINGS = "v=2\nclient=AppMarket/2.4.4\npadding-md5=00000000000000000000000000000000"
    }
}

/** 会话里的一条流，对应一个被代理的 TCP 连接。 */
internal class AnyTlsStream(private val session: AnyTlsSession, private val streamId: Int) {

    private val lock = Object()
    private val chunks = ArrayDeque<ByteArray>()
    private var eof = false
    private var failure: String? = null
    private var closed = false

    fun onData(data: ByteArray) {
        if (data.isEmpty()) return
        synchronized(lock) {
            chunks.addLast(data)
            lock.notifyAll()
        }
    }

    fun onFin() {
        synchronized(lock) {
            eof = true
            lock.notifyAll()
        }
    }

    fun onError(message: String) {
        synchronized(lock) {
            failure = message
            eof = true
            lock.notifyAll()
        }
    }

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        synchronized(lock) {
            while (chunks.isEmpty() && !eof) {
                lock.wait(READ_WAIT_MS)
            }
            val chunk = chunks.firstOrNull()
            if (chunk == null) {
                failure?.let { throw IOException(it) }
                return -1
            }
            val count = minOf(length, chunk.size)
            chunk.copyInto(buffer, offset, 0, count)
            if (count == chunk.size) chunks.removeFirst() else chunks[0] = chunk.copyOfRange(count, chunk.size)
            return count
        }
    }

    fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (closed) throw IOException("stream closed")
        var position = offset
        var left = length
        while (left > 0) {
            val count = minOf(left, MAX_CHUNK)
            session.writeFrame(CMD_PSH, streamId, buffer.copyOfRange(position, position + count))
            position += count
            left -= count
        }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { session.writeFrame(CMD_FIN, streamId, ByteArray(0)) }
    }

    companion object {
        private const val MAX_CHUNK = 16_000
        private const val READ_WAIT_MS = 30_000L
        private const val CMD_PSH = 2
        private const val CMD_FIN = 3
    }
}