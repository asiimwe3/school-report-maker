package com.derycode.srs.desktopteacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.support.Support

/**
 * Where a teacher finishes the job: send the marks back, switch teacher, move to
 * another school, or get help. Everything here is a file or a WhatsApp message,
 * so it works with no internet and no account.
 */
@Composable
fun SettingsScreen(state: TeacherStore, onRefresh: () -> Unit) {
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().background(T.BG).verticalScroll(rememberScrollState()).padding(24.dp)) {
        H1("Settings", "Version $APP_VERSION · ${state.data.school.name.ifBlank { "not connected to a school" }}")
        Spacer(Modifier.height(16.dp))

        Card(Modifier.widthIn(max = 760.dp)) {
            Text("Send marks to the admin", color = T.INK, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Writes one small file holding your marks, attendance and any new learners. " +
                    "Copy it to a flash disk, or send it on WhatsApp Web — the console imports it on the Marks screen.",
                color = T.MUTED, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Btn("Export my marks", {
                    val (good, path) = state.exportBundle()
                    ok = good
                    msg = if (good && path != null) "Saved: ${path}" else "There are no marks on this computer yet, so there is nothing to send."
                })
                Spacer(Modifier.width(10.dp))
                Btn("Open folder", { FilePicker.revealInFolder(state.outbox) }, kind = Kind.GHOST)
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.widthIn(max = 760.dp)) {
            Text("Teacher on this computer", color = T.INK, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.me?.name ?: "Not chosen yet", color = T.MUTED, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Btn("Change", { state.setMe(null); onRefresh() }, kind = Kind.GHOST)
            }
            Spacer(Modifier.height(14.dp))
            Text("Moving to another school", color = T.INK, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Use this only if you have left one school for another. The current school's learners and marks " +
                    "are archived first, and nothing is carried across.",
                color = T.MUTED, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Btn("Switch school from file…", {
                val f = FilePicker.open("Open the new school's file", listOf("json"))
                if (f != null) {
                    val (good, m) = state.switchSchool(f)
                    ok = good; msg = m; onRefresh()
                }
            }, kind = Kind.GHOST)
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.widthIn(max = 760.dp)) {
            Text("Help", color = T.INK, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("WhatsApp ${Support.SUPPORT_WHATSAPP} — send a picture of the screen and say what happened.", color = T.MUTED, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row {
                Btn("Message support", { FilePicker.openUrl(Support.SUPPORT_LINK) })
                Spacer(Modifier.width(10.dp))
                Btn("Downloads page", { FilePicker.openUrl(Support.RELEASES_PAGE) }, kind = Kind.GHOST)
            }
            Spacer(Modifier.height(12.dp))
            Text("Files: ${state.dataDir}", color = T.MUTED, fontSize = 11.sp)
            Text("Marks are saved on this computer as you type. A backup copy is kept beside them.", color = T.MUTED, fontSize = 11.sp)
        }

        if (msg.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(msg, color = if (ok) T.OK else T.RUST, fontSize = 13.sp)
        }
    }
}
