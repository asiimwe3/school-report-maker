// Versions used across modules
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("com.android.application") version "8.5.2" apply false
}

// ─────────────────────────────────────────────────────────────────────────────
// version.json is what the console and the teacher phone download to discover an
// update, so a stale field there silently blocks updates: teacher "1.20.1" while
// teacherDownload still said srs-teacher-1.20.0.apk answered 404 and every phone
// stayed behind. Every URL is now derived from the build files, and this task
// fails the build when the two disagree, on every module's tests.
// ─────────────────────────────────────────────────────────────────────────────
val verifyVersionJson by tasks.registering {
    val root = layout.projectDirectory
    doLast {
        fun version(pattern: Regex, path: String): String =
            pattern.find(root.file(path).asFile.readText())?.groupValues?.get(1)
                ?: throw GradleException("could not read the version from $path")

        val teacher = version(Regex("versionName\\s*=\\s*\"([^\"]+)\""), "teacher-app/build.gradle.kts")
        val console = version(Regex("val consoleVersion\\s*=\\s*\"([^\"]+)\""), "desktop-admin/build.gradle.kts")
        val teacherDesktop = version(
            Regex("val teacherDesktopVersion\\s*=\\s*\"([^\"]+)\""), "desktop-teacher/build.gradle.kts"
        )
        val text = root.file("version.json").asFile.readText()
        fun field(name: String) = Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1)
        val base = "https://github.com/asiimwe3/school-report-maker/releases/latest/download"

        val expected = mapOf(
            "console" to console,
            "consoleDownload" to "$base/SchoolReportMaker-$console.msi",
            "teacher" to teacher,
            "teacherDownload" to "$base/srs-teacher-$teacher.apk",
            "apkDownload" to "$base/srs-teacher-$teacher.apk",
            "teacherDesktop" to teacherDesktop,
            "teacherDesktopDownload" to "$base/SchoolReportMakerTeacher-$teacherDesktop.msi"
        )
        val wrong = expected.filter { (k, v) -> field(k) != v }
        if (wrong.isNotEmpty()) {
            val detail = wrong.entries.joinToString("\n") { (k, v) -> "  $k is \"${field(k)}\", build files say \"$v\"" }
            throw GradleException("version.json is out of date with the builds:\n$detail\nRun: python3 scripts/sync_version.py")
        }
        println("version.json matches the builds (console $console, teacher $teacher, teacher desktop $teacherDesktop)")
    }
}

gradle.projectsEvaluated {
    allprojects {
        tasks.matching { it.name == "test" || it.name == "check" }.configureEach {
            dependsOn(rootProject.tasks.named("verifyVersionJson"))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The release workflow asks one Windows runner for "gradle :desktop-admin:packageMsi"
// and uploads every *.msi it finds in that folder. The teacher's laptop installer
// rides along on the same command: it is built first, then copied next to the
// console's, so a tag produces SchoolReportMakerTeacher-<version>.msi too. Done
// here, in a normal build file, because the release token cannot edit workflows.
// Windows only — jpackage builds no MSI elsewhere and CI runs this job on Linux.
// ─────────────────────────────────────────────────────────────────────────────
val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
if (windows) {
    val collectTeacherMsi = tasks.register<Copy>("collectTeacherMsi") {
        description = "Puts the teacher laptop MSI beside the console's, ready for the release upload"
        from(project(":desktop-teacher").layout.buildDirectory.dir("compose/binaries/main/msi")) { include("*.msi") }
        into(project(":desktop-admin").layout.buildDirectory.dir("compose/binaries/main/msi"))
    }
    gradle.projectsEvaluated {
        val consoleMsi = project(":desktop-admin").tasks.findByName("packageMsi")
        val teacherMsi = project(":desktop-teacher").tasks.findByName("packageMsi")
        if (consoleMsi != null && teacherMsi != null) {
            consoleMsi.finalizedBy(teacherMsi)
            teacherMsi.finalizedBy(collectTeacherMsi)
        }
    }
}

// The CI job builds the console with "gradle :desktop-admin:build", so the laptop
// app's own tests ride on that same command instead of needing a workflow edit.
gradle.projectsEvaluated {
    val adminBuild = project(":desktop-admin").tasks.findByName("build")
    val laptopTests = project(":desktop-teacher").tasks.findByName("test")
    val laptopJar = project(":desktop-teacher").tasks.findByName("jar")
    if (adminBuild != null && laptopTests != null && laptopJar != null) {
        adminBuild.dependsOn(laptopTests)
        adminBuild.dependsOn(laptopJar)
    }
}
