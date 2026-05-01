package com.inngest

import kotlin.test.Test
import kotlin.test.assertEquals

class InngestEventBuilderTest {
    @Test
    fun constructorOnlyWithRequiredParameters() {
        val event =
            InngestEventBuilder("test-name", mapOf())
                .build()
        assertEquals(InngestEvent("test-name", mapOf()), event)
    }

    @Test
    fun optionalParameters() {
        val event =
            InngestEventBuilder("test-name", mapOf())
                .user(mapOf("userId" to 5))
                .id("test-id")
                .ts(100)
                .v("1.0")
                .build()
        assertEquals(InngestEvent("test-name", mapOf(), mapOf("userId" to 5), "test-id", 100, "1.0"), event)
    }
}
