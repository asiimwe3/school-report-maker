package com.derycode.srs.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.results.ResultEngine
import com.derycode.srs.core.model.*

// ─────────────────────────────────────────────────────────────────────────────
// Report preview — the same card the console prints, on the teacher's phone.
//
// A teacher does not need to open a laptop to know what a learner's report will
// say. This reads the marks already on this phone and lays them out exactly like
// the printed A4 card: header, subject table with marks/grades/remarks, totals,
// position, division, class teacher comment and signature lines.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReportPreviewScreen(state: TeacherState) {
    val d = state.data
    val termId = state.currentTermId
    val classes = state.myClasses
    var classId by remember { mutableStateOf(classes.firstOrNull()?.id ?: "") }
    var studentIndex by remember(classId) { mutableStateOf(0) }
    var showAll by remember { mutableStateOf(false) }

    val cls = d.classes.firstOrNull { it.id == classId }
    val year = d.academicYears.firstOrNull { y -> y.terms.any { it.id == termId } }
    val term = year?.terms?.firstOrNull { it.id == termId }

    val results: List<TermResult> = remember(classId, termId, state.data) {
        val scheme = cls?.let { c -> d.gradingSchemes.firstOrNull { it.level == c.level } ?: d.gradingSchemes.firstOrNull() }
        if (cls == null || termId == null || scheme == null) emptyList()
        else ResultEngine.classResults(d, cls.id, termId, scheme.id)
    }
    val scheme = cls?.let { c -> d.gradingSchemes.firstOrNull { it.level == c.level } ?: d.gradingSchemes.firstOrNull() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)
    ) {
        item {
            CardBox {
                Text("Report preview", color = NAVY, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "The card exactly as it prints, built from the marks on this phone.",
                    color = MUTED, fontSize = 13.sp
                )
                if (classes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classes.forEach { c ->
                            FilterChipPill(classShort(c), selected = classId == c.id) {
                                classId = c.id; studentIndex = 0
                            }
                        }
                    }
                }
                if (results.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChipPill("One learner", selected = !showAll) { showAll = false }
                        FilterChipPill("Whole class", selected = showAll) { showAll = true }
                    }
                }
            }
        }

        if (results.isEmpty() || cls == null) {
            item {
                CardBox {
                    Text(
                        when {
                            classes.isEmpty() -> "No classes are assigned to you yet."
                            termId == null -> "No term is open. The console opens the term."
                            else -> "No marks yet for ${cls?.let { classLabel(it) } ?: "this class"} \u2014 enter marks and the cards appear here."
                        },
                        color = MUTED, fontSize = 14.sp
                    )
                }
            }
        } else if (showAll) {
            items(results.size) { i ->
                val r = results[i]
                val st = d.students.firstOrNull { it.id == r.studentId }
                ReportCard(
                    d = d, cls = cls, term = term, year = year,
                    student = st, result = r,
                    comment = classComment(d, r.studentId, r.termId),
                    scheme = scheme,
                    cardNumber = i + 1, cardTotal = results.size
                )
            }
        } else {
            item {
                val idx = studentIndex.coerceIn(0, results.size - 1)
                val r = results[idx]
                val st = d.students.firstOrNull { it.id == r.studentId }
                CardBox(padding = 14) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                st?.fullName ?: r.studentId,
                                color = NAVY, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1
                            )
                            Text("${idx + 1} of ${results.size}", color = MUTED, fontSize = 12.sp)
                        }
                        FilterChipPill("<", selected = false) { studentIndex = (idx - 1 + results.size) % results.size }
                        Spacer(Modifier.width(6.dp))
                        FilterChipPill(">", selected = false) { studentIndex = (idx + 1) % results.size }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ReportCard(
                    d = d, cls = cls, term = term, year = year,
                    student = st, result = r,
                    comment = classComment(d, r.studentId, r.termId),
                    scheme = scheme,
                    cardNumber = idx + 1, cardTotal = results.size
                )
            }
        }
    }
}

/** The class teacher's own comment for this learner, if one was written. */
private fun classComment(d: SchoolData, studentId: String, termId: String): String =
    d.comments.lastOrNull { it.studentId == studentId && it.termId == termId && it.subjectId == null }?.text.orEmpty()

