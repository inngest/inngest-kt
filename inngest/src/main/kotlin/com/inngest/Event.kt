package com.inngest

import com.beust.klaxon.Converter
import com.beust.klaxon.JsonValue
import com.beust.klaxon.KlaxonException

data class Event(
    val id: String? = null,
    val name: String,
    @KlaxonEventData
    val data: LinkedHashMap<String, Any>,
    val user: LinkedHashMap<String, Any>? = null,
    val ts: Long? = null,
    val v: Any? = null,
)

// Klaxon can't bind a nested JSON object/array onto a Map<String, Any> value: the generic type
// argument is just Any, so it tries to reflectively instantiate Any/Object instead of keeping the
// already-parsed tree. This converter intercepts the whole `data` field and rebuilds any Map/List
// it finds (Klaxon's own JsonObject/JsonArray included, since both already implement those
// interfaces) into plain LinkedHashMap/ArrayList, recursively. Scalars are returned untouched, so
// their current types (Int/Long/BigInteger/Double/String/Boolean/null) are unaffected.
@Target(AnnotationTarget.FIELD)
annotation class KlaxonEventData

val eventDataConverter =
    object : Converter {
        override fun canConvert(cls: Class<*>): Boolean = cls == LinkedHashMap::class.java

        override fun fromJson(jv: JsonValue): Any? = normalizeEventDataValue(jv.inside)

        override fun toJson(value: Any): String = throw KlaxonException("Event data serialization is not supported")
    }

private fun normalizeEventDataValue(value: Any?): Any? =
    when (value) {
        is Map<*, *> -> value.entries.associateTo(LinkedHashMap()) { (k, v) -> k.toString() to normalizeEventDataValue(v) }
        is List<*> -> value.mapTo(ArrayList()) { normalizeEventDataValue(it) }
        else -> value
    }

// data class EventAPIResponse(
//    val ids: Array<String>,
//    val status: String,
// )
