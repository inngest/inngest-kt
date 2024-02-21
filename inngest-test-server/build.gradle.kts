description = "Inngest Test Server"

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.9.10"

    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    api(project(":inngest-core"))

    // Use the Kotlin JUnit 5 integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    // Use the JUnit 5 integration.
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.3")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("io.ktor:ktor-server-core:2.3.5")
    implementation("io.ktor:ktor-server-netty:2.3.5")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
}

// Apply a specific Java toolchain to ease working on different environments.
java { toolchain { languageVersion.set(JavaLanguageVersion.of(20)) } }

application {
    // Define the main class for the application.
    mainClass.set("com.inngest.testserver.AppKt")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
