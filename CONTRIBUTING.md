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

## Testing Ktor

Run the Ktor adapter and test-server unit test targets from the repository root:

```sh
make test-ktor
```

Run the Netty integration test against the Inngest dev server:

```sh
nix develop -c make itest-ktor TEST_JAVA_VERSION=21
```

Outside the Nix shell, `make itest-ktor` requires the `inngest` CLI on `PATH`
or `INNGEST_CLI_PATH` set to its executable. The integration test starts and
stops its own servers.

## Releasing

Releases follow the same high-level flow as `inngest-rs`, adapted for package-prefixed tags:

1. Pushes to `main` automatically create or update a `release/next` PR.
2. That PR bumps each releasable package's `VERSION` file and regenerates its package changelog at `pkg/CHANGELOG.md`.
3. Merging the release PR creates the matching package tags, publishes any package versions not yet on Maven Central, and creates or updates the GitHub releases using `git-cliff` notes.

Release tags use the package-prefixed format, for example `inngest-0.2.2`, `inngest-ktor-adapter-0.2.1`, and `inngest-spring-boot-adapter-0.2.1`.
