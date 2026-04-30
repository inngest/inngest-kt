package com.inngest.ktor

import com.fasterxml.jackson.databind.ObjectMapper
import com.inngest.FunctionContext
import com.inngest.Inngest
import com.inngest.InngestFunction
import com.inngest.InngestFunctionConfigBuilder
import com.inngest.InngestHeaderKey
import com.inngest.Step
import com.inngest.testing.ProtocolFixtures
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

internal class RouteTest {
    private val mapper = ObjectMapper()
    private var mockWebServer: MockWebServer? = null

    @AfterEach
    fun tearDown() {
        mockWebServer?.shutdown()
        mockWebServer = null
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
                client.post("/api/inngest?fnId=echo-fn") {
                    contentType(ContentType.Application.Json)
                    setBody(ProtocolFixtures.executionRequestPayloadJson("echo-fn"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("2", response.headers[InngestHeaderKey.RequestVersion.value])
            assertEquals("ktor", response.headers[InngestHeaderKey.Framework.value])
            assertEquals("\"done\"", response.bodyAsText())
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

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Successfully synced.", body["message"].asText())
            assertTrue(body["modified"].asBoolean())
            assertEquals("/fn/register?deployId=deploy-1", recordedRequest.path)
            assertEquals("cloud", recordedRequest.getHeader(InngestHeaderKey.ExpectedServerKind.value))
            assertEquals("2", recordedRequest.getHeader(InngestHeaderKey.RequestVersion.value))
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

    private class EchoFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("echo-fn")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any = "done"
    }
}
