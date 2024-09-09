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
        val user: Any? = null,
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
    var id: String?,
    var name: String?,
    var data: Map<String, Any>?,
    private var user: Any?,
    private var ts: Long?,
    private var v: String? = null,
) {
    fun id(id: String): InngestEventBuilder {
        this.id = id
        return this
    }

    fun name(name: String): InngestEventBuilder {
        this.name = name
        return this
    }

    fun data(data: Map<String, Any>): InngestEventBuilder {
        this.data = data
        return this
    }

    fun ts(ts: Long): InngestEventBuilder {
        this.ts = ts
        return this
    }

    fun v(v: String): InngestEventBuilder {
        this.v = v
        return this
    }

    fun build(): InngestEvent {
        if (name == null) {
            throw IllegalArgumentException("name is required")
        }
        if (data == null) {
            throw IllegalArgumentException("data is required")
        }
        return InngestEvent(
            name!!,
            data!!,
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
