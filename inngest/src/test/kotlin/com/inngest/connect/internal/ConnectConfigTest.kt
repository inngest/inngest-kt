package com.inngest.connect.internal

import com.inngest.FunctionContext
import com.inngest.Inngest
import com.inngest.InngestFunction
import com.inngest.InngestFunctionConfigBuilder
import com.inngest.Step
import com.inngest.connect.ConnectApp
import com.inngest.connect.ConnectOptions
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ConnectConfigTest {
    private class NoopFunction(
        private val fnId: String = "noop-fn",
    ) : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id(fnId).triggerEvent("test/noop")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any = "ok"
    }

    private fun devApp(appId: String = "test-app"): ConnectApp =
        ConnectApp(
            client = Inngest(appId = appId, isDev = true),
            functions = listOf(NoopFunction()),
        )

    private fun options(
        vararg apps: ConnectApp,
        configure: ConnectOptions.Builder.() -> ConnectOptions.Builder = { this },
    ): ConnectOptions = ConnectOptions.builder(apps.toList()).configure().build()

    @Test
    fun `requires at least one app`() {
        assertFailsWith<IllegalArgumentException> { ConnectConfig(options()) }
    }

    @Test
    fun `rejects duplicate app ids`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                ConnectConfig(options(devApp("same-app"), devApp("same-app")))
            }
        assertTrue(error.message!!.contains("same-app"))
    }

    @Test
    fun `dev mode needs no signing key and uses the local api base url`() {
        val config = ConnectConfig(options(devApp()))

        assertTrue(config.isDev)
        assertEquals(emptyMap(), config.authHeaders)
        assertEquals("http://127.0.0.1:8288", config.apiBaseUrl)
    }

    @Test
    @SetEnvironmentVariable(key = "INNGEST_DEV", value = "0")
    fun `cloud mode requires a signing key`() {
        assertFailsWith<Exception> {
            ConnectConfig(
                options(
                    ConnectApp(
                        client = Inngest(appId = "cloud-app"),
                        functions = listOf(NoopFunction()),
                    ),
                ),
            )
        }
    }

    @Test
    @SetEnvironmentVariable(key = "INNGEST_DEV", value = "0")
    fun `cloud mode hashes the signing key into a bearer auth header`() {
        val config =
            ConnectConfig(
                options(
                    ConnectApp(
                        client = Inngest(appId = "cloud-app"),
                        functions = listOf(NoopFunction()),
                    ),
                ) { signingKey("signkey-prod-12345678") },
            )

        assertTrue(config.isDev.not())
        val authorization = config.authHeaders["Authorization"]!!
        assertTrue(authorization.startsWith("Bearer signkey-prod-"))
        // The key material must be hashed, never echoed.
        assertNotEquals("Bearer signkey-prod-12345678", authorization)
        assertEquals("https://api.inngest.com", config.apiBaseUrl)
    }

    @Test
    @SetEnvironmentVariable(key = "INNGEST_DEV", value = "0")
    fun `branch signing keys require an environment name`() {
        assertFailsWith<IllegalArgumentException> {
            ConnectConfig(
                options(
                    ConnectApp(
                        client = Inngest(appId = "cloud-app"),
                        functions = listOf(NoopFunction()),
                    ),
                ) { signingKey("signkey-branch-12345678") },
            )
        }
    }

    @Test
    @SetEnvironmentVariable(key = "INNGEST_DEV", value = "0")
    @SetEnvironmentVariable(key = "INNGEST_ENV", value = "my-branch")
    fun `branch signing keys work when the environment is configured`() {
        val config =
            ConnectConfig(
                options(
                    ConnectApp(
                        client = Inngest(appId = "cloud-app"),
                        functions = listOf(NoopFunction()),
                    ),
                ) { signingKey("signkey-branch-12345678") },
            )

        assertEquals("my-branch", config.envName)
    }

    @Test
    fun `builds function config json per app`() {
        val config = ConnectConfig(options(devApp()))

        val app = config.apps.single()
        assertEquals("test-app", app.appName)
        assertTrue(app.functionsJson.contains("\"ws://connect?fnId=test-app-noop-fn&stepId=step\""))
        assertTrue(app.functionsJson.contains("\"type\" : \"ws\"") || app.functionsJson.contains("\"type\": \"ws\""))
    }

    @Test
    fun `option precedence beats environment for worker concurrency and gateway url`() {
        System.setProperty("INNGEST_CONNECT_MAX_WORKER_CONCURRENCY", "7")
        System.setProperty("INNGEST_CONNECT_GATEWAY_URL", "ws://from-env:1234")
        try {
            val fromEnv = ConnectConfig(options(devApp()))
            assertEquals(7, fromEnv.maxWorkerConcurrency)
            assertEquals("ws://from-env:1234", fromEnv.gatewayUrlOverride)

            val fromOptions =
                ConnectConfig(
                    options(devApp()) {
                        maxWorkerConcurrency(3).gatewayUrl("ws://explicit:9999")
                    },
                )
            assertEquals(3, fromOptions.maxWorkerConcurrency)
            assertEquals("ws://explicit:9999", fromOptions.gatewayUrlOverride)
        } finally {
            System.clearProperty("INNGEST_CONNECT_MAX_WORKER_CONCURRENCY")
            System.clearProperty("INNGEST_CONNECT_GATEWAY_URL")
        }
    }

    @Test
    fun `defaults apply when nothing is configured`() {
        val config = ConnectConfig(options(devApp()))

        assertEquals(ConnectConfig.DEFAULT_MAX_WORKER_CONCURRENCY, config.maxWorkerConcurrency)
        assertNull(config.gatewayUrlOverride)
        assertEquals(ConnectOptions.DEFAULT_MISSED_HEARTBEAT_TOLERANCE, config.missedHeartbeatTolerance)
        assertTrue(config.handleShutdownSignals)
        assertTrue(config.sdkVersion.startsWith("v"))
        assertTrue(config.instanceId.isNotBlank())
    }

    @Test
    fun `instance id option wins over hostname`() {
        val config = ConnectConfig(options(devApp()) { instanceId("worker-42") })

        assertEquals("worker-42", config.instanceId)
    }
}
