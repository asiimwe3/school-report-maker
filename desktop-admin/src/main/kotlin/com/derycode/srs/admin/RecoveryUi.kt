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
    }
}
