package com.inngest

import com.beust.klaxon.Converter
import com.beust.klaxon.Json
import com.beust.klaxon.JsonValue
import com.beust.klaxon.KlaxonException
import java.time.Duration

// TODO: Throw illegal argument exception
class InngestFunctionConfigBuilder {
    var id: String? = null
    internal var name: String? = null
    private var triggers: MutableList<InngestFunctionTrigger> = mutableListOf()
    private var concurrency: MutableList<Concurrency>? = null
    private var retries = 3
    private var throttle: Throttle? = null
    private var rateLimit: RateLimit? = null
    private var debounce: Debounce? = null
    private var priority: Priority? = null
    private var idempotency: String? = null
    private var batchEvents: BatchEvents? = null

    /**
     * @param id A unique identifier for the function that should not change over time
     */
    fun id(id: String): InngestFunctionConfigBuilder {
        this.id = id
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
     *
     * @param event The name of the event to trigger on
     */
    fun triggerEvent(event: String): InngestFunctionConfigBuilder {
        this.triggers.add(InngestFunctionTriggers.Event(event, null))
        return this
    }

    /**
     * Define a function trigger for any matching events with a given name and filter
     * matching events with an expression statement.
     *
     * @param event The name of the event to trigger on
     * @param if    A CEL expression to filter matching events to trigger on.
     *              Example: "event.data.appId == '12345'"
     */
    @Suppress("unused")
    fun triggerEventIf(
        event: String,
        `if`: String? = null,
    ): InngestFunctionConfigBuilder {
        this.triggers.add(InngestFunctionTriggers.Event(event, `if`))
        return this
    }

    /**
     * @param cron A crontab expression
     */
    @Suppress("unused")
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
    fun batchEvents(
        maxSize: Int,
        timeout: Duration,
        key: String? = null,
    ): InngestFunctionConfigBuilder {
        this.batchEvents = BatchEvents(maxSize, timeout, key)
        return this
    }

    /**
     * Configure step concurrency limit
     *
     * @param limit Maximum number of concurrent executing steps across function type
     * @param key   A CEL expression to apply limit using event payload properties. Example: "event.data.destinationId"
     * @param scope The scope to apply the limit to. Options
     */
    fun concurrency(
        limit: Int,
        key: String? = null,
        scope: ConcurrencyScope? = null,
    ): InngestFunctionConfigBuilder {
        when (scope) {
            ConcurrencyScope.ENVIRONMENT ->
                if (key == null) {
                    throw InngestInvalidConfigurationException("Concurrency key required with environment scope")
                }
            ConcurrencyScope.ACCOUNT ->
                if (key == null) {
                    throw InngestInvalidConfigurationException("Concurrency key required with account scope")
                }
            ConcurrencyScope.FUNCTION -> {}
            null -> {}
        }

        val c = Concurrency(limit, key, scope)
        if (this.concurrency == null) {
            this.concurrency = mutableListOf(c)
        } else if (this.concurrency!!.size == 2) {
            throw InngestInvalidConfigurationException("Maximum of 2 concurrency options allowed")
        } else {
            this.concurrency!!.add(c)
        }
        return this
    }

    /**
     * Specifies the maximum number of retries for all steps across this function.
     *
     * @param attempts The number of times to retry a step before failing, defaults to 3.
     */
    fun retries(attempts: Int): InngestFunctionConfigBuilder = apply { this.retries = attempts }

    /**
     * Configure function throttle limit
     *
     * @param limit The total number of runs allowed to start within the given period. The limit is applied evenly over the period.
     * @param period The period of time for the rate limit. Run starts are evenly spaced through the given period.
     * The minimum granularity is 1 second.
     * @param key An optional expression which returns a throttling key for controlling throttling.
     * Every unique key is its own throttle limit. Event data may be used within this expression, eg "event.data.user_id".
     * @param burst The number of runs allowed to start in the given window in a single burst.
     * A burst > 1 bypasses smoothing for the burst and allows many runs to start at once, if desired. Defaults to 1, which disables bursting.
     */
    @JvmOverloads
    fun throttle(
        limit: Int,
        period: Duration,
        key: String? = null,
        burst: Int? = null,
    ): InngestFunctionConfigBuilder = apply { this.throttle = Throttle(limit, period, key, burst) }

    /**
     * Configure function rate limit
     *
     * @param limit The number of times to allow the function to run per the given `period`.
     * @param period The period of time to allow the function to run `limit` times. The period begins when the first matching event
     * is received
     * @param key An optional expression to use for rate limiting, similar to idempotency.
     */
    @JvmOverloads
    fun rateLimit(
        limit: Int,
        period: Duration,
        key: String? = null,
    ): InngestFunctionConfigBuilder = apply { this.rateLimit = RateLimit(limit, period, key) }

    /**
     * Debounce delays functions for the `period` specified. If an event is sent,
     * the function will not run until at least `period` has elapsed.
     *
     * If any new events are received that match the same debounce `key`, the
     * function is rescheduled for another `period` delay, and the triggering
     * event is replaced with the latest event received.
     *
     * See the [Debounce documentation](https://innge.st/debounce) for more
     * information.
     *
     * @param period The period of time to delay after receiving the last trigger to run the function.
     * @param key An optional key to use for debouncing.
     * @param timeout The maximum time that a debounce can be extended before running.
     * If events are continually received within the given period, a function
     * will always run after the given timeout period.
     */
    @JvmOverloads
    fun debounce(
        period: Duration,
        key: String? = null,
        timeout: Duration? = null,
    ): InngestFunctionConfigBuilder = apply { this.debounce = Debounce(period, key, timeout) }

    /**
     * Configure how the priority of a function run is decided when multiple
     * functions are triggered at the same time.
     *
     * @param run An expression to use to determine the priority of a function run. The
     * expression can return a number between `-600` and `600`, where `600`
     * declares that this run should be executed before any others enqueued in
     * the last 600 seconds (10 minutes), and `-600` declares that this run
     * should be executed after any others enqueued in the last 600 seconds.
     */
    fun priority(run: String): InngestFunctionConfigBuilder = apply { this.priority = Priority(run) }

    /**
     * Allow the specification of an idempotency key using event data. If
     * specified, this overrides the `rateLimit` object.
     *
     * @param idempotencyKey An expression using event payload data for a
     * unique string key for idempotency.
     */
    fun idempotency(idempotencyKey: String): InngestFunctionConfigBuilder = apply { this.idempotency = idempotencyKey }

    private fun buildSteps(serveUrl: String): Map<String, StepConfig> {
        val scheme = serveUrl.split("://")[0]
        return mapOf(
            "step" to
                StepConfig(
                    id = "step",
                    name = "step",
                    retries = mapOf("attempts" to this.retries),
                    runtime =
                        hashMapOf(
                            "type" to scheme,
                            "url" to "$serveUrl?fnId=$id&stepId=step",
                        ),
                ),
        )
    }

    internal fun build(
        appId: String,
        serverUrl: String,
    ): InternalFunctionConfig {
        if (id == null) {
            throw InngestInvalidConfigurationException("Function id must be configured via builder")
        }
        val globalId = String.format("%s-%s", appId, id)
        val config =
            InternalFunctionConfig(
                globalId,
                name ?: id,
                triggers,
                concurrency,
                throttle,
                rateLimit,
                debounce,
                priority,
                idempotency,
                batchEvents,
                steps = buildSteps(serverUrl),
            )
        return config
    }
}

class InngestInvalidConfigurationException(
    message: String,
) : Exception(message)

@Target(AnnotationTarget.FIELD)
annotation class KlaxonDuration

val durationConverter =
    object : Converter {
        override fun canConvert(cls: Class<*>): Boolean = cls == Duration::class.java

