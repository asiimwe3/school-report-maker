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
                    "App: Admin Console $APP_VERSION\nOS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}\n" +
                    "Thread: ${thread.name}\n${throwable.stackTraceToString().take(3500)}\n"
                Files.writeString(logFile, Files.exists(logFile).let { if (it) Files.readString(logFile) else "" } + entry)
            } catch (e: Exception) { e.printStackTrace() }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun hasCrash(): Boolean = Files.exists(logFile) && Files.size(logFile) > 0
}

// ─────────────────────────────────────────────────────────────────────────────
// Auto-update (desktop) — checks GitHub on every launch, one click to install
// ─────────────────────────────────────────────────────────────────────────────

/** Shared flag so the top-bar pill can re-open the update prompt after "Later". */
object UpdatePrompt {
    var open by androidx.compose.runtime.mutableStateOf(false)
}

object DesktopUpdateChecker {
    const val CURRENT_VERSION = APP_VERSION   // audit fix: was hardcoded "2.0.0"

    /** One small JSON fetch. Cache-busted so a fresh release is seen immediately. */
    fun check(): UpdateInfo? = try {
        val stamp = System.currentTimeMillis()
        val conn = URL("${Support.VERSION_URL}?t=$stamp").openConnection()
        conn.connectTimeout = 12000; conn.readTimeout = 12000
        conn.setRequestProperty("Cache-Control", "no-cache")
        val text = conn.getInputStream().bufferedReader().readText()
        parseVersionJson(text)?.takeIf {
            it.consoleVersion.isNotBlank() && isNewerVersion(it.consoleVersion, CURRENT_VERSION)
        }
    } catch (_: Exception) { null }

