package com.derycode.srs.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*

// ─────────────────────────────────────────────────────────────────────────────
// My Classes — search + filter chips + class cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ClassesListScreen(state: TeacherState, onOpenClass: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    val classes = state.myClasses
    val filters = listOf("All") + classes.map { classShort(it) }.distinct()
    val shown = classes.filter { c ->
        (filter == "All" || classShort(c) == filter) &&
            (query.isBlank() || classLabel(c).contains(query, ignoreCase = true))
    }
    val learners = classes.sumOf { state.studentsIn(it.id).size }

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("My classes", color = NAVY, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${classes.size} class${if (classes.size == 1) "" else "es"} \u00b7 $learners learner${if (learners == 1) "" else "s"}",
                    color = MUTED, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(GREEN.copy(alpha = 0.14f)).padding(horizontal = 11.dp, vertical = 6.dp)) {
                Text("Works offline", color = GREEN, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        SearchField(query, { query = it }, "Search your classes")
        Spacer(Modifier.height(10.dp))

        // The honest banner: this phone is showing the whole school because we could
        // not tie it to a staff record yet.
        if (!state.classesMatched && classes.isNotEmpty()) {
            UnmatchedBanner(onFind = { state.resolveMe() })
            Spacer(Modifier.height(10.dp))
        }

        if (filters.size > 2) {
            ChipRow {
                filters.forEach { f -> FilterChipPill(f, selected = filter == f) { filter = f } }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (shown.isEmpty()) {
            if (classes.isEmpty()) {
                Column(Modifier.fillMaxWidth()) {
                    EmptyHint("No classes in this school yet.\nImport the school setup from More \u2192 Sync.")
                }
            } else {
                EmptyHint("No class matches \u201c$query\u201d.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(shown) { c -> ClassCard(state, c) { onOpenClass(c.id) } }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
    }
}

/** Zeraki-style: a teacher must always be able to get back to their own classes. */
@Composable
private fun UnmatchedBanner(onFind: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ORANGE.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, ORANGE.copy(alpha = 0.35f)), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text("Showing every class in the school", color = NAVY, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(
            "This phone is not linked to your staff record yet, so you are seeing all classes. Tap below and the app will find you.",
            color = MUTED, fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Find my classes",
            color = BLUE, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onFind).padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun ClassCard(state: TeacherState, c: SchoolClass, onClick: () -> Unit) {
    val students = state.studentsIn(c.id)
    val subjects = state.mySubjectsFor(c.id)
    val color = when (c.level) { Level.PRIMARY -> TEAL; Level.A_LEVEL -> PURPLE; else -> BLUE }
    val total = students.size
    val entered = marksEntered(state, c.id, students.map { it.id }, subjects.map { it.id })
    val pct = if (total == 0) 0f else (entered.toFloat() / total).coerceIn(0f, 1f)

    Column(
        Modifier.fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = STROKE, spotColor = STROKE)
            .clip(RoundedCornerShape(16.dp))
            .background(CARD)
            .border(BorderStroke(1.dp, STROKE), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Class monogram, the thing teachers recognise first on a Zeraki card
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    classMonogram(c),
                    color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(classLabel(c), color = NAVY, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (total == 0) "No learners in this class yet" else "$total learner${if (total == 1) "" else "s"} \u00b7 ${subjects.size} subject${if (subjects.size == 1) "" else "s"}",
                    color = MUTED, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MUTED)
        }

        if (total > 0) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Marks this term", color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("$entered of $total", color = if (entered >= total) GREEN else ORANGE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(STROKE)) {
                Box(Modifier.fillMaxWidth(pct).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                    .background(if (entered >= total) GREEN else color))
            }
        }

        if (subjects.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                subjects.take(3).forEach { s -> SubjectPill(s.name) }
                if (subjects.size > 3) SubjectPill("+${subjects.size - 3}")
            }
        }

        if (total == 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Ask the head teacher for a fresh school setup (More \u2192 Sync) if learners are missing.",
                color = MUTED, fontSize = 13.sp
            )
        }
    }
}

/** "Senior 3" + "West" -> "S3W": short enough for a 48dp tile. */
internal fun classMonogram(c: SchoolClass): String {
    val words = c.name.trim().split(" ").filter { it.isNotBlank() }
    val head = words.take(2).joinToString("") { it.first().uppercase() }
    val number = words.lastOrNull { w -> w.any { it.isDigit() } }?.filter { it.isDigit() } ?: ""
    val base = if (number.isNotEmpty()) "${head.firstOrNull() ?: 'C'}$number" else head.take(2)
    val stream = c.stream.trim().firstOrNull()?.uppercase() ?: ""
    return (base + stream).take(4)
}

/** How many learners already have a mark this term in any subject the teacher handles. */
private fun marksEntered(state: TeacherState, classId: String, studentIds: List<String>, subjectIds: List<String>): Int {
    if (studentIds.isEmpty() || subjectIds.isEmpty()) return 0
    val termId = state.currentTermId
    val ids = studentIds.toSet()
    val subjects = subjectIds.toSet()
    return state.data.marks
        .filter { it.termId == termId && it.studentId in ids && it.subjectId in subjects }
        .map { it.studentId }
        .distinct()
        .size
}

@Composable
internal fun SubjectPill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(BLUE.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = BLUE, fontSize = 14.sp)
    }
}

