package com.inngest.ktor

import com.inngest.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.serve(
    path: String,
    client: Inngest,
    // TODO: should be using List<Function> instead
    fnList: List<InngestFunction>,
    id: String? = null,
    signingKey: String? = null,
    serveOrigin: String? = null,
    servePath: String? = null,
    // streaming: String = "false" // probably can't stream yet
    logLevel: String? = null,
    baseUrl: String? = null,
) {
    val config =
        ServeConfig(
            client = client,
            id = id,
            baseUrl = baseUrl,
            signingKey = signingKey,
            serveOrigin = serveOrigin,
            servePath = servePath,
            logLevel = logLevel,
        )

    val fnMap = fnList.associateBy { it.id() }
    val comm =
        CommHandler(
            functions = fnMap,
            client = client,
            framework = SupportedFrameworkName.Ktor,
        )

    route(path) {
        get("") {
            val resp = comm.introspect()
            call.respond(HttpStatusCode.OK, resp)
        }

        post("") {
            val fnId = call.request.queryParameters["fnId"]
            if (fnId == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing fnId parameter")
            } else {
                val body = call.receiveText()
                try {
                    val response = comm.callFunction(fnId, body)
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

            call.respondText("Invoke functions")
        }

        put("") {
            val resp = comm.register()
            call.respond(HttpStatusCode.OK, resp)
        }
    }
}
