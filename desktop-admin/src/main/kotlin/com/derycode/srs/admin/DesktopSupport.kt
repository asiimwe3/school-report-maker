package com.derycode.srs.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*
import com.derycode.srs.core.support.*
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date

// ─────────────────────────────────────────────────────────────────────────────
// Crash tracking (desktop) — every uncaught crash lands in crash-log.txt
// ─────────────────────────────────────────────────────────────────────────────

object DesktopCrashTracker {
    val logFile: java.nio.file.Path get() = dataDir.resolve("crash-log.txt")

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Files.createDirectories(dataDir)
                val entry = "\n=== CRASH ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())} ===\n" +
                    "App: Admin Console 1.0.0\nOS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}\n" +
                    "Thread: ${thread.name}\n${throwable.stackTraceToString().take(3500)}\n"
                Files.writeString(logFile, Files.exists(logFile).let { if (it) Files.readString(logFile) else "" } + entry)
            } catch (_: Exception) { }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun hasCrash(): Boolean = Files.exists(logFile) && Files.size(logFile) > 0
}

// ─────────────────────────────────────────────────────────────────────────────
// Auto-update (desktop) — checks GitHub on every launch, one click to install
// ─────────────────────────────────────────────────────────────────────────────

object DesktopUpdateChecker {
    const val CURRENT_VERSION = "1.0.0"

    fun check(): UpdateInfo? = try {
        val conn = URL(Support.VERSION_URL).openConnection()
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        val text = conn.getInputStream().bufferedReader().readText()
        parseVersionJson(text)?.takeIf { it.versionName.isNotBlank() && it.versionName != CURRENT_VERSION }
    } catch (_: Exception) { null }

    fun downloadAndRunInstaller(url: String): String {
        return try {
            val dir = dataDir.resolve("updates")
            Files.createDirectories(dir)
            val out = dir.resolve("SchoolReportMaker-update.msi")
            URL(url).openStream().use { input -> Files.copy(input, out) }
            try { Desktop.getDesktop().open(out.toFile()); "Installer downloaded — follow the setup wizard." }
            catch (_: Exception) { "Downloaded to ${out.toAbsolutePath()}. Run it to update." }
        } catch (_: Exception) { "Download failed — check internet, or grab it from the releases page." }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Support & Licence screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SupportScreenDesktop(state: AppState) {
    var update by remember { mutableStateOf<UpdateInfo?>(state.updateAvailable) }
    var msg by remember { mutableStateOf("") }
    var docView by remember { mutableStateOf("") }

    ScreenTitle("Support & Licence", "Updates, crash reports, subscription plans, terms and privacy — all in one place.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        // ── Update ──
        CardBox {
            Text("App update", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Current version: ${DesktopUpdateChecker.CURRENT_VERSION}. A check runs automatically every time the console starts.", color = MUTED, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Check for update now") {
                    Thread { update = DesktopUpdateChecker.check() }.start()
                }
            }
            update?.let { u ->
                Spacer(Modifier.height(6.dp))
                Text("✓ Update ${u.versionName} available", color = GOOD, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (u.releaseNotes.isNotBlank()) Text(u.releaseNotes.take(300), color = MUTED, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Download & run installer") {
                        Thread { msg = DesktopUpdateChecker.downloadAndRunInstaller(u.msiUrl.ifBlank { Support.RELEASES_PAGE }) }.start()
                        msg = "Downloading…"
                    }
                    Btn("Open releases page", primary = false, onClick = {
                        try { Desktop.getDesktop().browse(URI(Support.RELEASES_PAGE)) } catch (_: Exception) { }
                    })
                }
            } ?: Text("You are up to date." + if (update == null && msg == "checked") "" else "", color = MUTED, fontSize = 12.sp)
            if (msg.isNotEmpty() && msg != "Downloading…") Text(msg, color = GOOD, fontSize = 12.sp)
        }

        // ── Crash report ──
        CardBox {
            Text("Crash report", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (DesktopCrashTracker.hasCrash()) {
                Text("✓ Crash file found. Nothing was sent anywhere — you choose.", color = Color(0xFFFFB84D), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Send to support (WhatsApp 0762306675)") {
                        val log = Files.readString(DesktopCrashTracker.logFile).take(3000)
                        val text = URLEncoder.encode("School Report Maker (Admin Console) crash report:\n\n$log", "UTF-8")
                        try { Desktop.getDesktop().browse(URI("${Support.SUPPORT_LINK}?text=$text")) } catch (_: Exception) { }
                    }
                    Btn("Clear crash file", primary = false, onClick = { Files.deleteIfExists(DesktopCrashTracker.logFile); state.refresh() })
                }
            } else {
                Text("No crashes recorded on this computer. If the console ever crashes, the error is saved automatically.", color = MUTED, fontSize = 12.sp)
            }
        }

        // ── Docs ──
        CardBox {
            Text("Subscription, terms & privacy", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "Subscription plans" to Plans.SUMMARY,
                    "Terms & conditions" to Terms.SUMMARY,
                    "Privacy policy" to Privacy.SUMMARY
                ).forEach { (label, text) ->
                    Btn(if (docView == text) "Hide" else label, primary = docView != text, onClick = {
                        docView = if (docView == text) "" else text
                    })
                }
            }
        }
        if (docView.isNotEmpty()) {
            CardBox {
                Text(docView.trim(), color = MUTED, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        CardBox {
            Text("Licence", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("DeryCode School Report Maker is licensed per school. Support: WhatsApp 0762306675. The software and all school data stay on your computers — nothing is hosted online.", color = MUTED, fontSize = 12.sp)
        }
    }
}
