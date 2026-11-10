package com.derycode.srs.desktopteacher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.derycode.srs.core.model.*
import com.derycode.srs.core.seed.Seeds
import com.derycode.srs.core.store.JsonStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Everything this computer knows, on disk, offline.
 *
 * The laptop is the teacher's notebook: the same files the phone keeps, in the
 * same format, so a teacher can move between a phone and a laptop without the
 * school ever needing internet. Marks are written on every save, and a rotating
 * backup runs alongside them, because a laptop that dies in a staff room must
 * not take a term's marks with it.
 */
class TeacherStore(root: Path? = null) {
    private val store = JsonStore()

    /** Where this laptop keeps its copy. A test passes its own folder. */
    val dataDir: Path = root ?: Path.of(
        System.getProperty("user.home") ?: ".",
        "SchoolReportMakerTeacher"
    )

    private val file: Path = dataDir.resolve("school-data.json")
    private val meFile: Path = dataDir.resolve("teacher-id.txt")
    val outbox: Path = dataDir.resolve("to-admin")

    var data by mutableStateOf(SchoolData())
        private set

    /** The teacher using this computer. Picked once, remembered. */
    var meId by mutableStateOf<String?>(
        if (Files.exists(meFile)) Files.readString(meFile).trim().ifBlank { null } else null
    )
        private set

    var lastMessage by mutableStateOf("")
    var lastOk by mutableStateOf(true)

    val me: Teacher? get() = meId?.let { id -> data.teachers.firstOrNull { it.id == id } }
    val myFirstName: String get() = me?.name?.split(" ")?.firstOrNull { it.isNotBlank() } ?: "Teacher"
    // A brand-new install carries a placeholder school called "My School" and no
    // classes, so the name alone cannot mean "connected". Classes can.
    val connected: Boolean get() = data.classes.isNotEmpty() || data.students.isNotEmpty()

    val scope: SchoolScope get() = SchoolScope.of(data.school)

    val currentTermId: String
        get() = data.academicYears.firstOrNull()?.currentTermId
            ?: data.academicYears.firstOrNull()?.terms?.firstOrNull()?.id ?: ""

    val currentTermLabel: String
        get() {
            data.academicYears.forEach { y ->
                y.terms.firstOrNull { it.id == currentTermId }?.let { return "${y.year} Term ${it.number}" }
            }
            return "Term"
        }

    val myClasses: List<SchoolClass>
        get() {
            val mine = if (meId == null) data.classes.filter { it.active }
            else data.assignments.filter { a -> a.teacherId == meId }
                .mapNotNull { a -> data.classes.firstOrNull { c -> c.id == a.classId && c.active } }
                .distinctBy { it.id }
            return mine.filter { scope.allows(it.level) }
        }

    fun mySubjectsFor(classId: String): List<Subject> {
        val cls = data.classes.firstOrNull { it.id == classId }
        val base = data.subjects.filter { it.level == cls?.level && it.active && scope.allows(it.level) }
        if (meId == null) return base
        val mine = data.assignments.filter { it.teacherId == meId && it.classId == classId }
        if (mine.any { it.subjectId == null }) return base   // class teacher sees all
        val ids = mine.mapNotNull { it.subjectId }.toSet()
        return base.filter { it.id in ids }
    }

    fun studentsIn(classId: String): List<Student> =
        data.enrollments.filter { it.classId == classId }
            .mapNotNull { e -> data.students.firstOrNull { s -> s.id == e.studentId && s.status == StudentStatus.ACTIVE } }
            .sortedBy { it.admissionNo }

    /** Marks this teacher can see: only this school's rows, per the isolation rules. */
    fun myMarks(): List<Mark> = SchoolIsolation.scopedMarks(data)

    fun markFor(studentId: String, subjectId: String, termId: String, componentId: String): Mark? =
        com.derycode.srs.core.entry.MarkLedger.get(data.marks, studentId, subjectId, termId, componentId)

    init {
        data = if (Files.exists(file)) {
            try { store.load(file) } catch (e: Exception) {
                quarantine(); loadNewestUsableBackup() ?: freshStart()
            }
        } else freshStart()
    }

