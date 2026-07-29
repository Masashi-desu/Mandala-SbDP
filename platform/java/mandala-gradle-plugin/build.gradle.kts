plugins { `java-gradle-plugin` }

dependencies { implementation(project(":mandala-cli")) }

gradlePlugin {
    plugins {
        create("mandala") {
            id = "io.github.mandala.sbdp"
            implementationClass = "io.github.mandala.sbdp.gradle.MandalaPlugin"
            displayName = "Mandala SbDP"
            description = "Generates and verifies a Mandala Documentation Graph"
        }
    }
}
