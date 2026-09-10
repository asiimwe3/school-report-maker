package com.derycode.srs.teacher

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*
import com.derycode.srs.core.seed.Seeds
import com.derycode.srs.core.store.JsonStore
import com.derycode.srs.core.support.UpdateInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashTracker.install(this)
        val state = TeacherState(this)
        state.checkForUpdate()   // auto-check on every launch; only asks GitHub, never sends data
        setContent { MaterialTheme(colorScheme = darkColorScheme(background = NAVY, surface = CARD)) { TeacherApp(state) } }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// State & storage — local file is the primary database (never requires internet)
// ─────────────────────────────────────────────────────────────────────────────

class TeacherState(context: Context) {
    private val store = JsonStore()
    private val file: Path = context.filesDir.toPath().resolve("school-data.json")
    val exportDir: File? = context.getExternalFilesDir(null)

    var data by mutableStateOf(SchoolData())
        private set

    private val meFile = File(context.filesDir, "teacher-id.txt")

    /** The teacher using this phone — picked once, remembered. */
    var meId by mutableStateOf<String?>(if (meFile.exists() && meFile.readText().isNotBlank()) meFile.readText().trim() else null)
        private set

    fun setMe(id: String?) {
        meId = id
        if (id == null) meFile.delete() else meFile.writeText(id)
        data = data
    }

    val me: Teacher? get() = meId?.let { id -> data.teachers.firstOrNull { it.id == id } }
    val myFirstName: String get() = me?.name?.split(" ")?.firstOrNull { it.isNotBlank() } ?: "Teacher"

    /** ALL classes this teacher is assigned to — a teacher can have multiple classes. */
    val myClasses: List<SchoolClass>
        get() = if (meId == null) data.classes.filter { it.active }
                else data.assignments.filter { it.teacherId == meId }
                    .mapNotNull { a -> data.classes.firstOrNull { c -> c.id == a.classId && c.active } }
                    .distinctBy { it.id }

    /** Subjects this teacher teaches in a given class (null subject assignment = class teacher → all). */
    fun mySubjectsFor(classId: String): List<Subject> {
        val cls = data.classes.firstOrNull { it.id == classId }
        val base = data.subjects.filter { it.level == cls?.level && it.active }
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
            val slots = listOf("08:00 – 09:00", "10:00 – 11:00", "11:15 – 12:15", "13:00 – 14:00")
            return myClasses.take(slots.size).mapIndexedNotNull { i, c ->
                val subj = mySubjectsFor(c.id).firstOrNull() ?: return@mapIndexedNotNull null
                ScheduleItem(slots[i], subj.name, classLabel(c))
            }
        }

    /** Import the school setup exported by the admin (structure only — local marks/comments are kept). */
    fun importSchoolData(): String {
        val dir = exportDir ?: return "Cannot access device storage"
        val f = dir.resolve("school-data-from-admin.json")
        if (!f.exists()) return "File not found: ${f.name} — copy it from the admin computer first (USB/Bluetooth)."
        return try {
            val imported = store.loadSchoolConfig(f.toPath(), data)
            data = imported
            save()
            "✓ Imported ${imported.classes.size} classes, ${imported.students.size} students, ${imported.subjects.size} subjects. Your marks were kept."
        } catch (_: Exception) { "Import failed — file unreadable or wrong format." }
    }

    init {
        data = if (Files.exists(file)) store.load(file)
        else SchoolData(
            components = Seeds.COMPONENTS,
            subjects = Seeds.ALL_SUBJECTS,
            gradingSchemes = com.derycode.srs.core.grading.GradingSchemes.ALL
        ).also { store.save(file, it) }   // clean start: config only, no demo students/marks
    }

    var updateAvailable by mutableStateOf<UpdateInfo?>(null)
        private set

    fun checkForUpdate() {
        Thread {
            val u = UpdateChecker.checkBlocking(BuildConfig.VERSION_CODE)
            if (u != null) updateAvailable = u
        }.start()
    }

    fun refresh() { data = data }

    fun save() = store.save(file, data)

    fun upsertMark(m: Mark) {
        val key = "${m.studentId}|${m.subjectId}|${m.termId}|${m.componentId}"
        val existing = data.marks.firstOrNull { "${it.studentId}|${it.subjectId}|${it.termId}|${it.componentId}" == key }
        val stamped = m.copy(updatedAt = System.currentTimeMillis())
        data = if (existing == null) data.copy(marks = data.marks + stamped)
        else data.copy(marks = data.marks.map { if (it === existing) stamped else it })
        save()
    }

    fun addComment(c: Comment) {
        data = data.copy(comments = data.comments + c)
        save()
    }

