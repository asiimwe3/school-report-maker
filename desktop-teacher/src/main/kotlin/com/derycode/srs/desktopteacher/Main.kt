package com.derycode.srs.desktopteacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.derycode.srs.core.support.Support
import com.derycode.srs.core.support.UpdateInfo
import kotlinx.serialization.json.Json

/**
 * DeryCode School Report Maker — the teacher app for a laptop.
 *
 * Same data, same files and the same rules as the phone app, for the staff
 * rooms where the marks are actually typed: a DOS office laptop, a library
 * desktop, a borrowed machine. No internet is required at any point.
 */
fun main() = application {
    val state = remember { TeacherStore() }
    var screen by remember { mutableStateOf("auto") }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        Thread {
            update = DesktopTeacherUpdates.check(APP_VERSION)
        }.start()
    }

    val connected = state.connected && state.meId != null
    LaunchedEffect(connected) { if (screen == "auto") screen = if (connected) "marks" else "connect" }

    Window(
        onCloseRequest = ::exitApplication,
        title = "School Report Maker — Teacher",
        state = rememberWindowState(width = 1180.dp, height = 780.dp)
    ) {
        Row(Modifier.fillMaxSize().background(T.BG)) {
            // rail
            Column(
                Modifier.width(212.dp).fillMaxHeight().background(T.DEEP).padding(vertical = 20.dp, horizontal = 14.dp)
            ) {
                Text("DeryCode", color = T.GOLD, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Teacher", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.connected) state.data.school.name.ifBlank { "school not named" } else "no school yet",
                    color = Color(0xFF9FB3AC), fontSize = 11.sp
                )
                Spacer(Modifier.height(22.dp))
                NavItem("Marks", screen == "marks") { screen = "marks" }
                NavItem("Attendance", screen == "attendance") { screen = "attendance" }
                NavItem("My classes", screen == "classes") { screen = "classes" }
                NavItem("Timetable", screen == "timetable") { screen = "timetable" }
                NavItem("Settings", screen == "settings") { screen = "settings" }
                Spacer(Modifier.weight(1f))
                if (state.me != null) {
                    Text(state.me!!.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${state.myClasses.size} classes", color = Color(0xFF9FB3AC), fontSize = 11.sp)
                } else {
                    Text("No teacher picked", color = Color(0xFF9FB3AC), fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text("v$APP_VERSION", color = Color(0xFF6D7D75), fontSize = 10.sp)
            }

            Column(Modifier.weight(1f)) {
                if (update != null) {
                    val u = update!!
                    Row(
                        Modifier.fillMaxWidth().background(T.TEAL_SOFT).padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "A newer teacher app is available (${u.teacherDesktop.ifBlank { u.teacher }}).",
                            color = T.TEAL, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                        )
                        Btn("Download", {
                            FilePicker.openUrl(
                                u.teacherDesktopDownload.ifBlank { Support.RELEASES_PAGE }
                            )
                        }, modifier = Modifier.height(34.dp))
                        Spacer(Modifier.width(8.dp))
                        Btn("Later", { update = null }, kind = Kind.GHOST, modifier = Modifier.height(34.dp))
                    }
                }
                when {
                    !state.connected || state.meId == null -> ConnectScreen(state) {
                        screen = "marks"
                    }
                    screen == "marks" -> MarksScreen(state)
                    screen == "attendance" -> AttendanceScreen(state)
                    screen == "classes" -> ClassesScreen(state)
                    screen == "timetable" -> TimetableScreen(state)
                    else -> SettingsScreen(state) { screen = if (state.meId == null) "connect" else "marks" }
                }
            }
        }
    }
}

@Composable
private fun NavItem(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (active) Color(0xFF1E4B43) else Color.Transparent, RoundedCornerShape(9.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            color = if (active) Color.White else Color(0xFFBFD0CA),
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Reads the same version.json the console and the phone use. */
object DesktopTeacherUpdates {
    fun check(current: String): UpdateInfo? = try {
        val conn = java.net.URL(Support.VERSION_URL).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
        try {
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                Json { ignoreUnknownKeys = true }.decodeFromString<UpdateInfo>(body)
                    .takeIf { it.teacherDesktop.isNotBlank() && it.teacherDesktop != current }
            } else null
        } finally { conn.disconnect() }
    } catch (_: Exception) { null }
}
