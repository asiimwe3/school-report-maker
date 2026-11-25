plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))
    // PDFBox is a desktop library (java.awt). It lives here, not in :core, so the
    // Android teacher app never has to dex it.
    implementation("org.apache.pdfbox:pdfbox:2.0.31")
    testImplementation(kotlin("test"))
    testImplementation(project(":core"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
