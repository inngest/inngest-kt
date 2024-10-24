package com.inngest.testserver

import com.fasterxml.jackson.annotation.JsonProperty
import com.inngest.*

/**
 * A demo function that accepts an event in a batch and invokes a child function
 */
class PoolItemNotAvailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

open class Point(
    @JsonProperty("x")
    open val x: Int,
    @JsonProperty("y")
    open val y: Int,
)

class ThreeDPoint(
    @JsonProperty("x")
    override val x: Int,
    @JsonProperty("y")
    override val y: Int,
    @JsonProperty("z")
    val z: Int,
) : Point(x, y)

class ProcessAlbum : InngestFunction() {
    override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
        builder
            .id("ProcessAlbum")
            .name("Process Album!")
            .triggerEvent("delivery/process.requested")
            .retries(0)

    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): LinkedHashMap<String, Any> {
//        val abc : Point = step.run("process-album") {
//            ThreeDPoint(1, 1, 3)
//        }

        val xyz =
            step.run("process-album") {
                ThreeDPoint(1, 1, 3)
                "42"
            }

//        println(xyz as ThreeDPoint)

        return linkedMapOf("hello" to xyz)
    }
}
