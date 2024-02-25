package com.inngest.testserver

import com.fasterxml.jackson.annotation.JsonProperty
import com.inngest.*
import com.inngest.ktor.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import java.time.Duration

data class Result(
    @JsonProperty("sum")
    val sum: Int,
)

const val FOLLOW_UP_EVENT_NAME = "user.signup.completed"

val fn =
    InngestFunction(
        FunctionOptions(
            id = "fn-id-slug",
            name = "My function!",
            triggers = arrayOf(FunctionTrigger(event = "user.signup")),
        ),
    ) { ctx, step ->
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

        hashMapOf("message" to "cool - this finished running")
    }
val fn2 =
    InngestFunction(
        FunctionOptions(
            id = "fn-follow-up",
            name = "Follow up function!",
            triggers = arrayOf(FunctionTrigger(event = FOLLOW_UP_EVENT_NAME)),
        ),
    ) { ctx, _ ->
        println("-> followup fn called $ctx.event.name")

        ctx.event.data
    }

data class IngestData(val message: String)

fun Application.module() {
    val inngest = Inngest(appId = "ktor-dev")

    routing {
        serve("/api/inngest", inngest, listOf(fn, fn2))
    }
}

fun main() {
    var port = 8080

    println("Test server running on port " + port)

    embeddedServer(
        Netty,
        port,
        module = Application::module,
    ).start(wait = true)
}
