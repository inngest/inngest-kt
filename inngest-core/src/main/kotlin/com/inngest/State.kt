package com.inngest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest

class StateNotFound() : Throwable("State not found for id")

class State(val payloadJson: String) {
    fun getHashFromId(id: String): String {
        val bytes = id.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-1")
        val hashedBytes = digest.digest(bytes)
        val sb = StringBuilder()
        for (byte in hashedBytes) {
            sb.append(String.format("%02x", byte))
        }
        return sb.toString()
    }

    inline fun <reified T> getState(hashedId: String): T? = getState(hashedId, T::class.java)

    fun <T> getState(
        hashedId: String,
        type: Class<T>,
    ): T? {
        val mapper = ObjectMapper()
        val node: JsonNode = mapper.readTree(payloadJson)
        val stepResult = node.path("steps").get(hashedId) ?: throw StateNotFound()
        if (stepResult.has("data")) {
            val dataNode = stepResult.get("data")
            return mapper.treeToValue(dataNode, type)
        } else if (stepResult.has("error")) {
            // TODO - Parse the error and throw it
            return null
        }
        // NOTE - Sleep steps will be stored as null
        // TODO - Investigate if sendEvents stores null as well.
        // TODO - Check the state is actually null
        return null
    }
}
