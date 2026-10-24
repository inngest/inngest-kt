package com.inngest.connect.internal

import com.inngest.connect.v1.ConnectProto
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Periodic WORKER_STATUS reports for the active generation, so the gateway
 * can observe in-flight requests and shutdown state. Opt-in: the gateway
 * enables it by sending a non-zero status interval in CONNECTION_READY.
 */
internal class StatusReporter(
    private val scheduler: ScheduledExecutorService,
    private val inFlightRequestIds: () -> List<String>,
    private val shutdownRequested: () -> Boolean,
) {
    companion object {
        private val logger = Logger.getLogger("com.inngest.connect")
    }

    private val lock = Any()
    private var task: ScheduledFuture<*>? = null

    /** Start reporting for [conn]; cancels any previous generation's task. */
    fun attach(conn: GatewayConnection) {
        synchronized(lock) {
            cancelLocked()
            val interval = conn.statusIntervalMillis
            if (interval <= 0) return
            task =
                scheduler.scheduleAtFixedRate(
                    { report(conn) },
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
        task?.cancel(false)
        task = null
    }

    private fun report(conn: GatewayConnection) {
        val payload =
            ConnectProto.WorkerStatusData
                .newBuilder()
                .addAllInFlightRequestIds(inFlightRequestIds())
                .setShutdownRequested(shutdownRequested())
                .build()
                .toByteString()

        val result = conn.send(ConnectProto.GatewayMessageType.WORKER_STATUS, payload)
        logger.finest("worker status send on ${conn.id}: $result")
    }
}
