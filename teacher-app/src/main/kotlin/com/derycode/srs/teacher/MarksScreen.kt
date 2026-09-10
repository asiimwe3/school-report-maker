package com.derycode.srs.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.derycode.srs.core.results.ResultEngine

// ─────────────────────────────────────────────────────────────────────────────
// Enter Marks — subject/assessment/term picker → max marks → grid → save
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EnterMarksScreen(state: TeacherState, classId: String, initialSubjectId: String?, onDone: () -> Unit) {
    val d = state.data
    val cls = d.classes.firstOrNull { it.id == classId }
    val subjects = state.mySubjectsFor(classId)
    var subjectId by remember { mutableStateOf(initialSubjectId ?: subjects.firstOrNull()?.id ?: "") }
    val components = d.components.filter { it.levels.contains(cls?.level ?: Level.O_LEVEL) && it.active }
    var componentId by remember { mutableStateOf(components.firstOrNull()?.id ?: "") }
    val terms = d.academicYears.flatMap { it.terms }
    var termId by remember { mutableStateOf(state.currentTermId) }
    var maxMarks by remember { mutableStateOf((subjects.firstOrNull { it.id == subjectId }?.maxMarks ?: 100).toString()) }
    var showGrid by remember { mutableStateOf(false) }
    val edits = remember { mutableStateMapOf<String, String>() }
    var msg by remember { mutableStateOf("") }
    val students = state.studentsIn(classId)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(BLUE.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Assignment, contentDescription = null, tint = BLUE, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text((subjects.firstOrNull { it.id == subjectId }?.name ?: "Subject") + " \u2013 " + (cls?.let { classShort(it) } ?: ""),
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${components.firstOrNull { it.id == componentId }?.name ?: ""} \u00B7 ${students.size} students", color = MUTED, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            CardBox {
                FieldLabel("Subject")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    subjects.forEach { s -> FilterChipPill(s.name, selected = subjectId == s.id) {
                        subjectId = s.id
                        maxMarks = (s.maxMarks).toString()
                    } }
                }
                Spacer(Modifier.height(10.dp))
                FieldLabel("Assessment Type")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    components.forEach { c -> FilterChipPill(c.name, selected = componentId == c.id) { componentId = c.id } }
                }
                Spacer(Modifier.height(10.dp))
                FieldLabel("Term")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    terms.forEach { t -> FilterChipPill("Term ${t.number}", selected = termId == t.id) { termId = t.id } }
                }
                Spacer(Modifier.height(10.dp))
                FieldLabel("Maximum Marks")
                DarkTextField(maxMarks, { maxMarks = it })
            }
        }
        if (!showGrid) {
            item {
                Button(
                    onClick = { showGrid = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BLUE),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Save & Continue", fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp)) }
            }
            item {
                CardBox {
                    Cell("Options", MUTED, true)
                    Spacer(Modifier.height(6.dp))
                    OptionRow(Icons.Filled.UploadFile, "Import from Excel")
                    Divider(color = STROKE, modifier = Modifier.padding(vertical = 4.dp))
                    OptionRow(Icons.Filled.ContentCopy, "Use Existing Template")
                    Divider(color = STROKE, modifier = Modifier.padding(vertical = 4.dp))
                    OptionRow(Icons.Filled.Save, "Save as Draft")
                }
            }
        } else {
            item {
                CardBox {
                    Cell("Students (${students.size}) \u2014 type a score, or ABS / EXEMPT", MUTED, true)
                    Spacer(Modifier.height(8.dp))
                    val max = maxMarks.toIntOrNull() ?: 100
                    students.forEach { s ->
                        val key = "${s.id}|$subjectId|$termId|$componentId"
                        val existing = d.marks.firstOrNull { "${it.studentId}|${it.subjectId}|${it.termId}|${it.componentId}" == key }
                        val field = edits[key] ?: (existing?.let {
                            when (it.type) {
                                MarkType.ABS -> "ABS"; MarkType.EXEMPT -> "EXEMPT"
                                else -> fmt1(it.score)
                            }
                        } ?: "")
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(s.firstName.take(1) + s.lastName.take(1), avatarColor(s.id), 30)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Cell(s.fullName, bold = true)
                                Cell(s.admissionNo, MUTED)
                            }
                            DarkTextFieldSmall(field, { edits[key] = it })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            var saved = 0
                            edits.forEach { (key, text) ->
                                val parts = key.split("|")
                                val sid = parts[0]; val subj = parts[1]; val tid = parts[2]; val cid = parts[3]
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
                                state.upsertMark(Mark(id = "m-$key", studentId = sid, subjectId = subj, termId = tid,
                                    componentId = cid, score = score, type = type, maxScore = max))
                                saved++
                            }
                            edits.clear()
                            msg = if (saved > 0) "Saved $saved marks \u2713" else "Nothing to save"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BLUE),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save marks") }
                    if (msg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Cell(msg, GREEN) }
                }
            }
            item {
                TextButton(onClick = onDone) { Text("Done", color = BLUE) }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text, color = MUTED, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
}

@Composable
internal fun DarkTextField(value: String, onChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(BLUE),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CARD_ALT).padding(horizontal = 12.dp, vertical = 12.dp)
    )
}

@Composable
private fun DarkTextFieldSmall(value: String, onChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(BLUE),
        modifier = Modifier.width(72.dp).clip(RoundedCornerShape(8.dp)).background(CARD_ALT).padding(horizontal = 8.dp, vertical = 9.dp)
    )
}

@Composable
private fun OptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MUTED, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MUTED, modifier = Modifier.size(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Assessments overview (bottom-nav tab) — recent assessment card per class
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AssessmentsOverviewScreen(state: TeacherState, onOpenClass: (String) -> Unit) {
    val classes = state.myClasses
    if (classes.isEmpty()) {
        EmptyHint("No classes assigned yet.\nImport school data from More \u2192 Sync.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(classes) { c ->
            val students = state.studentsIn(c.id)
            val subject = state.mySubjectsFor(c.id).firstOrNull()
            val termId = state.currentTermId
            val marks = state.data.marks.filter { it.subjectId == subject?.id && it.termId == termId && students.any { s -> s.id == it.studentId } }
            val entered = marks.map { it.studentId }.distinct().size
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CARD).clickable { onOpenClass(c.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(TEAL.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Assignment, contentDescription = null, tint = TEAL, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${subject?.name ?: "No subject"} \u2013 ${classShort(c)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("$entered / ${students.size} marks entered", color = MUTED, fontSize = 11.sp)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MUTED)
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}
