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

## Releasing

Releases follow the same high-level flow as `inngest-rs`, adapted for package-prefixed tags:

1. Pushes to `main` automatically create or update a `release/next` PR.
2. That PR bumps each releasable package's `VERSION` file and regenerates its package changelog at `pkg/CHANGELOG.md`.
3. Merging the release PR creates the matching package tags, publishes any package versions not yet on Maven Central, and creates or updates the GitHub releases using `git-cliff` notes.

Release tags use the package-prefixed format, for example `inngest-0.2.2` and `inngest-spring-boot-adapter-0.2.1`.
