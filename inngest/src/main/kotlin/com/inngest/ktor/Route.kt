package com.inngest.ktor

import com.inngest.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
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
            config = config,
            framework = SupportedFrameworkName.Ktor,
        )

    route(path) {
        get("") {
            val signature = call.request.headers[InngestHeaderKey.Signature.value]
            val serverKind = call.request.headers[InngestHeaderKey.ServerKind.value]

            val requestBody = call.receiveText()

            val resp = comm.introspect(signature, requestBody, serverKind)
            call.respondText(resp, ContentType.Application.Json, HttpStatusCode.OK)
        }

        post("") {
            val fnId = call.request.queryParameters["fnId"]
            if (fnId == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing fnId parameter")
            } else {
                val body = call.receiveText()
                try {
                    val response = comm.callFunction(fnId, body)
                    response.headers.forEach({ (k, v) -> call.response.header(k, v) })
                    call.response.status(
                        HttpStatusCode(response.statusCode.code, response.statusCode.message),
                    )
                    call.respondText(response.body)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.toString())
                }
            }
        }

        put("") {
            val syncId = call.request.queryParameters[InngestQueryParamKey.SyncId.value]

            val origin = getOrigin(call)
            val resp = comm.register(origin, syncId)
            call.respond(HttpStatusCode.OK, resp)
        }
    }
}

fun getOrigin(call: ApplicationCall): String {
    var origin = String.format("%s://%s", call.request.origin.scheme, call.request.origin.serverHost)
    if (call.request.origin.serverPort != 80 || call.request.origin.serverPort != 443) {
        origin = String.format("%s:%s", origin, call.request.origin.serverPort)
    }
    return origin
}
