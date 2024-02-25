description = "Inngest SDK"
version = "0.0.1-SNAPSHOT"

plugins { id("org.jetbrains.kotlin.jvm") version "1.9.10" }

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
}

// TODO - Move this to share conventions gradle file
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(8)) }
}
