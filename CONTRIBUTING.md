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

To release a new version, increment the version in each package's `VERSION` file. This will automatically trigger the GitHub workflow that publishes the given package to Maven central and adds a git tag for the given package release.

After the release process is complete, draft a new release via Github either generating the changelog or manually adding notes.
