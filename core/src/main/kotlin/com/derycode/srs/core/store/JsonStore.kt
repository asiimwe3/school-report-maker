package com.derycode.srs.core.store

import com.derycode.srs.core.model.AttendanceRecord
import com.derycode.srs.core.entry.MarkLedger
import com.derycode.srs.core.model.AuditEntry
import com.derycode.srs.core.model.DutyRecord
import com.derycode.srs.core.model.Enrollment
import com.derycode.srs.core.model.EnrollmentRequest
import com.derycode.srs.core.model.GatePass
import com.derycode.srs.core.model.Mark
import com.derycode.srs.core.model.MarkType
import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.model.Student
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
        val data: SchoolData = json.decodeFromString(Files.readString(path))
        // Files written before the newest-wins fix carry duplicate mark rows.
        // Collapse them on the way in so nothing can read a stale score again.
        return if (MarkLedger.hasDuplicates(data.marks))
            data.copy(marks = MarkLedger.newestPerKey(data.marks)) else data
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
        val marks: List<Mark> = emptyList(),
        val students: List<Student> = emptyList(),
        val enrollmentRequests: List<EnrollmentRequest> = emptyList(),
        val dutyRecords: List<DutyRecord> = emptyList(),
        val gatePasses: List<GatePass> = emptyList(),
        val attendance: List<AttendanceRecord> = emptyList()
    )

    /** Teacher exports their marks + duty data as a small bundle file (USB / Bluetooth). */
    fun exportBundle(path: Path, schoolName: String, deviceId: String, marks: List<Mark>,
                     students: List<Student> = emptyList(),
                     enrollmentRequests: List<EnrollmentRequest> = emptyList(),
                     dutyRecords: List<DutyRecord> = emptyList(),
                     gatePasses: List<GatePass> = emptyList(),
                     attendance: List<AttendanceRecord> = emptyList()) {
        val bundle = Bundle(
            schoolName = schoolName, deviceId = deviceId,
            createdAt = System.currentTimeMillis(), marks = marks,
            students = students, enrollmentRequests = enrollmentRequests,
            dutyRecords = dutyRecords, gatePasses = gatePasses, attendance = attendance
        )
        Files.createDirectories(path.toAbsolutePath().parent)
        Files.writeString(path, json.encodeToString(Bundle.serializer(), bundle))
    }

    /**
     * Import a bundle. Marks are merged newest-wins per
     * (student, subject, term, component); every merge is returned for audit.
     * Old/corrupt bundles never corrupt newer data (versioned + validated).
     */
    /**
     * Which school sent this bundle? A bundle records the sender's school name,
     * not a full school config, so this reads that one field. Empty string means
     * the bundle does not say (older teacher builds) — the caller then falls back
     * to pruning rows it cannot attribute.
     */
    fun peekBundleSchool(bundleFile: Path): String = try {
        json.decodeFromString<Bundle>(Files.readString(bundleFile)).schoolName.trim()
    } catch (_: Exception) { "" }

    fun importBundle(data: SchoolData, bundleFile: Path): BundleMergeReport {
        return try {
            val bundle = json.decodeFromString<Bundle>(Files.readString(bundleFile))
            if (bundle.format != "DERYCODE-SRS-BUNDLE") {
                return BundleMergeReport(success = false, message = "Not a School Report Maker bundle")
            }
            val merged = mergeMarks(data, bundle.marks)
            // students: replace same-id, add new
            val mergedStudents = data.students.filter { s -> bundle.students.none { it.id == s.id } } + bundle.students
            // pending enrollment requests: approve + create enrollments
            val approvedIds = data.enrollmentRequests.map { it.id }.toSet()
            val newReqs = bundle.enrollmentRequests.filter { it.status == "PENDING" && it.id !in approvedIds }
            val approvedReqs = newReqs.map { it.copy(status = "APPROVED") }
            val mergedRequests = data.enrollmentRequests.filter { r -> approvedReqs.none { it.id == r.id } } + approvedReqs +
                bundle.enrollmentRequests.filter { it.status == "APPROVED" }
            val existingEnrIds = data.enrollments.map { it.id }.toSet()
            val newEnrollments = approvedReqs.filter { "enr-${it.id}" !in existingEnrIds }.map {
                Enrollment(id = "enr-${it.id}", studentId = it.student.id, academicYearId = it.academicYearId, classId = it.classId)
            }
            val mergedEnrollments = data.enrollments + newEnrollments
            // duty records & gate passes: replace same-id (newest state wins)
            val mergedDuty = data.dutyRecords.filter { r -> bundle.dutyRecords.none { it.id == r.id } } + bundle.dutyRecords
            val mergedPasses = data.gatePasses.filter { g -> bundle.gatePasses.none { it.id == g.id } } + bundle.gatePasses
            val mergedAttendance = data.attendance.filter { a -> bundle.attendance.none { it.id == a.id } } + bundle.attendance
            val bits = mutableListOf<String>()
            if (bundle.marks.isNotEmpty()) bits.add("${bundle.marks.size} marks")
            if (newReqs.isNotEmpty()) bits.add("${newReqs.size} new students enrolled")
            if (bundle.dutyRecords.isNotEmpty()) bits.add("${bundle.dutyRecords.size} duty records")
            if (bundle.gatePasses.isNotEmpty()) bits.add("${bundle.gatePasses.size} gate passes")
            if (bundle.attendance.isNotEmpty()) bits.add("${bundle.attendance.size} attendance records")
            val msg = "Imported: " + (if (bits.isEmpty()) "no new data" else bits.joinToString(", "))
            BundleMergeReport(success = true, message = msg, merged = merged, mergedStudents = mergedStudents,
                mergedEnrollments = mergedEnrollments, mergedRequests = mergedRequests,
                mergedDutyRecords = mergedDuty, mergedGatePasses = mergedPasses, mergedAttendance = mergedAttendance)
        } catch (e: Exception) {
            BundleMergeReport(success = false, message = "Bundle could not be read: ${e.message}")
        }
    }

    /** Merge marks into data, newest-wins, both versions kept on conflict. */
    fun mergeMarks(data: SchoolData, incoming: List<Mark>): List<Mark> {
        val key = { m: Mark -> "${m.studentId}|${m.subjectId}|${m.termId}|${m.componentId}" }
        val incomingByKey = incoming.associateBy(key)
        // Newest wins per mark, but every mark already in the school is KEPT.
        // (Before v2.2.36 this returned only the incoming marks, so importing one
        // class or subject wiped the marks of every other class in the school.)
        val updatedExisting = data.marks.map { ex ->
            val inc = incomingByKey[key(ex)]
            if (inc != null && inc.updatedAt >= ex.updatedAt) inc else ex
        }
        val existingKeys = data.marks.map(key).toSet()
        // and duplicates already sitting in the file collapse to their newest row
        return MarkLedger.newestPerKey(updatedExisting + incoming.filter { key(it) !in existingKeys })
    }
    private val cfgJson = Json { ignoreUnknownKeys = true }

    fun exportSchoolConfig(path: Path, data: SchoolData) {
        val config = data.copy(
            marks = emptyList(), termResults = emptyList(),
            comments = emptyList(), reports = emptyList(), auditLog = emptyList(),
            syncQueue = emptyList(), conflicts = emptyList(), feePayments = emptyList(),
            attendance = emptyList(), gatePasses = emptyList(), dutyRecords = emptyList()
        )
        Files.createDirectories(path.toAbsolutePath().parent)
        Files.writeString(path, cfgJson.encodeToString(SchoolData.serializer(), config))
    }

    /** Peek at the school identity inside an exported config without importing it. */
    fun peekSchool(path: Path): Pair<String, String> {
        val imported = cfgJson.decodeFromString<SchoolData>(Files.readString(path))
        return imported.school.id to imported.school.name
    }

    /** Fresh school config with NOTHING kept from the phone — used when a teacher switches schools. */
    fun loadSchoolConfigFresh(path: Path): SchoolData {
        val imported = cfgJson.decodeFromString<SchoolData>(Files.readString(path))
        return imported.copy(
            marks = emptyList(), comments = emptyList(),
            auditLog = emptyList(), syncQueue = emptyList(), conflicts = emptyList()
        )
    }

    /** Teacher phone loads a school config; keeps its own marks, comments and audit log. */
    fun loadSchoolConfig(path: Path, keep: SchoolData): SchoolData {
        val imported = cfgJson.decodeFromString<SchoolData>(Files.readString(path))
        return imported.copy(
            marks = keep.marks, comments = keep.comments,
            // lock states come from the office — the head teacher's decisions are authoritative
            auditLog = keep.auditLog, syncQueue = keep.syncQueue, conflicts = keep.conflicts,
            reports = keep.reports, settings = keep.settings
        )
    }

}

