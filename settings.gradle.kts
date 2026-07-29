pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "mandala-sbdp"

val mandalaPlatformProjects = listOf(
    "mandala-model",
    "mandala-core",
    "mandala-spring",
    "mandala-doma",
    "mandala-postgres",
    "mandala-opentelemetry",
    "mandala-renderer",
    "mandala-cli",
    "mandala-spring-boot-starter",
    "mandala-gradle-plugin",
)

include(*mandalaPlatformProjects.toTypedArray())
mandalaPlatformProjects.forEach { projectName ->
    project(":$projectName").projectDir = file("platform/java/$projectName")
}

include("sample-app:backend")
