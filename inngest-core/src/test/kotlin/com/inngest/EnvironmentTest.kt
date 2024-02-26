package com.inngest

import kotlin.test.*

// NOTE: not sure how to test values using environment variables
internal class EnvironmentTest {
    // AppId
    @Test
    fun testInngestAppIdWithoutServeId() {
        val clientId = "hello"
        assertEquals(clientId, Environment.inngestAppId(clientId))
    }

    @Test
    fun testInngestAppIdWithServeId() {
        val clientId = "hello"
        val serveId = "world"
        assertEquals(serveId, Environment.inngestAppId(clientId, serveId))
    }

    // EventKey
    @Test
    fun testInngestEventKeyWithKey() {
        val key = "event"
        assertEquals(key, Environment.inngestEventKey(key))
    }

    @Test
    fun testInngestEventKeyWithoutKey() {
        assertEquals("", Environment.inngestEventKey())
    }

    // @Test
    // fun testInngestEventKeyWithEnvVar() {}

    // Signing key
    @Test
    fun testInngestSigningKeyWithKey() {
        val key = "signing key"
        assertEquals(key, Environment.inngestSigningKey(InngestEnv.Dev, key))
    }

    @Test
    fun testInngestSigningKeyWithoutKeyDev() {
        assertEquals("test", Environment.inngestSigningKey(InngestEnv.Dev))
    }

    // @Test
    // fun testInngestSigningKeyWithEnvVarProd() {}

    @Test
    fun testInngestSigningKeyWithoutKeyOrEnvVarProd() {
        assertFails(
            "signing key is required",
            { Environment.inngestSigningKey(InngestEnv.Prod) },
        )
    }

    // EventAPI URL
    @Test
    fun testInngestEventApiBaseUrlDefaultDev() {
        assertEquals("http://127.0.0.1:8288", Environment.inngestEventApiBaseUrl(env = InngestEnv.Dev))
    }

    fun testInngestEventApiBaseUrlDefaultProd() {
        assertEquals("https://inn.gs", Environment.inngestEventApiBaseUrl(env = InngestEnv.Prod))
    }

    fun testInngestEventApiBaseUrlWithUrl() {
        assertEquals(
            "https://yolo.com",
            Environment.inngestEventApiBaseUrl(env = InngestEnv.Prod, url = "https://yolo.com"),
        )
    }

    // TODO: Test with env `INNGEST_BASE_URL`
    // @Test
    // fun testInngestEventApiBaseUrlEnvVarProd() { }

    // API URL
    @Test
    fun testInngestApiBaseUrlDefaultDev() {
        assertEquals("http://127.0.0.1:8288", Environment.inngestApiBaseUrl(InngestEnv.Dev))
    }

    @Test
    fun testInngestApiBaseUrlDefaultProd() {
        assertEquals("https://api.inngest.com", Environment.inngestApiBaseUrl(env = InngestEnv.Prod))
    }

    @Test
    fun testInngestApiBaseUrlWithUrl() {
        assertEquals(
            "https://api.yolo.com",
            Environment.inngestApiBaseUrl(env = InngestEnv.Prod, url = "https://api.yolo.com"),
        )
    }

    // TODO: Test with env `INNGEST_API_BASE_URL`
    // @Test
    // fun testInngestApiBaseUrlEnvVarProd() { }

    // Serve Origin
    @Test
    fun testInngestServeOriginNothing() {
        assertNull(Environment.inngestServeOrigin())
    }

    @Test
    fun testInngestServeOriginWithValue() {
        val host = "yolo.com"
        assertEquals(host, Environment.inngestServeOrigin(host))
    }

    // @Test
    // fun testInngestServeOriginWithEnvVar() {}

    // Serve Path
    @Test
    fun testInngestServePathNothing() {
        assertNull(Environment.inngestServePath())
    }

    @Test
    fun testInngestServePathWithValue() {
        val path = "/api/inngest"
        assertEquals(path, Environment.inngestServePath(path))
    }

    // @Test
    // fun testInngestServePathWithEnvVar() {}

    // Env
    // Log Level
    @Test
    fun testInngestLogLevelDefault() {
        assertEquals("info", Environment.inngestLogLevel())
    }

    @Test
    fun testInngestLogLevelWithLevel() {
        assertEquals("error", Environment.inngestLogLevel("error"))
    }
}
