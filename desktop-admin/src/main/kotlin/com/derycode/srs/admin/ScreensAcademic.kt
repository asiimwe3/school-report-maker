package com.derycode.srs.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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

    var classId by remember { mutableStateOf(d.classes.firstOrNull { it.active }?.id ?: "") }
    var subjectId by remember { mutableStateOf(d.subjects.firstOrNull { it.active }?.id ?: "") }
    val edits = remember { mutableStateMapOf<String, String>() }
    var error by remember { mutableStateOf("") }

    val cls = d.classes.firstOrNull { it.id == classId }
    val levelComponents = d.components.filter { term != null && cls != null && it.levels.contains(cls.level) && it.active }
    val yearFor = year

    ScreenTitle("Marks Grid", "Keyboard-first: type a value, press Enter, it saves. Special: ABS / EXEMPT.")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (term != null) "Term ${term.number} (${year?.year})" else "Create an academic year first", color = Theme.MUTED, fontSize = 12.sp)
            if (d.classes.isEmpty() || d.subjects.isEmpty()) {
                Text("Add classes and subjects first.", color = Color(0xFFF5A623), fontSize = 12.sp)
            }
        }
        if (d.classes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.width(200.dp)) {
                    FieldLabel("Class")
                    d.classes.filter { it.active }.forEach { c ->
                        Text((if (classId == c.id) "● " else "○ ") + if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}",
                            color = if (classId == c.id) Theme.ACCENT else Theme.TEXT, fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().clickable { classId = c.id }.padding(vertical = 3.dp))
                    }
                }
                Column(Modifier.width(200.dp)) {
                    FieldLabel("Subject")
                    cls?.let { c ->
                        val subjects = if (c.combinationId != null) {
                            val comb = d.combinations.firstOrNull { it.id == c.combinationId }
                            d.subjects.filter { s -> (comb?.principalSubjectIds ?: emptyList()).contains(s.id) || (comb?.subsidiarySubjectIds ?: emptyList()).contains(s.id) }
                        } else d.subjects.filter { it.level == c.level && it.active }
                        subjects.forEach { s ->
                            Text((if (subjectId == s.id) "● " else "○ ") + s.name,
                                color = if (subjectId == s.id) Theme.ACCENT else Theme.TEXT, fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().clickable { subjectId = s.id }.padding(vertical = 3.dp))
                        }
                    }
                }
                Column {
                    Spacer(Modifier.height(20.dp))
                    Text("Assessment columns for this level:", color = Theme.MUTED, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        levelComponents.forEach { c -> AssistChip(onClick = { }, label = { Text(c.code, fontSize = 10.sp) }) }
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
                    }) { Text("Submit sheet for review →", color = Theme.GOOD, fontSize = 12.sp) }
                }
            }
        }

        if (cls != null && subjectId.isNotBlank() && term != null) {
            val students = d.enrollments.filter { it.classId == cls.id && (yearFor == null || it.academicYearId == yearFor.id) }
                .mapNotNull { e -> d.students.firstOrNull { s -> s.id == e.studentId && s.status == StudentStatus.ACTIVE } }

            CardBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(2f)) { HeaderCell("Student") }
                    levelComponents.forEach { Box(Modifier.weight(1f)) { HeaderCell(it.code) } }
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.height(380.dp)) {
                    items(students) { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(2f)) { Cell(s.fullName, bold = true) }
                            levelComponents.forEach { comp ->
                                val markKey = "${s.id}|$subjectId|${term.id}|${comp.id}"
                                val existing = d.marks.firstOrNull { "${it.studentId}|${it.subjectId}|${it.termId}|${it.componentId}" == markKey }
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
                                    placeholder = { Text("—", color = Color(0xFF3A4763), fontSize = 11.sp) }
                                )
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
                FilterChip(selected = selectedId == s.id, onClick = { selectedId = s.id }, label = { Text("${s.name} (v${s.version})", fontSize = 11.sp) })
            }
        }

        scheme?.let { s ->
            CardBox {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(levelLabel(s.level), color = Theme.MUTED, fontSize = 11.sp)
                    Text("Rule: ${s.aggregateRule}", color = Theme.MUTED, fontSize = 11.sp)
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
                    Text("Existing approved reports are not affected (grade history).", color = Theme.MUTED, fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("Preview: 50% → grade ${previewGrade(s, 50.0)} · 79.9% → ${previewGrade(s, 79.9)} · 90% → ${previewGrade(s, 90.0)}", color = Theme.GOOD, fontSize = 12.sp)
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
    var classId by remember { mutableStateOf(d.classes.firstOrNull { it.active }?.id ?: "") }
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Class:", color = Theme.MUTED, fontSize = 12.sp)
            d.classes.filter { it.active }.forEach { c ->
                FilterChip(selected = classId == c.id, onClick = { classId = c.id }, label = { Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 11.sp) })
            }
        }
        if (cls == null || term == null) {
            Text("Create an academic year with terms and add classes first.", color = Theme.MUTED, fontSize = 12.sp)
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
                    Text("Locking freezes the scheme version used on reports (reproducibility).", color = Theme.MUTED, fontSize = 11.sp)
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
    var classId by remember { mutableStateOf(d.classes.firstOrNull { it.active }?.id ?: "") }
    var layout by remember { mutableStateOf(TemplateLayout.CLASSIC) }
    val year = d.academicYears.firstOrNull { it.currentTermId != null } ?: d.academicYears.firstOrNull()
    val term = year?.terms?.firstOrNull { it.id == year.currentTermId } ?: year?.terms?.firstOrNull()
    val cls = d.classes.firstOrNull { it.id == classId }
    val scheme = d.gradingSchemes.firstOrNull { it.level == cls?.level } ?: d.gradingSchemes.firstOrNull()

    ScreenTitle("Reports", "Print-ready HTML report cards — open in any browser, print to PDF.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Class:", color = Theme.MUTED, fontSize = 12.sp)
            d.classes.filter { it.active }.forEach { c ->
                FilterChip(selected = classId == c.id, onClick = { classId = c.id }, label = { Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 11.sp) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Design:", color = Theme.MUTED, fontSize = 12.sp)
            TemplateLayout.entries.forEach { l ->
                FilterChip(selected = layout == l, onClick = { layout = l }, label = { Text(l.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) })
            }
        }
        if (cls == null || term == null || scheme == null) {
            Text("Set up classes and an academic year with terms first.", color = Theme.MUTED, fontSize = 12.sp)
        } else {
            val results = ResultEngine.classResults(d, classId, term.id, scheme.id)
            if (results.isEmpty()) {
                Text("No students enrolled in this class for ${year?.year}.", color = Theme.MUTED, fontSize = 12.sp)
            } else {
                CardBox {
                    Text("Batch — whole class (${results.size} students)", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Each student gets one A4 card: school header, marks, grades, aggregate/division, comments, signature lines, grading key.", color = Theme.MUTED, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn("Generate Word documents (.docx)") {
                            val outDir = dataDir.resolve("reports")
                            Files.createDirectories(outDir)
                            val f = outDir.resolve("class-${cls.name.replace(" ", "")}-${term.number}-reports.docx")
                            DocxReport.writeClassReport(f, d, results)
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
                            try { Desktop.getDesktop().open(f.toFile()) } catch (_: Exception) { }
                        }
                        Btn("Print merit list (Word)", primary = false) {
                            val outDir = dataDir.resolve("reports")
                            Files.createDirectories(outDir)
                            val label = if (cls.stream.isBlank()) cls.name else "${cls.name} ${cls.stream}"
                            val f = outDir.resolve("merit-${label.replace(" ", "")}-term${term.number}.docx")
                            DocxReport.writeMeritList(f, d, results, label, "Term ${term.number} ${year?.year}")
                            try { Desktop.getDesktop().open(f.toFile()) } catch (_: Exception) { }
                        }
                        Btn("Generate single (first student)", primary = false) {
                            val outDir = dataDir.resolve("reports")
                            Files.createDirectories(outDir)
                            val f = outDir.resolve("student-${results.first().studentId}-report.docx")
                            DocxReport.writeStudentReport(f, d, results.first(), layout)
                            try { Desktop.getDesktop().open(f.toFile()) } catch (_: Exception) { }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                CardBox {
                    Text("Recently generated (frozen scheme versions)", color = Theme.TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    d.reports.takeLast(8).reversed().forEach { r ->
                        val s = d.students.firstOrNull { it.id == r.studentId }
                        Text("• ${s?.fullName ?: r.studentId} — ${r.resultSummary} — scheme v${r.schemeVersion}", color = Theme.MUTED, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    if (d.reports.isEmpty()) Text("None yet.", color = Theme.MUTED, fontSize = 11.sp)
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
            Text("Backup", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
            Text("Restore", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("A safety backup of current data is taken automatically before any restore.", color = Theme.MUTED, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            val backups = listBackups(backupsDir)
            if (backups.isEmpty()) Text("No backups yet.", color = Theme.MUTED, fontSize = 12.sp)
            else LazyColumn(Modifier.height(150.dp)) {
                items(backups.takeLast(10).reversed()) { b ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Cell(b.fileName.toString(), color = Theme.MUTED)
                        TextButton(onClick = {
                                    message = if (state.repo.restore(b)) "Restored from ${b.fileName}" else "Restore failed"
                                    state.refresh()
                                }) { Text("Restore", color = Theme.WARN, fontSize = 11.sp) }
                    }
                }
            }
        }
        CardBox {
            Text("Export school setup for teacher phones", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Each school runs its own admin console and exports its own bundle — teachers connect only to their own school.", color = Theme.MUTED, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text("1. Tap Export school setup — creates school-data-from-admin.json (classes, students, subjects, schemes — no marks)\n2. Copy it to each teacher's phone via USB or Bluetooth\n3. On the teacher's phone: More → Sync & Export → Import\n4. Teacher picks their name and enters marks for their classes\n5. Teacher taps Export marks bundle and copies it back here\n6. Import bundle below — newest-wins merge, everything logged", color = Theme.MUTED, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text("The teacher app now shows which school it is connected to, and refuses to silently mix two different schools' data — if a teacher joins another school, it asks before replacing.", color = Theme.GOOD, fontSize = 11.sp)
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
                    try { java.awt.Desktop.getDesktop().open(dir) } catch (_: Exception) { }
                })
            }
        }
        CardBox {
            Text("Teacher bundle (USB / Bluetooth)", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                                    attendance = (dd.attendance.filter { a -> report.mergedAttendance.none { it.id == a.id } } + report.mergedAttendance))
                            }
                        }
                        message = report.message
                        state.refresh()
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Bundles are versioned and validated — a corrupt or old bundle can never corrupt newer data.", color = Theme.MUTED, fontSize = 11.sp)
        }
        CardBox {
            Text("Audit trail (latest 12)", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            d.auditLog.takeLast(12).reversed().forEach { a ->
                Text("• ${a.action} ${a.entity}${if (a.entityId.isNotBlank()) "(${a.entityId})" else ""} ${if (a.newValue.isNotBlank()) "→ ${a.newValue.take(40)}" else ""}",
                    color = Theme.MUTED, fontSize = 10.5.sp, modifier = Modifier.padding(vertical = 1.dp))
            }
            if (d.auditLog.isEmpty()) Text("Nothing yet — actions will appear here.", color = Theme.MUTED, fontSize = 11.sp)
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
    var classId by remember { mutableStateOf(d.classes.firstOrNull { it.active }?.id ?: "") }
    val year = d.academicYears.firstOrNull { it.currentTermId != null } ?: d.academicYears.firstOrNull()
    val term = year?.terms?.firstOrNull { it.id == year.currentTermId } ?: year?.terms?.firstOrNull()
    val cls = d.classes.firstOrNull { it.id == classId }
    val scheme = d.gradingSchemes.firstOrNull { it.level == cls?.level } ?: d.gradingSchemes.firstOrNull()
    val results = remember(state.refreshTick, classId, term?.id, scheme?.id) {
        if (cls != null && term != null && scheme != null) ResultEngine.classResults(d, classId, term.id, scheme.id) else emptyList()
    }

    ScreenTitle("Analytics", "Live performance insights — averages, rankings, subject trends, gender split.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            d.classes.filter { it.active }.forEach { c ->
                FilterChip(selected = classId == c.id, onClick = { classId = c.id },
                    label = { Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 11.sp) })
            }
        }
        if (results.isEmpty()) {
            CardBox { Text(if (cls == null || term == null) "Set up classes and an academic year first." else "No marks entered for this class/term yet — enter marks in the Marks Grid to see analytics.", color = Theme.MUTED, fontSize = 12.sp) }
        } else {
            val avg = results.map { it.averagePercent }.average()
            val pass = results.count { it.averagePercent >= 50 }
            val boys = results.count { r -> d.students.firstOrNull { it.id == r.studentId }?.sex == "M" }
            val girls = results.size - boys
            val ranked = results.filter { it.classPosition > 0 }.sortedBy { it.classPosition }

            CardBox {
                Text("Class snapshot — ${cls?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" }}, Term ${term?.number} ${year?.year}",
                    color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) { Text("Class average", color = Theme.MUTED, fontSize = 11.sp); Text("${"%.1f".format(avg)}%", color = Theme.ACCENT, fontSize = 22.sp, fontWeight = FontWeight.Black) }
                    Column(Modifier.weight(1f)) { Text("Passing (≥50%)", color = Theme.MUTED, fontSize = 11.sp); Text("$pass / ${results.size}", color = Theme.GOOD, fontSize = 22.sp, fontWeight = FontWeight.Black) }
                    Column(Modifier.weight(1f)) { Text("Best student", color = Theme.MUTED, fontSize = 11.sp); Text(ranked.firstOrNull()?.let { r -> d.students.firstOrNull { it.id == r.studentId }?.fullName ?: "—" } ?: "—", color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
            }

            CardBox {
                Text("Subject performance — class average % per subject", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val subjectAvgs = d.subjects.mapNotNull { sub ->
                    val marks = results.flatMap { r -> r.subjectResults.filter { it.subjectId == sub.id }.map { it.percentage } }
                    if (marks.isEmpty()) null else sub.name to marks.average()
                }
                subjectAvgs.sortedByDescending { it.second }.forEach { (name, pct) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name.take(18), color = Theme.MUTED, fontSize = 11.sp, modifier = Modifier.width(120.dp))
                        Box(Modifier.weight(1f).height(14.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRoundRect(color = Color(0xFF243356), cornerRadius = CornerRadius(7.dp.toPx()))
                                drawRoundRect(color = Theme.ACCENT, cornerRadius = CornerRadius(7.dp.toPx()),
                                    size = Size(size.width * (pct / 100.0).toFloat(), size.height))
                            }
                        }
                        Text("${pct.toInt()}%", color = if (pct >= 50) Theme.GOOD else Theme.WARN, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                    }
                }
                if (subjectAvgs.isEmpty()) Text("No subject marks yet.", color = Theme.MUTED, fontSize = 11.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { CardBox {
                    Text("Top 5 students", color = Theme.GOOD, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    ranked.take(5).forEach { r ->
                        val s = d.students.firstOrNull { it.id == r.studentId }
                        Text("${r.classPosition}. ${s?.fullName ?: "—"} — ${"%.1f".format(r.averagePercent)}%", color = Theme.TEXT, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    if (ranked.isEmpty()) Text("No ranked students yet.", color = Theme.MUTED, fontSize = 11.sp)
                } }
                Box(Modifier.weight(1f)) { CardBox {
                    Text("Needs attention — bottom 5", color = Theme.WARN, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    ranked.takeLast(5).reversed().forEach { r ->
                        val s = d.students.firstOrNull { it.id == r.studentId }
                        Text("${s?.fullName ?: "—"} — ${"%.1f".format(r.averagePercent)}%", color = Theme.TEXT, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    if (ranked.isEmpty()) Text("No ranked students yet.", color = Theme.MUTED, fontSize = 11.sp)
                } }
            }

            CardBox {
                Text("Gender split", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Boys $boys", color = Theme.MUTED, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                    Box(Modifier.weight(1f).height(14.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRoundRect(color = Color(0xFF243356), cornerRadius = CornerRadius(7.dp.toPx()))
                            drawRoundRect(color = Theme.ACCENT, cornerRadius = CornerRadius(7.dp.toPx()),
                                size = Size(size.width * (if (results.isEmpty()) 0f else boys.toFloat() / results.size), size.height))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Girls $girls", color = Theme.MUTED, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                    Box(Modifier.weight(1f).height(14.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRoundRect(color = Color(0xFF243356), cornerRadius = CornerRadius(7.dp.toPx()))
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
    var classId by remember { mutableStateOf(d.classes.firstOrNull { it.active }?.id ?: "") }
    var msg by remember { mutableStateOf("") }
    val klass = d.classes.firstOrNull { it.id == classId }
    val students = d.enrollments.filter { it.classId == classId }
        .sortedBy { it.studentId }.map { e -> d.students.firstOrNull { it.id == e.studentId } }
        .filterNotNull().distinctBy { it.id }
    val recs = d.attendance.filter { it.classId == classId }

    ScreenTitle("Attendance", "Daily register from teacher phones — present / absent / late, per class.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            d.classes.filter { it.active }.forEach { c ->
                FilterChip(selected = classId == c.id, onClick = { classId = c.id },
                    label = { Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 11.sp) })
            }
        }
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
                    try { java.awt.Desktop.getDesktop().open(f.toFile().parentFile) } catch (_: Exception) { }
                }
            }
            if (msg.isNotBlank()) Cell(msg, color = Theme.GOOD)
            Spacer(Modifier.height(6.dp))
            if (recs.isEmpty()) Text("No attendance recorded for this class yet — teachers mark it daily on their phones (More → Attendance) and it syncs in their bundle.",
                color = Theme.MUTED, fontSize = 11.sp)
        }
        CardBox {
            Text("Per-student totals", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (students.isEmpty()) Text("No students in this class.", color = Theme.MUTED, fontSize = 12.sp)
            LazyColumn(Modifier.height(320.dp)) {
                items(students) { s ->
                    val a = recs.filter { it.studentId == s.id }
                    val pct = if (a.isEmpty()) 0.0 else a.count { it.status == "PRESENT" } * 100.0 / a.size
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.fullName, color = Theme.TEXT, fontSize = 12.sp, modifier = Modifier.weight(1.4f))
                        Text("P ${a.count { it.status == "PRESENT" }}", color = Theme.GOOD, fontSize = 12.sp, modifier = Modifier.weight(0.5f))
                        Text("L ${a.count { it.status == "LATE" }}", color = Theme.WARN, fontSize = 12.sp, modifier = Modifier.weight(0.5f))
                        Text("A ${a.count { it.status == "ABSENT" }}", color = Color(0xFFEF4444), fontSize = 12.sp, modifier = Modifier.weight(0.5f))
                        Text(if (a.isEmpty()) "—" else "${pct.toInt()}%", color = if (pct >= 75) Theme.GOOD else Theme.WARN, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
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
                FilterChip(selected = category == id, onClick = { category = id }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        CardBox {
            Text("Add to ${if (category == "TEACHER") "class teacher" else "headteacher"} bank", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
            Text("${d.commentTemplates.count { it.category == category }} comments in this bank", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.height(360.dp)) {
                items(d.commentTemplates.filter { it.category == category }) { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.text, color = Theme.TEXT, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            state.repo.mutate("COMMENT_TEMPLATE_REMOVED", "CommentTemplate", old = c.id) { dd ->
                                dd.copy(commentTemplates = dd.commentTemplates.filter { it.id != c.id })
                            }
                        }) { Text("Delete", color = Theme.WARN, fontSize = 11.sp) }
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
    val active = d.classes.filter { it.active }
    var fromId by remember { mutableStateOf(active.firstOrNull()?.id ?: "") }
    var toId by remember { mutableStateOf(active.lastOrNull()?.id ?: "") }
    var msg by remember { mutableStateOf("") }
    var rolloverConfirm by remember { mutableStateOf(false) }

    val latestEnrollment = { studentId: String -> d.enrollments.lastOrNull { it.studentId == studentId } }
    val toMove = d.students.filter { latestEnrollment(it.id)?.classId == fromId }

    ScreenTitle("Promotion & Rollover", "Move students to the next class and start the next academic year — all offline.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Text("Promote students to the next class", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Leavers (P7 / S4 / S6 / U6) are NOT moved — graduate them by leaving the class as-is.", color = Theme.MUTED, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Text("From:", color = Theme.MUTED, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                active.forEach { c -> FilterChip(selected = fromId == c.id, onClick = { fromId = c.id },
                    label = { Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 10.sp) }) }
            }
            Spacer(Modifier.height(6.dp))
            Text("To:", color = Theme.MUTED, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                active.forEach { c -> FilterChip(selected = toId == c.id, onClick = { toId = c.id },
                    label = { Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 10.sp) }) }
            }
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
            Text("End-of-year rollover", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val year = d.academicYears.firstOrNull { it.currentTermId != null } ?: d.academicYears.lastOrNull()
            if (year == null) {
                Text("Create an academic year first (Calendar screen).", color = Theme.MUTED, fontSize = 12.sp)
            } else {
                Text("Creates academic year ${(year.year.toIntOrNull() ?: 2026) + 1} with 3 open terms, sets Term 1 as current, and closes this year's terms. Classes, students, grading schemes and the comment bank all carry over. Fee structures must be re-entered for the new terms.", color = Theme.MUTED, fontSize = 11.sp)
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
