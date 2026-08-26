package com.inngest.connect.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.inngest.FunctionContext
import com.inngest.Inngest
import com.inngest.InngestFunction
import com.inngest.InngestFunctionConfigBuilder
import com.inngest.Step
import com.inngest.connect.ConnectApp
import com.inngest.connect.ConnectOptions
import com.inngest.connect.ConnectionState
import com.inngest.connect.v1.ConnectProto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Timeout(30)
internal class ConnectionSupervisorTest {
    private val mapper = ObjectMapper()
    private var gateway: MockGateway? = null
    private var supervisor: ConnectionSupervisor? = null

    @AfterEach
    fun tearDown() {
        supervisor?.close()
        gateway?.close()
    }

    private class NoopFunction : InngestFunction() {
        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("noop-fn").triggerEvent("test/noop")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any = "ok"
    }

    private fun buildSupervisor(
        gateway: MockGateway,
        tolerance: Int = 3,
    ): ConnectionSupervisor {
        val options =
            ConnectOptions
                .builder(
                    listOf(
                        ConnectApp(
                            client = Inngest(appId = "test-app", isDev = true),
                            functions = listOf(NoopFunction()),
                        ),
                    ),
                ).apiBaseUrl(gateway.apiBaseUrl())
                .instanceId("test-instance")
                .maxWorkerConcurrency(5)
                .handleShutdownSignals(false)
                .missedHeartbeatTolerance(tolerance)
                .build()
        val config = ConnectConfig(options)
        val apiClient =
            ConnectApiClient(
                apiBaseUrl = config.apiBaseUrl,
                envName = config.envName,
                authHeaders = config.authHeaders,
            )
        // Near-zero backoff keeps reconnect tests fast.
        return ConnectionSupervisor(config, apiClient, backoffMillis = { 5L }, drainingRetryMillis = { 5L })
    }

