package com.inngest

import com.beust.klaxon.Converter
import com.beust.klaxon.Json
import com.beust.klaxon.JsonValue
import com.beust.klaxon.KlaxonException
import java.time.Duration

@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class FunctionConfig(
    val id: String,
    val name: String,
)

@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class FunctionEventTrigger(
    @Json(serializeNull = false) val event: String,
)

@Target(AnnotationTarget.CLASS)
@Repeatable
@MustBeDocumented
annotation class FunctionCronTrigger(
    @Json(serializeNull = false) val cron: String,
)

@Target(AnnotationTarget.CLASS)
@Repeatable
@MustBeDocumented
annotation class FunctionIfTrigger(
    @Json(serializeNull = false) val `if`: String,
)


abstract class InngestFunction {

//    abstract val id: String;

    // TODO: Throw illegal argument exception
    class Builder {
        private var name: String? = null;
        private var triggers: MutableList<InngestFunctionTrigger> = mutableListOf();
        private var batchEvents: BatchEvents? = null;

        fun name(name: String): Builder {
            this.name = name
            return this
        }

        fun trigger(trigger: InngestFunctionTrigger): Builder {
            // TODO - Check max triggers
            // TODO - Check mutually exclusive opts (cron v. event+if?)
            this.triggers.add(trigger)
            return this
        }

        fun batchEvents(maxSize: Int, timeout: Duration, key: String? = null): Builder {
            this.batchEvents = BatchEvents(maxSize, timeout, key)
            return this;
        }

        private fun buildSteps(): Map<String, StepConfig> {
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
//                            "type" to scheme,
//                            // TODO - Create correct URL
//                            "url" to
//                                "$serveUrl?fnId=${config.id}&stepId=step",
                        ),
                    ),
            )
        }

        fun build(id: String): InternalFunctionConfig {
            val config = InternalFunctionConfig(
                id,
                name,
                triggers,
                batchEvents,
                steps = buildSteps()
            )
            return config
        }
    }

    open fun config(builder: Builder): Builder {
        return builder
    }

    abstract fun execute(
        ctx: FunctionContext,
        step: Step,
    ): Any?

    private val config = this::class.annotations.find { it.annotationClass == FunctionConfig::class }

    fun id(): String {
        if (config == null || config !is FunctionConfig) {
            throw Exception("InngestFuncConfig annotation is required to setup an InngestFunc")
        }
        return config.id
    }

    internal fun toInngestFunction(): InternalInngestFunction {
//        if (config == null || config !is FunctionConfig) {
//            throw Exception("FunctionConfig annotation is required to setup an InngestFunction")
//        }
        val triggers = buildEventTriggers() + buildCronTriggers() + buildIfTriggers()
        val builder = Builder()
        val config = this.config(builder).build(id())
        return InternalInngestFunction(config, this::execute)
    }

    // TODO: DRY this
    private fun buildEventTriggers(): List<InternalFunctionTrigger> =
        this::class.annotations.filter { it.annotationClass == FunctionEventTrigger::class }
            .map { InternalFunctionTrigger(event = (it as FunctionEventTrigger).event) }

    private fun buildCronTriggers(): List<InternalFunctionTrigger> =
        this::class.annotations.filter { it.annotationClass == FunctionCronTrigger::class }
            .map { InternalFunctionTrigger(cron = (it as FunctionCronTrigger).cron) }

    private fun buildIfTriggers(): List<InternalFunctionTrigger> =
        this::class.annotations.filter { it.annotationClass == FunctionIfTrigger::class }
            .map { InternalFunctionTrigger(event = (it as FunctionIfTrigger).`if`) }
}

data class InngestFunctionTrigger
@JvmOverloads
constructor(
    @Json(serializeNull = false) val event: String? = null,
    @Json(name = "expression", serializeNull = false) val `if`: String? = null,
    @Json(serializeNull = false) val cron: String? = null,
)

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
