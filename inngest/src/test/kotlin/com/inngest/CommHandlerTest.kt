package com.inngest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.inngest.testing.ProtocolFixtures
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

internal class CommHandlerTest {
    private val mapper = ObjectMapper()
    private var mockWebServer: MockWebServer? = null

    @AfterEach
    fun tearDown() {
        mockWebServer?.shutdown()
        mockWebServer = null
    }

    @Test
    fun `callFunction returns success payload with required headers`() {
        val response =
            commHandler(EchoFunction())
                .callFunction("echo-fn", ProtocolFixtures.executionRequestPayloadJson("echo-fn"))

        assertEquals(ResultStatusCode.FunctionComplete, response.statusCode)
        assertEquals("2", response.headers[InngestHeaderKey.RequestVersion.value])
        assertEquals("springboot", response.headers[InngestHeaderKey.Framework.value])
        assertTrue(response.headers[InngestHeaderKey.Sdk.value]!!.startsWith("inngest-kt:v"))
        assertEquals("done", mapper.readValue(response.body, String::class.java))
    }

    @Test
    fun `callFunction accepts composite function ids`() {
        val response =
            commHandler(EchoFunction())
                .callFunction("test-app-echo-fn", ProtocolFixtures.executionRequestPayloadJson("echo-fn"))

        assertEquals(ResultStatusCode.FunctionComplete, response.statusCode)
        assertEquals("done", mapper.readValue(response.body, String::class.java))
    }

    @Test
    fun `callFunction marks non-retriable errors correctly`() {
        val response =
            commHandler(NonRetriableFailureFunction())
                .callFunction("non-retriable-fn", ProtocolFixtures.executionRequestPayloadJson("non-retriable-fn"))

        val body = mapper.readTree(response.body)

        assertEquals(ResultStatusCode.NonRetriableError, response.statusCode)
        assertEquals("true", response.headers[InngestHeaderKey.NoRetry.value])
        assertEquals("2", response.headers[InngestHeaderKey.RequestVersion.value])
        assertEquals("hard failure", body["message"].asText())
    }

    @Test
    fun `callFunction propagates retry-after headers for retriable errors`() {
        val response =
            commHandler(RetryAfterFailureFunction())
                .callFunction("retry-after-fn", ProtocolFixtures.executionRequestPayloadJson("retry-after-fn"))

        val body = mapper.readTree(response.body)

        assertEquals(ResultStatusCode.RetriableError, response.statusCode)
        assertEquals("false", response.headers[InngestHeaderKey.NoRetry.value])
        assertEquals("5", response.headers[InngestHeaderKey.RetryAfter.value])
        assertEquals("retry later", body["message"].asText())
    }

    @Test
    fun `callFunction returns run step payload`() {
        val response =
            commHandler(RunStepFunction())
                .callFunction("run-step-fn", ProtocolFixtures.executionRequestPayloadJson("run-step-fn"))

        assertEquals(ResultStatusCode.StepComplete, response.statusCode)
        assertStepMatchesGolden(response, "protocol/steps/run-step.json")
    }

    @Test
    fun `callFunction returns sleep step payload`() {
        val response =
            commHandler(SleepStepFunction())
                .callFunction("sleep-step-fn", ProtocolFixtures.executionRequestPayloadJson("sleep-step-fn"))

        assertEquals(ResultStatusCode.StepComplete, response.statusCode)
        assertStepMatchesGolden(response, "protocol/steps/sleep-step.json")
    }

    @Test
    fun `callFunction returns waitForEvent payload`() {
        val response =
            commHandler(WaitForEventStepFunction())
                .callFunction("wait-step-fn", ProtocolFixtures.executionRequestPayloadJson("wait-step-fn"))

        assertEquals(ResultStatusCode.StepComplete, response.statusCode)
        assertStepMatchesGolden(response, "protocol/steps/wait-for-event-step.json")
    }

    @Test
    fun `callFunction returns invoke payload`() {
        val response =
            commHandler(InvokeStepFunction())
                .callFunction("invoke-step-fn", ProtocolFixtures.executionRequestPayloadJson("invoke-step-fn"))

        assertEquals(ResultStatusCode.StepComplete, response.statusCode)
        assertStepMatchesGolden(response, "protocol/steps/invoke-step.json")
    }

    @Test
    fun `callFunction returns sendEvent payload`() {
        val server = MockWebServer()
        mockWebServer = server
        server.enqueue(MockResponse().setBody("""{"ids":["evt-1"]}"""))

        val baseUrl = server.url("").toString().removeSuffix("/")
        val response =
            commHandler(SendEventStepFunction(), baseUrl = baseUrl)
                .callFunction("send-step-fn", ProtocolFixtures.executionRequestPayloadJson("send-step-fn"))

        assertEquals(ResultStatusCode.StepComplete, response.statusCode)
        assertStepMatchesGolden(response, "protocol/steps/send-event-step.json")
    }

    private fun commHandler(
        function: InngestFunction,
        baseUrl: String? = null,
    ): CommHandler {
        val client =
            Inngest(
                appId = "test-app",
                baseUrl = baseUrl,
                eventKey = "evt-key",
                isDev = true,
            )

        return CommHandler(
            functions = mapOf(function.id() to function),
            client = client,
            config = ServeConfig(client, baseUrl = baseUrl),
            framework = SupportedFrameworkName.SpringBoot,
        )
    }

    private fun firstStep(response: CommResponse): JsonNode {
        val body = mapper.readTree(response.body)
        assertTrue(body.isArray)
        assertEquals(1, body.size())
        return body[0]
    }

    private fun assertStepMatchesGolden(
        response: CommResponse,
        resourcePath: String,
    ) {
        assertEquals(loadGoldenJson(resourcePath), firstStep(response))
    }

    private fun loadGoldenJson(resourcePath: String): JsonNode =
        mapper.readTree(
            javaClass.classLoader.getResourceAsStream(resourcePath)
                ?: error("Missing golden fixture: $resourcePath"),
        )

    private class EchoFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("echo-fn")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any = "done"
    }

    private class NonRetriableFailureFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("non-retriable-fn")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any? = throw NonRetriableError("hard failure")
    }

    private class RetryAfterFailureFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("retry-after-fn")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any? = throw RetryAfterError("retry later", 5000)
    }

    private class RunStepFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("run-step-fn")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any = step.run("compute") { 42 }
    }

    private class SleepStepFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("sleep-step-fn")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ) {
            step.sleep("pause", Duration.ofSeconds(9))
        }
    }

    private class WaitForEventStepFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("wait-step-fn")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any? = step.waitForEvent("wait-for-user", "app/user.updated", "10m", "event.data.userId == async.data.userId")
    }

    private class InvokeStepFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("invoke-step-fn")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any = step.invoke<String>("invoke-user", "target-app", "target-fn", mapOf("userId" to "123"), "30s")
    }

    private class SendEventStepFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("send-step-fn")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any =
            step.sendEvent(
                "send-user",
                InngestEvent("app/user.created", ProtocolFixtures.linkedData("userId", "123")),
            )
    }
}
