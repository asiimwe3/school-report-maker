package com.derycode.srs.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.derycode.srs.core.grading.Grading
import com.derycode.srs.core.grading.round1
import com.derycode.srs.core.model.*
import com.derycode.srs.core.report.ReportEngine
import com.derycode.srs.core.store.JsonStore
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.UIManager

/** In-memory working set; persisted to a single JSON file. */
val store = JsonStore()
val dataFile = Path.of(System.getProperty("user.home"), "SchoolReportMaker", "school-data.json")

private val seed = SchoolData(
    school = School(
        name = "Tooro Junior School",
        address = "Kyenjojo, Uganda",
        motto = "Knowledge is Light",
        headTeacher = "A. Derick"
    ),
    teachers = listOf(Teacher("t1", "Patrick Mugisha")),
    classes = listOf(
        SchoolClass("c1", "Primary 7", "A", "t1"),
        SchoolClass("c2", "Primary 6", "A", "t1")
    ),
    subjects = listOf(
        Subject("s1", "MTC", "Mathematics"),
        Subject("s2", "ENG", "English"),
        Subject("s3", "SCI", "Science"),
        Subject("s4", "SST", "Social Studies")
    ),
    students = listOf(
        Student("st1", "c1", "P7-001", "Mary", "Aine", "F", guardianName = "John Aine"),
        Student("st2", "c1", "P7-002", "Peter", "Kato", "M", guardianName = "Jane Kato"),
        Student("st3", "c1", "P7-003", "Grace", "Ninsiima", "F", guardianName = "Rose N.")
    ),
    marks = buildList {
        for ((sid, s) in listOf("st1" to listOf(82.0, 78.0, 88.0), "st2" to listOf(55.0, 62.0, 58.0), "st3" to listOf(70.0, 74.0, 79.0))) {
            for ((subj, base) in listOf("s1" to 1.0, "s2" to 0.97, "s3" to 1.03, "s4" to 0.9)) {
                listOf("BOT" to 0.92, "MID" to 0.96, "EOT" to 1.0).forEach { (assessment, f) ->
                    add(MarkEntry(sid, subj, 3, assessment, (s[0] * base * f).coerceAtMost(100.0)))
                }
            }
        }
    }
)

fun main() {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    var current: SchoolData? = null
    if (Files.exists(dataFile)) runCatching { current = store.load(dataFile) }
    val state = mutableStateOf(current ?: seed)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1200.dp, height = 780.dp),
            title = "School Report Maker — Admin Console"
        ) {
            App(state, onSave = { store.save(dataFile, it) })
        }
    }
}

@Composable
fun App(state: MutableState<SchoolData>, onSave: (SchoolData) -> Unit) {
    var selected by remember { mutableStateOf("Dashboard") }
    MaterialTheme(colorScheme = lightColorScheme()) {
        Row(Modifier.fillMaxSize().background(Color(0xFFF6F6F8))) {
            // Sidebar
            Column(
                Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF1B3A2A)),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "REPORT MAKER",
                    color = Color(0xFFE8C97A),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                listOf(
                    "Dashboard", "Students", "Classes", "Subjects",
                    "Marks", "Reports", "Backup"
                ).forEach { item ->
                    Text(
                        item,
                        color = if (selected == item) Color(0xFF1B3A2A) else Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected == item) Color(0xFFE8C97A) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "OFFLINE • v0.1.0",
                    color = Color(0xFF9DB8A9),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
            // Content
            Column(Modifier.weight(1f).padding(24.dp)) {
                when (selected) {
                    "Dashboard" -> Dashboard(state, selected = { selected = it })
                    "Students" -> Students(state, onSave)
                    "Reports" -> Reports(state)
                    else -> Placeholder(selected)
                }
            }
        }
    }
}

