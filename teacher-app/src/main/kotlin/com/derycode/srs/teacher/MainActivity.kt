package com.derycode.srs.teacher

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*
import com.derycode.srs.core.seed.Seeds
import com.derycode.srs.core.store.JsonStore
import com.derycode.srs.core.support.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashTracker.install(this)
        CrashReporter.sendPending(this)   // deliver any previous crash BEFORE the UI (survives startup crash-loops)
        val state = TeacherState(this)
        state.checkForUpdate()   // auto-check on every launch; only asks GitHub, never sends data
        applyPhoneTheme(state.phoneTheme)   // paint the first frame in the chosen theme
        setContent {
            MaterialTheme(
                colorScheme = if (DARK_THEME)
                    darkColorScheme(background = BG, surface = CARD, surfaceVariant = CARD_ALT, onBackground = NAVY,
                        onSurface = NAVY, onSurfaceVariant = MUTED, primary = BLUE, onPrimary = Color.White,
                        outline = STROKE, error = RED, secondary = TEAL, tertiary = ORANGE)
                else
                    lightColorScheme(background = BG, surface = CARD, surfaceVariant = CARD_ALT, onBackground = NAVY,
                        onSurface = NAVY, onSurfaceVariant = MUTED, primary = BLUE, onPrimary = Color.White,
                        outline = STROKE, error = RED, secondary = TEAL, tertiary = ORANGE),
                typography = AppTypography
            ) { TeacherApp(state) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// State & storage — local file is the primary database (never requires internet)
// ─────────────────────────────────────────────────────────────────────────────

class TeacherState(private val context: Context) {
    private val store = JsonStore()
    private val file: Path = context.filesDir.toPath().resolve("school-data.json")
    val exportDir: File? = context.getExternalFilesDir(null)

    var data by mutableStateOf(SchoolData())
        private set

    private val meFile = File(context.filesDir, "teacher-id.txt")
    private val themeFile = File(context.filesDir, "phone-theme.txt")

    /** A theme chosen on this phone alone; null = follow the school's console theme. */
    var themeChoice by mutableStateOf<String?>(
        if (themeFile.exists()) themeFile.readText().trim().ifBlank { null } else null
    )
        private set

    fun setThemeOverride(name: String?) {
        themeChoice = name
        if (name == null) themeFile.delete() else themeFile.writeText(name.trim())
    }

    /** The theme actually in use: the phone's own choice first, else the school's. */
    val phoneTheme: String get() = themeChoice?.takeIf { it.isNotBlank() } ?: data.settings.theme

    /** The teacher using this phone — picked once, remembered. */
    var meId by mutableStateOf<String?>(if (meFile.exists() && meFile.readText().isNotBlank()) meFile.readText().trim() else null)
        private set

    /**
     * What this teacher told us when they joined (name, role, the classes and
     * subjects they picked). Kept on the phone so a later "pull school setup"
     * from the console cannot quietly remove them from staff and remove access.
     */
    var myJoin: JoinProfile? = JoinProfile.read(context.filesDir)
        private set

    fun setMe(id: String?) {
        meId = id
        if (id == null) meFile.delete() else meFile.writeText(id)
        data = data
    }

    val me: Teacher? get() = meId?.let { id -> data.teachers.firstOrNull { it.id == id } }
    val myFirstName: String get() = me?.name?.split(" ")?.firstOrNull { it.isNotBlank() } ?: "Teacher"

    /** ALL classes this teacher is assigned to — a teacher can have multiple classes. */
    /** Which levels this school runs — a secondary school's phones never show Primary. */
    val scope: com.derycode.srs.core.model.SchoolScope
        get() = com.derycode.srs.core.model.SchoolScope.of(data.school)

    /**
     * Where the class list came from, and whether we actually matched this teacher.
     * Roster never hands back an empty list while the school has classes, because
     * "no classes" on a teacher's phone is almost always an id mismatch, not a
     * school without classes.
     */
    val classLoad: com.derycode.srs.core.model.Roster.Load
        get() = com.derycode.srs.core.model.Roster.classesFor(data, meId, scope)

    val myClasses: List<SchoolClass> get() = classLoad.classes

    /** True when assignments (or a class-teacher slot) name this teacher. */
    val classesMatched: Boolean get() = classLoad.matched

    /** Subjects this teacher teaches in a given class (null subject assignment = class teacher → all). */
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
        com.derycode.srs.core.model.Roster.studentsFor(data, classId)

    fun classOf(studentId: String): SchoolClass? {
        val e = data.enrollments.lastOrNull { it.studentId == studentId } ?: return null
        return data.classes.firstOrNull { it.id == e.classId }
    }

    val currentTermId: String get() = data.academicYears.firstOrNull()?.currentTermId
        ?: data.academicYears.firstOrNull()?.terms?.firstOrNull()?.id ?: ""

    /** A light, deterministic timetable preview built from real assignments (no separate timetable entity yet). */
    data class ScheduleItem(val time: String, val subject: String, val classLabel: String)
    val todaysSchedule: List<ScheduleItem>
        get() {
            // Real timetable (set by the admin) wins when it exists
            val cal = java.util.Calendar.getInstance()
            val dayIdx = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> 1; java.util.Calendar.TUESDAY -> 2; java.util.Calendar.WEDNESDAY -> 3
                java.util.Calendar.THURSDAY -> 4; java.util.Calendar.FRIDAY -> 5; java.util.Calendar.SATURDAY -> 6
                else -> 0
            }
            val myId = me?.id ?: ""
            val today = data.timetable.filter { it.day == dayIdx && (it.teacherId.isBlank() || it.teacherId == myId) }.sortedBy { it.start }
            if (today.isNotEmpty()) return today.mapNotNull { p ->
                val c = data.classes.firstOrNull { it.id == p.classId } ?: return@mapNotNull null
                val subj = p.subjectId?.let { sid -> data.subjects.firstOrNull { it.id == sid }?.name }
                    ?: p.notes.ifBlank { "Lesson" }
                ScheduleItem("${p.start} – ${p.end}", subj, classLabel(c))
            }
            // Fallback: light preview built from assignments
            val slots = listOf("08:00 – 09:00", "10:00 – 11:00", "11:15 – 12:15", "13:00 – 14:00")
            return myClasses.take(slots.size).mapIndexedNotNull { i, c ->
                val subj = mySubjectsFor(c.id).firstOrNull() ?: return@mapIndexedNotNull null
                ScheduleItem(slots[i], subj.name, classLabel(c))
            }
        }

    var pendingSchoolName by mutableStateOf<String?>(null)

    /** Import the school setup exported by the admin (structure only — local marks/comments are kept). */
    fun importSchoolData(): String {
        val dir = exportDir ?: return "Cannot access device storage"
        val f = dir.resolve("school-data-from-admin.json")
        if (!f.exists()) return "File not found: ${f.name} — copy it from the admin computer first (USB/Bluetooth)."
        return try {
            val incomingCheck = store.loadSchoolConfigFresh(f.toPath())
            val incomingName = incomingCheck.school.name.ifBlank { incomingCheck.school.id }
            val sameSchool = data.students.isEmpty() && data.marks.isEmpty() ||
                SchoolIsolation.sameSchool(incomingCheck, data)
            if (!sameSchool) {
                pendingSchoolName = incomingName
                "This file is from ${pendingSchoolName} — but this phone already holds ${data.school.name.ifBlank { "another school" }} with ${data.students.size} students. Importing would mix two schools! Tap 'Switch school' below to replace everything, or copy the correct file."
            } else {
                pendingSchoolName = null
                val merged = store.loadSchoolConfig(f.toPath(), data)
                // a mark/comment for a learner this school does not have is another
                // school's row: drop it here so it can never surface or be sent
                data = SchoolIsolation.prune(merged).first
                save()
                resolveMe()
                "✓ Connected to ${data.school.name.ifBlank { "your school" }} — imported ${data.classes.size} classes, ${data.students.size} students, ${data.subjects.size} subjects. Your marks were kept."
            }
        } catch (_: Exception) { "Import failed — file unreadable or wrong format." }
    }

    /** Replace ALL school data on this phone with the file's school (teacher switched schools). */
    fun confirmSchoolSwitch(): String {
        val dir = exportDir ?: return "Cannot access device storage"
        val f = dir.resolve("school-data-from-admin.json")
        if (!f.exists()) return "File not found."
        return try {
            data = store.loadSchoolConfigFresh(f.toPath())
            setMe(null)
            pendingSchoolName = null
            save()
            "✓ Switched to ${data.school.name.ifBlank { "the new school" }}. Old school's marks and students were cleared — pick your name on Home."
        } catch (_: Exception) { "Switch failed — file unreadable." }
    }

    // ── Cloud sync state ─────────────────────────────────────────────────
    val cloud = TeacherCloud(context)
    var cloudSchools by mutableStateOf<List<SchoolLite>>(emptyList())

    /** Pull school config from cloud into the normal import path (same rules as USB import). */
    fun importCloudConfig(payload: String): Pair<Boolean, String> {
        val dir = exportDir ?: return Pair(false, "Cannot access device storage")
        return try {
            File(dir, "school-data-from-admin.json").writeText(payload)
            val msg = importSchoolData()
            if (pendingSchoolName != null) Pair(false, msg)
            else Pair(true, msg)
        } catch (_: Exception) { Pair(false, "Import failed — wrong data format") }
    }

    /** Set when the phone has just put this teacher back on the staff list. */
    var staffNote by mutableStateOf("")

    /**
     * Field bug, November 2026: some teachers opened the app and found no classes
     * and no learners. Their teacher id had gone stale when a fresh setup arrived
     * from the console (or they were joined under a different record), and nothing
     * ever put them back on staff. This does, every time the app comes up and every
     * time a school file is imported.
     */
    fun resolveMe(): String? {
        val p = myJoin
        val r = com.derycode.srs.core.model.StaffLink.resolve(
            data, meId,
            name = p?.name ?: "",
            phone = p?.phone ?: "",
            cloudUid = p?.cloudUid ?: "",
            classIds = p?.classIds ?: emptyList(),
            subjectIds = p?.subjectIds ?: emptyList()
        )
        if (r.data !== data) { data = r.data; save() }
        if (r.teacherId != null && r.teacherId != meId) setMe(r.teacherId)
        staffNote = r.note
        return r.teacherId
    }

    /** A file the head teacher can open on the console: no cloud account needed. */
    fun marksBundleFile(): File? {
        val payload = buildMarksBundle() ?: return null
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(java.util.Date())
        val who = (me?.name ?: "teacher").replace(Regex("[^A-Za-z0-9]+"), "-")
        val f = File(dir, "marks-$who-$stamp.json")
        f.writeText(payload)
        return f
    }

    /** Build the same marks bundle the USB export creates, as a JSON string for the cloud. */
    fun buildMarksBundle(): String? {
        val dir = exportDir ?: return null
        val target = File(dir, "cloud-bundle-temp.json")
        return try {
            val mine = SchoolIsolation.scopedMarks(data)
            if (mine.isEmpty()) return null
            store.exportBundle(target.toPath(), data.school.name, deviceId = "teacher-cloud", marks = mine,
                students = data.students, enrollmentRequests = data.enrollmentRequests,
                dutyRecords = data.dutyRecords, gatePasses = data.gatePasses, attendance = data.attendance)
            target.readText().also { target.delete() }
        } catch (_: Exception) { null }
    }

    init {
        data = if (Files.exists(file)) {
            // v1.13.10 — startup must NEVER crash: a phone whose school-data.json
            // was truncated (crash mid-save / disk-full on an older version) used to
            // throw here on every launch ("app keeps stopping"). Now we quarantine the
            // bad file, fall back to the newest readable backup, and only then start clean.
            try {
                store.load(file)
            } catch (e: Exception) {
                CrashTracker.note(context, "school-data.json was unreadable (${e.javaClass.simpleName}: ${e.message?.take(200)}). Quarantined and recovered.")
                quarantineCorrupt()
                loadNewestUsableBackup() ?: freshStart()
            }
        } else freshStart()
    }

    /** Default configuration for a brand-new install (no demo data). */
    private fun freshStart(): SchoolData = SchoolData(
        components = Seeds.COMPONENTS,
        subjects = Seeds.ALL_SUBJECTS,
        gradingSchemes = com.derycode.srs.core.grading.GradingSchemes.ALL
    ).also { store.save(file, it) }

    /** Keep the unreadable file for recovery instead of deleting it. */
    private fun quarantineCorrupt() = try {
        val stamp = System.currentTimeMillis()
        Files.move(file, file.resolveSibling("school-data-corrupt-$stamp.json"), StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) { e.printStackTrace() }

    /** Try the rotating backups, newest first; null if none is readable. */
    private fun loadNewestUsableBackup(): SchoolData? {
        val dir = file.resolveSibling("backups").toFile()
        val backups = dir.listFiles { f -> f.name.startsWith("backup-") }?.sortedByDescending { it.name } ?: return null
        for (b in backups) {
            try { return store.load(b.toPath()) } catch (e: Exception) { e.printStackTrace() }
        }
        return null
    }

    var updateAvailable by mutableStateOf<UpdateInfo?>(null)
    var scannedInvite by mutableStateOf("")   // filled by the QR scanner

    fun checkForUpdate() {
        Thread {
            val u = UpdateChecker.checkBlocking(BuildConfig.VERSION_NAME)
            if (u != null) updateAvailable = u
        }.start()
    }

    /**
     * Takes over the school setup pulled by a single scan: school, classes,
     * streams, subjects, staff, timetable, calendar, grading and the learners.
     * Marks already on this phone for the SAME student/subject/term are kept.
     */
    /**
     * Joining a school by code. If this code opens a DIFFERENT school, the incoming
     * setup replaces everything: not one mark, comment or learner of the old school
     * is carried across, so a second school's information can never show up here.
     * The old school is archived to a file first, so nothing a teacher typed is lost.
     */
    /**
     * Joining with the school code puts this teacher on staff straight away: their
     * record, role and classes exist locally before anything else runs, so every
     * screen works immediately. No waiting for the head teacher to assign a role.
     */
    fun joinAsStaff(
        name: String,
        roleName: String = "TEACHER",
        phone: String = "",
        cloudUid: String = "",
        classIds: List<String> = emptyList(),
        subjectIds: List<String> = emptyList(),
        photoPath: String? = null
    ): String? {
        val role = runCatching { Role.valueOf(roleName.trim().uppercase()) }.getOrDefault(Role.TEACHER)
        val joined = com.derycode.srs.core.model.TeacherJoin.apply(
            data, name, role, phone = phone, cloudUid = cloudUid,
            classIds = classIds, subjectIds = subjectIds, photoPath = photoPath
        )
        data = joined.data
        save()
        val profile = JoinProfile(
            name = joined.data.teachers.first { it.id == joined.teacherId }.name,
            role = role.name, phone = phone, cloudUid = cloudUid,
            classIds = classIds,
            classNames = classIds.mapNotNull { id -> joined.data.classes.firstOrNull { it.id == id }?.name },
            subjectIds = subjectIds,
            subjectNames = subjectIds.mapNotNull { id -> joined.data.subjects.firstOrNull { it.id == id }?.name }
        )
        myJoin = profile
        JoinProfile.write(context.filesDir, profile)
        setMe(joined.teacherId)
        return joined.teacherId
    }

    fun adoptSchoolSetup(incoming: SchoolData, code: String) {
        val previous = data
        val switching = previous.students.isNotEmpty() && !SchoolIsolation.sameSchool(incoming, previous)
        if (switching) archivePreviousSchool(previous)
        data = SchoolIsolation.adopt(incoming, previous)
        // The console's setup replaces the staff list. Put this teacher back on it
        // straight away, or a pull would take their classes and access away.
        myJoin?.let { p ->
            val ids = p.classIds.filter { cid -> data.classes.any { it.id == cid } }
                .ifEmpty { p.classNames.mapNotNull { n -> data.classes.firstOrNull { it.name.equals(n, true) }?.id } }
            val subIds = p.subjectIds.filter { sid -> data.subjects.any { it.id == sid } }
                .ifEmpty { p.subjectNames.mapNotNull { n -> data.subjects.firstOrNull { it.name.equals(n, true) }?.id } }
            val role = runCatching { Role.valueOf(p.role) }.getOrDefault(Role.TEACHER)
            val rejoined = com.derycode.srs.core.model.TeacherJoin.apply(
                data, p.name, role, phone = p.phone, cloudUid = p.cloudUid,
                classIds = ids, subjectIds = subIds
            )
            data = rejoined.data
            setMe(rejoined.teacherId)
        }
        save()
        resolveMe()
        codeFile.writeText(code.trim().uppercase())
        schoolCode = code.trim().uppercase()
        if (switching) lastSwitchNote = "Replaced " + previous.school.name + " (" + previous.marks.size + " marks archived on this phone)."
    }

    /** Notes for the UI after a school switch, cleared when the teacher reads it. */
    var lastSwitchNote by mutableStateOf("")

    private fun archivePreviousSchool(previous: SchoolData) {
        try {
            val slug = previous.school.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "school" }
            val f = java.io.File(context.filesDir, "archived-school-" + slug + ".json")
            store.save(f.toPath(), previous)
        } catch (_: Exception) { /* archiving must never block the switch */ }
    }

    /** The invite code this phone joined with — kept so sending marks never asks again. */
    var schoolCode by mutableStateOf(
        java.io.File(context.filesDir, "school-code.txt").takeIf { it.exists() }?.readText()?.trim().orEmpty()
    )
        private set

    private val codeFile = java.io.File(context.filesDir, "school-code.txt")

    fun refresh() { data = data }

    fun save() {
        store.save(file, data)
        store.backup(file)   // v1.13.10: rotating local backup so a corrupt main file can be recovered
    }

    fun upsertMark(m: Mark) {
        // newest-wins: every row with this key is replaced, never left beside the edit
        val stamped = m.copy(updatedAt = System.currentTimeMillis())
        data = data.copy(marks = com.derycode.srs.core.entry.MarkLedger.upsert(data.marks, m, stamped.updatedAt))
        save()
    }

    fun addComment(c: Comment) {
        data = data.copy(comments = data.comments + c)
        save()
    }

    /**
     * One remark per learner, term and type: writing again replaces the old text
     * instead of stacking a second copy the report card has to choose between.
     */
    fun upsertComment(c: Comment) {
        val key = "${c.studentId}|${c.termId}|${c.type}|${c.subjectId.orEmpty()}"
        val rest = data.comments.filter {
            "${it.studentId}|${it.termId}|${it.type}|${it.subjectId.orEmpty()}" != key
        }
        data = data.copy(comments = rest + c)
        save()
    }

    /** Bundle file for USB / Bluetooth transfer to the admin console. */
    fun exportBundle(fileName: String = "teacher-bundle.json"): String? {
        val dir = exportDir ?: return null
        val target = dir.toPath().resolve(fileName)
        store.exportBundle(target, data.school.name, deviceId = "teacher-phone", marks = data.marks,
            students = data.students, enrollmentRequests = data.enrollmentRequests,
            dutyRecords = data.dutyRecords, gatePasses = data.gatePasses, attendance = data.attendance,
            comments = data.comments)
        return target.toString()
    }

    // ── Teacher-on-duty module ─────────────────────────────────────────────
    val todayKey: String get() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    val myDutyToday: DutyRecord? get() = data.dutyRecords.firstOrNull { it.teacherId == (me?.id ?: "") && it.date == todayKey }

    fun startDuty(role: String) {
        val id = me?.id ?: return
        val rec = DutyRecord(id = "dr-$id-$todayKey", teacherId = id, date = todayKey, role = role)
        data = data.copy(dutyRecords = data.dutyRecords.filter { it.id != rec.id } + rec)
        save()
    }

    fun endDuty() {
        val id = me?.id ?: return
        val today = todayKey
        data = data.copy(dutyRecords = data.dutyRecords.filterNot { it.teacherId == id && it.date == today })
        save()
    }

    fun addIncident(note: String) {
        val rec = myDutyToday ?: return
        val updated = rec.copy(incidents = rec.incidents + DutyIncident(System.currentTimeMillis(), note.trim()))
        data = data.copy(dutyRecords = data.dutyRecords.filter { it.id != rec.id } + updated)
        save()
    }

    fun issueGatePass(studentId: String, reason: String, destination: String, expectedBack: String) {
        val id = me?.id ?: return
        val pass = GatePass(id = "gp-$studentId-${System.currentTimeMillis()}", studentId = studentId, teacherId = id,
            reason = reason, destination = destination, outAt = System.currentTimeMillis(), expectedBack = expectedBack)
        data = data.copy(gatePasses = data.gatePasses + pass)
        save()
    }

    fun markReturned(passId: String) {
        data = data.copy(gatePasses = data.gatePasses.map { if (it.id == passId) it.copy(returnedAt = System.currentTimeMillis()) else it })
        save()
    }

    // ── Attendance ─────────────────────────────────────────────────────────
    fun attendanceFor(classId: String, date: String): Map<String, String> =
        data.attendance.filter { it.classId == classId && it.date == date }.associate { it.studentId to it.status }

    fun setAttendance(studentId: String, classId: String, date: String, status: String) {
        val id = "att-$studentId-$date"
        val rec = AttendanceRecord(id = id, studentId = studentId, classId = classId, date = date, status = status, recordedBy = me?.id ?: "")
        data = data.copy(attendance = data.attendance.filter { it.id != id } + rec)
        save()
    }

    fun markAllPresent(classId: String, date: String, studentIds: List<String>) {
        val recs = studentIds.map { AttendanceRecord(id = "att-$it-$date", studentId = it, classId = classId, date = date, status = "PRESENT", recordedBy = me?.id ?: "") }
        val ids = recs.map { it.id }.toSet()
        data = data.copy(attendance = data.attendance.filter { it.id !in ids } + recs)
        save()
    }

    fun registerStudent(firstName: String, lastName: String, sex: String, classId: String, guardianName: String, guardianPhone: String): String {
        if (firstName.isBlank() || lastName.isBlank()) return "Fill the first and last name."
        if (classId.isBlank()) return "Pick a class."
        val year = data.academicYears.firstOrNull { it.currentTermId != null } ?: data.academicYears.firstOrNull()
        val sid = "stu-${System.currentTimeMillis()}"
        val stu = Student(id = sid, admissionNo = "", firstName = firstName.trim(), lastName = lastName.trim(),
            sex = sex, guardianName = guardianName.trim(), guardianPhone = guardianPhone.trim())
        val req = EnrollmentRequest(id = "er-$sid", student = stu, academicYearId = year?.id ?: "", classId = classId,
            teacherId = me?.id ?: "", status = "PENDING", createdAt = System.currentTimeMillis())
        data = data.copy(students = data.students + stu, enrollmentRequests = data.enrollmentRequests + req)
        save()
        return "✓ ${stu.firstName} ${stu.lastName} registered — PENDING until the admin imports your bundle."
    }

    /** Marks entered but not yet bundled — simple local status. */
    val pendingCount: Int get() = data.marks.count { it.updatedAt > 0 && it.sheetState == MarkSheetState.DRAFT }
}

fun classLabel(c: SchoolClass): String = if (c.stream.isBlank()) c.name else "${c.name} – ${c.stream}"
fun classShort(c: SchoolClass): String {
    val n = c.name.replace(Regex("[^0-9]"), "")
    val prefix = when (c.level) { Level.PRIMARY -> "P"; Level.A_LEVEL -> "S"; else -> "S" }
    return "$prefix${n.ifBlank { "?" }}${c.stream.take(1).uppercase()}"
}

// ─────────────────────────────────────────────────────────────────────────────
// Theme — light, airy dashboard with bold colored accent system
// ─────────────────────────────────────────────────────────────────────────────

internal val AppTypography = Typography(
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),      // Buttons
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),   // Text fields
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Bold),       // Top bars
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
)

