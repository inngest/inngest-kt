package com.inngest.testserver

import com.inngest.*
import java.time.Duration

/**
 * A demo function that accepts an event in a batch and invokes a child function
 */
class PoolItemNotAvailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

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
        try {
            step.run<String>("process-album") {
                throw PoolItemNotAvailableException("pool1", null)
                "result"
            }

        } catch (e: Exception) { // throws UnrecognizedPropertyException
//        } catch (e: StepError) { // this should successfully deserialize the exception
            step.run("handle-error") {
                e
            }
        }
        return linkedMapOf("hello" to true)
    }
}
