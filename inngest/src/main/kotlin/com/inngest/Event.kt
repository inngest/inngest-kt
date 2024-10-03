package com.inngest

import com.beust.klaxon.Json

/**
 * An internal class used for parsing events sent to Inngest functions
 */
internal data class Event(
    val id: String,
    val name: String,
    val data: LinkedHashMap<String, Any>,
    val user: LinkedHashMap<String, Any>? = null,
    val ts: Long,
    val v: Any? = null,
)

/**
 * Create an event to send to Inngest
 */
data class InngestEvent
    @JvmOverloads
    constructor(
        val name: String,
        val data: Map<String, Any>,
        @Json(serializeNull = false)
        val user: Map<String, Any>? = null,
        @Json(serializeNull = false)
        val id: String? = null,
        @Json(serializeNull = false)
        val ts: Long? = null,
        @Json(serializeNull = false)
        val v: String? = null,
    )

/**
 * Construct a new Inngest Event via builder
 */
class InngestEventBuilder(
    val name: String,
    val data: Map<String, Any>,
) {
    private var id: String? = null
    private var user: Map<String, Any>? = null
    private var ts: Long? = null
    private var v: String? = null

    fun id(id: String): InngestEventBuilder = apply { this.id = id }

    fun ts(ts: Long): InngestEventBuilder = apply { this.ts = ts }

    fun user(user: Map<String, Any>) = apply { this.user = user }

    fun v(v: String): InngestEventBuilder = apply { this.v = v }

    fun build(): InngestEvent {
        return InngestEvent(
            name,
            data,
            user,
            id,
            ts,
            v,
        )
    }
}

/**
 * The response from the Inngest Event API including the ids of any event created
 * in the order of which they were included in the request
 */
data class SendEventsResponse(
    val ids: Array<String>,
    val status: Int,
)