// ─── Theme tokens (live) ─────────────────────────────────────────────────────
// These are mutable so the phone app restyles instantly when the head teacher
// picks a theme on the console and the school setup reaches the phone.
internal var BG by mutableStateOf(Color(0xFFF1F5FE))       // app background
internal var NAVY by mutableStateOf(Color(0xFF16213E))     // primary text/icons
internal var CARD by mutableStateOf(Color(0xFFFFFFFF))     // cards
internal var CARD_ALT by mutableStateOf(Color(0xFFEFF3FB)) // inputs, pills
internal var STROKE by mutableStateOf(Color(0xFFE3E9F7))   // hairlines
internal var BLUE by mutableStateOf(Color(0xFF3B82F6))     // primary action
internal var PURPLE by mutableStateOf(Color(0xFF8B5CF6))
internal var TEAL by mutableStateOf(Color(0xFF14B8A6))
internal var ORANGE by mutableStateOf(Color(0xFFF59E0B))
internal var GREEN by mutableStateOf(Color(0xFF22C55E))
internal var RED by mutableStateOf(Color(0xFFEF4444))
internal var PINK by mutableStateOf(Color(0xFFEC4899))
internal var MUTED by mutableStateOf(Color(0xFF6C7793))
internal var GOOD by mutableStateOf(GREEN)
internal var ACCENT by mutableStateOf(BLUE)
/** True when the school's theme is a dark one. */
internal var DARK_THEME by mutableStateOf(false)

