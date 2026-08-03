package com.inngest.connect.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.ByteString
import com.inngest.FunctionContext
import com.inngest.Inngest
import com.inngest.InngestFunction
import com.inngest.InngestFunctionConfigBuilder
import com.inngest.NonRetriableError
import com.inngest.Step
import com.inngest.connect.ConnectApp
import com.inngest.connect.ConnectOptions
import com.inngest.connect.v1.ConnectProto
import com.inngest.testing.ProtocolFixtures
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end request processing over the mock gateway: executor request ->
 * ACK -> execution through CommHandler -> reply -> reply ACK / buffer + flush.
 */
@Timeout(30)
internal class RequestProcessingTest {
    private val mapper = ObjectMapper()
    private var gateway: MockGateway? = null
    private var supervisor: ConnectionSupervisor? = null

    companion object {
        val executions = AtomicInteger()
    }

    @AfterEach
    fun tearDown() {
        supervisor?.close()
        gateway?.close()
        executions.set(0)
    }

    private class EchoFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("echo-fn").triggerEvent("test/echo")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any {
            executions.incrementAndGet()
            return "echo-done"
        }
    }

    private class SlowFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("slow-fn").triggerEvent("test/slow")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any {
            Thread.sleep(400)
            return "slow-done"
        }
    }

    private class FailingFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("failing-fn").triggerEvent("test/fail")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any = throw NonRetriableError("hard failure")
    }

    private fun startSupervisor(
        gw: MockGateway,
        replyAckDeadlineMillis: Long = 5_000,
        vararg functions: InngestFunction,
    ): ConnectionSupervisor {
        val options =
            ConnectOptions
                .builder(
                    listOf(
                        ConnectApp(
                            client = Inngest(appId = "test-app", isDev = true),
                            functions = functions.toList(),
                        ),
                    ),
                ).apiBaseUrl(gw.apiBaseUrl())
                .instanceId("test-instance")
                .handleShutdownSignals(false)
                .build()
        val config = ConnectConfig(options)
        val apiClient =
            ConnectApiClient(
                apiBaseUrl = config.apiBaseUrl,
                envName = config.envName,
                authHeaders = config.authHeaders,
            )
        val sup =
            ConnectionSupervisor(
                config,
                apiClient,
                backoffMillis = { 5L },
                drainingRetryMillis = { 5L },
                replyAckDeadlineMillis = replyAckDeadlineMillis,
            )
        supervisor = sup
        sup.start()
        return sup
    }

    private fun executorRequest(
        functionSlug: String,
        requestId: String = "req-1",
        stepId: String? = "step",
    ): ConnectProto.GatewayExecutorRequestData {
        val builder =
            ConnectProto.GatewayExecutorRequestData
                .newBuilder()
                .setRequestId(requestId)
                .setAccountId("acct-1")
                .setEnvId("env-1")
                .setAppId("app-uuid")
                .setAppName("test-app")
                .setFunctionId("fn-uuid")
                .setFunctionSlug(functionSlug)
                .setRequestPayload(
                    ByteString.copyFromUtf8(
                        ProtocolFixtures.executionRequestPayloadJson(functionSlug),
                    ),
                ).setRunId("run-1")
                .setLeaseId("lease-1")
                .setJobId("job-1")
        if (stepId != null) builder.setStepId(stepId)
        return builder.build()
    }

    @Test
    fun `executes a function and replies DONE with the function output`() {
        val gw = MockGateway().also { gateway = it }
        gw.expectConnection()
        startSupervisor(gw, functions = arrayOf(EchoFunction()))

        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        socket.send(
            ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST,
            executorRequest("test-app-echo-fn").toByteString(),
        )

        val ack = socket.expect(ConnectProto.GatewayMessageType.WORKER_REQUEST_ACK)
        val ackData = ConnectProto.WorkerRequestAckData.parseFrom(ack.payload)
        assertEquals("req-1", ackData.requestId)
        assertEquals("test-app-echo-fn", ackData.functionSlug)
        assertEquals("run-1", ackData.runId)

        val reply = socket.expect(ConnectProto.GatewayMessageType.WORKER_REPLY)
        val sdkResponse = ConnectProto.SDKResponse.parseFrom(reply.payload)
        assertEquals(ConnectProto.SDKResponseStatus.DONE, sdkResponse.status)
        assertEquals("req-1", sdkResponse.requestId)
        assertEquals("run-1", sdkResponse.runId)
        assertEquals("echo-done", mapper.readValue(sdkResponse.body.toStringUtf8(), String::class.java))
        assertFalse(sdkResponse.noRetry)
        assertEquals(2, sdkResponse.requestVersion)
        assertTrue(sdkResponse.sdkVersion.startsWith("inngest-kt:v"))
        assertEquals(1, executions.get())

        // Acknowledge so nothing is buffered; close must not flush anything.
        socket.send(
            ConnectProto.GatewayMessageType.WORKER_REPLY_ACK,
            ConnectProto.WorkerReplyAckData
                .newBuilder()
                .setRequestId("req-1")
                .build()
                .toByteString(),
        )
        supervisor!!.close()
        assertNull(gw.flushRequests.poll(200, TimeUnit.MILLISECONDS), "no flush expected after reply ACK")
    }

    @Test
    fun `nonretriable function error maps to ERROR with no_retry`() {
        val gw = MockGateway().also { gateway = it }
        gw.expectConnection()
        startSupervisor(gw, functions = arrayOf(FailingFunction()))

        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        socket.send(
            ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST,
            executorRequest("test-app-failing-fn").toByteString(),
        )

        val reply = socket.expect(ConnectProto.GatewayMessageType.WORKER_REPLY)
        val sdkResponse = ConnectProto.SDKResponse.parseFrom(reply.payload)
        assertEquals(ConnectProto.SDKResponseStatus.ERROR, sdkResponse.status)
        assertTrue(sdkResponse.noRetry)
        assertTrue(sdkResponse.body.toStringUtf8().contains("hard failure"))
    }

    @Test
    fun `unknown app is skipped without an ack`() {
        val gw = MockGateway().also { gateway = it }
        gw.expectConnection()
        startSupervisor(gw, functions = arrayOf(EchoFunction()))

        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        val foreignRequest =
            executorRequest("other-app-fn")
                .toBuilder()
                .setAppName("other-app")
                .build()
        socket.send(ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST, foreignRequest.toByteString())

        // No ACK, no reply, no execution.
        val unexpected = socket.received.poll(500, TimeUnit.MILLISECONDS)
        assertNull(unexpected, "expected no worker response, got ${unexpected?.kind}")
        assertEquals(0, executions.get())
    }

    @Test
    fun `unacked reply is buffered and flushed over http`() {
        val gw = MockGateway().also { gateway = it }
        gw.expectConnection()
        // Short ACK deadline; the mock gateway never sends WORKER_REPLY_ACK.
        startSupervisor(gw, replyAckDeadlineMillis = 100, functions = arrayOf(EchoFunction()))

        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        socket.send(
            ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST,
            executorRequest("test-app-echo-fn").toByteString(),
        )
        socket.expect(ConnectProto.GatewayMessageType.WORKER_REPLY)

        // Deadline expires -> buffered; close() flushes over HTTP.
        Thread.sleep(250)
        supervisor!!.close()

        val flushRequest = gw.flushRequests.poll(5, TimeUnit.SECONDS)
        assertTrue(flushRequest != null, "expected an HTTP flush of the unacked reply")
        val flushed = ConnectProto.SDKResponse.parseFrom(flushRequest.body.readByteArray())
        assertEquals("req-1", flushed.requestId)
        assertEquals(ConnectProto.SDKResponseStatus.DONE, flushed.status)
    }

    @Test
    fun `slow request extends its lease and rotates the lease id`() {
        val gw = MockGateway(extendLeaseInterval = "50ms").also { gateway = it }
        gw.expectConnection()
        startSupervisor(gw, functions = arrayOf(SlowFunction()))

        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        socket.send(
            ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST,
            executorRequest("test-app-slow-fn").toByteString(),
        )

        val firstExtend = socket.expect(ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE)
        val firstData = ConnectProto.WorkerRequestExtendLeaseData.parseFrom(firstExtend.payload)
        assertEquals("lease-1", firstData.leaseId)

        // Rotate the lease; subsequent extensions must carry the new id.
        socket.send(
            ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE_ACK,
            ConnectProto.WorkerRequestExtendLeaseAckData
                .newBuilder()
                .setRequestId("req-1")
                .setNewLeaseId("lease-2")
                .build()
                .toByteString(),
        )

        val deadline = System.currentTimeMillis() + 2_000
        var sawRotated = false
        while (System.currentTimeMillis() < deadline && !sawRotated) {
            val message = socket.received.poll(100, TimeUnit.MILLISECONDS) ?: continue
            if (message.kind == ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE) {
                val data = ConnectProto.WorkerRequestExtendLeaseData.parseFrom(message.payload)
                if (data.leaseId == "lease-2") sawRotated = true
            }
        }
        assertTrue(sawRotated, "expected an extension carrying the rotated lease id")

        socket.expect(ConnectProto.GatewayMessageType.WORKER_REPLY)
    }

    @Test
    fun `lost lease stops extensions but the reply is still delivered`() {
        val gw = MockGateway(extendLeaseInterval = "50ms").also { gateway = it }
        gw.expectConnection()
        startSupervisor(gw, functions = arrayOf(SlowFunction()))

        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        socket.send(
            ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST,
            executorRequest("test-app-slow-fn").toByteString(),
        )
        socket.expect(ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE)

        // Lease lost: no new_lease_id.
        socket.send(
            ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE_ACK,
            ConnectProto.WorkerRequestExtendLeaseAckData
                .newBuilder()
                .setRequestId("req-1")
                .build()
                .toByteString(),
        )

        val reply = socket.expect(ConnectProto.GatewayMessageType.WORKER_REPLY, 10)
        assertEquals(
            ConnectProto.SDKResponseStatus.DONE,
            ConnectProto.SDKResponse.parseFrom(reply.payload).status,
        )
    }

    @Test
    fun `graceful shutdown drains an in-flight request before closing`() {
        val gw = MockGateway().also { gateway = it }
        gw.expectConnection()
        startSupervisor(gw, functions = arrayOf(SlowFunction()))

        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        socket.send(
            ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST,
            executorRequest("test-app-slow-fn").toByteString(),
        )
        socket.expect(ConnectProto.GatewayMessageType.WORKER_REQUEST_ACK)

        // Close while the slow function is still executing.
        val closer = Thread { supervisor!!.close() }
        closer.start()

        socket.expect(ConnectProto.GatewayMessageType.WORKER_PAUSE, 10)
        val reply = socket.expect(ConnectProto.GatewayMessageType.WORKER_REPLY, 10)
        assertEquals(
            ConnectProto.SDKResponseStatus.DONE,
            ConnectProto.SDKResponse.parseFrom(reply.payload).status,
        )
        closer.join(10_000)
        assertFalse(closer.isAlive, "close() should return after the drain")
    }

    @Test
    fun `requests arriving during shutdown are skipped without execution`() {
        val gw = MockGateway().also { gateway = it }
        gw.expectConnection()
        startSupervisor(gw, functions = arrayOf(SlowFunction(), EchoFunction()))

        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        // Occupy the worker with a slow request so close() has to drain.
        socket.send(
            ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST,
            executorRequest("test-app-slow-fn", requestId = "req-slow").toByteString(),
        )
        socket.expect(ConnectProto.GatewayMessageType.WORKER_REQUEST_ACK)

        val closer = Thread { supervisor!!.close() }
        closer.start()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_PAUSE, 10)

        // A request routed after the pause: the Closing phase denies its ACK,
        // so it must be skipped and never executed.
        socket.send(
            ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST,
            executorRequest("test-app-echo-fn", requestId = "req-late").toByteString(),
        )

        closer.join(15_000)
        assertFalse(closer.isAlive)
        assertEquals(0, executions.get(), "late request must not execute")
    }
}
