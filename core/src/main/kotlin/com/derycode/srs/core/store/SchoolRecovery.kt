package com.derycode.srs.core.store

import com.derycode.srs.core.model.SchoolData
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Finding a school that seems to have gone missing.
 *
 * Data can look "lost" without being lost: the console opened a fresh file, a
 * colleague's laptop was set up again, a teacher switched school, a USB bundle
 * was taken home. The school is usually still sitting in a backup or an archived
 * file on the same machine. This scans the places it hides and describes what it
 * found, so a head teacher can point at the right one and restore it instead of
 * re-entering everything.
 */
object SchoolRecovery {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** One file that holds a school, with enough detail to recognise it. */
    data class Candidate(
        val file: Path,
        val school: String,
        val students: Int,
        val classes: Int,
        val teachers: Int,
        val marks: Int,
        val savedAt: Long,
        val origin: String
    ) {
        /** A file with no learners and no classes cannot restore anybody's school. */
        val usable: Boolean get() = students > 0 || classes > 0
    }

    /** Is this dataset effectively empty? Then the console has nothing to lose and everything to recover. */
    fun looksEmpty(data: SchoolData): Boolean =
        data.students.isEmpty() && data.classes.isEmpty() && data.subjects.isEmpty() &&
            data.marks.isEmpty() && data.teachers.count { it.username.isNotBlank() } == 0

    private fun isSchoolFile(file: Path): Boolean {
        val name = file.fileName.toString().lowercase()
        if (!name.endsWith(".json")) return false
        if (name.contains("version")) return false
        // kotlinx skips default values, so a real school file can be surprisingly
        // small (a name and a couple of classes is ~150 bytes). Only tiny scraps
        // and obvious non-school files are skipped here.
        return Files.size(file) > 20
    }

    /** Parses one file; null when it is not a school file at all. */
    fun read(file: Path, origin: String = ""): Candidate? = try {
        val text = Files.readString(file)
        if (!text.contains("\"students\"") && !text.contains("\"classes\"")) null
        else {
            val data = json.decodeFromString(SchoolData.serializer(), text)
            Candidate(
                file = file,
                school = data.school.name.ifBlank { "(name never set)" },
                students = data.students.size,
                classes = data.classes.size,
                teachers = data.teachers.size,
                marks = data.marks.size,
                savedAt = stampOf(file),
                origin = origin
            )
        }
    } catch (_: Exception) { null }

    private fun stampOf(file: Path): Long {
        val fromName = file.fileName.toString().removeSuffix(".json")
            .split('-').lastOrNull()?.toLongOrNull()
        return fromName ?: try { Files.getLastModifiedTime(file).toMillis() } catch (_: Exception) { 0L }
    }

    /**
     * Every recoverable school file in [roots], newest first. Directories are
     * searched one level deep (plus their own `backups` folder), which covers the
     * data folder, the backups folder and a folder copied off a USB stick.
     */
    fun scan(roots: List<Path>): List<Candidate> {
        val paths = mutableListOf<Path>()
        for (root in roots) {
            if (!Files.isDirectory(root)) continue
            val children = mutableListOf<Path>()
            Files.list(root).use { stream -> stream.forEach { child -> children.add(child) } }
            for (child in children) {
                if (Files.isDirectory(child)) {
                    Files.list(child).use { stream -> stream.forEach { file -> if (!Files.isDirectory(file)) paths.add(file) } }
                } else {
                    paths.add(child)
                }
            }
        }
        val seen = mutableSetOf<Path>()
        return paths
            .filter { isSchoolFile(it) }
            .filter { seen.add(it.toAbsolutePath().normalize()) }
            .mapNotNull { file ->
                val origin = when {
                    file.toString().contains("backups") -> "Backup"
                    file.fileName.toString().startsWith("archived-school") -> "Archived school"
                    file.fileName.toString().contains("bundle") -> "USB bundle"
                    else -> "School file"
                }
                read(file, origin)
            }
            .filter { it.usable }
            .sortedByDescending { it.savedAt }
    }

    /** The best file to bring back: the newest one that still holds learners. */
    fun best(candidates: List<Candidate>, schoolHint: String = ""): Candidate? {
        val hint = schoolHint.trim().lowercase()
        val matching = if (hint.isBlank()) candidates
            else candidates.filter { it.school.lowercase().contains(hint) }
        val pool = if (matching.isNotEmpty()) matching else candidates
        return pool.maxByOrNull { it.students * 1000L + it.marks + it.savedAt / 86_400_000L }
    }
}
