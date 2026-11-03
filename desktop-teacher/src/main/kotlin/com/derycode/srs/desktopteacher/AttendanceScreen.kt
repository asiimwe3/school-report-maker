package com.derycode.srs.desktopteacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.AttendanceRecord
import java.time.LocalDate

/**
 * The daily register. One click per learner, and the whole class is present with
 * a single button, because that is the common case and it should take seconds.
 */
@Composable
fun AttendanceScreen(state: TeacherStore) {
    val classes = state.myClasses
    var classId by remember { mutableStateOf(classes.firstOrNull()?.id ?: "") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var msg by remember { mutableStateOf("") }

    val students = if (classId.isBlank()) emptyList() else state.studentsIn(classId)
    val existing = state.data.attendance.filter { it.date == date && it.classId == classId }
    var overrides by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun statusOf(studentId: String): String =
        overrides[studentId]
            ?: existing.firstOrNull { it.studentId == studentId }?.status
            ?: ""

    fun mark(studentId: String, status: String) {
        overrides = overrides + (studentId to status)
        state.upsertAttendance(
            AttendanceRecord(
                id = "att-$studentId-$date", studentId = studentId, classId = classId,
                date = date, status = status, recordedBy = state.me?.name ?: ""
            )
        )
    }

    Column(Modifier.fillMaxSize().background(T.BG).padding(24.dp)) {
        H1("Attendance", "One tap per learner. Saved as you go.")
        Spacer(Modifier.height(16.dp))
        if (classes.isEmpty()) {
            Card { Text("Open the school file first to see your classes.", color = T.RUST, fontSize = 13.sp) }
            return@Column
        }
        Card(Modifier.fillMaxWidth()) {
            Label("CLASS")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                classes.forEach { c -> Chip(c.name, c.id == classId) { classId = c.id; overrides = emptyMap() } }
            }
            Spacer(Modifier.height(12.dp))
            Label("DATE")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tf(date, { date = it; overrides = emptyMap() }, "yyyy-mm-dd", Modifier.width(150.dp))
                Spacer(Modifier.width(10.dp))
                Btn("Today", { date = LocalDate.now().toString(); overrides = emptyMap() }, kind = Kind.GHOST)
                Spacer(Modifier.width(10.dp))
                Btn("All present", {
                    students.forEach { mark(it.id, "PRESENT") }
                    msg = "Marked all ${students.size} present."
                })
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(students, key = { it.id }) { st ->
                    val current = statusOf(st.id)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(st.admissionNo, color = T.MUTED, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                        Text(st.fullName, color = T.INK, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        listOf("PRESENT" to "P", "ABSENT" to "A", "LATE" to "L").forEach { (status, letter) ->
                            val on = current == status
                            Box(
                                Modifier.padding(start = 6.dp)
                                    .background(if (on) when (status) {
                                        "PRESENT" -> T.TEAL; "ABSENT" -> T.RUST; else -> T.GOLD
                                    } else T.CARD, RoundedCornerShape(8.dp))
                                    .border(1.dp, T.LINE, RoundedCornerShape(8.dp))
                                    .clickableNoRipple { mark(st.id, status) }
                                    .size(34.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(letter, color = if (on) Color.White else T.MUTED, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            msg.ifBlank {
                val p = students.count { statusOf(it.id) == "PRESENT" }
                val a = students.count { statusOf(it.id) == "ABSENT" }
                "$p present · $a absent · ${students.size - p - a} not yet marked"
            },
            color = T.MUTED, fontSize = 12.sp
        )
    }
}
