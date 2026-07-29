plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))
    implementation(project(":mandala-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(libs.doma.spring.boot)
    implementation(libs.doma.core)
    annotationProcessor(libs.doma.processor)
    runtimeOnly(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.springdoc)
    implementation(libs.opentelemetry.sdk)
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.51.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Adoma.resources.dir=${projectDir}/src/main/resources"))
}
