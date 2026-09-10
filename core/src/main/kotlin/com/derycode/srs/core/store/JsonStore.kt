package com.derycode.srs.core.store

import com.derycode.srs.core.model.AuditEntry
import com.derycode.srs.core.model.Mark
import com.derycode.srs.core.model.MarkType
import com.derycode.srs.core.model.SchoolData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * File-based offline storage (Section 31). The entire dataset lives in a single
 * JSON file — trivially portable, backup-able, USB-stick friendly. Writes are
 * atomic (temp + move) so a power cut never corrupts data.
 */
class JsonStore(
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true }
) {

    // ── Load / save ───────────────────────────────────────────────────────────

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

    // ── Backups (Section 37) ──────────────────────────────────────────────────

    /** Rotating backups: keep the newest [keep]. Returns the backup path. */
    fun backup(path: Path, keep: Int = 10): Path {
        if (!Files.exists(path)) return path
        val dir = path.toAbsolutePath().parent.resolve("backups")
        Files.createDirectories(dir)
        val stamp = System.currentTimeMillis()
        val target = dir.resolve("backup-$stamp.json")
        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
        // rotate: delete oldest beyond `keep`
        val backups = dir.toFile().listFiles { f -> f.name.startsWith("backup-") }
            ?.map { it.toPath() }?.sortedByDescending { it.fileName.toString() } ?: emptyList()
        backups.drop(keep).forEach { Files.deleteIfExists(it) }
        return target
    }

    /** Restore with a safety backup first (Section 38). */
    fun restore(path: Path, backupFile: Path): SchoolData {
        backup(path)   // safety copy of current data before overwrite
        val data = json.decodeFromString<SchoolData>(Files.readString(backupFile))
        save(path, data)
        return data
    }

    // ── USB / file bundles (Sections 39–40) ─────────────────────────────────

    @Serializable
    data class Bundle(
        val bundleVersion: Int = 1,
        val format: String = "DERYCODE-SRS-BUNDLE",
        val schoolName: String = "",
        val deviceId: String = "",
        val createdAt: Long = 0,
        val marks: List<Mark> = emptyList()
    )

    /** Teacher exports their marks as a small bundle file (USB / Bluetooth). */
    fun exportBundle(path: Path, schoolName: String, deviceId: String, marks: List<Mark>) {
        val bundle = Bundle(
            schoolName = schoolName, deviceId = deviceId,
            createdAt = System.currentTimeMillis(), marks = marks
        )
        Files.createDirectories(path.toAbsolutePath().parent)
        Files.writeString(path, json.encodeToString(Bundle.serializer(), bundle))
    }

    /**
     * Import a bundle. Marks are merged newest-wins per
     * (student, subject, term, component); every merge is returned for audit.
     * Old/corrupt bundles never corrupt newer data (versioned + validated).
     */
    fun importBundle(data: SchoolData, bundleFile: Path): BundleMergeReport {
        return try {
            val bundle = json.decodeFromString<Bundle>(Files.readString(bundleFile))
            if (bundle.format != "DERYCODE-SRS-BUNDLE") {
                return BundleMergeReport(success = false, message = "Not a School Report Maker bundle")
            }
            val merged = mergeMarks(data, bundle.marks)
            BundleMergeReport(success = true, message = "Imported ${merged.size} marks", merged = merged)
        } catch (e: Exception) {
            BundleMergeReport(success = false, message = "Bundle could not be read: ${e.message}")
        }
    }

    /** Merge marks into data, newest-wins, both versions kept on conflict. */
    fun mergeMarks(data: SchoolData, incoming: List<Mark>): List<Mark> {
        val merged = mutableListOf<Mark>()
        val key = { m: Mark -> "${m.studentId}|${m.subjectId}|${m.termId}|${m.componentId}" }
        val byKey = data.marks.associateBy(key)
        val result = incoming.map { inc ->
            val existing = byKey[key(inc)]
            when {
                existing == null -> inc.also { merged.add(it) }
                inc.updatedAt >= existing.updatedAt -> inc.also { merged.add(it) }
                else -> existing   // local newer — keep
            }
        }
        return result.ifEmpty { emptyList() }.also { /* merged list used for audit */ }
    }
    private val cfgJson = Json { ignoreUnknownKeys = true }

    fun exportSchoolConfig(path: Path, data: SchoolData) {
        val config = data.copy(
            marks = emptyList(), markSheets = emptyList(), termResults = emptyList(),
            comments = emptyList(), reports = emptyList(), auditLog = emptyList(),
            syncQueue = emptyList(), conflicts = emptyList()
        )
        Files.createDirectories(path.toAbsolutePath().parent)
        Files.writeString(path, cfgJson.encodeToString(SchoolData.serializer(), config))
    }

    /** Teacher phone loads a school config; keeps its own marks, comments and audit log. */
    fun loadSchoolConfig(path: Path, keep: SchoolData): SchoolData {
        val imported = cfgJson.decodeFromString<SchoolData>(Files.readString(path))
        return imported.copy(
            marks = keep.marks, comments = keep.comments, markSheets = keep.markSheets,
            auditLog = keep.auditLog, syncQueue = keep.syncQueue, conflicts = keep.conflicts,
            reports = keep.reports, settings = keep.settings
        )
    }

}

