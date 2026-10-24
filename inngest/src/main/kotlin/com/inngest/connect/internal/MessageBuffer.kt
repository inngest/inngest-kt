package com.inngest.connect.internal

import com.inngest.connect.v1.ConnectProto
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Delivery guarantee for SDK responses.
 *
 * A reply written to the WebSocket is only *probably* delivered: OkHttp's
 * send() is an async enqueue, and even a transmitted frame may race a gateway
 * crash. Every sent reply is therefore tracked as pending until the gateway's
 * WORKER_REPLY_ACK; un-ACKed or unsendable replies move to the buffer and are
 * delivered over HTTP `/v0/connect/flush`. Buffered delivery can duplicate a
 * reply that actually arrived — the gateway deduplicates by request id.
 *
 * All map transitions happen under one lock, and the deadline task re-checks
 * pending membership under that lock before promoting (plan 010, hazard 8).
 * HTTP flush I/O deliberately happens outside the lock.
 */
internal class MessageBuffer(
    private val apiClient: ConnectApiClient,
    private val scheduler: ScheduledExecutorService,
    private val backoffMillis: (Int) -> Long = Backoff::delayMillis,
) {
    companion object {
        const val DEFAULT_REPLY_ACK_DEADLINE_MILLIS = 5_000L
        private const val MAX_FLUSH_ROUNDS = 5
        private val logger = Logger.getLogger("com.inngest.connect")
    }

    private val lock = Any()
    private val pending = HashMap<String, ConnectProto.SDKResponse>()
    private val buffered = LinkedHashMap<String, ConnectProto.SDKResponse>()

    /**
     * Track a reply that was written to the WebSocket; if no ACK arrives
     * within [deadlineMillis] it is promoted to the flush buffer.
     */
    fun addPending(
        response: ConnectProto.SDKResponse,
        deadlineMillis: Long,
    ) {
        synchronized(lock) { pending[response.requestId] = response }
        try {
            scheduler.schedule({ expirePending(response.requestId) }, deadlineMillis, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            // Scheduler is shutting down; expireAllPending() covers teardown.
        }
    }

    private fun expirePending(requestId: String) {
        val promoted =
            synchronized(lock) {
                val response = pending.remove(requestId) ?: return
                buffered[requestId] = response
                true
            }
        if (promoted) {
            logger.warning("reply for request $requestId not acknowledged in time; buffered for HTTP flush")
        }
    }

    /** The gateway confirmed receipt; stop tracking. */
    fun acknowledge(requestId: String) {
        synchronized(lock) { pending.remove(requestId) }
    }

    /** Buffer a reply that could not be written to any WebSocket. */
    fun append(response: ConnectProto.SDKResponse) {
        synchronized(lock) {
            buffered[response.requestId] = response
            pending.remove(response.requestId)
        }
    }

    /**
     * Promote every pending reply to the buffer. Called at teardown, where the
     * deadline tasks may no longer run and ACKs can no longer arrive; a
     * duplicate flush of an actually-delivered reply is safe.
     */
    fun expireAllPending() {
        synchronized(lock) {
            pending.forEach { (requestId, response) -> buffered[requestId] = response }
            pending.clear()
        }
    }

    fun hasBufferedMessages(): Boolean = synchronized(lock) { buffered.isNotEmpty() }

    fun hasPendingMessages(): Boolean = synchronized(lock) { pending.isNotEmpty() }

    /**
     * Deliver buffered replies over HTTP, retrying failed rounds with backoff
     * up to [MAX_FLUSH_ROUNDS]. Returns true when the buffer is empty on exit.
     * Blocking; callers run it on the executor pool or during teardown.
     */
    fun flush(): Boolean {
        for (attempt in 0 until MAX_FLUSH_ROUNDS) {
            val snapshot = synchronized(lock) { buffered.values.toList() }
            if (snapshot.isEmpty()) return true

            if (attempt > 0) {
                try {
                    Thread.sleep(backoffMillis(attempt - 1))
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }

            logger.info("flushing ${snapshot.size} buffered replies over HTTP (attempt ${attempt + 1})")
            var failed = false
            for (response in snapshot) {
                try {
                    apiClient.flush(response)
                    synchronized(lock) { buffered.remove(response.requestId) }
                } catch (e: Exception) {
                    logger.warning("failed to flush reply for request ${response.requestId}: ${e.message}")
                    failed = true
                    break
                }
            }
            if (!failed && synchronized(lock) { buffered.isEmpty() }) return true
        }

        val remaining = synchronized(lock) { buffered.size }
        if (remaining > 0) {
            logger.severe("failed to flush $remaining buffered replies after $MAX_FLUSH_ROUNDS attempts")
        }
        return remaining == 0
    }
}
