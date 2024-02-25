package com.inngest

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

data class RegistrationRequestPayload(
    val appName: String,
    val deployType: String = "ping",
    val framework: String,
    val functions: List<FunctionConfig> = listOf(),
    val sdk: String,
    val url: String,
    val v: String,
)

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

val jsonMediaType = "application/json".toMediaType()

class CommHandler(val functions: HashMap<String, InngestFunction>) {
    private fun getHeaders(): Map<String, String> {
        return mapOf(
            "Content-Type" to "application/json",
            // TODO - Get this from the build
            "x-inngest-sdk" to "inngest-kt:${Version.getVersion()}",
            // TODO - Pull this from options
            "x-inngest-framework" to "ktor",
        )
    }

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

            val result =
                function.call(
                    ctx = ctx,
                    requestBody,
                )
            var body: Any? = null
            if (result.statusCode == ResultStatusCode.StepComplete || result is StepOptions) {
                body = listOf(result)
            }
            if (result is StepResult && result.statusCode == ResultStatusCode.FunctionComplete) {
                body = result.data
            }
            return CommResponse(
                body = Klaxon().toJsonString(body),
                statusCode = result.statusCode,
                headers = getHeaders(),
            )
        } catch (e: Exception) {
            val err =
                CommError(
                    name = e.toString(),
                    message = e.message,
                    stack = e.stackTrace.joinToString(separator = "\n"),
                )
            return CommResponse(
                body = Klaxon().toJsonString(err),
                statusCode = ResultStatusCode.Error,
                headers = getHeaders(),
            )
        }
    }

    fun getFunctionConfigs(): List<FunctionConfig> {
        val configs: MutableList<FunctionConfig> = mutableListOf()
        functions.forEach { entry -> configs.add(entry.value.getConfig()) }
        return configs
    }

    companion object Client {
        inline fun <reified T> sendEvent(payload: Any): T? {
            val eventKey = "test"
            return send("http://localhost:8288/e/$eventKey", payload)
        }

        inline fun <reified T> send(
            url: String,
            payload: Any,
        ): T? {
            val jsonRequestBody = Klaxon().toJsonString(payload)
            val requestBody = jsonRequestBody.toRequestBody(jsonMediaType)

            val client = OkHttpClient()

            // TODO - Add missing headers
            val request =
                Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

            client.newCall(request).execute().use { response ->
                // TODO: Handle error case
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                if (Unit::class.java.isAssignableFrom(T::class.java)) {
                    return Unit as T
                }
                return Klaxon().parse<T>(response.body!!.charStream())
            }
        }
    }

    fun register(): String {
        // TODO - This should detect the dev server or use base url
        val registrationUrl = "http://localhost:8288/fn/register"
        val requestPayload =
            RegistrationRequestPayload(
                appName = "my-app",
                framework = "ktor",
                sdk = "kotlin",
                url = "http://localhost:8080/api/inngest",
                v = Version.getVersion(),
                functions = getFunctionConfigs(),
            )

        send<Unit>(registrationUrl, requestPayload)

        // TODO - Add headers to output
        val body: Map<String, Any?> = mapOf()
        return Klaxon().toJsonString(body)
    }

    fun introspect(): String {
        val requestPayload =
            RegistrationRequestPayload(
                appName = "my-app",
                framework = "ktor",
                sdk = "kotlin",
                url = "http://localhost:8080/api/inngest",
                v = Version.getVersion(),
                functions = getFunctionConfigs(),
            )
        return Klaxon().toJsonString(requestPayload)
    }
}
