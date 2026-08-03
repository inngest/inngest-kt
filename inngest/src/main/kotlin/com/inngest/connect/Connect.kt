package com.inngest.connect

import com.inngest.connect.internal.ConnectApiClient
import com.inngest.connect.internal.ConnectConfig
import com.inngest.connect.internal.ConnectionSupervisor
import java.time.Duration

/**
 * Entry point for Inngest Connect: a persistent, worker-initiated WebSocket
 * transport that receives execution requests from an Inngest gateway instead
 * of serving inbound HTTP.
 *
 * ```java
 * WorkerConnection conn = Connect.start(
 *     ConnectOptions.builder(List.of(new ConnectApp(client, functions))).build());
 * conn.awaitClosed();
 * ```
 */
object Connect {
    /**
     * Establishes a connection and blocks until it is ready to receive work
     * (or throws on a terminal startup failure). Reconnection, drains, and
     * graceful shutdown are handled in the background afterwards.
     */
    @JvmStatic
    fun start(options: ConnectOptions): WorkerConnection {
        val config = ConnectConfig(options)
        val apiClient =
            ConnectApiClient(
                apiBaseUrl = config.apiBaseUrl,
                envName = config.envName,
                authHeaders = config.authHeaders,
            )
        val supervisor = ConnectionSupervisor(config, apiClient)
        val connection = SupervisedWorkerConnection(supervisor)

        if (config.handleShutdownSignals) {
            val hook = Thread({ connection.close() }, "inngest-connect-shutdown-hook")
            try {
                Runtime.getRuntime().addShutdownHook(hook)
            } catch (e: IllegalStateException) {
                // JVM already shutting down; nothing to register.
            }
        }

        supervisor.start()
        return connection
    }

    private class SupervisedWorkerConnection(
        private val supervisor: ConnectionSupervisor,
    ) : WorkerConnection {
        override val state: ConnectionState
            get() = supervisor.currentState

        override val connectionId: String?
            get() = supervisor.connectionId

        override fun close() {
            supervisor.close()
        }

        override fun awaitClosed() {
            supervisor.awaitClosed()
        }

        override fun awaitClosed(timeout: Duration): Boolean = supervisor.awaitClosed(timeout.toMillis())

        override fun debugState(): ConnectDebugState = supervisor.debugState()
    }
}
