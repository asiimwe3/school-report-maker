package com.derycode.srs.teacher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Attendance — daily class register: present / absent / late
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AttendanceScreen(state: TeacherState) {
    val d = state.data
    val classes = state.myClasses
    var classId by remember { mutableStateOf(classes.firstOrNull()?.id ?: "") }
    var date by remember { mutableStateOf(state.todayKey) }
    val students = remember(classId) { state.studentsIn(classId) }
    val att = remember(state.data, classId, date) { state.attendanceFor(classId, date) }
    var msg by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            CardBox {
                Row {
                    Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = BLUE, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Cell("Daily attendance", MUTED, true)
                }
                Spacer(Modifier.height(6.dp))
                if (classes.isEmpty()) {
                    Cell("No classes assigned yet \u2014 import school data from More \u2192 Sync.", ORANGE)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classes.forEach { c ->
                            FilterChip(selected = classId == c.id, onClick = { classId = c.id },
                                label = { Text(classShort(c), fontSize = 12.sp) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = date, onValueChange = { date = it },
                        placeholder = { Text("Date (yyyy-mm-dd)", color = MUTED) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            state.markAllPresent(classId, date, students.map { it.id })
                            msg = "\u2713 All ${students.size} students marked present."
                        }, colors = ButtonDefaults.buttonColors(containerColor = GREEN)) { Text("Mark all present") }
                    }
                    if (msg.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Cell(msg, GREEN) }
                    Spacer(Modifier.height(4.dp))
                    val done = att.size
                    Cell("$done of ${students.size} marked for $date", MUTED)
                }
            }
        }
        items(students, key = { it.id }) { s ->
            val status = att[s.id]
            CardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Cell("${s.firstName} ${s.lastName}", Color.White, true)
                        if (s.admissionNo.isNotBlank()) Cell(s.admissionNo, MUTED)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("PRESENT" to "P", "ABSENT" to "A", "LATE" to "L").forEach { (st, label) ->
                            FilterChip(selected = status == st, onClick = { state.setAttendance(s.id, classId, date, st) },
                                label = { Text(label, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = CARD_ALT,
                                    selectedContainerColor = when (st) {
                                        "PRESENT" -> GREEN.copy(alpha = 0.25f)
                                        "ABSENT" -> RED.copy(alpha = 0.25f)
                                        else -> ORANGE.copy(alpha = 0.25f)
                                    }))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}
