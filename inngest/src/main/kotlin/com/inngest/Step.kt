package com.inngest

import java.time.Duration

typealias MemoizedRecord = HashMap<String, Any>
typealias MemoizedState = HashMap<String, MemoizedRecord>

data class InngestEvent(
    val name: String,
    val data: Any,
)

data class SendEventsResponse(
    val ids: Array<String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SendEventsResponse

        return ids.contentEquals(other.ids)
    }

    override fun hashCode(): Int = ids.contentHashCode()
}

class StepInvalidStateTypeException(
    val id: String,
    val hashedId: String,
) : Throwable("Step execution interrupted")

// TODO - Add State type mismatch checks
// class StepStateTypeMismatchException(
//    val id: String,
//    val hashedId: String,
// ) : Throwable("Step execution interrupted")

open class StepInterruptException(
    val id: String,
    val hashedId: String,
    open val data: Any?,
) : Throwable("Interrupt $id")

class StepInterruptSleepException(
    id: String,
    hashedId: String,
    override val data: String,
) : StepInterruptException(id, hashedId, data)

class StepInterruptSendEventException(
    id: String,
    hashedId: String,
    val eventIds: Array<String>,
) : StepInterruptException(id, hashedId, eventIds)

class StepInterruptInvokeException(
    id: String,
    hashedId: String,
    val appId: String,
    val fnId: String,
    data: Any?,
    val timeout: String?,
) : StepInterruptException(id, hashedId, data)

class StepInterruptWaitForEventException(
    id: String,
    hashedId: String,
    val waitEvent: String,
    val timeout: String,
    val ifExpression: String?,
) : StepInterruptException(id, hashedId, null)

class StepInterruptErrorException(
    id: String,
    hashedId: String,
    val error: Exception,
) : StepInterruptException(id, hashedId, null)

class Step(
    private val state: State,
    val client: Inngest,
) {
    /**
     * Run a function
     *
     * @param id unique step id for memoization
     * @param fn the function to run
     *
     * @exception StepError if the function throws an [Exception].
     */
    inline fun <reified T> run(
        id: String,
        noinline fn: () -> T,
    ): T = run(id, fn, T::class.java)

    fun <T> run(
        id: String,
        fn: () -> T,
        type: Class<T>,
    ): T {
        val hashedId = state.getHashFromId(id)

        try {
            return state.getState(hashedId, type) as T
        } catch (e: StateNotFound) {
            // If there is no existing result, run the lambda
            executeStep(id, hashedId, fn)
        } catch (e: StepError) {
            throw e
        }

        // TODO - handle invalidly stored step types properly
        throw Exception("step state incorrect type")
    }

    private fun <T> executeStep(
        id: String,
        hashedId: String,
        fn: () -> T,
    ) {
        try {
            val data = fn()
            throw StepInterruptException(id, hashedId, data)
        } catch (exception: Exception) {
            when (exception) {
                is RetryAfterError,
                is NonRetriableError,
                -> throw exception

                else -> throw StepInterruptErrorException(id, hashedId, exception)
            }
        }
    }

    /**
     * Invoke another Inngest function as a step
     *
     * @param id unique step id for memoization
     * @param appId ID of the Inngest app which contains the function to invoke (see client)
     * @param fnId ID of the function to invoke
     * @param data the data to pass within `event.data` to the function
     * @param timeout an optional timeout for the invoked function.  If the invoked function does
     * not finish within this time, the invoked function will be marked as failed.
     *
     * @exception StepError if the invoked function fails.
     */
    inline fun <reified T> invoke(
        id: String,
        appId: String,
        fnId: String,
        data: Any?,
        timeout: String? = null,
    ): T = invoke(id, appId, fnId, data, timeout, T::class.java)

    fun <T> invoke(
        id: String,
        appId: String,
        fnId: String,
        data: Any?,
        timeout: String?,
        type: Class<T>,
    ): T {
        val hashedId = state.getHashFromId(id)
        try {
            val stepResult = state.getState(hashedId, type)
            if (stepResult != null) {
                return stepResult
            }
        } catch (e: StateNotFound) {
            throw StepInterruptInvokeException(id, hashedId, appId, fnId, data, timeout)
        } catch (e: StepError) {
            throw e
        }

        // TODO - handle invalidly stored step types properly
        throw Exception("step state incorrect type")
    }

    /**
     * Sleep for a specific duration
     *
     * @param id unique step id for memoization
     * @param duration the duration of time to sleep for
     */
    fun sleep(
        id: String,
        duration: Duration,
    ) {
        val hashedId = state.getHashFromId(id)

        try {
            // If this doesn't throw an error, it's null and that's what is expected
            val stepState = state.getState<Any?>(hashedId)
            if (stepState != null) {
                throw Exception("step state expected sleep, got something else")
            }
            return
        } catch (e: StateNotFound) {
            val durationInSeconds = duration.seconds
            throw StepInterruptSleepException(id, hashedId, "${durationInSeconds}s")
        }
    }

    /**
     * Sends a single event to Inngest.
     *
     * @param id Unique step id for memoization.
     * @param event The event to send.
     *
     */
    fun sendEvent(
        id: String,
        event: InngestEvent,
    ) = sendEvent(id, arrayOf(event))

    /**
     * Sends multiple events to Inngest.
     *
     * @param id Unique step id for memoization.
     * @param events The events to send.
     *
     */
    fun sendEvent(
        id: String,
        events: Array<InngestEvent>,
    ): SendEventsResponse {
        val hashedId = state.getHashFromId(id)

        try {
            val stepState = state.getState<Array<String>>(hashedId, "event_ids")

            if (stepState != null) {
                return SendEventsResponse(stepState)
            }
            throw Exception("step state expected sendEvent, got something else")
        } catch (e: StateNotFound) {
            val response = client.send(events)
            throw StepInterruptSendEventException(id, hashedId, response!!.ids)
        }
    }

    /**
     * Waits for an event with the name provided in `waitEvent`, optionally check for a condition
     * specified in `ifExpression`
     *
     * @param id Unique step id for memoization.
     * @param waitEvent The name of the event we want the function to wait for
     * @param timeout The amount of time to wait to receive an event. A time string compatible with https://www.npmjs.com/package/ms
     * @param ifExpression An expression on which to conditionally match the original event trigger (`event`) and the wait event (`async`).
     *        Expressions are defined using the Common Expression Language (CEL) with the events accessible using dot-notation.
     *
     */
    fun waitForEvent(
        id: String,
        waitEvent: String,
        timeout: String,
        ifExpression: String?,
        // TODO use better types for timeout and ifExpression that serialize to the relevant strings we send to the inngest server, instead of using raw strings
        // TODO support `match` which is a convenience for checking the same expression in `event` and `async`. Also make it a mutually exclusive argument with
        // ifExpression, possibly with a sealed class?
    ): Any? {
        val hashedId = state.getHashFromId(id)

        try {
            val stepResult = state.getState<Any?>(hashedId)
            if (stepResult != null) {
                return stepResult
            }
            return null // TODO should this throw an exception? also look into `EventPayload` https://github.com/inngest/inngest-kt/pull/26#discussion_r150176713
        } catch (e: StateNotFound) {
            throw StepInterruptWaitForEventException(id, hashedId, waitEvent, timeout, ifExpression)
        }
    }
}
