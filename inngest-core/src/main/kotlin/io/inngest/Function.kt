package io.inngest

import com.beust.klaxon.Json

// IDEA: Use data classes
data class FunctionOptions(
    val id: String,
    val name: String,
    val triggers: Array<FunctionTrigger>,
)

data class FunctionTrigger(
    @Json(serializeNull = false) val event: String? = null,
    @Json(serializeNull = false) val `if`: String? = null,
    @Json(serializeNull = false) val cron: String? = null,
)

// TODO - Add an abstraction layer between the Function call response and the comm handler response
enum class OpCode {
    StepRun,
    Sleep,
    StepStateFailed, // TODO

    // FUTURE:
    WaitForEvent,
    StepNotFound,

}

enum class ResultStatusCode(val code: Int, val message: String) {
    StepComplete(206, "Step Complete"),
    FunctionComplete(200, "Function Complete"),
    Error(500, "Function Error"),
}


abstract class StepOp(
    // The hashed ID of a step
    open val id: String = "",
    open val name: String = "",
    open val op: OpCode,
    open val statusCode: ResultStatusCode,
)

data class StepResult(
    override val id: String,
    override val name: String,
    override val op: OpCode,
    override val statusCode: ResultStatusCode,
    val data: Any? = null,
) : StepOp(id, name, op, statusCode)

data class StepOptions(
    override val id: String,
    override val name: String,
    override val op: OpCode,
    override val statusCode: ResultStatusCode,
    val opts: HashMap<String, String>? = null,
) : StepOp(id, name, op, statusCode)

data class StepConfig(
    val id: String,
    val name: String,
    val retries: Map<String, Int>,
    val runtime: HashMap<String, String> = hashMapOf("type" to "http"),
)

data class FunctionConfig(
    val id: String,
    val name: String,
    val triggers: Array<FunctionTrigger>,
    val steps: Map<String, StepConfig>
)

/**
 * The context for the current function run
 *
 * Includes event(s) and other run information
 */
data class FunctionContext(
    val event: Event,
    val events: List<Event>,
    val runId: String,
    val fnId: String,
    val attempt: Int,
)

// TODO - Determine if we should merge config + trigger
/**
 * A function that can be called by the Inngest system
 *
 * @param config The options for the function
 * @param handler The function to be called when the function is triggered
 */
open class InngestFunction(
    val config: FunctionOptions,
    val handler: (ctx: FunctionContext, step: Step) -> kotlin.Any?
) {
    // TODO - Validate options and trigger

    fun call(ctx: FunctionContext, requestBody: String): StepOp {
        val state = State(requestBody)
        val step = Step(state)

        // DEBUG
        println(state)

        try {
            val data = handler(ctx, step)
            return StepResult(
                data = data,
                id = "",
                name = "",
                op = OpCode.StepRun,
                statusCode = ResultStatusCode.FunctionComplete
            )
        } catch (e: StepInterruptSleepException) {
            return StepOptions(
                opts = hashMapOf("duration" to e.data),

                id = e.hashedId,
                name = e.id,
                op = OpCode.Sleep,
                statusCode = ResultStatusCode.StepComplete
            )
        } catch (e: StepInterruptException) {
            // NOTE - Currently this error could be caught in the user's own function
            // that wraps a
            // step.run() - how can we prevent that or warn?
            return StepResult(
                data = e.data,
                id = e.hashedId,
                name = e.id,
                op = OpCode.StepRun,
                statusCode = ResultStatusCode.StepComplete
            )
        } catch (e: StepInvalidStateTypeException) {
            // TODO - Handle this with the proper op code
            return StepResult(
                data = null,
                id = e.hashedId,
                name = e.id,
                op = OpCode.StepStateFailed,
                statusCode = ResultStatusCode.Error
            )
        }
    }

    fun getConfig(): FunctionConfig {
        return FunctionConfig(
            id = config.id,
            name = config.name,
            triggers = config.triggers,
            steps =
            mapOf(
                "step" to
                    StepConfig(
                        id = "step",
                        name = "step",
                        retries =
                        mapOf(
                            "attempts" to 3
                        ), // TODO - Pull from FunctionOptions
                        runtime =
                        hashMapOf(
                            "type" to "http",
                            // TODO - Create correct URL
                            "url" to
                                "http://localhost:8080/api/inngest?fnId=${config.id}&stepId=step"
                        )
                    )
            )
        )
    }
}
