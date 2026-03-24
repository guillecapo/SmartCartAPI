// build.gradle.kts

plugins {
    id("java")
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
    id("jacoco") // ← agrega el plugin
}

group = "com.msd"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

configurations.all {
    resolutionStrategy.eachDependency {
        // CVE-2024-25710, CVE-2024-26308 — commons-compress vulnerable < 1.27.1
        if (requested.group == "org.apache.commons" && requested.name == "commons-compress") {
            useVersion("1.27.1")
            because("CVE-2024-25710, CVE-2024-26308 — forced secure version")
        }
        // CVE-2025-48924 — commons-lang3 vulnerable < 3.18.0
        if (requested.group == "org.apache.commons" && requested.name == "commons-lang3") {
            useVersion("3.18.0")
            because("CVE-2025-48924 — forced secure version")
        }
    }
}

dependencies {
    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    testCompileOnly("org.projectlombok:lombok:1.18.36")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.36")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")

    // ULID
    implementation("com.github.f4b6a3:ulid-creator:5.2.4")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.16")

    // Test — JUnit 5 + Mockito + AssertJ via Spring Boot BOM
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Testcontainers
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.6"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // ← genera reporte automáticamente después de cada test run
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)   // para CI/CD
        html.required.set(true)  // para lectura humana
    }
    // Excluye clases que no tienen lógica de negocio testeable
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/infrastructure/config/**",
                    "**/infrastructure/adapter/in/http/request/**",
                    "**/infrastructure/adapter/in/http/response/**",
                    "**/infrastructure/adapter/out/rabbitmq/message/**",
                    "**/domain/model/enums/**",
                    "**/*Application*",
                    "**/shared/annotation/**"
                )
            }
        })
    )
    // ← CSS oscuro
    doLast {
        val cssSource = file("src/test/resources/jacoco-dark.css")
        val reportDir = reports.html.outputLocation.asFile.get()
        val jacocoCSS = File(reportDir, "jacoco-resources/report.css")

        if (jacocoCSS.exists()) {
            jacocoCSS.appendText("\n\n/* ===== Dark Theme ===== */\n")
            jacocoCSS.appendText(cssSource.readText())
        }

    }
}

jacoco {
    toolVersion = "0.8.11"
}