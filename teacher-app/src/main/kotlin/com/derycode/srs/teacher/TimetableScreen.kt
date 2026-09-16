package com.derycode.srs.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// My Timetable — the weekly periods set by the admin (cloud pull / setup import)
// ─────────────────────────────────────────────────────────────────────────────

private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun TimetableScreen(state: TeacherState) {
    val d = state.data
    var day by remember { mutableStateOf(
        when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 1; java.util.Calendar.TUESDAY -> 2; java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4; java.util.Calendar.FRIDAY -> 5; java.util.Calendar.SATURDAY -> 6
            else -> 1
        })
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            CardBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAYS.forEachIndexed { i, lbl ->
                        val dv = i + 1
                        FilterChipPill(lbl, selected = day == dv) { day = dv }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val myId = state.me?.id ?: ""
                val mine = d.timetable.filter { it.day == day && (it.teacherId.isBlank() || it.teacherId == myId) }.sortedBy { it.start }
                if (state.me == null) {
                    Cell("Pick who you are on the Home screen first, so we can show only your periods.", MUTED)
                } else if (mine.isEmpty()) {
                    Cell("No periods for you on ${DAYS[day - 1]} yet — the admin sets the timetable. Pull school setup to get updates.", MUTED)
                } else {
                    mine.forEachIndexed { i, p ->
                        if (i > 0) Divider(color = STROKE, modifier = Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(110.dp)) {
                                Cell(p.start, BLUE, true)
                                Cell(p.end, MUTED, false)
                            }
                            Column(Modifier.weight(1f)) {
                                val subj = p.subjectId?.let { sid -> d.subjects.firstOrNull { it.id == sid }?.name }
                                    ?: p.notes.ifBlank { "Lesson" }
                                Cell(subj, bold = true)
                                d.classes.firstOrNull { it.id == p.classId }?.let { Cell(classLabel(it), MUTED, false) }
                            }
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(BLUE.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = BLUE, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            CardBox {
                Cell("Week at a glance", MUTED, true)
                Spacer(Modifier.height(8.dp))
                val myId = state.me?.id ?: ""
                val perDay = (1..6).map { dv -> d.timetable.count { it.day == dv && (it.teacherId.isBlank() || it.teacherId == myId) } }
                if (perDay.sum() == 0) {
                    Cell("Nothing scheduled this week yet.", MUTED)
                } else {
                    perDay.forEachIndexed { i, cnt ->
                        if (cnt > 0) Cell("${DAYS[i]}: $cnt period" + (if (cnt > 1) "s" else ""))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Cell("The admin can change this timetable any time — pull school setup to refresh.", MUTED, false)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