@Composable
fun Dashboard(state: MutableState<SchoolData>, selected: (String) -> Unit) {
    val d = state.value
    Column {
        Text("Dashboard", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("Students", d.students.count { it.active }.toString())
            StatCard("Classes", d.classes.size.toString())
            StatCard("Subjects", d.subjects.size.toString())
            StatCard("Marks entered", d.marks.size.toString())
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { selected("Students") }) { Text("Enter students") }
            OutlinedButton(onClick = { selected("Reports") }) { Text("Generate reports") }
        }
    }
}

@Composable
fun StatCard(label: String, value: String) {
    Card {
        Column(Modifier.padding(20.dp)) {
            Text(value, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 14.sp, color = Color(0xFF666666))
        }
    }
}

@Composable
fun Students(state: MutableState<SchoolData>, onSave: (SchoolData) -> Unit) {
    val d = state.value
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var classId by remember { mutableStateOf(d.classes.firstOrNull()?.id ?: "") }
    var sex by remember { mutableStateOf("M") }

    Column {
        Text("Students", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(first, { first = it }, label = { Text("First name") }, Modifier.width(160.dp))
            OutlinedTextField(last, { last = it }, label = { Text("Last name") }, Modifier.width(160.dp))
            OutlinedTextField(sex, { sex = it.uppercase().take(1) }, label = { Text("Sex") }, Modifier.width(70.dp))
            Button(
                onClick = {
                    if (first.isNotBlank() && last.isNotBlank()) {
                        val new = Student(
                            id = "st-" + System.currentTimeMillis(),
                            classId = classId,
                            studentNo = "S-${d.students.size + 1}",
                            firstName = first.trim(),
                            lastName = last.trim(),
                            sex = sex
                        )
                        val updated = d.copy(students = d.students + new)
                        state.value = updated
                        onSave(updated)
                        first = ""; last = ""
                    }
                },
                enabled = d.classes.isNotEmpty()
            ) { Text("Add student") }
        }
        Spacer(Modifier.height(16.dp))
        if (d.students.isEmpty()) {
            Text("No students yet — add your first above.", color = Color(0xFF888888))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(d.students.filter { it.active }) { s ->
                    Card {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${s.firstName} ${s.lastName}  (${s.studentNo})")
                            Text(d.classes.firstOrNull { it.id == s.classId }?.name ?: "-", color = Color(0xFF666666))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Reports(state: MutableState<SchoolData>) {
    val d = state.value
    var term by remember { mutableStateOf(3) }
    var status by remember { mutableStateOf("") }

    Column {
        Text("Reports", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Render professional report cards for a whole class in one click.", color = Color(0xFF555555))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Term:")
            (1..3).forEach { t ->
                FilterChip(selected = term == t, onClick = { term = t }, label = { Text("Term $t") })
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = {
                val chooser = JFileChooser()
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val dir = chooser.selectedFile.toPath()
                    val engine = ReportEngine(d)
                    var count = 0
                    d.classes.forEach { c ->
                        engine.reportCards(c.id, term).forEach { (student, html) ->
                            Files.writeString(
                                dir.resolve("report-${student.firstName}-${student.lastName}-T$term.html"),
                                html
                            )
                            count++
                        }
                    }
                    status = "Generated $count report card(s) to ${dir.fileName}"
                }
            }, enabled = d.classes.isNotEmpty()) { Text("Generate all reports…") }
        }
        Spacer(Modifier.height(16.dp))
        if (status.isNotEmpty()) {
            Card { Text(status, Modifier.padding(16.dp)) }
        }
        Spacer(Modifier.height(24.dp))
        // Term averages preview
        Text("Term $term averages", fontWeight = FontWeight.SemiBold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(d.students.filter { it.active }) { s ->
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${s.firstName} ${s.lastName}")
                    Text("${Grading.termAverage(d.marks, s.id, term).round1()}%", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun Placeholder(name: String) {
    Column {
        Text(name, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("This module is scheduled in the roadmap (see PLAN.md Phase plan).", color = Color(0xFF888888))
    }
}
