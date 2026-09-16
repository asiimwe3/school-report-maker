package com.derycode.srs.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.CalendarEventType
import java.text.SimpleDateFormat
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Home dashboard — big, bold, bright cards (light theme)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(state: TeacherState, onOpenClass: (String) -> Unit, onQuickAction: (Route) -> Unit) {
    val d = state.data
    val greeting = greetingWord()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            GreetingCard(greeting, state.me?.let { "Mr./Mrs. ${it.name}" } ?: "Teacher")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Modifier.weight(1f), Icons.Filled.MenuBook, BLUE, state.myClasses.size.toString(), "My Classes")
                StatCard(Modifier.weight(1f), Icons.Filled.People, GREEN, d.students.size.toString(), "Total Students")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Modifier.weight(1f), Icons.Filled.MenuBook, PURPLE, d.subjects.count { it.active }.toString(), "Subjects")
                StatCard(Modifier.weight(1f), Icons.Filled.EditNote, ORANGE, state.pendingCount.toString(), "Pending Marks")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard(Modifier.weight(1f), "Marks Entry", "Enter and manage marks for your classes and subjects.", Icons.Filled.Assignment, BLUE) { onQuickAction(Route.Assessments) }
                FeatureCard(Modifier.weight(1f), "Class Comments", "Write end-of-term remarks for your students.", Icons.Filled.EditNote, PURPLE) { onQuickAction(Route.Comments) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard(Modifier.weight(1f), "Students", "View students by class, stream and level.", Icons.Filled.People, GREEN) { onQuickAction(Route.Students(null)) }
                FeatureCard(Modifier.weight(1f), "Attendance", "Daily register \u2014 present, absent or late.", Icons.Filled.EventAvailable, TEAL) { onQuickAction(Route.Attendance) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard(Modifier.weight(1f), "My Classes", "View & manage all your classes.", Icons.Filled.School, PINK) { onQuickAction(Route.Classes) }
                FeatureCard(Modifier.weight(1f), "More Tools", "Sync, cloud, duty desk & support.", Icons.Filled.MoreHoriz, ORANGE) { onQuickAction(Route.More) }
            }
        }
        item {
            CardBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(BLUE.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, tint = BLUE, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Cell("Today's Schedule", bold = true)
                    }
                    Text("View all", color = BLUE, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onQuickAction(Route.Timetable) })
                }
                Spacer(Modifier.height(10.dp))
                if (state.todaysSchedule.isEmpty()) {
                    Cell("No classes assigned yet \u2014 import school data or ask the admin.", MUTED)
                } else {
                    state.todaysSchedule.forEachIndexed { i, item ->
                        if (i > 0) Divider(color = STROKE, modifier = Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Cell(item.time, MUTED, false)
                                Cell("${item.subject}  (${item.classLabel})", bold = true)
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MUTED, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        item {
            CardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(PURPLE.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = PURPLE, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Cell("School Calendar", bold = true)
                }
                Spacer(Modifier.height(10.dp))
                val today = state.todayKey
                val upcoming = d.calendarEvents.filter { it.date >= today }.sortedWith(compareBy({ it.date }, { it.id })).take(6)
                if (upcoming.isEmpty()) {
                    Cell("No upcoming events — ask your admin to set the school calendar.", MUTED)
                } else {
                    upcoming.forEachIndexed { i, ev ->
                        if (i > 0) Divider(color = STROKE, modifier = Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Cell(prettyDate(ev.date), MUTED, false)
                                Cell(ev.title, bold = true)
                                if (ev.notes.isNotBlank()) Cell(ev.notes, MUTED, false)
                            }
                            Box(Modifier.background(eventColor(ev.type).copy(alpha = 0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(eventLabel(ev.type), color = eventColor(ev.type), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        if (d.teachers.isEmpty() || state.meId == null) {
            item {
                CardBox {
                    Cell("Teaching as", MUTED, true)
                    Spacer(Modifier.height(8.dp))
                    if (d.teachers.isEmpty()) {
                        Cell("No teachers loaded yet. Import the school setup from More \u2192 Sync, or ask your admin to add you.", MUTED)
                    } else {
                        Cell("Pick who you are so you see only your classes:", MUTED)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            d.teachers.take(4).forEach { t ->
                                Button(onClick = { state.setMe(t.id) }, colors = ButtonDefaults.buttonColors(containerColor = BLUE)) {
                                    Text(t.name, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SchoolFooterBar(state)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private fun greetingWord(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun GreetingCard(greeting: String, name: String) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BLUE.copy(alpha = 0.12f))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83D\uDC4B", fontSize = 20.sp)
                Spacer(Modifier.width(6.dp))
                Text("$greeting, $name", color = NAVY, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text("Let's make teaching and reporting easier.", color = MUTED, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.size(52.dp).clip(CircleShape).background(BLUE.copy(alpha = 0.20f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.School, contentDescription = null, tint = BLUE, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, icon: ImageVector, color: Color, value: String, label: String) {
    Row(
        modifier
            .shadow(2.dp, RoundedCornerShape(18.dp), ambientColor = STROKE, spotColor = STROKE)
            .clip(RoundedCornerShape(18.dp))
            .background(CARD)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, color = NAVY, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun FeatureCard(modifier: Modifier, title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        modifier
            .shadow(2.dp, RoundedCornerShape(20.dp), ambientColor = STROKE, spotColor = STROKE)
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(color), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = NAVY, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
    }
}

@Composable
private fun SchoolFooterBar(state: TeacherState) {
    val d = state.data
    val year = d.academicYears.firstOrNull()
    val cls = state.myClasses.firstOrNull()
    val online = state.cloud.signedIn
    Row(
        Modifier.fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = STROKE, spotColor = STROKE)
            .clip(RoundedCornerShape(16.dp))
            .background(CARD)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(BLUE.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.School, contentDescription = null, tint = BLUE, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(d.school.name.ifBlank { "SRS Teacher" }, color = NAVY, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                listOfNotNull(cls?.let { classShort(it) }, year?.year?.ifBlank { null }?.let { "$it Academic Year" })
                    .joinToString("  \u00B7  "),
                color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
            )
        }
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).background(if (online) GREEN else MUTED.copy(alpha = 0.18f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(if (online) "Online" else "Offline", color = if (online) Color.White else MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun prettyDate(iso: String): String {
    return try {
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.US)
        val parsed = inFmt.parse(iso)
        if (parsed != null) outFmt.format(parsed) else iso
    } catch (_: Exception) { iso }
}

private fun eventLabel(t: CalendarEventType): String = when (t) {
    CalendarEventType.EXAM -> "EXAM"
    CalendarEventType.HOLIDAY -> "HOLIDAY"
    CalendarEventType.MEETING -> "MEETING"
    CalendarEventType.DEADLINE -> "DEADLINE"
    CalendarEventType.TERM_START -> "TERM START"
    CalendarEventType.TERM_END -> "TERM END"
    CalendarEventType.OTHER -> "EVENT"
}

private fun eventColor(t: CalendarEventType): Color = when (t) {
    CalendarEventType.EXAM -> ORANGE
    CalendarEventType.HOLIDAY -> GREEN
    CalendarEventType.MEETING -> BLUE
    CalendarEventType.DEADLINE -> RED
    CalendarEventType.TERM_START, CalendarEventType.TERM_END -> TEAL
    CalendarEventType.OTHER -> PURPLE
}
