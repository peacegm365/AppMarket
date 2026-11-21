package com.app.market.data.remote.coolapk

import com.app.market.data.platform.md5

/**
 * 酷安 X-App-Token（v3）签名器。
 *
 * 算法（已用抓包样本逐字节验证，cost 必须是 4）：
 *  1. idx = ((ts + versionCode) % 100) * 4 + 0x80
 *  2. segment = base64decode(blob[idx : idx + 0x80])
 *  3. plain = "com.coolapk.market" & segment & md5(device) & ts & versionCode
 *  4. pw = md5(base64(plain))
 *  5. saltSrc = base64(hex(ts) + "/" + md5(plain))，去掉末尾 "="
 *  6. salt22 = saltSrc[0:22]，末位在标准 base64 字母表里左移 5 位
 *  7. setting = "$2y$04$" + salt22
 *  8. hashed = bcrypt(pw, setting)
 *  9. token = "v3" + base64(hashed)，去掉末尾 "="
 *
 * 密钥表 [CoolapkAuthBlobBase64] 提取自酷安 APK 的 lib/arm64-v8a/libauth.so。
 */
internal object CoolapkAuth {
    private const val PackageName = "com.coolapk.market"
    private const val SaltPrefix = "\$2y\$04\$"
    private const val SaltSourceLength = 22
    private const val SegmentLength = 0x80
    private const val SegmentOffsetBase = 0x80

    /** 标准 base64 字母表，用于盐末位偏移。 */
    private const val StandardBase64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val AuthBlob: ByteArray = base64Decode(CoolapkAuthBlobBase64)

    /**
     * 生成指定时间戳的 v3 token。盐由时间戳派生，部分秒会得到非法盐，
     * 抛 [IllegalArgumentException] 时调用方应换时间戳重试。
     */
    fun tokenAt(device: String, timestamp: Long, versionCode: Int): String {
        val index = (((timestamp + versionCode) % 100) * 4 + SegmentOffsetBase).toInt()
        require(index + SegmentLength <= AuthBlob.size) { "酷安密钥表索引越界：idx=$index" }
        val segment = base64Decode(AuthBlob.decodeToString(index, index + SegmentLength))

        // plain 一律按字节拼接：segment 是二进制，不能经 String 中转
        val md5Device = hex(md5(device.encodeToByteArray()))
        val plain = buildBytes {
            append(PackageName.encodeToByteArray())
            append(byteArrayOf('&'.code.toByte()))
            append(segment)
            append(byteArrayOf('&'.code.toByte()))
            append(md5Device.encodeToByteArray())
            append(byteArrayOf('&'.code.toByte()))
            append(timestamp.toString().encodeToByteArray())
            append(byteArrayOf('&'.code.toByte()))
            append(versionCode.toString().encodeToByteArray())
        }

        val pw = hex(md5(base64Encode(plain).encodeToByteArray()))
        val md5Plain = hex(md5(plain))
        val saltSource = base64Encode("${timestamp.toString(16)}/$md5Plain".encodeToByteArray())
            .trimEnd('=')
        val salt22 = shiftLastChar(saltSource.substring(0, SaltSourceLength), shift = -5)
        val hashed = CoolapkBcrypt.hashpw(pw.encodeToByteArray(), SaltPrefix + salt22)
        return "v3" + base64Encode(hashed.encodeToByteArray()).trimEnd('=')
    }

    /**
     * 生成当前时刻可用的 token；对非法盐做时间戳前探（最多 [maxAhead] 秒）。
     */
    fun token(device: String, versionCode: Int, nowSeconds: Long, maxAhead: Int = 15): String {
        var lastError: Exception? = null
        for (offset in 0..maxAhead) {
            try {
                return tokenAt(device, nowSeconds + offset, versionCode)
            } catch (error: IllegalArgumentException) {
                lastError = error
            }
        }
        throw IllegalStateException("酷安 token 生成失败：窗口内均无法生成有效盐", lastError)
    }

    /** 把字符串最后一位在标准 base64 字母表里偏移 [shift] 位（mod 64）。 */
    private fun shiftLastChar(value: String, shift: Int): String {
        val index = StandardBase64.indexOf(value.last())
        require(index >= 0) { "非法 base64 字符：${value.last()}" }
        val length = StandardBase64.length
        val moved = ((index + shift) % length + length) % length
        return value.dropLast(1) + StandardBase64[moved]
    }

    private fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { append((it.toInt() and 0xff).toString(16).padStart(2, '0')) }
    }
}

/** 按字节拼接构建数组，避免二进制片段经 String 中转被破坏。 */
private fun buildBytes(block: MutableList<Byte>.() -> Unit): ByteArray {
    val buffer = ArrayList<Byte>()
    buffer.block()
    return buffer.toByteArray()
}

private fun MutableList<Byte>.append(bytes: ByteArray) {
    bytes.forEach { add(it) }
}

/** 标准 base64 解码（带 "=" 填充，容忍缺失填充）。 */
internal fun base64Decode(text: String): ByteArray {
    val clean = text.trim().replace("\n", "").replace("\r", "")
    val output = ArrayList<Byte>(clean.length * 3 / 4 + 3)
    var buffer = 0
    var bits = 0
    for (char in clean) {
        if (char == '=') break
        val value = when (char) {
            in 'A'..'Z' -> char - 'A'
            in 'a'..'z' -> char - 'a' + 26
            in '0'..'9' -> char - '0' + 52
            '+' -> 62
            '/' -> 63
            else -> continue
        }
        buffer = (buffer shl 6) or value
        bits += 6
        if (bits >= 8) {
            bits -= 8
            output.add(((buffer shr bits) and 0xff).toByte())
        }
    }
    return output.toByteArray()
}

/** 标准 base64 编码（带 "=" 填充）。 */
internal fun base64Encode(data: ByteArray): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val output = StringBuilder((data.size + 2) / 3 * 4)
    var index = 0
    while (index < data.size) {
        val remaining = data.size - index
        val b0 = data[index].toInt() and 0xff
        val b1 = if (remaining > 1) data[index + 1].toInt() and 0xff else 0
        val b2 = if (remaining > 2) data[index + 2].toInt() and 0xff else 0
        output.append(alphabet[b0 shr 2])
        output.append(alphabet[((b0 and 0x03) shl 4) or (b1 shr 4)])
        output.append(if (remaining > 1) alphabet[((b1 and 0x0f) shl 2) or (b2 shr 6)] else '=')
        output.append(if (remaining > 2) alphabet[b2 and 0x3f] else '=')
        index += 3
    }
    return output.toString()
}
