import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

group = "com.inngest"
description = "Ktor adapter for Inngest SDK"
version = file("VERSION").readText().trim()

plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(8)) }
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    val pkg = if (System.getenv("RELEASE") != null) "com.inngest:inngest:[0.2.0, 0.3.0)" else project(":inngest")
    api(pkg)

    implementation("io.ktor:ktor-server-core:2.3.5")

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":inngest")))
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    testImplementation("io.ktor:ktor-server-test-host:2.3.5")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

val testJavaVersion = providers.gradleProperty("testJavaVersion").map { it.toInt() }

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
        register<MavenPublication>("inngest-ktor-adapter") {
            from(components["java"])

            pom {
                name.set(project.name)
                description.set(project.description)
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
    val signingKey = System.getenv("GPG_SIGNING_KEY")
    val signingPasswd = System.getenv("GPG_SIGNING_KEY_PASSWORD")
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
    testJavaVersion.orNull?.let { version ->
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(maxOf(version, 8)))
            },
        )
    }

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
    }
}
