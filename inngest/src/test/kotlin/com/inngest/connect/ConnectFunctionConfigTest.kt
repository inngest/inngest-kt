package com.inngest.connect

import com.fasterxml.jackson.databind.ObjectMapper
import com.inngest.CommHandler
import com.inngest.FunctionContext
import com.inngest.FunctionRuntimeKind
import com.inngest.Inngest
import com.inngest.InngestFunction
import com.inngest.InngestFunctionConfigBuilder
import com.inngest.ServeConfig
import com.inngest.Step
import com.inngest.SupportedFrameworkName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ConnectFunctionConfigTest {
    private val mapper = ObjectMapper()

    @Test
    fun `builder emits ws runtime with connect placeholder url`() {
        val config =
            InngestFunctionConfigBuilder()
                .id("test-id")
                .build("app-id", "ws://connect", FunctionRuntimeKind.Ws)

        val runtime = config.steps["step"]!!.runtime

        assertEquals("ws", runtime["type"])
        assertEquals("ws://connect?fnId=app-id-test-id&stepId=step", runtime["url"])
    }

    @Test
    fun `builder defaults to http runtime when kind is omitted`() {
        val config =
            InngestFunctionConfigBuilder()
                .id("test-id")
                .build("app-id", "https://mysite.com/api/inngest")

        assertEquals("http", config.steps["step"]!!.runtime["type"])
    }

    @Test
    fun `connect function configs mirror http configs except step runtime`() {
        val handler = commHandler(EchoFunction())

        val connectConfigs = handler.getConnectFunctionConfigs()

        assertEquals(1, connectConfigs.size)
        val config = connectConfigs.single()
        assertEquals("test-app-echo-fn", config.id)
        val runtime = config.steps["step"]!!.runtime
        assertEquals("ws", runtime["type"])
        assertEquals("ws://connect?fnId=test-app-echo-fn&stepId=step", runtime["url"])
    }

    @Test
    fun `connect function configs include generated failure handlers`() {
        val handler = commHandler(FailureHandlingFunction())

        val ids = handler.getConnectFunctionConfigs().map { it.id }

        assertEquals(2, ids.size)
        assertTrue(ids.contains("test-app-failing-fn"), "expected base function in $ids")
        assertTrue(ids.any { it.contains("failure") }, "expected generated failure handler in $ids")
    }

    @Test
    fun `connect function configs serialize to a json array with triggers and ws steps`() {
        val handler = commHandler(EchoFunction())

        val json = handler.getConnectFunctionConfigsJson()
        val parsed = mapper.readTree(json)

        assertTrue(parsed.isArray, "expected a JSON array, got: $json")
        val fn = parsed[0]
        assertEquals("test-app-echo-fn", fn["id"].asText())
        assertEquals("test/echo", fn["triggers"][0]["event"].asText())
        val step = fn["steps"]["step"]
        assertEquals("ws", step["runtime"]["type"].asText())
        assertEquals("ws://connect?fnId=test-app-echo-fn&stepId=step", step["runtime"]["url"].asText())
        assertEquals(3, step["retries"]["attempts"].asInt())
    }

    @Test
    fun `connect config json uses the same duration formatting as register`() {
        val handler = commHandler(BatchingFunction())

        val json = handler.getConnectFunctionConfigsJson()
        val parsed = mapper.readTree(json)
        val fn = parsed.first { it["id"].asText() == "test-app-batching-fn" }

        assertEquals("30s", fn["batchEvents"]["timeout"].asText())
        assertEquals(10, fn["batchEvents"]["maxSize"].asInt())
    }

    private fun commHandler(function: InngestFunction): CommHandler {
        val client =
            Inngest(
                appId = "test-app",
                eventKey = "evt-key",
                isDev = true,
            )

        return CommHandler(
            functions = mapOf(function.id() to function),
            client = client,
            config = ServeConfig(client),
            framework = SupportedFrameworkName.Connect,
        )
    }

    private class EchoFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
            builder
                .id("echo-fn")
                .triggerEvent("test/echo")
                .retries(3)

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any = "done"
    }

    private class BatchingFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
            builder
                .id("batching-fn")
                .triggerEvent("test/batch")
                .batchEvents(10, java.time.Duration.ofSeconds(30))

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any = "done"
    }

    private class FailureHandlingFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
            builder
                .id("failing-fn")
                .triggerEvent("test/fail")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any = "done"

        override fun onFailure(
            ctx: FunctionContext,
            step: Step,
        ): Any = "handled"
    }
}