internal val AVATAR_PALETTE: List<Color>
    get() = listOf(BLUE, PINK, PURPLE, GREEN, RED, ORANGE, TEAL, Color(0xFF6366F1))

internal fun avatarColor(seed: String) = AVATAR_PALETTE[kotlin.math.abs(seed.hashCode()) % AVATAR_PALETTE.size]

/**
 * Applies the school's chosen theme to the whole phone app.
 * Same names as the Admin console, so one choice covers console + phone.
 */
internal fun applyPhoneTheme(name: String) {
    val p = phonePalette(name)
    DARK_THEME = p.dark
    BG = p.bg
    CARD = p.card
    CARD_ALT = p.cardAlt
    STROKE = p.stroke
    NAVY = p.ink
    MUTED = p.muted
    BLUE = p.accent
    ACCENT = p.accent
    TEAL = p.teal
    GREEN = p.green
    RED = p.red
    ORANGE = p.orange
    PURPLE = p.purple
    PINK = p.pink
    GOOD = p.green
}

/** Display-only quick grade band for dashboard color coding (NOT the official PLE/UCE/UACE grading — reports use ResultEngine + the school's real grading scheme). */
internal fun quickBandColor(pct: Double): Color = when {
    pct >= 80 -> GREEN
    pct >= 70 -> TEAL
    pct >= 60 -> ORANGE
    else -> RED
}
internal fun quickLetter(pct: Double): String = when {
    pct >= 90 -> "A"; pct >= 80 -> "B+"; pct >= 70 -> "B"; pct >= 60 -> "C"; pct >= 50 -> "D"; else -> "F"
}

