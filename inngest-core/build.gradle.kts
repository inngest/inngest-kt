description = "Inngest SDK"

plugins { id("org.jetbrains.kotlin.jvm") version "1.9.10" }

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies { implementation("com.beust:klaxon:5.5") }

// TODO - Move this to share conventions gradle file
java { toolchain { languageVersion.set(JavaLanguageVersion.of(20)) } }
