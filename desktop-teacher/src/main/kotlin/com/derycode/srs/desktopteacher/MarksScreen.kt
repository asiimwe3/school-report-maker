package com.derycode.srs.desktopteacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.Mark
import com.derycode.srs.core.model.MarkType
import com.derycode.srs.core.results.ResultEngine

/**
 * Marks entry, the reason a teacher wants this on a laptop.
 *
 * A whole class on one screen, a number pad, and Enter to move to the next
 * learner - the same order as the paper register. Nothing is lost if the laptop
 * sleeps: every sheet saves as it is typed.
 */
@Composable
fun MarksScreen(state: TeacherStore) {
    val classes = state.myClasses
    var classId by remember { mutableStateOf(classes.firstOrNull()?.id ?: "") }
    LaunchedEffect(classes.map { it.id }) {
        if (classId.isBlank() || classes.none { it.id == classId }) classId = classes.firstOrNull()?.id ?: ""
    }
    val subjects = if (classId.isBlank()) emptyList() else state.mySubjectsFor(classId)
    var subjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    LaunchedEffect(classId, subjects.map { it.id }) {
        if (subjects.none { it.id == subjectId }) subjectId = subjects.firstOrNull()?.id ?: ""
    }
    val components = state.data.components.filter { it.active }
    var componentId by remember { mutableStateOf(components.firstOrNull()?.id ?: "") }
    LaunchedEffect(components.map { it.id }) {
        if (components.none { it.id == componentId }) componentId = components.firstOrNull()?.id ?: ""
    }

    var edits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }
    val focus = remember { mutableStateMapOf<String, FocusRequester>() }

    val students = if (classId.isBlank()) emptyList() else state.studentsIn(classId)
    val maxScore = 100
    val existing = if (classId.isBlank() || subjectId.isBlank() || componentId.isBlank()) emptyMap()
    else state.sheetValues(classId, subjectId, componentId)

    // reload the sheet whenever the chosen sheet changes
    LaunchedEffect(classId, subjectId, componentId) {
        edits = existing.mapValues { (_, m) ->
            when (m.type) {
                MarkType.ABS -> "ABS"
                MarkType.EXEMPT -> "EXEMPT"
                MarkType.MISSING -> "ABS"
                else -> if (m.score % 1.0 == 0.0) m.score.toInt().toString() else m.score.toString()
            }
        }
        saved = ""; error = ""
    }

    fun valueOf(id: String) = edits[id] ?: ""
    fun gradeOf(id: String): String {
        val t = valueOf(id).trim()
        if (t.isEmpty()) return ""
        val num = t.toDoubleOrNull() ?: return t.uppercase().take(3)
        val level = state.data.classes.firstOrNull { it.id == classId }?.level
        val scheme = state.data.gradingSchemes.firstOrNull { it.level == level }
            ?: state.data.gradingSchemes.firstOrNull()
            ?: return ""
        val pct = (num / maxScore) * 100.0
        return try {
            com.derycode.srs.core.grading.GradingEngine.grade(scheme, pct)
        } catch (_: Exception) { "" }
    }

    fun save() {
        if (classId.isBlank() || subjectId.isBlank() || componentId.isBlank()) return
        var n = 0
        var problem = ""
        students.forEach { st ->
            val raw = valueOf(st.id).trim()
            if (raw.isEmpty()) return@forEach
            val upper = raw.uppercase()
            val type = when (upper) {
                "ABS", "A" -> MarkType.ABS
                "EXEMPT", "EX" -> MarkType.EXEMPT
                "NA", "-" -> MarkType.NA
                else -> MarkType.VALUE
            }
            val score: Double
            if (type == MarkType.VALUE) {
                val bad = ResultEngine.validateMark(raw, maxScore, allowBlank = false)
                if (bad != null) { problem = "${st.fullName}: $bad"; return@forEach }
                score = raw.toDouble()
            } else score = 0.0
            state.upsertMark(
                Mark(
                    id = "m-${st.id}-$subjectId-${state.currentTermId}-$componentId",
                    studentId = st.id, subjectId = subjectId, termId = state.currentTermId,
                    componentId = componentId, score = score, type = type, maxScore = maxScore,
                    enteredBy = state.meId
                )
            )
            n++
        }
        error = problem
        saved = when {
            problem.isNotBlank() -> ""
            n == 0 -> "Nothing typed yet."
            else -> "Saved $n mark${if (n == 1) "" else "s"} for ${state.data.subjects.firstOrNull { it.id == subjectId }?.name} · ${state.currentTermLabel}."
        }
    }

    Column(Modifier.fillMaxSize().background(T.BG).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            H1("Enter marks", "${state.currentTermLabel} · ${state.data.school.name.ifBlank { "your school" }}")
            Spacer(Modifier.weight(1f))
            if (state.me != null) Pill(state.me!!.name)
        }
        Spacer(Modifier.height(16.dp))

        if (classes.isEmpty()) {
            Card { Text("No classes yet. Open the school file from your admin first.", color = T.RUST, fontSize = 13.sp) }
            return@Column
        }

        // class + subject + assessment pickers
        Card(Modifier.fillMaxWidth()) {
            Label("CLASS")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                classes.forEach { c ->
                    Chip(label(c.name), c.id == classId) { classId = c.id }
                }
            }
            Spacer(Modifier.height(12.dp))
            Label("SUBJECT")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                subjects.take(9).forEach { s -> Chip(s.name, s.id == subjectId) { subjectId = s.id } }
            }
            Spacer(Modifier.height(12.dp))
            Label("ASSESSMENT")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                components.forEach { c -> Chip("${c.name} (/${maxScore})", c.id == componentId) { componentId = c.id } }
            }
        }
        Spacer(Modifier.height(14.dp))

        // the sheet
        Card(Modifier.fillMaxWidth().weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LEARNER", color = T.MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("MARK /$maxScore", color = T.MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp))
                Text("GRADE", color = T.MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(students, key = { it.id }) { st ->
                    val filled = valueOf(st.id).isNotBlank()
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (filled) T.TEAL_SOFT.copy(alpha = 0.45f) else Color.Transparent, RoundedCornerShape(7.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(st.admissionNo, color = T.MUTED, fontSize = 11.sp, modifier = Modifier.width(74.dp))
                        Text(st.fullName, color = T.INK, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        val fr = focus.getOrPut(st.id) { FocusRequester() }
                        Box(
                            Modifier.width(110.dp)
                                .background(Color.White, RoundedCornerShape(7.dp))
                                .border(1.dp, T.LINE, RoundedCornerShape(7.dp))
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                        ) {
                            BasicTextField(
                                value = valueOf(st.id),
                                onValueChange = { new ->
                                    edits = edits + (st.id to new.take(6))
                                    error = ""
                                },
                                singleLine = true,
                                textStyle = TextStyle(color = T.INK, fontSize = 13.sp, textAlign = TextAlign.Center),
                                modifier = Modifier.fillMaxWidth()
                                    .focusRequester(fr)
                                    .onPreviewKeyEvent { e ->
                                        if (e.key == Key.Enter && e.type == KeyEventType.KeyDown) {
                                            val order = students.map { it.id }
                                            val next = order.getOrNull(order.indexOf(st.id) + 1)
                                            if (next != null) focus.getOrPut(next) { FocusRequester() }.requestFocus()
                                            else save()
                                            true
                                        } else if (e.key == Key.Tab && e.type == KeyEventType.KeyDown) {
                                            val order = students.map { it.id }
                                            val next = order.getOrNull(order.indexOf(st.id) + 1)
                                            if (next != null) focus.getOrPut(next) { FocusRequester() }.requestFocus()
                                            true
                                        } else false
                                    }
                            )
                        }
                        Text(gradeOf(st.id), color = T.TEAL, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(70.dp).padding(start = 10.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val done = students.count { valueOf(it.id).isNotBlank() }
            Stat("$done/${students.size}", "entered", Modifier.width(120.dp))
            Spacer(Modifier.width(10.dp))
            val avg = students.mapNotNull { valueOf(it.id).trim().toDoubleOrNull() }.takeIf { it.isNotEmpty() }?.average()
            Stat(if (avg == null) "—" else String.format("%.1f", avg), "average", Modifier.width(120.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                if (error.isNotBlank()) Text(error, color = T.RUST, fontSize = 12.sp)
                if (saved.isNotBlank()) Text(saved, color = T.OK, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Btn("Save this sheet", { save() })
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Type a mark, press Enter for the next learner. ABS, EXEMPT and NA are accepted.", color = T.MUTED, fontSize = 11.sp)
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) T.TEAL else T.CARD, RoundedCornerShape(50))
            .border(1.dp, if (selected) T.TEAL else T.LINE, RoundedCornerShape(50))
            .clickableNoRipple(onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(text, color = if (selected) Color.White else T.INK, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

internal fun label(name: String) = name
