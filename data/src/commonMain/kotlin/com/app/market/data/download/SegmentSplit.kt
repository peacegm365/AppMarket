package com.app.market.data.download

/** 把 [size] 字节切分为连续范围分片：片数受 [maxPartCount] 与 [minPartSize] 约束，余数分给靠前的分片。 */
internal fun splitToRanges(size: Long, minPartSize: Long, maxPartCount: Int): List<LongRange> {
    require(size >= 1) { "size must be >= 1, passed: $size" }
    require(minPartSize >= 1) { "minPartSize must be >= 1, passed: $minPartSize" }
    require(maxPartCount >= 1) { "maxPartCount must be >= 1, passed: $maxPartCount" }
    val minParts = (size + minPartSize - 1) / minPartSize
    val partCount = minOf(maxPartCount.toLong(), minParts).toInt().coerceAtLeast(1)
    val idealPartSize = size / partCount
    val remainder = size % partCount
    val ranges = mutableListOf<LongRange>()
    var start = 0L
    for (index in 0 until partCount) {
        val end = start + idealPartSize - 1 + if (index < remainder) 1L else 0L
        ranges.add(start..end)
        start = end + 1
    }
    return ranges
}
