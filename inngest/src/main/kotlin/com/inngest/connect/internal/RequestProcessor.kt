package com.inngest.connect.internal

import com.inngest.CommHandler
import com.inngest.CommResponse
import com.inngest.InngestHeaderKey
import com.inngest.ResultStatusCode
import com.inngest.connect.v1.ConnectProto
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Executes gateway executor requests through the shared engine
 * ([CommHandler.callFunction]) and manages the request-side protocol: ACK,
 * lease extension, reply delivery, and reply-ACK bookkeeping.
 *
 * Ownership contract (plan 010, hazard 1): a successful WORKER_REQUEST_ACK
 * write is the ownership boundary. Any failure before it skips the request —
 * the gateway reroutes it — and the function is never invoked. After it, the
 * function always runs to completion and its result is always delivered,
 * over the socket or the HTTP flush path.
 */
internal class RequestProcessor(
    private val commHandlersByApp: Map<String, CommHandler>,
    private val executor: ExecutorService,
    private val scheduler: ScheduledExecutorService,
    private val inFlight: InFlightRequests,
    private val buffer: MessageBuffer,
    private val hooks: Hooks,
    private val sdkResponseVersion: String,
    private val replyAckDeadlineMillis: Long = MessageBuffer.DEFAULT_REPLY_ACK_DEADLINE_MILLIS,
) {
    internal interface Hooks {
        /** The current live generation, used when the owning one can no longer write. */
        fun activeConnection(): GatewayConnection?

        fun shutdownRequested(): Boolean

        fun wake(reason: WakeReason)
    }

    companion object {
        private val logger = Logger.getLogger("com.inngest.connect")
    }

    private val leaseLock = Any()
    private val leases = HashMap<String, String>()
    private val extendTasks = HashMap<String, ScheduledFuture<*>>()

    /**
     * Entry point from the socket reader thread: parse, validate, and enqueue.
     * In-flight counting starts here — before the ACK — so the shutdown drain
     * gate covers queued work (plan 010, hazard 9).
     */
    fun handleExecutorRequest(
        conn: GatewayConnection,
        message: ConnectProto.ConnectMessage,
    ) {
        val request: ConnectProto.GatewayExecutorRequestData
        try {
            request = ConnectProto.GatewayExecutorRequestData.parseFrom(message.payload)
        } catch (e: Exception) {
            logger.warning("dropping unparseable executor request on connection ${conn.id}: $e")
            return
        }

        if (request.appName.isEmpty() || !commHandlersByApp.containsKey(request.appName)) {
            logger.warning(
                "no handler for app '${request.appName}' " +
                    "(request ${request.requestId}, function ${request.functionSlug}); skipping",
            )
            return
        }

        inFlight.increment()
        try {
            executor.execute {
                try {
                    process(conn, request)
                } catch (t: Throwable) {
                    logger.log(Level.SEVERE, "unexpected error processing request ${request.requestId}", t)
                } finally {
                    finishRequest(request.requestId)
                }
            }
        } catch (e: RejectedExecutionException) {
            // Executor is shutting down; we never ACKed, so the gateway reroutes.
            inFlight.decrement()
            logger.warning("executor pool rejected request ${request.requestId}; skipping (never ACKed)")
        }
    }

    fun handleReplyAck(message: ConnectProto.ConnectMessage) {
        val ack: ConnectProto.WorkerReplyAckData
        try {
            ack = ConnectProto.WorkerReplyAckData.parseFrom(message.payload)
        } catch (e: Exception) {
            logger.warning("dropping unparseable reply ack: $e")
            return
        }
        logger.fine("reply acknowledged for request ${ack.requestId}")
        buffer.acknowledge(ack.requestId)
    }

    fun handleExtendLeaseAck(message: ConnectProto.ConnectMessage) {
        val ack: ConnectProto.WorkerRequestExtendLeaseAckData
        try {
            ack = ConnectProto.WorkerRequestExtendLeaseAckData.parseFrom(message.payload)
        } catch (e: Exception) {
            logger.warning("dropping unparseable extend-lease ack: $e")
            return
        }

        synchronized(leaseLock) {
            // A late ACK for a request that already finished (or lost its
            // lease) must be ignored, not re-inserted (plan 010, hazard 3).
            if (!leases.containsKey(ack.requestId)) {
                logger.fine("ignoring extend-lease ack for non-in-flight request ${ack.requestId}")
                return
            }
            if (ack.hasNewLeaseId()) {
                leases[ack.requestId] = ack.newLeaseId
            } else {
                logger.severe(
                    "lease lost for request ${ack.requestId}: the server did not renew it; " +
                        "another worker may have claimed the request. Execution continues " +
                        "but its result may be discarded.",
                )
                leases.remove(ack.requestId)
                extendTasks.remove(ack.requestId)?.cancel(false)
            }
        }
    }

    // -------------------------------------------------------------------
    // Worker-pool execution path
    // -------------------------------------------------------------------

    private fun process(
        owning: GatewayConnection,
        request: ConnectProto.GatewayExecutorRequestData,
    ) {
        // ACK: the ownership boundary. The phase check is a fast path; the
        // write result is the authority.
        when (owning.send(ConnectProto.GatewayMessageType.WORKER_REQUEST_ACK, buildAck(request).toByteString())) {
            WriteResult.DENIED_BY_PHASE -> {
                logger.fine(
                    "skipping request ${request.requestId}: phase ${owning.phase} does not allow ACK",
                )
                return
            }

            WriteResult.FAILED -> {
                // The socket is broken: retire the generation so queued work
                // stops attempting stale ACKs (Go: request_lifecycle.go).
                logger.warning("ACK write failed for request ${request.requestId}; retiring connection ${owning.id}")
                owning.retire()
                hooks.wake(WakeReason.WS_ERROR)
                return
            }

            WriteResult.SENT -> {}
        }

        synchronized(leaseLock) { leases[request.requestId] = request.leaseId }
        scheduleLeaseExtension(owning, request)

        logger.fine(
            "executing request ${request.requestId} (function ${request.functionSlug}, " +
                "step ${if (request.hasStepId()) request.stepId else "<discovery>"})",
        )

        val response = execute(request)

        buffer.addPending(response, replyAckDeadlineMillis)
        sendReply(owning, response)
    }

    private fun execute(request: ConnectProto.GatewayExecutorRequestData): ConnectProto.SDKResponse {
        val handler = commHandlersByApp.getValue(request.appName)
        val commResponse: CommResponse =
            try {
                handler.callFunction(
                    functionId = request.functionSlug,
                    requestBody = request.requestPayload.toStringUtf8(),
                    stepId = if (request.hasStepId() && request.stepId.isNotEmpty()) request.stepId else null,
                )
            } catch (t: Throwable) {
                // callFunction handles function errors itself; reaching this
                // catch means a protocol-level failure (e.g. malformed body).
                logger.log(Level.WARNING, "protocol error executing request ${request.requestId}", t)
                handler.protocolErrorResponse(t)
            }

        return buildSdkResponse(request, commResponse)
    }

    private fun buildSdkResponse(
        request: ConnectProto.GatewayExecutorRequestData,
        commResponse: CommResponse,
    ): ConnectProto.SDKResponse {
        val status =
            when (commResponse.statusCode) {
                ResultStatusCode.FunctionComplete -> ConnectProto.SDKResponseStatus.DONE

                ResultStatusCode.StepComplete,
                ResultStatusCode.StepError,
                -> ConnectProto.SDKResponseStatus.NOT_COMPLETED

                // RetriableError, NonRetriableError, and anything else are
                // errors; retriability travels in no_retry/retry_after. (The
                // JS SDK maps unknown statuses to DONE — deliberate divergence,
                // see plan 010.)
                else -> ConnectProto.SDKResponseStatus.ERROR
            }

        val builder =
            ConnectProto.SDKResponse
                .newBuilder()
                .setRequestId(request.requestId)
                .setAccountId(request.accountId)
                .setEnvId(request.envId)
                .setAppId(request.appId)
                .setStatus(status)
                .setBody(
                    com.google.protobuf.ByteString
                        .copyFromUtf8(commResponse.body),
                ).setNoRetry(commResponse.headers[InngestHeaderKey.NoRetry.value] == "true")
                .setSdkVersion(sdkResponseVersion)
                .setRequestVersion(
                    commResponse.headers[InngestHeaderKey.RequestVersion.value]?.toIntOrNull() ?: 2,
                ).setSystemTraceCtx(request.systemTraceCtx)
                .setUserTraceCtx(request.userTraceCtx)
                .setRunId(request.runId)

        commResponse.headers[InngestHeaderKey.RetryAfter.value]?.let { builder.setRetryAfter(it) }

        return builder.build()
    }

    /**
     * Reply delivery: prefer the generation that owns the request (Draining
     * and Closing still permit replies), fall back to the current active
     * generation, and buffer for HTTP flush when neither can write. Never
     * discard a result (plan 010, hazard 2).
     */
    private fun sendReply(
        owning: GatewayConnection,
        response: ConnectProto.SDKResponse,
    ) {
        val payload = response.toByteString()

        var result = owning.send(ConnectProto.GatewayMessageType.WORKER_REPLY, payload)
        if (result != WriteResult.SENT) {
            val active = hooks.activeConnection()
            if (active != null && active !== owning) {
                result = active.send(ConnectProto.GatewayMessageType.WORKER_REPLY, payload)
            }
        }

        if (result != WriteResult.SENT) {
            logger.info("no writable connection for reply ${response.requestId}; buffering for HTTP flush")
            buffer.append(response)
            hooks.wake(WakeReason.BUFFERED_REPLIES_PENDING)
        }
    }

    private fun scheduleLeaseExtension(
        owning: GatewayConnection,
        request: ConnectProto.GatewayExecutorRequestData,
    ) {
        val interval = owning.extendLeaseIntervalMillis
        val task =
            scheduler.scheduleAtFixedRate(
                { extendLease(owning, request) },
                interval,
                interval,
                TimeUnit.MILLISECONDS,
            )
        synchronized(leaseLock) {
            // The request may already have finished (fast function): cancel
            // immediately instead of leaking the timer.
            if (leases.containsKey(request.requestId)) {
                extendTasks[request.requestId] = task
            } else {
                task.cancel(false)
            }
        }
    }

    private fun extendLease(
        owning: GatewayConnection,
        request: ConnectProto.GatewayExecutorRequestData,
    ) {
        // Read the current lease id at fire time, under the same lock that
        // rotation uses (plan 010, hazard 4).
        val leaseId = synchronized(leaseLock) { leases[request.requestId] } ?: return

        val payload =
            ConnectProto.WorkerRequestExtendLeaseData
                .newBuilder()
                .setRequestId(request.requestId)
                .setAccountId(request.accountId)
                .setEnvId(request.envId)
                .setAppId(request.appId)
                .setFunctionSlug(request.functionSlug)
                .apply { if (request.hasStepId()) stepId = request.stepId }
                .setSystemTraceCtx(request.systemTraceCtx)
                .setUserTraceCtx(request.userTraceCtx)
                .setRunId(request.runId)
                .setLeaseId(leaseId)
                .build()
                .toByteString()

        var result = owning.send(ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE, payload)
        if (result != WriteResult.SENT) {
            // The owning generation retired (e.g. drain completed): keep the
            // lease alive on the current active socket (JS behavior) so
            // long-running work is not rerouted mid-execution.
            val active = hooks.activeConnection()
            if (active != null && active !== owning) {
                result = active.send(ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE, payload)
            }
        }
        if (result != WriteResult.SENT) {
            logger.fine("could not extend lease for request ${request.requestId} (no writable connection)")
        }
    }

    private fun finishRequest(requestId: String) {
        synchronized(leaseLock) {
            leases.remove(requestId)
            extendTasks.remove(requestId)?.cancel(false)
        }
        inFlight.decrement()
        if (hooks.shutdownRequested() && inFlight.isEmpty()) {
            hooks.wake(WakeReason.REQUEST_FINISHED_ON_SHUTDOWN)
        }
    }

    private fun buildAck(request: ConnectProto.GatewayExecutorRequestData): ConnectProto.WorkerRequestAckData =
        ConnectProto.WorkerRequestAckData
            .newBuilder()
            .setRequestId(request.requestId)
            .setAccountId(request.accountId)
            .setEnvId(request.envId)
            .setAppId(request.appId)
            .setFunctionSlug(request.functionSlug)
            .apply { if (request.hasStepId()) stepId = request.stepId }
            .setSystemTraceCtx(request.systemTraceCtx)
            .setUserTraceCtx(request.userTraceCtx)
            .setRunId(request.runId)
            .build()
}
