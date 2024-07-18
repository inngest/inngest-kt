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


    open fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder {
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
        val builder = InngestFunctionConfigBuilder()
        val configBuilder = this.config(builder)
        return InternalInngestFunction(configBuilder, this::execute)
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



