package com.inngest.testserver

import com.inngest.FunctionContext
import com.inngest.Inngest
import com.inngest.InngestFunction
import com.inngest.InngestFunctionConfigBuilder
import com.inngest.Step
import com.inngest.connect.Connect
import com.inngest.connect.ConnectApp
import com.inngest.connect.ConnectOptions

/**
 * A two-step function exercised over the connect transport: step memoization
 * requires multiple executor round-trips per run.
 */
class ConnectGreeter : InngestFunction() {
    override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
        builder
            .id("connect-greeter")
            .name("Connect Greeter")
            .triggerEvent("connect/hello")
            .retries(0)

    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): Any {
        val greeting = step.run("build-greeting") { "hello" }
        val audience = step.run("build-audience") { "world" }
        return mapOf("message" to "$greeting $audience")
    }
}

/**
 * Development worker serving functions over Inngest Connect (WebSocket)
 * instead of HTTP. Run with `make dev-connect` alongside `make inngest-dev`.
 */
fun main() {
    val client = Inngest(appId = "connect-dev")

    val connection =
        Connect.start(
            ConnectOptions
                .builder(
                    listOf(
                        ConnectApp(
                            client = client,
                            functions = listOf(ConnectGreeter()),
                        ),
                    ),
                ).instanceId("connect-dev-worker")
                .build(),
        )

    println("Connect worker ready: state=${connection.state} connectionId=${connection.connectionId}")
    connection.awaitClosed()
    println("Connect worker closed")
}
