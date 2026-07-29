plugins { application }

dependencies {
    implementation(project(":mandala-core"))
    implementation(project(":mandala-spring"))
    implementation(project(":mandala-doma"))
    implementation(project(":mandala-postgres"))
    implementation(project(":mandala-opentelemetry"))
    implementation(project(":mandala-renderer"))
    implementation(libs.picocli)
    implementation(libs.jackson.yaml)
    implementation(libs.jackson.jsr310)
    implementation(libs.javaparser)
    annotationProcessor(libs.picocli)
}

application {
    mainClass.set("io.github.mandala.sbdp.cli.MandalaCli")
    applicationName = "mandala"
}

// The CLI defaults are repository-relative. Gradle would otherwise execute
// this subproject's `run` task with platform/java/mandala-cli as the working directory.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.layout.projectDirectory.asFile
}

tasks.jar {
    manifest { attributes["Main-Class"] = application.mainClass.get() }
}
