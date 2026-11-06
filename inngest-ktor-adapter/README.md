# inngest-ktor-adapter

[![Maven Central](https://img.shields.io/maven-central/v/com.inngest/inngest-ktor-adapter)](https://central.sonatype.com/artifact/com.inngest/inngest-ktor-adapter)

Serve [Inngest](https://www.inngest.com) functions from a [Ktor](https://ktor.io/) application.

## Requirements

Requires Ktor 3; tested with Ktor 3.6.1. For Ktor 2 applications, use adapter 0.2.1.

## Installation

Add the adapter to your Gradle dependencies, with `inngestVersion` set to the
adapter version you want to use:

```kotlin
dependencies {
    implementation("com.inngest:inngest-ktor-adapter:$inngestVersion")
}
```

## Usage

[Define your functions](../README.md#defining-a-function), then register them in
your Ktor application. Using the `TranscodeVideo` function from the SDK example:

```kotlin
import com.inngest.Inngest
import com.inngest.ktor.serve
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.module() {
    val inngest = Inngest("my-app")
    routing {
        serve("/api/inngest", inngest, listOf(TranscodeVideo()))
    }
}
```

For local development, run your application with `INNGEST_DEV=1` and connect the
[Inngest dev server](https://www.inngest.com/docs/local-development):

```sh
inngest dev -u http://127.0.0.1:8080/api/inngest
```

In production, configure `INNGEST_EVENT_KEY` and `INNGEST_SIGNING_KEY` and set
`INNGEST_DEV=0` to disable dev mode and require signed execution requests.

## Contributing

See the [contributing guide](../CONTRIBUTING.md) for development and test commands.
