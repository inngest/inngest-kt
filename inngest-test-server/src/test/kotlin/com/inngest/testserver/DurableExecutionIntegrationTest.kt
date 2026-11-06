package com.inngest.testserver

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.inngest.FunctionContext
import com.inngest.Inngest
import com.inngest.InngestEvent
import com.inngest.InngestFunction
import com.inngest.InngestFunctionConfigBuilder
import com.inngest.Step
import com.inngest.ktor.serve
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Tag("integration")
internal class DurableExecutionIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val mapper = ObjectMapper()

    @Test
    fun `dev server executes steps once across sleep and replay`() {
        val devPort = freePort()
        val devUrl = "http://127.0.0.1:$devPort"
        val client = Inngest("ktor-integration", baseUrl = devUrl, eventKey = "test", isDev = true)
        val function = DurableFunction()
        val server =
            embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                routing {
                    serve("/api/inngest", client, listOf(function), baseUrl = devUrl)
                }
            }
        val log = tempDir.resolve("inngest.log").toFile()
        var devServer: Process? = null

        try {
            server.start(wait = false)
            val appPort =
                runBlocking {
                    server.engine
                        .resolvedConnectors()
                        .single()
                        .port
                }
            val serveUrl = "http://127.0.0.1:$appPort/api/inngest"
            devServer =
                ProcessBuilder(
                    System.getenv("INNGEST_CLI_PATH") ?: "inngest",
                    "dev", "-u", serveUrl,
                    "--host", "127.0.0.1", "--port", devPort.toString(),
                    "--no-discovery", "--no-poll", "--retry-interval", "1",
                    "--connect-gateway-port", freePort().toString(),
                    "--connect-gateway-grpc-port", freePort().toString(),
                    "--connect-executor-grpc-port", freePort().toString(),
                ).redirectErrorStream(true).redirectOutput(log).start()

            await("dev server health") {
                assertTrue(devServer.isAlive, "Dev server exited before startup")
                runCatching { request("$devUrl/health").statusCode() == 200 }.getOrDefault(false)
            }

            val sync = request(serveUrl, "PUT")
            assertEquals(200, sync.statusCode(), sync.body())
            assertEquals("Successfully synced.", mapper.readTree(sync.body())["message"].asText())

            val sent = client.send(InngestEvent("test/ktor.durable", mapOf("value" to 11)))
            assertNotNull(sent)
            val eventId = sent!!.ids.single()
            var runId = ""
            await("run for event $eventId") {
                val runs = json("$devUrl/v1/events/$eventId/runs")["data"]
                if (runs.size() == 0) {
                    false
                } else {
                    assertEquals(1, runs.size())
                    runId = runs[0]["run_id"].asText()
                    true
                }
            }

            var completed: JsonNode? = null
            await("completed run $runId") {
                val run = json("$devUrl/v1/runs/$runId")["data"]
                assertTrue(run["status"].asText() !in listOf("Failed", "Cancelled"), run.toString())
                completed = run
                run["status"].asText() == "Completed"
            }

            assertEquals(eventId, completed!!["event_id"].asText())
            assertEquals(42, completed!!["output"].asInt())
            assertEquals(1, function.firstExecutions.get())
            assertEquals(1, function.secondExecutions.get())
            println("Completed event $eventId, run $runId: output=42, step executions=1/1")
        } catch (failure: Throwable) {
            if (log.exists()) System.err.println(log.readText())
            throw failure
        } finally {
            devServer?.let {
                it.destroy()
                if (!it.waitFor(5, TimeUnit.SECONDS)) {
                    it.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
                }
            }
            server.stop(100, 1000)
        }
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun request(
        url: String,
        method: String = "GET",
    ): HttpResponse<String> =
        http.send(
            HttpRequest
                .newBuilder(URI(url))
                .timeout(Duration.ofSeconds(5))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun json(url: String): JsonNode {
        val response = request(url)
        assertEquals(200, response.statusCode(), response.body())
        return mapper.readTree(response.body())
    }

    private fun await(
        description: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos()
        while (!condition()) {
            assertTrue(System.nanoTime() < deadline, "Timed out waiting for $description")
            Thread.sleep(100)
        }
    }

    private class DurableFunction : InngestFunction() {
        val firstExecutions = AtomicInteger()
        val secondExecutions = AtomicInteger()

        override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder.id("durable-round-trip").triggerEvent("test/ktor.durable")

        override fun execute(
            ctx: FunctionContext,
            step: Step,
        ): Any {
            val first =
                step.run<Int>("add-three") {
                    firstExecutions.incrementAndGet()
                    (ctx.event.data["value"] as Number).toInt() + 3
                }
            step.sleep("pause", Duration.ofSeconds(3))
            return step.run<Int>("multiply-three") {
                secondExecutions.incrementAndGet()
                first * 3
            }
        }
    }
}
