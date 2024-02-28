package com.inngest

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import com.fasterxml.jackson.databind.ObjectMapper
import com.inngest.signingkey.getAuthorizationHeader
import java.io.IOException

data class ExecutionRequestPayload(
    val ctx: ExecutionContext,
    val event: Event,
    val events: List<Event>,
    val steps: MemoizedState,
)

data class ExecutionContext(
    val attempt: Int,
    @Json(name = "fn_id") val fnId: String,
    @Json(name = "run_id") val runId: String,
    val env: String,
)

internal data class RegistrationRequestPayload(
    val appName: String,
    val deployType: String = "ping",
    val framework: String,
    val functions: List<InternalFunctionConfig> = listOf(),
    val sdk: String,
    val url: String,
    val v: String,
)

enum class InngestSyncResult {
    None,
}

data class CommResponse(
    val body: String,
    val statusCode: ResultStatusCode,
    val headers: Map<String, String>,
)

data class CommError(
    val name: String,
    val message: String?,
    val stack: String?,
    val __serialized: Boolean = true,
)

class CommHandler(
    functions: Map<String, InngestFunction>,
    val client: Inngest,
    val config: ServeConfig,
    private val framework: SupportedFrameworkName,
) {
    val headers = Environment.inngestHeaders(framework).plus(client.headers)
    internal val functions = functions.mapValues { (_, fn) -> fn.toInngestFunction() }

    fun callFunction(
        functionId: String,
        requestBody: String,
    ): CommResponse {
        println(requestBody)

        try {
            val payload = Klaxon().parse<ExecutionRequestPayload>(requestBody)
            // TODO - check that payload is not null and throw error
            val function = functions[functionId] ?: throw Exception("Function not found")

            val ctx =
                FunctionContext(
                    event = payload!!.event,
                    events = payload.events,
                    runId = payload.ctx.runId,
                    fnId = payload.ctx.fnId,
                    attempt = payload.ctx.attempt,
                )

            val result = function.call(ctx = ctx, client = client, requestBody)
            var body: Any? = null
            if (result.statusCode == ResultStatusCode.StepComplete || result is StepOptions) {
                body = listOf(result)
            }
            if (result is StepResult && result.statusCode == ResultStatusCode.FunctionComplete) {
                body = result.data
            }
            return CommResponse(
                body = parseRequestBody(body),
                statusCode = result.statusCode,
                headers = headers,
            )
        } catch (e: Exception) {
            val retryDecision = RetryDecision.fromException(e)
            val statusCode = if (retryDecision.shouldRetry) ResultStatusCode.RetriableError else ResultStatusCode.NonRetriableError

            val err =
                CommError(
                    name = e.toString(),
                    message = e.message,
                    stack = e.stackTrace.joinToString(separator = "\n"),
                )
            return CommResponse(
                body = parseRequestBody(err),
                statusCode = statusCode,
                headers = headers.plus(retryDecision.headers),
            )
        }
    }

    private fun parseRequestBody(requestBody: Any?): String {
        val mapper = ObjectMapper()
        return mapper.writeValueAsString(requestBody)
    }

    private fun getFunctionConfigs(): List<InternalFunctionConfig> {
        val configs: MutableList<InternalFunctionConfig> = mutableListOf()
        functions.forEach { entry -> configs.add(entry.value.getFunctionConfig(getServeUrl())) }
        return configs
    }

    fun register(): String {
        val registrationUrl = "${config.baseUrl()}/fn/register"
        val requestPayload = getRegistrationRequestPayload()

        val httpClient = client.httpClient

        val signingKey = config.signingKey()
        val authorizationHeaderRequestConfig =
            if (config.client.env != InngestEnv.Dev) {
                RequestConfig(getAuthorizationHeader(signingKey))
            } else {
                null
            }

        val request = httpClient.build(registrationUrl, requestPayload, authorizationHeaderRequestConfig)

        httpClient.send(request) { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
        }

        // TODO - Add headers to output
        val body: Map<String, Any?> = mapOf()
        return parseRequestBody(body)
    }

    fun sync(): Result<InngestSyncResult> {
        return Result.success(InngestSyncResult.None)
    }

    fun introspect(): String {
        val requestPayload = getRegistrationRequestPayload()
        return parseRequestBody(requestPayload)
    }

    private fun getRegistrationRequestPayload(): RegistrationRequestPayload {
        return RegistrationRequestPayload(
            appName = config.appId(),
            framework = framework.toString(),
            sdk = "inngest-kt",
            url = getServeUrl(),
            v = Version.getVersion(),
            functions = getFunctionConfigs(),
        )
    }

    private fun getServeUrl(): String {
        val serveOrigin = config.serveOrigin() ?: "http://localhost:8080"
        val servePath = config.servePath() ?: "/api/inngest"
        return "$serveOrigin$servePath"
    }
}
