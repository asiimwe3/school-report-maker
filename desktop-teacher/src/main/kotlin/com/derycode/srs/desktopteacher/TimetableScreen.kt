package com.derycode.srs.desktopteacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/**
 * The timetable comes from the admin as real periods when they enter them, and
 * as the school's timetable sheet image when they photograph the paper grid.
 * Both are shown here, because a teacher needs whichever one the school has.
 */
@Composable
fun TimetableScreen(state: TeacherStore) {
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val today = days.getOrNull(LocalDate.now().dayOfWeek.value - 1) ?: "Monday"
    val meId = state.me?.id ?: ""
    val mine = state.data.timetable.filter { it.teacherId.isBlank() || it.teacherId == meId }

    Column(Modifier.fillMaxSize().background(T.BG).padding(24.dp)) {
        H1("My timetable", "Today is $today.")
        Spacer(Modifier.height(16.dp))

        val sheet = state.data.timetableSheet
        val hasSheet = sheet != null && sheet.imageBase64.isNotBlank()
        if (hasSheet) {
            Card(Modifier.fillMaxWidth().weight(1f)) {
                Text("School timetable sheet", color = T.INK, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(sheet.fileName.ifBlank { "photographed grid" }, color = T.MUTED, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                val bytes = try { java.util.Base64.getDecoder().decode(sheet.imageBase64) } catch (_: Exception) { null }
                val bmp = bytes?.let {
                    runCatching { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull()
                }
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp,
                        contentDescription = "Timetable",
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Text("The timetable picture could not be read. Ask your admin to send it again.", color = T.RUST, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Card(Modifier.fillMaxWidth().weight(if (hasSheet) 0.8f else 1f)) {
            Text("Your periods", color = T.INK, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (mine.isEmpty()) {
                Text("The admin has not typed the timetable grid yet. If they sent a timetable sheet, it appears above.", color = T.MUTED, fontSize = 12.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(mine.sortedWith(compareBy({ it.day }, { it.start }))) { p ->
                        val cls = state.data.classes.firstOrNull { it.id == p.classId }
                        val subject = p.subjectId?.let { id -> state.data.subjects.firstOrNull { it.id == id }?.name }
                            ?: p.notes.ifBlank { "Lesson" }
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (days.getOrNull(p.day) == today) T.TEAL_SOFT else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(days.getOrNull(p.day) ?: "—", color = T.MUTED, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                            Text("${p.start} – ${p.end}", color = T.INK, fontSize = 12.sp, modifier = Modifier.width(130.dp))
                            Text(subject, color = T.INK, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(cls?.name ?: "", color = T.MUTED, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
