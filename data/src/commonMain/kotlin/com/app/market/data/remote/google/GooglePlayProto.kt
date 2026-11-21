package com.app.market.data.remote.google

import com.app.market.data.platform.gzip

/**
 * protobuf 线格式的极简读写（不引 protobuf 运行时）：
 * [Pb] 解析响应树，[PbW] 拼请求体。字段号按 fdfe 协议实测/对照 gplayapi proto 定义。
 */
internal class Pb(
    val tag: Int,
    val wire: Int,
    val num: Long = 0L,
    val bytes: ByteArray? = null,
) {
    val children: List<Pb> by lazy { bytes?.let { parse(it) } ?: emptyList() }
    val asString: String get() = bytes?.decodeToString().orEmpty()

    companion object {
        fun parse(data: ByteArray): List<Pb> {
            val out = ArrayList<Pb>()
            var i = 0
            while (i < data.size) {
                val key = readVarint(data, i) ?: break
                i = key.second
                val tag = (key.first ushr 3).toInt()
                val wire = (key.first and 7L).toInt()
                when (wire) {
                    0 -> {
                        val v = readVarint(data, i) ?: break
                        i = v.second
                        out.add(Pb(tag, 0, num = v.first))
                    }
                    1 -> {
                        if (i + 8 > data.size) break
                        out.add(Pb(tag, 1, num = 0L, bytes = data.copyOfRange(i, i + 8)))
                        i += 8
                    }
                    2 -> {
                        val len = readVarint(data, i) ?: break
                        i = len.second
                        val size = len.first.toInt()
                        if (size < 0 || i + size > data.size) break
                        out.add(Pb(tag, 2, bytes = data.copyOfRange(i, i + size)))
                        i += size
                    }
                    5 -> {
                        if (i + 4 > data.size) break
                        i += 4
                    }
                    else -> break
                }
            }
            return out
        }

        private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int>? {
            var result = 0L
            var shift = 0
            var i = start
            while (i < data.size) {
                val b = data[i].toInt() and 0xFF
                i++
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result to i
                shift += 7
                if (shift > 63) return null
            }
            return null
        }
    }
}

internal fun List<Pb>.msg(tag: Int): Pb? = firstOrNull { it.tag == tag }
internal fun List<Pb>.msgs(tag: Int): List<Pb> = filter { it.tag == tag }
internal fun List<Pb>.str(tag: Int): String = msg(tag)?.asString.orEmpty()
internal fun List<Pb>.long(tag: Int): Long = msg(tag)?.num ?: 0L

/** 拼 protobuf 请求体。 */
internal object PbW {
    fun varint(tag: Int, value: Long): ByteArray {
        val out = ArrayList<Byte>()
        encodeVarint(out, (tag.toLong() shl 3) or 0L)
        encodeVarint(out, value)
        return out.toByteArray()
    }

    fun bytes(tag: Int, data: ByteArray): ByteArray {
        val out = ArrayList<Byte>()
        encodeVarint(out, (tag.toLong() shl 3) or 2L)
        encodeVarint(out, data.size.toLong())
        out.addAll(data.toList())
        return out.toByteArray()
    }

    fun str(tag: Int, value: String): ByteArray = bytes(tag, value.encodeToByteArray())
    fun msg(tag: Int, body: ByteArray): ByteArray = bytes(tag, body)

    private fun encodeVarint(out: MutableList<Byte>, value: Long) {
        var v = value
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v == 0L) {
                out.add(b.toByte())
                return
            }
            out.add((b or 0x80).toByte())
        }
    }
}

/** 纯 Kotlin Base64 编码（编码端足够，避免依赖平台 API）。 */
internal object B64 {
    private const val STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(data: ByteArray, urlSafe: Boolean, pad: Boolean): String {
        val table = if (urlSafe) URL else STD
        val out = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < data.size) {
            val chunk = ((data[i].toInt() and 0xFF) shl 16) or
                ((data[i + 1].toInt() and 0xFF) shl 8) or
                (data[i + 2].toInt() and 0xFF)
            out.append(table[(chunk ushr 18) and 63])
                .append(table[(chunk ushr 12) and 63])
                .append(table[(chunk ushr 6) and 63])
                .append(table[chunk and 63])
            i += 3
        }
        when (data.size - i) {
            1 -> {
                val chunk = (data[i].toInt() and 0xFF) shl 16
                out.append(table[(chunk ushr 18) and 63]).append(table[(chunk ushr 12) and 63])
                if (pad) out.append("==")
            }
            2 -> {
                val chunk = ((data[i].toInt() and 0xFF) shl 16) or ((data[i + 1].toInt() and 0xFF) shl 8)
                out.append(table[(chunk ushr 18) and 63])
                    .append(table[(chunk ushr 12) and 63])
                    .append(table[(chunk ushr 6) and 63])
                if (pad) out.append('=')
            }
        }
        return out.toString()
    }
}

/** gzip 后 URL-safe Base64（X-PS-RH 的编码方式）。 */
internal fun gzipBase64(data: ByteArray): String = B64.encode(gzip(data), urlSafe = true, pad = true)