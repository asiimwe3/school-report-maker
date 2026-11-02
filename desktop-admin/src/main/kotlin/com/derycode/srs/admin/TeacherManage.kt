// ─────────────────────────────────────────────────────────────────────────────
// Teacher management — the admin has full control of every teacher:
// role (standard posts or a custom title), class-teacher post, classes taught,
// subjects taught, extra duties (DOS, games, patron …), and access on/off.
// Everything here is local data: no internet needed.
// ─────────────────────────────────────────────────────────────────────────────

package com.derycode.srs.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*

/** Duties a Ugandan secondary school actually hands out. Free text is allowed too. */
val DUTY_PRESETS = listOf(
    "Director of Studies", "Deputy Head Teacher", "Class Teacher", "Games Master",
    "Examination Officer", "Discipline", "Patron — Clubs", "Timekeeper",
    "Library", "Sanitation", "Stores", "Chaplaincy", "Transport", "Boarding",
    "Guidance & Counselling", "ICT / Computer Lab", "Sports Captain"
)

private val ROLE_PRESETS = listOf(
    Role.TEACHER, Role.CLASS_TEACHER, Role.DOS, Role.DEPUTY_HEAD,
    Role.HEAD_TEACHER, Role.BURSAR, Role.LIBRARIAN, Role.DATA_ENTRY
)

