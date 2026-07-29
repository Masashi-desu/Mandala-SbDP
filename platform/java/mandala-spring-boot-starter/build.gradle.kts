dependencies {
    api(project(":mandala-model"))
    api(libs.opentelemetry.api)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.5.3")
    compileOnly("org.springframework:spring-context:6.2.8")
    compileOnly("org.springframework:spring-web:6.2.8")
    compileOnly("jakarta.servlet:jakarta.servlet-api:6.1.0")
    implementation("org.springframework:spring-aop:6.2.8")
    implementation("org.aspectj:aspectjweaver:1.9.24")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.5.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.3")
    testImplementation("org.springframework:spring-web:6.2.8")
    testImplementation("jakarta.servlet:jakarta.servlet-api:6.1.0")
    testImplementation(libs.opentelemetry.sdk)
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.51.0")
}
