package com.inngest

import kotlin.test.*

// NOTE: not sure how to test values using environment variables
internal class EnvironmentTest {
    // AppId
    @Test
    fun `test inngestAppId`() {
        val clientId = "hello"
        assertEquals(clientId, Environment.inngestAppId(clientId))
    }

    @Test
    fun `test inngestAppId with serveId`() {
        val clientId = "hello"
        val serveId = "world"
        assertEquals(serveId, Environment.inngestAppId(clientId, serveId))
    }

    // EventKey
    @Test
    fun `test inngestEventKey`() {
        assertEquals("", Environment.inngestEventKey())
    }

    @Test
    fun `test inngestEventKey with key value`() {
        val key = "event"
        assertEquals(key, Environment.inngestEventKey(key))
    }

    // @Test
    // fun `test inngestEventKey with INNGEST_EVENT_KEY value`() {}

    // Signing key
    @Test
    fun `test inngestSigningKey - dev`() {
        assertEquals("test", Environment.inngestSigningKey(InngestEnv.Dev))
    }

    @Test
    fun `test inngestSigningKey with key value`() {
        val key = "signing key"
        assertEquals(key, Environment.inngestSigningKey(InngestEnv.Dev, key))
    }

    @Test
    fun `test inngestSigningKey - prod`() {
        assertFails(
            "signing key is required",
            { Environment.inngestSigningKey(InngestEnv.Prod) },
        )
    }

    // @Test
    // fun `test inngestSigningKey with INNGEST_SIGNING_KEY value - prod`() {}

    // EventAPI URL
    @Test
    fun `test inngestEventApiBaseUrl - dev`() {
        assertEquals("http://127.0.0.1:8288", Environment.inngestEventApiBaseUrl(env = InngestEnv.Dev))
    }

    fun `test inngestEventApiBaseUrl - prod`() {
        assertEquals("https://inn.gs", Environment.inngestEventApiBaseUrl(env = InngestEnv.Prod))
    }

    fun `test inngestEventApiBaseUrl with url value`() {
        assertEquals(
            "https://yolo.com",
            Environment.inngestEventApiBaseUrl(env = InngestEnv.Prod, url = "https://yolo.com"),
        )
    }

    // TODO: Test with env `INNGEST_BASE_URL`
    // @Test
    // fun `test inngestEventApiBaseUrl with INNGEST_BASE_URL value`() { }

    // API URL
    @Test
    fun `test inngestApiBaseUrl - dev`() {
        assertEquals("http://127.0.0.1:8288", Environment.inngestApiBaseUrl(InngestEnv.Dev))
    }

    @Test
    fun `test inngestApiBaseUrl - prod`() {
        assertEquals("https://api.inngest.com", Environment.inngestApiBaseUrl(env = InngestEnv.Prod))
    }

    @Test
    fun `test inngestApiBaseUrl with url value`() {
        assertEquals(
            "https://api.yolo.com",
            Environment.inngestApiBaseUrl(env = InngestEnv.Prod, url = "https://api.yolo.com"),
        )
    }

    // TODO: Test with env `INNGEST_API_BASE_URL`
    // @Test
    // fun `test inngestApiBaseUrl with INNGEST_API_BASE_URL value - prod`() { }

    // Serve Origin
    @Test
    fun `test inngestServeOrigin`() {
        assertNull(Environment.inngestServeOrigin())
    }

    @Test
    fun `test inngestServeOrigin with origin value`() {
        val origin = "yolo.com"
        assertEquals(origin, Environment.inngestServeOrigin(origin))
    }

    // @Test
    // fun `test inngestServeOrigin with INNGEST_SERVE_ORIGIN value`() {}

    // Serve Path
    @Test
    fun `test inngestServePath`() {
        assertNull(Environment.inngestServePath())
    }

    @Test
    fun `test inngestServePath with path value`() {
        val path = "/api/inngest"
        assertEquals(path, Environment.inngestServePath(path))
    }

    // @Test
    // fun `test inngestServePath with INNGEST_SERVE_PATH value`() {}

    // Env
    @Test
    fun `test inngestEnv with no params`() {
        assertEquals(InngestEnv.Dev, Environment.inngestEnv())
    }

    @Test
    fun `test inngestEnv with env value`() {
        assertEquals(InngestEnv.Dev, Environment.inngestEnv(env = "dev"))
        assertEquals(InngestEnv.Dev, Environment.inngestEnv(env = "development"))
        assertEquals(InngestEnv.Prod, Environment.inngestEnv(env = "prod"))
        assertEquals(InngestEnv.Prod, Environment.inngestEnv(env = "production"))
        assertEquals(InngestEnv.Other, Environment.inngestEnv(env = "yolo"))
    }

    @Test
    fun `test inngestEnv with isDev value`() {
        assertEquals(InngestEnv.Dev, Environment.inngestEnv(isDev = true))
        assertEquals(InngestEnv.Prod, Environment.inngestEnv(isDev = false))
    }

    // @Test
    // fun `test inngestEnv with INNGEST_DEV value`() { }

    // Log Level
    @Test
    fun `test inngestLogLevel default value`() {
        assertEquals("info", Environment.inngestLogLevel())
    }

    @Test
    fun `test inngestLogLevel with level value`() {
        assertEquals("error", Environment.inngestLogLevel("error"))
    }
}