    /** Bundle file for USB / Bluetooth transfer to the admin console. */
    fun exportBundle(fileName: String = "teacher-bundle.json"): String? {
        val dir = exportDir ?: return null
        val target = dir.toPath().resolve(fileName)
        store.exportBundle(target, data.school.name, deviceId = "teacher-phone", marks = data.marks)
        return target.toString()
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
// Theme — dark navy dashboard, colored accent system
// ─────────────────────────────────────────────────────────────────────────────

internal val NAVY = Color(0xFF0A0E1A)
internal val CARD = Color(0xFF141B2E)
internal val CARD_ALT = Color(0xFF1B2438)
internal val STROKE = Color(0xFF232C42)
internal val BLUE = Color(0xFF3B82F6)
internal val PURPLE = Color(0xFF8B5CF6)
internal val TEAL = Color(0xFF14B8A6)
internal val ORANGE = Color(0xFFF59E0B)
internal val GREEN = Color(0xFF22C55E)
internal val RED = Color(0xFFEF4444)
internal val PINK = Color(0xFFEC4899)
internal val MUTED = Color(0xFF8B96AC)
internal val GOOD = GREEN
internal val ACCENT = BLUE

internal val AVATAR_PALETTE = listOf(BLUE, PINK, PURPLE, GREEN, RED, ORANGE, TEAL, Color(0xFF6366F1))
internal fun avatarColor(seed: String) = AVATAR_PALETTE[kotlin.math.abs(seed.hashCode()) % AVATAR_PALETTE.size]

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
    object More : Route()
    object Comments : Route()
    object Sync : Route()
    object Support : Route()
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
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val current = stack.last()
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun switchTab(r: Route) { stack.clear(); stack.add(r) }

    val activeTab = when (current) {
        is Route.Home -> Tab.HOME
        is Route.Classes, is Route.ClassDetail, is Route.EnterMarks -> Tab.CLASSES
        is Route.Assessments -> Tab.ASSESSMENTS
        is Route.Students, is Route.StudentDetail -> Tab.STUDENTS
        is Route.More, is Route.Comments, is Route.Sync, is Route.Support -> Tab.MORE
    }

    Column(Modifier.fillMaxSize().background(NAVY)) {
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(10.dp))
            TopBar(state, current, onBack = { pop() })
            Spacer(Modifier.height(10.dp))
            state.updateAvailable?.let { u ->
                UpdateBanner(u) { push(Route.More) }
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.weight(1f)) {
                when (val r = current) {
                    is Route.Home -> HomeScreen(state, onOpenClass = { push(Route.ClassDetail(it)) }, onQuickAction = { push(it) })
                    is Route.Classes -> ClassesListScreen(state, onOpenClass = { push(Route.ClassDetail(it)) })
                    is Route.ClassDetail -> ClassDetailScreen(state, r.classId,
                        onEnterMarks = { subj -> push(Route.EnterMarks(r.classId, subj)) },
                        onViewStudents = { push(Route.Students(r.classId)) })
                    is Route.EnterMarks -> EnterMarksScreen(state, r.classId, r.subjectId, onDone = { pop() })
                    is Route.Assessments -> AssessmentsOverviewScreen(state, onOpenClass = { push(Route.ClassDetail(it)) })
                    is Route.Students -> StudentsListScreen(state, r.classId, onOpenStudent = { push(Route.StudentDetail(it)) })
                    is Route.StudentDetail -> StudentDetailScreen(state, r.studentId)
                    is Route.More -> MoreScreen(state, onOpen = { push(it) })
                    is Route.Comments -> CommentsScreen(state)
                    is Route.Sync -> SyncScreen(state)
                    is Route.Support -> SupportScreen(state)
                }
            }
        }
        BottomNav(activeTab) { tab -> switchTab(tab.route) }
    }
}

@Composable
private fun TopBar(state: TeacherState, route: Route, onBack: () -> Unit) {
    val (title, showBack) = titleFor(state, route)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.width(4.dp))
        } else {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(BLUE), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.School, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!showBack) Text(state.data.school.name.ifBlank { "SRS Teacher" }, color = MUTED, fontSize = 10.sp, maxLines = 1)
        }
        if (!showBack) {
            Box {
                IconButton(onClick = {}, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = Color.White)
                }
                if (state.updateAvailable != null) {
                    Box(Modifier.size(8.dp).align(Alignment.TopEnd).clip(CircleShape).background(RED))
                }
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
    is Route.More -> "More" to false
    is Route.Comments -> "Class Comments" to true
    is Route.Sync -> "Sync & Support Data" to true
    is Route.Support -> "Support & Licence" to true
}

@Composable
private fun BottomNav(active: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CARD).padding(vertical = 8.dp, horizontal = 6.dp),
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
                Icon(tab.icon, contentDescription = tab.label, tint = if (selected) BLUE else MUTED, modifier = Modifier.size(21.dp))
                Text(tab.label, color = if (selected) BLUE else MUTED, fontSize = 9.sp, maxLines = 1)
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
            Text("Update ${u.versionName} available", color = GREEN, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Open More \u2192 Support to install.", color = MUTED, fontSize = 10.sp)
        }
        Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = GREEN), modifier = Modifier.height(32.dp)) {
            Text("Get it", color = NAVY, fontSize = 11.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI atoms
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CardBox(padding: Int = 14, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(16.dp)).padding(padding.dp),
        content = content
    )
}

@Composable
internal fun Cell(text: String, color: Color = Color.White, bold: Boolean = false, modifier: Modifier = Modifier) =
    Text(text, color = color, fontSize = 13.sp,
         fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = modifier)

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
        Text(pct.toInt().toString(), color = quickBandColor(pct), fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

internal fun fmt1(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else String.format("%.1f", d)
