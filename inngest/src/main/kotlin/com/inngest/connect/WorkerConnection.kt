package com.inngest.connect

import java.time.Duration

/**
 * A live connect worker connection.
 *
 * The connection keeps a non-daemon thread running, so the JVM stays alive
 * until [close] is called (or the process is signalled and the shutdown hook
 * closes it).
 */
interface WorkerConnection {
    /** Current user-visible connection state. */
    val state: ConnectionState

    /** The active gateway connection id, or null while (re)connecting. */
    val connectionId: String?

    /**
     * Gracefully shut down: stop accepting new work, drain in-flight
     * requests, deliver outstanding replies, and close the connection.
     * Idempotent; blocks until fully closed.
     */
    fun close()

    /** Blocks until the connection is fully closed. */
    fun awaitClosed()

    /** Blocks up to [timeout]; returns true when fully closed. */
    fun awaitClosed(timeout: Duration): Boolean
}