// ─────────────────────────────────────────────────────────────────────────────
// Navigation
// ─────────────────────────────────────────────────────────────────────────────

sealed class Route {
    object Home : Route()
    object Classes : Route()
    data class ClassDetail(val classId: String) : Route()
    data class EnterMarks(val classId: String, val subjectId: String? = null) : Route()
    object Assessments : Route()
    data class Students(val classId: String? = null) : Route()
    data class StudentDetail(val studentId: String) : Route()
    object ScanCode : Route()
    object More : Route()
    object Comments : Route()
    object Sync : Route()
    object Duty : Route()
    object Timetable : Route()
    object Register : Route()
    object Attendance : Route()
    object Support : Route()
    object Cloud : Route()
    object Tutorial : Route()
    object Themes : Route()
    object Insights : Route()
    object Results : Route()
    object ReportPreview : Route()
    object Calendar : Route()
    object Profile : Route()
}

private enum class Tab(val route: Route, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME(Route.Home, "Home", Icons.Filled.Home),
    CLASSES(Route.Classes, "Classes", Icons.Filled.MenuBook),
    ASSESSMENTS(Route.Assessments, "Assessments", Icons.Filled.Assignment),
    STUDENTS(Route.Students(null), "Students", Icons.Filled.People),
    MORE(Route.More, "More", Icons.Filled.MoreHoriz)
}

