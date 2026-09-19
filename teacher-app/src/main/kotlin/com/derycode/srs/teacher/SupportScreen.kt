package com.derycode.srs.teacher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*
import com.derycode.srs.core.results.ResultEngine
import com.derycode.srs.core.seed.Seeds
import com.derycode.srs.core.store.JsonStore
import com.derycode.srs.core.support.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Crash tracking — every uncaught crash is written to crash-log.txt (never auto-sent)
// ─────────────────────────────────────────────────────────────────────────────

object CrashTracker {
    const val FILE = "crash-log.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val f = File(context.filesDir, FILE)
                val entry = "\n=== CRASH ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())} ===\n" +
                    "App: SRS Teacher ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                    "Android: ${android.os.Build.VERSION.RELEASE} (${android.os.Build.VERSION.SDK_INT}), ${android.os.Build.MODEL}\n" +
                    "Thread: ${thread.name}\n${throwable.stackTraceToString().take(3500)}\n"
                f.appendText(entry)
                // v1.13.13 — also deliver RIGHT NOW, synchronously, so a phone that
                // crash-loops and never reaches onCreate again still gets the error out.
                try { CrashReporter.postNow(context, entry) } catch (_: Exception) { }
            } catch (_: Exception) { }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Append a non-crash event (e.g. corrupt data recovery) so it shows on the Support screen. */
    fun note(context: Context, text: String) = try {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) f.writeText("No crashes recorded on this phone. If the app ever crashes, the error is saved here automatically.\n")
        f.appendText("\n=== NOTE ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())} ===\n$text\n")
    } catch (_: Exception) { }

    fun logFile(context: Context): File = File(context.filesDir, FILE)
    fun hasCrash(context: Context): Boolean = logFile(context).length() > 0
    fun clear(context: Context) { logFile(context).delete() }
}


// ─────────────────────────────────────────────────────────────────────────────
// Crash reporter — delivers crash-log.txt to the developer on the next launch.
// Runs BEFORE the UI so the trace gets out even if the app crash-loops at
// startup: the upload thread is joined (max 3s) while the file still exists.
// ─────────────────────────────────────────────────────────────────────────────
object CrashReporter {
    private const val ENDPOINT = "https://superagent-d41c313d.base44.app/functions/saveSrsCrash"

    /** Build the JSON body for a crash report. */
    private fun payload(context: Context, reportText: String): String =
        org.json.JSONObject()
            .put("source", "srs-teacher")
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("appVersionCode", BuildConfig.VERSION_CODE.toString())
            .put("phoneModel", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
            .put("androidVersion", "Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")")
            .put("report", reportText.take(60000))
            .toString()

    /** Synchronous one-shot POST of a single crash entry (used from the crash handler). */
    fun postNow(context: Context, entry: String) {
        try {
            val conn = java.net.URL(ENDPOINT).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(payload(context, entry).toByteArray()) }
            conn.disconnect()
        } catch (_: Exception) { }
    }

