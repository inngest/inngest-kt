package com.inngest.ktor

import com.inngest.Inngest
import com.inngest.InngestFunction
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.serve(
    path: String,
    client: Inngest,
    // TODO: should be using List<Function> instead
    fnList: List<InngestFunction>,
) {
    val fnMap = fnList.associateBy { it.id() }
    print(fnMap)

    route(path) {
        get("") {
            call.respondText("Get server status")
        }

        post("") {
            call.respondText("Invoke functions")
        }

        put("") {
            call.respondText("Register app")
        }
    }
}
