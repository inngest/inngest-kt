package com.inngest

import com.beust.klaxon.Klaxon
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.Response
import java.io.IOException

class Inngest
    @JvmOverloads
    constructor(
        val appId: String,
        baseUrl: String? = null,
        eventKey: String? = null,
        env: String? = null,
        isDev: Boolean? = null,
    ) {
        val headers: RequestHeaders = Environment.inngestHeaders()
        val env = Environment.inngestEnv(env = env, isDev = isDev)
        val eventKey = Environment.inngestEventKey(eventKey)
        private val baseUrl = Environment.inngestEventApiBaseUrl(env = this.env, url = baseUrl)

        internal val httpClient = HttpClient(RequestConfig(headers))

        inline fun <reified T> send(payload: Any): T? =
            sendEvent<T>(payload) lambda@{ response ->
                // TODO: Handle error case
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                if (Unit::class.java.isAssignableFrom(T::class.java)) {
                    return@lambda Unit as T
                }
                return@lambda Klaxon().parse<T>(response.body!!.charStream())
            }

        fun <T> send(
            payload: Any,
            type: Class<T>,
        ): T? {
            return sendEvent<T>(payload) lambda@{ response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                if (Unit::class.java.isAssignableFrom(type)) {
                    return@lambda null
                }

                val mapper = ObjectMapper()
                return@lambda mapper.readValue(response.body!!.charStream(), type)
            }
        }

        fun <T> sendEvent(
            payload: Any,
            handler: (response: Response) -> T?,
        ): T? {
            val request = httpClient.build("$baseUrl/e/$eventKey", payload)
            return httpClient.send(request, handler)
        }
    }
