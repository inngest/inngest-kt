package com.inngest

import kotlin.test.*

// NOTE: not sure how to test values using environment variables
internal class EnvironmentTest {
    // EventKey
    @Test
    fun `test inngestEventKey`() {
        assertEquals("NO_EVENT_KEY_SET", Environment.inngestEventKey())
    }

    @Test
    fun `test inngestEventKey with key value`() {
        val key = "event"
        assertEquals(key, Environment.inngestEventKey(key))
    }

    @Test
    fun `test isInngestEventKeySet returns false when it's null`() {
        assertFalse(Environment.isInngestEventKeySet(null))
    }

    @Test
    fun `test isInngestEventKeySet returns false when it's set to the dummy event key`() {
        assertFalse(Environment.isInngestEventKeySet("NO_EVENT_KEY_SET"))
    }

    @Test
    fun `test isInngestEventKeySet returns true when it's set to a valid event key`() {
        assertTrue(Environment.isInngestEventKeySet("event"))
    }

    // @Test
    // fun `test inngestEventKey with INNGEST_EVENT_KEY value`() {}

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
}
