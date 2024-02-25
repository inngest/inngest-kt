package com.inngest.ktor

import com.inngest.Inngest
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.inngestRoutes(
    path: String,
    client: Inngest,
) {
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
