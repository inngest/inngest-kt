package com.inngest

import com.beust.klaxon.Json

/**
 * A generic class for defining and serializing function triggers
 */
abstract class InngestFunctionTrigger // or interface or data class
@JvmOverloads
constructor(
    @Json(serializeNull = false) val event: String? = null,
    @Json(serializeNull = false) val `if`: String? = null,
    @Json(serializeNull = false) val cron: String? = null,
    // IDEA - Add timeout and re-use for cancelOn?
)

/**
 * A class that contains nested classes to define function triggers
 */
class InngestFunctionTriggers {

    /**
     * Define a function trigger for any matching events with a given name.
     * Optionally filter matching events with an expression statement.
     *
     * @param event The name of the event to trigger on
     * @param if    A CEL expression to filter matching events to trigger on (optional).
     *              Example: "event.data.appId == '12345'"
     */
    class Event(event: String, `if`: String? = null) : InngestFunctionTrigger(event, `if`, null) {}

    /**
     * Define a function trigger that will execute on a given crontab schedule.
     *
     * @param cron A crontab expression. Example: "0 9 * * 1"
     */
    class Cron(cron: String) : InngestFunctionTrigger(null, null, cron) {}

}
