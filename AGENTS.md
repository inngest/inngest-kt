# Agent Guide

This file provides guidance to AI coding agents working in this repository.

## Repository Context

This repository is the Inngest SDK for Kotlin with Java interoperability. It is
a Gradle multi-project build with these modules:

- `inngest` - core SDK package published as `com.inngest:inngest`.
- `inngest-spring-boot-adapter` - Spring Boot adapter published as
  `com.inngest:inngest-spring-boot-adapter`.
- `inngest-test-server` - Ktor development/test server for exercising the SDK.
- `inngest-spring-boot-demo` - Spring Boot demo and integration test app.

Prefer local documentation and plans over broad assumptions. The canonical SDK
contract is tracked in the upstream Inngest SDK specification linked from
`README.md`, and active implementation plans live in `docs/plans/`. Use
`scripts/check-plan-status.sh` to review plan status when working from a plan.

## Commit and PR Titles

Conventional Commit-style titles are required for any commit or PR title you
create. This repository's changelog configuration in `cliff.toml` enables
`conventional_commits = true` and `filter_unconventional = true`, so
non-conventional titles are easy to lose or misclassify.

Keep title types aligned with `cliff.toml` and `.github/workflows/commits.yml`.
The accepted types are:

- `feat`
- `fix`
- `doc`
- `perf`
- `refactor`
- `style`
- `test`
- `chore`
- `ci`
- `revert`
- `security`

Use standard conventional formatting such as
`fix(inngest): preserve retry-after headers` or
`test(spring-boot): cover introspection signatures`.

## Pull Request Text

When preparing PR text, follow `.github/pull_request_template.md`:

- `Summary` should briefly explain what changed and why.
- `Changes` should list notable implementation details when helpful.
- `Checklist` should mark documentation and test coverage as complete or
  explicitly not applicable.
- `Related` should include linked issues or PRs when present.

Do not add a second PR-note convention unless release automation is changed to
expect it.

## Release Metadata

Releases are package-prefixed. The releasable packages keep their versions in:

- `inngest/VERSION`
- `inngest-spring-boot-adapter/VERSION`

Release tags use package-prefixed names such as `inngest-0.2.2` and
`inngest-spring-boot-adapter-0.2.1`. Release automation and package changelog
generation are implemented by the scripts in `scripts/` and the GitHub
workflows in `.github/workflows/`.

## Development Commands

- `make dev-ktor` runs the Ktor test server via
  `./gradlew inngest-test-server:run`.
- `make dev-spring-boot` runs the Spring Boot demo with
  `SPRING_BOOT_VERSION` defaulting to `2.7.18`.
- `make inngest-dev` starts the local Inngest dev server pointed at
  `http://127.0.0.1:8080/api/inngest`.
- `make test` runs the core, Ktor, Spring adapter, and Spring demo tests.
- `make itest` runs Spring demo integration tests.
- `make test-core` runs tests for `inngest`.
- `make test-ktor` runs tests for `inngest-test-server`.
- `make test-springboot-adapter` runs tests for `inngest-spring-boot-adapter`.
- `make test-springboot-demo` runs tests for `inngest-spring-boot-demo`.
- `make lint` runs `ktlint --color`.
- `make fmt` runs `ktlint -F`.

CI runs tests across multiple Java and Spring Boot versions. When changing
Spring adapter or demo behavior, consider relevant `SPRING_BOOT_VERSION`
coverage in addition to the default `2.7.18`.

## Project Architecture

- `inngest/src/main/kotlin/com/inngest/` contains the core SDK API, function
  configuration, event and step handling, request handling, environment logic,
  HTTP client behavior, and signing key verification.
- `inngest/src/main/kotlin/com/inngest/ktor/` contains Ktor route integration.
- `inngest/src/test/kotlin/` and `inngest/src/test/resources/protocol/` contain
  shared SDK and protocol contract tests and fixtures.
- `inngest/src/testFixtures/` exposes reusable Java test fixtures for adapter
  tests.
- `inngest-spring-boot-adapter/src/main/java/com/inngest/springboot/` contains
  the Spring Boot controller and configuration layer.
- `inngest-spring-boot-demo/` exercises the adapter and hosts integration tests.
- `inngest-test-server/` provides a lightweight Ktor server for local SDK
  testing.

## Working Style

- Prefer minimal, targeted changes that preserve existing Kotlin and Java style.
- Keep public SDK behavior and wire shapes compatible with protocol fixtures and
  plan notes unless the task explicitly changes the contract.
- Add or update focused tests for behavior changes. Use shared fixtures when
  changing cross-adapter request handling.
- Run the most relevant module tests before handing work back when practical.
- Run `make lint` or `make fmt` when formatting-sensitive Kotlin or Java files
  are edited.
- Commits should be small logical chunks so each one is self reviewable.
- If operating from a plan in `docs/plans/`, update checklist items as work is
  completed.
