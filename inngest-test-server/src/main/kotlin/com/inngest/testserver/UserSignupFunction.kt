package com.inngest.testserver

import com.inngest.*
import java.time.Duration

class ProcessUserSignup : InngestFunction() {

    override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder {
        return builder.id("process-user-signup")
            .triggerEvent("user-signup")
    }

    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): HashMap<String, String> {
        val x = 10

        println("-> handler called " + ctx.event.name)

        val y =
            step.run<Int>("add-ten") { ->
                x + 10
            }

        val res =
            step.run<Result>("cast-to-type-add-ten") { ->
                println("-> running step 1!! " + x)
                // throw Exception("An error!")
                Result(
                    sum = y + 10,
                )
            }
        println("res" + res)

        step.waitForEvent("wait-for-hello", "hello", "10m", "event.data.hello == async.data.hello")

        val add: Int =
            step.run<Int>("add-one-hundred") {
                println("-> running step 2 :) " + res?.sum)
                res.sum + 100
            }

        step.sleep("wait-one-sec", Duration.ofSeconds(2))

        step.run("last-step") { res.sum.times(add) ?: 0 }

        step.sendEvent("followup-event-id", InngestEvent(FOLLOW_UP_EVENT_NAME, data = hashMapOf("hello" to "world")))

        return hashMapOf("message" to "cool - this finished running")
    }
}
