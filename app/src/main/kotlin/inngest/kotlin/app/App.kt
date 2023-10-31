package inngest.kotlin.app

// fun main() {

//     println("start")

//     // This fakes out the executor generator loop
//     // val result = fn.call(event = evt, state = state)
//     // var newState = HashMap(state)
//     // newState.put(result.id, result.data)

//     // println(newState)

//     // println("===== NEXT EXECUTION =====")
//     // val result2 = fn.call(event = evt, state = newState)
//     // println("result2 " + result2)

//     println("===== COMM =====")
//     val comm = CommHandler(functions = hashMapOf("fn-id-slug" to fn))

//     val response = comm.callFunction("fn-id-slug")

//     println("response: " + response)
// }

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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

val fn =
        InngestFunction(
                FunctionOptions(id = "fn-id-slug", name = "My function!"),
                mapOf("event" to "user.signup")
                // NOTE - Should we just make the args always the events array so that there isn't a
                // required blank _ arg?
                ) { event, _, step, _ ->
            var x = 10

            println("-> handler called")

            var res: Int =
                    step.run("step-1") { ->
                        println("-> running step 1!! " + x)
                        // throw Exception("An error!")
                        x + 10
                    }
            var add: Int =
                    step.run("step-abc") {
                        println("-> running step 2 :) " + res)
                        res + 100
                    }
            step.run("last-step") { res * add }
            hashMapOf("message" to "cool - this finished running")
        }

val comm = CommHandler(functions = hashMapOf("fn-id-slug" to fn))

fun main() {

    // println("config: " + comm.getFunctionConfigs())

    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}
