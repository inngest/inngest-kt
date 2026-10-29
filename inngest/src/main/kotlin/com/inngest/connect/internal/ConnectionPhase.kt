package com.inngest.connect.internal

import com.inngest.connect.v1.ConnectProto

/**
 * Lifecycle phase of a single WebSocket generation.
 *
 * This is deliberately separate from the coarse, user-visible connection state
 * of the whole worker: during a gateway drain an old generation is Draining or
 * Retired while the worker as a whole remains ACTIVE on its replacement.
 *
 * Ported from the Go SDK (connect/connection_lifecycle.go), whose phase
 * machine with gated writes is the core defense against check-then-act races:
 * every protocol write asks the owning generation's phase, and the phase can
 * only move through validated transitions.
 */
internal enum class ConnectionPhase {
    /** Local object exists but the gateway WebSocket handshake has not started. */
    New,

    /** Only the connect handshake protocol is allowed; no request-protocol writes. */
    Handshaking,

    /** Normal read-loop phase: ACKs, heartbeats, lease extensions, replies, status. */
    Active,

    /**
     * Gateway-initiated replacement. New request ACKs and heartbeats stop, but
     * already-ACKed work is still owned here and may extend leases and reply.
     */
    Draining,

    /**
     * Local worker shutdown. Permits WORKER_PAUSE plus lease extensions and
     * replies for already-ACKed work while the executor pool drains.
     */
    Closing,

    /**
     * Hard no-write boundary. Queued pre-ACK work is skipped and completed
     * work buffers replies for HTTP flush instead of using this WebSocket.
     */
    Retired,

    /** Transport closed (or best-effort closed). Same write policy as Retired. */
    Closed,
    ;

    /** True once this generation must never write to its WebSocket again. */
    val isNoWrite: Boolean
        get() = this == Retired || this == Closed

    /**
     * Valid phase transitions. Anything not listed is a programming error and
     * is rejected (and logged by the caller) rather than applied.
     */
    fun canTransitionTo(to: ConnectionPhase): Boolean =
        when (this) {
            New -> to == Handshaking || to == Retired || to == Closed
            Handshaking -> to == Active || to == Retired || to == Closed
            Active -> to == Draining || to == Closing || to == Retired || to == Closed
            Draining -> to == Retired || to == Closed
            Closing -> to == Retired || to == Closed
            Retired -> to == Closed
            Closed -> false
        }

    /**
     * Write policy: which protocol messages may be written to the WebSocket in
     * this phase. Handshake writes (WORKER_CONNECT) are intentionally not
     * modeled — they happen on the raw socket before the generation is Active.
     *
     * The policy is the authority consulted before a write, but it is only a
     * fast path: the actual write can still fail, and the failure paths (skip
     * pre-ACK, buffer replies, retire the generation) are what make the
     * check-then-act window safe. See plan 010's concurrency section.
     */
    fun allowsWrite(kind: ConnectProto.GatewayMessageType): Boolean =
        when (this) {
            Active -> {
                when (kind) {
                    ConnectProto.GatewayMessageType.WORKER_HEARTBEAT,
                    ConnectProto.GatewayMessageType.WORKER_REQUEST_ACK,
                    ConnectProto.GatewayMessageType.WORKER_REPLY,
                    ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE,
                    ConnectProto.GatewayMessageType.WORKER_PAUSE,
                    ConnectProto.GatewayMessageType.WORKER_STATUS,
                    ConnectProto.GatewayMessageType.WORKER_READY,
                    -> true

                    else -> false
                }
            }

            Draining -> {
                when (kind) {
                    ConnectProto.GatewayMessageType.WORKER_REPLY,
                    ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE,
                    -> true

                    else -> false
                }
            }

            Closing -> {
                when (kind) {
                    ConnectProto.GatewayMessageType.WORKER_REPLY,
                    ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE,
                    ConnectProto.GatewayMessageType.WORKER_PAUSE,
                    -> true

                    else -> false
                }
            }

            else -> {
                false
            }
        }
}

/** Outcome of a requested phase transition. */
internal enum class PhaseTransitionResult {
    /** The phase changed to the requested value. */
    Changed,

    /** Already in the requested phase; nothing to do. */
    NoOp,

    /** The transition is not allowed from the current phase; state unchanged. */
    Invalid,
}

/**
 * Thread-safe holder for a generation's [ConnectionPhase].
 *
 * All mutations go through [transition] so that write permission, the
 * no-write boundary callback, and validation stay coupled. [onEnterNoWrite]
 * fires exactly once, outside the lock, when the generation first reaches a
 * no-write phase — used to notify the flush path that buffered replies may
 * need HTTP delivery.
 */
internal class ConnectionLifecycle(
    private val onEnterNoWrite: Runnable = Runnable {},
) {
    private val lock = Any()

    @Volatile
    private var currentPhase = ConnectionPhase.New

    val phase: ConnectionPhase
        get() = currentPhase

    fun allowsWrite(kind: ConnectProto.GatewayMessageType): Boolean = currentPhase.allowsWrite(kind)

    fun transition(to: ConnectionPhase): PhaseTransitionResult {
        val enteredNoWrite: Boolean
        synchronized(lock) {
            val from = currentPhase
            if (from == to) return PhaseTransitionResult.NoOp
            if (!from.canTransitionTo(to)) return PhaseTransitionResult.Invalid
            currentPhase = to
            enteredNoWrite = to.isNoWrite && !from.isNoWrite
        }
        if (enteredNoWrite) {
            onEnterNoWrite.run()
        }
        return PhaseTransitionResult.Changed
    }
}
