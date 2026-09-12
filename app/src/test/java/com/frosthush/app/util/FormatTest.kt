package com.frosthush.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `countdown 不足一小时用 MM_SS`() {
        assertEquals("00:00", Format.countdown(0))
        assertEquals("00:59", Format.countdown(59_999))
        assertEquals("09:58", Format.countdown(9 * 60_000L + 58_000L))
    }

    @Test
    fun `countdown 超一小时用 HH_MM_SS`() {
        assertEquals("01:00:00", Format.countdown(3_600_000))
        assertEquals("02:03:04", Format.countdown(2 * 3_600_000L + 3 * 60_000L + 4_000L))
    }

    @Test
    fun `countdown 负值钳制为 0`() {
        assertEquals("00:00", Format.countdown(-1234))
    }

    @Test
    fun `hourBucket 分桶边界`() {
        val base = 1_780_000_000_000L
        fun at(hour: Int) = java.util.Calendar.getInstance().apply {
            set(2026, 8, 14, hour, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(0, Format.hourBucket(at(0)))
        assertEquals(0, Format.hourBucket(at(11)))
        assertEquals(1, Format.hourBucket(at(12)))
        assertEquals(1, Format.hourBucket(at(17)))
        assertEquals(2, Format.hourBucket(at(18)))
        assertEquals(2, Format.hourBucket(at(23)))
    }
}
