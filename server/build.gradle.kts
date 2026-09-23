plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
}

group = "io.github.devasenan134"
version = "0.1.0"

repositories {
    mavenCentral()
}

val ktor = "3.6.0"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-server-auth:$ktor")
    implementation("io.ktor:ktor-server-websockets:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
    implementation("io.ktor:ktor-server-call-logging:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-client-core:$ktor")
    implementation("io.ktor:ktor-client-cio:$ktor")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("ch.qos.logback:logback-classic:1.6.3")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.52.0") // Firebase login for push

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation("io.ktor:ktor-client-websockets:$ktor")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.github.devasenan134.isaipetti.server.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

// Local testing: the server plus pretend "bot_" users (see src/test/.../DevServer.kt).
tasks.register<JavaExec>("runDev") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.devasenan134.isaipetti.server.DevServerKt")
    // Your Navidrome server: -PnavidromeUrl=https://... or ISAIPETTI_SERVER_URL in ~/.gradle/gradle.properties.
    environment("NAVIDROME_URL", (project.findProperty("navidromeUrl") ?: project.findProperty("ISAIPETTI_SERVER_URL") ?: "http://localhost:4533") as String)
    environment("DB_PATH", layout.buildDirectory.file("dev/isaipetti-social.db").get().asFile.path)
    // Optional: real push notifications while testing (-PfirebaseKey=/path/to/key.json).
    (project.findProperty("firebaseKey") as String?)?.let { environment("FIREBASE_KEY_FILE", it) }
}
