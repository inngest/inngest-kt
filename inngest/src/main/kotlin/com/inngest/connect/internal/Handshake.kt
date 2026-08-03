package com.inngest.connect.internal

import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import com.inngest.connect.v1.ConnectProto
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.logging.Logger

/**
 * Messages and socket-level events delivered after a successful handshake.
 * Implementations must be fast and non-blocking: callbacks arrive on OkHttp's
 * reader thread.
 */
internal interface GatewayMessageHandler {
    fun onMessage(
        conn: GatewayConnection,
        message: ConnectProto.ConnectMessage,
    )

    fun onSocketClosed(
        conn: GatewayConnection,
        code: Int,
        reason: String,
    )

    fun onSocketFailure(
        conn: GatewayConnection,
        t: Throwable,
    )
}

/**
 * Establishes one gateway connection: start request, WebSocket dial, and the
 * GATEWAY_HELLO -> WORKER_CONNECT -> GATEWAY_CONNECTION_READY handshake.
 *
 * Timeouts follow the Go SDK: the dial is bounded by the HTTP client's
 * connect timeout, HELLO by [HELLO_TIMEOUT_MILLIS], and CONNECTION_READY by
 * [READY_TIMEOUT_MILLIS] (the longer budget covers the server-side app sync).
 */
internal class Handshake(
    private val config: ConnectConfig,
    private val apiClient: ConnectApiClient,
    private val httpClient: OkHttpClient,
) {
    companion object {
        const val WS_SUBPROTOCOL = "v0.connect.inngest.com"
        const val HELLO_TIMEOUT_MILLIS = 15_000L // includes the async dial
        const val READY_TIMEOUT_MILLIS = 20_000L

        private val logger = Logger.getLogger("com.inngest.connect")
    }

    /**
     * Blocks until the connection is Active with steady-state handlers
     * attached, or throws:
     * - [ConnectAuthException] / [ConnectionLimitException] from the start request,
     * - [ConnectApiException] for any retriable failure (dial, handshake
     *   protocol violation, timeout, SYNC_FAILED).
     */
    fun establish(
        excludeGateways: Collection<String>,
        steadyStateHandler: GatewayMessageHandler,
        onEnterNoWrite: Runnable,
    ): GatewayConnection {
        val startResponse = apiClient.start(excludeGateways)
        val endpoint = config.gatewayUrlOverride ?: startResponse.gatewayEndpoint

        val conn =
            GatewayConnection(
                id = startResponse.connectionId,
                gatewayGroup = startResponse.gatewayGroup,
                endpoint = endpoint,
                onEnterNoWrite = onEnterNoWrite,
            )
        conn.lifecycle.transition(ConnectionPhase.Handshaking)

        val helloReceived = CompletableFuture<Unit>()
        val ready = CompletableFuture<ConnectProto.GatewayConnectionReadyData>()

        val listener = HandshakeListener(conn, startResponse, helloReceived, ready, steadyStateHandler)

        val request =
            Request
                .Builder()
                // OkHttp accepts ws:// and wss:// here and maps them to http(s).
                .url(endpoint)
                .header("Sec-WebSocket-Protocol", WS_SUBPROTOCOL)
                .build()

        conn.ws = httpClient.newWebSocket(request, listener)

        try {
            await(helloReceived, HELLO_TIMEOUT_MILLIS, "GATEWAY_HELLO")
            val readyData = await(ready, READY_TIMEOUT_MILLIS, "GATEWAY_CONNECTION_READY")

            conn.heartbeatIntervalMillis =
                GoDuration.toMillis(readyData.heartbeatInterval, GatewayConnection.DEFAULT_HEARTBEAT_INTERVAL_MILLIS)
            conn.extendLeaseIntervalMillis =
                GoDuration.toMillis(
                    readyData.extendLeaseInterval,
                    GatewayConnection.DEFAULT_EXTEND_LEASE_INTERVAL_MILLIS,
                )

            conn.activeAtNanos = System.nanoTime()
            conn.lifecycle.transition(ConnectionPhase.Active)
            listener.markHandshakeComplete()

            logger.fine("connection ${conn.id} established to ${conn.gatewayGroup}")
            return conn
        } catch (e: Exception) {
            conn.retire()
            try {
                conn.ws?.close(
                    GatewayConnection.UNEXPECTED_CLOSURE_CODE,
                    ConnectProto.WorkerDisconnectReason.UNEXPECTED.name,
                )
            } catch (closeError: Exception) {
                conn.ws?.cancel()
            }
            conn.lifecycle.transition(ConnectionPhase.Closed)
            throw e
        }
    }

    private fun <T> await(
        future: CompletableFuture<T>,
        timeoutMillis: Long,
        stage: String,
    ): T =
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw ConnectApiException("timed out waiting for $stage")
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is ConnectApiException) throw cause
            throw ConnectApiException("handshake failed waiting for $stage", cause ?: e)
        }

    private inner class HandshakeListener(
        private val conn: GatewayConnection,
        private val startResponse: ConnectProto.StartResponse,
        private val helloReceived: CompletableFuture<Unit>,
        private val ready: CompletableFuture<ConnectProto.GatewayConnectionReadyData>,
        private val steadyStateHandler: GatewayMessageHandler,
    ) : WebSocketListener() {
        @Volatile
        private var handshakeComplete = false

        /**
         * Guards the hand-off from handshake to steady-state dispatch. A
         * message can arrive on the reader thread after READY resolves but
         * before establish() flips [handshakeComplete]; without the buffer it
         * would be silently dropped (a check-then-act hazard). Post-READY
         * messages are queued under this lock and drained by
         * [markHandshakeComplete], which preserves delivery order because
         * OkHttp delivers reader callbacks sequentially.
         */
        private val dispatchLock = Any()
        private val pendingSteadyMessages = ArrayList<ConnectProto.ConnectMessage>()

        fun markHandshakeComplete() {
            val drained: List<ConnectProto.ConnectMessage>
            synchronized(dispatchLock) {
                handshakeComplete = true
                drained = ArrayList(pendingSteadyMessages)
                pendingSteadyMessages.clear()
            }
            drained.forEach { steadyStateHandler.onMessage(conn, it) }
        }

        private fun readyResolvedSuccessfully(): Boolean = ready.isDone && !ready.isCompletedExceptionally

        override fun onMessage(
            webSocket: WebSocket,
            bytes: okio.ByteString,
        ) {
            val message: ConnectProto.ConnectMessage
            try {
                message = ConnectProto.ConnectMessage.parseFrom(bytes.toByteArray())
            } catch (e: Exception) {
                logger.warning("dropping unparseable gateway message on connection ${conn.id}: $e")
                return
            }

            if (handshakeComplete) {
                steadyStateHandler.onMessage(conn, message)
                return
            }

            when (message.kind) {
                ConnectProto.GatewayMessageType.GATEWAY_HELLO -> {
                    if (helloReceived.isDone) return
                    val sent =
                        conn.sendUngated(
                            ConnectProto.GatewayMessageType.WORKER_CONNECT,
                            buildWorkerConnectPayload().toByteString(),
                        )
                    if (sent != WriteResult.SENT) {
                        helloReceived.completeExceptionally(
                            ConnectApiException("failed to send WORKER_CONNECT on connection ${conn.id}"),
                        )
                        return
                    }
                    helloReceived.complete(Unit)
                }

                ConnectProto.GatewayMessageType.GATEWAY_CONNECTION_READY -> {
                    try {
                        ready.complete(ConnectProto.GatewayConnectionReadyData.parseFrom(message.payload))
                    } catch (e: Exception) {
                        ready.completeExceptionally(
                            ConnectApiException("invalid GATEWAY_CONNECTION_READY payload", e),
                        )
                    }
                }

                ConnectProto.GatewayMessageType.SYNC_FAILED -> {
                    val error =
                        try {
                            ConnectProto.SystemError.parseFrom(message.payload).message
                        } catch (e: Exception) {
                            "unknown sync error"
                        }
                    failHandshake(ConnectApiException("app sync failed during handshake: $error"))
                }

                else -> {
                    synchronized(dispatchLock) {
                        when {
                            handshakeComplete -> {
                                steadyStateHandler.onMessage(conn, message)
                            }

                            readyResolvedSuccessfully() -> {
                                pendingSteadyMessages.add(message)
                            }

                            else -> {
                                failHandshake(
                                    ConnectApiException(
                                        "unexpected message kind during handshake: ${message.kind}",
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            // OkHttp does not acknowledge the server's close frame until we
            // call close(); without the echo, onClosed never fires and the
            // connection hangs half-closed. Treat onClosing as connection
            // death immediately (onSocketClosed/onConnectionDead are
            // idempotent, so the later onClosed is harmless).
            try {
                webSocket.close(code, reason)
            } catch (e: Exception) {
                webSocket.cancel()
            }
            if (handshakeComplete || readyResolvedSuccessfully()) {
                steadyStateHandler.onSocketClosed(conn, code, reason)
            } else {
                failHandshake(ConnectApiException("connection closed during handshake: $code $reason"))
            }
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            if (handshakeComplete || readyResolvedSuccessfully()) {
                steadyStateHandler.onSocketClosed(conn, code, reason)
            } else {
                failHandshake(ConnectApiException("connection closed during handshake: $code $reason"))
            }
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            if (handshakeComplete || readyResolvedSuccessfully()) {
                steadyStateHandler.onSocketFailure(conn, t)
            } else {
                failHandshake(ConnectApiException("connection failed during handshake", t))
            }
        }

        private fun failHandshake(error: ConnectApiException) {
            helloReceived.completeExceptionally(error)
            ready.completeExceptionally(error)
        }

        private fun buildWorkerConnectPayload(): ConnectProto.WorkerConnectRequestData {
            val builder =
                ConnectProto.WorkerConnectRequestData
                    .newBuilder()
                    .setConnectionId(startResponse.connectionId)
                    .setInstanceId(config.instanceId)
                    .setAuthData(
                        ConnectProto.AuthData
                            .newBuilder()
                            .setSessionToken(startResponse.sessionToken)
                            .setSyncToken(startResponse.syncToken),
                    ).setCapabilities(ByteString.copyFromUtf8(ConnectConfig.CAPABILITIES_JSON))
                    .setWorkerManualReadinessAck(true)
                    .setSystemAttributes(SystemAttributes.retrieve())
                    .setFramework(ConnectConfig.FRAMEWORK)
                    .setSdkVersion(config.sdkVersion)
                    .setSdkLanguage(ConnectConfig.SDK_LANGUAGE)
                    .setStartedAt(nowTimestamp())
                    .setMaxWorkerConcurrency(config.maxWorkerConcurrency.toLong())

            if (config.envName != null) {
                builder.setEnvironment(config.envName)
            }

            config.apps.forEach { app ->
                builder.addApps(
                    ConnectProto.AppConfiguration
                        .newBuilder()
                        .setAppName(app.appName)
                        .setFunctions(ByteString.copyFromUtf8(app.functionsJson)),
                )
            }

            return builder.build()
        }

        private fun nowTimestamp(): Timestamp {
            val nowMillis = System.currentTimeMillis()
            return Timestamp
                .newBuilder()
                .setSeconds(nowMillis / 1_000)
                .setNanos(((nowMillis % 1_000) * 1_000_000).toInt())
                .build()
        }
    }
}
