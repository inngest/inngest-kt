package com.inngest.connect.internal

import com.google.protobuf.ByteString
import com.inngest.FunctionContext
import com.inngest.Inngest
import com.inngest.InngestFunction
import com.inngest.InngestFunctionConfigBuilder
import com.inngest.Step
import com.inngest.connect.ConnectApp
import com.inngest.connect.ConnectOptions
import com.inngest.connect.ConnectionState
import com.inngest.connect.v1.ConnectProto
import com.inngest.testing.ProtocolFixtures
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Timeout(30)
internal class StatusAndDebugStateTest {
    private var gateway: MockGateway? = null
    private var supervisor: ConnectionSupervisor? = null

    /**
     * Await an internal-state condition instead of asserting it instantly.
     * Gateway-side observations (e.g. seeing the ACK) do not imply the
     * worker thread has finished its post-write bookkeeping yet.
     */
    private fun awaitTrue(
        timeoutMillis: Long = 5_000,
        message: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue(condition(), message)
    }

    @AfterEach
    fun tearDown() {
        supervisor?.close()
        gateway?.close()
    }

    private class SlowFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("slow-fn").triggerEvent("test/slow")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any {
            Thread.sleep(500)
            return "slow-done"
        }
    }

    private fun startSupervisor(gw: MockGateway): ConnectionSupervisor {
        val options =
            ConnectOptions
                .builder(
                    listOf(
                        ConnectApp(
                            client = Inngest(appId = "test-app", isDev = true),
                            functions = listOf(SlowFunction()),
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
        val sup = ConnectionSupervisor(config, apiClient, backoffMillis = { 5L }, drainingRetryMillis = { 5L })
        supervisor = sup
        sup.start()
        return sup
    }

    private fun executorRequest(requestId: String): ConnectProto.GatewayExecutorRequestData =
        ConnectProto.GatewayExecutorRequestData
            .newBuilder()
            .setRequestId(requestId)
            .setAccountId("acct-1")
            .setEnvId("env-1")
            .setAppId("app-uuid")
            .setAppName("test-app")
            .setFunctionId("fn-uuid")
            .setFunctionSlug("test-app-slow-fn")
            .setStepId("step")
            .setRequestPayload(
                ByteString.copyFromUtf8(ProtocolFixtures.executionRequestPayloadJson("test-app-slow-fn")),
            ).setRunId("run-1")
            .setLeaseId("lease-1")
            .setJobId("job-1")
            .build()

    @Test
    fun `reports worker status with in-flight request ids when enabled`() {
        val gw = MockGateway(statusInterval = "50ms").also { gateway = it }
        gw.expectConnection()
        startSupervisor(gw)

        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        // Idle status first: no in-flight ids, no shutdown.
        val idleStatus =
            ConnectProto.WorkerStatusData.parseFrom(
                socket.expect(ConnectProto.GatewayMessageType.WORKER_STATUS).payload,
            )
        assertEquals(0, idleStatus.inFlightRequestIdsCount)
        assertFalse(idleStatus.shutdownRequested)

        // While a slow request executes, some status report must carry its id.
        socket.send(
            ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST,
            executorRequest("req-status").toByteString(),
        )
        socket.expect(ConnectProto.GatewayMessageType.WORKER_REQUEST_ACK)

        val deadline = System.currentTimeMillis() + 5_000
        var sawInFlight = false
        while (System.currentTimeMillis() < deadline && !sawInFlight) {
            val message = socket.received.poll(200, TimeUnit.MILLISECONDS) ?: continue
            if (message.kind == ConnectProto.GatewayMessageType.WORKER_STATUS) {
                val status = ConnectProto.WorkerStatusData.parseFrom(message.payload)
                if (status.inFlightRequestIdsList.contains("req-status")) sawInFlight = true
            }
        }
        assertTrue(sawInFlight, "expected a WORKER_STATUS report carrying the in-flight request id")
    }

    @Test
    fun `status reporting stays disabled when the gateway sends no interval`() {
        val gw = MockGateway().also { gateway = it } // statusInterval defaults to ""
        gw.expectConnection()
        startSupervisor(gw)

        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        val deadline = System.currentTimeMillis() + 500
        while (System.currentTimeMillis() < deadline) {
            val message = socket.received.poll(100, TimeUnit.MILLISECONDS) ?: continue
            assertTrue(
                message.kind != ConnectProto.GatewayMessageType.WORKER_STATUS,
                "unexpected WORKER_STATUS when reporting is disabled",
            )
        }
    }

    @Test
    fun `debug state reflects active connection and in-flight work`() {
        val gw = MockGateway().also { gateway = it }
        gw.expectConnection()
        val sup = startSupervisor(gw)

        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        val idle = sup.debugState()
        assertEquals(ConnectionState.ACTIVE, idle.state)
        assertEquals(sup.connectionId, idle.activeConnectionId)
        assertNull(idle.drainingConnectionId)
        assertFalse(idle.shutdownRequested)
        assertEquals(0, idle.inFlightRequestCount)
        assertNull(idle.millisSinceLastGatewayHeartbeat)

        socket.send(
            ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST,
            executorRequest("req-debug").toByteString(),
        )
        socket.expect(ConnectProto.GatewayMessageType.WORKER_REQUEST_ACK)

        // The worker registers in-flight metadata after the ACK write, so
        // seeing the ACK does not mean the registration is visible yet.
        assertEquals(1, sup.debugState().inFlightRequestCount)
        awaitTrue(message = "expected req-debug in inFlightRequestIds") {
            sup.debugState().inFlightRequestIds.contains("req-debug")
        }

        // Heartbeat receipt is visible in the snapshot.
        socket.send(ConnectProto.GatewayMessageType.GATEWAY_HEARTBEAT)
        awaitTrue(message = "expected millisSinceLastGatewayHeartbeat after a gateway heartbeat") {
            sup.debugState().millisSinceLastGatewayHeartbeat != null
        }

        socket.expect(ConnectProto.GatewayMessageType.WORKER_REPLY, 10)
    }
}
