plugins {
    `java-library`
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.inngest"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":inngest-core"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:2.7.18") {
            bomProperty("kotlin.version", "1.9.10")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
