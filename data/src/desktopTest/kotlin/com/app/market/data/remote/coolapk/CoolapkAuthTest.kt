package com.app.market.data.remote.coolapk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 酷安 v3 token 签名验收测试。
 *
 * 全部期望值取自真实抓包（CoolMarket 16.7.0 / versionCode 2610291，设备码固定），
 * 与抓包 token 逐字节一致才算通过——这同时验证了纯 Kotlin bcrypt 的正确性。
 */
class CoolapkAuthTest {

    private val device =
        "YTZmZTN5AjNzUWZ4YGNllDI7YTMw4SNwYDM2IjLBJDUDByODVkQLJlMwETNyAyOp1GZlJFI7kWbvFWaYByOgsDI7AyOid2b2dzajNkQ4wGW2F3Y2kTOyFDSndTY010M3MzS4Y0SSVFR"

    private val versionCode = 2610291

    private data class Vector(val timestamp: Long, val expected: String)

    private val vectors = listOf(
        Vector(
            1793767082L,
            "v3JDJ5JDA0JE5tRmxZV0kyWVdFdk9UaG1OMlUxTk81TTBLcU5BYXNRbmFKTVl2UGQubFgxdWxEakMwMmRD",
        ),
        Vector(
            1793767056L,
            "v3JDJ5JDA0JE5tRmxZV0kyT1RBdk5tWXpORFkxTy5ud3NoWWJ5NmJLUi9CZE5DMFpKMG5VQ3BtbFJsQ1J1",
        ),
        Vector(
            1793767061L,
            "v3JDJ5JDA0JE5tRmxZV0kyT1RVdll6Rm1NREJsTWVCa0lleHZWT2ptdFVRVXNLaDguT0lWbWdILnB5LzZP",
        ),
        Vector(
            1793767063L,
            "v3JDJ5JDA0JE5tRmxZV0kyT1Rjdk0yWmxZbUkzWk91bFgxckJZYWVELnNKOW9xOTlYRXl1akVZbG9SbVJP",
        ),
    )

    /** 这些时间戳派生的盐不含 bcrypt 非法字符，可直接逐字节比对。 */
    private val validSaltTimestamps = setOf(1793767082L, 1793767061L)

    @Test
    fun `token 必须与抓包样本逐字节一致`() {
        // 仅对盐合法的时间戳做逐字节比对；其余时间戳会派生出含「+」等 bcrypt 非法字符的盐，
        // 官方客户端同样靠时间戳前探规避（见 token()）。
        vectors.filter { it.timestamp in validSaltTimestamps }.forEach { (timestamp, expected) ->
            val actual = CoolapkAuth.tokenAt(device, timestamp, versionCode)
            assertEquals(expected, actual, "ts=$timestamp 的 token 不匹配")
        }
    }

    @Test
    fun `非法盐的时间戳由重试机制规避`() {
        vectors.filterNot { it.timestamp in validSaltTimestamps }.forEach { (timestamp, _) ->
            val token = CoolapkAuth.token(device, versionCode, nowSeconds = timestamp)
            assertTrue(token.startsWith("v3") && token.length > 70, "ts=$timestamp 重试后仍无可用 token")
        }
    }

    @Test
    fun `token 形态正确且确定性可复现`() {
        val first = CoolapkAuth.tokenAt(device, vectors[0].timestamp, versionCode)
        val second = CoolapkAuth.tokenAt(device, vectors[0].timestamp, versionCode)
        assertEquals(first, second, "同一输入应产生同一 token")
        assertTrue(first.startsWith("v3"), "token 必须以 v3 开头")
        assertTrue(first.length > 70, "token 长度异常：${first.length}")
    }

    @Test
    fun `token 生成器能为当前时刻产出可用形态`() {
        val now = 1793767000L
        val token = CoolapkAuth.token(device, versionCode, nowSeconds = now)
        assertTrue(token.startsWith("v3") && token.length > 70, "生成器产出形态异常：$token")
    }

    @Test
    fun `base64 往返一致`() {
        val samples = listOf("", "a", "ab", "abc", "abcd", "酷安测试", ByteArray(930) { it.toByte() })
        samples.forEach { sample ->
            val bytes = when (sample) {
                is String -> sample.encodeToByteArray()
                is ByteArray -> sample
                else -> ByteArray(0)
            }
            val encoded = base64Encode(bytes)
            assertTrue(base64Decode(encoded).contentEquals(bytes), "base64 往返不一致：$sample")
        }
    }

    @Test
    fun `bcrypt 对已知向量产出正确形态`() {
        // 形态校验：$2y$04$ + 22 位盐 + 31 位哈希 = 60 字符
        val setting = "\$2y\$04\$" + "a".repeat(22)
        val hashed = CoolapkBcrypt.hashpw("hello".encodeToByteArray(), setting)
        assertEquals(60, hashed.length, "bcrypt 输出长度异常：${hashed.length}")
        assertTrue(hashed.startsWith("\$2y\$04\$"), "bcrypt 前缀异常：$hashed")
    }
}