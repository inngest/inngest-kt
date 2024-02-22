description = "Inngest SDK"

plugins { id("org.jetbrains.kotlin.jvm") version "1.9.10" }

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    implementation("com.beust:klaxon:5.5")
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    testImplementation(kotlin("test"))
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

// TODO - Move this to share conventions gradle file
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    sourceCompatibility = JavaVersion.VERSION_17
}
