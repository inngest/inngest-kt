package com.inngest

import kotlin.test.*

internal class ClientTest {
    @Test
    fun testEventKeyWithParam() {
        val client = Inngest(appId = "test", eventKey = "eventKey")
        assertEquals(client.eventKey, "eventKey")
    }
}
