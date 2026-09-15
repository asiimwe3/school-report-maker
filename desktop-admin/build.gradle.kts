import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin { jvmToolchain(17) }

val consoleVersion = "2.2.11"   // single source of truth — also drives packageVersion below

// Generate AppVersion.kt so runtime code always matches the packaged version
// (audit fix: DesktopSupport had a stale hardcoded "2.0.0")
val generateVersionKt = tasks.register("generateVersionKt") {
    val outDir = layout.buildDirectory.dir("generated/versionkt")
    outputs.dir(outDir)
    doLast {
        val pkgDir = File(outDir.get().asFile, "com/derycode/srs/admin")
        pkgDir.mkdirs()
        File(pkgDir, "AppVersion.kt").writeText(
            "package com.derycode.srs.admin\n\n/** Auto-generated from consoleVersion in build.gradle.kts — do not edit. */\nconst val APP_VERSION = \"$consoleVersion\"\n"
        )
    }
}
tasks.named("compileKotlin") { dependsOn(generateVersionKt) }
sourceSets.main { kotlin.srcDir(layout.buildDirectory.dir("generated/versionkt")) }

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
            packageVersion = consoleVersion
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
