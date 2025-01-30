package com.inngest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.security.MessageDigest

class StateNotFound : Throwable("State not found for id")

class State(
    private val payloadJson: String,
) {
    private val stepIdsToNextStepNumber = mutableMapOf<String, Int>()
    private val stepIds = mutableSetOf<String>()

    fun getHashFromId(id: String): String {
        val idToHash: String = findNextAvailableStepId(id)
        stepIds.add(idToHash)

        val bytes = idToHash.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-1")
        val hashedBytes = digest.digest(bytes)
        val sb = StringBuilder()
        for (byte in hashedBytes) {
            sb.append(String.format("%02x", byte))
        }
        return sb.toString()
    }

    private fun findNextAvailableStepId(id: String): String {
        if (id !in stepIds) {
            return id
        }

        // start with the seen count so far for current stepId
        // but loop over all seen stepIds to make sure a user didn't explicitly define
        // a step using the same step number
        var stepNumber = stepIdsToNextStepNumber.getOrDefault(id, 1)
        while ("$id:$stepNumber" in stepIds) {
            stepNumber = stepNumber + 1
        }
        // now we know stepNumber is unused and can be used for the current stepId
        // save stepNumber + 1 to the hash for next time
        stepIdsToNextStepNumber[id] = stepNumber + 1

        return "$id:$stepNumber"
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

        return when {
            stepResult.has(fieldName) -> deserializeStepData(stepResult.get(fieldName), type)
            stepResult.has("error") -> throw mapper.treeToValue(stepResult.get("error"), StepError::class.java)
            // NOTE - Sleep steps will be stored as null
            stepResult is NullNode -> null
            else -> throw Exception("Unexpected step data structure")
        }
    }

    private fun <T> deserializeStepData(
        serializedStepData: JsonNode?,
        type: Class<T>,
    ): T? {
        val mapper = ObjectMapper()
        if (serializedStepData == null || !serializedStepData.isObject || !serializedStepData.has("class")) {
            // null and primitives can be deserialized directly
            return mapper.treeToValue(serializedStepData, type)
        }

        val writeableJson = serializedStepData as ObjectNode
        val className = writeableJson.remove("class").asText()
        return mapper.treeToValue(writeableJson, Class.forName(className)) as T
    }
}
