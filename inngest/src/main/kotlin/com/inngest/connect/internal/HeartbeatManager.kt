package com.inngest.connect.internal

import com.inngest.connect.v1.ConnectProto
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Worker heartbeat sender plus gateway-heartbeat watchdog for the active
 * generation.
 *
 * Liveness follows the Go SDK's watchdog model rather than the JS SDK's
 * shared miss counter: the sender and the watchdog share no mutable state.
 * The watchdog computes a monotonic deadline from the last GATEWAY_HEARTBEAT
 * receipt (or the connection's activation time before the first one) and
 * retires the generation when `(tolerance + 1) * interval` elapses with no
 * heartbeat.
 */
internal class HeartbeatManager(
    private val scheduler: ScheduledExecutorService,
    private val missedHeartbeatTolerance: Int,
    private val onConnectionDead: (GatewayConnection) -> Unit,
) {
    companion object {
        private val logger = Logger.getLogger("com.inngest.connect")
    }

    private val lock = Any()
    private var senderTask: ScheduledFuture<*>? = null
    private var watchdogTask: ScheduledFuture<*>? = null

    /** Start heartbeating [conn]; any previous generation's tasks are cancelled. */
    fun attach(conn: GatewayConnection) {
        synchronized(lock) {
            cancelLocked()

            val interval = conn.heartbeatIntervalMillis
            senderTask =
                scheduler.scheduleAtFixedRate(
                    { sendHeartbeat(conn) },
                    interval,
                    interval,
                    TimeUnit.MILLISECONDS,
                )
            watchdogTask =
                scheduler.scheduleAtFixedRate(
                    { checkGatewayHeartbeat(conn) },
                    interval,
                    interval,
                    TimeUnit.MILLISECONDS,
                )
        }
    }

    fun stop() {
        synchronized(lock) { cancelLocked() }
    }

    private fun cancelLocked() {
        senderTask?.cancel(false)
        watchdogTask?.cancel(false)
        senderTask = null
        watchdogTask = null
    }

    private fun sendHeartbeat(conn: GatewayConnection) {
        when (conn.send(ConnectProto.GatewayMessageType.WORKER_HEARTBEAT)) {
            WriteResult.SENT -> {
                logger.finest("worker heartbeat sent on ${conn.id}")
            }

            WriteResult.DENIED_BY_PHASE -> {
                logger.fine("skipping worker heartbeat, phase ${conn.phase} on ${conn.id}")
            }

            WriteResult.FAILED -> {
                logger.fine("worker heartbeat write failed on ${conn.id}")
            }
        }
    }

    private fun checkGatewayHeartbeat(conn: GatewayConnection) {
        if (conn.phase != ConnectionPhase.Active) return

        val baseNanos = maxOf(conn.lastGatewayHeartbeatAtNanos, conn.activeAtNanos)
        val deadlineNanos =
            baseNanos +
                TimeUnit.MILLISECONDS.toNanos((missedHeartbeatTolerance + 1L) * conn.heartbeatIntervalMillis)

        if (System.nanoTime() > deadlineNanos) {
            logger.warning(
                "no gateway heartbeat within ${missedHeartbeatTolerance + 1} intervals " +
                    "(${conn.heartbeatIntervalMillis}ms) on connection ${conn.id}; reconnecting",
            )
            if (conn.retire()) {
                onConnectionDead(conn)
            }
        }
    }
}
