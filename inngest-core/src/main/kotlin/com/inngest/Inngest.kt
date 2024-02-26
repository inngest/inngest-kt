package com.inngest

import com.beust.klaxon.Klaxon
import java.io.IOException

class Inngest
    @JvmOverloads
    constructor(
        val appId: String,
        baseUrl: String? = null,
        eventKey: String? = null,
        env: String? = null,
    ) {
        val headers: RequestHeaders = Environment.inngestHeaders()
        val eventKey = Environment.inngestEventKey(eventKey)
        val baseUrl = Environment.inngestEventApiBaseUrl(env = env, url = baseUrl)
        val env = Environment.inngestEnv(env)

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
