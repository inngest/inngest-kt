package com.inngest

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals


internal class EventDataParsingTest {

    @Test
    fun `scalar values keep their current types`() {
        val data = callWithData(
            """
                {
                    "int": 1,
                    "negativeInt": -7,
                    "long": 3000000000
                    "bigInteger": 12345678901234567890123,
                    "decimal": 1.5,
                    "negativeDecimal": -0.25,
                    "exponent": 1e3,
                    "bool": true,
                    "text": "h\u00e9 \"quoted\" ✓"
                }
            """.trimIndent()
        )

        assertEquals(1, data["int"])
        assertEquals(-7, data["negativeInt"])
        assertEquals(3000000000L, data["long"])
        assertEquals(BigInteger("12345678901234567890123"), data["bigInteger"])
        assertEquals(1.5, data["decimal"])
        assertEquals(-0.25, data["negativeDecimal"])
        assertEquals(1000.0, data["exponent"])
        assertEquals(true, data["bool"])
        assertEquals("hé \"quoted\" ✓", data["text"])


    }

    private fun callWithData(dataJson: String): Map<String, Any?> {
        val event = """{"name": "test/run", "data": $dataJson}"""
        return callWithEvents(event, listOf(event)).event.data
    }

    private fun callWithEvents(eventJson: String, eventsJson: List<String>): FunctionContext {
        val function = CaptureFunction()
        val response = commHandler(function).callFunction(CAPTURE_FN_ID, requestBody(eventJson, eventsJson))

        assertEquals(ResultStatusCode.FunctionComplete, response.statusCode, response.body)
        return function.captured!!
    }

    private fun requestBody(
        eventJson: String,
        eventsJson: List<String>,
    ): String =
        """
        {
          "ctx": {"attempt": 0, "fn_id": "$CAPTURE_FN_ID", "run_id": "run-test", "env": "test"},
          "event": $eventJson,
          "events": [${eventsJson.joinToString(",")}],
          "steps": {}
        }
        """.trimIndent()

    private fun commHandler(function: InngestFunction): CommHandler {
        val client = Inngest(appId = "test-app", eventKey = "evt-key", isDev = true)

        return CommHandler(
            functions = mapOf(function.id() to function),
            client = client,
            config = ServeConfig(client),
            framework = SupportedFrameworkName.SpringBoot
        )
    }

    private class CaptureFunction : InngestFunction() {
        var captured: FunctionContext? = null

        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
            builder.id(CAPTURE_FN_ID)

        override fun execute(ctx: FunctionContext, step: Step): Any? {
            captured = ctx
            return "captured"
        }
    }

    private companion object {
        const val CAPTURE_FN_ID = "capture-fn"
    }


}