    /**
     * Downloads the installer and runs it, exactly like the phone app updates
     * itself. [onProgress] reports megabytes so the admin sees it moving on a
     * slow school connection instead of a dead button.
     */
    fun downloadAndRunInstaller(url: String, onProgress: (String) -> Unit = {}): String {
        return try {
            val dir = dataDir.resolve("updates")
            Files.createDirectories(dir)
            val out = dir.resolve("SchoolReportMaker-update.msi")
            Files.deleteIfExists(out)
            Files.deleteIfExists(dir.resolve("update-status.txt"))

            val conn = URL(url).openConnection()
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            val total = conn.contentLengthLong
            onProgress(if (total > 0) "Downloading… 0 of ${total / 1048576} MB" else "Downloading…")
            conn.getInputStream().use { input ->
                Files.newOutputStream(out).use { fos ->
                    val buf = ByteArray(64 * 1024)
                    var got = 0L
                    var lastShown = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                        got += n
                        val mb = (got / 1048576).toInt()
                        if (mb != lastShown) { lastShown = mb; onProgress(if (total > 0) "Downloading… $mb of ${total / 1048576} MB" else "Downloading… $mb MB") }
                    }
                }
            }
            if (Files.size(out) < 1_000_000) {
                onProgress("The download stopped early — trying the releases page instead.")
                openReleasesPage()
                return "Download was incomplete. The releases page is open — download the .msi there."
            }

            if (!System.getProperty("os.name").lowercase().contains("win")) {
                return try {
                    Desktop.getDesktop().open(out.toFile())
                    "Installer downloaded — close this app, then follow the setup wizard."
                } catch (_: Exception) { "Downloaded to ${out.toAbsolutePath()}. Run it to update." }
            }

            // Windows Installer cannot replace files this console is running, so hand the
            // job to a small script that waits for us to exit, installs, and — if Windows
            // refuses because an older product registration is still there — removes that
            // registration and installs again. It then reopens the console for the admin.
            val script = dir.resolve("apply-update.bat")
            val msi = out.toAbsolutePath().toString()
            val log = dir.resolve("update-install.log").toAbsolutePath().toString()
            val status = dir.resolve("update-status.txt").toAbsolutePath().toString()
            val product = installedProductCode()
            val exe = System.getProperty("jpackage.app-path") ?: ""
            val batText =
                "@echo off\r\n" +
                "timeout /t 3 /nobreak >nul\r\n" +
                "msiexec /i \"$msi\" /qb /norestart /l*v \"$log\"\r\n" +
                "if not errorlevel 1 goto ok\r\n" +
                (if (product.isNullOrBlank()) "" else
                    "msiexec /x $product /qb /norestart >>\"$log\" 2>&1\r\n" +
                    "msiexec /i \"$msi\" /qb /norestart /l*v \"$log\"\r\n") +
                "if not errorlevel 1 goto ok\r\n" +
                "echo FAILED>\"$status\"\r\n" +
                "goto reopen\r\n" +
                ":ok\r\n" +
                "echo OK>\"$status\"\r\n" +
                ":reopen\r\n" +
                (if (exe.isBlank()) "" else "start \"\" \"$exe\"\r\n")
            Files.writeString(script, batText)
            ProcessBuilder("cmd", "/c", script.toAbsolutePath().toString())
                .redirectErrorStream(true).start()
            onProgress("Installing… the console closes now and reopens by itself. Your data is safe.")
            Thread {
                Thread.sleep(1200)
                Runtime.getRuntime().halt(0)
            }.start()
            "Installing — this console closes and reopens automatically. Your data is safe."
        } catch (e: Exception) {
            System.err.println("update failed: ${e.message}")
            openReleasesPage()
            "Could not download the update (${e.message?.take(60) ?: "network"}). The releases page is open — get the .msi there."
        }
    }

    /** The product code Windows has registered for this app, if any (used when an upgrade is refused). */
    fun installedProductCode(): String? = try {
        val roots = listOf(
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall"
        )
        for (root in roots) {
            val p = ProcessBuilder("reg", "query", root, "/s", "/f", "SchoolReportMaker")
                .redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            p.waitFor()
            text.lineSequence().map { it.trim() }
                .firstOrNull { it.startsWith("HKEY") && it.contains("\\Uninstall\\{") }
                ?.substringAfterLast("\\")?.takeIf { it.startsWith("{") }?.let { return it }
        }
        null
    } catch (_: Exception) { null }

    /** What the last update attempt did, so a refusal is never silent. */
    fun lastUpdateResult(): String? {
        val status = dataDir.resolve("updates").resolve("update-status.txt")
        if (!Files.exists(status)) return null
        return try {
            val v = Files.readString(status).trim()
            if (v.startsWith("OK")) null else
                "The last update did not finish. Installer log: ${dataDir.resolve("updates").resolve("update-install.log")}"
        } catch (_: Exception) { null }
    }

    /** Fallback so an admin is never stuck: opens the download page in their browser. */
    fun openReleasesPage() {
        try { Desktop.getDesktop().browse(URI(Support.RELEASES_PAGE)) } catch (e: Exception) { e.printStackTrace() }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Update ──
        CardBox {
            Text("App update", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Current version: ${DesktopUpdateChecker.CURRENT_VERSION}. A check runs automatically every time the console starts.", color = Theme.MUTED, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Check for update now") {
                    Thread { update = DesktopUpdateChecker.check() }.start()
                }
            }
            update?.let { u ->
                Spacer(Modifier.height(6.dp))
                Text("✓ Update ${u.consoleVersion} available", color = Theme.GOOD, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (u.releaseNotes.isNotBlank()) Text(u.releaseNotes.take(300), color = Theme.MUTED, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Download & run installer") {
                        Thread { msg = DesktopUpdateChecker.downloadAndRunInstaller(u.consoleMsiUrl.ifBlank { Support.RELEASES_PAGE }) }.start()
                        msg = "Downloading…"
                    }
                    Btn("Open releases page", primary = false, onClick = {
                        try { Desktop.getDesktop().browse(URI(Support.RELEASES_PAGE)) } catch (e: Exception) { e.printStackTrace() }
                    })
                }
            } ?: Text("You are up to date." + if (update == null && msg == "checked") "" else "", color = Theme.MUTED, fontSize = 14.sp)
            if (msg.isNotEmpty() && msg != "Downloading…") Text(msg, color = Theme.GOOD, fontSize = 14.sp)
        }

        // ── Crash report ──
        CardBox {
            Text("Crash report", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (DesktopCrashTracker.hasCrash()) {
                Text("✓ Crash file found. Nothing was sent anywhere — you choose.", color = Color(0xFFFFB84D), fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Send to support (WhatsApp 0762306675)") {
                        val log = Files.readString(DesktopCrashTracker.logFile).take(3000)
                        val text = URLEncoder.encode("School Report Maker (Admin Console) crash report:\n\n$log", "UTF-8")
                        try { Desktop.getDesktop().browse(URI("${Support.SUPPORT_LINK}?text=$text")) } catch (e: Exception) { e.printStackTrace() }
                    }
                    Btn("Clear crash file", primary = false, onClick = { Files.deleteIfExists(DesktopCrashTracker.logFile); state.refresh() })
                }
            } else {
                Text("No crashes recorded on this computer. If the console ever crashes, the error is saved automatically.", color = Theme.MUTED, fontSize = 14.sp)
            }
        }

        // ── Docs ──
        CardBox {
            Text("Subscription, terms & privacy", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                Text(docView.trim(), color = Theme.MUTED, fontSize = 14.sp, lineHeight = 17.sp)
            }
        }
        CardBox {
            Text("Licence", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("DeryCode School Report Maker is licensed per school. Support: WhatsApp 0762306675. The software and all school data stay on your computers — nothing is hosted online.", color = Theme.MUTED, fontSize = 14.sp)
        }
    }
}
