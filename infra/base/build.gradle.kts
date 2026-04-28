// Actual dependency versions resolved from Maven Central (2026-04-28):
// com.pulumi:pulumi:1.13.2, com.pulumi:aws:6.83.0, com.pulumi:awsx:2.22.0,
// com.pulumi:eks:3.9.1, com.pulumi:kubernetes:4.23.0

plugins {
    kotlin("jvm") version "2.3.10"
    application
}

group = "com.agentwork.infra.base"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.pulumi:pulumi:1.13.2")
    implementation("com.pulumi:aws:6.83.0")
    implementation("com.pulumi:awsx:2.22.0")
    implementation("com.pulumi:eks:3.9.1")
    implementation("com.pulumi:kubernetes:4.23.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.agentwork.infra.base.AppKt"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
