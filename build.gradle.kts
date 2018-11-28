import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.10"
    id("io.vertx.vertx-plugin") version "0.1.0"
}

group = "com.grimsward"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-web-client")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

vertx {
    mainVerticle = "com.grimsward.diplomacy.MainVerticle"
    vertxVersion = "3.5.4"
}