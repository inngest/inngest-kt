import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

group = "com.inngest"
description = "Inngest SDK"
version = "0.0.1"

plugins {
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm") version "1.9.10"
}

// TODO - Move this to share conventions gradle file
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(8)) }
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    implementation("com.beust:klaxon:5.5")
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("io.ktor:ktor-server-core:2.3.5")

    testImplementation(kotlin("test"))
}

publishing {
    repositories {
        // maven {
        //     name = "OSSRH"
        //     url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
        //     credentials {
        //         username = System.getenv("MAVEN_USERNAME")
        //         password = System.getenv("MAVEN_PASSWORD")
        //     }
        // }

        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/inngest/inngest-kt")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
            ),
        )
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()

    testLogging {
        events =
            setOf(
                TestLogEvent.FAILED,
                TestLogEvent.PASSED,
                TestLogEvent.SKIPPED,
            )

        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        showStackTraces = true

        // set options for log level DEBUG and INFO
        debug {
            events =
                setOf(
                    TestLogEvent.STARTED,
                    TestLogEvent.FAILED,
                    TestLogEvent.PASSED,
                    TestLogEvent.SKIPPED,
                    TestLogEvent.STANDARD_ERROR,
                    TestLogEvent.STANDARD_OUT,
                )

            exceptionFormat = TestExceptionFormat.FULL
        }

        info.events = debug.events
        info.exceptionFormat = debug.exceptionFormat

        afterSuite(
            KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
                if (desc.parent == null) { // will match the outermost suite
                    println(
                        "Results: ${result.resultType} (${result.testCount} tests, ${result.successfulTestCount} successes, ${result.failedTestCount} failures, ${result.skippedTestCount} skipped)",
                    )
                }
            }),
        )
    }
}