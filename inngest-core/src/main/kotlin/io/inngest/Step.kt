package io.inngest

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.TypeAdapter
import com.beust.klaxon.TypeFor
import java.security.MessageDigest
import java.time.Duration
import kotlin.reflect.KClass

class MemoizedStepResult(
    val data: Any?,
    val error: SerializedError?,
) {
    fun isError(): Boolean {
        return error != null;
    }
}

//@TypeFor(field = "data", adapter = MemoAdapter::class)
open class Memo()
data class MemoizedStep(
    val data: Any,
) : Memo()

data class MemoizedStepError(
    val error: SerializedError,
) : Memo()

data class SerializedError(
    val name: String,
    val message: String,
    val stack: String?
)

//class MemoAdapter : TypeAdapter<Memo> {
//    override fun classFor(data: Any): KClass<out Memo> = when (data) {
//        null ->
//        "rectangle" -> MemoizedStep::class
//        "circle" -> MemoizedStepError::class
//        else -> throw IllegalArgumentException("Unknown type: $data")
//    }
//}


typealias MemoizedRecord = HashMap<String, Any>
typealias MemoizedState = HashMap<String, MemoizedRecord>

class StepInvalidStateTypeException(val id: String, val hashedId: String) : Throwable("Step execution interrupted")
class StepStateTypeMismatchException(val id: String, val hashedId: String) : Throwable("Step execution interrupted")

open class StepInterruptException(val id: String, val hashedId: String, open val data: kotlin.Any?) :
    Throwable("Interrupt $id") {}

class StepInterruptSleepException(id: String, hashedId: String, override val data: Duration) :
    StepInterruptException(id, hashedId, data) {}

// TODO: Add name, stack, etc. if poss
class StepError(message: String) : Exception(message)

class Step(val state: State) {

    /**
     * Run a function
     *
     * @param id unique step id for memoization
     * @param fn the function to run
     */
    inline fun <reified T> run(id: String, fn: () -> T): T {
        val hashedId = state.getHashFromId(id)

        // TODO - Catch Step Error here and throw it when error parsing is added to getState
        val stepResult = state.getState<T>(hashedId)

        // If there is no existing result, run the action
        if (stepResult == null) {
            val data = fn()
            throw StepInterruptException(id, hashedId, data)
        }

        return stepResult;
    }

    /**
     * Sleep for a specific duration
     *
     * @param id unique step id for memoization
     * @param duration the duration of time to sleep for
     */
    fun sleep(id: String, duration: Duration) {
        val hashedId = state.getHashFromId(id)
        // TODO - Use the proper value for the return type here
        val stepResult = state.getState<Any>(hashedId)

        if (stepResult != null) {
            println("State found for id $id - SKIPPING")
            return;
        }

        throw StepInterruptSleepException(id, hashedId, duration)
    }
}

