package inngest.kotlin.app

// IDEA: Use data classes
data class FunctionOptions(
        val id: String,
        val name: String,
)

typealias FunctionTrigger = Map<String, Any>

typealias Context = Map<String, Any>

typealias MemoizedState = HashMap<String, Any>

// TODO - Add an abstraction layer between the Function call response and the comm handler response
enum class OpCode {
    Step,
    // FUTURE:
    WaitForEvent,
    Sleep,
    StepNotFound,
}

enum class ResultStatusCode(val code: Int, val message: String) {
    StepComplete(206, "Step Complete"),
    FunctionComplete(200, "Funciton Complete"),
    Error(500, "Function Error"),
}

data class StepResult(
        val data: Any? = null,
        // The hashed ID of a step
        val id: String = "",
        val name: String = "",
        val op: OpCode,
        val statusCode: ResultStatusCode,
)

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

class InngestFunction(
        val config: FunctionOptions,
        val trigger: FunctionTrigger,
        val handler: (event: Event, events: List<Event>, step: Step, ctx: Context) -> kotlin.Any?
) {
    fun call(event: Event, events: List<Event>, state: MemoizedState, ctx: Context): StepResult {
        val step = Step(state)
        try {
            val data = handler(event, events, step, ctx)
            println("FINAL RETURN: " + data)
            return StepResult(
                    data,
                    id = "",
                    name = "",
                    op = OpCode.Step,
                    statusCode = ResultStatusCode.FunctionComplete
            )
        } catch (e: StepInterruptException) {
            // NOTE - Currently this error could be caught in the user's own function that wraps a
            // step.run() - how can we prevent that or warn?
            return StepResult(
                    data = e.data,
                    id = e.hashedId,
                    name = e.id,
                    op = OpCode.Step,
                    statusCode = ResultStatusCode.StepComplete
            )
        }
    }

    fun getConfig(): FunctionConfig {
        return FunctionConfig(
                id = config.id,
                name = config.name,
                triggers = arrayOf(trigger),
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
