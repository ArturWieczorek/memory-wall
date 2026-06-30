plugins {
    `java-library`
    id("com.diffplug.spotless") version "6.25.0"
    // The Spring Boot backend (API that builds the post tx + serves the feed) is added in Ch 03.
}

repositories {
    mavenCentral()
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    // Cardano: build/submit transactions, read metadata, talk to a Blockfrost-compatible backend.
    implementation("com.bloxbean.cardano:cardano-client-lib:0.7.2")
    implementation("com.bloxbean.cardano:cardano-client-backend-blockfrost:0.7.2")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-parameters") }

tasks.test {
    useJUnitPlatform { excludeTags("integration") }
    testLogging { events("passed", "skipped", "failed") }
}

tasks.register<Test>("integrationTest") {
    description = "Runs tests tagged 'integration' (need a live devnet/testnet)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
}

spotless {
    java {
        googleJavaFormat("1.22.0")
        target("src/**/*.java")
    }
}
