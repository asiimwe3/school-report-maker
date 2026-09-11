import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
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
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "SchoolReportMaker"
            packageVersion = "1.9.0"
            description = "DeryCode School Report Maker — offline report cards for Ugandan schools"
            vendor = "DeryCode"
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "DeryCode"
                upgradeUuid = "3f8a1c6e-9b42-4d17-a8e5-c71d94e60213"
            }
            linux {
                iconFile.set(project.file("icons/icon-512.png"))
            }
        }
    }
}
