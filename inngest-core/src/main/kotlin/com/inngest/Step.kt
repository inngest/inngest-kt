package com.inngest

import java.time.Duration

typealias MemoizedRecord = HashMap<String, Any>
typealias MemoizedState = HashMap<String, MemoizedRecord>

data class InngestEvent(val name: String, val data: Any)

data class SendEventsResponse(val ids: Array<String>)

class StepInvalidStateTypeException(val id: String, val hashedId: String) : Throwable("Step execution interrupted")

class StepStateTypeMismatchException(val id: String, val hashedId: String) : Throwable("Step execution interrupted")

open class StepInterruptException(val id: String, val hashedId: String, open val data: kotlin.Any?) :
    Throwable("Interrupt $id")

class StepInterruptSleepException(id: String, hashedId: String, override val data: String) :
    StepInterruptException(id, hashedId, data)

class StepInterruptSendEventException(id: String, hashedId: String, val eventIds: Array<String>) :
    StepInterruptException(id, hashedId, eventIds)

class StepInterruptWaitForEventException(
    id: String,
    hashedId: String,
    val waitEvent: String,
    val timeout: String,
    val ifExpression: String?,
) :
    StepInterruptException(id, hashedId, null)

// TODO: Add name, stack, etc. if poss
class StepError(message: String) : Exception(message)

class Step(val state: State, val client: Inngest) {
    /**
     * Run a function
     *
     * @param id unique step id for memoization
     * @param fn the function to run
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
            val stepResult = state.getState(hashedId, type)
            if (stepResult != null) {
                return stepResult
            }
        } catch (e: StateNotFound) {
            // If there is no existing result, run the lambda
            val data = fn()
            throw StepInterruptException(id, hashedId, data)
        }
        // TODO - Catch Step Error here and throw it when error parsing is added to getState

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
            val durationInSeconds = duration.getSeconds()
            throw StepInterruptSleepException(id, hashedId, "${durationInSeconds}s")
        }
    }

    /**
     * Sends multiple events to Inngest
     *
     * @param id Unique step id for memoization.
     * @param event An event payload object.
     *
     */

    fun sendEvent(
        id: String,
        event: InngestEvent,
    ) = sendEvent(id, arrayOf(event))

    /**
     * Sends a single event to Inngest
     *
     * @param id Unique step id for memoization.
     * @param events An array of event payload objects.
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
            val response = client.send<SendEventsResponse>(events)
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
