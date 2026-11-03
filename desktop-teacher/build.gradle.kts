import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJvmToolOperationTask

// jpackage must come from Amazon Corretto 17 - the Temurin re-spin ships a broken
// jpackage that produces a byte-mangled app image. Same pin as the console.
val packagingJdkLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
    vendor.set(JvmVendorSpec.AMAZON)
}
tasks.withType<AbstractJvmToolOperationTask>().configureEach {
    javaHome.set(packagingJdkLauncher.map { it.metadata.installationPath.toString() })
}

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin { jvmToolchain(17) }

val teacherDesktopVersion = "1.0.0"

val generateVersionKt = tasks.register("generateVersionKt") {
    val outDir = layout.buildDirectory.dir("generated/versionkt")
    outputs.dir(outDir)
    doLast {
        val pkgDir = File(outDir.get().asFile, "com/derycode/srs/desktopteacher")
        pkgDir.mkdirs()
        File(pkgDir, "AppVersion.kt").writeText(
            "package com.derycode.srs.desktopteacher\n\n/** Auto-generated from teacherDesktopVersion in build.gradle.kts - do not edit. */\nconst val APP_VERSION = \"$teacherDesktopVersion\"\n"
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
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.derycode.srs.desktopteacher.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "SchoolReportMakerTeacher"
            packageVersion = teacherDesktopVersion
            description = "DeryCode School Report Maker for teachers - enter marks on a laptop"
            vendor = "DeryCode"
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "DeryCode"
                // its own uuid: installing the teacher app never upgrades the console
                upgradeUuid = "b7d3f904-2c68-4a51-9e0d-8f4c17a25b63"
            }
            linux { iconFile.set(project.file("icons/icon-512.png")) }
        }
    }
}
