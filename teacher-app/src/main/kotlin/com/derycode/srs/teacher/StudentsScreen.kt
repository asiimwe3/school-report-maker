package com.derycode.srs.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

// ─────────────────────────────────────────────────────────────────────────────
// Students list — class dropdown-ish chips + search + score badges
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StudentsListScreen(state: TeacherState, initialClassId: String?, onOpenStudent: (String) -> Unit) {
    val classes = state.myClasses
    var classId by remember { mutableStateOf(initialClassId ?: classes.firstOrNull()?.id ?: "") }
    var query by remember { mutableStateOf("") }
    val subject = state.mySubjectsFor(classId).firstOrNull()
    val termId = state.currentTermId
    val students = state.studentsIn(classId).filter {
        query.isBlank() || it.fullName.contains(query, true) || it.admissionNo.contains(query, true)
    }

    Column {
        if (classes.size > 1) {
            ClassPickerRow(classes, classId) { classId = it }
            Spacer(Modifier.height(10.dp))
        }
        Text(subject?.let { "${it.name} \u2013 ${classes.firstOrNull { c -> c.id == classId }?.let { classLabel(it) } ?: ""}" }
            ?: classes.firstOrNull { it.id == classId }?.let { classLabel(it) } ?: "No class selected",
            color = MUTED, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        SearchField(query, { query = it }, "Search student name or index...")
        Spacer(Modifier.height(10.dp))
        if (students.isEmpty()) {
            EmptyHint(if (classes.isEmpty()) "No classes assigned yet." else "No students found.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(students) { s ->
                    val pct = latestPercent(state, s.id, subject?.id, termId)
                    StudentRow(s, pct) { onOpenStudent(s.id) }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
    }
}

@Composable
private fun ClassPickerRow(classes: List<SchoolClass>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(classes) { c -> FilterChipPill(classShort(c), selected = selected == c.id) { onSelect(c.id) } }
    }
}

@Composable
private fun StudentRow(s: Student, pct: Double?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CARD).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(s.firstName.take(1) + s.lastName.take(1), avatarColor(s.id), 38)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.fullName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(s.admissionNo, color = MUTED, fontSize = 10.sp)
        }
        if (pct != null) ScoreBadge(pct) else Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MUTED)
    }
}

/** Latest entered percentage for a student in a subject (for the score badge preview). */
private fun latestPercent(state: TeacherState, studentId: String, subjectId: String?, termId: String): Double? {
    if (subjectId == null) return null
    val m = state.data.marks.filter { it.studentId == studentId && it.subjectId == subjectId && it.termId == termId && it.type == MarkType.VALUE }
        .maxByOrNull { it.updatedAt } ?: return null
    return if (m.maxScore > 0) (m.score / m.maxScore) * 100.0 else null
}

// ─────────────────────────────────────────────────────────────────────────────
// Student Details
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StudentDetailScreen(state: TeacherState, studentId: String) {
    val d = state.data
    val s = d.students.firstOrNull { it.id == studentId }
    if (s == null) { EmptyHint("Student not found."); return }
    val cls = state.classOf(studentId)
    val termId = state.currentTermId
    val subjects = cls?.let { state.mySubjectsFor(it.id) } ?: emptyList()

    val perSubject = subjects.mapNotNull { subj ->
        val m = d.marks.filter { it.studentId == studentId && it.subjectId == subj.id && it.termId == termId && it.type == MarkType.VALUE }
            .maxByOrNull { it.updatedAt } ?: return@mapNotNull null
        val pct = if (m.maxScore > 0) (m.score / m.maxScore) * 100.0 else 0.0
        Triple(subj, m, pct)
    }
    val average = if (perSubject.isEmpty()) 0.0 else perSubject.map { it.third }.average()
    val absences = d.marks.count { it.studentId == studentId && it.type == MarkType.ABS }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.firstName.take(1) + s.lastName.take(1), avatarColor(s.id), 52)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(s.fullName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Index: ${s.admissionNo}", color = MUTED, fontSize = 11.sp)
                        Text("Class: ${cls?.let { classLabel(it) } ?: "\u2014"}", color = MUTED, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    MiniStatBig(Modifier.weight(1f), fmt1(average), "Average", GREEN)
                    MiniStatBig(Modifier.weight(1f), absences.toString(), "Absent", ORANGE)
                    MiniStatBig(Modifier.weight(1f), "0", "Late", RED)
                }
            }
        }
        item {
            CardBox {
                Cell("Subject Performance", MUTED, true)
                Spacer(Modifier.height(8.dp))
                if (perSubject.isEmpty()) {
                    Cell("No marks recorded yet for this term.", MUTED)
                } else {
                    perSubject.forEachIndexed { i, (subj, m, pct) ->
                        if (i > 0) Divider(color = STROKE, modifier = Modifier.padding(vertical = 6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(avatarColor(subj.id).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = avatarColor(subj.id), modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(subj.name, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("${fmt1(m.score)} / ${m.maxScore}", color = MUTED, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(quickBandColor(pct).copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(quickLetter(pct), color = quickBandColor(pct), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            val comment = d.comments.filter { it.studentId == studentId && it.termId == termId }.lastOrNull()
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.StickyNote2, contentDescription = null, tint = MUTED, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Cell("Notes", MUTED, true)
                }
                Spacer(Modifier.height(6.dp))
                Cell(comment?.text ?: "No comment recorded yet for this term.", if (comment != null) Color.White else MUTED)
            }
        }
        item {
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(containerColor = BLUE),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Edit Marks") }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun MiniStatBig(modifier: Modifier, value: String, label: String, color: Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MUTED, fontSize = 10.sp)
    }
}
