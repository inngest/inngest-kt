package com.inngest.connect.internal

import com.inngest.connect.v1.ConnectProto
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ConnectionPhaseTest {
    // Exhaustive expected transition matrix: from -> set of allowed targets.
    private val allowedTransitions =
        mapOf(
            ConnectionPhase.New to
                setOf(ConnectionPhase.Handshaking, ConnectionPhase.Retired, ConnectionPhase.Closed),
            ConnectionPhase.Handshaking to
                setOf(ConnectionPhase.Active, ConnectionPhase.Retired, ConnectionPhase.Closed),
            ConnectionPhase.Active to
                setOf(
                    ConnectionPhase.Draining,
                    ConnectionPhase.Closing,
                    ConnectionPhase.Retired,
                    ConnectionPhase.Closed,
                ),
            ConnectionPhase.Draining to setOf(ConnectionPhase.Retired, ConnectionPhase.Closed),
            ConnectionPhase.Closing to setOf(ConnectionPhase.Retired, ConnectionPhase.Closed),
            ConnectionPhase.Retired to setOf(ConnectionPhase.Closed),
            ConnectionPhase.Closed to emptySet(),
        )

    @Test
    fun `transition matrix is exactly the expected table`() {
        for (from in ConnectionPhase.entries) {
            for (to in ConnectionPhase.entries) {
                if (from == to) continue
                val expected = allowedTransitions.getValue(from).contains(to)
                assertEquals(
                    expected,
                    from.canTransitionTo(to),
                    "transition $from -> $to should be ${if (expected) "allowed" else "rejected"}",
                )
            }
        }
    }

    @Test
    fun `only retired and closed are no-write phases`() {
        val noWrite = ConnectionPhase.entries.filter { it.isNoWrite }
        assertEquals(listOf(ConnectionPhase.Retired, ConnectionPhase.Closed), noWrite)
    }

    // Expected write policy: phase -> set of allowed message kinds.
    private val workerWriteKinds =
        listOf(
            ConnectProto.GatewayMessageType.WORKER_HEARTBEAT,
            ConnectProto.GatewayMessageType.WORKER_REQUEST_ACK,
            ConnectProto.GatewayMessageType.WORKER_REPLY,
            ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE,
            ConnectProto.GatewayMessageType.WORKER_PAUSE,
            ConnectProto.GatewayMessageType.WORKER_STATUS,
            ConnectProto.GatewayMessageType.WORKER_READY,
            ConnectProto.GatewayMessageType.WORKER_CONNECT,
        )

    private val allowedWrites =
        mapOf(
            ConnectionPhase.New to emptySet(),
            ConnectionPhase.Handshaking to emptySet(),
            ConnectionPhase.Active to
                setOf(
                    ConnectProto.GatewayMessageType.WORKER_HEARTBEAT,
                    ConnectProto.GatewayMessageType.WORKER_REQUEST_ACK,
                    ConnectProto.GatewayMessageType.WORKER_REPLY,
                    ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE,
                    ConnectProto.GatewayMessageType.WORKER_PAUSE,
                    ConnectProto.GatewayMessageType.WORKER_STATUS,
                    ConnectProto.GatewayMessageType.WORKER_READY,
                ),
            ConnectionPhase.Draining to
                setOf(
                    ConnectProto.GatewayMessageType.WORKER_REPLY,
                    ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE,
                ),
            ConnectionPhase.Closing to
                setOf(
                    ConnectProto.GatewayMessageType.WORKER_REPLY,
                    ConnectProto.GatewayMessageType.WORKER_REQUEST_EXTEND_LEASE,
                    ConnectProto.GatewayMessageType.WORKER_PAUSE,
                ),
            ConnectionPhase.Retired to emptySet<ConnectProto.GatewayMessageType>(),
            ConnectionPhase.Closed to emptySet(),
        )

    @Test
    fun `write policy matrix is exactly the expected table`() {
        for (phase in ConnectionPhase.entries) {
            for (kind in workerWriteKinds) {
                val expected = allowedWrites.getValue(phase).contains(kind)
                assertEquals(
                    expected,
                    phase.allowsWrite(kind),
                    "write $kind in phase $phase should be ${if (expected) "allowed" else "rejected"}",
                )
            }
        }
    }

    @Test
    fun `lifecycle applies valid transitions and rejects invalid ones`() {
        val lifecycle = ConnectionLifecycle()
        assertEquals(ConnectionPhase.New, lifecycle.phase)

        assertEquals(PhaseTransitionResult.Changed, lifecycle.transition(ConnectionPhase.Handshaking))
        assertEquals(PhaseTransitionResult.Changed, lifecycle.transition(ConnectionPhase.Active))
        assertEquals(PhaseTransitionResult.NoOp, lifecycle.transition(ConnectionPhase.Active))

        // Draining cannot go back to Active.
        assertEquals(PhaseTransitionResult.Changed, lifecycle.transition(ConnectionPhase.Draining))
        assertEquals(PhaseTransitionResult.Invalid, lifecycle.transition(ConnectionPhase.Active))
        assertEquals(ConnectionPhase.Draining, lifecycle.phase)

        assertEquals(PhaseTransitionResult.Changed, lifecycle.transition(ConnectionPhase.Retired))
        assertEquals(PhaseTransitionResult.Changed, lifecycle.transition(ConnectionPhase.Closed))
        assertEquals(PhaseTransitionResult.Invalid, lifecycle.transition(ConnectionPhase.Active))
    }

    @Test
    fun `no-write callback fires exactly once even when retired then closed`() {
        val fired = AtomicInteger()
        val lifecycle = ConnectionLifecycle(onEnterNoWrite = Runnable { fired.incrementAndGet() })

        lifecycle.transition(ConnectionPhase.Handshaking)
        lifecycle.transition(ConnectionPhase.Active)
        assertEquals(0, fired.get())

        lifecycle.transition(ConnectionPhase.Retired)
        assertEquals(1, fired.get())

        lifecycle.transition(ConnectionPhase.Closed)
        assertEquals(1, fired.get(), "Retired -> Closed must not re-fire the no-write callback")
    }

    @Test
    fun `no-write callback fires under concurrent racing transitions exactly once`() {
        repeat(50) {
            val fired = AtomicInteger()
            val lifecycle = ConnectionLifecycle(onEnterNoWrite = Runnable { fired.incrementAndGet() })
            lifecycle.transition(ConnectionPhase.Handshaking)
            lifecycle.transition(ConnectionPhase.Active)

            val threads =
                listOf(ConnectionPhase.Retired, ConnectionPhase.Closed, ConnectionPhase.Retired).map { target ->
                    Thread { lifecycle.transition(target) }
                }
            threads.forEach { it.start() }
            threads.forEach { it.join() }

            assertEquals(1, fired.get(), "no-write boundary crossed more than once")
            assertTrue(lifecycle.phase.isNoWrite)
        }
    }

    @Test
    fun `backoff schedule matches the cross-sdk contract and caps at five minutes`() {
        val expected = listOf<Long>(1_000, 2_000, 5_000, 10_000, 20_000, 30_000, 60_000, 120_000, 300_000)
        expected.forEachIndexed { attempt, delay ->
            assertEquals(delay, Backoff.delayMillis(attempt))
        }
        assertEquals(300_000, Backoff.delayMillis(100))
        assertEquals(1_000, Backoff.delayMillis(-1))
    }

    @Test
    fun `gateway draining retry delay stays within its jitter window`() {
        repeat(1_000) {
            val delay = Backoff.gatewayDrainingRetryMillis()
            assertTrue(delay in 500..1_500, "delay $delay outside [500, 1500]")
            assertFalse(delay < 0)
        }
    }
}
