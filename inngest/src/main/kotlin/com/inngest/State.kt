package com.inngest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest

class StateNotFound : Throwable("State not found for id")

class State(
    private val payloadJson: String,
) {
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

    inline fun <reified T> getState(
        hashedId: String,
        fieldName: String = "data",
    ): T? = getState(hashedId, T::class.java, fieldName)

    fun <T> getState(
        hashedId: String,
        type: Class<T>,
        fieldName: String = "data",
    ): T? {
        val mapper = ObjectMapper()
        val node: JsonNode = mapper.readTree(payloadJson)
        val stepResult = node.path("steps").get(hashedId) ?: throw StateNotFound()

        if (stepResult.has(fieldName)) {
            val dataNode = stepResult.get(fieldName)
            return mapper.treeToValue(dataNode, type)
        } else if (stepResult.has("error")) {
            val error = mapper.treeToValue(stepResult.get("error"), StepError::class.java)
            throw error
        }
        // NOTE - Sleep steps will be stored as null
        // TODO - Investigate if sendEvents stores null as well.
        // TODO - Check the state is actually null
        return null
    }
}