@Composable
private fun ReportCard(
    d: SchoolData,
    cls: SchoolClass,
    term: Term?,
    year: AcademicYear?,
    student: Student?,
    result: TermResult,
    comment: String,
    scheme: GradingScheme?,
    cardNumber: Int,
    cardTotal: Int
) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFFE2E2E2), RoundedCornerShape(6.dp))
            .padding(16.dp)
    ) {
        // ── School header ──
        Text(
            d.school.name.uppercase(),
            color = Color(0xFF16213E), fontSize = 17.sp, fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        if (d.school.address.isNotBlank() || d.school.district.isNotBlank()) {
            Text(
                listOf(d.school.address, d.school.district).filter { it.isNotBlank() }.joinToString(", "),
                color = Color(0xFF666666), fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF16213E)))
        Spacer(Modifier.height(6.dp))
        Text(
            "TERMINAL REPORT \u2014 " + (term?.let { "TERM ${it.number}" } ?: "TERM") + " " + (year?.year ?: ""),
            color = Color(0xFF16213E), fontSize = 12.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        // ── Learner ──
        InfoRow("Name", student?.fullName ?: result.studentId)
        InfoRow("Admission no", student?.admissionNo ?: "\u2014")
        InfoRow("Class", classLabel(cls))
        InfoRow(
            if (result.uceResult != null || result.uacePoints != null) "Result" else "Division",
            result.division ?: result.uceResult ?: result.uaceResult ?: "\u2014"
        )
        Spacer(Modifier.height(10.dp))

        // ── Subjects ──
        Row(Modifier.fillMaxWidth().background(Color(0xFFF2F4F8)).padding(vertical = 6.dp, horizontal = 6.dp)) {
            Head("SUBJECT", 1f)
            Head("MARKS", 48.dp)
            Head("GRADE", 48.dp)
            Head("REMARK", 78.dp)
            Head("POS", 34.dp)
        }
        result.subjectResults.forEach { sr ->
            val name = d.subjects.firstOrNull { it.id == sr.subjectId }?.name ?: sr.subjectId
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Color(0xFF222222), fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text("${sr.total.toInt()}", color = Color(0xFF222222), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(48.dp))
                Text(sr.grade, color = Color(0xFF16213E), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                Text(sr.remark, color = Color(0xFF666666), fontSize = 11.sp, maxLines = 1, modifier = Modifier.width(78.dp))
                Text(if (sr.positionInSubject > 0) "${sr.positionInSubject}" else "\u2014", color = Color(0xFF666666), fontSize = 12.sp, modifier = Modifier.width(34.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
        }
        Spacer(Modifier.height(8.dp))

        // ── Totals ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("AVERAGE", "${"%.1f".format(result.averagePercent)}%", Modifier.weight(1f))
            MiniStat("POSITION", if (result.classPosition > 0) "${result.classPosition}" else "\u2014", Modifier.weight(1f))
            MiniStat(
                if (result.uacePoints != null) "POINTS" else "AGGREGATE",
                result.uacePoints?.toString() ?: result.aggregate?.toString() ?: "\u2014",
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))

        // ── Comment ──
        Text("CLASS TEACHER'S COMMENT", color = Color(0xFF16213E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(
            comment.ifBlank { "No comment written for this learner yet." },
            color = Color(0xFF333333), fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))

        // ── Signatures ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SignatureLine("Class teacher", Modifier.weight(1f))
            SignatureLine("Head teacher", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Card $cardNumber of $cardTotal \u00B7 ${d.school.name}",
            color = Color(0xFF999999), fontSize = 10.sp
        )
        if (scheme != null && scheme.boundaries.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Grading: " + scheme.boundaries.sortedByDescending { it.minScore }
                    .joinToString("  ") { "${it.minScore.toInt()}%+ ${it.grade}" },
                color = Color(0xFF999999), fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Color(0xFF777777), fontSize = 12.sp, modifier = Modifier.width(110.dp))
        Text(value, color = Color(0xFF16213E), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Head(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text, color = Color(0xFF16213E), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(width))
}

@Composable
private fun RowScope.Head(text: String, weight: Float) {
    Text(text, color = Color(0xFF16213E), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(weight))
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color(0xFFF7F8FB), RoundedCornerShape(6.dp)).padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Text(label, color = Color(0xFF777777), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color(0xFF16213E), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SignatureLine(label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFBBBBBB)))
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color(0xFF777777), fontSize = 10.sp)
    }
}
