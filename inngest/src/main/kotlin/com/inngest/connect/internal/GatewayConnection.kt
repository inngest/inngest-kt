package com.inngest.connect.internal

import com.google.protobuf.ByteString
import com.inngest.connect.v1.ConnectProto
import okhttp3.WebSocket
import okio.ByteString.Companion.toByteString

/** Outcome of a gated protocol write. */
internal enum class WriteResult {
    /** Enqueued onto the WebSocket writer. Not a delivery guarantee. */
    SENT,

    /** The generation's phase forbids this message kind; nothing was written. */
    DENIED_BY_PHASE,

    /** The socket rejected the write (closed, failed, or queue full). */
    FAILED,
}

/**
 * One WebSocket generation: the socket, its lifecycle phase, and the
 * gateway-provided intervals. All protocol writes go through [send], which
 * consults the phase's write policy first; the policy is a fast path and the
 * socket-level result is the authority (see plan 010's concurrency section).
 */
internal class GatewayConnection(
    /** Connection id issued by the start request. */
    val id: String,
    val gatewayGroup: String,
    val endpoint: String,
    onEnterNoWrite: Runnable = Runnable {},
) {
    val lifecycle = ConnectionLifecycle(onEnterNoWrite)

    @Volatile
    var ws: WebSocket? = null

    @Volatile
    var heartbeatIntervalMillis: Long = DEFAULT_HEARTBEAT_INTERVAL_MILLIS

    @Volatile
    var extendLeaseIntervalMillis: Long = DEFAULT_EXTEND_LEASE_INTERVAL_MILLIS

    /** Nanotime of the most recent GATEWAY_HEARTBEAT (0 = none yet). */
    @Volatile
    var lastGatewayHeartbeatAtNanos: Long = 0

    /** Nanotime when the handshake completed and the generation became Active. */
    @Volatile
    var activeAtNanos: Long = 0

    val phase: ConnectionPhase
        get() = lifecycle.phase

    fun send(
        kind: ConnectProto.GatewayMessageType,
        payload: ByteString? = null,
    ): WriteResult {
        if (!lifecycle.allowsWrite(kind)) return WriteResult.DENIED_BY_PHASE
        return sendUngated(kind, payload)
    }

    /**
     * Write without consulting the phase policy. Only the handshake
     * (WORKER_CONNECT before the generation is Active) may use this.
     */
    fun sendUngated(
        kind: ConnectProto.GatewayMessageType,
        payload: ByteString? = null,
    ): WriteResult {
        val socket = ws ?: return WriteResult.FAILED
        val builder = ConnectProto.ConnectMessage.newBuilder().setKind(kind)
        if (payload != null) {
            builder.setPayload(payload)
        }
        val enqueued = socket.send(builder.build().toByteArray().toByteString())
        return if (enqueued) WriteResult.SENT else WriteResult.FAILED
    }

    fun retire(): Boolean = lifecycle.transition(ConnectionPhase.Retired) == PhaseTransitionResult.Changed

    /** Close with a normal closure code after transitioning to Closed. */
    fun closeNormal(reason: String) {
        lifecycle.transition(ConnectionPhase.Closed)
        try {
            ws?.close(NORMAL_CLOSURE_CODE, reason.take(MAX_CLOSE_REASON_LENGTH))
        } catch (e: Exception) {
            ws?.cancel()
        }
    }

    /** Abnormal teardown: mark Closed and cancel the socket immediately. */
    fun closeNow() {
        lifecycle.transition(ConnectionPhase.Closed)
        ws?.cancel()
    }

    companion object {
        const val DEFAULT_HEARTBEAT_INTERVAL_MILLIS = 10_000L
        const val DEFAULT_EXTEND_LEASE_INTERVAL_MILLIS = 5_000L
        const val NORMAL_CLOSURE_CODE = 1000
        const val UNEXPECTED_CLOSURE_CODE = 4001

        // RFC 6455 limits close reasons to 123 bytes.
        private const val MAX_CLOSE_REASON_LENGTH = 123
    }
}
