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
}