        // TODO Implement this - parse 30s into duration of seconds
        override fun fromJson(jv: JsonValue): Duration =
            throw KlaxonException("Duration parse not implemented: ${jv.string}")

        override fun toJson(value: Any): String = """"${(value as Duration).seconds}s""""
    }

@Target(AnnotationTarget.FIELD)
annotation class KlaxonConcurrencyScope

val concurrencyScopeConverter =
    object : Converter {
        override fun canConvert(cls: Class<*>): Boolean = cls.isEnum

        override fun fromJson(jv: JsonValue): ConcurrencyScope = enumValueOf<ConcurrencyScope>(jv.string!!)

        override fun toJson(value: Any): String = """"${(value as ConcurrencyScope).value}""""
    }

// TODO - Convert enum element to value, not name
enum class ConcurrencyScope(
    val value: String,
) {
    ACCOUNT("account"),
    ENVIRONMENT("env"),
    FUNCTION("fn"),
}

internal data class Concurrency
    @JvmOverloads
    constructor(
        val limit: Int,
        @Json(serializeNull = false)
        val key: String? = null,
        @Json(serializeNull = false)
        @KlaxonConcurrencyScope
        val scope: ConcurrencyScope? = null,
    )

internal data class Throttle
    @JvmOverloads
    constructor(
        val limit: Int,
        @KlaxonDuration
        val period: Duration,
        @Json(serializeNull = false)
        val key: String? = null,
        @Json(serializeNull = false)
        val burst: Int? = null,
    )

internal data class RateLimit
    @JvmOverloads
    constructor(
        val limit: Int,
        @KlaxonDuration
        val period: Duration,
        @Json(serializeNull = false)
        val key: String? = null,
    )

internal data class Debounce
    @JvmOverloads
    constructor(
        @KlaxonDuration
        val period: Duration,
        @Json(serializeNull = false)
        val key: String? = null,
        @Json(serializeNull = false)
        @KlaxonDuration
        val timeout: Duration? = null,
    )

internal data class Priority
    constructor(
        val run: String,
    )

internal data class BatchEvents
    @JvmOverloads
    constructor(
        val maxSize: Int,
        @KlaxonDuration
        val timeout: Duration,
        @Json(serializeNull = false) val key: String? = null,
    )
