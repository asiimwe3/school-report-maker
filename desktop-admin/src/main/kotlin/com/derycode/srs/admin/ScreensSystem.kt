package com.derycode.srs.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*
import com.derycode.srs.core.report.DocxReport
import com.derycode.srs.core.results.ResultEngine
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date

// ─────────────────────────────────────────────────────────────────────────────
// 7. Report Template Library (Section 25–27)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TemplatesScreen(state: AppState) {
    val d = state.data
    var name by remember { mutableStateOf("") }
    var layout by remember { mutableStateOf(TemplateLayout.CLASSIC) }

    ScreenTitle("Report Template Library", "Designs for report cards — create, duplicate, set a default.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        CardBox {
            Text("Create / duplicate a template", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(name, { name = it }, Modifier.width(180.dp), "e.g. P7 Classic 2026")
                TemplateLayout.entries.forEach { l ->
                    FilterChip(selected = layout == l, onClick = { layout = l },
                        label = { Text(l.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) })
                }
                Btn("Add template") {
                    if (name.isNotBlank()) {
                        val id = state.repo.nextId()
                        state.repo.mutate("TEMPLATE_ADDED", "ReportTemplate", id, new = name) { dd ->
                            dd.copy(templates = dd.templates + ReportTemplate(id = id, name = name, layout = layout))
                        }
                        name = ""
                        state.refresh()
                    }
                }
            }
        }
        // ── Template gallery: visual preview cards ──
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            d.templates.filter { !it.archived }.forEach { t ->
                TemplateCard(state, t)
            }
        }
        CardBox {
            Text("Archived templates", color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val archived = d.templates.filter { it.archived }
            if (archived.isEmpty()) Text("None.", color = Theme.MUTED, fontSize = 14.sp)
            archived.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(t.name, color = Theme.MUTED, fontSize = 14.sp, modifier = Modifier.width(220.dp))
                    Btn("Restore", primary = false, onClick = {
                        state.repo.mutate("TEMPLATE_RESTORED", "ReportTemplate", t.id) { dd ->
                            dd.copy(templates = dd.templates.map { if (it.id == t.id) it.copy(archived = false) else it })
                        }
                        state.refresh()
                    })
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(state: AppState, t: ReportTemplate) {
    Column(
        Modifier.width(230.dp).background(Theme.CARD, RoundedCornerShape(14.dp))
            .border(1.dp, if (t.isDefault) Theme.ACCENT else Color(0xFF1E2A47), RoundedCornerShape(14.dp)).padding(12.dp)
    ) {
        if (t.isDefault) Text("DEFAULT", color = Theme.ACCENT, fontSize = 9.sp, fontWeight = FontWeight.Black)
        Text(t.name, color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(t.layout.name.lowercase().replaceFirstChar { it.uppercase() } + " layout", color = Theme.MUTED, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        TemplatePreview(t.layout)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!t.isDefault) Btn("Default", primary = false, onClick = {
                state.repo.mutate("TEMPLATE_DEFAULT_CHANGED", "ReportTemplate", t.id, new = t.name) { dd ->
                    dd.copy(templates = dd.templates.map { x -> x.copy(isDefault = x.id == t.id) })
                }
                state.refresh()
            })
            Btn("Duplicate", primary = false, onClick = {
                val id = state.repo.nextId()
                state.repo.mutate("TEMPLATE_DUPLICATED", "ReportTemplate", id, new = "${t.name} copy") { dd ->
                    dd.copy(templates = dd.templates + t.copy(id = id, name = "${t.name} copy", isDefault = false))
                }
                state.refresh()
            })
            Btn("Archive", primary = false, onClick = {
                state.repo.mutate("TEMPLATE_ARCHIVED", "ReportTemplate", t.id) { dd ->
                    dd.copy(templates = dd.templates.map { if (it.id == t.id) it.copy(archived = true) else it })
                }
                state.refresh()
            })
        }
    }
}

/** Miniature visual mock of a report card per layout. */
@Composable
private fun TemplatePreview(layout: TemplateLayout) {
    val bg = Color(0xFF0E1526)
    Column(
        Modifier.fillMaxWidth().height(150.dp).background(bg, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF223054), RoundedCornerShape(8.dp)).padding(8.dp)
    ) {
        when (layout) {
            TemplateLayout.CLASSIC -> {
                Box(Modifier.fillMaxWidth().height(16.dp).background(Color(0xFF1F4E79), RoundedCornerShape(3.dp)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(7.dp).background(Color(0xFF3A4763), RoundedCornerShape(2.dp)))
                Spacer(Modifier.height(6.dp))
                repeat(5) {
                    Box(Modifier.fillMaxWidth().height(8.dp).border(1.dp, Color(0xFF2C3B5E), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.height(4.dp))
                }
            }
            TemplateLayout.MODERN -> {
                Box(Modifier.fillMaxWidth().height(26.dp).background(Color(0xFF4F8CFF), RoundedCornerShape(6.dp)))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { Box(Modifier.weight(1f).height(16.dp).background(Color(0xFF1B2A4A), RoundedCornerShape(3.dp))) }
                }
                Spacer(Modifier.height(6.dp))
                repeat(4) {
                    Box(Modifier.fillMaxWidth(0.8f).height(6.dp).background(Color(0xFF3A4763), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.height(5.dp))
                }
            }
            TemplateLayout.COMPACT -> {
                Box(Modifier.fillMaxWidth().height(10.dp).background(Color(0xFF2C3B5E), RoundedCornerShape(2.dp)))
                Spacer(Modifier.height(5.dp))
                repeat(9) {
                    Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF243355), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.height(3.dp))
                }
            }
            TemplateLayout.PLAIN -> {
                Box(Modifier.fillMaxWidth(0.6f).height(9.dp).background(Color(0xFF4E5D7E), RoundedCornerShape(2.dp)))
                Spacer(Modifier.height(8.dp))
                repeat(5) {
                    Box(Modifier.fillMaxWidth(0.9f).height(6.dp).background(Color(0xFF3A4763), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.height(6.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2C3B5E)))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. Report Preview — single student before batch generation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PreviewScreen(state: AppState) {
    val d = state.data
    var classId by remember { mutableStateOf(d.classes.firstOrNull { it.active }?.id ?: "") }
    var studentId by remember { mutableStateOf("") }
    var layout by remember { mutableStateOf(TemplateLayout.CLASSIC) }
    var msg by remember { mutableStateOf("") }
    val year = d.academicYears.firstOrNull { it.currentTermId != null } ?: d.academicYears.firstOrNull()
    val term = year?.terms?.firstOrNull { it.id == year.currentTermId } ?: year?.terms?.firstOrNull()
    val cls = d.classes.firstOrNull { it.id == classId }
    val scheme = d.gradingSchemes.firstOrNull { it.level == cls?.level } ?: d.gradingSchemes.firstOrNull()
    val classStudents = d.enrollments.filter { it.classId == classId }
        .mapNotNull { e -> d.students.firstOrNull { s -> s.id == e.studentId && s.status == StudentStatus.ACTIVE } }

    ScreenTitle("Report Preview", "Check one student's card before batch-printing the whole class.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Class:", color = Theme.MUTED, fontSize = 14.sp)
                d.classes.filter { it.active }.forEach { c ->
                    FilterChip(selected = classId == c.id, onClick = { classId = c.id; studentId = "" },
                        label = { Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 13.sp) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Student:", color = Theme.MUTED, fontSize = 14.sp)
                classStudents.take(12).forEach { s ->
                    FilterChip(selected = studentId == s.id, onClick = { studentId = s.id },
                        label = { Text(s.fullName, fontSize = 13.sp) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Design:", color = Theme.MUTED, fontSize = 14.sp)
                TemplateLayout.entries.forEach { l ->
                    FilterChip(selected = layout == l, onClick = { layout = l },
                        label = { Text(l.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 13.sp) })
                }
            }
        }
        if (term == null || scheme == null) {
            Text("Create an academic year with terms and grading schemes first.", color = Theme.MUTED, fontSize = 14.sp)
        } else if (classStudents.isEmpty()) {
            Text("No active students in this class.", color = Theme.MUTED, fontSize = 14.sp)
        } else {
            CardBox {
                Text("Preview as Word document", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("The preview uses the exact same engine as batch printing — it opens in Word (.docx), exactly as it will print.", color = Theme.MUTED, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Btn("Preview selected student") {
                    val sid = studentId.ifBlank { classStudents.first().id }
                    val result = ResultEngine.studentTermResult(
                        d, sid, term.id, scheme.id,
                        principalSubjectIds = emptyList(),
                        compulsorySubjectIds = emptySet()
                    )
                    val outDir = dataDir.resolve("reports")
                    Files.createDirectories(outDir)
                    val f = outDir.resolve("preview-${sid}.docx")
                    DocxReport.writeStudentReport(f, d, result, layout)
                    try { Desktop.getDesktop().open(f.toFile()) } catch (_: Exception) { }
                    msg = "Saved to ${f.fileName}"
                }
                if (msg.isNotEmpty()) Text(msg, color = Theme.GOOD, fontSize = 14.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 10. Settings (Section 55)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(state: AppState) {
    val s = state.data.settings
    var lang by remember(s) { mutableStateOf(s.language) }
    var theme by remember(s) { mutableStateOf(s.theme) }
    var currency by remember(s) { mutableStateOf(s.currencySymbol) }
    var notif by remember(s) { mutableStateOf(s.notificationsEnabled) }
    var autoSync by remember(s) { mutableStateOf(s.autoSyncEnabled) }
    var autoBackup by remember(s) { mutableStateOf(s.autoBackupEnabled) }
    var freq by remember(s) { mutableStateOf(s.autoBackupFrequencyDays.toString()) }
    var keep by remember(s) { mutableStateOf(s.backupsToKeep.toString()) }
    var saved by remember { mutableStateOf(false) }

    var confirmTxt by remember { mutableStateOf("") }
    var wiped by remember { mutableStateOf(false) }

    ScreenTitle("Settings", "App-wide preferences — stored in the same offline database.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Danger zone: factory reset ──
        CardBox {
            Text("Danger zone — Reset all data", color = Color(0xFFFF6B6B), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Erases EVERYTHING: school profile, students, marks, fees, backups and audit trail — restores the app to a clean, brand-new state. Use before transferring or reinstalling the console.", color = Theme.MUTED, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(confirmTxt, { confirmTxt = it }, Modifier.width(200.dp), "Type RESET to confirm")
                Btn("Erase all data", primary = false, onClick = {
                    if (confirmTxt.trim() == "RESET") {
                        state.repo.wipeAndReseed(com.derycode.srs.core.seed.Seeds.ALL_SUBJECTS, com.derycode.srs.core.seed.Seeds.COMPONENTS)
                        confirmTxt = ""
                        wiped = true
                        state.refresh()
                    }
                })
                if (wiped) Text("✓ All data erased — the console is clean.", color = Theme.GOOD, fontSize = 14.sp)
            }
        }
        CardBox {
            Text("General", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Language:", color = Theme.MUTED, fontSize = 14.sp, modifier = Modifier.width(90.dp))
                listOf("English", "Runyoro", "Luganda", "Swahili").forEach { l ->
                    FilterChip(selected = lang == l, onClick = { lang = l }, label = { Text(l, fontSize = 12.sp) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Theme:", color = Theme.MUTED, fontSize = 14.sp, modifier = Modifier.width(90.dp))
                Theme.THEMES.forEach { t ->
                    FilterChip(selected = theme == t, onClick = { theme = t; Theme.apply(t) }, label = { Text(t, fontSize = 12.sp) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Currency:", color = Theme.MUTED, fontSize = 14.sp, modifier = Modifier.width(90.dp))
                TextField(currency, { currency = it }, Modifier.width(80.dp), "UGX")
            }
        }
        CardBox {
            Text("Notifications & backup", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Checkbox(notif, { notif = it }); Text("In-app notifications", color = Theme.TEXT, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Checkbox(autoSync, { autoSync = it }); Text("Auto-send when a bundle source connects", color = Theme.TEXT, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Checkbox(autoBackup, { autoBackup = it }); Text("Auto-backup", color = Theme.TEXT, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Backup every", color = Theme.MUTED, fontSize = 14.sp)
                TextField(freq, { freq = it }, Modifier.width(50.dp), "7")
                Text("days, keep latest", color = Theme.MUTED, fontSize = 14.sp)
                TextField(keep, { keep = it }, Modifier.width(50.dp), "10")
                Spacer(Modifier.width(20.dp))
                Btn("Save settings") {
                    state.repo.mutate("SETTINGS_CHANGED", "AppSettings", "settings") { dd ->
                        dd.copy(settings = dd.settings.copy(
                            language = lang.ifBlank { "English" }, theme = theme, currencySymbol = currency.ifBlank { "UGX" },
                            notificationsEnabled = notif, autoSyncEnabled = autoSync, autoBackupEnabled = autoBackup,
                            autoBackupFrequencyDays = freq.toIntOrNull() ?: 7, backupsToKeep = keep.toIntOrNull() ?: 10
                        ))
                    }
                    saved = true
                    state.refresh()
                }
                if (saved) Text("Saved ✓", color = Theme.GOOD, fontSize = 14.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 15. Audit Log (Section 42)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AuditScreen(state: AppState) {
    val d = state.data
    var filter by remember { mutableStateOf("") }
    val fmt = SimpleDateFormat("MMM d, HH:mm:ss")
    val entries = d.auditLog.asReversed().filter {
        filter.isBlank() || it.action.contains(filter, ignoreCase = true) ||
        it.entity.contains(filter, ignoreCase = true) || it.entityId.contains(filter) || it.newValue.contains(filter)
    }

    ScreenTitle("Audit Log", "Every change is recorded — who, what, when, old → new. Cannot be edited from the app.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filter:", color = Theme.MUTED, fontSize = 14.sp)
                TextField(filter, { filter = it }, Modifier.width(260.dp), "action / entity / id / value")
                Text("${entries.size} of ${d.auditLog.size} entries", color = Theme.MUTED, fontSize = 14.sp)
            }
        }
        CardBox {
            LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries.take(300)) { e ->
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(fmt.format(Date(e.timestamp)), color = Theme.MUTED, fontSize = 13.sp, modifier = Modifier.width(120.dp))
                            Text(e.action, color = Theme.ACCENT, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(160.dp))
                            Text(if (e.entity.isEmpty()) "" else "${e.entity} ${e.entityId}", color = Theme.TEXT, fontSize = 13.sp, modifier = Modifier.width(180.dp))
                            Text(if (e.oldValue.isNotEmpty()) "\"${e.oldValue}\" → \"${e.newValue}\"" else e.newValue,
                                color = Theme.MUTED, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = Color(0xFF1B2540))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 16. Reports archive (Section 51–52)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReportsArchiveScreen(state: AppState) {
    val d = state.data
    val fmt = SimpleDateFormat("MMM d, yyyy HH:mm")

    ScreenTitle("Generated Reports", "Every report card ever generated, with the exact grading scheme version frozen at generation time.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                Text("Student", color = Theme.MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(220.dp))
                Text("Scheme (version)", color = Theme.MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(180.dp))
                Text("Status", color = Theme.MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                Text("Generated", color = Theme.MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.height(400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(d.reports.asReversed()) { r ->
                    val st = d.students.firstOrNull { it.id == r.studentId }
                    val scheme = d.gradingSchemes.firstOrNull { it.id == r.schemeId }
                    Row(horizontalArrangement = Arrangement.spacedBy(30.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(st?.fullName ?: r.studentId, color = Theme.TEXT, fontSize = 14.sp, modifier = Modifier.width(220.dp))
                        Text("${scheme?.name ?: r.schemeId} (v${r.schemeVersion})", color = Theme.MUTED, fontSize = 14.sp, modifier = Modifier.width(180.dp))
                        Text(if (r.approved) "approved" else "draft", color = if (r.approved) Theme.GOOD else Theme.MUTED, fontSize = 14.sp, modifier = Modifier.width(100.dp))
                        Text(if (r.generatedAt > 0) fmt.format(Date(r.generatedAt)) else "—", color = Theme.MUTED, fontSize = 14.sp)
                    }
                }
                if (d.reports.isEmpty()) item { Text("No reports generated yet — use Reports → Generate.", color = Theme.MUTED, fontSize = 14.sp) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 17. Backup History (Section 37–38)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BackupHistoryScreen(state: AppState) {
    var msg by remember(state.refreshTick) { mutableStateOf("") }
    val backups = listBackups(dataDir.resolve("backups")).sortedByDescending { it.fileName.toString() }
    val fmt = SimpleDateFormat("MMM d, yyyy HH:mm:ss")

    ScreenTitle("Backup History", "Rotating automatic backups of the whole database. A safety backup is taken before any restore.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Create backup now") {
                    state.repo.store.backup(dataFile)
                    state.repo.audit("BACKUP_CREATED", "SchoolData")
                    msg = "Backup created ✓"
                    state.refresh()
                }
                Btn("Open backups folder") {
                    val dir = dataDir.resolve("backups").toFile()
                    if (dir.exists()) try { Desktop.getDesktop().open(dir) } catch (_: Exception) { }
                }
                if (msg.isNotEmpty()) Text(msg, color = Theme.GOOD, fontSize = 14.sp)
            }
        }
        CardBox {
            Text("Local backups (${backups.size})", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.height(380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(backups) { b ->
                    val stamp = b.fileName.toString().removePrefix("backup-").removeSuffix(".json").toLongOrNull() ?: 0L
                    val size = Files.size(b)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(fmt.format(Date(stamp)), color = Theme.TEXT, fontSize = 14.sp, modifier = Modifier.width(220.dp))
                        Text(String.format("%.1f KB", size / 1024.0), color = Theme.MUTED, fontSize = 14.sp, modifier = Modifier.width(100.dp))
                        if (stamp > 0) Btn("Restore", primary = false, onClick = {
                            val ok = state.repo.restore(b)
                            msg = if (ok) "Restored ✓ (a safety backup was taken first)" else "Restore failed — file unreadable"
                            state.refresh()
                        })
                    }
                }
                if (backups.isEmpty()) item { Text("No backups yet — one is created automatically after every change.", color = Theme.MUTED, fontSize = 14.sp) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 18. User Management (Section 11–12) — users are Teacher/Role accounts
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun UsersScreen(state: AppState) {
    val d = state.data
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.TEACHER) }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    ScreenTitle("User Management", "Accounts for the admin console and teacher app. Roles decide what each user can do.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(name, { name = it }, Modifier.width(160.dp), "Full name")
                TextField(username, { username = it }, Modifier.width(120.dp), "username")
                TextField(pin, { pin = it }, Modifier.width(80.dp), "4-digit PIN")
                Role.entries.forEach { r ->
                    FilterChip(selected = role == r, onClick = { role = r }, label = { Text(roleLabel(r), fontSize = 9.sp) })
                }
                Btn("Add user") {
                    if (name.isNotBlank()) {
                        val id = state.repo.nextId()
                        state.repo.mutate("USER_ADDED", "User", id, new = "$name (${roleLabel(role)})") { dd ->
                            dd.copy(teachers = dd.teachers + Teacher(
                                id = id, name = name, username = username,
                                pinHash = if (pin.length == 4) pin.hashCode().toString() else "",
                                role = role
                            ))
                        }
                        name = ""; username = ""; pin = ""
                        state.refresh()
                    }
                }
            }
        }
        CardBox {
            Text("Users (${d.teachers.size})", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                Text("Name", color = Theme.MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(180.dp))
                Text("Username", color = Theme.MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp))
                Text("Role", color = Theme.MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp))
                Text("Status", color = Theme.MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
            }
            d.teachers.forEach { t ->
                var roleEdit by remember(t.id) { mutableStateOf(t.role) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(30.dp),
                    modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(t.name, color = Theme.TEXT, fontSize = 14.sp, modifier = Modifier.width(180.dp))
                    Text(t.username.ifBlank { "—" }, color = Theme.MUTED, fontSize = 14.sp, modifier = Modifier.width(120.dp))
                    Text(roleLabel(t.role), color = Theme.TEXT, fontSize = 14.sp, modifier = Modifier.width(130.dp))
                    Text(if (t.active) "active" else "disabled", color = if (t.active) Theme.GOOD else Theme.MUTED, fontSize = 14.sp, modifier = Modifier.width(100.dp))
                    Btn(if (t.active) "Disable" else "Enable", primary = false, onClick = {
                        state.repo.mutate("USER_STATUS", "User", t.id, new = (!t.active).toString()) { dd ->
                            dd.copy(teachers = dd.teachers.map { if (it.id == t.id) it.copy(active = !it.active) else it })
                        }
                        state.refresh()
                    })
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 19. Notifications (Section 58)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NotificationsScreen(state: AppState) {
    val d = state.data
    val year = d.academicYears.firstOrNull { it.currentTermId != null } ?: d.academicYears.firstOrNull()
    val term = year?.terms?.firstOrNull { it.id == year.currentTermId } ?: year?.terms?.firstOrNull()

    val alerts = buildList {
        if (!d.settings.notificationsEnabled) add("notifications" to "Notifications are switched off in Settings.")
        if (term != null) d.classes.filter { it.active }.forEach { c ->
            val subjects = d.subjects.filter { it.level == c.level && it.active }
            subjects.forEach { s ->
                val missing = ResultEngine.studentsWithMissingMarks(d, c.id, term.id, s.id).size
                if (missing > 0) add("missing" to "${missing} student(s) missing marks: ${s.name} — ${c.name} ${c.stream}".trim())
            }
        }
        if (d.marks.isNotEmpty() && d.reports.isEmpty()) add("reports" to "Marks exist but no report cards have been generated yet.")
        val lastBackup = listBackups(dataDir.resolve("backups")).maxOfOrNull { it.fileName.toString().removePrefix("backup-").removeSuffix(".json").toLongOrNull() ?: 0 }
        val ageDays = if (lastBackup == null) -1 else ((System.currentTimeMillis() - lastBackup) / 86_400_000L).toInt()
        if (ageDays < 0) add("backup" to "No backup has been created yet.")
        else if (ageDays > d.settings.autoBackupFrequencyDays) add("backup" to "Last backup is $ageDays days old (setting: every ${d.settings.autoBackupFrequencyDays} days).")
        if (d.marks.any { it.updatedAt > d.lastSyncAt && d.lastSyncAt > 0 }) add("sync" to "Some marks are newer than the last sync — export a bundle from the teacher device or re-generate.")
    }

    ScreenTitle("Notifications", "In-app alerts — everything that needs your attention right now.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Text("${alerts.size} alert(s)", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.height(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(alerts) { (kind, text) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(when (kind) {
                            "missing" -> "⚠"; "backup" -> "💾"; "sync" -> "⇅"
                            "reports" -> "📄"; else -> "ℹ"
                        }, color = when (kind) { "missing" -> Color(0xFFFFB84D); else -> Theme.ACCENT }, fontSize = 16.sp)
                        Text(text, color = Theme.TEXT, fontSize = 14.sp)
                    }
                }
                if (alerts.isEmpty()) item { Text("All clear ✓", color = Theme.GOOD, fontSize = 15.sp) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 20. School Calendar (Section 44.20)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CalendarScreen(state: AppState) {
    val d = state.data
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CalendarEventType.OTHER) }

    ScreenTitle("School Calendar", "Term dates, exams, holidays, meetings and deadlines.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(title, { title = it }, Modifier.width(220.dp), "e.g. Beginning of Term 1")
                TextField(date, { date = it }, Modifier.width(130.dp), "2026-09-14")
                CalendarEventType.entries.take(6).forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t },
                        label = { Text(t.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, fontSize = 9.sp) })
                }
                Btn("Add event") {
                    if (title.isNotBlank() && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        val id = state.repo.nextId()
                        state.repo.mutate("CALENDAR_EVENT_ADDED", "CalendarEvent", id, new = "$title ($date)") { dd ->
                            dd.copy(calendarEvents = dd.calendarEvents + CalendarEvent(id = id, title = title, date = date, type = type))
                        }
                        title = ""; date = ""
                        state.refresh()
                    }
                }
            }
        }
        CardBox {
            Text("Events (${d.calendarEvents.size})", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(d.calendarEvents.sortedBy { it.date }) { e ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(e.date, color = Theme.ACCENT, fontSize = 14.sp, modifier = Modifier.width(110.dp))
                        Text(e.title, color = Theme.TEXT, fontSize = 14.sp, modifier = Modifier.width(280.dp))
                        Text(e.type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, color = Theme.MUTED, fontSize = 14.sp)
                        Btn("Delete", primary = false, onClick = {
                            state.repo.mutate("CALENDAR_EVENT_DELETED", "CalendarEvent", e.id, old = e.title) { dd ->
                                dd.copy(calendarEvents = dd.calendarEvents.filter { it.id != e.id })
                            }
                            state.refresh()
                        })
                    }
                }
                if (d.calendarEvents.isEmpty()) item { Text("No calendar events yet.", color = Theme.MUTED, fontSize = 14.sp) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 13–14. Import / Export (Section 40–41) — CSV
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ImportExportScreen(state: AppState) {
    val d = state.data
    var msg by remember { mutableStateOf("") }
    val exportDir = dataDir.resolve("exports")

    ScreenTitle("Import / Export", "CSV in and out — students, marks, results. No internet needed.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Text("Export", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Export students CSV") {
                    Files.createDirectories(exportDir)
                    val f = exportDir.resolve("students-${System.currentTimeMillis()}.csv")
                    val rows = d.students.joinToString("\n") { s ->
                        listOf(s.admissionNo, s.firstName, s.middleName, s.lastName, s.sex, s.dateOfBirth, s.guardianPhone, s.status.name).joinToString(",") { csv(it) }
                    }
                    Files.writeString(f, "admissionNo,firstName,middleName,lastName,sex,dob,guardianPhone,status\n$rows")
                    msg = "Saved ${f.fileName} (${d.students.size} students)"
                }
                Btn("Export marks CSV") {
                    Files.createDirectories(exportDir)
                    val f = exportDir.resolve("marks-${System.currentTimeMillis()}.csv")
                    val rows = d.marks.joinToString("\n") { m ->
                        listOf(m.studentId, m.subjectId, m.termId, m.componentId, m.score.toString(), m.type.name, m.maxScore.toString()).joinToString(",")
                    }
                    Files.writeString(f, "studentId,subjectId,termId,componentId,score,type,maxScore\n$rows")
                    msg = "Saved ${f.fileName} (${d.marks.size} marks)"
                }
                Btn("Export results CSV") {
                    Files.createDirectories(exportDir)
                    val f = exportDir.resolve("results-${System.currentTimeMillis()}.csv")
                    val rows = d.termResults.joinToString("\n") { r ->
                        listOf(r.studentId, r.termId, r.schemeId, r.aggregate.toString(), r.division.toString(), r.classPosition.toString()).joinToString(",")
                    }
                    Files.writeString(f, "studentId,termId,schemeId,aggregate,division,position\n$rows")
                    msg = "Saved ${f.fileName} (${d.termResults.size} results)"
                }
                Btn("Open exports folder") {
                    if (exportDir.toFile().exists()) try { Desktop.getDesktop().open(exportDir.toFile()) } catch (_: Exception) { }
                }
            }
            if (msg.isNotEmpty()) Text(msg, color = Theme.GOOD, fontSize = 14.sp)
        }
        CardBox {
            Text("Import students", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Place a CSV named students-import.csv in the exports folder with columns: admissionNo,firstName,middleName,lastName,sex,dob,guardianPhone. Existing admission numbers are skipped.", color = Theme.MUTED, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Btn("Import from exports/students-import.csv") {
                val f = exportDir.resolve("students-import.csv")
                if (!Files.exists(f)) { msg = "File not found: $f"; return@Btn }
                val lines = Files.readAllLines(f).drop(1).filter { it.isNotBlank() }
                var added = 0; var skipped = 0
                val toAdd = mutableListOf<Student>()
                lines.forEach { line ->
                    val p = line.split(",")
                    if (p.size < 4) return@forEach
                    val adm = p[0].trim()
                    if (d.students.any { it.admissionNo == adm } || toAdd.any { it.admissionNo == adm }) { skipped++; return@forEach }
                    toAdd += Student(
                        id = state.repo.nextId(), admissionNo = adm,
                        firstName = p[1].trim(), middleName = if (p.size > 2) p[2].trim() else "",
                        lastName = p[3].trim(), sex = p[4].trim().ifBlank { "M" },
                        dateOfBirth = if (p.size > 5) p[5].trim() else "", guardianPhone = if (p.size > 6) p[6].trim() else ""
                    )
                    added++
                }
                if (toAdd.isNotEmpty()) {
                    state.repo.mutate("STUDENTS_IMPORTED", "Student", "", new = "$added added, $skipped skipped") { dd ->
                        dd.copy(students = dd.students + toAdd)
                    }
                    state.refresh()
                }
                msg = "Imported $added students, skipped $skipped (already enrolled or malformed)."
            }
        }
    }
}

private fun csv(s: String): String = if (s.contains(',') || s.contains('"')) "\"${s.replace("\"", "\"\"")}\"" else s
