# Contributing

To build this in development, set up Java, Kotlin and Gradle locally and run the test server:

```
make dev-ktor
```

This runs a `ktor` web server to test the SDK against the dev server.

To run the `spring-boot` test server:

```
make dev-spring-boot
```

## Spring compatibility tests

The published Spring adapter always compiles against Spring Boot 2.7.18 with
Java 8 APIs and bytecode. Consumer versions must not change that artifact.
There are two complementary checks:

- `make test-springboot-adapter test-springboot-demo SPRING_BOOT_VERSION=4.0.6 TEST_JAVA_VERSION=21`
  compiles the framework-specific test sources (including MockMvc) against the
  selected Boot version. The demo also compiles against that version; the adapter
  itself stays at its release baseline.
- `make itest-spring-compatibility SPRING_BOOT_RUNTIME_VERSION=4.0.6 TEST_JAVA_VERSION=21`
  compiles the adapter, demo, and integration fixtures against Boot 2.7.18, then
  runs the existing dev-server integration suite on the selected consumer's
  runtime dependencies. Only the runtime BOM and test JVM vary, not compilation.

The runtime check requires the `inngest` CLI on `PATH` (for example, run it with
`nix develop -c make ...`) or `INNGEST_CLI_PATH` set to its executable. Boot 3/4
require Java 17 or newer; Boot 2 also runs on Java 8/11. Run Gradle itself on
Java 21 and use `TEST_JAVA_VERSION` to choose the test JVM. CI covers Boot
2.7.18 on Java 8/11/17/21 and Boot 3.5.14/4.0.6 on Java 17/21/25.

`SPRING_BOOT_VERSION` / `-PspringBootVersion` selects source-test dependencies;
`SPRING_BOOT_RUNTIME_VERSION` / `-PspringBootRuntimeVersion` selects only the
compatibility task's runtime graph. `compatibilityTest` rejects non-baseline
source versions and unsupported runtime JVMs. `make itest` remains available
for integration tests compiled against a chosen source version.

MockMvc request-builder signatures changed between Spring 5 and 7, so those
unit tests deliberately keep their source-version coverage. The dev-mode
introspection integration test uses real HTTP instead, preserving its assertions
while allowing the same compiled fixture to run on all three Boot majors.

## Releasing

Releases follow the same high-level flow as `inngest-rs`, adapted for package-prefixed tags:

1. Pushes to `main` automatically create or update a `release/next` PR.
2. That PR bumps each releasable package's `VERSION` file and regenerates its package changelog at `pkg/CHANGELOG.md`.
3. Merging the release PR creates the matching package tags, publishes any package versions not yet on Maven Central, and creates or updates the GitHub releases using `git-cliff` notes.

Release tags use the package-prefixed format, for example `inngest-0.2.2`, `inngest-ktor-adapter-0.2.1`, and `inngest-spring-boot-adapter-0.2.1`.
