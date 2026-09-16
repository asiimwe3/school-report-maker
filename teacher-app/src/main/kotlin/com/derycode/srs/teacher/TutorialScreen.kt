package com.derycode.srs.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// How-to Guide — step-by-step training slides, fully offline
// (the same content as the SRS Teacher training deck)
// ─────────────────────────────────────────────────────────────────────────────

private data class Step(val title: String, val sub: String, val points: List<String>, val tip: String = "")

private val STEPS = listOf(
    Step("Join your school — once", "You need one thing from the head teacher: your code.",
        listOf("Install the app (APK) and open it",
            "Tap Join school, enter your name and the code",
            "Pick your role (Teacher, Head Teacher, Admin, Data Entry)",
            "Your account is created automatically — no email, no password",
            "The school's classes, students and subjects download to your phone"),
        "The code is one-time — if it fails, ask the head teacher for a fresh one."),
    Step("Pick who you are", "One tap, right after joining.",
        listOf("On the Home screen, tap \u201CPick who you are\u201D",
            "Choose your name from the staff list",
            "Now the app shows only your classes and your periods"),
        "Doing this first makes every other screen show only your data."),
    Step("Enter marks offline", "No network needed — marks stay on your phone.",
        listOf("Classes → pick your class → pick the subject",
            "Type each mark; ABS for absent, EXEMPT for exempted",
            "Saved instantly on the phone",
            "Send everything to the school with one button (see Send school data)"),
        ""),
    Step("Daily attendance", "More → Attendance.",
        listOf("Pick your class, tap Present / Absent / Late per student",
            "Defaults to all-present so you only mark the exceptions",
            "The head teacher sees it in the console after sending"),
        ""),
    Step("Teacher on duty", "More → Duty Desk.",
        listOf("Write the duty log for your day on duty",
            "Issue gate passes — who left, when, reason",
            "All of it goes to the school console with one send"),
        "The app shows you when you are on duty — set by the admin."),
    Step("Your week: Timetable & Calendar", "More → My Timetable.",
        listOf("Mon–Sat tabs with your periods, times and classes",
            "Home shows Today's Schedule",
            "Calendar events from the admin: term dates, exams, closures"),
        ""),
    Step("Send school data", "More → Cloud Sync → Send school data.",
        listOf("Needs network — it is the only time you need internet",
            "Uploads your marks, attendance, duty logs and gate passes",
            "The head teacher taps \u201CPull Teacher Submissions\u201D and it lands in the school records",
            "After sending you can keep working offline"),
        "Send once a day, or at least every Friday."),
    Step("Get updates from the school", "More → Cloud Sync → Pull school setup.",
        listOf("Downloads the latest classes, students, subjects, timetable, duty roster and calendar events",
            "Do it after the head teacher changes anything",
            "You are here — this guide is always available under More → How-to Guide"),
        "")
)

@Composable
fun TutorialScreen(state: TeacherState) {
    var i by remember { mutableStateOf(0) }
    val s = STEPS[i]
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            CardBox {
                Text("How-to Guide", color = NAVY, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("SRS Teacher training — the whole app in 8 steps.", color = MUTED, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    STEPS.indices.forEach { idx ->
                        Box(Modifier.size(8.dp).background(if (idx <= i) BLUE else STROKE, RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
        item {
            CardBox {
                Text("${i + 1}. ${s.title}", color = NAVY, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(s.sub, color = MUTED, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                s.points.forEach { p ->
                    Text("\u2022  $p", color = Color(0xFF1B2540), fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp))
                }
                if (s.tip.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("TIP — ${s.tip}", color = ORANGE, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().background(ORANGE.copy(alpha = 0.10f), RoundedCornerShape(8.dp)).padding(10.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(enabled = i > 0, container = STROKE, content = NAVY, label = "\u2039 Back") { if (i > 0) i-- }
                Text("Step ${i + 1} of ${STEPS.size}", color = MUTED, fontSize = 13.sp)
                Pill(enabled = i < STEPS.size - 1, container = BLUE, content = Color.White,
                    label = if (i == STEPS.size - 1) "Finish \u2713" else "Next \u203A") { if (i < STEPS.size - 1) i++ }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun Pill(enabled: Boolean, container: Color, content: Color, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (enabled) container else STROKE.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) { Text(label, color = if (enabled) content else MUTED, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}
