package com.inngest.utility

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class StringExtensionsKtTest {
    @Test
    fun `nonBlankOrNull returns null for null`() {
        assertNull(null.nonBlankOrNull())
    }

    @Test
    fun `nonBlankOrNull returns null for empty string`() {
        assertNull("".nonBlankOrNull())
    }

    @Test
    fun `nonBlankOrNull returns null for whitespace-only string`() {
        assertNull(" ".nonBlankOrNull())
        assertNull("\t\n".nonBlankOrNull())
    }

    @Test
    fun `nonBlankOrNull returns the value for a non-blank string`() {
        assertEquals("hello", "hello".nonBlankOrNull())
    }

    @Test
    fun `nonBlankOrNull preserves inner and surrounding whitespace for a non-blank string`() {
        assertEquals(" hello world ", " hello world ".nonBlankOrNull())
    }
}
