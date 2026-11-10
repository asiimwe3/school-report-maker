package com.derycode.srs.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.store.SchoolRecovery
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date

/**
 * "My school setup is gone" — find it again.
 *
 * Everything the console has ever saved lives as JSON on this machine: the data
 * folder, its rotating backups, the laptop teacher app, and any file copied off a
 * USB stick. This panel reads them all and offers back the newest one that still
 * holds learners, so a head teacher never has to retype a school.
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

    // Never read a folder on the drawing thread: a big folder or an unreadable
    // OneDrive placeholder made the console look frozen. Scan in the background.
    var candidates by remember { mutableStateOf<List<SchoolRecovery.Candidate>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var rescan by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(state.refreshTick, rescan) {
        scanning = true
        candidates = withContext(Dispatchers.IO) { runCatching { SchoolRecovery.scan(roots, setOf(dataFile)) }.getOrDefault(emptyList()) }
        scanning = false
    }
    val fmt = SimpleDateFormat("MMM d, yyyy HH:mm")
    val hint = state.data.school.name.takeIf { it.isNotBlank() && it != "Your School" } ?: ""

    fun restore(file: Path) {
        val ok = state.repo.restore(file)
        if (ok) {
            state.refresh()
            state.refresh()
            rescan++
            val name = state.data.school.name.ifBlank { "your school" }
            msg = "Restored $name. Check Students and Classes to confirm everything is back."
            onRestored(name)
        } else {
            msg = "That file could not be read. Try the one above or below it."
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (scanning) "Searching this computer for saved copies…" else
                 "Found ${candidates.size} saved cop${if (candidates.size == 1) "y" else "ies"} on this computer",
                color = if (scanning) Theme.MUTED else if (candidates.isEmpty()) Theme.WARN else Theme.GOOD,
                fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Btn("Look again", primary = false) { rescan++ }
        }
        SchoolRecovery.best(candidates, hint)?.let { best ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Newest full copy: ${best.school}, ${best.classes} classes, ${best.students} learners, saved ${fmt.format(Date(best.savedAt))}",
                    color = Theme.TEXT, fontSize = 13.sp)
                Btn("Restore this copy") { restore(best.file) }
            }
        }
        if (candidates.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("School", color = Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(190.dp))
                Text("Classes", color = Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                Text("Learners", color = Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(75.dp))
                Text("Marks", color = Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(65.dp))
                Text("Saved", color = Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(160.dp))
                Text("From", color = Theme.MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            candidates.take(12).forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(c.school, color = Theme.TEXT, fontSize = 13.sp, modifier = Modifier.width(190.dp))
                    Text(c.classes.toString(), color = Theme.TEXT, fontSize = 13.sp, modifier = Modifier.width(70.dp))
                    Text(c.students.toString(), color = Theme.TEXT, fontSize = 13.sp, modifier = Modifier.width(75.dp))
                    Text(c.marks.toString(), color = Theme.TEXT, fontSize = 13.sp, modifier = Modifier.width(65.dp))
                    Text(fmt.format(Date(c.savedAt)), color = Theme.MUTED, fontSize = 12.sp, modifier = Modifier.width(160.dp))
                    Text(c.origin, color = Theme.MUTED, fontSize = 12.sp, modifier = Modifier.width(120.dp))
                    Btn("Restore", primary = false) { restore(c.file) }
                }
            }
        } else if (!scanning) {
            Text("No saved copy with learners was found here. On the school's own computer, a copy is created after every change; if this is a new machine, copy the school-data.json (or a backup) from the old one into ${dataDir} and press Look again.",
                color = Theme.MUTED, fontSize = 12.sp)
        }
        if (msg.isNotBlank()) Text(msg, color = Theme.GOOD, fontSize = 13.sp)
    }
}
