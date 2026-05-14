package com.inngest

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.inngest.signingkey.checkHeadersAndValidateSignature
import com.inngest.signingkey.getAuthorizationHeader
import com.inngest.signingkey.hashedSigningKey
import java.security.MessageDigest

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

data class SyncResponse(
    val body: String,
    val statusCode: Int,
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

private fun compositeFunctionId(
    appId: String,
    functionId: String,
): String = "$appId-$functionId"

private fun indexFunctions(
    functions: Map<String, InternalInngestFunction>,
    appId: String,
): Map<String, InternalInngestFunction> =
    functions
        .flatMap { (functionId, function) ->
            listOf(
                functionId to function,
                compositeFunctionId(appId, functionId) to function,
            )
        }.toMap()

class CommHandler(
    functions: Map<String, InngestFunction>,
    val client: Inngest,
    val config: ServeConfig,
    private val framework: SupportedFrameworkName,
) {
    val headers = Environment.inngestHeaders(framework).plus(client.headers)

    private val baseFunctions = functions.mapValues { (_, fn) -> fn.toInngestFunction() }
    private val failureFunctions = generateFailureFunctions(functions, client)
    private val allFunctions = baseFunctions.plus(failureFunctions)
    private val functionsById = indexFunctions(allFunctions, client.appId)

    fun callFunction(
        functionId: String,
        requestBody: String,
    ): CommResponse {
        try {
            val payload = Klaxon().parse<ExecutionRequestPayload>(requestBody)
            // TODO - check that payload is not null and throw error
            val function = functionsById[functionId] ?: throw Exception("Function not found")

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

            return CommResponse(
                body = parseRequestBody(commError(e)),
                statusCode = statusCode,
                headers = headers.plus(retryDecision.headers),
            )
        }
    }

    fun protocolErrorResponse(e: Throwable): CommResponse =
        CommResponse(
            body = parseRequestBody(commError(e)),
            statusCode = ResultStatusCode.RetriableError,
            headers = headers,
        )

    private fun parseRequestBody(requestBody: Any?): String {
        val mapper = ObjectMapper()
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        return mapper.writeValueAsString(requestBody)
    }

    private fun commError(e: Throwable): CommError =
        CommError(
            name = e.toString(),
            message = e.message,
            stack = e.stackTrace.joinToString(separator = "\n"),
        )

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
        allFunctions.forEach { entry -> configs.add(entry.value.getFunctionConfig(getServeUrl(origin), client)) }
        return configs
    }

    @JvmOverloads
    fun register(
        origin: String,
        syncId: String?,
        expectedServerKind: String? = null,
    ): SyncResponse {
        val registrationUrl = "${config.baseUrl()}/fn/register"
        val requestPayload = getRegistrationRequestPayload(origin)

        val httpClient = client.httpClient

        val signingKey = config.signingKey()
        val requestHeaders = mutableMapOf<String, String>()
        if (config.client.env != InngestEnv.Dev) {
            requestHeaders.putAll(getAuthorizationHeader(signingKey))
        }
        if (expectedServerKind != null) {
            requestHeaders[InngestHeaderKey.ExpectedServerKind.value] = expectedServerKind
        }

        val queryParams = syncId?.let { mapOf(InngestQueryParamKey.SyncId.value to it) } ?: emptyMap()
        val requestConfig = if (requestHeaders.isEmpty()) null else RequestConfig(requestHeaders)

        val request = httpClient.build(registrationUrl, requestPayload, queryParams, requestConfig)

        return httpClient.send(request) { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@send SyncResponse(
                    body =
                        parseRequestBody(
                            mapOf(
                                "message" to (syncFailureMessage(responseBody) ?: "Unexpected code $response"),
                                "modified" to false,
                            ),
                        ),
                    statusCode = 500,
                    headers = headers,
                )
            }

            SyncResponse(
                body =
                    parseRequestBody(
                        mapOf(
                            "message" to "Successfully synced.",
                            "modified" to syncModified(responseBody),
                        ),
                    ),
                statusCode = 200,
                headers = headers,
            )
        }
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
                functionCount = allFunctions.size,
                hasEventKey = Environment.isInngestEventKeySet(client.eventKey),
                hasSigningKey = config.hasSigningKey(),
                mode = if (client.env == InngestEnv.Dev) "dev" else "cloud",
            )

        val requestPayload =
            when (client.env) {
                InngestEnv.Dev -> {
                    insecureIntrospection
                }

                else -> {
                    runCatching {
                        checkHeadersAndValidateSignature(signature, requestBody, serverKind, config)

                        SecureIntrospection(
                            functionCount = allFunctions.size,
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
            }

        return serializePayload(requestPayload)
    }

    private fun getRegistrationRequestPayload(origin: String): RegistrationRequestPayload =
        RegistrationRequestPayload(
            appName = config.appId(),
            framework = framework.value,
            sdk = Environment.inngestSdk(),
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

    private fun syncFailureMessage(responseBody: String): String? =
        runCatching {
            ObjectMapper()
                .readTree(responseBody)
                .path("error")
                .takeIf { !it.isMissingNode && !it.isNull }
                ?.asText()
        }.getOrNull()

    private fun syncModified(responseBody: String): Boolean =
        runCatching {
            ObjectMapper()
                .readTree(responseBody)
                .path("modified")
                .takeIf { !it.isMissingNode && !it.isNull }
                ?.asBoolean() ?: false
        }.getOrDefault(false)
}
