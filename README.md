# Inngest Kotlin SDK

## 🚧 Currently in Alpha! Not production ready 🚧

An [Inngest](https://www.inngest.com) SDK for Kotlin with Java interoperability.

## Defining a function

<details open>
  <summary>Kotlin</summary>

```kotlin
import io.inngest.InngestFunction
import io.inngest.FunctionOptions
import io.inngest.FunctionTrigger

val myFunction = InngestFunction(
        FunctionOptions(id = "fn-id-slug", name = "My function!"),
        FunctionTrigger(event = "user.signup"),
    ) { event, _, step, _ ->
      var x = 10

      var res: Int =
              step.run("step-1") { ->
                  x + 10
              }
      var add: Int =
              step.run("step-abc") {
                  res + 100
              }
      step.run("last-step") { res * add }
      hashMapOf("message" to "success")
  }
```

</details>

<details>
  <summary>Java (Coming soon)</summary>

</details>

## Contributing [WIP]

To build this in development, set up Java, Kotlin and Gradle locally and run the test server:

```
gradle run inngest-test-server:run
```

This runs a `ktor` web server to test the SDK against the dev server.
