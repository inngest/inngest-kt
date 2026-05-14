import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

val springBootVersion = providers.gradleProperty("springBootVersion").orElse("2.7.18")
val springBootMajorVersion = springBootVersion.map { it.substringBefore(".").toInt() }

plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management") version "1.1.4"
    id("io.freefair.lombok") version "8.6"
}

group = "com.inngest"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility =
        if (springBootMajorVersion.get() >= 3) {
            JavaVersion.VERSION_17
        } else {
            JavaVersion.VERSION_1_8
        }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":inngest-spring-boot-adapter"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    if (springBootMajorVersion.get() >= 4) {
        testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    }
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    if (JavaVersion.current().isJava11Compatible) {
        testImplementation("uk.org.webcompere:system-stubs-jupiter:2.1.6")
    } else {
        testImplementation("uk.org.webcompere:system-stubs-jupiter:1.2.1")
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${springBootVersion.get()}") {
            bomProperty("kotlin.version", "1.9.10")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.parallel.enabled", true)
    systemProperty("test-group", "unit-test")

    // Required by `system-stubs-jupiter` for JDK 21+ compatibility
    // https://github.com/raphw/byte-buddy/issues/1396
    jvmArgs = listOf("-Dnet.bytebuddy.experimental=true")
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

tasks.register<Test>("integrationTest") {
    systemProperty("test-group", "integration-test")
    systemProperty("junit.jupiter.execution.parallel.enabled", false)
}
