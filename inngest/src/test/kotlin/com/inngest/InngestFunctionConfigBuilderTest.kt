package com.inngest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InngestFunctionConfigBuilderTest {
    @Test
    fun testGlobalId() {
        val config =
            InngestFunctionConfigBuilder()
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

    @Test
    fun testConcurrencyLimitOnly() {
        val config =
            InngestFunctionConfigBuilder()
                .id("test-id")
                .concurrency(5)
                .build("app-id", "https://mysite.com/api/inngest")
        assertEquals<List<Concurrency>?>(listOf(Concurrency(5)), config.concurrency)
    }

    @Test
    fun testConcurrencyLimitAndKeyWithoutScope() {
        val config =
            InngestFunctionConfigBuilder()
                .id("test-id")
                .concurrency(5, "event.data.user_id")
                .build("app-id", "https://mysite.com/api/inngest")
        assertEquals<List<Concurrency>?>(listOf(Concurrency(5, "event.data.user_id")), config.concurrency)
    }

    @Test
    fun testConcurrencyScope() {
        val config =
            InngestFunctionConfigBuilder()
                .id("test-id")
                .concurrency(5, null, ConcurrencyScope.FUNCTION)
                .build("app-id", "https://mysite.com/api/inngest")
        assertEquals<List<Concurrency>?>(listOf(Concurrency(5, null, ConcurrencyScope.FUNCTION)), config.concurrency)

        assertFailsWith<InngestInvalidConfigurationException> {
            InngestFunctionConfigBuilder()
                .id("test-id")
                .concurrency(5, null, ConcurrencyScope.ENVIRONMENT)
                .build("app-id", "https://mysite.com/api/inngest")
        }

        assertFailsWith<InngestInvalidConfigurationException> {
            InngestFunctionConfigBuilder()
                .id("test-id")
                .concurrency(5, null, ConcurrencyScope.ACCOUNT)
                .build("app-id", "https://mysite.com/api/inngest")
        }
    }

    @Test
    fun testConcurrencyMaxOptionsLength() {
        val config =
            InngestFunctionConfigBuilder()
                .id("test-id")
                .concurrency(5, null, ConcurrencyScope.FUNCTION)
                .concurrency(7, "event.data.user_id", ConcurrencyScope.ENVIRONMENT)
                .build("app-id", "https://mysite.com/api/inngest")
        assertEquals<List<Concurrency>?>(listOf(Concurrency(5, null, ConcurrencyScope.FUNCTION), Concurrency(7, "event.data.user_id", ConcurrencyScope.ENVIRONMENT)), config.concurrency)

        assertFailsWith<InngestInvalidConfigurationException> {
            InngestFunctionConfigBuilder()
                .id("test-id")
                .concurrency(5, null, ConcurrencyScope.FUNCTION)
                .concurrency(7, "event.data.user_id", ConcurrencyScope.ENVIRONMENT)
                .concurrency(9, "event.data.account_id", ConcurrencyScope.ACCOUNT)
                .build("app-id", "https://mysite.com/api/inngest")
        }
    }
}
