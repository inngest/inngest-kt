package com.inngest.connect

/**
 * User-visible state of a worker connection.
 *
 * This is intentionally coarser than the internal per-WebSocket-generation
 * phase: during a gateway drain the worker stays [ACTIVE] while an old
 * generation drains and a replacement is dialed.
 */
enum class ConnectionState {
    /** Initial startup, before the first connection is established. */
    CONNECTING,

    /** Connected and ready to receive executor requests. */
    ACTIVE,

    /**
     * Present for cross-SDK parity; the worker does not currently report this
     * state (local shutdown is [CLOSING]).
     */
    PAUSED,

    /** An established connection was lost and is being re-established. */
    RECONNECTING,

    /** Graceful shutdown in progress: draining in-flight requests. */
    CLOSING,

    /** Fully closed; the connection cannot be reused. */
    CLOSED,
    ;

    /**
     * Valid manager-state transitions, mirroring the Go SDK's
     * manager_state.go. Invalid transitions are ignored (and logged) rather
     * than applied, so e.g. a drain reconnect can never resurrect a CLOSING
     * worker to ACTIVE.
     */
    internal fun canTransitionTo(to: ConnectionState): Boolean =
        when (this) {
            CONNECTING -> to == ACTIVE || to == CLOSING || to == CLOSED
            ACTIVE -> to == RECONNECTING || to == CLOSING || to == CLOSED
            RECONNECTING -> to == ACTIVE || to == CLOSING || to == CLOSED
            PAUSED -> to == CLOSING || to == CLOSED
            CLOSING -> to == CLOSED
            CLOSED -> false
        }
}
