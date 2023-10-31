# Inngest Kotlin SDK

## 🚧 Currently in Alpha! Not production ready 🚧

An Inngest SDK for Kotlin with Java interoperability.

## Defining a function

<details open>
  <summary>Kotlin</summary>

```kotlin
package inngest.kotlin.app

val myFunction = InngestFunction(
        FunctionOptions(id = "fn-id-slug", name = "My function!"),
        mapOf("event" to "user.signup")
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

To build this in development, set up Java, Kotlin and Gradle locally and run `gradle run`.
