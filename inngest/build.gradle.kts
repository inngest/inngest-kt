import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

group = "com.inngest"
description = "Inngest SDK"
version = "0.0.2"

plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
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
        // maven {
        //     name = "OSSRH"
        //     url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
        //     credentials {
        //         username = System.getenv("MAVEN_USERNAME")
        //         password = System.getenv("MAVEN_PASSWORD")
        //     }
        // }

        // NOTE: create a local repo and bundle this to upload it to Maven Central for now
        maven {
            // local repo
            val releasesRepoUrl = uri(layout.buildDirectory.dir("repos/releases"))
            val snapshotsRepoUrl = uri(layout.buildDirectory.dir("repos/snapshots"))
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        }

        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/inngest/inngest-kt")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("inngest") {
            from(components["java"])

            pom {
                name.set(project.name)
                description.set("Inngest SDK for Kotlin/Java")
                url.set("https://github.com/inngest/inngest-kt")
                inceptionYear.set("2024")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("eng")
                        name.set("Inngest Engineering")
                        email.set("eng@inngest.com")
                    }
                }

                scm {
                    url.set("https://github.com/inngest/inngest-kt")
                    connection.set("scm:git:https://github.com/inngest/inngest-kt.git")
                    developerConnection.set("scm:git:git@github.com:inngest/inngest-kt.git")
                }
            }
        }
    }
}

signing {
    val signingKey = System.getenv("MAVEN_SIGNING_KEY")
    val signingPasswd = System.getenv("MAVEN_SIGNING_PASSWORD")
    useInMemoryPgpKeys(signingKey, signingPasswd)
    sign(publishing.publications)
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
