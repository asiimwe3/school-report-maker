package com.derycode.srs.desktopteacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.MarkType

/** What this teacher carries: their classes, their subjects, their learners. */
@Composable
fun ClassesScreen(state: TeacherStore) {
    val classes = state.myClasses
    var openClass by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(T.BG).padding(24.dp)) {
        H1("My classes", "Every learner you are responsible for, with the marks already saved.")
        Spacer(Modifier.height(16.dp))
        if (classes.isEmpty()) {
            Card { Text("No classes yet. Open the school file from your admin, or ask them to assign you classes.", color = T.RUST, fontSize = 13.sp) }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("${classes.size}", "classes", Modifier.width(130.dp))
            Stat("${classes.sumOf { state.studentsIn(it.id).size }}", "learners", Modifier.width(130.dp))
            Stat("${state.myMarks().size}", "marks saved", Modifier.width(150.dp))
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(classes, key = { it.id }) { c ->
                    val students = state.studentsIn(c.id)
                    val subjects = state.mySubjectsFor(c.id)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = T.INK, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${students.size} learners · ${subjects.size} subjects: " +
                                    subjects.take(4).joinToString(", ") { it.name } + if (subjects.size > 4) "…" else "",
                                color = T.MUTED, fontSize = 11.sp
                            )
                        }
                        Btn(if (openClass == c.id) "Hide" else "Learners", {
                            openClass = if (openClass == c.id) null else c.id
                        }, kind = Kind.GHOST)
                    }
                    if (openClass == c.id) {
                        Column(Modifier.padding(start = 6.dp, top = 4.dp)) {
                            students.forEach { s ->
                                val marks = state.myMarks().count { it.studentId == s.id && it.type == MarkType.VALUE }
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(s.admissionNo, color = T.MUTED, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                                    Text(s.fullName, color = T.INK, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Text("$marks marks", color = T.MUTED, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
