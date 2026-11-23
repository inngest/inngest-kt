package com.inngest

import org.junitpioneer.jupiter.SetEnvironmentVariable
import kotlin.test.*

// NOTE: not sure how to test values using environment variables
internal class EnvironmentTest {
    // EventKey
    @Test
    fun `test inngestEventKey`() {
        assertEquals("NO_EVENT_KEY_SET", Environment.inngestEventKey())
    }

    @Test
    fun `test inngestEventKey treats blank key arg as unset`() {
        assertEquals("NO_EVENT_KEY_SET", Environment.inngestEventKey(" "))
    }

    @Test
    fun `test inngestEventKey treats blank system property as unset`() {
        System.setProperty(InngestSystem.EventKey.value, " ")
        try {
            assertEquals("NO_EVENT_KEY_SET", Environment.inngestEventKey())
        } finally {
            System.clearProperty(InngestSystem.EventKey.value)
        }
    }

    @Test
    fun `test inngestEventKey with key value`() {
        val key = "event"
        assertEquals(key, Environment.inngestEventKey(key))
    }

    @Test
    fun `test inngestEventKey falls back to system property when key arg is blank`() {
        System.setProperty(InngestSystem.EventKey.value, "real-event-key")
        try {
            assertEquals("real-event-key", Environment.inngestEventKey(" "))
        } finally {
            System.clearProperty(InngestSystem.EventKey.value)
        }
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

    @Test
    fun `test inngestEventApiBaseUrl treats blank url arg as unset`() {
        assertEquals("http://127.0.0.1:8288", Environment.inngestEventApiBaseUrl(env = InngestEnv.Dev, url = " "))
    }

    @Test
    fun `test inngestEventApiBaseUrl treats blank system property as unset`() {
        System.setProperty(InngestSystem.EventApiBaseUrl.value, " ")
        try {
            assertEquals("http://127.0.0.1:8288", Environment.inngestEventApiBaseUrl(env = InngestEnv.Dev))
        } finally {
            System.clearProperty(InngestSystem.EventApiBaseUrl.value)
        }
    }

    @Test
    fun `test inngestEventApiBaseUrl falls back to system property when url arg is blank`() {
        System.setProperty(InngestSystem.EventApiBaseUrl.value, "https://yolo.com")
        try {
            assertEquals("https://yolo.com", Environment.inngestEventApiBaseUrl(env = InngestEnv.Dev, url = " "))
        } finally {
            System.clearProperty(InngestSystem.EventApiBaseUrl.value)
        }
    }

    // TODO: Test with env `INNGEST_BASE_URL`
    // @Test
    // fun `test inngestEventApiBaseUrl with INNGEST_BASE_URL value`() { }

    // systemValue()
    @Test
    @SetEnvironmentVariable(key = "INNGEST_TEST_SYSTEM_VALUE_KEY", value = "real-env-value")
    fun `systemValue treats blank system property as unset and falls back to environment variable`() {
        System.setProperty("INNGEST_TEST_SYSTEM_VALUE_KEY", " ")
        try {
            assertEquals("real-env-value", Environment.systemValue("INNGEST_TEST_SYSTEM_VALUE_KEY"))
        } finally {
            System.clearProperty("INNGEST_TEST_SYSTEM_VALUE_KEY")
        }
    }

    // Env
    @Test
    fun `test inngestEnv with no params`() {
        assertEquals(InngestEnv.Dev, Environment.inngestEnv())
    }

    @Test
    fun `test inngestEnv treats blank env arg as unset`() {
        assertEquals(InngestEnv.Dev, Environment.inngestEnv(env = " "))
    }

    @Test
    fun `test inngestEnv treats blank INNGEST_DEV system property as unset`() {
        System.setProperty(InngestSystem.Dev.value, " ")
        try {
            assertEquals(InngestEnv.Dev, Environment.inngestEnv())
        } finally {
            System.clearProperty(InngestSystem.Dev.value)
        }
    }

    @Test
    fun `test inngestEnv treats blank INNGEST_ENV system property as unset`() {
        System.setProperty(InngestSystem.Env.value, " ")
        try {
            assertEquals(InngestEnv.Dev, Environment.inngestEnv())
        } finally {
            System.clearProperty(InngestSystem.Env.value)
        }
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
