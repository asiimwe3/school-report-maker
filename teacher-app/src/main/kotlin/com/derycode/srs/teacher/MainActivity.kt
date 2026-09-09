package com.derycode.srs.teacher

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*
import com.derycode.srs.core.results.ResultEngine
import com.derycode.srs.core.seed.Seeds
import com.derycode.srs.core.store.JsonStore
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = TeacherState(this)
        setContent { MaterialTheme { TeacherApp(state) } }
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

    init {
        data = if (Files.exists(file)) store.load(file) else demoData().also { store.save(file, it) }
    }

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

    /** Marks entered but not yet bundled — simple local status (Section 36). */
    val pendingCount: Int get() = data.marks.count { it.updatedAt > 0 && it.sheetState == MarkSheetState.DRAFT }
}

/** Usable demo school on first open, so the app is never a blank screen. */
private fun demoData(): SchoolData {
    val components = Seeds.COMPONENTS
    val subjects = Seeds.subjectsFor(Level.O_LEVEL).take(6)
    val students = (1..12).map { i ->
        Student(
            id = "st-$i", admissionNo = "2027/$i",
            firstName = listOf("Asiimwe", "Kato", "Natasha", "Mwesigwa", "Grace", "Tobby", "Ainembabazi", "Mugisha")[i % 8],
            lastName = listOf("Brian", "Divine", "Kemigisha", "Aheisibwe", "Kabuye")[i % 5],
            sex = if (i % 2 == 0) "M" else "F",
            guardianPhone = "+2567${1000000 + i * 137}"
        )
    }
    val term = Term(id = "t1", yearId = "y1", number = 1)
    return SchoolData(
        school = School(name = "Tooro Junior School", motto = "Knowledge is Light", headTeacher = "Mr. Okello"),
        academicYears = listOf(AcademicYear(id = "y1", year = "2026", terms = listOf(term), currentTermId = "t1")),
        classes = listOf(SchoolClass(id = "c1", name = "Senior 1", level = Level.O_LEVEL, stream = "East", classTeacherId = null)),
        subjects = subjects,
        students = students,
        enrollments = students.map { Enrollment(id = "e-${it.id}", studentId = it.id, academicYearId = "y1", classId = "c1") },
        components = components,
        gradingSchemes = listOf(com.derycode.srs.core.grading.GradingSchemes.UCE),
        marks = students.take(6).flatMap { s ->
            listOf("c-bot", "c-mid").map { comp ->
                Mark(
                    id = "m-${s.id}-$comp", studentId = s.id,
                    subjectId = subjects[0].id, termId = "t1", componentId = comp,
                    score = Random.nextDouble(30.0, 90.0), type = MarkType.VALUE, maxScore = 100
                )
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// UI theme helpers
// ─────────────────────────────────────────────────────────────────────────────

private val NAVY = Color(0xFF0B1220)
private val CARD = Color(0xFF111A2E)
private val ACCENT = Color(0xFF4F8CFF)
private val MUTED = Color(0xFF8B98B4)
private val GOOD = Color(0xFF3FCF8E)

private enum class Screen { HOME, MARKS, COMMENTS, SEND }

@Composable
fun TeacherApp(state: TeacherState) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    Column(Modifier.fillMaxSize().background(NAVY).padding(14.dp)) {
        Text("SRS Teacher", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("100% offline — marks stay on this phone", color = MUTED, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        when (screen) {
            Screen.HOME -> HomeScreen(state)
            Screen.MARKS -> MarksScreen(state)
            Screen.COMMENTS -> CommentsScreen(state)
            Screen.SEND -> SendScreen(state)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Screen.HOME to "Home", Screen.MARKS to "Marks",
                Screen.COMMENTS to "Comments", Screen.SEND to "Export"
            ).forEach { (s, label) ->
                Button(
                    onClick = { screen = s },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (screen == s) ACCENT else CARD,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text(label, fontSize = 11.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(14.dp)).padding(14.dp),
        content = content
    )
}

@Composable
private fun Cell(text: String, color: Color = Color.White, bold: Boolean = false, modifier: Modifier = Modifier) =
    Text(text, color = color, fontSize = 13.sp,
         fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = modifier)

// ─────────────────────────────────────────────────────────────────────────────
// Home (Section 45.1)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(state: TeacherState) {
    val d = state.data
    val term = d.academicYears.firstOrNull()?.terms?.firstOrNull { it.id == d.academicYears.firstOrNull()?.currentTermId }
        ?: d.academicYears.firstOrNull()?.terms?.firstOrNull()
    val entered = d.marks.size

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardBox {
            Cell("Welcome", MUTED)
            Cell(d.school.name, bold = true)
            Text("Term ${term?.number ?: 1} · ${d.academicYears.firstOrNull()?.year ?: ""}", color = MUTED, fontSize = 12.sp)
        }
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { Cell("${d.students.size}", ACCENT, true); Cell("students", MUTED) }
                Column(Modifier.weight(1f)) { Cell("$entered", ACCENT, true); Cell("marks entered", MUTED) }
                Column(Modifier.weight(1f)) { Cell("${d.subjects.size}", ACCENT, true); Cell("subjects", MUTED) }
            }
        }
        CardBox {
            Cell("Sync status", MUTED, true)
            Spacer(Modifier.height(4.dp))
            Cell(if (state.pendingCount > 0) "↑ ${state.pendingCount} changes waiting to export" else "✓ All changes saved", GOOD)
            Cell("This app has no internet permission — data physically cannot leave the phone.", MUTED)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Marks entry (Sections 14–16): class → subject → assessment → grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MarksScreen(state: TeacherState) {
    val d = state.data
    var classId by remember { mutableStateOf(d.classes.firstOrNull()?.id ?: "") }
    var subjectId by remember { mutableStateOf(d.subjects.firstOrNull()?.id ?: "") }
    var componentId by remember { mutableStateOf(d.components.firstOrNull()?.id ?: "") }
    val edits = remember { mutableStateMapOf<String, String>() }
    var msg by remember { mutableStateOf("") }

    val termId = d.academicYears.firstOrNull()?.currentTermId ?: d.academicYears.firstOrNull()?.terms?.firstOrNull()?.id ?: ""
    val cls = d.classes.firstOrNull { it.id == classId }
    val students = d.enrollments.filter { it.classId == classId }
        .mapNotNull { e -> d.students.firstOrNull { s -> s.id == e.studentId && s.status == StudentStatus.ACTIVE } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // pickers
        CardBox {
            Cell("Class", MUTED, true)
            Row(Modifier.horizontalScrollEnabled(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                d.classes.filter { it.active }.forEach { c ->
                    Button(onClick = { classId = c.id },
                        colors = ButtonDefaults.buttonColors(containerColor = if (classId == c.id) ACCENT else CARD)) {
                        Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 10.sp, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Cell("Subject", MUTED, true)
            Row(Modifier.horizontalScrollEnabled(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                d.subjects.filter { it.level == cls?.level && it.active }.forEach { s ->
                    Button(onClick = { subjectId = s.id },
                        colors = ButtonDefaults.buttonColors(containerColor = if (subjectId == s.id) ACCENT else CARD)) {
                        Text(s.name, fontSize = 10.sp, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Cell("Assessment", MUTED, true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                d.components.filter { it.levels.contains(cls?.level ?: Level.O_LEVEL) && it.active }.forEach { c ->
                    Button(onClick = { componentId = c.id },
                        colors = ButtonDefaults.buttonColors(containerColor = if (componentId == c.id) ACCENT else CARD)) {
                        Text(c.code, fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        }

        // entry grid (Section 14: keyboard-friendly)
        CardBox {
            Cell("Students (${students.size}) — type a value; ABS = absent", MUTED, true)
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(students) { s ->
                    val key = "${s.id}|$subjectId|$termId|$componentId"
                    val existing = d.marks.firstOrNull {
                        "${it.studentId}|${it.subjectId}|${it.termId}|${it.componentId}" == key
                    }
                    val field = edits[key] ?: (existing?.let {
                        when (it.type) {
                            MarkType.ABS -> "ABS"; MarkType.EXEMPT -> "EXEMPT"
                            else -> if (it.score == it.score.toLong().toDouble()) it.score.toLong().toString() else it.score.toString()
                        }
                    } ?: "")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Cell(s.fullName, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = field,
                            onValueChange = { edits[key] = it },
                            modifier = Modifier.width(110.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    var saved = 0
                    val max = d.subjects.firstOrNull { it.id == subjectId }?.maxMarks ?: 100
                    edits.forEach { (key, text) ->
                        val (sid, subj, tid, cid) = key.split("|")
                        val t = text.trim()
                        if (t.isEmpty()) return@forEach
                        val type: MarkType; val score: Double
                        when (t.uppercase()) {
                            "ABS" -> { type = MarkType.ABS; score = 0.0 }
                            "EXEMPT" -> { type = MarkType.EXEMPT; score = 0.0 }
                            else -> {
                                if (ResultEngine.validateMark(t, max, allowBlank = false) != null) return@forEach
                                type = MarkType.VALUE; score = t.toDouble()
                            }
                        }
                        state.upsertMark(Mark(
                            id = "m-$key", studentId = sid, subjectId = subj, termId = tid,
                            componentId = cid, score = score, type = type, maxScore = max
                        ))
                        saved++
                    }
                    edits.clear()
                    msg = if (saved > 0) "Saved $saved marks ✓" else "Nothing to save"
                },
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT)
            ) { Text("Save marks", color = Color.White) }
            if (msg.isNotEmpty()) Cell(msg, GOOD)
        }
    }
}

private fun Modifier.horizontalScrollEnabled(): Modifier = this  // compact: row wraps are acceptable on phones

// ─────────────────────────────────────────────────────────────────────────────
// Comments (Section 23)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CommentsScreen(state: TeacherState) {
    val d = state.data
    var studentId by remember { mutableStateOf(d.students.firstOrNull()?.id ?: "") }
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val termId = d.academicYears.firstOrNull()?.currentTermId ?: ""

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CardBox {
            Cell("Class teacher comments — pick student, write, save", MUTED, true)
            Spacer(Modifier.height(6.dp))
            // horizontal picker of students (first 12)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                d.students.take(6).forEach { s ->
                    Button(onClick = { studentId = s.id },
                        colors = ButtonDefaults.buttonColors(containerColor = if (studentId == s.id) ACCENT else CARD)) {
                        Text(s.firstName, fontSize = 9.sp, color = Color.White)
                    }
                }
            }
            val student = d.students.firstOrNull { it.id == studentId }
            if (student != null && d.students.size > 6) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    d.students.drop(6).take(6).forEach { s ->
                        Button(onClick = { studentId = s.id },
                            colors = ButtonDefaults.buttonColors(containerColor = if (studentId == s.id) ACCENT else CARD)) {
                            Text(s.firstName, fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Comment for ${d.students.firstOrNull { it.id == studentId }?.fullName ?: "student"}…", color = MUTED, fontSize = 12.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    if (text.isNotBlank() && studentId.isNotBlank()) {
                        state.addComment(Comment(
                            id = "cm-${System.currentTimeMillis()}", studentId = studentId, termId = termId,
                            type = CommentType.CLASS_TEACHER, text = text
                        ))
                        text = ""; msg = "Comment saved ✓"
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = ACCENT)) { Text("Save comment", color = Color.White, fontSize = 12.sp) }
                // predefined comment shortcuts (Section 23)
                listOf("Excellent performance.", "Good improvement.", "Needs more effort.").forEach { pc ->
                    Button(onClick = { text = pc }, colors = ButtonDefaults.buttonColors(containerColor = CARD)) {
                        Text(pc.take(11) + "…", fontSize = 9.sp, color = Color.White)
                    }
                }
            }
            if (msg.isNotEmpty()) Cell(msg, GOOD)
        }
        CardBox {
            Cell("Saved comments this term", MUTED, true)
            Spacer(Modifier.height(4.dp))
            d.comments.takeLast(8).reversed().forEach { c ->
                val s = d.students.firstOrNull { it.id == c.studentId }
                Cell("• ${s?.fullName ?: c.studentId}: ${c.text}", MUTED)
            }
            if (d.comments.isEmpty()) Cell("None yet.", MUTED)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Export / auto-send staging (Sections 39, 47)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SendScreen(state: TeacherState) {
    val d = state.data
    var path by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CardBox {
            Cell("Export marks bundle", MUTED, true)
            Spacer(Modifier.height(4.dp))
            Cell("Copy this file to the admin computer via USB or Bluetooth and import it in Sync & Backup.", MUTED)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { path = state.exportBundle() }, colors = ButtonDefaults.buttonColors(containerColor = ACCENT)) {
                Text("Export ${d.marks.size} marks", color = Color.White)
            }
            if (path != null) {
                Spacer(Modifier.height(6.dp))
                Cell("✓ Saved:", GOOD, true)
                Cell(path ?: "", MUTED)
            }
        }
        CardBox {
            Cell("How offline sync works", MUTED, true)
            Spacer(Modifier.height(4.dp))
            Cell("1. Marks save instantly to this phone.", MUTED)
            Cell("2. Export creates a validated bundle file.", MUTED)
            Cell("3. Admin imports it — newest-wins merge, everything logged.", MUTED)
        }
    }
}
