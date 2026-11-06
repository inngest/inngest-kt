import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

group = "com.inngest"
description = "Spring Boot adapter for Inngest SDK"
version = file("VERSION").readText().trim()

val springBootVersion = "2.7.18"
val testSpringBootVersion = providers.gradleProperty("springBootVersion").orElse(springBootVersion)
val testSpringBootMajorVersion = testSpringBootVersion.map { it.substringBefore(".").toInt() }

plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    // Every consumer must exercise the same Java 8 / Boot 2 release artifact.
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
    withSourcesJar()
}

tasks.compileJava {
    options.release.set(8)
}

// MockMvc is not binary-compatible across Spring majors. Keep compiling the
// framework-specific tests against the consumer version, never the adapter.
val testCompilationVersion = if (testSpringBootMajorVersion.get() >= 3) 17 else 8
tasks.compileTestJava {
    sourceCompatibility = testCompilationVersion.toString()
    targetCompatibility = testCompilationVersion.toString()
}
listOf(configurations.testCompileClasspath, configurations.testRuntimeClasspath).forEach {
    it.configure {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, testCompilationVersion)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    val pkg = if (System.getenv("RELEASE") != null) "com.inngest:inngest:[0.2.0, 0.3.0)" else project(":inngest")
    api(pkg)

    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation(testFixtures(project(":inngest")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val testJavaVersion = providers.gradleProperty("testJavaVersion").map { it.toInt() }

dependencyManagement {
    imports {
        // Import the Spring Boot BOM directly instead of applying the Boot
        // Gradle plugin; the Boot 2.7 plugin is not compatible with Gradle 9.
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion") {
            bomProperty("kotlin.version", "1.9.10")
        }
    }
    // Configuration-scoped imports are exposed through the plugin's Groovy DSL.
    withGroovyBuilder {
        "configurations"("testCompileClasspath", "testRuntimeClasspath") {
            "imports" {
                "mavenBom"("org.springframework.boot:spring-boot-dependencies:${testSpringBootVersion.get()}") {
                    "bomProperty"("kotlin.version", "1.9.10")
                }
            }
        }
    }
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
        register<MavenPublication>("inngest-spring-boot-adapter") {
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

tasks.withType<Test> {
    testJavaVersion.orNull?.let { version ->
        // The CI matrix asks for each Java version explicitly. Boot 3/4 tests
        // still need at least Java 17, so clamp older requested JVMs upward.
        val minimumVersion = testCompilationVersion
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(maxOf(version, minimumVersion)))
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
