package com.derycode.srs.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.store.SchoolRecovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date

/**
 * "My school setup is gone" — find it again, and bring back everything in it.
 *
 * A restore replaces the whole database: school profile, classes, students,
 * subjects, marks, fees, comments, users, settings. Each saved copy is one
 * clickable row, so a head teacher taps the copy that still has their school and
 * is done — no typing, no rebuilding.
 */
@Composable
fun RestoreSchoolPanel(state: AppState, onRestored: (String) -> Unit = {}) {
    val home = Path.of(System.getProperty("user.home"))
    fun isDir(p: Path) = try { Files.isDirectory(p) } catch (_: Throwable) { false }
    val roots = listOf(
        dataDir,
        dataDir.resolve("backups"),
        home.resolve("SchoolReportMakerTeacher"),
        home.resolve("SchoolReportMakerTeacherDesktop"),
        home.resolve("Desktop"),
        home.resolve("Documents"),
        home.resolve("Downloads")
    ).filter { isDir(it) }

    var filePath by remember { mutableStateOf("") }
    var fileMsg by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<SchoolRecovery.Candidate>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var rescan by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    val fmt = SimpleDateFormat("MMM d, yyyy HH:mm")
    val hint = state.data.school.name.takeIf { it.isNotBlank() && it != "Your School" } ?: ""

    LaunchedEffect(state.refreshTick, rescan) {
        scanning = true
        candidates = withContext(Dispatchers.IO) {
            runCatching { SchoolRecovery.scan(roots, setOf(dataFile)) }.getOrDefault(emptyList())
        }
        scanning = false
    }

    fun restore(file: Path) {
        msg = "Restoring…"
        val ok = runCatching { state.repo.restore(file) }.getOrDefault(false)
        if (!ok) { msg = "That copy could not be read. Try the next one."; return }
        // A restored school must not land back in first-run setup: the file carries
        // the school profile, so treat it as set up once it has a name.
        if (!state.data.settings.setupComplete && state.data.school.name.isNotBlank()) {
            state.repo.mutate("SCHOOL_RESTORED", "School", state.data.school.id) { dd ->
                dd.copy(settings = dd.settings.copy(setupComplete = true))
            }
        }
        state.refresh()
        rescan++
        val restored = state.data
        msg = "Restored ${restored.school.name.ifBlank { "the school" }}: " +
            "${restored.students.size} learners, ${restored.classes.size} classes, ${restored.marks.size} marks, " +
            "${restored.teachers.size} users. Everything came back, not just the profile."
        onRestored(restored.school.name)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (scanning) "Searching this computer for saved copies…"
                else "Found ${candidates.size} saved cop${if (candidates.size == 1) "y" else "ies"} on this computer",
                color = if (scanning) Theme.MUTED else if (candidates.isEmpty()) Theme.WARN else Theme.GOOD,
                fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            Btn("Look again", primary = false) { rescan++ }
        }

        val best = SchoolRecovery.best(candidates, hint)
        if (best != null) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Theme.ACCENT_SOFT).clickable { restore(best.file) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Restore ${best.school}", color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${best.classes} classes · ${best.students} learners · ${best.marks} marks · saved ${fmt.format(Date(best.savedAt))} · tap to bring everything back",
                            color = Theme.MUTED, fontSize = 12.sp)
                    }
                    Btn("Restore") { restore(best.file) }
                }
            }
        }

        if (candidates.isNotEmpty()) {
            Text("Every copy found, newest first. Tap a row to restore it.",
                color = Theme.MUTED, fontSize = 12.sp)
            candidates.take(15).forEach { c ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                        .background(Theme.CARD).clickable { restore(c.file) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(c.school, color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("${c.classes} classes · ${c.students} learners · ${c.marks} marks · ${fmt.format(Date(c.savedAt))} · ${c.origin}",
                                color = Theme.MUTED, fontSize = 12.sp)
                        }
                        Btn("Restore", primary = false) { restore(c.file) }
                    }
                }
            }
        } else if (!scanning) {
            Text("No saved copy with learners was found here. On the school's own computer a copy is written after every change; if this is a new machine, copy ${dataFile.fileName} (or a file from the backups folder) from the old one into ${dataDir}, then press Look again.",
                color = Theme.MUTED, fontSize = 12.sp)
        }

        if (msg.isNotBlank()) Text(msg, color = Theme.GOOD, fontSize = 13.sp)

        CardBox {
            Text("Rebuild from a file you already have", color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("A setup file a phone or laptop received (its name starts school-setup-), or a teacher's marks file sent on WhatsApp (marks-...). Use this when the console computer is new or was wiped.",
                color = Theme.MUTED, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(filePath, { filePath = it }, Modifier.weight(1f), "path to the .json file")
                Btn("Browse", primary = false) {
                    val chooser = javax.swing.JFileChooser()
                    chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("School files (*.json)", "json")
                    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) filePath = chooser.selectedFile.absolutePath
                }
                Btn("Load file") {
                    fileMsg = runCatching { rebuildFromFile(state, Path.of(filePath.trim())) }
                        .getOrElse { "Could not load that file: " + (it.message ?: "unknown") }
                    rescan++
                }
            }
            if (fileMsg.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(fileMsg,
                    color = if (fileMsg.startsWith("School restored") || fileMsg.startsWith("Loaded the marks")) Theme.GOOD else Theme.WARN,
                    fontSize = 12.5.sp)
            }
        }
    }
}

