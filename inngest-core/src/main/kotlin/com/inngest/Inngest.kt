package com.inngest

import com.beust.klaxon.Klaxon
import java.io.IOException

class Inngest(val appId: String) {
    // TODO - Fetch INNGEST_EVENT_KEY env variable on instantiation

    val headers: RequestHeaders = Environment.inngestHeaders()
    internal val httpClient = HttpClient(RequestConfig(headers))

    internal inline fun <reified T> send(
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

    internal inline fun <reified T> sendEvent(payload: Any): T? {
        val eventKey = "test"
        return send("http://localhost:8288/e/$eventKey", payload)
    }
}