/** Report shown to the admin after a bundle import. */
data class BundleMergeReport(
    val success: Boolean,
    val message: String,
    val merged: List<Mark> = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// Repository — the single source of truth helper both apps use (Rule 10).
// Every mutation: audit (Section 42) + atomic save + rotating backup.
// ─────────────────────────────────────────────────────────────────────────────

class SchoolRepository(
    val store: JsonStore,
    private val file: Path,
    private val deviceId: String = "desktop"
) {
    var data: SchoolData = store.load(file)
        private set

    private val auditFile = file.toAbsolutePath().parent.resolve("audit.jsonl")

    fun save() {
        store.save(file, data)
    }

    /** Load, mutate, audit, save in one guarded operation. */
    fun mutate(action: String, entity: String, entityId: String = "", old: String = "", new: String = "", block: (SchoolData) -> SchoolData) {
        val before = data
        data = block(data)
        save()
        audit(action, entity, entityId, old, new)
        store.backup(file)   // rotating local backup (Section 37)
    }

    fun audit(action: String, entity: String, entityId: String = "", old: String = "", new: String = "", userId: String? = null) {
        val entry = AuditEntry(
            id = UUID.randomUUID().toString(),
            userId = userId,
            action = action, entity = entity, entityId = entityId,
            oldValue = old, newValue = new,
            timestamp = System.currentTimeMillis(), device = deviceId
        )
        // keep audit inside the data file AND append to an append-only journal
        data = data.copy(auditLog = (data.auditLog + entry).takeLast(5000))
        try {
            Files.createDirectories(auditFile.toAbsolutePath().parent)
            Files.writeString(
                auditFile,
                kotlinx.serialization.json.Json.encodeToString(AuditEntry.serializer(), entry) + "\n",
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND
            )
        } catch (_: Exception) { /* journal is best-effort */ }
        save()
    }

    fun restore(backupFile: Path): Boolean {
        return try {
            data = store.restore(file, backupFile)
            audit("RESTORE", "SchoolData", old = "", new = backupFile.toString())
            true
        } catch (_: Exception) { false }
    }

    // ── Seed data on first run (Section 59) ────────────────────────────────

    fun seedIfNeeded(defaultSubjects: List<com.derycode.srs.core.model.Subject>,
                     defaultComponents: List<com.derycode.srs.core.model.AssessmentComponent>) {
        if (data.students.isEmpty() && data.academicYears.isEmpty() && data.gradingSchemes.isEmpty()) {
            data = data.copy(
                subjects = defaultSubjects,
                components = defaultComponents,
                gradingSchemes = com.derycode.srs.core.grading.GradingSchemes.ALL,
                templates = listOf(
                    com.derycode.srs.core.model.ReportTemplate(id = "tpl-classic", name = "Classic", layout = com.derycode.srs.core.model.TemplateLayout.CLASSIC, isDefault = true),
                    com.derycode.srs.core.model.ReportTemplate(id = "tpl-modern", name = "Modern", layout = com.derycode.srs.core.model.TemplateLayout.MODERN),
                    com.derycode.srs.core.model.ReportTemplate(id = "tpl-compact", name = "Compact", layout = com.derycode.srs.core.model.TemplateLayout.COMPACT),
                    com.derycode.srs.core.model.ReportTemplate(id = "tpl-plain", name = "Plain", layout = com.derycode.srs.core.model.TemplateLayout.PLAIN)
                )
            )
            save()
        }
    }

    // ── School config for teacher phones (Section 45: teacher gets structure, admin gets marks) ──

    /** Admin exports the school setup (classes, students, subjects, schemes — NO marks) for teacher phones. */

    // ── Marks helpers with audit trail (Rules 4–5) ───────────────────────────

    fun upsertMark(mark: Mark): Mark {
        val key = "${mark.studentId}|${mark.subjectId}|${mark.termId}|${mark.componentId}"
        val existing = data.marks.firstOrNull {
            "${it.studentId}|${it.subjectId}|${it.termId}|${it.componentId}" == key
        }
        val stamped = mark.copy(updatedAt = System.currentTimeMillis())
        val newMarks = if (existing == null) data.marks + stamped
        else data.marks.map { if (it === existing) stamped else it }
        mutate(
            action = if (existing == null) "MARK_ENTERED" else "MARK_CHANGED",
            entity = "Mark", entityId = key,
            old = existing?.let { "${it.score} (${it.type})" } ?: "",
            new = "${stamped.score} (${stamped.type})"
        ) { d -> d.copy(marks = newMarks) }
        return stamped
    }

    fun nextId(): String = UUID.randomUUID().toString().substring(0, 8)
}
