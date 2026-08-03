package com.inngest.connect.internal

import com.google.protobuf.ByteString
import com.inngest.connect.v1.ConnectProto
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * In-process mock of the connect control plane: an HTTP server for
 * `/v0/connect/start` + `/v0/connect/flush` and a WebSocket server acting as
 * the gateway, exercising real network code paths (protobuf framing, binary
 * WS encoding, TCP lifecycle). Ported from the JS SDK's mock-gateway.ts.
 */
internal class MockGateway(
    private val autoHandshake: Boolean = true,
    private val heartbeatInterval: String = "10s",
    private val extendLeaseInterval: String = "5s",
    private val statusInterval: String = "",
    private val gatewayGroup: String = "gw-mock",
) : Closeable {
    val httpServer = MockWebServer()
    val wsServer = MockWebServer()

    /** One connected worker socket, in accept order. */
    class GatewaySocket(
        val socket: okhttp3.WebSocket,
    ) {
        val received = LinkedBlockingQueue<ConnectProto.ConnectMessage>()
        val closeEvents = LinkedBlockingQueue<Pair<Int, String>>()

        fun send(
            kind: ConnectProto.GatewayMessageType,
            payload: ByteString? = null,
        ) {
            val builder = ConnectProto.ConnectMessage.newBuilder().setKind(kind)
            if (payload != null) builder.setPayload(payload)
            socket.send(builder.build().toByteArray().toByteString())
        }

        /** Waits for the next message of [kind], failing after [timeoutSeconds]. */
        fun expect(
            kind: ConnectProto.GatewayMessageType,
            timeoutSeconds: Long = 5,
        ): ConnectProto.ConnectMessage {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            while (true) {
                val remaining = deadline - System.nanoTime()
                require(remaining > 0) { "timed out waiting for $kind" }
                val message =
                    received.poll(remaining, TimeUnit.NANOSECONDS)
                        ?: throw AssertionError("timed out waiting for $kind")
                if (message.kind == kind) return message
            }
        }
    }

    val sockets = LinkedBlockingQueue<GatewaySocket>()
    val allSockets = CopyOnWriteArrayList<GatewaySocket>()
    val startRequests = LinkedBlockingQueue<RecordedRequest>()
    val flushRequests = LinkedBlockingQueue<RecordedRequest>()

    init {
        httpServer.start()
        wsServer.start()
        httpServer.dispatcher =
            object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/v0/connect/start" -> {
                            startRequests.add(request)
                            MockResponse()
                                .setResponseCode(200)
                                .setBody(Buffer().write(startResponse().toByteArray()))
                        }

                        "/v0/connect/flush" -> {
                            flushRequests.add(request)
                            // Clone: reading drains the Buffer, and tests read it again.
                            val sdkResponse = ConnectProto.SDKResponse.parseFrom(request.body.clone().readByteArray())
                            MockResponse()
                                .setResponseCode(200)
                                .setBody(
                                    Buffer().write(
                                        ConnectProto.FlushResponse
                                            .newBuilder()
                                            .setRequestId(sdkResponse.requestId)
                                            .build()
                                            .toByteArray(),
                                    ),
                                )
                        }

                        else -> {
                            MockResponse().setResponseCode(404)
                        }
                    }
            }
    }

    /** Base URL for [ConnectApiClient]. */
    fun apiBaseUrl(): String = httpServer.url("/").toString()

    private fun startResponse(): ConnectProto.StartResponse =
        ConnectProto.StartResponse
            .newBuilder()
            .setConnectionId("conn-${System.nanoTime()}")
            .setGatewayEndpoint(wsServer.url("/v0/connect").toString().replaceFirst("http", "ws"))
            .setGatewayGroup(gatewayGroup)
            .setSessionToken("session-token")
            .setSyncToken("sync-token")
            .build()

    /**
     * Allows one more WebSocket connection to be accepted. Each expected
     * worker connection (initial, reconnect, drain replacement) needs one.
     */
    fun expectConnection() {
        wsServer.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : okhttp3.WebSocketListener() {
                    lateinit var gatewaySocket: GatewaySocket

                    override fun onOpen(
                        webSocket: okhttp3.WebSocket,
                        response: okhttp3.Response,
                    ) {
                        gatewaySocket = GatewaySocket(webSocket)
                        allSockets.add(gatewaySocket)
                        sockets.add(gatewaySocket)
                        if (autoHandshake) {
                            gatewaySocket.send(ConnectProto.GatewayMessageType.GATEWAY_HELLO)
                        }
                    }

                    override fun onMessage(
                        webSocket: okhttp3.WebSocket,
                        bytes: okio.ByteString,
                    ) {
                        val message = ConnectProto.ConnectMessage.parseFrom(bytes.toByteArray())
                        if (autoHandshake &&
                            message.kind == ConnectProto.GatewayMessageType.WORKER_CONNECT
                        ) {
                            gatewaySocket.send(
                                ConnectProto.GatewayMessageType.GATEWAY_CONNECTION_READY,
                                ConnectProto.GatewayConnectionReadyData
                                    .newBuilder()
                                    .setHeartbeatInterval(heartbeatInterval)
                                    .setExtendLeaseInterval(extendLeaseInterval)
                                    .setStatusInterval(statusInterval)
                                    .build()
                                    .toByteString(),
                            )
                        }
                        gatewaySocket.received.add(message)
                    }

                    override fun onClosing(
                        webSocket: okhttp3.WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        gatewaySocket.closeEvents.add(code to reason)
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
    }

    /** The next accepted worker socket (blocks up to [timeoutSeconds]). */
    fun awaitSocket(timeoutSeconds: Long = 5): GatewaySocket =
        sockets.poll(timeoutSeconds, TimeUnit.SECONDS)
            ?: throw AssertionError("no worker connection within ${timeoutSeconds}s")

    override fun close() {
        httpServer.shutdown()
        wsServer.shutdown()
    }
}
