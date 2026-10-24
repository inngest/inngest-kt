package com.inngest.connect

import com.google.protobuf.ByteString
import com.inngest.connect.v1.ConnectProto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the vendored connect protocol: enum wire numbers must match the
 * canonical proto in inngest/inngest, and envelope/payload round-trips must be
 * lossless. If these tests fail after regenerating from a newer proto, the
 * gateway contract changed and the connect implementation must be reviewed.
 */
class ConnectProtoCodecTest {
    @Test
    fun `gateway message type enum numbers match the canonical proto`() {
        val expected =
            mapOf(
                ConnectProto.GatewayMessageType.GATEWAY_HELLO to 0,
                ConnectProto.GatewayMessageType.WORKER_CONNECT to 1,
                ConnectProto.GatewayMessageType.GATEWAY_CONNECTION_READY to 2,
                ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST to 3,
                ConnectProto.GatewayMessageType.WORKER_READY to 4,
                ConnectProto.GatewayMessageType.WORKER_REQUEST_ACK to 5,
                ConnectProto.GatewayMessageType.WORKER_REPLY to 6,
                ConnectProto.GatewayMessageType.WORKER_REPLY_ACK to 7,
                ConnectProto.GatewayMessageType.WORKER_PAUSE to 8,
                ConnectProto.GatewayMessageType.WORKER_HEARTBEAT to 9,
                ConnectProto.GatewayMessageType.GATEWAY_HEARTBEAT to 10,
                ConnectProto.GatewayMessageType.GATEWAY_CLOSING to 11,
                ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE to 12,
                ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE_ACK to 13,
                ConnectProto.GatewayMessageType.SYNC_FAILED to 14,
                ConnectProto.GatewayMessageType.WORKER_STATUS to 15,
            )
        expected.forEach { (type, number) ->
            assertEquals(number, type.number, "wire number changed for $type")
        }
    }

    @Test
    fun `sdk response status enum numbers match the canonical proto`() {
        assertEquals(0, ConnectProto.SDKResponseStatus.NOT_COMPLETED.number)
        assertEquals(1, ConnectProto.SDKResponseStatus.DONE.number)
        assertEquals(2, ConnectProto.SDKResponseStatus.ERROR.number)
    }

    @Test
    fun `worker disconnect reason enum numbers match the canonical proto`() {
        assertEquals(0, ConnectProto.WorkerDisconnectReason.WORKER_SHUTDOWN.number)
        assertEquals(1, ConnectProto.WorkerDisconnectReason.UNEXPECTED.number)
        assertEquals(2, ConnectProto.WorkerDisconnectReason.GATEWAY_DRAINING.number)
        assertEquals(3, ConnectProto.WorkerDisconnectReason.CONSECUTIVE_HEARTBEATS_MISSED.number)
        assertEquals(4, ConnectProto.WorkerDisconnectReason.MESSAGE_TOO_LARGE.number)
    }

    @Test
    fun `connect message envelope round-trips`() {
        val envelope =
            ConnectProto.ConnectMessage
                .newBuilder()
                .setKind(ConnectProto.GatewayMessageType.WORKER_HEARTBEAT)
                .setPayload(ByteString.copyFromUtf8("payload"))
                .build()

        val decoded = ConnectProto.ConnectMessage.parseFrom(envelope.toByteArray())

        assertEquals(ConnectProto.GatewayMessageType.WORKER_HEARTBEAT, decoded.kind)
        assertEquals("payload", decoded.payload.toStringUtf8())
    }

    @Test
    fun `worker connect request data round-trips all fields`() {
        val original =
            ConnectProto.WorkerConnectRequestData
                .newBuilder()
                .setConnectionId("01JCONNID")
                .setInstanceId("worker-1")
                .setAuthData(
                    ConnectProto.AuthData
                        .newBuilder()
                        .setSessionToken("session-token")
                        .setSyncToken("sync-token"),
                ).setCapabilities(ByteString.copyFromUtf8("""{"connect":"v1"}"""))
                .addApps(
                    ConnectProto.AppConfiguration
                        .newBuilder()
                        .setAppName("my-app")
                        .setAppVersion("1.2.3")
                        .setFunctions(ByteString.copyFromUtf8("[]")),
                ).setWorkerManualReadinessAck(true)
                .setSystemAttributes(
                    ConnectProto.SystemAttributes
                        .newBuilder()
                        .setCpuCores(8)
                        .setMemBytes(1024L * 1024L * 1024L)
                        .setOs("linux"),
                ).setEnvironment("branch-env")
                .setFramework("connect")
                .setPlatform("aws")
                .setSdkVersion("v0.2.2")
                .setSdkLanguage("java")
                .setStartedAt(
                    com.google.protobuf.Timestamp
                        .newBuilder()
                        .setSeconds(1_753_000_000L)
                        .setNanos(500),
                ).setMaxWorkerConcurrency(100)
                .build()

        val decoded = ConnectProto.WorkerConnectRequestData.parseFrom(original.toByteArray())

        assertEquals(original, decoded)
        assertEquals("01JCONNID", decoded.connectionId)
        assertEquals("session-token", decoded.authData.sessionToken)
        assertEquals("my-app", decoded.appsList.single().appName)
        assertEquals("1.2.3", decoded.appsList.single().appVersion)
        assertTrue(decoded.workerManualReadinessAck)
        assertEquals(8, decoded.systemAttributes.cpuCores)
        assertEquals(100, decoded.maxWorkerConcurrency)
        assertEquals(1_753_000_000L, decoded.startedAt.seconds)
    }

