import java.util.Base64

plugins {
    kotlin("jvm") version "1.9.10"
    id("com.google.devtools.ksp") version "1.9.10-1.0.13"
    `java`
    `maven-publish`
    `signing`
}

group = "com.kishlaly.tools.coroutinecache"
version = "1.0.0"

java {
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.squareup:kotlinpoet:1.14.2")
    implementation("com.squareup:kotlinpoet-ksp:1.14.2")
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.10-1.0.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("CoroutineCache")
                description.set("Fast coroutine cache for Kotlin")
                url.set("https://github.com/s1ac2x1/CoroutineCache")
            }
        }
    }
    repositories {
        mavenLocal()
    }
}

signing {
    val rawKey = System.getenv("GPG_SIGNING_KEY") ?: ""
    val decodedKey = String(Base64.getDecoder().decode(rawKey))
    useInMemoryPgpKeys(decodedKey, System.getenv("GPG_SIGNING_PASSPHRASE"))
    sign(publishing.publications["mavenJava"])
}