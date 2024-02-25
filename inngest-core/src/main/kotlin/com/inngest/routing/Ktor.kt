package com.inngest.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.inngest(path: String) {
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
