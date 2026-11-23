package com.inngest

import com.inngest.exception.SigningKeyRequiredException
import com.inngest.utility.nonBlankOrNull

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
            id.nonBlankOrNull()?.let { return it }
            return client.appId
        }

        fun signingKey(): String {
            signingKey.nonBlankOrNull()?.let { return it }

            return when (client.env) {
                InngestEnv.Dev -> {
                    DUMMY_SIGNING_KEY
                }

                else -> {
                    Environment.systemValue(InngestSystem.SigningKey.value)
                        ?: throw SigningKeyRequiredException()
                }
            }
        }

        fun hasSigningKey() =
            when (signingKey()) {
                DUMMY_SIGNING_KEY -> false
                else -> true
            }

        fun baseUrl(): String {
            baseUrl.nonBlankOrNull()?.let { return it }

            val url = Environment.systemValue(InngestSystem.ApiBaseUrl.value)
            if (url != null) {
                return url
            }

            return when (client.env) {
                InngestEnv.Dev -> "http://127.0.0.1:8288"
                else -> "https://api.inngest.com"
            }
        }

        fun serveOrigin(): String? {
            serveOrigin.nonBlankOrNull()?.let { return it }
            return Environment.systemValue(InngestSystem.ServeOrigin.value)
        }

        fun servePath(): String? {
            servePath.nonBlankOrNull()?.let { return it }
            return Environment.systemValue(InngestSystem.ServePath.value)
        }

        fun logLevel(): String {
            logLevel.nonBlankOrNull()?.let { return it }
            return Environment.systemValue(InngestSystem.LogLevel.value) ?: "info"
        }
    }
