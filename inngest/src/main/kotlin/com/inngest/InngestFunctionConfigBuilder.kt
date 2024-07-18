package com.inngest

import com.beust.klaxon.Converter
import com.beust.klaxon.Json
import com.beust.klaxon.JsonValue
import com.beust.klaxon.KlaxonException
import java.time.Duration

// TODO: Throw illegal argument exception
class InngestFunctionConfigBuilder() {
    var id: String? = null;
    private var name: String? = null;
    private var triggers: MutableList<InngestFunctionTrigger> = mutableListOf();
    private var batchEvents: BatchEvents? = null;

    /**
     * @param id A unique identifier for the function that should not change over time
     */
    fun id(id: String): InngestFunctionConfigBuilder {
        this.id = id;
        return this
    }

    /**
     * @param name A formatted name for the function, visible in UIs and logs
     */
    fun name(name: String): InngestFunctionConfigBuilder {
        this.name = name
        return this
    }

    /**
     * Define a function trigger using a given InngestFunctionTrigger nested class constructor
     *
     * @param trigger An event or cron function trigger
     */
    fun trigger(trigger: InngestFunctionTrigger): InngestFunctionConfigBuilder {
        // TODO - Check max triggers
        // TODO - Check mutually exclusive opts (cron v. event+if?)
        this.triggers.add(trigger)
        return this
    }

    /**
     * Define a function trigger for any matching events with a given name.
     * Optionally filter matching events with an expression statement.
     *
     * @param event The name of the event to trigger on
     * @param if    A CEL expression to filter matching events to trigger on (optional).
     *              Example: "event.data.appId == '12345'"
     */
    fun triggerEvent(event: String, `if`: String? = null): InngestFunctionConfigBuilder {
        this.triggers.add(InngestFunctionTriggers.Event(event, `if`))
        return this
    }

    /**
     * @param cron A crontab expression
     */
    fun triggerCron(cron: String): InngestFunctionConfigBuilder {
        this.triggers.add(InngestFunctionTriggers.Cron(cron))
        return this
    }

    /**
     * Configure the function to be executed with batches of events (1 to n).
     * Events will be added into a batch until the maxSize has been reached or
     * until the timeout has expired. Any events in this batch will be passed
     * to the executing function.
     *
     * @param maxSize The maximum number of events to execute the function with
     * @param timeout The maximum duration of time to wait before executing the function
     * @param key     A CEL expression to group events batches by. Example: "event.data.destinationId"
     */
    fun batchEvents(maxSize: Int, timeout: Duration, key: String? = null): InngestFunctionConfigBuilder {
        this.batchEvents = BatchEvents(maxSize, timeout, key)
        return this;
    }

    private fun buildSteps(serveUrl: String): Map<String, StepConfig> {
        val scheme = serveUrl.split("://")[0]
        return mapOf(
            "step" to
                StepConfig(
                    id = "step",
                    name = "step",
                    retries =
                    mapOf(
                        // TODO - Pull from conf option
                        "attempts" to 3,
                    ),
                    runtime =
                    hashMapOf(
                        "type" to scheme,
                        "url" to "$serveUrl?fnId=${id}&stepId=step",
                    ),
                ),
        )
    }

    fun build(appId: String, serverUrl: String): InternalFunctionConfig {
        if (id == null) {
            throw InngestInvalidConfigurationException("Function id must be configured via builder")
        }
        val globalId = String.format("%s-%s", appId, id)
        val config = InternalFunctionConfig(
            globalId,
            name,
            triggers,
            batchEvents,
            steps = buildSteps(serverUrl)
        )
        return config
    }
}

@Target(AnnotationTarget.FIELD)
annotation class KlaxonDuration

val durationConverter = object : Converter {
    override fun canConvert(cls: Class<*>): Boolean = cls == Duration::class.java;

    // TODO Implement this - parse 30s into duration of seconds
    override fun fromJson(jv: JsonValue): Duration =
        throw KlaxonException("Duration parse not implemented: ${jv.string}")

    override fun toJson(value: Any): String = """"${(value as Duration).seconds}s""""
}

data class BatchEvents
@JvmOverloads
constructor(
    val maxSize: Int,
    @KlaxonDuration
    val timeout: Duration,
    @Json(serializeNull = false) val key: String? = null
)
