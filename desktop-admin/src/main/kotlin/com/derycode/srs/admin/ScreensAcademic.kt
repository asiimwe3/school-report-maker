package com.derycode.srs.admin

import androidx.compose.foundation.Canvas
import com.derycode.srs.core.entry.MarkLedger
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*
import com.derycode.srs.core.report.DocxReport
import com.derycode.srs.core.results.ResultEngine
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path

// ─────────────────────────────────────────────────────────────────────────────
// Marks grid (bulk, keyboard-first)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MarksScreen(state: AppState) {
    val d = state.data
    val year = d.academicYears.firstOrNull { it.currentTermId != null } ?: d.academicYears.firstOrNull()
    val term = year?.terms?.firstOrNull { it.id == year.currentTermId } ?: year?.terms?.firstOrNull()

    var classId by remember(state.level) { mutableStateOf(d.classes.firstOrNull { it.active && it.level == state.level }?.id ?: "") }
    var subjectId by remember { mutableStateOf(d.subjects.firstOrNull { it.active }?.id ?: "") }
    val edits = remember { mutableStateMapOf<String, String>() }
    var error by remember { mutableStateOf("") }

    val cls = d.classes.firstOrNull { it.id == classId }
    // Weighted assessments (A2 20%, A3 80%) lead the grid, then the rest in school order.
    val levelComponents = d.components.filter { term != null && cls != null && it.levels.contains(cls.level) && it.active }
        .sortedByDescending { it.weightPercent }
    val yearFor = year

    ScreenTitle("Marks Grid", "Keyboard-first: type a value, press Enter, it saves. Special: ABS / EXEMPT.")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (term != null) "Term ${term.number} (${year?.year})" else "Create an academic year first", color = Theme.MUTED, fontSize = 14.sp)
            if (d.classes.isEmpty() || d.subjects.isEmpty()) {
                Text("Add classes and subjects first.", color = Theme.WARN, fontSize = 14.sp)
            }
        }
        if (d.classes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.width(200.dp)) {
                    FieldLabel("Class")
                    Level.entries.filter { it == state.level }.forEach { lvl ->
                        val inLevel = d.classes.filter { it.active && it.level == lvl }
                        if (inLevel.isNotEmpty()) {
                            Text(levelLabel(lvl), color = Theme.ACCENT, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                            inLevel.forEach { c ->
                                Text((if (classId == c.id) "● " else "○ ") + if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}",
                                    color = if (classId == c.id) Theme.ACCENT else Theme.TEXT, fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth().clickable { classId = c.id }.padding(vertical = 3.dp))
                            }
                        }
                    }
                }
                Column(Modifier.width(200.dp)) {
                    FieldLabel("Subject")
                    // Subjects offered to the selected class: combination subjects if the class has one,
                    // otherwise every active subject for the class's level. Fallbacks guarantee the list is
                    // never empty (the "no subjects for this class" screen bug).
                    val subjects = cls?.let { c ->
                        val fromComb = if (c.combinationId != null) {
                            val comb = d.combinations.firstOrNull { it.id == c.combinationId }
                            d.subjects.filter { s -> s.active && ((comb?.principalSubjectIds ?: emptyList()).contains(s.id) || (comb?.subsidiarySubjectIds ?: emptyList()).contains(s.id)) }
                        } else emptyList()
                        if (fromComb.isNotEmpty()) fromComb
                        else d.subjects.filter { it.level == c.level && it.active }.ifEmpty { d.subjects.filter { it.active } }
                    } ?: emptyList()
                    // Keep the selected subject valid for this class — auto-switch when it isn't offered,
                    // so marks are never silently saved against a subject the class doesn't take.
                    LaunchedEffect(classId, subjects.firstOrNull()?.id) {
                        if (subjects.isNotEmpty() && subjects.none { it.id == subjectId }) subjectId = subjects.first().id
                    }
                    subjects.forEach { s ->
                        Text((if (subjectId == s.id) "● " else "○ ") + s.name,
                            color = if (subjectId == s.id) Theme.ACCENT else Theme.TEXT, fontSize = 14.sp, fontWeight = if (subjectId == s.id) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable { subjectId = s.id }.padding(vertical = 3.dp))
                    }
                    if (subjects.isEmpty()) Text("No subjects yet — add subjects first.", color = Theme.WARN, fontSize = 12.sp)
                }
                Column {
                    Spacer(Modifier.height(20.dp))
                    Text("Assessment columns for this level:", color = Theme.MUTED, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        levelComponents.forEach { c -> AssistChip(onClick = { }, label = { Text(c.code, fontSize = 12.sp) }) }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        // submit sheet
                        val key = "$classId|$subjectId|${term?.id}"
                        state.repo.mutate("MARKS_SUBMITTED", "MarkSheet", key) { dd ->
                            val sheets = dd.markSheets.toMutableList()
                            val existing = sheets.indexOfFirst { it.classId == classId && it.subjectId == subjectId && it.termId == term?.id }
                            val sheet = if (existing >= 0) sheets[existing].copy(state = MarkSheetState.SUBMITTED)
                            else MarkSheet(id = state.repo.nextId(), classId = classId, subjectId = subjectId, termId = term?.id ?: "", state = MarkSheetState.SUBMITTED)
                            if (existing >= 0) sheets[existing] = sheet else sheets.add(sheet)
                            dd.copy(markSheets = sheets)
                        }
                        state.refresh()
                    }) { Text("Submit sheet for review →", color = Theme.GOOD, fontSize = 14.sp) }
                }
            }
        }

        if (cls != null && subjectId.isNotBlank() && term != null) {
            val students = d.enrollments.filter { it.classId == cls.id && (yearFor == null || it.academicYearId == yearFor.id) }
                .mapNotNull { e -> d.students.firstOrNull { s -> s.id == e.studentId && s.status == StudentStatus.ACTIVE } }

            CardBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(2f)) { HeaderCell("Student") }
                    levelComponents.forEach { Box(Modifier.weight(1f)) { HeaderCell(componentHeader(it)) } }
                    Box(Modifier.weight(1.1f)) { HeaderCell("Final") }
                    Box(Modifier.weight(0.7f)) { HeaderCell("Grade") }
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.height(380.dp)) {
                    items(students) { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(2f)) { Cell(s.fullName, bold = true) }
                            levelComponents.forEach { comp ->
                                val markKey = "${s.id}|$subjectId|${term.id}|${comp.id}"
                                val existing = MarkLedger.get(d.marks, s.id, subjectId, term.id, comp.id)
                                val field = edits[markKey] ?: (existing?.let {
                                    when (it.type) {
                                        MarkType.ABS -> "ABS"; MarkType.EXEMPT -> "EXEMPT"
                                        MarkType.MISSING -> ""; MarkType.NA -> ""
                                        MarkType.VALUE -> if (it.score == it.score.toLong().toDouble()) it.score.toLong().toString() else it.score.toString()
                                    }
                                } ?: "")
                                TextField(
                                    value = field,
                                    onValueChange = { edits[markKey] = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("—", color = Theme.MUTED, fontSize = 13.sp) }
                                )
                            }
                            // Final = A2 × 20% + A3 × 80%, computed from what is in the
                            // boxes right now, so it updates as soon as either changes.
                            val typed = levelComponents.mapNotNull { comp ->
                                val k = "${s.id}|$subjectId|${term.id}|${comp.id}"
                                val txt = edits[k] ?: MarkLedger
                                    .get(d.marks, s.id, subjectId, term.id, comp.id)
                                    ?.takeIf { it.type == MarkType.VALUE }?.score?.let { v -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString() } ?: ""
                                val v = com.derycode.srs.core.results.AssessmentWeighting.sanitize(txt) ?: return@mapNotNull null
                                Mark(
                                    id = "row-${s.id}-${comp.id}", studentId = s.id, subjectId = subjectId,
                                    termId = term.id, componentId = comp.id, score = v, maxScore = 100
                                )
                            }
                            val rowScore = com.derycode.srs.core.results.AssessmentWeighting.forMarks(
                                typed, levelComponents,
                                d.settings.a2a3Weighting
                            )
                            // same scheme the reports use for this class's level
                            val rowScheme = d.gradingSchemes.firstOrNull { it.level == cls.level && it.active }
                                ?: d.gradingSchemes.firstOrNull { it.level == cls.level }
                            Box(Modifier.weight(1.1f)) {
                                Cell(
                                    when {
                                        rowScore.final != null -> com.derycode.srs.core.results.AssessmentWeighting.display(rowScore)
                                        rowScore.weighted -> "incomplete"
                                        else -> "—"
                                    },
                                    bold = rowScore.final != null,
                                    color = if (rowScore.final != null) Theme.TEXT else Theme.MUTED
                                )
                            }
                            Box(Modifier.weight(0.7f)) {
                                val g = rowScore.grade(rowScheme)
                                Cell(g, bold = g != "—", color = if (g == "—") Theme.MUTED else Theme.GOOD)
                            }
                        }
                        // save on change is too aggressive; save happens per field on Enter via explicit button below
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn("Save all entered marks") {
                        var saved = 0; var errs = 0
                        edits.forEach { (key, text) ->
                            val (sid, subj, tid, cid) = key.split("|")
                            val max = d.subjects.firstOrNull { it.id == subj }?.maxMarks ?: 100
                            val t = text.trim()
                            if (t.isEmpty()) return@forEach
                            val type: MarkType
                            val score: Double
                            when (t.uppercase()) {
                                "ABS" -> { type = MarkType.ABS; score = 0.0 }
                                "EXEMPT" -> { type = MarkType.EXEMPT; score = 0.0 }
                                else -> {
                                    val err = ResultEngine.validateMark(t, max, allowBlank = false)
                                    if (err != null) { errs++; return@forEach }
                                    type = MarkType.VALUE; score = t.toDouble()
                                }
                            }
                            state.repo.upsertMark(Mark(
                                id = state.repo.nextId(), studentId = sid, subjectId = subj, termId = tid,
                                componentId = cid, score = score, type = type, maxScore = max
                            ))
                            saved++
                        }
                        error = if (errs > 0) "$errs value(s) invalid and were NOT saved." else ""
                        edits.clear()
                        state.refresh()
                    }
                    if (error.isNotBlank()) ErrorText(error)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grading scheme editor
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GradingScreen(state: AppState) {
    val d = state.data
    var selectedId by remember { mutableStateOf(d.gradingSchemes.firstOrNull()?.id ?: "") }
    val scheme = d.gradingSchemes.firstOrNull { it.id == selectedId }

    ScreenTitle("Grading Schemes", "Boundaries, labels, points — all data, all adjustable. Old reports keep the scheme they were printed with.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            d.gradingSchemes.forEach { s ->
                FilterChip(selected = selectedId == s.id, onClick = { selectedId = s.id }, label = { Text("${s.name} (v${s.version})", fontSize = 13.sp) })
            }
        }

        scheme?.let { s ->
            CardBox {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, color = Theme.TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(levelLabel(s.level), color = Theme.MUTED, fontSize = 13.sp)
                    Text("Rule: ${s.aggregateRule}", color = Theme.MUTED, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Btn("New version (bump v${s.version} → v${s.version + 1})") {
                        state.repo.mutate("SCHEME_VERSION_BUMPED", "GradingScheme", s.id, old = "v${s.version}", new = "v${s.version + 1}") { dd ->
                            dd.copy(gradingSchemes = dd.gradingSchemes.map { if (it.id == s.id) it.copy(version = it.version + 1) else it })
                        }
                        state.refresh()
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(0.7f)) { HeaderCell("Min %") }
                    Box(Modifier.weight(0.7f)) { HeaderCell("Max %") }
                    Box(Modifier.weight(0.6f)) { HeaderCell("Grade") }
                    Box(Modifier.weight(1.4f)) { HeaderCell("Label") }
                    Box(Modifier.weight(0.6f)) { HeaderCell("Points") }
                    Box(Modifier.weight(2f)) { HeaderCell("Remark") }
                }
                var minEdits = remember { mutableStateMapOf<String, String>() }
                var maxEdits = remember { mutableStateMapOf<String, String>() }
                s.boundaries.forEach { b ->
                    val key = "${b.minScore}-${b.maxScore}-${b.grade}"
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(0.7f)) { TextField(minEdits[key] ?: b.minScore.toString(), { minEdits[key] = it }, Modifier.fillMaxWidth()) }
                        Box(Modifier.weight(0.7f)) { TextField(maxEdits[key] ?: b.maxScore.toString(), { maxEdits[key] = it }, Modifier.fillMaxWidth()) }
                        Box(Modifier.weight(0.6f)) { Cell(b.grade, bold = true, color = Theme.ACCENT) }
                        Box(Modifier.weight(1.4f)) { Cell(b.label, color = Theme.MUTED) }
                        Box(Modifier.weight(0.6f)) { Cell(b.points.toString()) }
                        Box(Modifier.weight(2f)) { Cell(b.remark, color = Theme.MUTED) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn("Save boundary changes") {
                        state.repo.mutate("SCHEME_CHANGED", "GradingScheme", s.id) { dd ->
                            dd.copy(gradingSchemes = dd.gradingSchemes.map { sch ->
                                if (sch.id == s.id) sch.copy(boundaries = sch.boundaries.map { b ->
                                    val key = "${b.minScore}-${b.maxScore}-${b.grade}"
                                    val newMin = minEdits[key]?.toDoubleOrNull() ?: b.minScore
                                    val newMax = maxEdits[key]?.toDoubleOrNull() ?: b.maxScore
                                    b.copy(minScore = newMin, maxScore = newMax)
                                }) else sch
                            })
                        }
                        minEdits.clear(); maxEdits.clear()
                        state.refresh()
                    }
                    Text("Existing approved reports are not affected (grade history).", color = Theme.MUTED, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("Preview: 50% → grade ${previewGrade(s, 50.0)} · 79.9% → ${previewGrade(s, 79.9)} · 90% → ${previewGrade(s, 90.0)}", color = Theme.GOOD, fontSize = 14.sp)
            }
        }
    }
}