@Composable
fun TeacherApp(state: TeacherState) {
    // Live theme: whenever the school setup (from the console) carries a theme,
    // the WHOLE phone app restyles — background, cards, text, buttons, badges.
    LaunchedEffect(state.phoneTheme) { applyPhoneTheme(state.phoneTheme) }
    // A stale teacher id must never leave someone staring at an empty app.
    LaunchedEffect(Unit) { state.resolveMe() }

    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val current = stack.last()
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun switchTab(r: Route) { stack.clear(); stack.add(r) }
    // "The app should ask for update" — a clear prompt the moment a newer build is known.
    var askUpdate by remember { mutableStateOf(true) }
    val upd = state.updateAvailable
    if (upd != null && askUpdate) {
        AlertDialog(
            onDismissRequest = { askUpdate = false },
            title = { Text("Update available", fontWeight = FontWeight.Bold) },
            text = { Text("SRM Teacher ${upd.teacherVersion} is ready. Install it to get the latest fixes and features. Your marks and classes are kept.") },
            confirmButton = {
                TextButton({ askUpdate = false; push(Route.Support) }) { Text("Update now", color = BLUE, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton({ askUpdate = false }) { Text("Later", color = MUTED) } }
        )
    }

    val activeTab = when (current) {
        is Route.Home -> Tab.HOME
        is Route.Classes, is Route.ClassDetail, is Route.EnterMarks -> Tab.CLASSES
        is Route.Assessments -> Tab.ASSESSMENTS
        is Route.Students, is Route.StudentDetail -> Tab.STUDENTS
        is Route.More, is Route.Comments, is Route.Sync, is Route.Duty, is Route.Register, is Route.Attendance, is Route.Support, is Route.Cloud, is Route.ScanCode, is Route.Timetable, is Route.Tutorial, is Route.Themes, is Route.Insights, is Route.Results, is Route.ReportPreview, is Route.Calendar, is Route.Profile -> Tab.MORE
    }

    // ── Side menu (drawer): every section reachable in one tap ──
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = current !is Route.EnterMarks,
        drawerContent = {
            ModalDrawerSheet {
                // Header stays pinned; the list below scrolls so the LAST items
                // (Support & Licence, How-to Guide) are never cut off on small phones.
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.srs_logo),
                        contentDescription = "DeryCode School Report System",
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("SRS Teacher", color = NAVY, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text(state.data.school.name.ifBlank { "School Report Maker" }, color = MUTED, fontSize = 13.sp, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(6.dp))
                DrawerItem(Icons.Filled.Home, "Home", current is Route.Home) { switchTab(Route.Home); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.MenuBook, "Classes & Marks", current is Route.Classes || current is Route.ClassDetail || current is Route.EnterMarks) { switchTab(Route.Classes); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.Assessment, "Assessments", current is Route.Assessments) { switchTab(Route.Assessments); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.People, "Students", current is Route.Students || current is Route.StudentDetail) { switchTab(Route.Students(null)); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.Comment, "Class Teacher Comments", current is Route.Comments) { switchTab(Route.Comments); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.Shield, "Duty Desk", current is Route.Duty) { switchTab(Route.Duty); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.PersonAdd, "Register student", current is Route.Register) { switchTab(Route.Register); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.EventAvailable, "Attendance", current is Route.Attendance) { switchTab(Route.Attendance); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.Insights, "Term Insight", current is Route.Insights) { switchTab(Route.Insights); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.EmojiEvents, "Ranked Results", current is Route.Results) { switchTab(Route.Results); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.Description, "Report Preview", current is Route.ReportPreview) { switchTab(Route.ReportPreview); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.Event, "Calendar", current is Route.Calendar) { switchTab(Route.Calendar); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.Palette, "Themes", current is Route.Themes) { switchTab(Route.Themes); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.Badge, "My Profile", current is Route.Profile) { switchTab(Route.Profile); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.Cloud, "Cloud Sync", current is Route.Cloud) { switchTab(Route.Cloud); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.Sync, "Sync & Export", current is Route.Sync) { switchTab(Route.Sync); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.SupportAgent, "Support & Licence", current is Route.Support) { switchTab(Route.Support); scope.launch { drawerState.close() } }
                DrawerItem(Icons.Filled.School, "How-to Guide", current is Route.Tutorial) { switchTab(Route.Tutorial); scope.launch { drawerState.close() } }
                Spacer(Modifier.height(12.dp))
                }
                HorizontalDivider()
                Text("DeryCode · Simple · Fast · Reliable", color = MUTED, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }
    ) {
    Column(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(10.dp))
            TopBar(state, current, onBack = { pop() }, onProfile = { push(Route.Profile) }, onMenu = { scope.launch { drawerState.open() } }, onBell = { push(Route.Support) })
            Spacer(Modifier.height(10.dp))
            state.updateAvailable?.let { u ->
                UpdateBanner(u) { push(Route.Support) }
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.weight(1f)) {
                when (val r = current) {
                    is Route.Home -> HomeScreen(state, onOpenClass = { push(Route.ClassDetail(it)) }, onQuickAction = { push(it) })
                    is Route.Classes -> ClassesListScreen(state, onOpenClass = { push(Route.ClassDetail(it)) })
                    is Route.Timetable -> TimetableScreen(state)
                    is Route.ClassDetail -> ClassDetailScreen(state, r.classId,
                        onEnterMarks = { subj -> push(Route.EnterMarks(r.classId, subj)) },
                        onViewStudents = { push(Route.Students(r.classId)) },
                        onOpenStudent = { push(Route.StudentDetail(it)) })
                    is Route.EnterMarks -> EnterMarksScreen(state, r.classId, r.subjectId, onDone = { pop() })
                    is Route.Assessments -> AssessmentsOverviewScreen(state, onOpenClass = { push(Route.ClassDetail(it)) })
                    is Route.Students -> StudentsListScreen(state, r.classId, onOpenStudent = { push(Route.StudentDetail(it)) })
                    is Route.StudentDetail -> StudentDetailScreen(state, r.studentId, onEnterMarks = { clsId -> push(Route.EnterMarks(clsId)) })
                    is Route.More -> MoreScreen(state, onOpen = { push(it) })
                    is Route.Comments -> CommentsScreen(state)
                    is Route.Sync -> SyncScreen(state)
                    is Route.Duty -> DutyScreen(state)
                    is Route.Attendance -> AttendanceScreen(state)
                    is Route.Register -> RegisterScreen(state)
                    is Route.Support -> SupportScreen(state)
                    is Route.Tutorial -> TutorialScreen(state)
                    is Route.Cloud -> CloudScreen(state, onScan = { push(Route.ScanCode) })
                    is Route.Themes -> ThemeScreen(state)
                    is Route.Insights -> InsightsScreen(state, onOpenClass = { push(Route.ClassDetail(it)) })
                    is Route.Results -> ResultsScreen(state, onOpenStudent = { push(Route.StudentDetail(it)) })
                    is Route.ReportPreview -> ReportPreviewScreen(state)
                    is Route.Calendar -> CalendarScreen(state)
                    is Route.Profile -> ProfileScreen(state, onOpenThemes = { push(Route.Themes) })
                    is Route.ScanCode -> QrScannerScreen(
                        onCode = { code -> state.scannedInvite = code; pop() },
                        onClose = { pop() })
                }
            }
        }
        BottomNav(activeTab) { tab -> switchTab(tab.route) }
    }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) BLUE else NAVY, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, color = if (selected) BLUE else NAVY, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun TopBar(state: TeacherState, route: Route, onBack: () -> Unit, onProfile: () -> Unit, onMenu: () -> Unit = {}, onBell: () -> Unit = {}) {
    val (title, showBack) = titleFor(state, route)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CARD)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = NAVY)
            }
            Spacer(Modifier.width(6.dp))
        } else {
            IconButton(onClick = onMenu, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(CARD)) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = NAVY, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = NAVY, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!showBack) Text(state.data.school.name.ifBlank { "SRS Teacher" }, color = MUTED, fontSize = 14.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
        }
        if (!showBack) {
            Box {
                IconButton(onClick = onBell, modifier = Modifier.size(40.dp).clip(CircleShape).background(CARD)) {
                    Icon(Icons.Filled.Notifications, contentDescription = if (state.updateAvailable != null) "New update available" else "Notifications", tint = NAVY)
                }
                if (state.updateAvailable != null) {
                    Box(Modifier.size(9.dp).align(Alignment.TopEnd).clip(CircleShape).background(RED).border(1.5.dp, CARD, CircleShape))
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(BLUE.copy(alpha = 0.15f))
                    .clickable(onClick = onProfile),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = "Profile", tint = BLUE, modifier = Modifier.size(21.dp))
            }
        }
    }
}

