package com.inngest.testserver

import com.fasterxml.jackson.annotation.JsonProperty
import com.inngest.*
import com.inngest.ktor.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class Result(
    @JsonProperty("sum")
    val sum: Int,
)

const val FOLLOW_UP_EVENT_NAME = "user.signup.completed"

fun Application.module() {
    val inngest = Inngest(appId = "ktor-dev")

    routing {
        serve(
            "/api/inngest", inngest,
            listOf(
                ProcessAlbum(),
                RestoreFromGlacier(),
                ProcessUserSignup(),
                TranscodeVideo(),
                ImageFromPrompt(),
                PushToSlackChannel(),
                WeeklyCleanup(),
            ),
        )
        get("/send") {
            println("Sending event...")
            try {
                val event =
                    InngestEvent(
                        "delivery/process.requested",
                        mapOf(
                            "albumId" to "3d345a93-57c0-478b-a8ff-4d5f6a7df3b0",
                            "title" to "The Teal Album",
                        ),
                    )
                val res = inngest.send(event)
                println(res)
                call.respondText(res?.ids.toString())
            } catch (e: Exception) {
                println(e)
                call.respondText(e.toString())
            }
        }
    }
}

fun main() {
    val port = 8080

    println("Test server running on port $port")

    embeddedServer(
        Netty,
        port,
        module = Application::module,
    ).start(wait = true)
}
