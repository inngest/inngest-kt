import java.io.FileOutputStream
import java.util.Properties

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

    testImplementation(kotlin("test"))
}

val generatedVersionDir = "$buildDir/generated-version"

sourceSets {
    main {
        kotlin {
            output.dir(generatedVersionDir)
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

tasks.register("generateVersionProperties") {
    doLast {
        val propertiesFile = file("$generatedVersionDir/version.properties")
        propertiesFile.parentFile.mkdirs()
        val properties = Properties()
        properties.setProperty("version", "$version")
        val out = FileOutputStream(propertiesFile)
        properties.store(out, null)
    }
}

tasks.named("processResources") {
    dependsOn("generateVersionProperties")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

// TODO - Move this to share conventions gradle file
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(8)) }
}
