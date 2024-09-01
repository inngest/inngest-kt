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
        isDev: Boolean? = null,
    ) {
        val headers: RequestHeaders = Environment.inngestHeaders()
        val env = Environment.inngestEnv(env = env, isDev = isDev)
        val eventKey = Environment.inngestEventKey(eventKey)
        private val baseUrl = Environment.inngestEventApiBaseUrl(env = this.env, url = baseUrl)

        internal val httpClient = HttpClient(RequestConfig(headers))

        /**
         * Send a single event to Inngest,
         *
         * @param event The event to send.
         *
         */
        fun send(event: InngestEvent): SendEventsResponse? = send(arrayOf(event))

        /**
         * Send multiple events to Inngest,
         *
         * @param events The events to send.
         *
         */
        fun send(events: Array<InngestEvent>): SendEventsResponse? {
            val request = httpClient.build("$baseUrl/e/$eventKey", events)

            return httpClient.send(request) lambda@{ response ->
                // TODO: Handle error case
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                return@lambda Klaxon().parse<SendEventsResponse>(response.body!!.charStream())
            }
        }
    }
