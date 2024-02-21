package io.inngest

import com.beust.klaxon.JsonObject
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import java.io.StringReader
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

    inline fun <reified T> getState(hashedId: String): T? {
        val mapper = ObjectMapper()
        val node: JsonNode = mapper.readTree(payloadJson)
        val stepResult = node.path("steps").get(hashedId) ?: throw StateNotFound()
        if (stepResult.has("data")) {
            val dataNode = stepResult.get("data")
            return mapper.treeToValue(dataNode, T::class.java);
        } else if (stepResult.has("error")) {
            // TODO - Parse the error and throw it
            return null
        }
        // NOTE - Sleep steps will be stored as null
        // TODO - Check the state is actually null
        return null;
    }

}