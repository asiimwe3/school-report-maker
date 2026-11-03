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
        val text = root.file("version.json").asFile.readText()
        fun field(name: String) = Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1)
        val base = "https://github.com/asiimwe3/school-report-maker/releases/latest/download"

        val expected = mapOf(
            "console" to console,
            "consoleDownload" to "$base/SchoolReportMaker-$console.msi",
            "teacher" to teacher,
            "teacherDownload" to "$base/srs-teacher-$teacher.apk",
            "apkDownload" to "$base/srs-teacher-$teacher.apk"
        )
        val wrong = expected.filter { (k, v) -> field(k) != v }
        if (wrong.isNotEmpty()) {
            val detail = wrong.entries.joinToString("\n") { (k, v) -> "  $k is \"${field(k)}\", build files say \"$v\"" }
            throw GradleException("version.json is out of date with the builds:\n$detail\nRun: python3 scripts/sync_version.py")
        }
        println("version.json matches the builds (console $console, teacher $teacher)")
    }
}

gradle.projectsEvaluated {
    allprojects {
        tasks.matching { it.name == "test" || it.name == "check" }.configureEach {
            dependsOn(rootProject.tasks.named("verifyVersionJson"))
        }
    }
}