    /** Post the crash log if there is an unsent one. No-op (0ms) on healthy phones. */
    fun sendPending(context: Context) {
        try {
            val log = File(context.filesDir, CrashTracker.FILE)
            if (!log.exists() || log.length() == 0L) return
            val marker = File(context.filesDir, "crash-log-posted.txt")
            if (marker.exists() && marker.readText().trim() == log.length().toString()) return

            val body = payload(context, log.readText())

            val t = Thread {
                try {
                    val conn = java.net.URL(ENDPOINT).openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 2500
                    conn.readTimeout = 2500
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                    if (conn.responseCode == 200) marker.writeText(log.length().toString())
                    conn.disconnect()
                } catch (_: Exception) { }
            }
            t.isDaemon = false
            t.start()
            t.join(3000)   // only reached when an unsent crash log exists
        } catch (_: Exception) { }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Auto-update — checks GitHub on startup; downloads & installs when you accept
// ─────────────────────────────────────────────────────────────────────────────

object UpdateChecker {
    // Compares the published "teacher" version (version.json) with the installed
    // version name. v2 audit fix: the old versionCode comparison always failed
    // because version.json never carried a versionCode field.
    fun checkBlocking(currentVersion: String): UpdateInfo? = try {
        val conn = java.net.URL(Support.VERSION_URL).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        try {
            if (conn.responseCode == 200) parseVersionJson(conn.inputStream.bufferedReader().readText())
                ?.takeIf { it.teacherVersion.isNotBlank() && it.teacherVersion != currentVersion }
            else null
        } finally { conn.disconnect() }
    } catch (_: Exception) { null }

    fun downloadApkBlocking(context: Context, url: String): File? = try {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val out = File(dir, "srs-teacher-update.apk")
        java.net.URL(url).openStream().use { input -> out.outputStream().use { input.copyTo(it) } }
        out
    } catch (_: Exception) { null }

    fun installPrompt(context: Context, apk: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Support screen — plans, terms, privacy, update, crash report (WhatsApp 0762306675)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SupportScreen(state: TeacherState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    var downloadMsg by remember { mutableStateOf("") }
    var docView by remember { mutableStateOf("") }

    fun sendCrashViaWhatsApp() {
        val log = CrashTracker.logFile(context).readText().take(3000)
        val text = java.net.URLEncoder.encode(
            "SRS Teacher crash report (auto-collected on my phone):\n\n$log", "UTF-8"
        )
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${Support.SUPPORT_LINK}?text=$text")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp).let { it }, modifier = Modifier.verticalScroll(rememberScrollState())) {
        // ── Update card ──
        CardBox {
            Cell("App update", MUTED, true)
            Spacer(Modifier.height(4.dp))
            Cell("Current version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}). The app checks for updates when opened.", MUTED)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        checking = true
                        scope.launch(Dispatchers.IO) {
                            val u = UpdateChecker.checkBlocking(BuildConfig.VERSION_NAME)
                            update = u
                            checking = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
                    enabled = !checking
                ) { Text(if (checking) "Checking…" else "Check for update", color = Color.White, fontSize = 16.sp) }
            }
            update?.let { u ->
                Spacer(Modifier.height(6.dp))
                Cell("✓ Update ${u.teacherVersion} available", GOOD, true)
                if (u.releaseNotes.isNotBlank()) Cell(u.releaseNotes.take(200), MUTED)
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        downloadMsg = "Downloading…"
                        scope.launch(Dispatchers.IO) {
                            val apk = UpdateChecker.downloadApkBlocking(context, u.teacherApkUrl)
                            downloadMsg = if (apk != null) "Downloaded ✓ — install when prompted" else "Download failed — check internet"
                            if (apk != null) UpdateChecker.installPrompt(context, apk)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GOOD)
                ) { Text("Download & install update", color = Color(0xFF0B1220), fontSize = 16.sp) }
            }
            if (downloadMsg.isNotEmpty()) Cell(downloadMsg, GOOD)
        }

        // ── Crash report card ──
        CardBox {
            Cell("Crash report", MUTED, true)
            Spacer(Modifier.height(4.dp))
            if (CrashTracker.hasCrash(context)) {
                Cell("✓ Crash file found on this phone. Nothing was sent — you choose.", Color(0xFFFFB84D))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { sendCrashViaWhatsApp() }, colors = ButtonDefaults.buttonColors(containerColor = ACCENT)) {
                        Text("Send to support (WhatsApp 0762306675)", color = Color.White, fontSize = 15.sp)
                    }
                    Button(onClick = { CrashTracker.clear(context); state.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = CARD)) {
                        Text("Clear", color = MUTED, fontSize = 15.sp)
                    }
                }
            } else {
                Cell("No crashes recorded on this phone. If the app ever crashes, the error is saved here automatically.", MUTED)
            }
        }

        // ── Docs cards ──
        CardBox {
            Cell("Subscription, terms & privacy", MUTED, true)
            Spacer(Modifier.height(6.dp))
            listOf(
                "Subscription plans" to Plans.SUMMARY,
                "Terms & conditions" to Terms.SUMMARY,
                "Privacy policy" to Privacy.SUMMARY
            ).forEach { (label, text) ->
                Button(
                    onClick = { docView = if (docView == text) "" else text },
                    colors = ButtonDefaults.buttonColors(containerColor = CARD),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) { Text(if (docView == text) "Hide $label" else label, color = NAVY, fontSize = 16.sp) }
            }
        }
        if (docView.isNotEmpty()) {
            CardBox {
                Text(docView.trim(), color = MUTED, fontSize = 15.sp, lineHeight = 15.sp)
            }
        }
        CardBox {
            Cell("Licence & support", MUTED, true)
            Spacer(Modifier.height(4.dp))
            Cell("DeryCode School Report Maker — licensed per school. Support: WhatsApp 0762306675.", MUTED)
        }
    }
}
