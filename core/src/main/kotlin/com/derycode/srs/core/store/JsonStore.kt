package com.derycode.srs.core.store

import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.model.Student
import com.derycode.srs.core.model.Teacher
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * File-based offline storage. The entire dataset lives in a single JSON file
 * so it is trivially portable, backup-able, and USB-stick friendly.
 * Writes are atomic (temp file + move) so a power cut never corrupts data.
 */
class JsonStore(private val json: Json = Json { prettyPrint = false; ignoreUnknownKeys = true }) {

    fun load(path: Path): SchoolData {
        if (!Files.exists(path)) return SchoolData()
        return json.decodeFromString(Files.readString(path))
    }

    fun save(path: Path, data: SchoolData) {
        Files.createDirectories(path.toAbsolutePath().parent)
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(tmp, json.encodeToString(SchoolData.serializer(), data))
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** A bundle a teacher exports from the phone for the admin to import. */
    fun exportTeacherBundle(path: Path, students: List<Student>, teacher: Teacher) {
        val bundle = TeacherBundle(teacher, students)
        Files.createDirectories(path.toAbsolutePath().parent)
        Files.writeString(path, json.encodeToString(TeacherBundle.serializer(), bundle))
    }
}

@kotlinx.serialization.Serializable
data class TeacherBundle(
    val teacher: Teacher,
    val students: List<Student>
)
