package io.inngest.testserver

import io.inngest.CommHandler
import io.inngest.FunctionOptions
import io.inngest.FunctionTrigger
import io.inngest.InngestFunction
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.fasterxml.jackson.annotation.JsonProperty

data class IngestData(val message: String)

fun Application.module() {
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
                        ContentType.Application.Json.toString()
                    )
                    call.response.status(
                        HttpStatusCode(response.statusCode.code, response.statusCode.message)
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
    }
}

data class Result(
    @JsonProperty("sum")
    val sum: Int,
)

val fn =
    InngestFunction(
        FunctionOptions(
            id = "fn-id-slug",
            name = "My function!",
            triggers = arrayOf(FunctionTrigger(event = "user.signup"))
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
        val add: Int =
            step.run<Int>("step-abc") {
//                println("-> running step 2 :) " + res?.sum)
//                res?.sum.plus(100)
                99
            }
        step.run("last-step") { res.sum.times(add) ?: 0 }
        hashMapOf("message" to "cool - this finished running")
    }

val comm = CommHandler(functions = hashMapOf("fn-id-slug" to fn))

fun main() {

    var port = 8080

    println("Test server running on port " + port)

    embeddedServer(Netty, port, module = Application::module).start(wait = true)
}
