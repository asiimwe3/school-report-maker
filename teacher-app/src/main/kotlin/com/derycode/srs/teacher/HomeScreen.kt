package com.derycode.srs.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Home dashboard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(state: TeacherState, onOpenClass: (String) -> Unit, onQuickAction: (Route) -> Unit) {
    val d = state.data
    val greeting = greetingWord()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("$greeting,", color = MUTED, fontSize = 15.sp)
            Text(state.me?.let { "Mr./Mrs. ${it.name}" } ?: "Teacher", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Teach \u00B7 Guide \u00B7 Build the future", color = MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
        }
        item {
            Row(Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(16.dp)).padding(vertical = 14.dp)) {
                StatItem(Modifier.weight(1f), d.students.size.toString(), "Students")
                Divider(color = STROKE)
                StatItem(Modifier.weight(1f), d.subjects.count { it.active }.toString(), "Subjects")
                Divider(color = STROKE)
                StatItem(Modifier.weight(1f), state.myClasses.size.toString(), "My Classes")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard(Modifier.weight(1f), "My Classes", "View & manage your classes", Icons.Filled.MenuBook, PURPLE) { onQuickAction(Route.Classes) }
                QuickActionCard(Modifier.weight(1f), "Assessments", "Create & manage tests and marks", Icons.Filled.Assignment, TEAL) { onQuickAction(Route.Assessments) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard(Modifier.weight(1f), "Student Records", "View student performance", Icons.Filled.BarChart, PINK) { onQuickAction(Route.Students(null)) }
                QuickActionCard(Modifier.weight(1f), "Reports", "Generate & export reports", Icons.Filled.Description, ORANGE) { onQuickAction(Route.More) }
            }
        }
        item {
            CardBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = BLUE, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Cell("Today's Schedule", bold = true)
                    }
                    Text("View all", color = BLUE, fontSize = 13.sp, modifier = Modifier.clickable { onQuickAction(Route.Classes) })
                }
                Spacer(Modifier.height(8.dp))
                if (state.todaysSchedule.isEmpty()) {
                    Cell("No classes assigned yet \u2014 import school data or ask the admin.", MUTED)
                } else {
                    state.todaysSchedule.forEachIndexed { i, item ->
                        if (i > 0) Divider(color = STROKE, modifier = Modifier.padding(vertical = 6.dp))
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
        if (d.teachers.isEmpty() || state.meId == null) {
            item {
                CardBox {
                    Cell("Teaching as", MUTED, true)
                    Spacer(Modifier.height(6.dp))
                    if (d.teachers.isEmpty()) {
                        Cell("No teachers loaded yet. Import the school setup from More \u2192 Sync, or ask your admin to add you.", MUTED)
                    } else {
                        Cell("Pick who you are so you see only your classes:", MUTED)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            d.teachers.take(4).forEach { t ->
                                Button(onClick = { state.setMe(t.id) }, colors = ButtonDefaults.buttonColors(containerColor = BLUE)) {
                                    Text(t.name, fontSize = 13.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
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
private fun StatItem(modifier: Modifier, value: String, label: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MUTED, fontSize = 13.sp)
    }
}

@Composable
private fun QuickActionCard(modifier: Modifier, title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CARD)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.20f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = MUTED, fontSize = 12.sp, lineHeight = 13.sp)
    }
}
