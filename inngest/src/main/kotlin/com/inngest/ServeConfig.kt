package com.inngest

const val DUMMY_SIGNING_KEY = "test"

class ServeConfig
    @JvmOverloads
    constructor(
        val client: Inngest,
        private val id: String? = null,
        private val signingKey: String? = null,
        private val serveOrigin: String? = null,
        private val servePath: String? = null,
        // streaming: String = "false" // probably can't stream yet
        private val logLevel: String? = null,
        private val baseUrl: String? = null,
    ) {
        fun appId(): String {
            if (id != null) return id
            return client.appId
        }

        fun signingKey(): String {
            if (signingKey != null) return signingKey

            return when (client.env) {
                InngestEnv.Dev -> DUMMY_SIGNING_KEY
                else -> {
                    val signingKey =
                        System.getenv(InngestSystem.SigningKey.value)
                            ?: throw Exception("signing key is required")
                    signingKey
                }
            }
        }

        fun hasSigningKey() =
            when (signingKey()) {
                DUMMY_SIGNING_KEY -> false
                "" -> false
                else -> true
            }

        fun baseUrl(): String {
            if (baseUrl != null) return baseUrl

            val url = System.getenv(InngestSystem.ApiBaseUrl.value)
            if (url != null) {
                return url
            }

            return when (client.env) {
                InngestEnv.Dev -> "http://127.0.0.1:8288"
                else -> "https://api.inngest.com"
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
