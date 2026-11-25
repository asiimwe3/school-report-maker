plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Reads .xlsx class lists for bulk student import (v2.2.3) — no writing, read-only usage.
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    // Real PDF output for whole-class printing — no browser, no print dialog.
    implementation("org.apache.pdfbox:pdfbox:2.0.31")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
