package inngest.kotlin.app

import com.beust.klaxon.Klaxon
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class ExecutionRequestPayload(
    val ctx: HashMap<String, Any>,
    val event: Event,
    val events: List<Event>,
    val steps: HashMap<String, Any>,
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

class CommHandler(val functions: HashMap<String, InngestFunction>) {

  fun getHeaders(): Map<String, String> {
    return mapOf(
        "Content-Type" to "application/json",
        "x-inngest-sdk" to "inngest-kt:v0.0.1", // TODO - Get this from the build
        "x-inngest-framework" to "ktor", // TODO - Pull this from options
    )
  }

  fun callFunction(functionId: String, requestBody: String): CommResponse {
    val payload = Klaxon().parse<ExecutionRequestPayload>(requestBody)

    val function = functions.get(functionId)
    if (function == null) {
      throw Exception("Function not found")
    }

    try {
      val result =
          function.call(
              event = payload!!.event,
              events = payload.events,
              state = payload.steps,
              ctx = payload.ctx
          )
      var body: Any? = null
      if (result.statusCode == ResultStatusCode.StepComplete) {
        body = listOf(result)
      }
      if (result.statusCode == ResultStatusCode.FunctionComplete) {
        body = result.data
      }
      return CommResponse(
          body = Klaxon().toJsonString(body),
          statusCode = result.statusCode,
          headers = getHeaders()
      )
    } catch (e: Exception) {
      val err =
          CommError(
              name = e.toString(),
              message = e.message,
              stack = e.getStackTrace().joinToString(separator = "\n")
          )
      return CommResponse(
          body = Klaxon().toJsonString(err),
          statusCode = ResultStatusCode.Error,
          headers = getHeaders()
      )
    }
  }

  fun getFunctionConfigs(): List<FunctionConfig> {
    var configs: MutableList<FunctionConfig> = mutableListOf()
    functions.forEach { entry -> configs.add(entry.value.getConfig()) }
    return configs
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
            v = "0.0.1",
            functions = getFunctionConfigs()
        )
    val requestBody = Klaxon().toJsonString(requestPayload)

    val client = HttpClient.newBuilder().build()
    // TODO - Add headers?
    val request =
        HttpRequest.newBuilder()
            .uri(URI.create(registrationUrl))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    // TODO - Decode response and relay any message

    // TODO - Add headers to output
    val body: Map<String, kotlin.Any?> = mapOf()
    return Klaxon().toJsonString(body)
  }

  fun introspect(): String {
    val requestPayload =
        RegistrationRequestPayload(
            appName = "my-app",
            framework = "ktor",
            sdk = "kotlin",
            url = "http://localhost:8080/api/inngest",
            v = "0.0.1",
            functions = getFunctionConfigs()
        )
    return Klaxon().toJsonString(requestPayload)
  }
}
