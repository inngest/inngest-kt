import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

val springBootVersion = providers.gradleProperty("springBootVersion").orElse("2.7.18")
val springBootMajorVersion = springBootVersion.map { it.substringBefore(".").toInt() }

plugins {
    java
    application
    // Use the dependency-management plugin plus a local bootRun task below.
    // Applying the Spring Boot Gradle plugin would break Boot 2.7 on Gradle 9.
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.inngest"
version = "0.0.1-SNAPSHOT"

java {
    // Spring Boot 3+ requires Java 17, while Boot 2.x can still compile for
    // Java 8. This demo follows the selected Boot version's baseline.
    sourceCompatibility =
        if (springBootMajorVersion.get() >= 3) {
            JavaVersion.VERSION_17
        } else {
            JavaVersion.VERSION_1_8
        }
}

application {
    mainClass.set("com.inngest.springbootdemo.SpringBootDemoApplication")
}

repositories {
    mavenCentral()
}

val testJavaVersion = providers.gradleProperty("testJavaVersion").map { it.toInt() }

dependencies {
    implementation(project(":inngest-spring-boot-adapter"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    if (springBootMajorVersion.get() >= 4) {
        // Spring Boot 4 moved MVC test support out of starter-test.
        testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    }
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    val effectiveTestJavaVersion = testJavaVersion.orNull ?: JavaVersion.current().majorVersion.toInt()
    // system-stubs 2.x requires Java 11. Keep 1.2.1 available for the Boot 2
    // / Java 8 matrix entries.
    if (effectiveTestJavaVersion >= 11) {
        testImplementation("uk.org.webcompere:system-stubs-jupiter:2.1.6")
    } else {
        testImplementation("uk.org.webcompere:system-stubs-jupiter:1.2.1")
    }
}

dependencyManagement {
    imports {
        // Import the BOM directly so the selected Spring Boot version controls
        // dependency alignment without applying version-specific Boot tasks.
        mavenBom("org.springframework.boot:spring-boot-dependencies:${springBootVersion.get()}") {
            bomProperty("kotlin.version", "1.9.10")
        }
    }
}

tasks.withType<Test> {
    testJavaVersion.orNull?.let { version ->
        // CI includes Java 8/11 entries for compatibility, but Boot 3/4 cannot
        // run below Java 17. Use the requested JVM unless the Boot baseline is higher.
        val minimumVersion = if (springBootMajorVersion.get() >= 3) 17 else 8
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(maxOf(version, minimumVersion)))
            },
        )
    }

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
    description = "Runs Spring Boot demo integration tests."
    group = "verification"
    // Reuse the standard test source set and switch groups with a system
    // property. Creating a separate source set makes these tests disappear
    // under the current Gradle 9 setup.
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)
    systemProperty("test-group", "integration-test")
    systemProperty("junit.jupiter.execution.parallel.enabled", false)
}

tasks.register<JavaExec>("bootRun") {
    group = "application"
    description = "Runs the Spring Boot demo application."
    // Local replacement for the Spring Boot plugin's bootRun task so the demo
    // can still be exercised against Boot 2.7 on Gradle 9.
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
}
