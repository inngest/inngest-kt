package io.inngest

import com.beust.klaxon.JsonObject
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import java.io.StringReader
import java.security.MessageDigest

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
        val stepResult = node.path("steps").get(hashedId) ?: return null
        if (stepResult.has("data")) {
            val dataNode = stepResult.get("data")
            return mapper.treeToValue(dataNode, T::class.java);
        }
        if (stepResult.has("error")) {
            // TODO - Parse the error and throw it
            return null
        }
        // NOTE - Should we throw error for unable to parse state?
        return null;
    }

}