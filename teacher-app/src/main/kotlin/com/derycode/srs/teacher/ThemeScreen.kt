package com.derycode.srs.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Themes screen — one tap restyles the whole phone: background, cards, text,
 * buttons, badges, bars and the bottom navigation. The school's console choice
 * still applies until a teacher picks their own.
 */
@Composable
fun ThemeScreen(state: TeacherState) {
    val following = state.themeChoice.isNullOrBlank()
    val active = state.phoneTheme

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BLUE.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Palette, contentDescription = null, tint = BLUE, modifier = Modifier.size(21.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Look & feel", color = NAVY, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Seven palettes. Every screen follows the one you pick.", color = MUTED, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("In use now: $active", color = BLUE, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Follow the school ──
        item {
            CardBox {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        state.setThemeOverride(null)
                        applyPhoneTheme(state.data.settings.theme)
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Follow the school theme", color = NAVY, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Uses whatever the Admin console set (${state.data.settings.theme.ifBlank { "Editorial" }}). " +
                                "Pick this when your head teacher wants every phone to match.",
                            color = MUTED, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    if (following) Ticked()
                }
            }
        }

        // ── The seven palettes ──
        items(THEME_CHOICES) { choice ->
            val p = phonePalette(choice.name)
            val on = !following && active.equals(choice.name, ignoreCase = true)
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(p.bg)
                    .border(if (on) 2.dp else 1.dp, if (on) p.accent else p.stroke, RoundedCornerShape(20.dp))
                    .clickable {
                        state.setThemeOverride(choice.name)
                        applyPhoneTheme(choice.name)
                    }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(choice.name, color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (on) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = p.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("IN USE", color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(choice.blurb, color = p.muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))

                // live sample: a card with ink, a figure, and an accent button
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(p.card)
                            .border(1.dp, p.stroke, RoundedCornerShape(12.dp)).padding(11.dp)
                    ) {
                        Text("MATHEMATICS", color = p.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("78%", color = p.ink, fontSize = 21.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(6.dp))
                            Text("B+", color = p.teal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(p.accent).padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Enter", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("marks", color = Color.White, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(p.accent, p.teal, p.green, p.orange, p.red, p.purple).forEach { c ->
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(c))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (p.dark) "Dark" else "Light",
                        color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        item {
            CardBox {
                Text("Why a phone theme?", color = NAVY, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Teachers mark at night, in the staff room and in the sun. Midnight is easier on the eyes after dark, " +
                        "Ivory and Editorial read best in daylight. Your choice stays on this phone and never changes the " +
                        "school's reports.",
                    color = MUTED, fontSize = 13.sp
                )
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun Ticked() {
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(GREEN.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.Check, contentDescription = "Selected", tint = GREEN, modifier = Modifier.size(15.dp)) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile — who you are in this school, and what you carry
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(state: TeacherState, onOpenThemes: () -> Unit) {
    val d = state.data
    val me = state.me
    val myClassIds = state.myClasses.map { it.id }.toSet()
    val learners = d.enrollments.filter { it.classId in myClassIds }.map { it.studentId }.distinct().size
    val mySubjects = myClassIds.flatMap { cid -> state.mySubjectsFor(cid) }.distinctBy { it.id }
    val dutyRecords = d.dutyRecords.filter { it.teacherId == me?.id }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(me?.name ?: "Teacher", BLUE, 54)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(me?.name ?: "No teacher selected", color = NAVY, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            me?.roleTitle?.takeIf { it.isNotBlank() } ?: (me?.role?.name?.replace('_', ' ') ?: "Teacher"),
                            color = BLUE, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                        Text(d.school.name.ifBlank { "SRS Teacher" }, color = MUTED, fontSize = 13.sp)
                    }
                }
                if (me != null && me.duties.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        me.duties.forEach { FilterChipPill(it, selected = false) {} }
                    }
                }
                if (!me?.phone.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Phone: ${me?.phone}", color = MUTED, fontSize = 14.sp)
                }
            }
        }

        item {
            CardBox {
                Text("My load this term", color = NAVY, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("Classes", state.myClasses.size.toString(), BLUE),
                        Triple("Learners", learners.toString(), TEAL),
                        Triple("Subjects", mySubjects.size.toString(), PURPLE)
                    ).forEach { (label, value, tone) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(tone.copy(alpha = 0.12f))
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(value, color = tone, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text(label, color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (state.myClasses.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Classes", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(4.dp))
                    state.myClasses.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name + (if (c.stream.isBlank()) "" else " \u00B7 ${c.stream}"), color = NAVY, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text("${state.studentsIn(c.id).size} learners", color = MUTED, fontSize = 13.sp)
                        }
                    }
                }
                if (mySubjects.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Subjects taught", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        mySubjects.take(6).forEach { s -> FilterChipPill(s.name, selected = false) {} }
                    }
                }
            }
        }

        item {
            CardBox {
                Text("Settings", color = NAVY, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Divider(color = STROKE)
                Spacer(Modifier.height(6.dp))
                ProfileRow("Theme", "In use: ${state.phoneTheme}${if (state.themeChoice.isNullOrBlank()) " (school)" else ""}", onOpenThemes)
                ProfileRow("Duty records", "${dutyRecords.size} entries", null)
                ProfileRow("App version", BuildConfig.VERSION_NAME.let { "SRS Teacher $it" }, null)
                ProfileRow("Offline", "Marks stay on this phone until you sync", null)
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun ProfileRow(title: String, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = NAVY, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MUTED, fontSize = 13.sp)
        }
        if (onClick != null) Text("Open", color = BLUE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