private fun titleFor(state: TeacherState, route: Route): Pair<String, Boolean> = when (route) {
    is Route.Home -> "SRS Teacher" to false
    is Route.Classes -> "My Classes" to false
    is Route.ClassDetail -> {
        val c = state.data.classes.firstOrNull { it.id == route.classId }
        val subj = state.mySubjectsFor(route.classId).firstOrNull()?.name ?: ""
        (if (subj.isNotBlank()) "$subj – ${c?.let { classShort(it) } ?: ""}" else c?.let { classLabel(it) } ?: "Class") to true
    }
    is Route.EnterMarks -> "Enter Marks" to true
    is Route.Assessments -> "Assessments" to false
    is Route.Students -> "Students" to false
    is Route.StudentDetail -> "Student Details" to true
    is Route.ScanCode -> "Scan code" to false
    is Route.More -> "More" to false
    is Route.Comments -> "Class Comments" to true
    is Route.Sync -> "Sync & Support Data" to true
    is Route.Duty -> "Teacher on Duty" to true
    is Route.Timetable -> "My Timetable" to true
    is Route.Register -> "Register Student" to true
    is Route.Attendance -> "Attendance" to true
    is Route.Support -> "Support & Licence" to true
    is Route.Tutorial -> "How-to Guide" to true
    is Route.Cloud -> "Cloud Sync" to true
    is Route.Themes -> "Themes" to true
    is Route.Insights -> "Term Insight" to true
    is Route.Results -> "Ranked Results" to true
    is Route.ReportPreview -> "Report Preview" to true
    is Route.Calendar -> "Calendar" to true
    is Route.Profile -> "My Profile" to true
}