    @Test
    fun `optional fields report presence correctly`() {
        val without = ConnectProto.WorkerConnectRequestData.newBuilder().build()
        assertFalse(without.hasEnvironment())
        assertFalse(without.hasPlatform())
        assertFalse(without.hasMaxWorkerConcurrency())

        val executorRequest = ConnectProto.GatewayExecutorRequestData.newBuilder().build()
        assertFalse(executorRequest.hasStepId())

        val withStep =
            ConnectProto.GatewayExecutorRequestData
                .newBuilder()
                .setStepId("step")
                .build()
        assertTrue(withStep.hasStepId())
    }

    @Test
    fun `sdk response round-trips and preserves optional retry-after`() {
        val original =
            ConnectProto.SDKResponse
                .newBuilder()
                .setRequestId("req-1")
                .setAccountId("acct-1")
                .setEnvId("env-1")
                .setAppId("app-1")
                .setStatus(ConnectProto.SDKResponseStatus.ERROR)
                .setBody(ByteString.copyFromUtf8("""{"name":"Error","message":"boom"}"""))
                .setNoRetry(true)
                .setRetryAfter("30")
                .setSdkVersion("inngest-kt:v0.2.2")
                .setRequestVersion(2)
                .setSystemTraceCtx(ByteString.copyFromUtf8("sys"))
                .setUserTraceCtx(ByteString.copyFromUtf8("user"))
                .setRunId("run-1")
                .build()

        val decoded = ConnectProto.SDKResponse.parseFrom(original.toByteArray())

        assertEquals(original, decoded)
        assertTrue(decoded.hasRetryAfter())
        assertEquals("30", decoded.retryAfter)
        assertEquals(2, decoded.requestVersion)

        val withoutRetry =
            ConnectProto.SDKResponse
                .newBuilder()
                .setRequestId("req-2")
                .build()
        assertFalse(withoutRetry.hasRetryAfter())
    }

    @Test
    fun `gateway executor request payload decodes from envelope payload bytes`() {
        val executorRequest =
            ConnectProto.GatewayExecutorRequestData
                .newBuilder()
                .setRequestId("req-1")
                .setAccountId("acct-1")
                .setEnvId("env-1")
                .setAppId("app-uuid")
                .setAppName("my-app")
                .setFunctionId("fn-uuid")
                .setFunctionSlug("my-app-fn")
                .setStepId("step")
                .setRequestPayload(ByteString.copyFromUtf8("""{"ctx":{}}"""))
                .setRunId("run-1")
                .setLeaseId("lease-1")
                .setJobId("job-1")
                .build()

        val envelope =
            ConnectProto.ConnectMessage
                .newBuilder()
                .setKind(ConnectProto.GatewayMessageType.GATEWAY_EXECUTOR_REQUEST)
                .setPayload(executorRequest.toByteString())
                .build()

        val decodedEnvelope = ConnectProto.ConnectMessage.parseFrom(envelope.toByteArray())
        val decoded = ConnectProto.GatewayExecutorRequestData.parseFrom(decodedEnvelope.payload)

        assertEquals(executorRequest, decoded)
        assertEquals("my-app-fn", decoded.functionSlug)
        assertEquals("lease-1", decoded.leaseId)
    }

    @Test
    fun `extend lease ack distinguishes missing new lease id`() {
        val renewed =
            ConnectProto.WorkerRequestExtendLeaseAckData
                .newBuilder()
                .setRequestId("req-1")
                .setNewLeaseId("lease-2")
                .build()
        val lost =
            ConnectProto.WorkerRequestExtendLeaseAckData
                .newBuilder()
                .setRequestId("req-1")
                .build()

        assertTrue(ConnectProto.WorkerRequestExtendLeaseAckData.parseFrom(renewed.toByteArray()).hasNewLeaseId())
        assertFalse(ConnectProto.WorkerRequestExtendLeaseAckData.parseFrom(lost.toByteArray()).hasNewLeaseId())
    }
}