/** Report shown to the admin after a bundle import. */
data class BundleMergeReport(
    val success: Boolean,
    val message: String,
    val mergedStudents: List<Student> = emptyList(),
    val mergedEnrollments: List<Enrollment> = emptyList(),
    val mergedRequests: List<EnrollmentRequest> = emptyList(),
    val mergedDutyRecords: List<DutyRecord> = emptyList(),
    val mergedGatePasses: List<GatePass> = emptyList(),
    val mergedAttendance: List<AttendanceRecord> = emptyList(),
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
    // v2.2.26 — a corrupt/truncated data file must never crash the app at
    // startup. Quarantine the bad file, fall back to the newest readable
    // backup, then seed fresh (seedIfNeeded fills defaults afterwards).
    var data: SchoolData = try {
            store.load(file)
        } catch (e: Exception) {
            try {
                Files.move(file, file.resolveSibling("school-data-corrupt-${System.currentTimeMillis()}.json"), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) { e.printStackTrace() }
            newestUsableBackup() ?: SchoolData()
        }
        private set

    private fun newestUsableBackup(): SchoolData? {
        val dir = file.toAbsolutePath().parent.resolve("backups").toFile()
        val backups = dir.listFiles { f -> f.name.startsWith("backup-") }?.sortedByDescending { it.name } ?: return null
        for (b in backups) {
            try { return store.load(b.toPath()) } catch (e: Exception) { e.printStackTrace() }
        }
        return null
    }

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

    /** Factory reset: delete the entire data folder (data, backups, audit trail,
     *  crash logs, update downloads) and reseed fresh defaults. */
    fun wipeAndReseed(defaultSubjects: List<com.derycode.srs.core.model.Subject>,
                      defaultComponents: List<com.derycode.srs.core.model.AssessmentComponent>) {
        try {
            val dir = file.toAbsolutePath().parent
            if (java.nio.file.Files.exists(dir)) {
                java.nio.file.Files.walk(dir).sorted(java.util.Comparator.reverseOrder())
                    .forEach { java.nio.file.Files.deleteIfExists(it) }
            }
        } catch (e: Exception) { e.printStackTrace() }
        data = store.load(file)
        seedIfNeeded(defaultSubjects, defaultComponents)
    }

    fun seedIfNeeded(defaultSubjects: List<com.derycode.srs.core.model.Subject>,
                     defaultComponents: List<com.derycode.srs.core.model.AssessmentComponent>) {
        // v2.2.1 fix: each piece is seeded independently. The old all-or-nothing guard
        // (only seeded when students AND academicYears AND gradingSchemes were ALL empty)
        // meant a school that had already created a term — but had no classes yet because
        // of the earlier setup-wizard bug — would NEVER get its default classes seeded.
        var changed = false
        var next = data
        if (next.subjects.isEmpty()) { next = next.copy(subjects = defaultSubjects); changed = true }
        if (next.components.isEmpty()) { next = next.copy(components = defaultComponents); changed = true }
        if (next.classes.isEmpty()) { next = next.copy(classes = com.derycode.srs.core.seed.Seeds.DEFAULT_CLASSES); changed = true }
        if (next.gradingSchemes.isEmpty()) { next = next.copy(gradingSchemes = com.derycode.srs.core.grading.GradingSchemes.ALL); changed = true }
        if (next.templates.isEmpty()) {
            next = next.copy(templates = listOf(
                com.derycode.srs.core.model.ReportTemplate(id = "tpl-standard", name = "Standard", layout = com.derycode.srs.core.model.TemplateLayout.SUMMARY, accentColor = "#0B5CAD", isDefault = true),
                com.derycode.srs.core.model.ReportTemplate(id = "tpl-classic", name = "Classic", layout = com.derycode.srs.core.model.TemplateLayout.CLASSIC),
                com.derycode.srs.core.model.ReportTemplate(id = "tpl-modern", name = "Modern", layout = com.derycode.srs.core.model.TemplateLayout.MODERN),
                com.derycode.srs.core.model.ReportTemplate(id = "tpl-compact", name = "Compact", layout = com.derycode.srs.core.model.TemplateLayout.COMPACT),
                com.derycode.srs.core.model.ReportTemplate(id = "tpl-plain", name = "Plain", layout = com.derycode.srs.core.model.TemplateLayout.PLAIN)
            ))
            changed = true
        }
        next = withStandardAsDefault(next)
        next = withA2A3Components(next)
        if (next != data) changed = true
        if (changed) { data = next; save() }
    }

    /**
     * The school's own blue "Standard" summary card is the default report design.
     * A school that already has the template keeps it; one that predates it gets it
     * added. The default flips to Standard exactly once ([standardDefaultApplied]):
     * after that, a design the school chooses is never overridden.
     */
    /**
     * O-Level final score = A2 × 20% + A3 × 80%. A school that predates the two
     * assessments gets them added once, so its grid shows the A2/A3 columns and the
     * weighted final score. Nothing is rewritten: old marks keep their own component
     * and are never relabelled as A2 or A3, so history reads exactly as it was.
     */
    internal fun withA2A3Components(d: com.derycode.srs.core.model.SchoolData): com.derycode.srs.core.model.SchoolData {
        val hasOL = d.classes.any { it.level == com.derycode.srs.core.model.Level.O_LEVEL }
        if (!hasOL) return d
        val hasA2 = d.components.any { it.code.equals("A2", true) }
        val hasA3 = d.components.any { it.code.equals("A3", true) }
        if (hasA2 && hasA3) return d
        var comps = d.components
        if (!hasA2) comps = comps + com.derycode.srs.core.model.AssessmentComponent(
            id = "c-a2", code = "A2", name = "Assessment 2", weightPercent = d.settings.a2WeightPercent,
            levels = listOf(com.derycode.srs.core.model.Level.O_LEVEL)
        )
        if (!hasA3) comps = comps + com.derycode.srs.core.model.AssessmentComponent(
            id = "c-a3", code = "A3", name = "Assessment 3", weightPercent = d.settings.a3WeightPercent,
            levels = listOf(com.derycode.srs.core.model.Level.O_LEVEL)
        )
        return d.copy(components = comps)
    }

    internal fun withStandardAsDefault(d: com.derycode.srs.core.model.SchoolData): com.derycode.srs.core.model.SchoolData {
        if (d.settings.standardDefaultApplied) return d
        val layout = com.derycode.srs.core.model.TemplateLayout.SUMMARY
        var templates = d.templates
        if (templates.none { it.layout == layout && !it.archived }) {
            val archived = templates.firstOrNull { it.layout == layout }
            templates = if (archived != null) templates.map { if (it.id == archived.id) it.copy(archived = false) else it }
            else listOf(com.derycode.srs.core.model.ReportTemplate(id = "tpl-standard", name = "Standard", layout = layout, accentColor = "#0B5CAD")) + templates
        }
        val standardId = templates.first { it.layout == layout && !it.archived }.id
        templates = templates.map { it.copy(isDefault = it.id == standardId) }
        return d.copy(templates = templates, settings = d.settings.copy(standardDefaultApplied = true))
    }

    // ── School config for teacher phones (Section 45: teacher gets structure, admin gets marks) ──

    /** Admin exports the school setup (classes, students, subjects, schemes — NO marks) for teacher phones. */

    // ── Marks helpers with audit trail (Rules 4–5) ───────────────────────────

    fun upsertMark(mark: Mark): Mark {
        val key = MarkLedger.key(mark)
        val existing = MarkLedger.get(data.marks, mark.studentId, mark.subjectId, mark.termId, mark.componentId)
        val stamped = mark.copy(updatedAt = System.currentTimeMillis())
        // v2.3.29: every row sharing this key is replaced, so a leftover duplicate
        // can never shadow the edit (the "old marks come back" bug).
        val newMarks = MarkLedger.upsert(data.marks, mark, stamped.updatedAt)
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
