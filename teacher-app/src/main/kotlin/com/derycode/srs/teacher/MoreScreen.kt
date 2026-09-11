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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*

// ─────────────────────────────────────────────────────────────────────────────
// More — teacher identity, comments, sync/export, support & licence
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MoreScreen(state: TeacherState, onOpen: (Route) -> Unit) {
    val d = state.data
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar((state.me?.name ?: "Teacher").take(2), BLUE, 44)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.me?.name ?: "No teacher selected", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(d.school.name.ifBlank { "SRS Teacher" }, color = MUTED, fontSize = 13.sp)
                    }
                }
                if (d.teachers.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Cell("Switch teacher", MUTED, true)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.teachers.forEach { t ->
                            FilterChipPill(t.name, selected = state.meId == t.id) { state.setMe(t.id) }
                        }
                    }
                }
            }
        }
        item {
            CardBox {
                MoreRow(Icons.Filled.Comment, "Class Teacher Comments", "Write end-of-term remarks") { onOpen(Route.Comments) }
                Divider(color = STROKE, modifier = Modifier.padding(vertical = 4.dp))
                MoreRow(Icons.Filled.Shield, "Duty Desk", "Teacher on duty \u00B7 gate passes \u00B7 duty log") { onOpen(Route.Duty) }
                MoreRow(Icons.Filled.PersonAdd, "Register student", "Enroll a new student from the field") { onOpen(Route.Register) }
                MoreRow(Icons.Filled.EventAvailable, "Attendance", "Daily register \u00B7 present / absent / late") { onOpen(Route.Attendance) }
                MoreRow(Icons.Filled.Sync, "Sync & Export", "Import school data \u00B7 export your marks") { onOpen(Route.Sync) }
                Divider(color = STROKE, modifier = Modifier.padding(vertical = 4.dp))
                MoreRow(Icons.Filled.SupportAgent, "Support & Licence", "Updates \u00B7 crash reports \u00B7 plans") { onOpen(Route.Support) }
            }
        }
        item {
            CardBox {
                Cell("About", MUTED, true)
                Spacer(Modifier.height(6.dp))
                Cell("SRS Teacher ${BuildConfig.VERSION_NAME} \u00B7 100% offline \u2014 marks stay on this phone.", MUTED)
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(BLUE.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = BLUE, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MUTED, fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MUTED, modifier = Modifier.size(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Class teacher comments
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CommentsScreen(state: TeacherState) {
    val d = state.data
    val classes = state.myClasses
    var classId by remember { mutableStateOf(classes.firstOrNull()?.id ?: "") }
    val students = state.studentsIn(classId)
    var studentId by remember { mutableStateOf(students.firstOrNull()?.id ?: "") }
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val termId = state.currentTermId

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CardBox {
                Cell("Class", MUTED, true)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    classes.forEach { c -> FilterChipPill(classShort(c), selected = classId == c.id) { classId = c.id } }
                }
                Spacer(Modifier.height(10.dp))
                Cell("Student", MUTED, true)
                Spacer(Modifier.height(6.dp))
                students.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { studentId = s.id }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text((if (studentId == s.id) "\u25CF " else "\u25CB "), color = if (studentId == s.id) BLUE else MUTED)
                        Text(s.fullName, color = if (studentId == s.id) BLUE else Color.White, fontSize = 15.sp)
                    }
                }
            }
        }
        item {
            CardBox {
                Cell("Comment", MUTED, true)
                Spacer(Modifier.height(6.dp))
                DarkTextField(text) { text = it }
                Spacer(Modifier.height(8.dp))
                Cell("Comment bank \u2014 tap to insert", MUTED)
                Spacer(Modifier.height(4.dp))
                val bank = d.commentTemplates.filter { it.category == "TEACHER" }
                bank.take(8).forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { text = c.text }.padding(vertical = 5.dp)) {
                        Text("\u2022 ${c.text}", color = MUTED, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (studentId.isBlank() || text.isBlank()) { msg = "Pick a student and write a comment first."; return@Button }
                        state.addComment(Comment(
                            id = "c-${studentId}-$termId-${System.currentTimeMillis()}",
                            studentId = studentId, termId = termId, type = CommentType.CLASS_TEACHER,
                            text = text.trim()
                        ))
                        text = ""
                        msg = "Saved \u2713"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BLUE),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save comment") }
                if (msg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Cell(msg, GREEN) }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sync & Export — import school setup from admin, export marks bundle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SyncScreen(state: TeacherState) {
    val d = state.data
    var path by remember { mutableStateOf<String?>(null) }
    var importMsg by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = BLUE, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Cell("Import school data from admin", MUTED, true)
                }
                Spacer(Modifier.height(6.dp))
                Cell("Copy school-data-from-admin.json here via USB/Bluetooth, then tap Import. Your own marks and comments are always kept.", MUTED)
                Spacer(Modifier.height(4.dp))
                Cell("School: ${d.school.name.ifBlank { "not connected yet" }}", MUTED)
                Cell("Teacher: ${state.me?.name ?: "not selected"}", MUTED)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importMsg = state.importSchoolData() }, colors = ButtonDefaults.buttonColors(containerColor = BLUE)) {
                        Text("Import")
                    }
                    Button(onClick = { state.setMe(null) }, colors = ButtonDefaults.buttonColors(containerColor = CARD_ALT)) {
                        Text("Change teacher", color = Color.White)
                    }
                }
                if (state.pendingSchoolName != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { importMsg = state.confirmSchoolSwitch() }, colors = ButtonDefaults.buttonColors(containerColor = ORANGE)) {
                        Text("Switch school (replaces everything)")
                    }
                }
                if (importMsg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Cell(importMsg, if (importMsg.startsWith("\u2713") || importMsg.startsWith("\u2713")) GREEN else ORANGE) }
            }
        }
        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = BLUE, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Cell("Export marks bundle", MUTED, true)
                }
                Spacer(Modifier.height(6.dp))
                Cell("Copy this file to the admin computer via USB or Bluetooth and import it in Sync & Backup.", MUTED)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { path = state.exportBundle() }, colors = ButtonDefaults.buttonColors(containerColor = BLUE)) {
                    Text("Export ${d.marks.size} marks + duty data")
                }
                if (path != null) {
                    Spacer(Modifier.height(6.dp))
                    Cell("\u2713 Saved:", GREEN, true)
                    Cell(path ?: "", MUTED)
                }
            }
        }
        item {
            CardBox {
                Cell("How offline sync works", MUTED, true)
                Spacer(Modifier.height(4.dp))
                Cell("1. Marks save instantly to this phone.", MUTED)
                Cell("2. Export creates a validated bundle file.", MUTED)
                Cell("3. Admin imports it \u2014 newest-wins merge, everything logged.", MUTED)
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}
