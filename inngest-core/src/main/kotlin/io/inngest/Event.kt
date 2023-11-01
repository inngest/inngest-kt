package io.inngest

data class Event(
        val name: String,
        val data: LinkedHashMap<String, Any>,
        val user: LinkedHashMap<String, Any>? = null,
        val ts: Long,
        val v: Any? = null,
)
