package com.derycode.srs.teacher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*
import com.derycode.srs.core.store.JsonStore
import java.nio.file.Files
import java.nio.file.Path

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TeacherApp() }
    }
}

val store = JsonStore()
val bundleFile: Path get() = Path.of(filesDir.absolutePath, "teacher-bundle.json")

private val demo = SchoolData(
    school = School(name = "Tooro Junior School"),
    classes = listOf(SchoolClass("c1", "Primary 7", "A")),
    subjects = listOf(
        Subject("s1", "MTC", "Mathematics"),
        Subject("s2", "ENG", "English"),
        Subject("s3", "SCI", "Science"),
        Subject("s4", "SST", "Social Studies")
    ),
    students = listOf(
        Student("st1", "c1", "P7-001", "Mary", "Aine", "F"),
        Student("st2", "c1", "P7-002", "Peter", "Kato", "M"),
        Student("st3", "c1", "P7-003", "Grace", "Ninsiima", "F")
    )
)

@Composable
fun TeacherApp() {
    var tab by remember { mutableStateOf(0) }
    val data = remember { mutableStateOf(demo) }

    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("Home", "Marks", "Export").forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = {},
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(data.value)
                    1 -> MarksScreen(data)
                    2 -> ExportScreen(data.value)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(d: SchoolData) {
    Column(Modifier.padding(20.dp)) {
        Text(d.school.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Teacher console • offline", color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(20.dp))
        Card { Column(Modifier.padding(16.dp)) {
            Text("My class: ${d.classes.firstOrNull()?.name ?: "-"}", fontSize = 16.sp)
            Text("Students: ${d.students.count { it.active }}")
            Text("Subjects: ${d.subjects.size}")
        } }
        Spacer(Modifier.height(16.dp))
        Text(
            "Tap Marks to enter this term's scores. Everything stays on this phone.",
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun MarksScreen(state: MutableState<SchoolData>) {
    val d = state.value
    val subjects = d.subjects
    var subjectIdx by remember { mutableStateOf(0) }
    var assessment by remember { mutableStateOf("EOT") }
    // in-memory score entry for current subject/assessment
    val scores = remember(subjectIdx, assessment) {
        mutableStateMapOf(
            *d.students.filter { it.active }.map { s ->
                s.id to d.marks.firstOrNull {
                    it.studentId == s.id && it.subjectId == subjects.getOrNull(subjectIdx)?.id
                        && it.assessment == assessment
                }?.score?.toString().orEmpty()
            }.toTypedArray<Pair<String, String>>()
        )
    }

    Column(Modifier.padding(16.dp)) {
        Text("Enter marks", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEachIndexed { i, s ->
                FilterChip(selected = subjectIdx == i, onClick = { subjectIdx = i }, label = { Text(s.code) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("BOT", "MID", "EOT").forEach { a ->
                FilterChip(selected = assessment == a, onClick = { assessment = a }, label = { Text(a) })
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(d.students.filter { it.active }) { s ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${s.firstName} ${s.lastName}", Modifier.weight(1f))
                    OutlinedTextField(
                        value = scores[s.id].orEmpty(),
                        onValueChange = { v -> scores[s.id] = v.filter { it.isDigit() || it == '.' }.take(5) },
                        label = { Text("/100") },
                        modifier = Modifier.width(110.dp),
                        singleLine = true
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            val subjectId = subjects.getOrNull(subjectIdx)?.id ?: return@Button
            val cleaned = d.marks.filter {
                !(it.subjectId == subjectId && it.assessment == assessment)
            } + scores.entries
                .filter { it.value.toDoubleOrNull() != null }
                .map { (studentId, v) ->
                    MarkEntry(studentId, subjectId, 3, assessment, v.toDouble())
                }
            state.value = d.copy(marks = cleaned)
        }) { Text("Save marks") }
    }
}

@Composable
fun ExportScreen(d: SchoolData) {
    var status by remember { mutableStateOf("") }
    Column(Modifier.padding(20.dp)) {
        Text("Export to admin", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Writes a small file you can share to the school admin computer (USB, Bluetooth, or file share). " +
                "The admin console imports it in one click.",
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            runCatching {
                Files.createDirectories(bundleFile.parent)
                store.exportTeacherBundle(bundleFile, d.students.filter { it.active }, Teacher("t1", "Class Teacher"))
                status = "Saved: ${bundleFile.fileName}"
            }.onFailure { status = "Error: ${it.message}" }
        }) { Text("Export bundle") }
        Spacer(Modifier.height(12.dp))
        if (status.isNotEmpty()) Card { Text(status, Modifier.padding(14.dp)) }
    }
}
