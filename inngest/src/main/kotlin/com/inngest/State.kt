package com.inngest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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


        if (stepResult.has(fieldName)) {
            val dataNode = stepResult.get(fieldName)
            println("type in getState: " + type)
            val objectNode = dataNode as ObjectNode
            val classNode = objectNode.remove("class")
            val klass = classNode.asText()

//            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
//            return mapper.treeToValue(objectNode, Class.forName(klass).javaClass)
            val jsonString = mapper.writeValueAsString(objectNode)
            return mapper.readValue(jsonString, Class.forName(klass)) as T
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
