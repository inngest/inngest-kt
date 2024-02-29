import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

group = "com.inngest"
description = "Inngest SDK"
version = "0.0.2"

plugins {
    `java-library`
    `maven-publish`
    signing
    id("org.jetbrains.kotlin.jvm") version "1.9.10"
}

// TODO - Move this to share conventions gradle file
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(8)) }
    withJavadocJar()
    withSourcesJar()
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
        // NOTE: Does not work: https://central.sonatype.org/publish/publish-portal-gradle/
        maven {
            name = "OSSRH"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }

        // maven {
        //     name = "GitHubPackages"
        //     url = uri("https://maven.pkg.github.com/inngest/inngest-kt")
        //     credentials {
        //         username = System.getenv("GITHUB_ACTOR")
        //         password = System.getenv("GITHUB_TOKEN")
        //     }
        // }
    }
    publications {
        register<MavenPublication>("inngest") {
            from(components["java"])

            pom {
                name = "Inngest SDK"
                description = "Inngest SDK for Kotlin/Java"
                url = "https://github.com/inngest/inngest-kt"

                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }

                developers {
                    developer {
                        id = "eng"
                        name = "Inngest Engineering"
                        email = "eng@inngest.com"
                    }
                }

                scm {
                    connection = "scm:git:https://github.com/inngest/inngest-kt.git"
                    developerConnection = "scm:git:git@github.com:inngest/inngest-kt.git"
                    url = "https://github.com/inngest/inngest-kt"
                }
            }
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

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
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
