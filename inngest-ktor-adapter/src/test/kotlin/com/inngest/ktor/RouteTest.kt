package com.inngest.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import com.inngest.FunctionContext
import com.inngest.Inngest
import com.inngest.InngestFunction
import com.inngest.InngestFunctionConfigBuilder
import com.inngest.InngestHeaderKey
import com.inngest.Step
import com.inngest.testing.ProtocolFixtures
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class RouteTest {
    private val mapper = ObjectMapper()
    private var mockWebServer: MockWebServer? = null

    @AfterEach
    fun tearDown() {
        mockWebServer?.shutdown()
        mockWebServer = null
    }

    @Test
    fun `get route returns dev introspection without a request body`() =
        testApplication {
            application {
                routing {
                    serve("/api/inngest", Inngest("test-app", eventKey = "evt-key", isDev = true), listOf(EchoFunction()))
                }
            }

            val response = client.get("/api/inngest")
            val body = mapper.readTree(response.bodyAsText())

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            assertEquals("dev", body["mode"].asText())
            assertEquals(1, body["function_count"].asInt())
            assertTrue(body["authentication_succeeded"].isNull)
        }

    @Test
    fun `post route returns call response with required headers`() =
        testApplication {
            application {
                routing {
                    serve("/api/inngest", Inngest("test-app", eventKey = "evt-key", isDev = true), listOf(EchoFunction()))
                }
            }

            val response =
                client.post("/api/inngest?fnId=echo-fn&stepId=step") {
                    contentType(ContentType.Application.Json)
                    setBody(ProtocolFixtures.executionRequestPayloadJson("echo-fn"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("2", response.headers[InngestHeaderKey.RequestVersion.value])
            assertEquals("ktor", response.headers[InngestHeaderKey.Framework.value])
            assertEquals("\"done\"", response.bodyAsText())
        }

    @Test
    fun `post route executes a valid signed request in cloud`() =
        testApplication {
            application {
                routing {
                    serve(
                        "/api/inngest",
                        Inngest("test-app", eventKey = "evt-key", isDev = false),
                        listOf(EchoFunction()),
                        signingKey = "signkey-test-12345678",
                    )
                }
            }

            val requestBody = ProtocolFixtures.executionRequestPayloadJson("echo-fn")

            val response =
                client.post("/api/inngest?fnId=echo-fn&stepId=step") {
                    header(InngestHeaderKey.ServerKind.value, "cloud")
                    header(InngestHeaderKey.Signature.value, sign(requestBody))
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("2", response.headers[InngestHeaderKey.RequestVersion.value])
            assertEquals("ktor", response.headers[InngestHeaderKey.Framework.value])
            assertEquals("\"done\"", response.bodyAsText())

            for ((query, payload) in listOf("" to requestBody, "?fnId=unknown" to requestBody, "?fnId=echo-fn" to "{broken")) {
                val failure =
                    client.post("/api/inngest$query") {
                        header(InngestHeaderKey.ServerKind.value, "cloud")
                        header(InngestHeaderKey.Signature.value, sign(payload))
                        contentType(ContentType.Application.Json)
                        setBody(payload)
                    }
                val failureBody = mapper.readTree(failure.bodyAsText())
                assertEquals(HttpStatusCode.InternalServerError, failure.status)
                assertTrue(failureBody["stack"].asText().isNotBlank())
                assertTrue(failureBody["__serialized"].asBoolean())
            }
        }

    @Test
    fun `post route authenticates before returning execution diagnostics in cloud`() =
        testApplication {
            application {
                routing {
                    serve(
                        "/api/inngest",
                        Inngest("test-app", eventKey = "evt-key", isDev = false),
                        listOf(EchoFunction()),
                        signingKey = "signkey-test-12345678",
                    )
                }
            }

            val payload = ProtocolFixtures.executionRequestPayloadJson("echo-fn")
            val timestamp = Instant.now().epochSecond
            val signatures = listOf(null, "t=$timestamp&s=invalid", "t=invalid&s=invalid", "t=1&s=invalid")
            for (signature in signatures) {
                for ((query, requestBody) in listOf("" to payload, "?fnId=unknown" to payload, "?fnId=echo-fn" to payload, "?fnId=echo-fn" to "{broken")) {
                    val response =
                        client.post("/api/inngest$query") {
                            header(InngestHeaderKey.ServerKind.value, "dev")
                            signature?.let { header(InngestHeaderKey.Signature.value, it) }
                            contentType(ContentType.Application.Json)
                            setBody(requestBody)
                        }
                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    assertEquals(mapper.readTree("""{"message":"Unauthorized"}"""), mapper.readTree(response.bodyAsText()))
                }
            }
        }

    @Test
    fun `put route returns successful sync response and forwards expected server kind`() =
        testApplication {
            val server = MockWebServer()
            mockWebServer = server
            server.enqueue(MockResponse().setBody("""{"ok":true,"modified":true}"""))

            val apiBaseUrl = server.url("").toString().removeSuffix("/")

            application {
                routing {
                    serve(
                        path = "/api/inngest",
                        client = Inngest("test-app", eventKey = "evt-key", isDev = true),
                        fnList = listOf(EchoFunction()),
                        baseUrl = apiBaseUrl,
                    )
                }
            }

            val response =
                client.put("/api/inngest?deployId=deploy-1") {
                    header(InngestHeaderKey.ServerKind.value, "cloud")
                }

            val body = mapper.readTree(response.bodyAsText())
            val recordedRequest = server.takeRequest()
            val requestBody = mapper.readTree(recordedRequest.body.readUtf8())
            val function = requestBody["functions"][0]
            val runtime = function["steps"]["step"]["runtime"]

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Successfully synced.", body["message"].asText())
            assertTrue(body["modified"].asBoolean())
            assertEquals("/fn/register?deployId=deploy-1", recordedRequest.path)
            assertEquals("cloud", recordedRequest.getHeader(InngestHeaderKey.ExpectedServerKind.value))
            assertEquals("2", recordedRequest.getHeader(InngestHeaderKey.RequestVersion.value))
            assertEquals(recordedRequest.getHeader(InngestHeaderKey.Sdk.value), requestBody["sdk"].asText())
            assertEquals("test-app-echo-fn", function["id"].asText())
            assertEquals("http", runtime["type"].asText())
            assertEquals("${requestBody["url"].asText()}?fnId=test-app-echo-fn&stepId=step", runtime["url"].asText())
        }

    @Test
    fun `put route returns sync failure payload when register fails`() =
        testApplication {
            val server = MockWebServer()
            mockWebServer = server
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid config"}"""))

            val apiBaseUrl = server.url("").toString().removeSuffix("/")

            application {
                routing {
                    serve(
                        path = "/api/inngest",
                        client = Inngest("test-app", eventKey = "evt-key", isDev = true),
                        fnList = listOf(EchoFunction()),
                        baseUrl = apiBaseUrl,
                    )
                }
            }

            val response = client.put("/api/inngest")
            val body = mapper.readTree(response.bodyAsText())

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("invalid config", body["message"].asText())
            assertEquals(false, body["modified"].asBoolean())
        }

    private fun sign(body: String): String {
        val timestamp = Instant.now().epochSecond
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("12345678".toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal((body + timestamp).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "t=$timestamp&s=$digest"
    }

    private class EchoFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("echo-fn")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any = "done"
    }
}
