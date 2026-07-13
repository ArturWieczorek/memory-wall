plugins {
    `java-library`
    application // `./gradlew run` starts the Spring Boot backend
    id("com.diffplug.spotless") version "6.25.0"
}

repositories {
    mavenCentral()
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    // Spring Boot backend (BOM-managed; web + test starters only).
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Align the JUnit Platform launcher with the (newer) JUnit the Boot 3.5 BOM pulls in;
    // Gradle 8.10.2 bundles an older launcher, which otherwise crashes the test executor.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Cardano: build/submit transactions, read metadata, talk to a Blockfrost-compatible backend.
    implementation("com.bloxbean.cardano:cardano-client-lib:0.7.2")
    implementation("com.bloxbean.cardano:cardano-client-backend-blockfrost:0.7.2")
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

application {
    mainClass.set("org.wall.WallApplication")
}
