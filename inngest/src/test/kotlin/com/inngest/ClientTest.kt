package com.inngest

import kotlin.test.*

internal class ClientTest {
    @Test
    fun testEventKeyWithParam() {
        val client = Inngest(appId = "test", eventKey = "eventKey")
        assertEquals("eventKey", client.eventKey)
    }

    @Test
    fun testEventKeyWithBlankParamIsTreatedAsUnset() {
        val client = Inngest(appId = "test", eventKey = " ")
        assertEquals("NO_EVENT_KEY_SET", client.eventKey)
    }

    @Test
    fun testEventKeyWithInnerSpacesIsPreserved() {
        val client = Inngest(appId = "test", eventKey = "event key")
        assertEquals("event key", client.eventKey)
    }
}
