dependencies {
    api(project(":mandala-model"))
    implementation(project(":mandala-core"))
    implementation(libs.jackson.databind)
}

tasks.register<Test>("updateRendererGolden") {
    description = "Explicitly regenerate the reviewed renderer Golden file."
    group = "verification"
    useJUnitPlatform()
    filter { includeTestsMatching("io.github.mandala.sbdp.renderer.StaticSiteRendererTest.rendersBidirectionalLinksCrudAndSafeCustomHtml") }
    systemProperty("mandala.updateGolden", "true")
    systemProperty("mandala.goldenDir", layout.projectDirectory.dir("src/test/resources/golden").asFile.absolutePath)
    outputs.upToDateWhen { false }
}
