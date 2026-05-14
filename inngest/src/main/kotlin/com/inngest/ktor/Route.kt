package com.inngest.ktor

import com.inngest.*
import com.inngest.signingkey.checkHeadersAndValidateSignature
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
                val response = comm.protocolErrorResponse(IllegalArgumentException("Missing fnId parameter"))
                call.respondComm(response)
            } else {
                val body = call.receiveText()
                try {
                    val signature = call.request.headers[InngestHeaderKey.Signature.value]
                    val serverKind = call.request.headers[InngestHeaderKey.ServerKind.value]
                    checkHeadersAndValidateSignature(signature, body, serverKind, comm.config)

                    val response = comm.callFunction(fnId, body)
                    call.respondComm(response)
                } catch (e: Exception) {
                    val response = comm.protocolErrorResponse(e)
                    call.respondComm(response)
                }
            }
        }

        put("") {
            val syncId = call.request.queryParameters[InngestQueryParamKey.SyncId.value]
            val serverKind = call.request.headers[InngestHeaderKey.ServerKind.value]

            val origin = getOrigin(call)
            val response = comm.register(origin, syncId, serverKind)
            response.headers.forEach { (k, v) -> call.response.header(k, v) }
            call.respondText(response.body, ContentType.Application.Json, HttpStatusCode.fromValue(response.statusCode))
        }
    }
}

private suspend fun ApplicationCall.respondComm(response: CommResponse) {
    response.headers.forEach { (k, v) -> this.response.header(k, v) }
    respondText(
        response.body,
        ContentType.Application.Json,
        HttpStatusCode(response.statusCode.code, response.statusCode.message),
    )
}

val HTTP_PORTS = listOf(80, 443)

fun getOrigin(call: ApplicationCall): String {
    var origin = String.format("%s://%s", call.request.origin.scheme, call.request.origin.serverHost)
    if (call.request.origin.serverPort !in HTTP_PORTS) {
        origin = String.format("%s:%s", origin, call.request.origin.serverPort)
    }
    return origin
}