/**
 * Rebuild the school from a file the school already has: the setup file a phone or
 * laptop received, or a marks bundle a teacher sent on WhatsApp. This is the route
 * when the console computer is gone and no local backup survived.
 */
fun rebuildFromFile(state: AppState, path: java.nio.file.Path): String {
    if (!java.nio.file.Files.exists(path)) return "That file is not there: " + path.fileName
    val text = runCatching { java.nio.file.Files.readString(path) }.getOrNull()
        ?: return "That file could not be read. Copy it to this computer first and try again."

    if (text.contains("DERYCODE-SRS-BUNDLE")) {
        val who = state.repo.store.peekBundleSchool(path)
        val report = state.repo.store.importBundle(state.data, path)
        if (!report.success) return report.message
        return try {
            state.repo.mutate("SCHOOL_REBUILT", "Bundle", path.fileName.toString(), new = report.message) { dd ->
                dd.copy(
                    marks = state.repo.store.mergeMarks(dd, report.merged.ifEmpty { dd.marks }),
                    students = (dd.students.filter { s -> report.mergedStudents.none { it.id == s.id } } + report.mergedStudents),
                    comments = if (report.mergedComments.isEmpty()) dd.comments else report.mergedComments,
                    attendance = (dd.attendance.filter { a -> report.mergedAttendance.none { it.id == a.id } } + report.mergedAttendance),
                    dutyRecords = (dd.dutyRecords.filter { r -> report.mergedDutyRecords.none { it.id == r.id } } + report.mergedDutyRecords),
                    gatePasses = (dd.gatePasses.filter { g -> report.mergedGatePasses.none { it.id == g.id } } + report.mergedGatePasses),
                    school = if (dd.school.name.isBlank() && who.isNotBlank()) dd.school.copy(name = who) else dd.school
                )
            }
            state.refresh()
            "Loaded the marks file from " + (if (who.isBlank()) "a teacher" else who) + ": " +
                report.mergedStudents.size + " learners, " + report.merged.size +
                " marks. Classes still need a setup file from a phone: ask any phone that joined this school."
        } catch (e: Exception) {
            "The marks could not be saved: " + e.message
        }
    }

    return runCatching {
        val file = state.repo.store.loadSchoolConfig(path, state.data)
        if (file.school.name.isBlank()) {
            "That JSON file is not a school setup and not a teacher marks file."
        } else {
            val ready = if (file.classes.isNotEmpty() || file.students.isNotEmpty())
                file.copy(settings = file.settings.copy(setupComplete = true)) else file
            state.repo.mutate("SCHOOL_REBUILT", "School", path.fileName.toString()) { ready }
            state.refresh()
            "School restored: " + ready.school.name + ", " + ready.classes.size + " classes, " +
                ready.students.size + " learners, " + ready.subjects.size +
                " subjects. Now import each teacher's marks file."
        }
    }.getOrElse { "That file could not be used: " + (it.message ?: it.javaClass.simpleName) }
}
