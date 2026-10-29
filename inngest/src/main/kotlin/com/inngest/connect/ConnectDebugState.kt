package com.inngest.connect

/** Snapshot of a worker connection's health for debugging and diagnostics. */
class ConnectDebugState(
    /** Current user-visible connection state. */
    val state: ConnectionState,
    /** Active gateway connection id, if any. */
    val activeConnectionId: String?,
    /** Draining (being replaced) gateway connection id, if any. */
    val drainingConnectionId: String?,
    /** Whether graceful shutdown has been requested. */
    val shutdownRequested: Boolean,
    /** Requests currently owned by this worker (queued or executing). */
    val inFlightRequestCount: Int,
    /** Request ids that have been ACKed and are executing. */
    val inFlightRequestIds: List<String>,
    /** Milliseconds since the last gateway heartbeat, or null before the first. */
    val millisSinceLastGatewayHeartbeat: Long?,
) {
    override fun toString(): String =
        "ConnectDebugState(state=$state, activeConnectionId=$activeConnectionId, " +
            "drainingConnectionId=$drainingConnectionId, shutdownRequested=$shutdownRequested, " +
            "inFlightRequestCount=$inFlightRequestCount, inFlightRequestIds=$inFlightRequestIds, " +
            "millisSinceLastGatewayHeartbeat=$millisSinceLastGatewayHeartbeat)"
}
