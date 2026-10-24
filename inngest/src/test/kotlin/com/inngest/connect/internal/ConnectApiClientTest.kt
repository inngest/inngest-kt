package com.inngest.connect.internal

import com.google.protobuf.ByteString
import com.inngest.connect.v1.ConnectProto
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

internal class ConnectApiClientTest {
    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun client(
        envName: String? = null,
        authHeaders: Map<String, String> = emptyMap(),
    ): ConnectApiClient =
        ConnectApiClient(
            apiBaseUrl = server.url("/").toString(),
            envName = envName,
            authHeaders = authHeaders,
        )

    private fun startResponse(): ConnectProto.StartResponse =
        ConnectProto.StartResponse
            .newBuilder()
            .setConnectionId("conn-1")
            .setGatewayEndpoint("ws://127.0.0.1:1234/v0/connect")
            .setGatewayGroup("gw-group")
            .setSessionToken("session")
            .setSyncToken("sync")
            .build()

    @Test
    fun `start posts protobuf and parses the response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(startResponse().toByteArray())),
        )

        val response =
            client(envName = "my-branch", authHeaders = mapOf("Authorization" to "Bearer hashed"))
                .start(listOf("bad-gateway"))

        assertEquals("conn-1", response.connectionId)
        assertEquals("ws://127.0.0.1:1234/v0/connect", response.gatewayEndpoint)
        assertEquals("gw-group", response.gatewayGroup)

        val recorded = server.takeRequest()
        assertEquals("/v0/connect/start", recorded.path)
        assertEquals("application/protobuf", recorded.getHeader("Content-Type"))
        assertEquals("Bearer hashed", recorded.getHeader("Authorization"))
        assertEquals("my-branch", recorded.getHeader("x-inngest-env"))

        val sent = ConnectProto.StartRequest.parseFrom(recorded.body.readByteArray())
        assertEquals(listOf("bad-gateway"), sent.excludeGatewaysList)
    }

    @Test
    fun `start omits auth and env headers when not configured`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(startResponse().toByteArray())),
        )

        client().start(emptyList())

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("x-inngest-env"))
    }

    @Test
    fun `start maps 401 to an auth exception`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        assertFailsWith<ConnectAuthException> { client(envName = "branch").start(emptyList()) }
    }

    @Test
    fun `start maps 429 to a connection limit exception`() {
        server.enqueue(MockResponse().setResponseCode(429))

        assertFailsWith<ConnectionLimitException> { client().start(emptyList()) }
    }

    @Test
    fun `start maps other failures to a retriable api exception`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        assertFailsWith<ConnectApiException> { client().start(emptyList()) }
    }

    @Test
    fun `start maps connection failures to a retriable api exception`() {
        val closed = ConnectApiClient(apiBaseUrl = "http://127.0.0.1:1", envName = null, authHeaders = emptyMap())

        assertFailsWith<ConnectApiException> { closed.start(emptyList()) }
    }

    @Test
    fun `flush posts the sdk response and parses the flush response`() {
        val flushResponse =
            ConnectProto.FlushResponse
                .newBuilder()
                .setRequestId("req-1")
                .build()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(flushResponse.toByteArray())),
        )

        val sdkResponse =
            ConnectProto.SDKResponse
                .newBuilder()
                .setRequestId("req-1")
                .setStatus(ConnectProto.SDKResponseStatus.DONE)
                .setBody(ByteString.copyFromUtf8("{}"))
                .build()

        val parsed = client(authHeaders = mapOf("Authorization" to "Bearer hashed")).flush(sdkResponse)

        assertEquals("req-1", parsed.requestId)
        val recorded = server.takeRequest()
        assertEquals("/v0/connect/flush", recorded.path)
        assertEquals("Bearer hashed", recorded.getHeader("Authorization"))
        assertEquals(
            sdkResponse,
            ConnectProto.SDKResponse.parseFrom(recorded.body.readByteArray()),
        )
    }

    @Test
    fun `flush maps failures to a retriable api exception`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        assertFailsWith<ConnectApiException> {
            client().flush(
                ConnectProto.SDKResponse
                    .newBuilder()
                    .setRequestId("req-1")
                    .build(),
            )
        }
    }
}
