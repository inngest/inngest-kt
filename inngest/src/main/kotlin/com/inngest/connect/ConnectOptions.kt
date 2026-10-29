package com.inngest.connect

/**
 * Options for establishing a connect worker connection.
 *
 * Build with [ConnectOptions.builder]:
 * ```java
 * ConnectOptions options = ConnectOptions
 *     .builder(Collections.singletonList(new ConnectApp(client, functions)))
 *     .instanceId("worker-1")
 *     .build();
 * ```
 */
class ConnectOptions private constructor(
    val apps: List<ConnectApp>,
    val instanceId: String?,
    val maxWorkerConcurrency: Int?,
    val gatewayUrl: String?,
    val handleShutdownSignals: Boolean,
    val signingKey: String?,
    val apiBaseUrl: String?,
    val missedHeartbeatTolerance: Int,
) {
    companion object {
        const val DEFAULT_MISSED_HEARTBEAT_TOLERANCE = 3

        @JvmStatic
        fun builder(apps: List<ConnectApp>): Builder = Builder(apps)
    }

    class Builder(
        private val apps: List<ConnectApp>,
    ) {
        private var instanceId: String? = null
        private var maxWorkerConcurrency: Int? = null
        private var gatewayUrl: String? = null
        private var handleShutdownSignals: Boolean = true
        private var signingKey: String? = null
        private var apiBaseUrl: String? = null
        private var missedHeartbeatTolerance: Int = DEFAULT_MISSED_HEARTBEAT_TOLERANCE

        /**
         * Stable identifier for this worker across restarts (e.g. a hostname).
         * Defaults to the machine hostname.
         */
        fun instanceId(id: String): Builder = apply { this.instanceId = id }

        /**
         * Maximum number of executor requests processed concurrently. The
         * value is advertised to the gateway for routing and also bounds the
         * local executor pool. Defaults to
         * `INNGEST_CONNECT_MAX_WORKER_CONCURRENCY` when set, otherwise 1000.
         */
        fun maxWorkerConcurrency(limit: Int): Builder = apply { this.maxWorkerConcurrency = limit }

        /**
         * Override the gateway WebSocket endpoint returned by the start
         * request — useful behind proxies. Defaults to
         * `INNGEST_CONNECT_GATEWAY_URL` when set.
         */
        fun gatewayUrl(url: String): Builder = apply { this.gatewayUrl = url }

        /**
         * When true (the default), a JVM shutdown hook closes the connection
         * gracefully on SIGINT/SIGTERM.
         */
        fun handleShutdownSignals(enabled: Boolean): Builder = apply { this.handleShutdownSignals = enabled }

        /** Signing key; defaults to `INNGEST_SIGNING_KEY`. Required outside dev mode. */
        fun signingKey(key: String): Builder = apply { this.signingKey = key }

        /** Inngest API origin for start/flush requests; defaults to `INNGEST_API_BASE_URL`. */
        fun apiBaseUrl(url: String): Builder = apply { this.apiBaseUrl = url }

        /**
         * Number of gateway heartbeat intervals that may elapse without a
         * gateway heartbeat before the connection is considered lost.
         */
        fun missedHeartbeatTolerance(tolerance: Int): Builder =
            apply {
                require(tolerance >= 0) { "missedHeartbeatTolerance must be >= 0" }
                this.missedHeartbeatTolerance = tolerance
            }

        fun build(): ConnectOptions =
            ConnectOptions(
                apps = apps.toList(),
                instanceId = instanceId,
                maxWorkerConcurrency = maxWorkerConcurrency,
                gatewayUrl = gatewayUrl,
                handleShutdownSignals = handleShutdownSignals,
                signingKey = signingKey,
                apiBaseUrl = apiBaseUrl,
                missedHeartbeatTolerance = missedHeartbeatTolerance,
            )
    }
}