    @Test
    fun `connects, syncs apps, and signals readiness`() {
        val gw = MockGateway().also { gateway = it }
        gw.expectConnection()

        val sup = buildSupervisor(gw).also { supervisor = it }
        sup.start()

        assertEquals(ConnectionState.ACTIVE, sup.currentState)

        val socket = gw.awaitSocket()
        val workerConnect = socket.expect(ConnectProto.GatewayMessageType.WORKER_CONNECT)
        val connectData = ConnectProto.WorkerConnectRequestData.parseFrom(workerConnect.payload)

        assertEquals("test-instance", connectData.instanceId)
        assertEquals("session-token", connectData.authData.sessionToken)
        assertEquals("sync-token", connectData.authData.syncToken)
        assertTrue(connectData.workerManualReadinessAck)
        assertEquals("connect", connectData.framework)
        assertEquals("java", connectData.sdkLanguage)
        assertEquals(5, connectData.maxWorkerConcurrency)
        assertEquals("""{"connect":"v1"}""", connectData.capabilities.toStringUtf8())

        val app = connectData.appsList.single()
        assertEquals("test-app", app.appName)
        val functions = mapper.readTree(app.functions.toStringUtf8())
        assertEquals("test-app-noop-fn", functions[0]["id"].asText())
        assertEquals("ws", functions[0]["steps"]["step"]["runtime"]["type"].asText())

        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)
        assertEquals(sup.connectionId, connectData.connectionId)
    }

    @Test
    fun `close sends pause and closes the socket gracefully`() {
        val gw = MockGateway().also { gateway = it }
        gw.expectConnection()

        val sup = buildSupervisor(gw).also { supervisor = it }
        sup.start()
        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        sup.close()

        socket.expect(ConnectProto.GatewayMessageType.WORKER_PAUSE)
        val (code, _) =
            socket.closeEvents.poll(5, java.util.concurrent.TimeUnit.SECONDS)
                ?: throw AssertionError("socket was not closed")
        assertEquals(1000, code)
        assertEquals(ConnectionState.CLOSED, sup.currentState)
        assertTrue(sup.awaitClosed(1_000))
    }

    @Test
    fun `close is idempotent from multiple threads`() {
        val gw = MockGateway().also { gateway = it }
        gw.expectConnection()

        val sup = buildSupervisor(gw).also { supervisor = it }
        sup.start()

        val threads = (1..4).map { Thread { sup.close() } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(ConnectionState.CLOSED, sup.currentState)
    }

    @Test
    fun `reconnects when the gateway drops the socket and excludes the failed gateway`() {
        val gw = MockGateway().also { gateway = it }
        gw.expectConnection()
        gw.expectConnection()

        val sup = buildSupervisor(gw).also { supervisor = it }
        sup.start()

        val first = gw.awaitSocket()
        first.expect(ConnectProto.GatewayMessageType.WORKER_READY)
        val firstConnectionId = sup.connectionId

        // Gateway kills the socket without GATEWAY_CLOSING.
        first.socket.close(1011, "gateway crash")

        val second = gw.awaitSocket(10)
        second.expect(ConnectProto.GatewayMessageType.WORKER_READY, 10)
        assertEquals(ConnectionState.ACTIVE, sup.currentState)
        assertNotEquals(firstConnectionId, sup.connectionId)

        // The reconnect's start request must exclude the failed gateway group.
        gw.startRequests.poll() // initial
        val retryStart = gw.startRequests.poll(5, java.util.concurrent.TimeUnit.SECONDS)!!
        val startRequest = ConnectProto.StartRequest.parseFrom(retryStart.body.readByteArray())
        assertTrue(startRequest.excludeGatewaysList.contains("gw-mock"))
    }

    @Test
    fun `gateway drain establishes a replacement and closes the old socket`() {
        val gw = MockGateway().also { gateway = it }
        gw.expectConnection()
        gw.expectConnection()

        val sup = buildSupervisor(gw).also { supervisor = it }
        sup.start()

        val first = gw.awaitSocket()
        first.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        first.send(ConnectProto.GatewayMessageType.GATEWAY_CLOSING)

        val second = gw.awaitSocket(10)
        second.expect(ConnectProto.GatewayMessageType.WORKER_READY, 10)
        assertEquals(ConnectionState.ACTIVE, sup.currentState)

        // The drained socket is eventually closed by the worker.
        val closeEvent = first.closeEvents.poll(5, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(closeEvent != null, "old drained socket should be closed after replacement")
    }

    @Test
    fun `missed gateway heartbeats retire the connection and trigger a reconnect`() {
        // 50ms heartbeat interval, tolerance 1 => dead after ~100ms of silence.
        val gw = MockGateway(heartbeatInterval = "50ms").also { gateway = it }
        gw.expectConnection()
        gw.expectConnection()

        val sup = buildSupervisor(gw, tolerance = 1).also { supervisor = it }
        sup.start()

        val first = gw.awaitSocket()
        first.expect(ConnectProto.GatewayMessageType.WORKER_READY)
        // Never answer worker heartbeats: the watchdog must fire.

        val second = gw.awaitSocket(10)
        second.expect(ConnectProto.GatewayMessageType.WORKER_READY, 10)
        assertEquals(ConnectionState.ACTIVE, sup.currentState)
        assertTrue(gw.startRequests.size >= 2)
    }

    @Test
    fun `gateway heartbeats keep the connection alive`() {
        val gw = MockGateway(heartbeatInterval = "100ms").also { gateway = it }
        gw.expectConnection()

        val sup = buildSupervisor(gw, tolerance = 1).also { supervisor = it }
        sup.start()

        val socket = gw.awaitSocket()
        socket.expect(ConnectProto.GatewayMessageType.WORKER_READY)

        // Answer every worker heartbeat for ~6 intervals.
        val deadline = System.currentTimeMillis() + 600
        while (System.currentTimeMillis() < deadline) {
            val message = socket.received.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (message?.kind == ConnectProto.GatewayMessageType.WORKER_HEARTBEAT) {
                socket.send(ConnectProto.GatewayMessageType.GATEWAY_HEARTBEAT)
            }
        }

        assertEquals(ConnectionState.ACTIVE, sup.currentState)
        assertEquals(1, gw.startRequests.size, "no reconnect should have happened")
    }
}
