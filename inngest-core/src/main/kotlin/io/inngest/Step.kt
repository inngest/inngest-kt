package io.inngest

import java.security.MessageDigest

class StepInvalidStateTypeException : Throwable("Step execution interrupted")

class StepInterruptException(val id: String, val hashedId: String, val data: kotlin.Any?) :
        Throwable("Interrupt $id") {}

class Step(private val memos: Map<String, kotlin.Any>) {

    fun _getHashedId(id: String): String {
        val bytes = id.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-1")
        val hashedBytes = digest.digest(bytes)
        val sb = StringBuilder()
        for (byte in hashedBytes) {
            sb.append(String.format("%02x", byte))
        }
        return sb.toString()
    }

    fun _getMemo(key: String): kotlin.Any? {
        if (!memos.containsKey(key)) {
            return null
        }
        return memos.get(key)
    }

    inline fun <reified T> run(id: String, action: () -> T): T {
        var hashedId = _getHashedId(id)
        val state = _getMemo(hashedId)

        if (state != null) {
            if (state is T) {
                println("State found for id $id - SKIPPING")
                return state
            } else {
                throw StepInvalidStateTypeException()
            }
        }

        var data = action()
        throw StepInterruptException(id, hashedId, data)
    }
}
