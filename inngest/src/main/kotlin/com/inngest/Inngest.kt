package com.inngest

import com.beust.klaxon.Klaxon
import java.io.IOException
import io.ktor.http.*
import java.net.ConnectException

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

            try {
                return httpClient.send(request) lambda@{ response ->
                    if (!response.isSuccessful) {
                        // TODO - Attempt to parse the HTTP response and get error from JSON body to pass here
                        throw InngestSendEventBadResponseCodeException(response.code)
                    }

                    val responseBody = response.body!!.charStream()
                    try {
                        val sendEventsResponse = Klaxon().parse<EventAPIResponse>(responseBody)
                        if (sendEventsResponse != null) {
                            return@lambda sendEventsResponse
                        }
                    } catch (e: Exception) {
                        throw InngestSendEventInvalidResponseException(responseBody.toString())
                    }
                    throw InngestSendEventInvalidResponseException(responseBody.toString())
                }
            } catch (e: ConnectException) {
                throw InngestSendEventConnectException(e.message!!)
            } catch (e: Exception) {
                throw InngestSendEventException(e.message!!)
            }
        }
    }

/**
 * A generic exception occurred while sending events
 */
open class InngestSendEventException(
    message: String,
) : Exception("Failed to send event: $message")

/**
 * A failure occurred establishing a connection to the Inngest Event API
 */
class InngestSendEventConnectException(
    message: String,
) : InngestSendEventException(message)

/**
 * The Inngest Event API returned a non-successful HTTP status code
 */
class InngestSendEventBadResponseCodeException(
    code: Int,
) : InngestSendEventException("Bad response code: $code")

/**
 * The Inngest Event API returned a response that was not parsable
 */
class InngestSendEventInvalidResponseException(
    message: String,
) : InngestSendEventException("Unable to parse response: $message")