/** One teacher's editable control panel. */
@Composable
fun TeacherAdminPanel(state: AppState, t: Teacher, onClose: () -> Unit) {
    val d = state.data
    var customTitle by remember(t.id) { mutableStateOf(t.roleTitle) }
    var newDuty by remember(t.id) { mutableStateOf("") }

    val myAssignments = d.assignments.filter { it.teacherId == t.id }
    val mySubjectIds = myAssignments.mapNotNull { it.subjectId }.toSet()
    val myClassIds = myAssignments.filter { it.subjectId == null }.map { it.classId }.toSet()

    fun update(action: String, detail: String = t.name, block: (SchoolData) -> SchoolData) {
        state.repo.mutate(action, "Teacher", t.id, new = detail, block = block)
        state.refresh()
    }

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = Theme.ACCENT, modifier = Modifier.size(18.dp))
            Text("Manage ${t.name}", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (t.active) "Active" else "Access off", color = if (t.active) Theme.GOOD else Theme.WARN, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onClose, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(30.dp)) { Text("Done", fontSize = 11.sp) }
        }
        Spacer(Modifier.height(10.dp))

        // ── Role / post ──────────────────────────────────────────────────────
        Text("Role / post", color = Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ROLE_PRESETS.forEach { r ->
                FilterChip(
                    selected = t.role == r && t.roleTitle.isBlank(),
                    onClick = {
                        update("TEACHER_ROLE", "${t.name} → ${roleLabel(r)}") { dd ->
                            dd.copy(teachers = dd.teachers.map { if (it.id == t.id) it.copy(role = r, roleTitle = "") else it })
                        }
                    },
                    label = { Text(roleLabel(r), fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(customTitle, { customTitle = it }, Modifier.width(240.dp), "Custom title (optional, e.g. Director of Studies)")
            Btn("Use custom title", primary = false) {
                update("TEACHER_CUSTOM_ROLE", "${t.name} → ${customTitle.ifBlank { "standard role" }}") { dd ->
                    dd.copy(teachers = dd.teachers.map { if (it.id == t.id) it.copy(roleTitle = customTitle.trim()) else it })
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // ── Class teacher of ─────────────────────────────────────────────────
        Text("Class teacher of", color = Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = d.classes.none { it.classTeacherId == t.id },
                onClick = {
                    update("CLASS_TEACHER_CLEARED", t.name) { dd ->
                        dd.copy(classes = dd.classes.map { if (it.classTeacherId == t.id) it.copy(classTeacherId = "") else it })
                    }
                }, label = { Text("None", fontSize = 12.sp) })
            d.classes.forEach { c ->
                val label = if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}"
                FilterChip(selected = c.classTeacherId == t.id, onClick = {
                    update("CLASS_TEACHER_SET", "$label → ${t.name}") { dd ->
                        dd.copy(classes = dd.classes.map {
                            when {
                                it.id == c.id -> it.copy(classTeacherId = t.id)
                                it.classTeacherId == t.id -> it.copy(classTeacherId = "")   // one class each
                                else -> it
                            }
                        })
                    }
                }, label = { Text(label, fontSize = 12.sp) })
            }
        }

        Spacer(Modifier.height(14.dp))
        // ── Classes taught ───────────────────────────────────────────────────
        Text("Classes taught", color = Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            d.classes.forEach { c ->
                val label = if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}"
                FilterChip(selected = c.id in myClassIds, onClick = {
                    val on = c.id !in myClassIds
                    update("TEACHER_CLASSES", "${t.name}: $label ${if (on) "added" else "removed"}") { dd ->
                        val existing = dd.assignments.filter { it.teacherId == t.id && it.subjectId == null && it.classId == c.id }
                        dd.copy(assignments = if (on)
                            dd.assignments + TeacherAssignment(id = "asg-${t.id}-${c.id}", teacherId = t.id, subjectId = null, classId = c.id)
                        else dd.assignments - existing.toSet())
                    }
                }, label = { Text(label, fontSize = 12.sp) })
            }
        }

        Spacer(Modifier.height(14.dp))
        // ── Subjects taught (in the classes ticked above) ────────────────────
        Text("Subjects taught", color = Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("A subject is assigned in every class ticked above. Clear all classes first if you want one class only.", color = Theme.MUTED, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            d.subjects.forEach { subj ->
                FilterChip(selected = subj.id in mySubjectIds, onClick = {
                    val on = subj.id !in mySubjectIds
                    val targets = if (myClassIds.isEmpty()) d.classes.map { it.id } else myClassIds.toList()
                    update("TEACHER_SUBJECTS", "${t.name}: ${subj.name} ${if (on) "added" else "removed"}") { dd ->
                        if (on) {
                            val add = targets.filter { cid -> dd.assignments.none { it.teacherId == t.id && it.subjectId == subj.id && it.classId == cid } }
                                .map { cid -> TeacherAssignment(id = "asg-${t.id}-${subj.id}-$cid", teacherId = t.id, subjectId = subj.id, classId = cid) }
                            dd.copy(assignments = dd.assignments + add)
                        } else {
                            dd.copy(assignments = dd.assignments.filterNot { it.teacherId == t.id && it.subjectId == subj.id })
                        }
                    }
                }, label = { Text(subj.name, fontSize = 12.sp) })
            }
        }

        Spacer(Modifier.height(14.dp))
        // ── Extra duties ─────────────────────────────────────────────────────
        Text("Extra duties", color = Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DUTY_PRESETS.forEach { duty ->
                FilterChip(selected = t.duties.contains(duty), onClick = {
                    val on = !t.duties.contains(duty)
                    update("TEACHER_DUTY", "${t.name}: $duty ${if (on) "given" else "removed"}") { dd ->
                        dd.copy(teachers = dd.teachers.map {
                            if (it.id == t.id) it.copy(duties = if (on) it.duties + duty else it.duties - duty) else it
                        })
                    }
                }, label = { Text(duty, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(newDuty, { newDuty = it }, Modifier.width(240.dp), "Other duty (free text)")
            Btn("Give this duty", primary = false) {
                val duty = newDuty.trim()
                if (duty.isNotEmpty()) {
                    update("TEACHER_DUTY", "${t.name}: $duty") { dd ->
                        dd.copy(teachers = dd.teachers.map { if (it.id == t.id) it.copy(duties = (it.duties + duty).distinct()) else it })
                    }
                    newDuty = ""
                }
            }
        }
        if (t.duties.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Duties now: " + t.duties.joinToString(" · "), color = Theme.ACCENT, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(14.dp))
        // ── Activity ─────────────────────────────────────────────────────────
        val mine = d.marks.filter { it.enteredBy == t.id }
        Text("Activity", color = Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            buildString {
                append("${mine.size} marks entered")
                mine.maxByOrNull { it.updatedAt }?.let { last ->
                    append(" · last entry ")
                    append(java.time.Instant.ofEpochMilli(last.updatedAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")))
                }
                append(" · " + if (t.cloudUid.isNotBlank()) "phone connected" else "phone not connected")
            },
            color = Theme.TEXT, fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn(if (t.active) "Turn access off" else "Turn access on", primary = false) {
                update(if (t.active) "TEACHER_ACCESS_OFF" else "TEACHER_ACCESS_ON", t.name) { dd ->
                    dd.copy(teachers = dd.teachers.map { if (it.id == t.id) it.copy(active = !it.active) else it })
                }
            }
            Btn("Remove teacher", primary = false) {
                update("TEACHER_REMOVED", t.name) { dd ->
                    dd.copy(
                        teachers = dd.teachers.filterNot { it.id == t.id },
                        assignments = dd.assignments.filterNot { it.teacherId == t.id },
                        classes = dd.classes.map { if (it.classTeacherId == t.id) it.copy(classTeacherId = "") else it }
                    )
                }
                onClose()
            }
        }
    }
}
