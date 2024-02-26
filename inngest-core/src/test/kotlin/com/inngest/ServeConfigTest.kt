package com.inngest

import kotlin.test.*

internal class ServeConfigTest {
    val client = Inngest(appId = "test")

    // appId()
    @Test
    fun `should return client appId if none are passed in`() {
        val config = ServeConfig(client)
        assertEquals(client.appId, config.appId())
    }

    @Test
    fun `should return serve appId if passed in`() {
        val config = ServeConfig(client = client, id = "hello")
        assertEquals("hello", config.appId())
    }

    // signingKey()
    @Test
    fun `should return test string if not set - dev`() {
        val config = ServeConfig(client = client)
        assertEquals("test", config.signingKey())
    }

    @Test
    fun `should throw error if not set - prod`() {
        val prodClient = Inngest(appId = client.appId, env = "prod")
        val config = ServeConfig(client = prodClient)
        assertFails(
            "signing key is required",
            { config.signingKey() },
        )
    }

    // @Test
    // fun `should return INNGEST_SIGNING_KEY value - prod`() {}

    @Test
    fun `should return passed in value - sigingKey`() {
        val key = "sigingkey"
        val config = ServeConfig(client = client, signingKey = key)
        assertEquals(key, config.signingKey())
    }

    // baseUrl()
    @Test
    fun `should return dev server url - dev`() {
        val config = ServeConfig(client = client)
        assertEquals("http://127.0.0.1:8288", config.baseUrl())
    }

    @Test
    fun `should return inngest api - prod`() {
        val prodClient = Inngest(appId = client.appId, env = "prod")
        val config = ServeConfig(client = prodClient)
        assertEquals("https://api.inngest.com", config.baseUrl())
    }

    @Test
    fun `should return passed in value - baseUrl`() {
        val url = "https://api.yolo.com"
        val config = ServeConfig(client = client, baseUrl = url)
        assertEquals(url, config.baseUrl())
    }

    // @Test
    // fun `should return INNGEST_API_BASE_URL value`() {}

    // SERVEORIGIN()
    // servePath()
    // logLevel()
}
