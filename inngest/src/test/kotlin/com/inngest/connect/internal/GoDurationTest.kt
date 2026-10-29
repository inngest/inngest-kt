package com.inngest.connect.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class GoDurationTest {
    @Test
    fun `parses simple durations`() {
        assertEquals(10_000L, GoDuration.toMillisOrNull("10s"))
        assertEquals(500L, GoDuration.toMillisOrNull("500ms"))
        assertEquals(60_000L, GoDuration.toMillisOrNull("1m"))
        assertEquals(3_600_000L, GoDuration.toMillisOrNull("1h"))
        assertEquals(0L, GoDuration.toMillisOrNull("0"))
        assertEquals(0L, GoDuration.toMillisOrNull("0s"))
    }

    @Test
    fun `parses compound and fractional durations`() {
        assertEquals(90_000L, GoDuration.toMillisOrNull("1m30s"))
        assertEquals(1_500L, GoDuration.toMillisOrNull("1.5s"))
        assertEquals(3_661_000L, GoDuration.toMillisOrNull("1h1m1s"))
    }

    @Test
    fun `sub-millisecond units truncate toward zero`() {
        assertEquals(0L, GoDuration.toMillisOrNull("100ns"))
        assertEquals(1L, GoDuration.toMillisOrNull("1000us"))
    }

    @Test
    fun `invalid values return null`() {
        assertNull(GoDuration.toMillisOrNull(null))
        assertNull(GoDuration.toMillisOrNull(""))
        assertNull(GoDuration.toMillisOrNull("  "))
        assertNull(GoDuration.toMillisOrNull("10"))
        assertNull(GoDuration.toMillisOrNull("s"))
        assertNull(GoDuration.toMillisOrNull("10x"))
        assertNull(GoDuration.toMillisOrNull("ten seconds"))
    }

    @Test
    fun `default fallback applies to absent or invalid values`() {
        assertEquals(10_000L, GoDuration.toMillis(null, 10_000L))
        assertEquals(10_000L, GoDuration.toMillis("", 10_000L))
        assertEquals(10_000L, GoDuration.toMillis("bogus", 10_000L))
        assertEquals(5_000L, GoDuration.toMillis("5s", 10_000L))
    }
}
