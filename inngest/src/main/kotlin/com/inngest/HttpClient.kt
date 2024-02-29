package com.inngest

import com.beust.klaxon.Klaxon
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

typealias RequestHeaders = Map<String, String>

data class RequestConfig(val headers: RequestHeaders? = null)

val jsonMediaType = "application/json".toMediaType()

internal class HttpClient(private val clientConfig: RequestConfig) {
    private val client = OkHttpClient()

    fun <T> send(
        request: okhttp3.Request,
        handler: (Response) -> T,
    ) = this.client.newCall(request).execute().use(handler)

    fun build(
        url: String,
        payload: Any,
        config: RequestConfig? = null,
    ): okhttp3.Request {
        val jsonRequestBody = Klaxon().toJsonString(payload)
        val body = jsonRequestBody.toRequestBody(jsonMediaType)

        val clientHeaders = clientConfig.headers ?: emptyMap()
        val requestHeaders = config?.headers ?: emptyMap()

        return okhttp3.Request.Builder()
            .url(url)
            .post(body)
            .headers(toOkHttpHeaders(clientHeaders + requestHeaders))
            .build()
    }
}

fun toOkHttpHeaders(requestHeaders: RequestHeaders?): Headers {
    val builder = Headers.Builder()
    requestHeaders?.forEach { (k, v) -> builder.add(k, v) }
    return builder.build()
}
