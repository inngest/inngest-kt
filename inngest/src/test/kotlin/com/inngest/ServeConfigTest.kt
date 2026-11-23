package com.inngest

import org.junitpioneer.jupiter.SetEnvironmentVariable
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

    @Test
    fun `should fall back to client appId if blank`() {
        val config = ServeConfig(client = client, id = " ")
        assertEquals(client.appId, config.appId())
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

    @Test
    fun `should return false if not set - dev`() {
        val config = ServeConfig(client = client)
        assertFalse(config.hasSigningKey())
    }

    @Test
    @SetEnvironmentVariable(key = "INNGEST_SIGNING_KEY", value = "signkey-prod-b2ed992186a5cb19f6668aade821f502c1d00970dfd0e35128d51bac4649916c")
    fun `should return true if set - prod`() {
        val prodClient = Inngest(appId = client.appId, env = "prod")
        val config = ServeConfig(client = prodClient)
        assertTrue(config.hasSigningKey())
    }

    @Test
    fun `should return dummy key if blank - dev`() {
        val config = ServeConfig(client = client, signingKey = " ")
        assertEquals(DUMMY_SIGNING_KEY, config.signingKey())
    }

    @Test
    fun `should fall back to system property if blank - prod`() {
        System.setProperty(InngestSystem.SigningKey.value, "signkey-prod-fallback")
        try {
            val prodClient = Inngest(appId = client.appId, env = "prod")
            val config = ServeConfig(client = prodClient, signingKey = " ")
            assertEquals("signkey-prod-fallback", config.signingKey())
        } finally {
            System.clearProperty(InngestSystem.SigningKey.value)
        }
    }

    @Test
    fun `should throw error if blank and nothing else set - prod`() {
        val prodClient = Inngest(appId = client.appId, env = "prod")
        val config = ServeConfig(client = prodClient, signingKey = " ")
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

    @Test
    fun `should treat blank baseUrl arg as unset - falls to system property`() {
        System.setProperty(InngestSystem.ApiBaseUrl.value, "https://yolo.com")
        try {
            val config = ServeConfig(client = client, baseUrl = " ")
            assertEquals("https://yolo.com", config.baseUrl())
        } finally {
            System.clearProperty(InngestSystem.ApiBaseUrl.value)
        }
    }

    @Test
    fun `should treat blank baseUrl system property as unset - falls to default`() {
        System.setProperty(InngestSystem.ApiBaseUrl.value, " ")
        try {
            val config = ServeConfig(client = client)
            assertEquals("http://127.0.0.1:8288", config.baseUrl())
        } finally {
            System.clearProperty(InngestSystem.ApiBaseUrl.value)
        }
    }

    // @Test
    // fun `should return INNGEST_API_BASE_URL value`() {}

    // serveOrigin()
    @Test
    fun `should return null - serveOrigin`() {
        val config = ServeConfig(client = client)
        assertNull(config.serveOrigin())
    }

    @Test
    fun `should return passed in value - serveOrigin`() {
        val origin = "yolo.com"
        val config = ServeConfig(client = client, serveOrigin = origin)
        assertEquals(origin, config.serveOrigin())
    }

    @Test
    fun `should treat blank serveOrigin arg as unset - falls to system property`() {
        System.setProperty(InngestSystem.ServeOrigin.value, "https://yolo.com")
        try {
            val config = ServeConfig(client = client, serveOrigin = " ")
            assertEquals("https://yolo.com", config.serveOrigin())
        } finally {
            System.clearProperty(InngestSystem.ServeOrigin.value)
        }
    }

    @Test
    fun `should treat blank serveOrigin system property as unset - null`() {
        System.setProperty(InngestSystem.ServeOrigin.value, " ")
        try {
            val config = ServeConfig(client = client)
            assertNull(config.serveOrigin())
        } finally {
            System.clearProperty(InngestSystem.ServeOrigin.value)
        }
    }

    // @Test
    // fun `should return INNGEST_SERVE_ORIGIN value`() {}

    // servePath()
    @Test
    fun `should return null - servePath`() {
        val config = ServeConfig(client = client)
        assertNull(config.servePath())
    }

    @Test
    fun `should return passed in value - servePath`() {
        val path = "/api/inngest"
        val config = ServeConfig(client = client, servePath = path)
        assertEquals(path, config.servePath())
    }

    @Test
    fun `should treat blank servePath arg as unset - falls to system property`() {
        System.setProperty(InngestSystem.ServePath.value, "/api/inngest")
        try {
            val config = ServeConfig(client = client, servePath = " ")
            assertEquals("/api/inngest", config.servePath())
        } finally {
            System.clearProperty(InngestSystem.ServePath.value)
        }
    }

    @Test
    fun `should treat blank servePath system property as unset - null`() {
        System.setProperty(InngestSystem.ServePath.value, " ")
        try {
            val config = ServeConfig(client = client)
            assertNull(config.servePath())
        } finally {
            System.clearProperty(InngestSystem.ServePath.value)
        }
    }

    // @Test
    // fun `should return INNGEST_SERVE_PATH value`() {}

    // logLevel()
    @Test
    fun `should return default value - info`() {
        val config = ServeConfig(client = client)
        assertEquals("info", config.logLevel())
    }

    @Test
    fun `should return passed in value - logLevel`() {
        val config = ServeConfig(client = client, logLevel = "error")
        assertEquals("error", config.logLevel())
    }

    @Test
    fun `should treat blank logLevel arg as unset - falls to default`() {
        val config = ServeConfig(client = client, logLevel = " ")
        assertEquals("info", config.logLevel())
    }

    @Test
    fun `should treat blank logLevel system property as unset - falls to default`() {
        System.setProperty(InngestSystem.LogLevel.value, " ")
        try {
            val config = ServeConfig(client = client)
            assertEquals("info", config.logLevel())
        } finally {
            System.clearProperty(InngestSystem.LogLevel.value)
        }
    }
}