fun previewGrade(s: GradingScheme, pct: Double): String =
    com.derycode.srs.core.grading.GradingEngine.grade(s, pct)

// ─────────────────────────────────────────────────────────────────────────────
// Results review
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ResultsScreen(state: AppState) {
    val d = state.data
    var classId by remember(state.level) { mutableStateOf(d.classes.firstOrNull { it.active && it.level == state.level }?.id ?: "") }
    val year = d.academicYears.firstOrNull { it.currentTermId != null } ?: d.academicYears.firstOrNull()
    val term = year?.terms?.firstOrNull { it.id == year.currentTermId } ?: year?.terms?.firstOrNull()
    val cls = d.classes.firstOrNull { it.id == classId }
    var schemeId by remember { mutableStateOf("") }
    val activeScheme = schemeId.ifBlank { d.gradingSchemes.firstOrNull { it.level == cls?.level }?.id ?: d.gradingSchemes.firstOrNull()?.id ?: "" }

    val results = remember(state.refreshTick, classId, term?.id, activeScheme) {
        if (cls != null && term != null && activeScheme.isNotBlank())
            ResultEngine.classResults(d, classId, term.id, activeScheme)
        else emptyList()
    }

    ScreenTitle("Results Review", "Check calculations, approve and lock. Locked results need authorization to change.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Class:", color = Theme.MUTED, fontSize = 14.sp)
            ClassPickerChips(d.classes.filter { it.active && it.level == state.level }, classId) { classId = it }
        }
        if (cls == null || term == null) {
            Text("Create an academic year with terms and add classes first.", color = Theme.MUTED, fontSize = 14.sp)
        } else {
            val missing = ResultEngine.studentsWithMissingMarks(d, classId, term.id, d.subjects.firstOrNull()?.id ?: "")
            CardBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(2f)) { HeaderCell("Student") }
                    Box(Modifier.weight(1f)) { HeaderCell("Average") }
                    Box(Modifier.weight(1.4f)) { HeaderCell("Overall") }
                    Box(Modifier.weight(0.8f)) { HeaderCell("Pos") }
                    Box(Modifier.weight(1.2f)) { HeaderCell("Status") }
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.height(400.dp)) {
                    items(results) { r ->
                        val s = d.students.firstOrNull { it.id == r.studentId }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(2f)) { Cell(s?.fullName ?: "?", bold = true) }
                            Box(Modifier.weight(1f)) { Cell("${r.averagePercent}%") }
                            Box(Modifier.weight(1.4f)) { Cell(r.division ?: r.uceResult ?: r.uacePoints?.let { "$it pts" } ?: "—", color = Theme.ACCENT) }
                            Box(Modifier.weight(0.8f)) { Cell(r.classPosition.toString()) }
                            Box(Modifier.weight(1.2f)) {
                                Cell(
                                    if (r.subjectResults.any { it.incomplete }) "Incomplete" else "Ready",
                                    color = if (r.subjectResults.any { it.incomplete }) Theme.WARN else Theme.GOOD
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Approve & lock results") {
                        state.repo.mutate("RESULTS_LOCKED", "TermResult", classId) { dd ->
                            dd.copy(termResults = results.map { it.copy(state = MarkSheetState.LOCKED) } +
                                dd.termResults.filterNot { tr -> results.any { it.studentId == tr.studentId && it.termId == tr.termId } })
                        }
                        state.refresh()
                    }
                    Text("Locking freezes the scheme version used on reports (reproducibility).", color = Theme.MUTED, fontSize = 13.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reports
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReportsScreen(state: AppState) {
    val d = state.data
    var classId by remember(state.level) { mutableStateOf(d.classes.firstOrNull { it.active && it.level == state.level }?.id ?: "") }
    var layout by remember(classId) { mutableStateOf(templateFor(d, d.classes.firstOrNull { it.id == classId })?.layout ?: TemplateLayout.CLASSIC) }
    var previewIndex by remember(classId) { mutableStateOf(0) }
    val year = d.academicYears.firstOrNull { it.currentTermId != null } ?: d.academicYears.firstOrNull()
    val term = year?.terms?.firstOrNull { it.id == year.currentTermId } ?: year?.terms?.firstOrNull()
    val cls = d.classes.firstOrNull { it.id == classId }
    val scheme = d.gradingSchemes.firstOrNull { it.level == cls?.level } ?: d.gradingSchemes.firstOrNull()

    ScreenTitle("Reports", "Print-ready HTML report cards — open in any browser, print to PDF.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Class:", color = Theme.MUTED, fontSize = 14.sp)
            ClassPickerChips(d.classes.filter { it.active && it.level == state.level }, classId) { classId = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Design:", color = Theme.MUTED, fontSize = 14.sp)
            TemplateLayout.entries.forEach { l ->
                FilterChip(selected = layout == l, onClick = { layout = l }, label = { Text(l.displayName, fontSize = 13.sp) })
            }
        }
        if (cls == null || term == null || scheme == null) {
            Text("Set up classes and an academic year with terms first.", color = Theme.MUTED, fontSize = 14.sp)
        } else {
            val results = ResultEngine.classResults(d, classId, term.id, scheme.id)
            if (results.isEmpty()) {
                Text("No students enrolled in this class for ${year?.year}.", color = Theme.MUTED, fontSize = 14.sp)
            } else {
                // ── The real report card, right here ─────────────────────────
                // Same renderer the print output uses, fed by this class's real
                // marks, so what you see is what a parent will receive.
                val idx = previewIndex.coerceIn(0, (results.size - 1).coerceAtLeast(0))
                val shown = results.getOrNull(idx)
                val shownStudent = shown?.let { r -> d.students.firstOrNull { it.id == r.studentId } }
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Report card preview", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Real data from this class: marks, grades, aggregate and comments exactly as they print.",
                                color = Theme.MUTED, fontSize = 13.sp)
                        }
                        if (results.size > 1) {
                            Btn("<", primary = false) { previewIndex = (idx - 1 + results.size) % results.size }
                            Spacer(Modifier.width(6.dp))
                            Text("${idx + 1} / ${results.size}", color = Theme.MUTED, fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Btn(">", primary = false) { previewIndex = (idx + 1) % results.size }
                        }
                    }
                    if (shown != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(shownStudent?.fullName ?: shown.studentId, color = Theme.ACCENT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(
                                shown.division?.let { "Division $it" },
                                shown.uceResult?.let { "UCE $it" },
                                shown.uacePoints?.let { "$it points" },
                                "Average ${"%.1f".format(shown.averagePercent)}%"
                            ).joinToString("  ·  "),
                            color = Theme.MUTED, fontSize = 13.sp
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.weight(1f).background(Color(0xFFEFEFEF), RoundedCornerShape(10.dp)).padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ReportPaper(
                                d = d,
                                template = templateFor(d, cls)?.copy(layout = layout) ?: ReportTemplate(
                                    id = "inline", name = "Inline", layout = layout
                                ),
                                cls = cls,
                                term = year?.let { y -> y to (term) },
                                student = shownStudent,
                                result = shown,
                                widthDp = 470
                            )
                        }
                        Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("Print this card (Word)") {
                                val outDir = dataDir.resolve("reports")
                                Files.createDirectories(outDir)
                                shown?.let { r ->
                                    val f = outDir.resolve("student-${r.studentId}-report.docx")
                                    DocxReport.writeStudentReport(f, d, r, layout, templateFor(d, cls)?.accentColor)
                                    try { Desktop.getDesktop().open(f.toFile()) } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                            Btn("Print / save as PDF") {
                                // The summary card prints from this HTML: open it in the
                                // browser and press Ctrl+P (or Save as PDF).
                                val outDir = dataDir.resolve("reports")
                                Files.createDirectories(outDir)
                                shown?.let { r ->
                                    val html = com.derycode.srs.core.report.ReportEngine.studentReport(d, r, layout)
                                    val f = outDir.resolve("card-${r.studentId}-${layout.name.lowercase()}.html")
                                    Files.writeString(f, html)
                                    try { Desktop.getDesktop().open(f.toFile()) } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                            Btn("Preview whole class", primary = false) { state.screen = "preview" }
                            Text("Preview opens every card in the class on one scrolling page.", color = Theme.MUTED, fontSize = 12.sp)
                        }
                    }
                }
                CardBox {
                    Text("Batch — whole class (${results.size} students)", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Each student gets one A4 card: school header, marks, grades, aggregate/division, comments, signature lines, grading key.", color = Theme.MUTED, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn("Generate Word documents (.docx)") {
                            val outDir = dataDir.resolve("reports")
                            Files.createDirectories(outDir)
                            val f = outDir.resolve("class-${cls.name.replace(" ", "")}-${term.number}-reports.docx")
                            DocxReport.writeClassReport(f, d, results, layout, templateFor(d, cls)?.accentColor)
                            // record generated reports with frozen scheme version
                            state.repo.mutate("REPORTS_GENERATED", "Report", classId) { dd ->
                                dd.copy(reports = results.map { r ->
                                    GeneratedReport(
                                        id = state.repo.nextId(), studentId = r.studentId, termId = term.id,
                                        templateId = layout.name, schemeId = scheme.id, schemeVersion = scheme.version,
                                        approved = true, generatedAt = System.currentTimeMillis(),
                                        resultSummary = r.division ?: r.uceResult ?: r.uacePoints?.let { "$it pts" } ?: "${r.averagePercent}%"
                                    )
                                } + dd.reports)
                            }
                            state.refresh()
                            try { Desktop.getDesktop().open(f.toFile()) } catch (e: Exception) { e.printStackTrace() }
                        }
                        Btn("Print merit list (Word)", primary = false) {
                            val outDir = dataDir.resolve("reports")
                            Files.createDirectories(outDir)
                            val label = if (cls.stream.isBlank()) cls.name else "${cls.name} ${cls.stream}"
                            val f = outDir.resolve("merit-${label.replace(" ", "")}-term${term.number}.docx")
                            DocxReport.writeMeritList(f, d, results, label, "Term ${term.number} ${year?.year}")
                            try { Desktop.getDesktop().open(f.toFile()) } catch (e: Exception) { e.printStackTrace() }
                        }
                        Btn("Generate single (first student)", primary = false) {
                            val outDir = dataDir.resolve("reports")
                            Files.createDirectories(outDir)
                            val f = outDir.resolve("student-${results.first().studentId}-report.docx")
                            DocxReport.writeStudentReport(f, d, results.first(), layout, templateFor(d, cls)?.accentColor)
                            try { Desktop.getDesktop().open(f.toFile()) } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                CardBox {
                    Text("Recently generated (frozen scheme versions)", color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    d.reports.takeLast(8).reversed().forEach { r ->
                        val s = d.students.firstOrNull { it.id == r.studentId }
                        Text("• ${s?.fullName ?: r.studentId} — ${r.resultSummary} — scheme v${r.schemeVersion}", color = Theme.MUTED, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    if (d.reports.isEmpty()) Text("None yet.", color = Theme.MUTED, fontSize = 13.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sync & backup
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SyncScreen(state: AppState) {
    val d = state.data
    var message by remember { mutableStateOf("") }
    var bundlePath by remember { mutableStateOf("") }

    val backupsDir = dataDir.resolve("backups")

    ScreenTitle("Sync & Backup", "Local file is the primary database. The cloud is only a relay — and optional.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Backups kept", countBackups(backupsDir).toString(), Theme.GOOD)
            StatCard("Audit entries", d.auditLog.size.toString())
            StatCard("Pending sync", d.syncQueue.count { it.status == "PENDING" }.toString(), Theme.WARN)
            StatCard("Conflicts", d.conflicts.count { !it.resolved }.toString(), Theme.WARN)
        }
        CardBox {
            Text("Backup", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Create backup now") {
                    val p = state.repo.store.backup(dataFile)
                    message = "Backup saved: ${p.fileName}"
                    state.refresh()
                }
            }
            if (message.isNotBlank()) Cell(message, color = Theme.GOOD)
        }
        CardBox {
            Text("Restore", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("A safety backup of current data is taken automatically before any restore.", color = Theme.MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            val backups = listBackups(backupsDir)
            if (backups.isEmpty()) Text("No backups yet.", color = Theme.MUTED, fontSize = 14.sp)
            else LazyColumn(Modifier.height(150.dp)) {
                items(backups.takeLast(10).reversed()) { b ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Cell(b.fileName.toString(), color = Theme.MUTED)
                        TextButton(onClick = {
                                    message = if (state.repo.restore(b)) "Restored from ${b.fileName}" else "Restore failed"
                                    state.refresh()
                                }) { Text("Restore", color = Theme.WARN, fontSize = 13.sp) }
                    }
                }
            }
        }
        CardBox {
            Text("Export school setup for teacher phones", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Each school runs its own admin console and exports its own bundle — teachers connect only to their own school.", color = Theme.MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("1. Tap Export school setup — creates school-data-from-admin.json (classes, students, subjects, schemes — no marks)\n2. Copy it to each teacher's phone via USB or Bluetooth\n3. On the teacher's phone: More → Sync & Export → Import\n4. Teacher picks their name and enters marks for their classes\n5. Teacher taps Export marks bundle and copies it back here\n6. Import bundle below — newest-wins merge, everything logged", color = Theme.MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("The teacher app now shows which school it is connected to, and refuses to silently mix two different schools' data — if a teacher joins another school, it asks before replacing.", color = Theme.GOOD, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Export school setup") {
                    val out = dataDir.resolve("exports").resolve("school-data-from-admin.json")
                    state.repo.store.exportSchoolConfig(out, state.repo.data)
                    state.repo.audit("SCHOOL_CONFIG_EXPORTED", "SchoolData", new = out.toString())
                    message = "Saved: ${out.toAbsolutePath()}"
                }
                Btn("Open exports folder", primary = false, onClick = {
                    val dir = dataDir.resolve("exports").toFile()
                    if (!dir.exists()) dir.mkdirs()
                    try { java.awt.Desktop.getDesktop().open(dir) } catch (e: Exception) { e.printStackTrace() }
                })
            }
        }
        CardBox {
            Text("Teacher bundle (USB / Bluetooth)", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(bundlePath, { bundlePath = it }, Modifier.width(360.dp), "Path to bundle file from teacher's phone")
                Btn("Import bundle") {
                    val p = Path.of(bundlePath)
                    if (!Files.exists(p)) { message = "File not found: $bundlePath" }
                    else {
                        val report = state.repo.store.importBundle(state.repo.data, p)
                        if (report.success) {
                            state.repo.mutate("BUNDLE_IMPORTED", "Mark", new = report.message) { dd ->
                                dd.copy(
                                    marks = state.repo.store.mergeMarks(dd, report.merged.ifEmpty { dd.marks }),
                                    students = (dd.students.filter { s -> report.mergedStudents.none { it.id == s.id } } + report.mergedStudents),
                                    enrollments = (dd.enrollments.filter { e -> report.mergedEnrollments.none { it.id == e.id } } + report.mergedEnrollments),
                                    enrollmentRequests = (dd.enrollmentRequests.filter { r -> report.mergedRequests.none { it.id == r.id } } + report.mergedRequests),
                                    dutyRecords = (dd.dutyRecords.filter { r -> report.mergedDutyRecords.none { it.id == r.id } } + report.mergedDutyRecords),
                                    gatePasses = (dd.gatePasses.filter { g -> report.mergedGatePasses.none { it.id == g.id } } + report.mergedGatePasses),
                                    attendance = (dd.attendance.filter { a -> report.mergedAttendance.none { it.id == a.id } } + report.mergedAttendance),
                                    comments = if (report.mergedComments.isEmpty()) dd.comments else report.mergedComments)
                            }
                        }
                        message = report.message
                        state.refresh()
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Bundles are versioned and validated — a corrupt or old bundle can never corrupt newer data.", color = Theme.MUTED, fontSize = 13.sp)
        }
        CardBox {
            Text("Audit trail (latest 12)", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            d.auditLog.takeLast(12).reversed().forEach { a ->
                Text("• ${a.action} ${a.entity}${if (a.entityId.isNotBlank()) "(${a.entityId})" else ""} ${if (a.newValue.isNotBlank()) "→ ${a.newValue.take(40)}" else ""}",
                    color = Theme.MUTED, fontSize = 10.5.sp, modifier = Modifier.padding(vertical = 1.dp))
            }
            if (d.auditLog.isEmpty()) Text("Nothing yet — actions will appear here.", color = Theme.MUTED, fontSize = 13.sp)
        }
    }
}

private fun countBackups(dir: Path): Int =
    if (java.nio.file.Files.exists(dir)) dir.toFile().listFiles { f -> f.name.startsWith("backup-") }?.size ?: 0 else 0

internal fun listBackups(dir: Path): List<Path> =
    if (java.nio.file.Files.exists(dir)) dir.toFile().listFiles { f -> f.name.startsWith("backup-") }?.map { it.toPath() }?.sortedBy { it.fileName.toString() } ?: emptyList()
    else emptyList()


// ─────────────────────────────────────────────────────────────────────────────
// Analytics — charts, rankings, performance insights
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnalyticsScreen(state: AppState) {
    val d = state.data
    var classId by remember(state.level) { mutableStateOf(d.classes.firstOrNull { it.active && it.level == state.level }?.id ?: "") }
    val year = d.academicYears.firstOrNull { it.currentTermId != null } ?: d.academicYears.firstOrNull()
    val term = year?.terms?.firstOrNull { it.id == year.currentTermId } ?: year?.terms?.firstOrNull()
    val cls = d.classes.firstOrNull { it.id == classId }
    val scheme = d.gradingSchemes.firstOrNull { it.level == cls?.level } ?: d.gradingSchemes.firstOrNull()
    val results = remember(state.refreshTick, classId, term?.id, scheme?.id) {
        if (cls != null && term != null && scheme != null) ResultEngine.classResults(d, classId, term.id, scheme.id) else emptyList()
    }

    ScreenTitle("Analytics", "Live performance insights — averages, rankings, subject trends, gender split.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClassPickerChips(d.classes.filter { it.active && it.level == state.level }, classId) { classId = it }
        if (results.isEmpty()) {
            CardBox { Text(if (cls == null || term == null) "Set up classes and an academic year first." else "No marks entered for this class/term yet — enter marks in the Marks Grid to see analytics.", color = Theme.MUTED, fontSize = 14.sp) }
        } else {
            val avg = results.map { it.averagePercent }.average()
            val pass = results.count { it.averagePercent >= 50 }
            val boys = results.count { r -> d.students.firstOrNull { it.id == r.studentId }?.sex == "M" }
            val girls = results.size - boys
            val ranked = results.filter { it.classPosition > 0 }.sortedBy { it.classPosition }

            CardBox {
                Text("Class snapshot — ${cls?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" }}, Term ${term?.number} ${year?.year}",
                    color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) { Text("Class average", color = Theme.MUTED, fontSize = 13.sp); Text("${"%.1f".format(avg)}%", color = Theme.ACCENT, fontSize = 26.sp, fontWeight = FontWeight.Black) }
                    Column(Modifier.weight(1f)) { Text("Passing (≥50%)", color = Theme.MUTED, fontSize = 13.sp); Text("$pass / ${results.size}", color = Theme.GOOD, fontSize = 26.sp, fontWeight = FontWeight.Black) }
                    Column(Modifier.weight(1f)) { Text("Best student", color = Theme.MUTED, fontSize = 13.sp); Text(ranked.firstOrNull()?.let { r -> d.students.firstOrNull { it.id == r.studentId }?.fullName ?: "—" } ?: "—", color = Theme.TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                }
            }

            CardBox {
                Text("Subject performance — class average % per subject", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val subjectAvgs = d.subjects.mapNotNull { sub ->
                    val marks = results.flatMap { r -> r.subjectResults.filter { it.subjectId == sub.id }.map { it.percentage } }
                    if (marks.isEmpty()) null else sub.name to marks.average()
                }
                subjectAvgs.sortedByDescending { it.second }.forEach { (name, pct) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name.take(18), color = Theme.MUTED, fontSize = 13.sp, modifier = Modifier.width(120.dp))
                        Box(Modifier.weight(1f).height(14.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRoundRect(color = Theme.LINE, cornerRadius = CornerRadius(7.dp.toPx()))
                                drawRoundRect(color = Theme.ACCENT, cornerRadius = CornerRadius(7.dp.toPx()),
                                    size = Size(size.width * (pct / 100.0).toFloat(), size.height))
                            }
                        }
                        Text("${pct.toInt()}%", color = if (pct >= 50) Theme.GOOD else Theme.WARN, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                    }
                }
                if (subjectAvgs.isEmpty()) Text("No subject marks yet.", color = Theme.MUTED, fontSize = 13.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { CardBox {
                    Text("Top 5 students", color = Theme.GOOD, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    ranked.take(5).forEach { r ->
                        val s = d.students.firstOrNull { it.id == r.studentId }
                        Text("${r.classPosition}. ${s?.fullName ?: "—"} — ${"%.1f".format(r.averagePercent)}%", color = Theme.TEXT, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    if (ranked.isEmpty()) Text("No ranked students yet.", color = Theme.MUTED, fontSize = 13.sp)
                } }
                Box(Modifier.weight(1f)) { CardBox {
                    Text("Needs attention — bottom 5", color = Theme.WARN, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    ranked.takeLast(5).reversed().forEach { r ->
                        val s = d.students.firstOrNull { it.id == r.studentId }
                        Text("${s?.fullName ?: "—"} — ${"%.1f".format(r.averagePercent)}%", color = Theme.TEXT, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    if (ranked.isEmpty()) Text("No ranked students yet.", color = Theme.MUTED, fontSize = 13.sp)
                } }
            }

            CardBox {
                Text("Gender split", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Boys $boys", color = Theme.MUTED, fontSize = 13.sp, modifier = Modifier.width(90.dp))
                    Box(Modifier.weight(1f).height(14.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRoundRect(color = Theme.LINE, cornerRadius = CornerRadius(7.dp.toPx()))
                            drawRoundRect(color = Theme.ACCENT, cornerRadius = CornerRadius(7.dp.toPx()),
                                size = Size(size.width * (if (results.isEmpty()) 0f else boys.toFloat() / results.size), size.height))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Girls $girls", color = Theme.MUTED, fontSize = 13.sp, modifier = Modifier.width(90.dp))
                    Box(Modifier.weight(1f).height(14.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRoundRect(color = Theme.LINE, cornerRadius = CornerRadius(7.dp.toPx()))
                            drawRoundRect(color = Color(0xFFFF7EB6), cornerRadius = CornerRadius(7.dp.toPx()),
                                size = Size(size.width * (if (results.isEmpty()) 0f else girls.toFloat() / results.size), size.height))
                        }
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Attendance — per-class register view + CSV export
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AttendanceScreen(state: AppState) {
    val d = state.data
    var classId by remember(state.level) { mutableStateOf(d.classes.firstOrNull { it.active && it.level == state.level }?.id ?: "") }
    var msg by remember { mutableStateOf("") }
    val klass = d.classes.firstOrNull { it.id == classId }
    val students = d.enrollments.filter { it.classId == classId }
        .sortedBy { it.studentId }.map { e -> d.students.firstOrNull { it.id == e.studentId } }
        .filterNotNull().distinctBy { it.id }
    val recs = d.attendance.filter { it.classId == classId }

    ScreenTitle("Attendance", "Daily register from teacher phones — present / absent / late, per class.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClassPickerChips(d.classes.filter { it.active && it.level == state.level }, classId) { classId = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Recorded days", recs.map { it.date }.distinct().count().toString(), Theme.GOOD)
            StatCard("Students", students.size.toString())
            StatCard("Attendance %", if (recs.isEmpty()) "—" else "${(recs.count { it.status == "PRESENT" } * 100.0 / recs.size).toInt()}%")
        }
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Export attendance CSV", primary = false) {
                    val f = dataDir.resolve("exports").resolve("attendance-${klass?.name ?: "class"}.csv")
                    Files.createDirectories(f.toAbsolutePath().parent)
                    val rows = students.joinToString("\n") { s ->
                        val a = recs.filter { it.studentId == s.id }
                        listOf(s.admissionNo, s.fullName, a.count { it.status == "PRESENT" }.toString(),
                            a.count { it.status == "LATE" }.toString(), a.count { it.status == "ABSENT" }.toString(),
                            if (a.isEmpty()) "" else "${(a.count { it.status == "PRESENT" } * 100.0 / a.size).toInt()}%")
                            .joinToString(",") { csvCell(it) }
                    }
                    Files.writeString(f, "admissionNo,student,present,late,absent,attendancePercent\n$rows")
                    msg = "Saved ${f.fileName}"
                    try { java.awt.Desktop.getDesktop().open(f.toFile().parentFile) } catch (e: Exception) { e.printStackTrace() }
                }
            }
            if (msg.isNotBlank()) Cell(msg, color = Theme.GOOD)
            Spacer(Modifier.height(6.dp))
            if (recs.isEmpty()) Text("No attendance recorded for this class yet — teachers mark it daily on their phones (More → Attendance) and it syncs in their bundle.",
                color = Theme.MUTED, fontSize = 13.sp)
        }
        CardBox {
            Text("Per-student totals", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (students.isEmpty()) Text("No students in this class.", color = Theme.MUTED, fontSize = 14.sp)
            LazyColumn(Modifier.height(320.dp)) {
                items(students) { s ->
                    val a = recs.filter { it.studentId == s.id }
                    val pct = if (a.isEmpty()) 0.0 else a.count { it.status == "PRESENT" } * 100.0 / a.size
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.fullName, color = Theme.TEXT, fontSize = 14.sp, modifier = Modifier.weight(1.4f))
                        Text("P ${a.count { it.status == "PRESENT" }}", color = Theme.GOOD, fontSize = 14.sp, modifier = Modifier.weight(0.5f))
                        Text("L ${a.count { it.status == "LATE" }}", color = Theme.WARN, fontSize = 14.sp, modifier = Modifier.weight(0.5f))
                        Text("A ${a.count { it.status == "ABSENT" }}", color = Color(0xFFEF4444), fontSize = 14.sp, modifier = Modifier.weight(0.5f))
                        Text(if (a.isEmpty()) "—" else "${pct.toInt()}%", color = if (pct >= 75) Theme.GOOD else Theme.WARN, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Comment bank — teacher & headteacher reusable comments
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CommentBankScreen(state: AppState) {
    val d = state.data
    var category by remember { mutableStateOf("TEACHER") }
    var newText by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    ScreenTitle("Comment Bank", "Reusable comments for teachers and the headteacher — tap-to-insert on teacher phones.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("TEACHER" to "Class teacher", "HEAD" to "Headteacher").forEach { (id, label) ->
                FilterChip(selected = category == id, onClick = { category = id }, label = { Text(label, fontSize = 13.sp) })
            }
        }
        CardBox {
            Text("Add to ${if (category == "TEACHER") "class teacher" else "headteacher"} bank", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            TextField(newText, { newText = it }, Modifier.fillMaxWidth(), "Comment text…")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Add comment") {
                    if (newText.isNotBlank()) {
                        val id = "cb-${System.currentTimeMillis()}"
                        state.repo.mutate("COMMENT_TEMPLATE_ADDED", "CommentTemplate", new = id) { dd ->
                            dd.copy(commentTemplates = dd.commentTemplates + CommentTemplate(id, category, newText.trim()))
                        }
                        newText = ""
                        msg = "Added to the bank."
                    }
                }
            }
            if (msg.isNotBlank()) Cell(msg, color = Theme.GOOD)
        }
        CardBox {
            Text("${d.commentTemplates.count { it.category == category }} comments in this bank", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.height(360.dp)) {
                items(d.commentTemplates.filter { it.category == category }) { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.text, color = Theme.TEXT, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            state.repo.mutate("COMMENT_TEMPLATE_REMOVED", "CommentTemplate", old = c.id) { dd ->
                                dd.copy(commentTemplates = dd.commentTemplates.filter { it.id != c.id })
                            }
                        }) { Text("Delete", color = Theme.WARN, fontSize = 13.sp) }
                    }
                }
            }
        }
        Cell("Syncs to teacher phones with the next school setup export.", color = Theme.MUTED)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Promotion & rollover — move students up, start the next academic year
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PromotionScreen(state: AppState) {
    val d = state.data
    val active = d.classes.filter { it.active && it.level == state.level }
    var fromId by remember(state.level) { mutableStateOf(active.firstOrNull()?.id ?: "") }
    var toId by remember(state.level) { mutableStateOf(active.lastOrNull()?.id ?: "") }
    var msg by remember { mutableStateOf("") }
    var rolloverConfirm by remember { mutableStateOf(false) }

    val latestEnrollment = { studentId: String -> d.enrollments.lastOrNull { it.studentId == studentId } }
    val toMove = d.students.filter { latestEnrollment(it.id)?.classId == fromId }

    ScreenTitle("Promotion & Rollover", "Move students to the next class and start the next academic year — all offline.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Text("Promote students to the next class", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Leavers (P7 / S4 / S6 / U6) are NOT moved — graduate them by leaving the class as-is.", color = Theme.MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text("From:", color = Theme.MUTED, fontSize = 13.sp)
            ClassPickerChips(active, fromId) { fromId = it }
            Spacer(Modifier.height(6.dp))
            Text("To:", color = Theme.MUTED, fontSize = 13.sp)
            ClassPickerChips(active, toId) { toId = it }
            Spacer(Modifier.height(8.dp))
            Cell("${toMove.size} students will be promoted.", color = Theme.TEXT, bold = true)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Promote ${toMove.size} students", primary = toMove.isNotEmpty()) {
                    val yearId = d.academicYears.firstOrNull { it.currentTermId != null }?.id ?: d.academicYears.firstOrNull()?.id ?: ""
                    state.repo.mutate("STUDENTS_PROMOTED", "Enrollment", new = "$fromId -> $toId (${toMove.size})") { dd ->
                        dd.copy(enrollments = dd.enrollments + toMove.map {
                            Enrollment(id = "enr-prom-${it.id}-${System.currentTimeMillis()}", studentId = it.id,
                                academicYearId = yearId, classId = toId)
                        })
                    }
                    msg = "Promoted ${toMove.size} students."
                }
            }
            if (msg.isNotBlank()) Cell(msg, color = Theme.GOOD)
        }
        CardBox {
            Text("End-of-year rollover", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val year = d.academicYears.firstOrNull { it.currentTermId != null } ?: d.academicYears.lastOrNull()
            if (year == null) {
                Text("Create an academic year first (Calendar screen).", color = Theme.MUTED, fontSize = 14.sp)
            } else {
                Text("Creates academic year ${(year.year.toIntOrNull() ?: 2026) + 1} with 3 open terms, sets Term 1 as current, and closes this year's terms. Classes, students, grading schemes and the comment bank all carry over. Fee structures must be re-entered for the new terms.", color = Theme.MUTED, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                if (!rolloverConfirm) {
                    Btn("Start rollover") { rolloverConfirm = true }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn("Confirm — create year ${(year.year.toIntOrNull() ?: 2026) + 1}") {
                            val ny = (year.year.toIntOrNull() ?: 2026) + 1
                            val terms = (1..3).map { n -> Term(id = "term-$ny-$n", yearId = "ay-$ny", number = n, status = "OPEN") }
                            state.repo.mutate("YEAR_ROLLOVER", "AcademicYear", new = "ay-$ny") { dd ->
                                val closed = dd.academicYears.map { y ->
                                    if (y.id == year.id) y.copy(terms = y.terms.map { it.copy(status = "CLOSED") }, currentTermId = null)
                                    else y
                                }
                                dd.copy(academicYears = closed + AcademicYear(id = "ay-$ny", year = ny.toString(), terms = terms, currentTermId = terms.first().id))
                            }
                            rolloverConfirm = false
                            msg = "Academic year $ny created — Term 1 is now current."
                        }
                        Btn("Cancel", primary = false) { rolloverConfirm = false }
                    }
                }
                if (msg.isNotBlank()) Cell(msg, color = Theme.GOOD)
            }
        }
    }
}

private fun csvCell(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

// ─────────────────────────────────────────────────────────────────────────────
// Timetable — admin builds the weekly timetable; teachers see it in the app
// ─────────────────────────────────────────────────────────────────────────────

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TimetableScreen(state: AppState) {
    val d = state.data
    var day by remember { mutableStateOf(java.time.LocalDate.now().dayOfWeek.value.coerceAtMost(6)) }
    var pStart by remember { mutableStateOf("08:00") }
    var pEnd by remember { mutableStateOf("09:00") }
    var pClass by remember { mutableStateOf("") }
    var pSubject by remember { mutableStateOf("") }
    var pTeacher by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var bulkText by remember { mutableStateOf("") }
    var codeText by remember { mutableStateOf("") }
    var bulkMsg by remember { mutableStateOf("") }
    var sheetMsg by remember { mutableStateOf("") }

    val cls = d.classes.firstOrNull { it.id == pClass }
    val dayPeriods = d.timetable.filter { it.day == day }.sortedWith(compareBy({ it.start }, { it.end }))
    val perDay = (1..6).map { dd -> d.timetable.count { it.day == dd } }
    val subjectsForClass = d.subjects.filter { it.active && (cls == null || it.level == cls.level) }

    ScreenTitle("Timetable", "Build the weekly timetable — teachers see their periods in the phone app after pulling school setup")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DAY_LABELS.forEachIndexed { i, lbl ->
                    val dv = i + 1
                    val on = day == dv
                    Column(
                        Modifier.background(if (on) Theme.ACCENT else Theme.ACCENT_SOFT, RoundedCornerShape(12.dp))
                            .clickable { day = dv }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(lbl, color = if (on) Color.White else Theme.ACCENT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${perDay[i]} periods", color = if (on) Color.White else Theme.MUTED, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (dayPeriods.isEmpty()) {
                Column(Modifier.fillMaxWidth().background(Theme.ACCENT_SOFT.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No periods yet for ${DAY_LABELS[day - 1]}", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Add the first one below.", color = Theme.MUTED, fontSize = 14.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dayPeriods.forEach { p ->
                        val c = d.classes.firstOrNull { it.id == p.classId }
                        val sbj = p.subjectId?.let { d.subjects.firstOrNull { x -> x.id == it } }
                        val t = d.teachers.firstOrNull { it.id == p.teacherId }
                        Row(
                            Modifier.fillMaxWidth().background(Theme.CARD, RoundedCornerShape(12.dp))
                                .border(1.dp, Theme.LINE, RoundedCornerShape(12.dp)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(
                                Modifier.width(92.dp).background(Theme.ACCENT_SOFT, RoundedCornerShape(10.dp)).padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(p.start, color = Theme.ACCENT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(p.end, color = Theme.MUTED, fontSize = 12.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(sbj?.name ?: p.notes.ifBlank { "Free period" }, color = Theme.INK, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                val clsName = c?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" } ?: "?"
                                Text(if (t != null) "$clsName · ${t.name}" else clsName, color = Theme.MUTED, fontSize = 13.sp)
                            }
                            Text("Remove", color = Theme.RUST, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    state.repo.mutate("PERIOD_REMOVED", "TimetablePeriod", p.id) { dd ->
                                        dd.copy(timetable = dd.timetable.filterNot { it.id == p.id })
                                    }
                                    state.refresh()
                                })
                        }
                    }
                }
            }
        }

        CardBox {
            Text("Add period — ${DAY_LABELS[day - 1]}", color = Theme.TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (d.classes.isEmpty()) {
                Text("Add classes first.", color = Theme.MUTED, fontSize = 14.sp)
            } else {
                FieldLabel("Class")
                ClassPickerChips(d.classes.filter { it.active && it.level == state.level }, pClass) { pClass = it }
                Spacer(Modifier.height(8.dp))
                FieldLabel("Subject (optional)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    subjectsForClass.forEach { sbj ->
                        FilterChip(selected = pSubject == sbj.id, onClick = { pSubject = if (pSubject == sbj.id) "" else sbj.id },
                            label = { Text(sbj.name, fontSize = 13.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                FieldLabel("Teacher (optional)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    d.teachers.forEach { t ->
                        FilterChip(selected = pTeacher == t.id, onClick = { pTeacher = if (pTeacher == t.id) "" else t.id },
                            label = { Text(t.name, fontSize = 13.sp) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.width(110.dp)) { FieldLabel("Starts"); TextField(pStart, { pStart = it }, Modifier.fillMaxWidth(), "08:00") }
                    Column(Modifier.width(110.dp)) { FieldLabel("Ends"); TextField(pEnd, { pEnd = it }, Modifier.fillMaxWidth(), "09:00") }
                    Btn("Add period") {
                        if (pClass.isBlank()) { error = "Pick the class first."; return@Btn }
                        if (pStart.isBlank()) { error = "Type the start time (24h HH:mm)."; return@Btn }
                        val id = state.repo.nextId()
                        state.repo.mutate("PERIOD_ADDED", "TimetablePeriod", id) { dd ->
                            dd.copy(timetable = dd.timetable + TimetablePeriod(
                                id = id, day = day, start = pStart.trim(), end = pEnd.trim(),
                                classId = pClass, subjectId = pSubject.ifBlank { null },
                                teacherId = pTeacher, notes = ""))
                        }
                        error = ""; state.refresh()
                    }
                }
                if (error.isNotBlank()) { Spacer(Modifier.height(6.dp)); ErrorText(error) }
                Spacer(Modifier.height(10.dp))
                Text("Times use 24h HH:mm · ${d.timetable.size} period(s) across ${d.timetable.map { it.day }.distinct().size} day(s) this week.", color = Theme.MUTED, fontSize = 13.sp)
            }
        }

        // ── The printed sheet itself: import the picture once, teachers see it ──
        CardBox {
            Text("Timetable picture", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Import a photo of the printed timetable once. Every teacher's phone then shows the same sheet after they pull school setup — nothing to retype.", color = Theme.MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            val sheet = d.timetableSheet
            if (sheet == null || sheet.imageBase64.isBlank()) {
                Text("No picture imported yet.", color = Theme.MUTED, fontSize = 13.sp)
            } else {
                Text("Imported ${sheet.fileName} on ${sheet.importedAt.ifBlank { "—" }}", color = Theme.GOOD, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val bytes = remember(sheet.imageBase64) { TimetableSheetTool.decodeBase64(sheet.imageBase64) }
                if (bytes != null) {
                    val bmp = remember(bytes) {
                        try {
                            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                        } catch (_: Exception) { null }
                    }
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp,
                            contentDescription = "Imported timetable sheet",
                            modifier = Modifier.fillMaxWidth(0.85f).border(1.dp, Theme.LINE, RoundedCornerShape(10.dp))
                        )
                    } else Text("The picture is saved but could not be shown here.", color = Theme.WARN, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(if (sheet == null) "Import timetable picture…" else "Replace picture…") {
                    val chooser = javax.swing.JFileChooser()
                    chooser.dialogTitle = "Choose a photo of the printed timetable"
                    chooser.setFileFilter(javax.swing.filechooser.FileNameExtensionFilter("Images (JPG, PNG)", "jpg", "jpeg", "png", "webp"))
                    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                        val (imported, msg) = TimetableSheetTool.import(chooser.selectedFile)
                        if (imported != null) {
                            state.repo.mutate("TIMETABLE_PICTURE_IMPORTED", "TimetableSheet", imported.fileName) { dd ->
                                dd.copy(timetableSheet = imported)
                            }
                            state.refresh()
                        }
                        sheetMsg = msg
                    }
                }
                if (sheet != null) {
                    Btn("Remove picture", primary = false) {
                        state.repo.mutate("TIMETABLE_PICTURE_REMOVED", "TimetableSheet", sheet.fileName) { dd ->
                            dd.copy(timetableSheet = null)
                        }
                        sheetMsg = "Picture removed — teachers will stop seeing it after their next setup pull."
                        state.refresh()
                    }
                }
            }
            if (sheetMsg.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(sheetMsg, color = Theme.GOOD, fontSize = 13.sp) }
        }

        // ── Bulk entry: paste one whole day from the printed timetable ────────
        // A printed timetable is a grid: rows = time slots, columns = classes.
        // The admin copies those rows here exactly as they read on paper, and
        // every period for that day is created in one action.
        CardBox {
            Text("Paste a whole day", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("One line per time slot, columns in class order. Teachers can be a number code or a name.", color = Theme.MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            val bulkClasses = d.classes.filter { it.active && it.level == state.level }
                .sortedBy { it.name.filter { c -> c.isDigit() }.toIntOrNull() ?: 99 }
            Text("Columns: " + bulkClasses.mapIndexed { i, c -> "${i + 1}=${if (c.stream.isBlank()) c.name else c.name + " " + c.stream}" }
                .joinToString("  ·  "), color = Theme.ACCENT, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Example:  08:00-08:40 | MATH 8 | ENG 2 | GEO 3 | BIO 4     (blank or - = free period)", color = Theme.MUTED, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                bulkText, { bulkText = it; bulkMsg = "" },
                Modifier.fillMaxWidth().height(170.dp),
                placeholder = { Text("08:00-08:40 | MATH 8 | ENG 2 | GEO 3 | BIO 4\n08:40-09:20 | ENG 2 | MATH 8 | BIO 4 | GEO 3", fontSize = 12.sp) },
                textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                codeText, { codeText = it; bulkMsg = "" },
                Modifier.fillMaxWidth().height(110.dp),
                placeholder = { Text("Teacher codes (optional), one per line:\n2 = TR.WASUKIRA ASHIMU\n8 = TR.MUSINGUZI LODRICK", fontSize = 12.sp) },
                textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Add all periods for ${DAY_LABELS[day - 1]}") {
                    val r = TimetableBulk.importDay(state, day, bulkText, codeText, bulkClasses)
                    bulkMsg = r
                    if (r.startsWith("OK")) { bulkText = ""; state.refresh() }
                }
                Btn("Preview only", primary = false) {
                    bulkMsg = TimetableBulk.importDay(state, day, bulkText, codeText, bulkClasses, dryRun = true)
                }
            }
            if (bulkMsg.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(bulkMsg, color = if (bulkMsg.startsWith("OK")) Theme.GOOD else Theme.WARN, fontSize = 13.sp)
            }
        }

        // ── Teacher load: who teaches what, class by class ───────────────────
        val loads = d.timetable.filter { it.teacherId.isNotBlank() }
            .groupBy { it.teacherId }
            .map { (tid, ps) ->
                val t = d.teachers.firstOrNull { it.id == tid }
                val perClass = ps.groupBy { it.classId }.map { (cid, list) ->
                    val c = d.classes.firstOrNull { it.id == cid }
                    val subs = list.mapNotNull { it.subjectId }.distinct()
                        .mapNotNull { sid -> d.subjects.firstOrNull { it.id == sid }?.code?.ifBlank { null } ?: d.subjects.firstOrNull { it.id == sid }?.name }
                        .distinct()
                    "${c?.name ?: "?"}${if (subs.isEmpty()) "" else ": " + subs.joinToString("/")}"
                }.sorted()
                Triple(t?.name ?: tid, ps.size, perClass)
            }.sortedByDescending { it.second }
        if (loads.isNotEmpty()) {
            CardBox {
                Text("Teacher load from this timetable", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Derived from the periods above — this is who teaches which class and subject.", color = Theme.MUTED, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                loads.forEach { (name, count, perClass) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Text(name, color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(220.dp))
                        Text("$count period(s)", color = Theme.ACCENT, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                        Text(perClass.joinToString("   ·   "), color = Theme.MUTED, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