@Composable
internal fun FilterChipPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(18.dp))
            .background(if (selected) BLUE else CARD_ALT)
            .border(BorderStroke(1.dp, if (selected) BLUE else STROKE), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else NAVY,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Row of FilterChipPills that scrolls sideways instead of squeezing labels.
 *  Long names ("Entrepreneurship", "End of Term") used to collapse into
 *  one-letter-per-line columns when several chips shared a fixed Row. */
@Composable
internal fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
internal fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CARD).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MUTED, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextFieldLine(value, onChange, placeholder)
    }
}

@Composable
private fun BasicTextFieldLine(value: String, onChange: (String) -> Unit, placeholder: String) {
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = NAVY, fontSize = 17.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(BLUE),
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = MUTED, fontSize = 17.sp)
            inner()
        }
    )
}

@Composable
internal fun EmptyHint(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(BLUE.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Inbox, contentDescription = null, tint = BLUE.copy(alpha = 0.7f), modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(text, color = MUTED, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Class Detail — tabs: Marks / Assignments / Resources
// ─────────────────────────────────────────────────────────────────────────────

private enum class ClassTab { STUDENTS, MARKS, REPORTS }

@Composable
fun ClassDetailScreen(
    state: TeacherState,
    classId: String,
    onEnterMarks: (String?) -> Unit,
    onViewStudents: () -> Unit,
    onOpenStudent: (String) -> Unit = {}
) {
    var studentQuery by remember { mutableStateOf("") }
    val d = state.data
    val cls = d.classes.firstOrNull { it.id == classId }
    val subjects = state.mySubjectsFor(classId)
    var subjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    var tab by remember { mutableStateOf(ClassTab.STUDENTS) }
    val students = state.studentsIn(classId)
    val termId = state.currentTermId

    // Most recently used component for this class+subject (else first component for the level)
    val relevantMarks = d.marks.filter { m -> m.subjectId == subjectId && m.termId == termId &&
        students.any { it.id == m.studentId } }
    val componentId = relevantMarks.maxByOrNull { it.updatedAt }?.componentId
        ?: d.components.firstOrNull { it.levels.contains(cls?.level ?: Level.O_LEVEL) && it.active }?.id ?: ""
    val component = d.components.firstOrNull { it.id == componentId }
    val entered = relevantMarks.map { it.studentId }.distinct().count { sid -> relevantMarks.any { it.studentId == sid && it.componentId == componentId } }
    val pending = (students.size - entered).coerceAtLeast(0)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (subjects.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(subjects) { s -> FilterChipPill(s.name, selected = subjectId == s.id) { subjectId = s.id } }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CARD).padding(4.dp)) {
                listOf(ClassTab.STUDENTS to "Students", ClassTab.MARKS to "Marks", ClassTab.REPORTS to "Reports").forEach { (t, label) ->
                    val selected = tab == t
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                            .background(if (selected) BLUE else Color.Transparent)
                            .clickable { tab = t }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(label, color = if (selected) Color.White else MUTED, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        when (tab) {
            ClassTab.STUDENTS -> {
                val shown = students.filter {
                    studentQuery.isBlank() ||
                        it.fullName.contains(studentQuery, true) ||
                        it.admissionNo.contains(studentQuery, true)
                }
                item { SearchField(studentQuery, { studentQuery = it }, "Search learners in this class") }
                if (students.isEmpty()) {
                    item {
                        EmptyHint(
                            "No learners in this class yet.\nThey arrive with the school setup \u2014 More \u2192 Sync, or ask the head teacher to send a fresh one."
                        )
                    }
                } else if (shown.isEmpty()) {
                    item { EmptyHint("No learner matches \u201c$studentQuery\u201d.") }
                } else {
                    items(shown) { st -> StudentRow(st) { onOpenStudent(st.id) } }
                }
                item {
                    TextButton(onClick = onViewStudents, modifier = Modifier.fillMaxWidth()) {
                        Text("Open the full marks view", color = BLUE, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            ClassTab.REPORTS -> {
                item {
                    CardBox {
                        Cell("Report cards", MUTED, true)
                        Spacer(Modifier.height(6.dp))
                        Cell(
                            "Your class list is what the head teacher needs: marks first, then the console builds and prints the cards. " +
                                "Send the marksheet as Word from Marks so nothing is retyped.",
                            MUTED
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            MiniStat(Modifier.weight(1f), students.size.toString(), "Learners", BLUE)
                            MiniStat(Modifier.weight(1f), entered.toString(), "Entered", GREEN)
                            MiniStat(Modifier.weight(1f), pending.toString(), "Pending", ORANGE)
                        }
                    }
                }
            }
            ClassTab.MARKS -> {
                item {
                    CardBox {
                        Cell("Recent Assessment", MUTED, true)
                        Spacer(Modifier.height(6.dp))
                        Text(component?.name ?: "No assessment yet", color = NAVY, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(todayLabel(), color = MUTED, fontSize = 15.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            MiniStat(Modifier.weight(1f), students.size.toString(), "Total Students", BLUE)
                            MiniStat(Modifier.weight(1f), entered.toString(), "Entered Marks", GREEN)
                            MiniStat(Modifier.weight(1f), pending.toString(), "Pending", ORANGE)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onViewStudents,
                            colors = ButtonDefaults.buttonColors(containerColor = BLUE),
                            modifier = Modifier.fillMaxWidth()
                        ) { Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("View All Marks") }
                    }
                }
                item {
                    CardBox {
                        Cell("Quick Actions", MUTED, true)
                        Spacer(Modifier.height(6.dp))
                        QuickRow(Icons.Filled.AddCircle, "Add Assessment") { onEnterMarks(subjectId) }
                        Divider(color = STROKE, modifier = Modifier.padding(vertical = 4.dp))
                        QuickRow(Icons.Filled.UploadFile, "Import Marks (Excel)") { onEnterMarks(subjectId) }
                        Divider(color = STROKE, modifier = Modifier.padding(vertical = 4.dp))
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        QuickRow(Icons.Filled.Description, "Generate Report") {
                            android.widget.Toast.makeText(ctx, "Report cards are generated on the school console \u2014 your head teacher compiles and prints them on the PC.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, value: String, label: String, color: Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MUTED, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun QuickRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BLUE, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = NAVY, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MUTED, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ComingSoonCard(title: String, body: String) {
    CardBox {
        Cell(title, MUTED, true)
        Spacer(Modifier.height(6.dp))
        Cell(body, MUTED)
    }
}

/** One learner in the class list — initials, name, admission number, chevron. */
@Composable
private fun StudentRow(student: Student, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CARD)
            .border(BorderStroke(1.dp, STROKE), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(avatarColor(student.id).copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Text(initialsOf(student), color = avatarColor(student.id), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(student.fullName, color = NAVY, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Adm ${student.admissionNo}", color = MUTED, fontSize = 13.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MUTED, modifier = Modifier.size(18.dp))
    }
}

private fun initialsOf(s: Student): String =
    listOf(s.firstName, s.lastName).mapNotNull { it.trim().firstOrNull()?.uppercase() }.joinToString("")

private fun todayLabel(): String =
    java.text.SimpleDateFormat("d MMM yyyy").format(java.util.Date())
