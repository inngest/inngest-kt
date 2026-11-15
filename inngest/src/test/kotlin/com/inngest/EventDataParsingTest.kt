package com.inngest

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class EventDataParsingTest {

    @Test
    fun `scalar values keep their current types`() {
        val data = callWithData(
            """
                {
                    "int": 1,
                    "negativeInt": -7,
                    "long": 3000000000,
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

    @Test
    fun `nested object becomes a Map`() {
        val data = callWithData("""{"reference": {"url": "https://inngest.com", "label": "ref"}}""")

        val reference = data["reference"]
        assertTrue(reference is Map<*, *>)
        val referenceMap = reference
        assertEquals("https://inngest.com", referenceMap["url"])
        assertEquals("ref", referenceMap["label"])
    }

    @Test
    fun `object nested three levels deep survives`() {
        val data = callWithData("""{"a": {"b": {"c": "leaf"}}}""")

        val a = data["a"] as Map<*, *>
        val b = a["b"] as Map<*, *>
        assertEquals("leaf", b["c"])
    }

    @Test
    fun `array of objects becomes a list of Maps`() {
        val data = callWithData("""{"items": [{"id": 1}, {"id": 2}]}""")

        val items = data["items"] as List<*>
        assertEquals(2, items.size)
        assertEquals(1, (items[0] as Map<*, *>)["id"])
        assertEquals(2, (items[1] as Map<*, *>)["id"])
    }

    @Test
    fun `mixed array keeps order and types`() {
        val data = callWithData("""{"mixed": [1, "x", {"k": "v"}, [2, 3], null]}""")

        val mixed = data["mixed"] as List<*>
        assertEquals(5, mixed.size)
        assertEquals(1, mixed[0])
        assertEquals("x", mixed[1])
        assertEquals("v", (mixed[2] as Map<*, *>)["k"])
        assertEquals(listOf(2, 3), mixed[3])
        assertNull(mixed[4])
    }

    @Test
    fun `empty object and array stay empty`() {
        val data = callWithData("""{"emptyObject": {}, "emptyArray": []}""")

        assertEquals(emptyMap<String, Any?>(), data["emptyObject"])
        assertEquals(emptyList<Any?>(), data["emptyArray"])
    }

    @Test
    fun `null values are preserved`() {
        val data = callWithData("""{"missing": null, "nested": {"value": null}}""")

        assertTrue(data.containsKey("missing"))
        assertNull(data["missing"])
        assertNull((data["nested"] as Map<*, *>)["value"])
    }

    @Test
    fun `batch request parses nested data for every event`() {
        val first = """{"name": "test/run", "data": {"ref": {"id": 1}}}"""
        val second = """{"name": "test/run", "data": {"ref": {"id": 2}}}"""
        val ctx = callWithEvents(first, listOf(first, second))

        assertEquals(2, ctx.events.size)
        assertEquals(1, (ctx.events[0].data["ref"] as Map<*, *>)["id"])
        assertEquals(2, (ctx.events[1].data["ref"] as Map<*, *>)["id"])
    }

    @Test
    fun `function-failed system event exposes nested event and error data`() {
        val event = """
            {
              "name": "inngest/function.failed",
              "data": {
                "event": {"name": "demo/echo", "data": {"jobId": 1}},
                "error": {"name": "Error", "message": "boom"}
              }
            }
        """.trimIndent()
        val ctx = callWithEvents(event, listOf(event))

        val innerEvent = ctx.event.data["event"] as Map<*, *>
        val innerEventData = innerEvent["data"] as Map<*, *>
        assertEquals(1, innerEventData["jobId"])

        val error = ctx.event.data["error"] as Map<*, *>
        assertEquals("boom", error["message"])
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

        override fun execute(ctx: FunctionContext, step: Step): Any {
            captured = ctx
            return "captured"
        }
    }

    private companion object {
        const val CAPTURE_FN_ID = "capture-fn"
    }
}
