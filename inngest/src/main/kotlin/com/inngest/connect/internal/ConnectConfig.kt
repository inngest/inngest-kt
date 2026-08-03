package com.inngest.connect.internal

import com.inngest.CommHandler
import com.inngest.InngestEnv
import com.inngest.InngestSystem
import com.inngest.ServeConfig
import com.inngest.SupportedFrameworkName
import com.inngest.Version
import com.inngest.connect.ConnectOptions
import com.inngest.signingkey.getAuthorizationHeader
import java.net.InetAddress

/** One app's resolved connect configuration. */
internal class ConnectAppConfig(
    val appName: String,
    val functionsJson: String,
    val commHandler: CommHandler,
)

/**
 * Fully-resolved configuration for a worker connection, derived from
 * [ConnectOptions] plus system properties and environment variables using the
 * SDK's usual precedence (explicit option, then system property, then env
 * var, then default).
 */
internal class ConnectConfig(
    options: ConnectOptions,
) {
    companion object {
        const val CAPABILITIES_JSON = """{"connect":"v1"}"""
        const val SDK_LANGUAGE = "java"
        const val FRAMEWORK = "connect"
        const val DEFAULT_MAX_WORKER_CONCURRENCY = 1_000
        private const val BRANCH_KEY_PREFIX = "signkey-branch-"

        private fun systemValue(key: InngestSystem): String? = System.getProperty(key.value) ?: System.getenv(key.value)
    }

    val apps: List<ConnectAppConfig>
    val isDev: Boolean
    val envName: String?
    val apiBaseUrl: String
    val instanceId: String
    val maxWorkerConcurrency: Int
    val gatewayUrlOverride: String?
    val missedHeartbeatTolerance: Int = options.missedHeartbeatTolerance
    val handleShutdownSignals: Boolean = options.handleShutdownSignals
    val sdkVersion: String = "v${Version.getVersion()}"

    /**
     * `Authorization` header for start/flush requests, or empty in dev mode.
     * Resolving this eagerly also enforces "signing key required outside dev".
     */
    val authHeaders: Map<String, String>

    init {
        require(options.apps.isNotEmpty()) { "at least one app is required" }

        val duplicateIds =
            options.apps
                .groupingBy { it.client.appId }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        require(duplicateIds.isEmpty()) { "duplicate app ids: $duplicateIds" }

        val envs = options.apps.map { it.client.env }.distinct()
        require(envs.size == 1) {
            "all apps must be configured for the same environment, got: $envs"
        }

        val primaryClient = options.apps.first().client
        val serveConfig =
            ServeConfig(
                client = primaryClient,
                signingKey = options.signingKey,
                baseUrl = options.apiBaseUrl,
            )

        isDev = primaryClient.env == InngestEnv.Dev
        apiBaseUrl = serveConfig.baseUrl()

        envName =
            systemValue(InngestSystem.Env)
                ?: if (primaryClient.env == InngestEnv.Other) primaryClient.env.value else null

        authHeaders =
            if (isDev) {
                emptyMap()
            } else {
                val signingKey = serveConfig.signingKey()
                if (signingKey.startsWith(BRANCH_KEY_PREFIX) && envName == null) {
                    throw IllegalArgumentException(
                        "an environment name (INNGEST_ENV) is required when using a branch environment signing key",
                    )
                }
                getAuthorizationHeader(signingKey)
            }

        apps =
            options.apps.map { app ->
                val handler =
                    CommHandler(
                        functions = app.functions.associateBy { it.id() },
                        client = app.client,
                        config = ServeConfig(client = app.client, signingKey = options.signingKey),
                        framework = SupportedFrameworkName.Connect,
                    )
                ConnectAppConfig(
                    appName = app.client.appId,
                    functionsJson = handler.getConnectFunctionConfigsJson(),
                    commHandler = handler,
                )
            }

        instanceId = options.instanceId ?: defaultInstanceId()

        maxWorkerConcurrency =
            options.maxWorkerConcurrency
                ?: systemValue(InngestSystem.ConnectMaxWorkerConcurrency)?.toIntOrNull()?.takeIf { it > 0 }
                ?: DEFAULT_MAX_WORKER_CONCURRENCY

        gatewayUrlOverride = options.gatewayUrl ?: systemValue(InngestSystem.ConnectGatewayUrl)
    }

    private fun defaultInstanceId(): String =
        try {
            InetAddress.getLocalHost().hostName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
}
