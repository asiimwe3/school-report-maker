package com.derycode.srs.desktopteacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path

/**
 * Getting started, in the order a teacher actually does it: the admin hands over
 * a file, the teacher opens it here, then picks their name off the staff list.
 * No internet, no account, no code to type.
 */
@Composable
fun ConnectScreen(state: TeacherStore, onPicked: () -> Unit) {
    var picked by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().background(T.BG).verticalScroll(rememberScrollState()).padding(32.dp)) {
        Text("School Report Maker", color = T.TEAL, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        H1(
            if (state.connected) "Signed in on this computer" else "Set up this computer",
            if (state.connected) "${state.data.school.name} — ${state.myClasses.size} of your classes, ${state.leaders()} learners."
            else "Two steps: open the school file from your admin, then pick your name."
        )
        Spacer(Modifier.height(22.dp))

        if (!state.connected) {
            Card(Modifier.widthIn(max = 720.dp)) {
                Text("1. The school file", color = T.INK, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "On the admin computer: Sync / Export school setup, then copy the file here " +
                        "(flash disk, WhatsApp Web, email). It holds your classes, learners, subjects and timetable — not marks.",
                    color = T.MUTED, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Btn("Open school file…", {
                        val f = FilePicker.open("Open the school file from your admin", listOf("json"))
                        if (f != null) {
                            val (good, m) = state.importSchoolSetup(f)
                            msg = m; ok = good
                            picked = f.toString()
                        }
                    })
                    Spacer(Modifier.width(12.dp))
                    if (picked.isNotBlank()) Text(Path.of(picked).fileName.toString(), color = T.MUTED, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            Card(Modifier.widthIn(max = 720.dp)) {
                Text("Ask your admin for a fresh school file when classes or learners change.", color = T.MUTED, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Row {
                    Btn("Update school file…", {
                        val f = FilePicker.open("Open the school file from your admin", listOf("json"))
                        if (f != null) {
                            val (good, m) = state.importSchoolSetup(f)
                            msg = m; ok = good
                        }
                    }, kind = Kind.GHOST)
                    Spacer(Modifier.width(10.dp))
                    Text(state.dataDir.toString(), color = T.MUTED, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (state.connected) {
            Card(Modifier.widthIn(max = 720.dp)) {
                Text("2. Who are you?", color = T.INK, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Your classes, subjects and marks all follow this choice.", color = T.MUTED, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                val teachers = state.data.teachers.filter { it.active }.sortedBy { it.name }
                if (teachers.isEmpty()) {
                    Text("This school file has no staff list yet. Ask your admin to add teachers in the console.", color = T.RUST, fontSize = 13.sp)
                }
                var q by remember { mutableStateOf("") }
                Tf(q, { q = it }, "Search your name", Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                val shown = teachers.filter { q.isBlank() || it.name.contains(q, true) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shown.take(12).forEach { t ->
                        val selected = state.meId == t.id
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (selected) T.TEAL_SOFT else T.CARD, RoundedCornerShape(9.dp))
                                .border(1.dp, if (selected) T.TEAL else T.LINE, RoundedCornerShape(9.dp))
                                .clickableNoRipple {
                                    state.setMe(t.id)
                                    onPicked()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(t.name, color = T.INK, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(
                                t.roleTitle.ifBlank { t.role.name.lowercase().replaceFirstChar { ch -> ch.uppercase() } },
                                color = T.MUTED, fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        if (msg.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(msg, color = if (ok) T.OK else T.RUST, fontSize = 13.sp, modifier = Modifier.widthIn(max = 720.dp))
        }
    }
}

private fun TeacherStore.leaders(): Int = data.students.count { s -> s.status.name == "ACTIVE" }
