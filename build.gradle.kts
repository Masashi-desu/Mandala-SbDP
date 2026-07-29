plugins {
    base
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "io.github.mandala.sbdp"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("failed", "skipped") }
    }

    dependencies {
        "testImplementation"(platform(rootProject.libs.junit.bom))
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
    }
}

tasks.register("checkAll") {
    group = "verification"
    dependsOn(gradle.includedBuilds.map { it.task(":check") })
    dependsOn(subprojects.map { it.tasks.named("check") })
}
