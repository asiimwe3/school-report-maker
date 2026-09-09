import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

compose.desktop {
    application {
        mainClass = "com.derycode.srs.admin.MainKt"
        nativeDistributions {
            packageName = "SchoolReportMaker"
            packageVersion = "0.1.0"
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Exe)
            description = "Offline school report making console for administrators"
        }
    }
}
