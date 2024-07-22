package com.inngest

import com.inngest.signingkey.InvalidSigningKeyException
import kotlin.test.Test
import kotlin.test.assertEquals

import org.junit.jupiter.api.Assertions.*
import kotlin.test.assertFailsWith

class InngestFunctionConfigBuilderTest {

    @Test
    fun testGlobalId() {
        val config = InngestFunctionConfigBuilder()
            .id("test-id")
            .build("app-id", "https://mysite.com/api/inngest")
        assertEquals("app-id-test-id", config.id)
    }

    @Test
    fun testMissingId() {
        assertFailsWith<InngestInvalidConfigurationException> {
            InngestFunctionConfigBuilder()
                .build("app-id", "https://mysite.com/api/inngest")
        }
    }
}
