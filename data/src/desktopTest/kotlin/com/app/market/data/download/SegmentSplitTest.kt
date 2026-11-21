package com.app.market.data.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SegmentSplitTest {
    @Test
    fun splitsEvenlyWithoutRemainder() {
        val ranges = splitToRanges(size = 400L, minPartSize = 1L, maxPartCount = 4)
        assertEquals(listOf(0L..99L, 100L..199L, 200L..299L, 300L..399L), ranges)
    }

    @Test
    fun distributesRemainderToLeadingSegments() {
        val ranges = splitToRanges(size = 10L, minPartSize = 1L, maxPartCount = 4)
        assertEquals(listOf(0L..2L, 3L..5L, 6L..7L, 8L..9L), ranges)
    }

    @Test
    fun respectsMinPartSize() {
        val ranges = splitToRanges(size = 100L, minPartSize = 60L, maxPartCount = 8)
        assertEquals(listOf(0L..49L, 50L..99L), ranges)
    }

    @Test
    fun coversWholeFileContiguously() {
        val size = 1_000_003L
        val ranges = splitToRanges(size = size, minPartSize = 256L, maxPartCount = 16)
        assertEquals(0L, ranges.first().first)
        assertEquals(size - 1, ranges.last().last)
        ranges.zipWithNext { a, b -> assertEquals(a.last + 1, b.first) }
        assertEquals(size, ranges.sumOf { it.last - it.first + 1 })
    }

    @Test
    fun rejectsInvalidArguments() {
        assertFailsWith<IllegalArgumentException> { splitToRanges(size = 0L, minPartSize = 1L, maxPartCount = 1) }
        assertFailsWith<IllegalArgumentException> { splitToRanges(size = 1L, minPartSize = 0L, maxPartCount = 1) }
        assertFailsWith<IllegalArgumentException> { splitToRanges(size = 1L, minPartSize = 1L, maxPartCount = 0) }
    }
}