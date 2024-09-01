package com.inngest

import com.beust.klaxon.Json
import java.util.function.BiFunction

// TODO - Add an abstraction layer between the Function call response and the comm handler response
enum class OpCode {
    StepRun,
    StepError,
    Sleep,
    StepStateFailed, // TODO
    Step,
    WaitForEvent,
    InvokeFunction,

    // FUTURE:
//    StepNotFound,
}

enum class ResultStatusCode(
    val code: Int,
    val message: String,
) {
    StepComplete(206, "Step Complete"),
    StepError(206, "Step Error"),
    FunctionComplete(200, "Function Complete"),
    NonRetriableError(400, "Bad Request"),
    RetriableError(500, "Function Error"),
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
    val error: Exception? = null,
) : StepOp(id, name, op, statusCode)

data class StepOptions(
    override val id: String,
    override val name: String,
    override val op: OpCode,
    override val statusCode: ResultStatusCode,
    val opts: Map<String, String>?,
) : StepOp(id, name, op, statusCode)

data class StepOptionsInvoke(
    override val id: String,
    override val name: String,
    override val op: OpCode,
    override val statusCode: ResultStatusCode,
    val opts: Map<String, Any>?,
) : StepOp(id, name, op, statusCode)

data class StepConfig(
    val id: String,
    val name: String,
    val retries: Map<String, Int>,
    val runtime: HashMap<String, String> = hashMapOf("type" to "http"),
)

@Suppress("unused")
internal class InternalFunctionConfig
    @JvmOverloads
    constructor(
        val id: String,
        @Json(serializeNull = false)
        val name: String?,
        val triggers: MutableList<InngestFunctionTrigger> = mutableListOf(),
        @Json(serializeNull = false)
        val concurrency: MutableList<Concurrency>? = null,
        @Json(serializeNull = false)
        val throttle: Throttle? = null,
        @Json(serializeNull = false)
        val batchEvents: BatchEvents? = null,
        val steps: Map<String, StepConfig>,
    )
// NOTE - This should probably be called serialized or formatted config
// as it's only used to format the config for register requests
// typealias InternalFunctionConfig = Map<String, Any>

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

data class SendEventPayload(
    // TODO - Change this to camelCase and add Klaxon annotation to parse underscore name via API
    @Suppress("PropertyName")
    val event_ids: Array<String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SendEventPayload

        return event_ids.contentEquals(other.event_ids)
    }

    override fun hashCode(): Int = event_ids.contentHashCode()
}

internal interface Function {
    fun id(): String
}

// TODO: make this implement the Function interface

/**
 * An internal class that accepts the configuration and a function handler
 * and handles the execution and memoization of an InngestFunction
 */
internal open class InternalInngestFunction(
    private val configBuilder: InngestFunctionConfigBuilder,
    val handler: (ctx: FunctionContext, step: Step) -> Any?,
) {
    @Suppress("unused")
    constructor(
        configBuilder: InngestFunctionConfigBuilder,
        handler: BiFunction<FunctionContext, Step, out Any>,
    ) : this(
        configBuilder,
        handler.toKotlin(),
    )

    fun id() = configBuilder.id

    fun call(
        ctx: FunctionContext,
        client: Inngest,
        requestBody: String,
    ): StepOp {
        val state = State(requestBody)
        val step = Step(state, client)

        try {
            val data = handler(ctx, step)
            return StepResult(
                data = data,
                id = "",
                name = "",
                op = OpCode.StepRun,
                statusCode = ResultStatusCode.FunctionComplete,
            )
        } catch (e: StepInterruptSendEventException) {
            return StepResult(
                id = e.hashedId,
                name = e.id,
                op = OpCode.Step,
                statusCode = ResultStatusCode.StepComplete,
                data = SendEventPayload(e.eventIds),
            )
        } catch (e: StepInterruptWaitForEventException) {
            return StepOptions(
                id = e.hashedId,
                name = e.id,
                op = OpCode.WaitForEvent,
                statusCode = ResultStatusCode.StepComplete,
                opts =
                    buildMap {
                        put("event", e.waitEvent)
                        put("timeout", e.timeout)
                        if (e.ifExpression != null) {
                            put("if", e.ifExpression)
                        }
                    },
            )
        } catch (e: StepInterruptSleepException) {
            return StepOptions(
                opts = hashMapOf("duration" to e.data),
                id = e.hashedId,
                name = e.id,
                op = OpCode.Sleep,
                statusCode = ResultStatusCode.StepComplete,
            )
        } catch (e: StepInterruptInvokeException) {
            val functionId = String.format("%s-%s", e.appId, e.fnId)
            return StepOptionsInvoke(
                id = e.hashedId,
                name = e.id,
                op = OpCode.InvokeFunction,
                statusCode = ResultStatusCode.StepComplete,
                opts =
                    buildMap {
                        put("function_id", functionId)
                        put("payload", mapOf("data" to e.data))
                        if (e.timeout != null) {
                            put("timeout", e.timeout)
                        }
                    },
            )
        } catch (e: StepInterruptErrorException) {
            return StepResult(
                id = e.hashedId,
                name = e.id,
                op = OpCode.StepError,
                statusCode = ResultStatusCode.StepError,
                error = e.error,
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
                statusCode = ResultStatusCode.StepComplete,
            )
        } catch (e: StepInvalidStateTypeException) {
            // TODO - Handle this with the proper op code
            return StepResult(
                data = null,
                id = e.hashedId,
                name = e.id,
                op = OpCode.StepStateFailed,
                statusCode = ResultStatusCode.RetriableError,
            )
        }
    }

    fun getFunctionConfig(
        serveUrl: String,
        client: Inngest,
    ): InternalFunctionConfig {
        // TODO use URL objects for serveUrl instead of strings so we can fetch things like scheme
        return configBuilder.build(client.appId, serveUrl)
    }
}
