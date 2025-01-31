package com.inngest

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.inngest.signingkey.checkHeadersAndValidateSignature
import com.inngest.signingkey.getAuthorizationHeader
import com.inngest.signingkey.hashedSigningKey
import java.io.IOException
import java.security.MessageDigest

data class ExecutionRequestPayload(
    val ctx: ExecutionContext,
    val event: InngestEvent,
    val events: List<InngestEvent>,
    val steps: MemoizedState,
)

data class ExecutionContext(
    val attempt: Int,
    @Json(name = "fn_id") val fnId: String,
    @Json(name = "run_id") val runId: String,
    val env: String,
)

internal data class RegistrationRequestPayload
    @JvmOverloads
    constructor(
        val appName: String,
        val deployType: String = "ping",
        val framework: String,
        val functions: List<InternalFunctionConfig> = listOf(),
        val sdk: String,
        val url: String,
        val v: String,
    )

// enum class InngestSyncResult {
//    None,
// }

data class CommResponse(
    val body: String,
    val statusCode: ResultStatusCode,
    val headers: Map<String, String>,
)

data class CommError(
    val name: String,
    val message: String?,
    val stack: String?,
    // TODO - Convert to camelCase and use Klaxon property renaming for parsing/serialization
    @Suppress("PropertyName")
    val __serialized: Boolean = true,
)

private val stepTerminalStatusCodes = setOf(ResultStatusCode.StepComplete, ResultStatusCode.StepError)

private fun generateFailureFunctions(
    functions: Map<String, InngestFunction>,
    client: Inngest,
): Map<String, InternalInngestFunction> =
    functions
        .mapNotNull { (_, fn) ->
            fn.toFailureHandler(client.appId)?.let { it.id()!! to it }
        }.toMap()

class CommHandler(
    functions: Map<String, InngestFunction>,
    val client: Inngest,
    val config: ServeConfig,
    private val framework: SupportedFrameworkName,
) {
    val headers = Environment.inngestHeaders(framework).plus(client.headers)

    private val failureFunctions = generateFailureFunctions(functions, client)
    private val functions = functions.mapValues { (_, fn) -> fn.toInngestFunction() }.plus(failureFunctions)

    fun callFunction(
        functionId: String,
        requestBody: String,
    ): CommResponse {
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
            if (result.statusCode in stepTerminalStatusCodes || result is StepOptions) {
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
            val statusCode =
                if (retryDecision.shouldRetry) ResultStatusCode.RetriableError else ResultStatusCode.NonRetriableError

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
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        return mapper.writeValueAsString(requestBody)
    }

    private fun serializePayload(payload: Any?): String {
        try {
            return Klaxon()
                .fieldConverter(KlaxonDuration::class, durationConverter)
                .fieldConverter(KlaxonConcurrencyScope::class, concurrencyScopeConverter)
                .toJsonString(payload)
        } catch (e: Exception) {
            // TODO - Properly log this serialization failure
            println(e)
            return """{ "message": "failed serialization" }"""
        }
    }

    private fun getFunctionConfigs(origin: String): List<InternalFunctionConfig> {
        val configs: MutableList<InternalFunctionConfig> = mutableListOf()
        functions.forEach { entry -> configs.add(entry.value.getFunctionConfig(getServeUrl(origin), client)) }
        return configs
    }

    fun register(
        origin: String,
        syncId: String?,
    ): String {
        val registrationUrl = "${config.baseUrl()}/fn/register"
        val requestPayload = getRegistrationRequestPayload(origin)

        val httpClient = client.httpClient

        val signingKey = config.signingKey()
        val authorizationHeaderRequestConfig =
            if (config.client.env != InngestEnv.Dev) {
                RequestConfig(getAuthorizationHeader(signingKey))
            } else {
                null
            }

        val queryParams = syncId?.let { mapOf(InngestQueryParamKey.SyncId.value to it) } ?: emptyMap()

        val request = httpClient.build(registrationUrl, requestPayload, queryParams, authorizationHeaderRequestConfig)

        httpClient.send(request) { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
        }

        // TODO - Add headers to output
        val body: Map<String, Any?> = mapOf()
        return parseRequestBody(body)
    }

    // TODO
//    fun sync(): Result<InngestSyncResult> = Result.success(InngestSyncResult.None)

    fun introspect(
        signature: String?,
        requestBody: String,
        serverKind: String?,
    ): String {
        val insecureIntrospection =
            InsecureIntrospection(
                functionCount = functions.size,
                hasEventKey = Environment.isInngestEventKeySet(client.eventKey),
                hasSigningKey = config.hasSigningKey(),
                mode = if (client.env == InngestEnv.Dev) "dev" else "cloud",
            )

        val requestPayload =
            when (client.env) {
                InngestEnv.Dev -> insecureIntrospection

                else ->
                    runCatching {
                        checkHeadersAndValidateSignature(signature, requestBody, serverKind, config)

                        SecureIntrospection(
                            functionCount = functions.size,
                            hasEventKey = Environment.isInngestEventKeySet(client.eventKey),
                            hasSigningKey = config.hasSigningKey(),
                            authenticationSucceeded = true,
                            mode = "cloud",
                            env = client.env.value,
                            appId = config.appId(),
                            apiOrigin = "${config.baseUrl()}/",
                            framework = framework.value,
                            sdkVersion = Version.getVersion(),
                            sdkLanguage = "java",
                            servePath = config.servePath(),
                            serveOrigin = config.serveOrigin(),
                            signingKeyHash = hashedSigningKey(config.signingKey()),
                            eventApiOrigin = "${Environment.inngestEventApiBaseUrl(client.env)}/",
                            eventKeyHash = if (config.hasSigningKey()) hashedEventKey(client.eventKey) else null,
                        )
                    }.getOrElse {
                        insecureIntrospection.apply { authenticationSucceeded = false }
                    }
            }

        return serializePayload(requestPayload)
    }

    private fun getRegistrationRequestPayload(origin: String): RegistrationRequestPayload =
        RegistrationRequestPayload(
            appName = config.appId(),
            framework = framework.value,
            sdk = "java:v${Version.getVersion()}",
            url = getServeUrl(origin),
            v = "0.1",
            functions = getFunctionConfigs(origin),
        )

    private fun getServeUrl(origin: String): String {
        // TODO - property from SpringBoot should take preference to env variable?
        val serveOrigin = config.serveOrigin() ?: origin
        val servePath = config.servePath() ?: "/api/inngest"
        return "$serveOrigin$servePath"
    }

    private fun hashedEventKey(eventKey: String): String? =
        eventKey
            .takeIf { Environment.isInngestEventKeySet(it) }
            ?.let {
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(it.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }
}
