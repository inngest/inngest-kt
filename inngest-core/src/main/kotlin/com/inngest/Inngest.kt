package com.inngest

import com.beust.klaxon.Klaxon
import java.io.IOException

class Inngest
    @JvmOverloads
    constructor(val appId: String, framework: String? = null) {
        // TODO - Fetch INNGEST_EVENT_KEY env variable on instantiation
        val headers: RequestHeaders =
            hashMapOf(
                InngestHeaderKey.ContentType.value to "application/json",
                InngestHeaderKey.Sdk.value to "inngest-kt:${Version.getVersion()}",
                InngestHeaderKey.Framework.value to (framework),
            ).filterValues { (it is String) }.entries.associate { (k, v) -> k to v!! }

        val httpClient = HttpClient(RequestConfig(headers))

        inline fun <reified T> send(
            url: String,
            payload: Any,
        ): T? {
            val request = httpClient.build(url, payload)

            return httpClient.send(request) lambda@{ response ->
                // TODO: Handle error case
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                if (Unit::class.java.isAssignableFrom(T::class.java)) {
                    return@lambda Unit as T
                }
                return@lambda Klaxon().parse<T>(response.body!!.charStream())
            }
        }

        inline fun <reified T> sendEvent(payload: Any): T? {
            val eventKey = "test"
            return send("http://localhost:8288/e/$eventKey", payload)
        }
    }
