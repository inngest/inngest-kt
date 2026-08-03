package com.inngest.connect.internal

import com.inngest.connect.ConnectionState
import com.inngest.connect.v1.ConnectProto
import okhttp3.OkHttpClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

/** Why the reconcile loop was woken; used for log vocabulary and coalescing. */
internal enum class WakeReason {
    SHUTDOWN_REQUESTED,
    WS_ERROR,
    WS_CLOSE,
    GATEWAY_CLOSING,
    HEARTBEAT_MISSED,
    REQUEST_FINISHED_ON_SHUTDOWN,
    BUFFERED_REPLIES_PENDING,
}

/**
 * Owns the worker's connection lifecycle: a reconcile loop on a dedicated
 * thread ensures one live gateway connection, reconnecting with backoff and
 * gateway exclusion, handling gateway drains, and exiting only when shutdown
 * is requested and no requests are in flight.
 *
 * Concurrency contract (see plan 010's TOCTOU section):
 * - the supervisor thread is the only writer of [active]/[draining],
 * - all socket writes go through the owning generation's phase-gated send,
 * - socket callbacks only mark state and enqueue wakes; they never block.
 */
internal class ConnectionSupervisor(
    private val config: ConnectConfig,
    private val apiClient: ConnectApiClient,
    private val backoffMillis: (Int) -> Long = Backoff::delayMillis,
    private val drainingRetryMillis: () -> Long = { Backoff.gatewayDrainingRetryMillis() },
    private val replyAckDeadlineMillis: Long = MessageBuffer.DEFAULT_REPLY_ACK_DEADLINE_MILLIS,
) {
    companion object {
        private val logger = Logger.getLogger("com.inngest.connect")
        private const val START_TIMEOUT_MILLIS = 30_000L
        private const val EXECUTOR_KEEP_ALIVE_SECONDS = 60L
    }

    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    private val scheduler =
        ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "inngest-connect-scheduler").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }

    private val heartbeatManager =
        HeartbeatManager(scheduler, config.missedHeartbeatTolerance) { conn ->
            onConnectionDead(conn, WakeReason.HEARTBEAT_MISSED)
        }

    internal val inFlightRequests = InFlightRequests()

    private val executor =
        java.util.concurrent
            .ThreadPoolExecutor(
                config.maxWorkerConcurrency,
                config.maxWorkerConcurrency,
                EXECUTOR_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                LinkedBlockingQueue(),
                object : java.util.concurrent.ThreadFactory {
                    private val counter =
                        java.util.concurrent.atomic
                            .AtomicInteger()

                    override fun newThread(runnable: Runnable): Thread =
                        Thread(runnable, "inngest-connect-worker-${counter.incrementAndGet()}").apply {
                            isDaemon = false
                        }
                },
            ).apply { allowCoreThreadTimeOut(true) }

    private val messageBuffer = MessageBuffer(apiClient, scheduler = scheduler)

    private val requestProcessor =
        RequestProcessor(
            commHandlersByApp = config.apps.associate { it.appName to it.commHandler },
            executor = executor,
            scheduler = scheduler,
            inFlight = inFlightRequests,
            buffer = messageBuffer,
            hooks =
                object : RequestProcessor.Hooks {
                    override fun activeConnection(): GatewayConnection? = active.get()

                    override fun shutdownRequested(): Boolean = shutdownRequested.get()

                    override fun wake(reason: WakeReason) {
                        this@ConnectionSupervisor.wake(reason)
                    }
                },
            sdkResponseVersion = "inngest-kt:${config.sdkVersion}",
            replyAckDeadlineMillis = replyAckDeadlineMillis,
        )

    private val wakeQueue = LinkedBlockingQueue<WakeReason>()
    private val active = AtomicReference<GatewayConnection?>()
    private val draining = AtomicReference<GatewayConnection?>()
    private val shutdownRequested = AtomicBoolean(false)
    private val closeStarted = AtomicBoolean(false)
    private val closedLatch = CountDownLatch(1)
    private val firstReady = java.util.concurrent.CompletableFuture<Unit>()

    private val stateLock = Any()
    private var state = ConnectionState.CONNECTING
    private var hasConnectedBefore = false

    private val excludeGateways = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private var supervisorThread: Thread? = null

    private val handshake = Handshake(config, apiClient, httpClient)

    /**
     * Steady-state message dispatch. Runs on OkHttp reader threads: mark and
     * wake only.
     */
    private val messageHandler =
        object : GatewayMessageHandler {
            override fun onMessage(
                conn: GatewayConnection,
                message: ConnectProto.ConnectMessage,
            ) {
                when (message.kind) {
                    ConnectProto.GatewayMessageType.GATEWAY_HEARTBEAT -> {
                        conn.lastGatewayHeartbeatAtNanos = System.nanoTime()
                        logger.finest("gateway heartbeat received on ${conn.id}")
                    }

                    ConnectProto.GatewayMessageType.GATEWAY_CLOSING -> {
                        logger.info("gateway draining connection ${conn.id}; establishing replacement")
                        if (conn.lifecycle.transition(ConnectionPhase.Draining) == PhaseTransitionResult.Changed) {
                            draining.set(conn)
                            active.compareAndSet(conn, null)
                            wake(WakeReason.GATEWAY_CLOSING)
                        }
                    }

                    ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST -> {
                        requestProcessor.handleExecutorRequest(conn, message)
                    }

                    ConnectProto.GatewayMessageType.WORKER_REPLY_ACK -> {
                        requestProcessor.handleReplyAck(message)
                    }

                    ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE_ACK -> {
                        requestProcessor.handleExtendLeaseAck(message)
                    }

                    else -> {
                        logger.warning("unexpected message kind ${message.kind} on connection ${conn.id}")
                    }
                }
            }

            override fun onSocketClosed(
                conn: GatewayConnection,
                code: Int,
                reason: String,
            ) {
                logger.warning("connection ${conn.id} closed by gateway: $code $reason")
                onConnectionDead(conn, WakeReason.WS_CLOSE)
            }

            override fun onSocketFailure(
                conn: GatewayConnection,
                t: Throwable,
            ) {
                logger.warning("connection ${conn.id} failed: $t")
                onConnectionDead(conn, WakeReason.WS_ERROR)
            }
        }

    val currentState: ConnectionState
        get() = synchronized(stateLock) { state }

    val connectionId: String?
        get() = active.get()?.id

    /**
     * Starts the reconcile loop and blocks until the first connection is
     * ready (or a terminal startup failure/timeout occurs, in which case the
     * supervisor is torn down and the failure is rethrown).
     */
    fun start() {
        val thread =
            Thread({ reconcileLoop() }, "inngest-connect-supervisor").apply {
                isDaemon = false
            }
        supervisorThread = thread
        thread.start()

        try {
            firstReady.get(START_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            close()
            throw ConnectApiException("timed out establishing initial connection after ${START_TIMEOUT_MILLIS}ms")
        } catch (e: java.util.concurrent.ExecutionException) {
            close()
            throw (e.cause as? Exception) ?: e
        }
    }

    /** Graceful shutdown; idempotent and safe to call from any thread. */
    fun close() {
        if (!closeStarted.compareAndSet(false, true)) {
            awaitClosed()
            return
        }

        logger.info("shutting down connect worker (in-flight: ${inFlightRequests.count()})")
        setState(ConnectionState.CLOSING, "close requested")
        shutdownRequested.set(true)

        active.get()?.let { conn ->
            conn.lifecycle.transition(ConnectionPhase.Closing)
            val result = conn.send(ConnectProto.GatewayMessageType.WORKER_PAUSE)
            logger.fine("WORKER_PAUSE send result: $result")
        }

        wake(WakeReason.SHUTDOWN_REQUESTED)
        awaitClosed()
        setState(ConnectionState.CLOSED, "close complete")
    }

    fun awaitClosed() {
        closedLatch.await()
    }

    fun awaitClosed(timeoutMillis: Long): Boolean = closedLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)

    // -----------------------------------------------------------------------
    // Reconcile loop
    // -----------------------------------------------------------------------

    private fun reconcileLoop() {
        var attempt = 0

        while (true) {
            if (shutdownRequested.get() && inFlightRequests.isEmpty()) break

            val current = active.get()
            if (current == null || current.phase.isNoWrite) {
                setState(
                    if (hasConnectedBefore) ConnectionState.RECONNECTING else ConnectionState.CONNECTING,
                    "no live connection",
                )

                try {
                    establishReplacement()
                    attempt = 0
                } catch (e: ConnectionLimitException) {
                    if (!hasConnectedBefore) {
                        failStartup(e)
                        break
                    }
                    logger.severe("connection limit reached; retrying with backoff")
                    attempt++
                    if (sleepBeforeRetry(backoffMillis(attempt - 1))) break
                    continue
                } catch (e: ConnectAuthException) {
                    if (!hasConnectedBefore) {
                        failStartup(e)
                        break
                    }
                    // No fallback signing key support yet (plan 006); keep
                    // retrying in case of transient server-side auth issues.
                    logger.severe("connect auth failed: ${e.message}; retrying with backoff")
                    attempt++
                    if (sleepBeforeRetry(backoffMillis(attempt - 1))) break
                    continue
                } catch (e: Exception) {
                    attempt++
                    val gatewayDraining = e.message?.contains("connect_gateway_closing") == true
                    val delay = if (gatewayDraining) drainingRetryMillis() else backoffMillis(attempt - 1)
                    logger.warning("connection attempt $attempt failed (${e.message}); retrying in ${delay}ms")
                    if (sleepBeforeRetry(delay)) break
                    continue
                }
            }

            // Flush buffered replies whenever there are any and we are able
            // to reach the API (the flush itself runs off-loop).
            if (messageBuffer.hasBufferedMessages()) {
                submitFlush()
            }

            // Park until something changes.
            val reason = wakeQueue.take()
            val extra = mutableListOf<WakeReason>()
            wakeQueue.drainTo(extra)
            logger.fine("reconcile loop woken: $reason${if (extra.isNotEmpty()) " (+$extra)" else ""}")
        }

        teardown()
    }

    private fun establishReplacement() {
        val conn =
            handshake.establish(
                excludeGateways = synchronized(excludeGateways) { excludeGateways.toList() },
                steadyStateHandler = messageHandler,
                onEnterNoWrite = Runnable { wake(WakeReason.BUFFERED_REPLIES_PENDING) },
            )

        // Old draining generation is superseded once the replacement is live.
        draining.getAndSet(null)?.let { old ->
            logger.info("closing drained connection ${old.id}, replaced by ${conn.id}")
            old.retire()
            old.closeNormal(ConnectProto.WorkerDisconnectReason.WORKER_SHUTDOWN.name)
        }

        active.set(conn)
        excludeGateways.remove(conn.gatewayGroup)
        heartbeatManager.attach(conn)
        hasConnectedBefore = true
        setState(ConnectionState.ACTIVE, "connection ${conn.id} active")

        if (shutdownRequested.get()) {
            // Reconnected mid-shutdown to keep in-flight leases alive: pause,
            // never signal readiness for new work.
            conn.lifecycle.transition(ConnectionPhase.Closing)
            conn.send(ConnectProto.GatewayMessageType.WORKER_PAUSE)
            logger.info("sent WORKER_PAUSE on reconnect during shutdown (${conn.id})")
        } else {
            val readyResult = conn.send(ConnectProto.GatewayMessageType.WORKER_READY)
            if (readyResult != WriteResult.SENT) {
                logger.warning("failed to send WORKER_READY on ${conn.id}: $readyResult")
            } else {
                logger.info("connection ${conn.id} ready (gateway group ${conn.gatewayGroup})")
            }
        }

        firstReady.complete(Unit)
    }

    private fun failStartup(e: Exception) {
        logger.severe("could not establish initial connection: ${e.message}")
        firstReady.completeExceptionally(e)
    }

    /**
     * Sleeps up to [delayMillis] but returns early when shutdown completes the
     * drain gate. Returns true when the loop should exit.
     */
    private fun sleepBeforeRetry(delayMillis: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis)
        while (true) {
            if (shutdownRequested.get() && inFlightRequests.isEmpty()) return true
            val remainingNanos = deadline - System.nanoTime()
            if (remainingNanos <= 0) return false
            wakeQueue.poll(minOf(TimeUnit.NANOSECONDS.toMillis(remainingNanos) + 1, 100L), TimeUnit.MILLISECONDS)
        }
    }

    private fun teardown() {
        logger.fine("reconcile loop exiting")
        heartbeatManager.stop()
        scheduler.shutdownNow()

        active.getAndSet(null)?.let { conn ->
            conn.closeNormal(ConnectProto.WorkerDisconnectReason.WORKER_SHUTDOWN.name)
        }
        draining.getAndSet(null)?.let { conn ->
            conn.closeNormal(ConnectProto.WorkerDisconnectReason.WORKER_SHUTDOWN.name)
        }

        // ACKs can no longer arrive and the deadline tasks are gone with the
        // scheduler: promote every pending reply and flush over HTTP. A
        // duplicate of an actually-delivered reply is deduplicated by the
        // gateway on request id.
        messageBuffer.expireAllPending()
        if (messageBuffer.hasBufferedMessages()) {
            messageBuffer.flush()
        }

        executor.shutdownNow()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()

        // Unblock a start() that never saw a successful connection.
        firstReady.completeExceptionally(ConnectApiException("connection closed before becoming ready"))

        closedLatch.countDown()
    }

    private val flushInProgress = AtomicBoolean(false)

    /** Run at most one HTTP flush at a time, off the supervisor thread. */
    private fun submitFlush() {
        if (!flushInProgress.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    // flush() retries internally with backoff; when it still
                    // fails, do NOT re-wake the loop — that would spin flush
                    // attempts forever. The next natural trigger (reconnect,
                    // newly buffered reply, shutdown) retries.
                    messageBuffer.flush()
                } finally {
                    flushInProgress.set(false)
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            flushInProgress.set(false)
        }
    }

    // -----------------------------------------------------------------------
    // Event handling
    // -----------------------------------------------------------------------

    private fun onConnectionDead(
        conn: GatewayConnection,
        reason: WakeReason,
    ) {
        // Record the exclusion and clear the active slot BEFORE retiring:
        // retire() fires the no-write callback, which wakes the reconcile
        // loop, and the loop must observe the exclusion when it snapshots
        // excludeGateways for the next start request (plan 010 hazard class:
        // publish state before waking observers).
        excludeGateways.add(conn.gatewayGroup)
        active.compareAndSet(conn, null)
        conn.retire()
        wake(reason)
    }

    private fun wake(reason: WakeReason) {
        wakeQueue.offer(reason)
    }

    private fun setState(
        to: ConnectionState,
        reason: String,
    ) {
        synchronized(stateLock) {
            val from = state
            if (from == to) return
            if (!from.canTransitionTo(to)) {
                logger.fine("ignoring invalid state transition $from -> $to ($reason)")
                return
            }
            state = to
            logger.fine("state $from -> $to ($reason)")
        }
    }
}
