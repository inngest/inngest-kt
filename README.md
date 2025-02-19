# Inngest Kotlin SDK

[![Maven Central Version - inngest](https://img.shields.io/maven-central/v/com.inngest/inngest?label=com.inngest%2Finngest)](https://central.sonatype.com/artifact/com.inngest/inngest)
[![Maven Central Version - inngest-spring-boot-adapter](https://img.shields.io/maven-central/v/com.inngest/inngest-spring-boot-adapter?label=com.inngest%2Finngest-spring-boot-adapter)](https://central.sonatype.com/artifact/com.inngest/inngest-spring-boot-adapter)
[![Discord](https://img.shields.io/discord/842170679536517141)](https://www.inngest.com/discord)

[Inngest](https://www.inngest.com) SDK for Kotlin with Java interoperability.

## Defining a function

<details open>
  <summary>Kotlin</summary>

```kotlin
class TranscodeVideo : InngestFunction() {
  override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
    builder
      .id("process-video")
      .name("Process video upload")
      .triggerEvent("media/video.uploaded")
      .retries(2)
      .concurrency(10)

  override fun execute(
    ctx: FunctionContext,
    step: Step,
  ): HashMap<String, Any> {
    val transcription =
      step.run("transcribe-video") {
        // Download video, run through transcription model, return output
        "Hi there, My name is Jamie..." // dummy example content
      }

    val summary =
      step.run("summarize") {
        // Send t
        "Hi there, My name is Jamie..." // dummy example content
      }

    step.run("save-results") {
      // Save summary, to your database
      // database.save(event.data["videoId"], transcription, summary)
    }

    return hashMapOf("restored" to false)
  }
}
```

</details>

<details>
  <summary>Java (Coming soon)</summary>
</details>

## Creating functions

Define your function's configuration using the `config` method and the `InngestFunctionConfigBuilder` class.
The `config` method must be overridden and an `id` is required. All options should are discoverable via
the builder class passed as the only argument to the `config` method.

<details open>
  <summary>Kotlin</summary>

```kotlin
import java.time.Duration

class TranscodeVideo : InngestFunction() {
  override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
    builder
      .id("process-video")
      .name("Process video upload")
      .triggerEvent("media/video.uploaded")
      .retries(2)
      .batchEvents(50, Duration.ofSeconds(30))
      .concurrency(10)
}
```

</details>

## Sending events (_triggering functions_)

<details open>
  <summary>Kotlin</summary>

```kotlin
import java.time.Duration

class TranscodeVideo : InngestFunction() {
  override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
    builder
      .id("process-video")
      .name("Process video upload")
      .triggerEvent("media/video.uploaded")
      .batchEvents(50, Duration.ofSeconds(30))
      .concurrency(10)
}
```

</details>

## Contributing

[See the contributing guide for more information](./CONTRIBUTING.md)
