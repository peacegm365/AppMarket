package com.app.market.domain.diagnostic

/**
 * 极轻量的运行期埋点。
 *
 * 由平台层（Android 的 CrashLogger）挂上 [sink] 后即可把任意界面事件落到崩溃日志，
 * 用于在拿不到 logcat 的环境里定位「点了没反应」这类问题。未挂 sink 时静默丢弃。
 */
object AppLog {
    /** 平台注入的实际写入器。 */
    var sink: ((String) -> Unit)? = null

    /** 平台注入的二进制转储器（把原始响应落盘，用于离线定案协议）。未挂时静默丢弃。 */
    var byteSink: ((String, ByteArray) -> Unit)? = null

    fun log(message: String) {
        sink?.invoke(message)
    }

    /** 把原始响应字节落到文件，供离线还原协议结构。 */
    fun dumpBytes(name: String, bytes: ByteArray) {
        byteSink?.invoke(name, bytes)
    }
}