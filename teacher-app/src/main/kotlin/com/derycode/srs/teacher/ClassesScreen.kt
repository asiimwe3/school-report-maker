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

    Column {
        SearchField(query, { query = it }, "Search class...")
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filters) { f ->
                FilterChipPill(f, selected = filter == f) { filter = f }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (shown.isEmpty()) {
            EmptyHint(if (classes.isEmpty()) "No classes assigned to you yet.\nImport school data from More \u2192 Sync." else "No classes match your search.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown) { c -> ClassCard(state, c) { onOpenClass(c.id) } }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
    }
}

@Composable
private fun ClassCard(state: TeacherState, c: SchoolClass, onClick: () -> Unit) {
    val students = state.studentsIn(c.id)
    val subjects = state.mySubjectsFor(c.id)
    val color = when (c.level) { Level.PRIMARY -> TEAL; Level.A_LEVEL -> PURPLE; else -> BLUE }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CARD).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.20f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.School, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(classLabel(c), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.People, contentDescription = null, tint = MUTED, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text("${students.size} students", color = MUTED, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                subjects.take(2).forEach { s -> SubjectPill(s.name) }
                if (subjects.size > 2) SubjectPill("+${subjects.size - 2}")
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MUTED)
    }
}

@Composable
internal fun SubjectPill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(BLUE.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = BLUE, fontSize = 12.sp)
    }
}

@Composable
internal fun FilterChipPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(18.dp))
            .background(if (selected) BLUE else CARD)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) Color.White else MUTED, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
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
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(BLUE),
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = MUTED, fontSize = 15.sp)
            inner()
        }
    )
}

@Composable
internal fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MUTED, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Class Detail — tabs: Marks / Assignments / Resources
// ─────────────────────────────────────────────────────────────────────────────

private enum class ClassTab { MARKS, ASSIGNMENTS, RESOURCES }

@Composable
fun ClassDetailScreen(state: TeacherState, classId: String, onEnterMarks: (String?) -> Unit, onViewStudents: () -> Unit) {
    val d = state.data
    val cls = d.classes.firstOrNull { it.id == classId }
    val subjects = state.mySubjectsFor(classId)
    var subjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    var tab by remember { mutableStateOf(ClassTab.MARKS) }
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

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (subjects.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(subjects) { s -> FilterChipPill(s.name, selected = subjectId == s.id) { subjectId = s.id } }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CARD).padding(4.dp)) {
                listOf(ClassTab.MARKS to "Marks", ClassTab.ASSIGNMENTS to "Assignments", ClassTab.RESOURCES to "Resources").forEach { (t, label) ->
                    val selected = tab == t
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                            .background(if (selected) BLUE else Color.Transparent)
                            .clickable { tab = t }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(label, color = if (selected) Color.White else MUTED, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
        when (tab) {
            ClassTab.MARKS -> {
                item {
                    CardBox {
                        Cell("Recent Assessment", MUTED, true)
                        Spacer(Modifier.height(6.dp))
                        Text(component?.name ?: "No assessment yet", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(todayLabel(), color = MUTED, fontSize = 13.sp)
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
                        QuickRow(Icons.Filled.Description, "Generate Report") { }
                    }
                }
            }
            ClassTab.ASSIGNMENTS -> item { ComingSoonCard("Assignments", "Set homework and track submissions here \u2014 coming in a future update.") }
            ClassTab.RESOURCES -> item { ComingSoonCard("Resources", "Share notes and past papers with your class \u2014 coming in a future update.") }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, value: String, label: String, color: Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MUTED, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
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

private fun todayLabel(): String =
    java.text.SimpleDateFormat("d MMM yyyy").format(java.util.Date())
