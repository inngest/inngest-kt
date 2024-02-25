package com.inngest.testserver

import com.fasterxml.jackson.annotation.JsonProperty
import com.inngest.CommHandler
import com.inngest.FunctionOptions
import com.inngest.FunctionTrigger
import com.inngest.Inngest
import com.inngest.InngestEvent
import com.inngest.InngestFunction
import com.inngest.ktor.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
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

val comm = CommHandler(functions = hashMapOf("fn-id-slug" to fn, "fn-follow-up" to fn2))

data class IngestData(val message: String)

fun Application.module() {
    var inngest = Inngest(appId = "ktor-dev")

    routing {
        post("/api/inngest") {
            val functionId = call.request.queryParameters["fnId"]
            if (functionId == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing fnId parameter")
            } else {
                val body = call.receiveText()
                try {
                    val response = comm.callFunction(functionId, body)
                    call.response.header(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString(),
                    )
                    call.response.status(
                        HttpStatusCode(response.statusCode.code, response.statusCode.message),
                    )
                    println("response: " + response.body)
                    call.respond(response.body)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.toString())
                }
            }
        }
        put("/api/inngest") {
            val response = comm.register()
            call.respond(HttpStatusCode.OK, response)
        }
        get("/api/inngest") {
            val response = comm.introspect()
            call.respond(HttpStatusCode.OK, response)
        }

        serve("/test", inngest, listOf(fn, fn2))
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
