package com.inngest

import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

internal class RetryAfterErrorTest {
    @Test
    fun `should return retryAfter in seconds when an integer is passed`() {
        val retryAfter = 5000
        val retryAfterError = RetryAfterError("Error", retryAfter)
        assertEquals("5", retryAfterError.retryAfter)
    }

    @Test
    fun `should return retryAfter in seconds when an string is passed`() {
        val retryAfter = "5000"
        val retryAfterError = RetryAfterError("Error", retryAfter)
        assertEquals("5", retryAfterError.retryAfter)
    }

    @Test
    fun `should return retryAfter as an ISO string when a ZonedDateTime is passed`() {
        val retryAfter = ZonedDateTime.parse("2021-08-25T00:00:00Z")
        val retryAfterError = RetryAfterError("Error", retryAfter)
        assertEquals("2021-08-25T00:00:00Z", retryAfterError.retryAfter)
    }
}
