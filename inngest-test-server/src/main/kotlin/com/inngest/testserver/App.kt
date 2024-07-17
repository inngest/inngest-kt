package com.inngest.testserver

import com.fasterxml.jackson.annotation.JsonProperty
import com.inngest.*
import com.inngest.ktor.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*

data class Result(
    @JsonProperty("sum")
    val sum: Int,
)

const val FOLLOW_UP_EVENT_NAME = "user.signup.completed"

data class IngestData(val message: String)

fun Application.module() {
    val inngest = Inngest(appId = "ktor-dev")

    routing {
        serve("/api/inngest", inngest, listOf(ProcessAlbum(), RestoreFromGlacier()))
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
