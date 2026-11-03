package com.derycode.srs.desktopteacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.AssessmentComponent
import com.derycode.srs.core.model.Level
import com.derycode.srs.core.model.Mark
import com.derycode.srs.core.model.MarkType
import com.derycode.srs.core.model.SchoolClass
import com.derycode.srs.core.model.Student
import com.derycode.srs.core.model.Subject
import com.derycode.srs.core.report.DocxReport
import com.derycode.srs.core.results.ResultEngine

/**
 * The class marksheet, ruled like the paper the school already prints:
 *
 *     No. | NAME | A1 | A2 | A3 | X/20 | X/80 | X/100
 *
 * A whole class on one screen, names in the register order, Enter dropping to the
 * next learner in the same column, Tab crossing to the next assessment. The two
 * totals are worked out as you type, so a teacher never adds figures by hand.
 */
@Composable
fun MarksScreen(state: TeacherStore) {
    val classes = state.myClasses
    var classId by remember { mutableStateOf(classes.firstOrNull()?.id ?: "") }
    LaunchedEffect(classes.map { it.id }) {
        if (classId.isBlank() || classes.none { it.id == classId }) classId = classes.firstOrNull()?.id ?: ""
    }
    val cls: SchoolClass? = state.data.classes.firstOrNull { it.id == classId }
    val subjects = if (classId.isBlank()) emptyList() else state.mySubjectsFor(classId)
    var subjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    LaunchedEffect(classId, subjects.map { it.id }) {
        if (subjects.none { it.id == subjectId }) subjectId = subjects.firstOrNull()?.id ?: ""
    }
    val subject: Subject? = state.data.subjects.firstOrNull { it.id == subjectId }

    // ── the three assessment columns and the exam column, from the school's own data
    val level = cls?.level ?: Level.O_LEVEL
    val forLevel = state.data.components.filter { it.active && (it.levels.isEmpty() || level in it.levels) }
    val assessments: List<AssessmentComponent> = forLevel
        .filter { it.code.uppercase() in setOf("A1", "A2", "A3") }
        .sortedBy { it.code }
        .ifEmpty { forLevel.filter { it.code.uppercase() != "EOT" }.take(3) }
    val exam: AssessmentComponent? =
        forLevel.firstOrNull { it.code.uppercase() in setOf("EOT", "EXAM", "PAPER") } ?: forLevel.lastOrNull()

    val assessMax = if (level == Level.PRIMARY) 100 else 20
    var examMax by remember(level) { mutableStateOf(if (level == Level.PRIMARY) 100 else 80) }
    val totalMax = assessMax + examMax

    val students: List<Student> =
        if (classId.isBlank()) emptyList() else state.studentsIn(classId).sortedBy { it.fullName.lowercase() }

    // one edit map for the whole sheet, keyed "studentId|componentId"
    var edits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }
    var printed by remember { mutableStateOf("") }
    val focus = remember { mutableStateMapOf<String, FocusRequester>() }

    val columns = assessments + listOfNotNull(exam)

    fun key(sid: String, cid: String) = "$sid|$cid"
    fun raw(sid: String, cid: String) = edits[key(sid, cid)] ?: ""
    fun number(sid: String, cid: String): Double? = raw(sid, cid).trim().toDoubleOrNull()
    fun maxOf(column: Int): Int = if (column < assessments.size) assessMax else examMax

    // ── the school's own percentages, for learners whose figures are complete
    fun percentage(st: Student): Double? {
        val marks = columns.mapIndexedNotNull { i, c ->
            val n = number(st.id, c.id) ?: return@mapIndexedNotNull null
            Mark(
                id = "tmp", studentId = st.id, subjectId = subjectId, termId = state.currentTermId,
                componentId = c.id, score = n, type = MarkType.VALUE, maxScore = maxOf(i)
            )
        }
        if (marks.size < columns.size) return null
        val scheme = state.data.gradingSchemes.firstOrNull { it.level == level }
            ?: state.data.gradingSchemes.firstOrNull() ?: return marks.map { it.score / it.maxScore * 100 }.average()
        val weighted = scheme.aggregateRule == com.derycode.srs.core.model.AggregateRule.UCE_INDICATOR
        return ResultEngine.subjectPercentage(
            marks, state.data.components, scheme.caWeightPercent, scheme.examWeightPercent, weighted
        ).first
    }

    fun gradeOf(pct: Double?): String {
        if (pct == null) return ""
        val scheme = state.data.gradingSchemes.firstOrNull { it.level == level }
            ?: state.data.gradingSchemes.firstOrNull() ?: return ""
        return try {
            com.derycode.srs.core.grading.GradingEngine.grade(scheme, pct)
        } catch (_: Exception) { "" }
    }

    // reload whenever the sheet changes: class, subject, term
    LaunchedEffect(classId, subjectId, state.currentTermId, columns.map { it.id }) {
        val term = state.currentTermId
        val loaded = mutableMapOf<String, String>()
        students.forEach { st ->
            columns.forEachIndexed { i, c ->
                val m = state.markFor(st.id, subjectId, term, c.id) ?: return@forEachIndexed
                val shown = when (m.type) {
                    MarkType.ABS, MarkType.MISSING -> "ABS"
                    MarkType.EXEMPT -> "EXEMPT"
                    MarkType.NA -> "NA"
                    else -> if (m.score % 1.0 == 0.0) m.score.toInt().toString() else m.score.toString()
                }
                loaded[key(st.id, c.id)] = shown
            }
        }
        edits = loaded
        saved = ""; error = ""; printed = ""
    }

    /** A1..A3: the running average, out of [assessMax]. Two figures are already a usable average. */
    fun formativeOf(st: Student): Double? {
        val present = assessments.mapNotNull { number(st.id, it.id) }
        return if (present.size >= 2) present.average() else null
    }

    fun examOf(st: Student): Double? = exam?.let { number(st.id, it.id) }

    /** X/100 on the paper: the assessments out of 20 plus the exam out of 80. */
    fun totalOf(st: Student): Double? {
        val f = formativeOf(st) ?: return null
        val e = examOf(st) ?: return null
        return f + e
    }

    fun save() {
        if (classId.isBlank() || subjectId.isBlank() || columns.isEmpty()) return
        var n = 0
        var firstProblem = ""
        students.forEach { st ->
            columns.forEachIndexed { i, c ->
                val text = raw(st.id, c.id).trim()
                val existing = state.markFor(st.id, subjectId, state.currentTermId, c.id)
                if (text.isEmpty()) {
                    if (existing != null) state.removeMark(st.id, subjectId, state.currentTermId, c.id)
                    return@forEachIndexed
                }
                val upper = text.uppercase()
                val type = when (upper) {
                    "ABS", "A" -> MarkType.ABS
                    "EXEMPT", "EX" -> MarkType.EXEMPT
                    "NA", "-" -> MarkType.NA
                    else -> MarkType.VALUE
                }
                val score: Double
                if (type == MarkType.VALUE) {
                    val bad = ResultEngine.validateMark(text, maxOf(i), allowBlank = false)
                    if (bad != null) {
                        if (firstProblem.isBlank()) firstProblem = "${st.fullName}: $bad"
                        return@forEachIndexed
                    }
                    score = text.toDouble()
                } else score = 0.0
                state.upsertMark(
                    Mark(
                        id = "m-${st.id}-$subjectId-${state.currentTermId}-${c.id}",
                        studentId = st.id, subjectId = subjectId, termId = state.currentTermId,
                        componentId = c.id, score = score, type = type, maxScore = maxOf(i),
                        enteredBy = state.meId
                    )
                )
                n++
            }
        }
        error = firstProblem
        saved = when {
            firstProblem.isNotBlank() -> ""
            n == 0 -> "Nothing typed yet."
            else -> "Saved $n figure${if (n == 1) "" else "s"} for ${subject?.name ?: "this subject"} · ${state.currentTermLabel}."
        }
    }

    fun headers(): List<String> = listOf("No.", "NAME") +
        assessments.map { it.code.uppercase() } +
        listOf("X/$assessMax", "X/$examMax", "X/$totalMax")

    fun printSheet() {
        val target = FilePicker.saveDocx(
            "${state.data.school.name.ifBlank { "school" }} marksheet ${cls?.name ?: ""} ${subject?.name ?: ""}".trim()
                .replace(Regex("[^A-Za-z0-9 ]"), "").replace("  ", " ") + ".docx"
        ) ?: return
        try {
            val rows = students.map { st ->
                val cells = columns.mapIndexed { i, c -> raw(st.id, c.id).trim() }
                st.fullName to cells
            }
            // the paper rules 33 lines; keep them so the sheet can be written on
            val lines = rows.map { it.first } + List((33 - rows.size).coerceAtLeast(0)) { "" }
            val filled = headers()
            DocxReport.writeMarkSheet(
                out = target,
                data = state.data,
                classLabel = cls?.name ?: "—",
                subjectName = subject?.name ?: "",
                termLabel = state.currentTermLabel,
                headers = filled,
                names = lines
            )
            printed = "Printed sheet saved to ${target.fileName}"
            FilePicker.revealInFolder(target)
        } catch (e: Exception) {
            error = "Could not write the sheet: ${e.message}"
        }
    }

    Column(Modifier.fillMaxSize().background(T.BG).padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            H1("Enter marks", "${cls?.name ?: "no class"} · ${subject?.name ?: "no subject"} · ${state.currentTermLabel}")
            Spacer(Modifier.weight(1f))
            if (state.me != null) Pill(state.me!!.name)
        }
        Spacer(Modifier.height(12.dp))

        if (classes.isEmpty()) {
            Card { Text("No classes yet. Open the school file from your admin first.", color = T.RUST, fontSize = 13.sp) }
            return@Column
        }

        // ── pickers: one line each, scrolling sideways when a teacher has many classes
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("CLASS")
                Spacer(Modifier.width(10.dp))
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    classes.forEach { c -> Chip(c.name, c.id == classId) { classId = c.id } }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("SUBJECT")
                Spacer(Modifier.width(10.dp))
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    subjects.forEach { s -> Chip(s.name, s.id == subjectId) { subjectId = s.id } }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("EXAM OUT OF")
                Spacer(Modifier.width(10.dp))
                Chip("80", examMax == 80) { examMax = 80 }
                Spacer(Modifier.width(6.dp))
                Chip("100", examMax == 100) { examMax = 100 }
                Spacer(Modifier.width(16.dp))
                Text(
                    assessments.joinToString(" · ") { "${it.code} = ${it.name}" } +
                        (exam?.let { "  |  exam = ${it.name}" } ?: ""),
                    color = T.MUTED, fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // ── the sheet
        Card(Modifier.fillMaxWidth().weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                HCell("No.", 40.dp)
                HCell("NAME", null, Modifier.weight(1f))
                assessments.forEach { HCell(it.code.uppercase(), 66.dp) }
                HCell("X/$assessMax", 62.dp, muted = true)
                HCell("X/$examMax", 66.dp)
                HCell("X/$totalMax", 66.dp, muted = true)
                Spacer(Modifier.width(46.dp))
            }
            Spacer(Modifier.height(4.dp))

            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(students, key = { _, st -> st.id }) { index, st ->
                    val formative = formativeOf(st)
                    val total = totalOf(st)

                    Row(
                        Modifier.fillMaxWidth()
                            .background(
                                if (total != null) T.TEAL_SOFT.copy(alpha = 0.35f) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}.", color = T.MUTED, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(st.fullName, color = T.INK, fontSize = 13.sp, maxLines = 1)
                        }
                        assessments.forEachIndexed { i, c ->
                            MarkCell(
                                value = raw(st.id, c.id),
                                max = assessMax,
                                focusKey = key(st.id, c.id),
                                focus = focus,
                                onChange = { new -> edits = edits + (key(st.id, c.id) to new.take(6)); error = "" },
                                onEnter = {
                                    val next = students.getOrNull(index + 1)
                                    if (next != null) focus.getOrPut(key(next.id, c.id)) { FocusRequester() }.requestFocus()
                                    else save()
                                },
                                onTab = {
                                    val nextColumn = columns.getOrNull(i + 1)
                                    if (nextColumn != null) {
                                        focus.getOrPut(key(st.id, nextColumn.id)) { FocusRequester() }.requestFocus()
                                    } else {
                                        val next = students.getOrNull(index + 1)
                                        if (next != null) focus.getOrPut(key(next.id, columns.first().id)) { FocusRequester() }.requestFocus()
                                    }
                                }
                            )
                        }
                        Cell(if (formative == null) "—" else fmt(formative), 62.dp, bold = formative != null, muted = formative == null)
                        exam?.let { c ->
                            MarkCell(
                                value = raw(st.id, c.id),
                                max = examMax,
                                focusKey = key(st.id, c.id),
                                focus = focus,
                                onChange = { new -> edits = edits + (key(st.id, c.id) to new.take(6)); error = "" },
                                onEnter = {
                                    val next = students.getOrNull(index + 1)
                                    if (next != null) focus.getOrPut(key(next.id, c.id)) { FocusRequester() }.requestFocus()
                                    else save()
                                },
                                onTab = {
                                    val next = students.getOrNull(index + 1)
                                    if (next != null) focus.getOrPut(key(next.id, assessments.firstOrNull()?.id ?: "")) { FocusRequester() }.requestFocus()
                                }
                            )
                        }
                        Cell(if (total == null) "—" else fmt(total), 66.dp, bold = total != null, muted = total == null)
                        Text(
                            gradeOf(percentage(st)),
                            color = T.TEAL, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(46.dp), textAlign = TextAlign.End
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val done = students.count { st -> totalOf(st) != null }
            Stat("$done/${students.size}", "complete", Modifier.width(120.dp))
            Spacer(Modifier.width(10.dp))
            val averages = students.mapNotNull { st -> totalOf(st) }
            val classAvg = if (averages.isEmpty()) null else averages.average()
            Stat(
                when {
                    classAvg == null -> "—"
                    classAvg % 1.0 == 0.0 -> classAvg.toInt().toString()
                    else -> String.format("%.1f", classAvg)
                },
                "class average", Modifier.width(140.dp)
            )
            Spacer(Modifier.weight(1f))
            if (error.isNotBlank()) Text(error, color = T.RUST, fontSize = 12.sp)
            else if (saved.isNotBlank()) Text(saved, color = T.OK, fontSize = 12.sp)
            else if (printed.isNotBlank()) Text(printed, color = T.MUTED, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
            Btn("Print this sheet", { printSheet() }, kind = Kind.GHOST)
            Spacer(Modifier.width(8.dp))
            Btn("Save sheet", { save() })
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Type a mark and press Enter to drop to the next learner, Tab to cross to the next column. " +
                "A1 to A3 are out of $assessMax, the exam out of $examMax. ABS, EXEMPT and NA are accepted.",
            color = T.MUTED, fontSize = 11.sp
        )
    }
}

@Composable
private fun HCell(text: String, width: androidx.compose.ui.unit.Dp?, modifier: Modifier = Modifier, muted: Boolean = false) {
    Text(
        text,
        color = if (muted) T.MUTED else T.INK,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = if (width != null) TextAlign.Center else TextAlign.Start,
        modifier = (if (width != null) modifier.width(width) else modifier).padding(bottom = 2.dp)
    )
}

@Composable
private fun Cell(text: String, width: androidx.compose.ui.unit.Dp, bold: Boolean, muted: Boolean) {
    Text(
        text,
        color = if (muted) T.MUTED else T.INK,
        fontSize = 13.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(width)
    )
}

/** One ruled score box: digits only, Enter down the column, Tab across. */
@Composable
private fun MarkCell(
    value: String,
    max: Int,
    focusKey: String,
    focus: MutableMap<String, FocusRequester>,
    onChange: (String) -> Unit,
    onEnter: () -> Unit,
    onTab: () -> Unit
) {
    val fr = focus.getOrPut(focusKey) { FocusRequester() }
    Box(
        Modifier.width(66.dp)
            .height(28.dp)
            .background(Color.White, RoundedCornerShape(5.dp))
            .border(1.dp, if (value.isBlank()) T.LINE else T.TEAL.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = T.INK, fontSize = 13.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth()
                .focusRequester(fr)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Enter, Key.NumPadEnter -> { onEnter(); true }
                        Key.Tab -> { onTab(); true }
                        else -> false
                    }
                }
        )
    }
}

private fun fmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)

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
