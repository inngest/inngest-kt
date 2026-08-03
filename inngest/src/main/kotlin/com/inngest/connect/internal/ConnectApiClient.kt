package com.inngest.connect.internal

import com.inngest.InngestHeaderKey
import com.inngest.connect.v1.ConnectProto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Start request failed with 401: the signing key was rejected. */
internal class ConnectAuthException(
    message: String,
) : Exception(message)

/** Start request failed with 429: the account's connection limit is reached. */
internal class ConnectionLimitException : Exception("maximum number of concurrent connections reached")

/** Any other start/flush failure; retriable with backoff. */
internal class ConnectApiException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * HTTP client for the connect REST endpoints: `/v0/connect/start` (obtain a
 * gateway endpoint and session tokens) and `/v0/connect/flush` (deliver
 * buffered SDK responses when the WebSocket path is unavailable).
 *
 * Bodies are protobuf, unlike the JSON-only [com.inngest.HttpClient], hence a
 * dedicated client.
 */
internal class ConnectApiClient(
    private val apiBaseUrl: String,
    private val envName: String?,
    private val authHeaders: Map<String, String>,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    companion object {
        private val PROTOBUF = "application/protobuf".toMediaType()
    }

    fun start(excludeGateways: Collection<String>): ConnectProto.StartResponse {
        val body =
            ConnectProto.StartRequest
                .newBuilder()
                .addAllExcludeGateways(excludeGateways)
                .build()
                .toByteArray()
                .toRequestBody(PROTOBUF)

        val request = requestBuilder("/v0/connect/start").post(body).build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    when (response.code) {
                        401 -> throw ConnectAuthException(
                            "connect start request was not authorized" +
                                (if (envName != null) " (env: $envName)" else "") +
                                ": $responseBody",
                        )

                        429 -> throw ConnectionLimitException()

                        else -> throw ConnectApiException(
                            "connect start request failed with status ${response.code}: $responseBody",
                        )
                    }
                }
                return ConnectProto.StartResponse.parseFrom(response.body!!.bytes())
            }
        } catch (e: java.io.IOException) {
            throw ConnectApiException("connect start request to $apiBaseUrl failed", e)
        }
    }

    fun flush(sdkResponse: ConnectProto.SDKResponse): ConnectProto.FlushResponse {
        val request =
            requestBuilder("/v0/connect/flush")
                .post(sdkResponse.toByteArray().toRequestBody(PROTOBUF))
                .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    throw ConnectApiException(
                        "connect flush request failed with status ${response.code}: $responseBody",
                    )
                }
                return ConnectProto.FlushResponse.parseFrom(response.body!!.bytes())
            }
        } catch (e: java.io.IOException) {
            throw ConnectApiException("connect flush request to $apiBaseUrl failed", e)
        }
    }

    private fun requestBuilder(path: String): Request.Builder {
        val builder =
            Request
                .Builder()
                .url(apiBaseUrl.trimEnd('/') + path)
                .header("Content-Type", "application/protobuf")

        authHeaders.forEach { (name, value) -> builder.header(name, value) }
        if (envName != null) {
            builder.header(InngestHeaderKey.Environment.value, envName)
        }
        return builder
    }
}
