package com.inngest

import kotlin.test.*

internal class ServeConfigTest {
    @Test
    fun `should return client appId if none are passed in`() {
        val client = Inngest(appId = "test")
        val config = ServeConfig(client)
        assertEquals(client.appId, config.appId())
    }

    @Test
    fun `should return serve appId if passed in`() {
        val client = Inngest(appId = "test")
        val config = ServeConfig(client = client, id = "hello")
        assertEquals("hello", config.appId())
    }
}
