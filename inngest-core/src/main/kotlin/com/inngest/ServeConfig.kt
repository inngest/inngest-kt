package com.inngest

class ServeConfig(
    val client: Inngest,
    internal val id: String? = null,
    internal val signingKey: String? = null,
    internal val serveOrigin: String? = null,
    internal val servePath: String? = null,
    // streaming: String = "false" // probably can't stream yet
    internal val logLevel: String? = null,
    internal val baseUrl: String? = null,
) {
    fun appId(): String {
        if (id != null) return id
        return client.appId
    }

    fun signingKey(): String {
        if (signingKey != null) return signingKey
        return System.getenv(InngestSystem.EventKey.value) ?: ""
    }

    fun baseUrl(): String {
        if (baseUrl != null) return baseUrl

        val url = System.getenv(InngestSystem.EventApiBaseUrl.value)
        if (url != null) {
            return url
        }

        return when (client.env) {
            InngestEnv.Dev -> "http://127.0.0.1:8288"
            InngestEnv.Prod -> "https://inn.gs"
            InngestEnv.Other -> "https://inn.gs"
        }
    }

    fun serveOrigin(): String? {
        if (serveOrigin != null) return serveOrigin
        return System.getenv(InngestSystem.ServeOrigin.value)
    }

    fun servePath(): String? {
        if (servePath != null) return servePath
        return System.getenv(InngestSystem.ServePath.value)
    }

    fun logLevel(): String {
        if (logLevel != null) return logLevel
        return System.getenv(InngestSystem.LogLevel.value) ?: "info"
    }
}
