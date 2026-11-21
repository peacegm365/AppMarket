package com.app.market.data.proxy

import com.app.market.data.platform.ProxyHosts
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.repository.ProxyMode
import com.app.market.domain.repository.ProxySettingsRepository
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory
import okhttp3.Dns

/**
 * 全局 AnyTLS 运行时：持有当前节点与共享会话。
 *
 * App 的流量不再经过本地端口：OkHttp 建连时用 [TunnelSocketFactory] 直接在这条 TLS 会话上开一条 Stream，
 * 目标地址以 SocksAddr 形式交给远端节点解析，等于「把这条连接换个服务器出去」。
 */
internal object AnyTlsRuntime {

    private val lock = Any()
    private var node: AnyTlsNode? = null
    private var session: AnyTlsSession? = null

    @Volatile
    var lastError: String = ""
        private set

    val current: AnyTlsNode? get() = synchronized(lock) { node }

    fun isReady(): Boolean = synchronized(lock) { node != null }

    fun setNode(value: AnyTlsNode?) {
        synchronized(lock) {
            node = value
            runCatching { session?.close() }
            session = null
            lastError = ""
        }
    }

    fun stop() {
        synchronized(lock) {
            node = null
            runCatching { session?.close() }
            session = null
        }
    }

    /** 在会话上开一条到 host:port 的流；会话不可用会重连一次。 */
    fun openStream(host: String, port: Int): AnyTlsStream {
        synchronized(lock) {
            val target = node ?: throw IOException("尚未选择代理节点")
            var current = session
            if (current == null || !current.alive) {
                current = AnyTlsSession(target)
                current.open()
                session = current
            }
            return runCatching { current.openStream(host, port) }
                .getOrElse { error ->
                    // 复用的会话可能已失效：重建一次
                    runCatching { current.close() }
                    val retry = AnyTlsSession(target)
                    retry.open()
                    session = retry
                    runCatching { retry.openStream(host, port) }
                        .getOrElse { throwable ->
                            lastError = throwable.message ?: throwable.toString()
                            throw throwable
                        }
                }
        }
    }
}

/** 判断某个 host 是否应该走代理：与原来 ProxySelector 的规则一致。 */
internal object ProxyDecision {

    fun shouldTunnel(proxy: ProxySettingsRepository, host: String?): Boolean {
        if (!proxy.enabled.value) return false
        if (!AnyTlsRuntime.isReady()) return false
        val source = ProxyHosts.sourceOf(host) ?: return false
        val hostLower = host?.lowercase().orEmpty()
        val effectiveSources = if (source == AppSource.GITHUB && hostLower.endsWith("objects.githubusercontent.com")) {
            setOf(AppSource.GITHUB, AppSource.LSPOSED)
        } else {
            setOf(source)
        }
        return when (proxy.mode.value) {
            ProxyMode.WHITELIST -> effectiveSources.any { it in proxy.whitelist.value }
            ProxyMode.BLACKLIST -> effectiveSources.none { it in proxy.blacklist.value }
        }
    }
}

/**
 * 走代理的 host 用一个「假地址」交给 OkHttp：InetAddress 里带上域名本身，
 * 这样 [TunnelSocket] 还能拿到域名（远端解析 DNS），而不是本机先解析出来的 IP。
 */
internal class TunnelDns(private val proxy: ProxySettingsRepository) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        if (ProxyDecision.shouldTunnel(proxy, hostname)) {
            return listOf(InetAddress.getByAddress(hostname, PLACEHOLDER))
        }
        return Dns.SYSTEM.lookup(hostname)
    }

    private companion object {
        val PLACEHOLDER = byteArrayOf(127, 0, 0, 1)
    }
}

/** OkHttp 的建连入口：建连时决定「隧道」还是「直连」。 */
internal class TunnelSocketFactory(private val proxy: ProxySettingsRepository) : SocketFactory() {

    override fun createSocket(): Socket = TunnelSocket(proxy)

    override fun createSocket(host: String, port: Int): Socket =
        TunnelSocket(proxy).apply { connect(InetSocketAddress(host, port), DEFAULT_TIMEOUT) }

    override fun createSocket(host: String, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        TunnelSocket(proxy).apply { connect(InetSocketAddress(host, port), DEFAULT_TIMEOUT) }

    override fun createSocket(host: InetAddress, port: Int): Socket =
        TunnelSocket(proxy).apply { connect(InetSocketAddress(host, port), DEFAULT_TIMEOUT) }

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int,
    ): Socket = TunnelSocket(proxy).apply { connect(InetSocketAddress(address, port), DEFAULT_TIMEOUT) }

    private companion object {
        const val DEFAULT_TIMEOUT = 20_000
    }
}

/**
 * 既是「隧道流」也是「直连 socket」的适配层：OkHttp 只认 Socket 接口，
 * 我们不想要本地端口，就只能在这里把任意 Stream 伪装成一个 Socket。
 */
private class TunnelSocket(private val proxy: ProxySettingsRepository) : Socket() {

    private var stream: AnyTlsStream? = null
    private var real: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var soTimeout: Int = 0
    private var connected = false
    private var closed = false

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        val target = endpoint as? InetSocketAddress ?: throw IOException("不支持的地址类型")
        val host = target.hostString
        val port = target.port
        if (ProxyDecision.shouldTunnel(proxy, host)) {
            stream = AnyTlsRuntime.openStream(host, port)
            connected = true
            return
        }
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(InetAddress.getByName(host), port), if (timeout > 0) timeout else 20_000)
        if (soTimeout > 0) socket.soTimeout = soTimeout
        real = socket
        connected = true
    }

    override fun getInputStream(): InputStream {
        input?.let { return it }
        val created = stream?.let { AnyTlsInputStream(it) } ?: real?.getInputStream()
            ?: throw IOException("未建立连接")
        input = created
        return created
    }

    override fun getOutputStream(): OutputStream {
        output?.let { return it }
        val created = stream?.let { AnyTlsOutputStream(it) } ?: real?.getOutputStream()
            ?: throw IOException("未建立连接")
        output = created
        return created
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { stream?.close() }
        runCatching { real?.close() }
        stream = null
        real = null
    }

    override fun shutdownInput() {
        runCatching { stream?.onFin() }
    }

    override fun shutdownOutput() {
        runCatching { stream?.close() }
    }

    override fun isConnected(): Boolean = connected
    override fun isClosed(): Boolean = closed
    override fun isBound(): Boolean = connected

    override fun setSoTimeout(timeout: Int) {
        soTimeout = timeout
        runCatching { real?.soTimeout = timeout }
    }

    override fun getSoTimeout(): Int = soTimeout
    override fun setTcpNoDelay(on: Boolean) = Unit
    override fun setKeepAlive(on: Boolean) = Unit
    override fun setSoLinger(on: Boolean, linger: Int) = Unit

    override fun toString(): String = if (stream != null) "AnyTlsSocket" else "DirectSocket"
}

private class AnyTlsInputStream(private val stream: AnyTlsStream) : InputStream() {
    override fun read(): Int {
        val buffer = ByteArray(1)
        val count = read(buffer, 0, 1)
        return if (count <= 0) -1 else buffer[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
    override fun close() = stream.close()
}

private class AnyTlsOutputStream(private val stream: AnyTlsStream) : OutputStream() {
    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
    override fun write(b: ByteArray, off: Int, len: Int) = stream.write(b, off, len)
    override fun close() = stream.close()
}