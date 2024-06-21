# Inngest Kotlin SDK

[Inngest](https://www.inngest.com) SDK for Kotlin with Java interoperability.

## Defining a function

<details open>
  <summary>Kotlin</summary>

```kotlin
import com.inngest.InngestFunction
import com.inngest.FunctionOptions
import com.inngest.FunctionTrigger

val myFunction = InngestFunction(
    FunctionOptions(
        id = "fn-id-slug",
        name = "My function!",
        triggers = arrayOf(FunctionTrigger(event = "user.signup")),
    ),
) { ctx, step ->
    val x = 10

    val res =
        step.run<Int>("add-ten") { ->
            x + 10
        }
    val add: Int =
        step.run("multiply-by-100") {
            res * 100
        }
    step.sleep("wait-one-minute", Duration.ofSeconds(60))

    step.run("last-step") { res * add }

    hashMapOf("message" to "success")
}
```

</details>

<details>
  <summary>Java (Coming soon)</summary>
</details>

## Declaring dependencies

WIP

## Contributing [WIP]

To build this in development, set up Java, Kotlin and Gradle locally and run the test server:

```
make dev-ktor
```

This runs a `ktor` web server to test the SDK against the dev server.


To run the `spring-boot` test server:

```
make dev-spring-boot
```
