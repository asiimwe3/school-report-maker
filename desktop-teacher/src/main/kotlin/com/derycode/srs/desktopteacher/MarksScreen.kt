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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.AssessmentComponent
import com.derycode.srs.core.model.GradingScheme
import com.derycode.srs.core.model.Level
import com.derycode.srs.core.model.Mark
import com.derycode.srs.core.model.MarkType
import com.derycode.srs.core.model.SchoolClass
import com.derycode.srs.core.model.Student
import com.derycode.srs.core.model.Subject
import com.derycode.srs.core.report.DocxReport
import com.derycode.srs.core.results.AssessmentWeighting

/**
 * The class marksheet, ruled like the paper the school already prints:
 *
 *     No. | NAME | A1 | A2 | A3 | X/20 | X/80 | X/100
 *
 * A2 is typed and carries 20%, A3 is typed and carries 80%. X/20, X/80 and X/100 are
 * worked out as the teacher types, using the same rule as the console and the phone, so
 * nobody adds figures by hand. (A1 prints on the school's form and shows here when the
 * school keeps an A1 column; it does not enter the final score.) Enter drops down the
 * same column, Tab crosses to the next one.
 */
@Composable
fun MarksScreen(state: TeacherStore) {
    val classes = state.myClasses
    var classId by remember { mutableStateOf(classes.firstOrNull()?.id ?: "") }
    LaunchedEffect(classes.map { it.id }) {
        if (classes.none { it.id == classId }) classId = classes.firstOrNull()?.id ?: ""
    }
    val cls: SchoolClass? = state.data.classes.firstOrNull { it.id == classId }
    val subjects = if (classId.isBlank()) emptyList() else state.mySubjectsFor(classId)
    var subjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    LaunchedEffect(classId, subjects.map { it.id }) {
        if (subjects.none { it.id == subjectId }) subjectId = subjects.firstOrNull()?.id ?: ""
    }
    val subject: Subject? = state.data.subjects.firstOrNull { it.id == subjectId }

    // ── the columns this school actually has, in paper order
    val level = cls?.level ?: Level.O_LEVEL
    val forLevel = state.data.components.filter { it.active && (it.levels.isEmpty() || level in it.levels) }
    fun component(code: String) = forLevel.firstOrNull { it.code.equals(code, ignoreCase = true) }
    val typed: List<AssessmentComponent> = listOfNotNull(component("A1"), component("A2"), component("A3"))

    val weighted = state.data.settings.a2a3Weighting
    val rules = AssessmentWeighting.rules(
        forLevel, state.data.settings.a2WeightPercent, state.data.settings.a3WeightPercent, weighted
    )
    // the column headings must say what the engine actually uses
    val w2 = rules.firstOrNull { it.code.equals("A2", true) }?.weightPercent ?: state.data.settings.a2WeightPercent
    val w3 = rules.firstOrNull { it.code.equals("A3", true) }?.weightPercent ?: state.data.settings.a3WeightPercent
    val totalWeight = w2 + w3
    val maxScore = AssessmentWeighting.MAX_MARK.toInt()
    val scheme: GradingScheme? = state.data.gradingSchemes.firstOrNull { it.level == level }
        ?: state.data.gradingSchemes.firstOrNull()

    val students: List<Student> =
        if (classId.isBlank()) emptyList() else state.studentsIn(classId).sortedBy { it.fullName.lowercase() }

    var edits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }
    var printed by remember { mutableStateOf("") }
    val focus = remember { mutableStateMapOf<String, FocusRequester>() }

    fun key(sid: String, cid: String) = "$sid|$cid"
    fun raw(sid: String, cid: String) = edits[key(sid, cid)] ?: ""

    /** The marks as the engine will see them, so the figures on screen are the figures saved. */
    fun marksOf(st: Student): List<Mark> = typed.mapNotNull { c ->
        val v = raw(st.id, c.id).trim().toDoubleOrNull() ?: return@mapNotNull null
        Mark(
            id = "tmp", studentId = st.id, subjectId = subjectId, termId = state.currentTermId,
            componentId = c.id, score = v, type = MarkType.VALUE, maxScore = maxScore
        )
    }

    fun scoreOf(st: Student) = AssessmentWeighting.score(marksOf(st), rules)

    /** X/20 and X/80: what each assessment adds to the total. */
    fun contribution(st: Student, code: String, weight: Int): Double? {
        val c = typed.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: return null
        val m = marksOf(st).firstOrNull { it.componentId == c.id } ?: return null
        return AssessmentWeighting.round1(AssessmentWeighting.percent(m) / 100.0 * weight)
    }

    // load the sheet whenever class, subject or term changes
    LaunchedEffect(classId, subjectId, state.currentTermId, typed.map { it.id }) {
        val term = state.currentTermId
        val loaded = mutableMapOf<String, String>()
        students.forEach { st ->
            typed.forEach { c ->
                val m = state.markFor(st.id, subjectId, term, c.id) ?: return@forEach
                loaded[key(st.id, c.id)] = when (m.type) {
                    MarkType.ABS, MarkType.MISSING -> "ABS"
                    MarkType.EXEMPT -> "EXEMPT"
                    MarkType.NA -> "NA"
                    else -> if (m.score % 1.0 == 0.0) m.score.toInt().toString() else m.score.toString()
                }
            }
        }
        edits = loaded
        saved = ""; error = ""; printed = ""
    }

    fun save() {
        if (classId.isBlank() || subjectId.isBlank() || typed.isEmpty()) return
        var n = 0
        var firstProblem = ""
        students.forEach { st ->
            typed.forEach { c ->
                val text = raw(st.id, c.id).trim()
                val existing = state.markFor(st.id, subjectId, state.currentTermId, c.id)
                if (text.isEmpty()) {
                    if (existing != null) state.removeMark(st.id, subjectId, state.currentTermId, c.id)
                    return@forEach
                }
                val type = when (text.uppercase()) {
                    "ABS", "A" -> MarkType.ABS
                    "EXEMPT", "EX" -> MarkType.EXEMPT
                    "NA", "-" -> MarkType.NA
                    else -> MarkType.VALUE
                }
                val score: Double
                if (type == MarkType.VALUE) {
                    val v = AssessmentWeighting.sanitize(text)
                    if (v == null) {
                        if (firstProblem.isBlank()) {
                            firstProblem = "${st.fullName}: ${c.code} must be a mark from 0 to $maxScore"
                        }
                        return@forEach
                    }
                    score = v
                } else score = 0.0
                state.upsertMark(
                    Mark(
                        id = "m-${st.id}-$subjectId-${state.currentTermId}-${c.id}",
                        studentId = st.id, subjectId = subjectId, termId = state.currentTermId,
                        componentId = c.id, score = score, type = type, maxScore = maxScore,
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

    fun headers(): List<String> =
        listOf("No.", "NAME") + typed.map { it.code.uppercase() } + listOf("X/$w2", "X/$w3", "X/$totalWeight")

    fun printSheet() {
        val suggested = (state.data.school.name.ifBlank { "school" } + " marksheet " + (cls?.name ?: "") + " " +
            (subject?.name ?: ""))
            .replace(Regex("[^A-Za-z0-9 ]"), "").replace(Regex(" +"), " ").trim() + ".docx"
        val target = FilePicker.saveDocx(suggested) ?: return
        try {
            val names = students.map { it.fullName }
            // the paper rules 33 lines; keep them so the sheet can be written on
            val lines = names + List((33 - names.size).coerceAtLeast(0)) { "" }
            DocxReport.writeMarkSheet(
                out = target,
                data = state.data,
                classLabel = cls?.name ?: "—",
                subjectName = subject?.name ?: "",
                termLabel = state.currentTermLabel,
                headers = headers(),
                names = lines
            )
            printed = "Sheet saved to ${target.fileName}"
            FilePicker.revealInFolder(target)
        } catch (e: Exception) {
            error = "Could not write the sheet: ${e.message}"
        }
    }

    Column(Modifier.fillMaxSize().background(T.BG).padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            H1("Enter marks", "${cls?.name ?: "no class"} · ${subject?.name ?: "no subject"} · ${state.currentTermLabel}")
            Spacer(Modifier.weight(1f))
            state.me?.let { Pill(it.name) }
        }
        Spacer(Modifier.height(12.dp))

        if (classes.isEmpty()) {
            Card {
                Text("No classes yet. Open the school file from your admin first.", color = T.RUST, fontSize = 13.sp)
            }
            return@Column
        }
        if (typed.isEmpty()) {
            Card {
                Text(
                    "This school has no A2/A3 assessment columns yet. Open the school file from your admin again, " +
                        "or add them in the admin console.",
                    color = T.RUST, fontSize = 13.sp
                )
            }
            return@Column
        }

        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("CLASS")
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) { classes.forEach { c -> PickChip(c.name, c.id == classId) { classId = c.id } } }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("SUBJECT")
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) { subjects.forEach { s -> PickChip(s.name, s.id == subjectId) { subjectId = s.id } } }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("FINAL SCORE")
                Spacer(Modifier.width(10.dp))
                Text(
                    if (weighted) "A2 × $w2% + A3 × $w3%" else "not weighted",
                    color = T.INK, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(14.dp))
                Text(typed.joinToString("  ·  ") { "${it.code} = ${it.name}" }, color = T.MUTED, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth().weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                HeadCell("No.", 40.dp)
                HeadCell("NAME", null, Modifier.weight(1f))
                typed.forEach { HeadCell(it.code.uppercase(), 66.dp) }
                HeadCell("X/$w2", 62.dp, muted = true)
                HeadCell("X/$w3", 66.dp, muted = true)
                HeadCell("X/$totalWeight", 66.dp, muted = true)
                HeadCell("GRADE", 52.dp, muted = true)
            }
            Spacer(Modifier.height(4.dp))

            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(students, key = { _, st -> st.id }) { index, st ->
                    val score = scoreOf(st)
                    val x20 = contribution(st, "A2", w2)
                    val x80 = contribution(st, "A3", w3)
                    val total = score.final
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
                        Text(st.fullName, color = T.INK, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        typed.forEachIndexed { i, c ->
                            MarkCell(
                                value = raw(st.id, c.id),
                                focusRequester = focus.getOrPut(key(st.id, c.id)) { FocusRequester() },
                                onChange = { new -> edits = edits + (key(st.id, c.id) to new.take(6)); error = "" },
                                onEnter = {
                                    val next = students.getOrNull(index + 1)
                                    if (next != null) {
                                        focus.getOrPut(key(next.id, c.id)) { FocusRequester() }.requestFocus()
                                    } else save()
                                },
                                onTab = {
                                    val nextColumn = typed.getOrNull(i + 1)
                                    if (nextColumn != null) {
                                        focus.getOrPut(key(st.id, nextColumn.id)) { FocusRequester() }.requestFocus()
                                    } else {
                                        val next = students.getOrNull(index + 1)
                                        if (next != null) {
                                            focus.getOrPut(key(next.id, typed.first().id)) { FocusRequester() }.requestFocus()
                                        }
                                    }
                                }
                            )
                        }
                        FigCell(show(x20), 62.dp, muted = x20 == null)
                        FigCell(show(x80), 66.dp, muted = x80 == null)
                        FigCell(AssessmentWeighting.display(score), 66.dp, muted = total == null)
                        Text(
                            if (total == null) "" else score.grade(scheme),
                            color = T.TEAL, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(52.dp), textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val done = students.count { scoreOf(it).final != null }
            Stat("$done/${students.size}", "complete", Modifier.width(118.dp))
            Spacer(Modifier.width(10.dp))
            val finals = students.mapNotNull { scoreOf(it).final }
            Stat(show(if (finals.isEmpty()) null else finals.average()), "class average", Modifier.width(138.dp))
            Spacer(Modifier.weight(1f))
            when {
                error.isNotBlank() -> Text(error, color = T.RUST, fontSize = 12.sp)
                saved.isNotBlank() -> Text(saved, color = T.OK, fontSize = 12.sp)
                printed.isNotBlank() -> Text(printed, color = T.MUTED, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Btn("Print this sheet", { printSheet() }, kind = Kind.GHOST)
            Spacer(Modifier.width(8.dp))
            Btn("Save sheet", { save() })
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Type a mark, press Enter to drop to the next learner, Tab to cross to the next column. " +
                "A2 counts $w2%, A3 counts $w3%, and X/$w2, X/$w3 and X/$totalWeight are worked out for you. " +
                "Marks run 0 to $maxScore; ABS, EXEMPT and NA are also accepted.",
            color = T.MUTED, fontSize = 11.sp
        )
    }
}

@Composable
private fun HeadCell(text: String, width: Dp?, modifier: Modifier = Modifier, muted: Boolean = false) {
    val m = if (width != null) modifier.width(width) else modifier
    Text(
        text,
        color = if (muted) T.MUTED else T.INK,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = if (width != null) TextAlign.Center else TextAlign.Start,
        modifier = m.padding(bottom = 2.dp)
    )
}

@Composable
private fun FigCell(text: String, width: Dp, muted: Boolean) {
    Text(
        text,
        color = if (muted) T.MUTED else T.INK,
        fontSize = 13.sp,
        fontWeight = if (muted) FontWeight.Normal else FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(width)
    )
}

/** One ruled score box: Enter down the column, Tab across. */
@Composable
private fun MarkCell(
    value: String,
    focusRequester: FocusRequester,
    onChange: (String) -> Unit,
    onEnter: () -> Unit,
    onTab: () -> Unit
) {
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
                .focusRequester(focusRequester)
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

/** "16" for a whole figure, "13.3" otherwise, "—" when nothing is there yet. */
private fun show(v: Double?): String = when {
    v == null -> "—"
    v % 1.0 == 0.0 -> v.toInt().toString()
    else -> String.format(java.util.Locale.US, "%.1f", v)
}

@Composable
internal fun PickChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) T.TEAL else T.CARD, RoundedCornerShape(50))
            .border(1.dp, if (selected) T.TEAL else T.LINE, RoundedCornerShape(50))
            .clickableNoRipple(onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else T.INK,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