    fun setMe(id: String?) {
        meId = id
        try {
            Files.createDirectories(dataDir)
            if (id == null) Files.deleteIfExists(meFile) else Files.writeString(meFile, id)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun save() {
        try {
            Files.createDirectories(dataDir)
            if (Files.exists(file)) store.backup(file)
            store.save(file, data)
        } catch (e: Exception) {
            lastOk = false
            lastMessage = "Could not save: ${e.message?.take(120)}"
        }
    }

    fun say(ok: Boolean, msg: String) { lastOk = ok; lastMessage = msg }

    /** Newest-wins per learner/subject/term/component, exactly like the phone. */
    fun upsertMark(m: Mark) {
        val stamped = m.copy(updatedAt = System.currentTimeMillis())
        data = data.copy(marks = com.derycode.srs.core.entry.MarkLedger.upsert(data.marks, m, stamped.updatedAt))
        save()
    }

    fun upsertAttendance(r: AttendanceRecord) {
        val existing = data.attendance.firstOrNull {
            it.studentId == r.studentId && it.date == r.date
        }
        data = if (existing == null) data.copy(attendance = data.attendance + r)
        else data.copy(attendance = data.attendance.map { if (it.id == existing.id) r else it })
        save()
    }

    /**
     * Import the setup the admin exported from the console.
     *
     * A different school's file never mixes silently: this computer either holds
     * nothing yet, or holds the same school, or the import stops and says so.
     */
    fun importSchoolSetup(path: Path): Pair<Boolean, String> = try {
        val incoming = store.loadSchoolConfigFresh(path)
        if (incoming.school.name.isBlank() && incoming.classes.isEmpty()) {
            Pair(false, "That file has no school in it. Export the school setup from the admin console and copy the file here.")
        } else {
            val empty = data.students.isEmpty() && data.marks.isEmpty() && data.classes.isEmpty()
            val same = empty || SchoolIsolation.sameSchool(incoming, data)
            if (!same) {
                Pair(false, "That file is from ${incoming.school.name}, but this computer holds " +
                    "${data.school.name.ifBlank { "another school" }} with ${data.students.size} learners. " +
                    "Importing would mix two schools. Use Settings > Switch school if this computer is moving to that school.")
            } else {
                val merged = SchoolIsolation.prune(store.loadSchoolConfig(path, data)).first
                data = merged
                save()
                setMe(meId?.takeIf { id -> data.teachers.any { it.id == id } })
                Pair(true, "Connected to ${data.school.name.ifBlank { "the school" }}: " +
                    "${data.classes.size} classes, ${data.students.size} learners, ${data.subjects.size} subjects. " +
                    "Marks already on this computer were kept.")
            }
        }
    } catch (e: Exception) {
        Pair(false, "That file could not be read: ${e.message?.take(100)}")
    }

    /** Replace everything with another school's setup (the teacher moved schools). */
    fun switchSchool(path: Path): Pair<Boolean, String> = try {
        val incoming = store.loadSchoolConfigFresh(path)
        if (incoming.classes.isEmpty() && incoming.students.isEmpty()) {
            Pair(false, "That file has no school in it.")
        } else {
            store.backup(file)
            data = incoming
            setMe(null)
            save()
            Pair(true, "Switched to ${data.school.name.ifBlank { "the new school" }}. The old school's learners and marks were archived, and nothing was carried across.")
        }
    } catch (e: Exception) { Pair(false, "Switch failed: ${e.message?.take(100)}") }

    /**
     * The marks bundle the console imports. Written to a folder the teacher can
     * reach from a flash disk or send on WhatsApp Web.
     */
    fun exportBundle(): Pair<Boolean, Path?> = try {
        Files.createDirectories(outbox)
        val mine = myMarks()
        if (mine.isEmpty()) Pair(false, null) else {
            val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
            val target = outbox.resolve("srs-marks-$stamp.json")
            store.exportBundle(
                target, data.school.name, deviceId = "teacher-laptop",
                marks = mine, students = data.students,
                enrollmentRequests = data.enrollmentRequests,
                dutyRecords = data.dutyRecords, gatePasses = data.gatePasses,
                attendance = data.attendance
            )
            Pair(true, target)
        }
    } catch (e: Exception) {
        lastOk = false
        lastMessage = "Could not write the bundle: ${e.message?.take(120)}"
        Pair(false, null)
    }

    private fun freshStart(): SchoolData {
        val fresh = SchoolData(
            components = Seeds.COMPONENTS,
            subjects = Seeds.ALL_SUBJECTS,
            gradingSchemes = com.derycode.srs.core.grading.GradingSchemes.ALL
        )
        return try {
            Files.createDirectories(dataDir)
            store.save(file, fresh)
            fresh
        } catch (e: Exception) { fresh }
    }

    private fun quarantine() = try {
        Files.move(file, dataDir.resolve("school-data-corrupt-${System.currentTimeMillis()}.json"),
            StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) { e.printStackTrace() }

    private fun loadNewestUsableBackup(): SchoolData? {
        val dir = dataDir.resolve("backups").toFile()
        val backups = dir.listFiles { f -> f.name.startsWith("backup-") }?.sortedByDescending { it.name } ?: return null
        for (b in backups) {
            try { return store.load(b.toPath()) } catch (e: Exception) { e.printStackTrace() }
        }
        return null
    }

    /** Marks for one sheet, filled with what is already saved. */
    /** Clearing a cell on the sheet means the mark is gone, not that it was never there. */
    fun removeMark(studentId: String, subjectId: String, termId: String, componentId: String) {
        val keep = data.marks.filterNot {
            it.studentId == studentId && it.subjectId == subjectId &&
                it.termId == termId && it.componentId == componentId
        }
        if (keep.size != data.marks.size) { data = data.copy(marks = keep); save() }
    }

    fun sheetValues(classId: String, subjectId: String, componentId: String): Map<String, Mark> {
        val term = currentTermId
        return studentsIn(classId).mapNotNull { s ->
            markFor(s.id, subjectId, term, componentId)?.let { s.id to it }
        }.toMap()
    }
}