@Composable
private fun BottomNav(active: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CARD).border(BorderStroke(1.dp, STROKE)).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Tab.values().forEach { tab ->
            val selected = tab == active
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .let { if (selected) it.background(BLUE.copy(alpha = 0.15f)) else it }
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(tab.icon, contentDescription = tab.label, tint = if (selected) BLUE else MUTED, modifier = Modifier.size(26.dp))
                Text(tab.label, color = if (selected) BLUE else MUTED, fontSize = 12.sp, maxLines = 1, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun UpdateBanner(u: UpdateInfo, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF14304F), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Update ${u.teacherVersion} available", color = GREEN, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Open More \u2192 Support to install.", color = Color(0xFFC9D8F2), fontSize = 14.sp)
        }
        Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = GREEN), modifier = Modifier.height(42.dp)) {
            Text("Get it", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI atoms
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CardBox(padding: Int = 20, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp), ambientColor = STROKE, spotColor = STROKE)
            .background(CARD, RoundedCornerShape(20.dp))
            .border(1.dp, STROKE, RoundedCornerShape(20.dp))
            .padding(padding.dp),
        content = content
    )
}

@Composable
internal fun Cell(text: String, color: Color = NAVY, bold: Boolean = false, modifier: Modifier = Modifier) =
    Text(text, color = color, fontSize = 17.sp,
         fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = modifier)

@Composable
internal fun Avatar(text: String, color: Color, size: Int = 40) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        Text(text.take(2).uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = (size * 0.32).sp)
    }
}

@Composable
internal fun ScoreBadge(pct: Double) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(quickBandColor(pct).copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(pct.toInt().toString(), color = quickBandColor(pct), fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

internal fun fmt1(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else String.format("%.1f", d)
