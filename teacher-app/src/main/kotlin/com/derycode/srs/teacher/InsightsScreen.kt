package com.derycode.srs.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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

/**
 * Insights — the term at a glance for the classes this teacher actually teaches.
 * Class averages, how much of the markbook is captured, grade spread and the
 * strongest / weakest subject in each class. All computed on the phone.
 */
@Composable
fun InsightsScreen(state: TeacherState, onOpenClass: (String) -> Unit) {
    val d = state.data
    val termId = state.currentTermId
    val classes = state.myClasses

    // Everything here is derived, so it updates the moment marks are typed.
    data class ClassInsight(
        val cls: SchoolClass,
        val learners: Int,
        val captured: Int,
        val expected: Int,
        val average: Double,
        val bands: Map<String, Int>,
        val best: Pair<String, Double>?,
        val worst: Pair<String, Double>?
    )

    val insights = classes.map { cls ->
        val students = state.studentsIn(cls.id)
        val subjects = state.mySubjectsFor(cls.id)
        val subjectIds = subjects.map { it.id }.toSet()
        val marks = d.marks.filter { termId != null && it.termId == termId && it.studentId in students.map { s -> s.id }.toSet() && it.subjectId in subjectIds }
        val expected = students.size * subjects.size
        val captured = students.sumOf { st -> subjects.count { sub -> marks.any { it.studentId == st.id && it.subjectId == sub.id } } }

        // Per-student average first (so one subject with many components cannot skew it).
        val perStudent = students.map { st ->
            val pcts = subjects.mapNotNull { sub ->
                val sm = marks.filter { it.studentId == st.id && it.subjectId == sub.id }
                if (sm.isEmpty()) null else sm.sumOf { it.score } / sm.sumOf { it.maxScore } * 100.0
            }
            if (pcts.isEmpty()) null else pcts.average()
        }.filterNotNull()

        val perSubject = subjects.mapNotNull { sub ->
            val sm = marks.filter { it.subjectId == sub.id }
            if (sm.isEmpty()) null else sub.name to (sm.sumOf { it.score } / sm.sumOf { it.maxScore } * 100.0)
        }

        ClassInsight(
            cls = cls,
            learners = students.size,
            captured = captured,
            expected = expected,
            average = if (perStudent.isEmpty()) 0.0 else perStudent.average(),
            bands = perStudent.groupingBy { quickLetter(it) }.eachCount(),
            best = perSubject.maxByOrNull { it.second },
            worst = perSubject.minByOrNull { it.second }
        )
    }

    val allPcts = insights.filter { it.captured > 0 }.map { it.average }
    val overallAvg = if (allPcts.isEmpty()) 0.0 else allPcts.average()
    val totalCaptured = insights.sumOf { it.captured }
    val totalExpected = insights.sumOf { it.expected }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            CardBox {
                Text("Term insight", color = NAVY, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (termId == null) "No term open yet \u2014 ask the console to open a term."
                    else "Across ${classes.size} class${if (classes.size == 1) "" else "es"} you teach",
                    color = MUTED, fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val cards = listOf(
                        Triple("Average", if (allPcts.isEmpty()) "\u2014" else "${overallAvg.toInt()}%", BLUE),
                        Triple("Captured", if (totalExpected == 0) "\u2014" else "${totalCaptured * 100 / totalExpected}%", TEAL),
                        Triple("Learners", insights.sumOf { it.learners }.toString(), PURPLE)
                    )
                    cards.forEach { card ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(card.third.copy(alpha = 0.12f))
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(card.second, color = card.third, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text(card.first, color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (insights.isEmpty()) {
            item {
                CardBox {
                    Text("No classes assigned yet", color = NAVY, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Scan the school code or sync school data first, then your classes appear here.", color = MUTED, fontSize = 14.sp)
                }
            }
        }

        insights.forEach { ins ->
            item {
                CardBox {
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenClass(ins.cls.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(classLabel(ins.cls), color = NAVY, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("${ins.learners} learners \u00B7 ${ins.captured}/${ins.expected} marks in", color = MUTED, fontSize = 13.sp)
                        }
                        ScoreBadge(ins.average)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MUTED, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    // Grade spread
                    val total = ins.bands.values.sum()
                    if (total == 0) {
                        Text("No marks yet for this class.", color = MUTED, fontSize = 13.sp)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("A", "B+", "B", "C", "D", "F").forEach { band ->
                                val n = ins.bands[band] ?: 0
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .background(bandTone(band).copy(alpha = if (n == 0) 0.07f else 0.18f))
                                        .padding(vertical = 7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(band, color = bandTone(band), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(n.toString(), color = if (n == 0) MUTED else NAVY, fontSize = 14.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                    if (ins.best != null || ins.worst != null) {
                        Spacer(Modifier.height(10.dp))
                        ins.best?.let {
                            Text("Strongest: ${it.first} (${it.second.toInt()}%)", color = GREEN, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        ins.worst?.let {
                            if (ins.best == null || it.first != ins.best.first) {
                                Text("Needs support: ${it.first} (${it.second.toInt()}%)", color = RED, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

private fun bandTone(band: String): Color = when (band) {
    "A" -> GREEN
    "B+", "B" -> TEAL
    "C" -> ORANGE
    else -> RED
}

// ─────────────────────────────────────────────────────────────────────────────
// Results — the ranked class list, exactly what the report card will use
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ResultsScreen(state: TeacherState, onOpenStudent: (String) -> Unit) {
    val d = state.data
    val termId = state.currentTermId
    val classes = state.myClasses
    var classId by remember { mutableStateOf(classes.firstOrNull()?.id ?: "") }
    val cls = d.classes.firstOrNull { it.id == classId }

    val results: List<TermResult> = remember(classId, termId, state.data) {
        val scheme = cls?.let { c -> d.gradingSchemes.firstOrNull { it.level == c.level } ?: d.gradingSchemes.firstOrNull() }
        if (cls == null || termId == null || scheme == null) emptyList()
        else ResultEngine.classResults(d, cls.id, termId, scheme.id)
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            CardBox {
                Text("Ranked results", color = NAVY, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Computed from the marks on this phone, using the school's own grading scheme.", color = MUTED, fontSize = 13.sp)
                if (classes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classes.forEach { c -> FilterChipPill(classShort(c), selected = classId == c.id) { classId = c.id } }
                    }
                }
            }
        }

        if (results.isEmpty()) {
            item {
                CardBox {
                    Text(
                        when {
                            classes.isEmpty() -> "No classes assigned yet."
                            termId == null -> "No term is open. The console opens the term."
                            else -> "No results yet \u2014 enter marks for ${cls?.let { classLabel(it) } ?: "this class"} and they appear here ranked."
                        },
                        color = MUTED, fontSize = 14.sp
                    )
                }
            }
        } else {
            item {
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Position", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.width(54.dp))
                        Text("Learner", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
                        Text("Average", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.width(64.dp))
                        Text("Result", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.width(58.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    results.take(60).forEach { r ->
                        val st = d.students.firstOrNull { it.id == r.studentId }
                        val name = st?.let { "${it.firstName} ${it.lastName}".trim() } ?: r.studentId
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenStudent(r.studentId) }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${r.classPosition}", color = if (r.classPosition <= 3) BLUE else MUTED, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
                            Text(name, color = NAVY, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("${r.averagePercent.toInt()}%", color = NAVY, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                            Text(
                                r.division ?: r.uceResult ?: r.uaceResult ?: quickLetter(r.averagePercent),
                                color = quickBandColor(r.averagePercent), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(58.dp)
                            )
                        }
                    }
                    if (results.size > 60) {
                        Spacer(Modifier.height(6.dp))
                        Text("Showing the top 60 of ${results.size}.", color = MUTED, fontSize = 12.sp)
                    }
                }
            }
        }

        if (results.isNotEmpty()) {
            item {
                CardBox {
                    Text("Class summary", color = NAVY, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    val avg = results.map { it.averagePercent }.average()
                    val passed = results.count { it.averagePercent >= 50 }
                    Text("Average ${avg.toInt()}% \u00B7 ${passed} of ${results.size} at 50% and above", color = MUTED, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Top: ${d.students.firstOrNull { it.id == results.first().studentId }?.firstName ?: "\u2014"}", color = GREEN, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Calendar — term dates and school events, straight from the console
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CalendarScreen(state: TeacherState) {
    val d = state.data
    val year = d.academicYears.firstOrNull()
    val terms = year?.terms ?: emptyList()
    val events = d.calendarEvents.sortedBy { it.date }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            CardBox {
                Text("School calendar", color = NAVY, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(year?.year?.let { "Academic year $it" } ?: "No academic year yet", color = MUTED, fontSize = 13.sp)
            }
        }

        if (terms.isNotEmpty()) {
            item {
                CardBox {
                    Text("Terms", color = NAVY, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    terms.forEach { t ->
                        val current = year?.currentTermId == t.id
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                                    .background((if (current) BLUE else MUTED).copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) { Text("${t.number}", color = if (current) BLUE else MUTED, fontWeight = FontWeight.Black, fontSize = 15.sp) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Term ${t.number}" + if (current) "  \u00B7 current" else "", color = NAVY, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    listOf(
                                        t.startDate.ifBlank { "\u2014" },
                                        t.endDate.ifBlank { "\u2014" }
                                    ).joinToString("  \u2192  "),
                                    color = MUTED, fontSize = 13.sp
                                )
                            }
                            Text(t.status, color = if (t.status == "OPEN") GREEN else MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            CardBox {
                Text("Events", color = NAVY, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                if (events.isEmpty()) {
                    Text("No events yet. The console can add exams, holidays, meetings and deadlines.", color = MUTED, fontSize = 14.sp)
                } else {
                    events.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(eventTone(e.type).copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(eventIcon(e.type), contentDescription = null, tint = eventTone(e.type), modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.title, color = NAVY, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    e.date + if (e.notes.isBlank()) "" else " \u00B7 ${e.notes}",
                                    color = MUTED, fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

private fun eventTone(t: CalendarEventType): Color = when (t) {
    CalendarEventType.EXAM -> RED
    CalendarEventType.TERM_START, CalendarEventType.TERM_END -> BLUE
    CalendarEventType.HOLIDAY -> GREEN
    CalendarEventType.MEETING -> PURPLE
    CalendarEventType.DEADLINE -> ORANGE
    else -> TEAL
}

private fun eventIcon(t: CalendarEventType) = when (t) {
    CalendarEventType.EXAM -> Icons.Filled.Edit
    CalendarEventType.TERM_START, CalendarEventType.TERM_END -> Icons.Filled.Event
    CalendarEventType.HOLIDAY -> Icons.Filled.BeachAccess
    CalendarEventType.MEETING -> Icons.Filled.Groups
    CalendarEventType.DEADLINE -> Icons.Filled.Timer
    else -> Icons.Filled.Info
}
