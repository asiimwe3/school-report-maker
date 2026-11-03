package com.derycode.srs.admin

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import org.jetbrains.skia.Image
import androidx.compose.ui.window.application
import com.derycode.srs.core.model.scopedView
import com.derycode.srs.core.model.*
import com.derycode.srs.core.seed.Seeds
import com.derycode.srs.core.store.JsonStore
import com.derycode.srs.core.store.SchoolRepository
import java.nio.file.Path

// ─────────────────────────────────────────────────────────────────────────────
// Theme
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Editorial design system (v2.2.26 console redesign)
// Cream paper, deep-green sidebar, gold crest, teal progress — mirrors
// design/redesign/_group.css. Legacy theme names in saved settings map onto
// this palette so every existing install gets the new look, zero migration.
// ─────────────────────────────────────────────────────────────────────────────

internal object Theme {
    // ── Full-app palette tokens ──────────────────────────────────────────────
    // Every screen paints from these tokens, so switching theme restyles the
    // WHOLE console: page background, cards, sidebar, text, lines and accents.
    var NAVY by mutableStateOf(Color(0xFFF4F0E7))        // page background (cream)
    var CARD by mutableStateOf(Color(0xFFFBFAF7))        // card surface (paper)
    var ACCENT by mutableStateOf(Color(0xFF237B71))      // primary action
    var ACCENT_SOFT by mutableStateOf(Color(0xFFDFF0ED))
    var TEXT by mutableStateOf(Color(0xFF18352E))        // ink
    var MUTED by mutableStateOf(Color(0xFF6D7D75))
    var GOOD by mutableStateOf(Color(0xFF237B71))
    var WARN by mutableStateOf(Color(0xFFA85E48))
    var INK by mutableStateOf(Color(0xFF18352E))
    val GOLD_BASE = Color(0xFFC59A4A)
    val RUST_BASE = Color(0xFFA85E48)
    val TEAL_BASE = Color(0xFF237B71)
    var HIGHLIGHT by mutableStateOf(GOLD_BASE)
    var DEEP by mutableStateOf(Color(0xFF112C26))        // sidebar
    val GOLD get() = HIGHLIGHT
    var GOLD_SOFT by mutableStateOf(Color(0xFFEFE2C3))
    var TEAL by mutableStateOf(Color(0xFF237B71))
    var TEAL_SOFT by mutableStateOf(Color(0xFFDFF0ED))
    var RUST by mutableStateOf(Color(0xFFA85E48))
    var RUST_SOFT by mutableStateOf(Color(0xFFF4DFD8))
    var LINE by mutableStateOf(Color(0xFFD7DDD5))
    /** True while a dark palette is active — used where a screen needs to know. */
    var DARK by mutableStateOf(false)

    /** One complete look for the whole app. */
    data class Palette(
        val bg: Color, val card: Color, val text: Color, val muted: Color, val line: Color,
        val sidebar: Color, val accent: Color, val accentSoft: Color,
        val highlight: Color, val highlightSoft: Color,
        val good: Color, val warn: Color, val dark: Boolean = false
    )

    private val PALETTES = linkedMapOf(
        "Editorial" to Palette(
            bg = Color(0xFFF4F0E7), card = Color(0xFFFBFAF7), text = Color(0xFF18352E),
            muted = Color(0xFF6D7D75), line = Color(0xFFD7DDD5), sidebar = Color(0xFF112C26),
            accent = Color(0xFF237B71), accentSoft = Color(0xFFDFF0ED),
            highlight = Color(0xFFC59A4A), highlightSoft = Color(0xFFEFE2C3),
            good = Color(0xFF237B71), warn = Color(0xFFA85E48)
        ),
        "Ivory" to Palette(
            bg = Color(0xFFFBF8F1), card = Color(0xFFFFFFFF), text = Color(0xFF2A2A28),
            muted = Color(0xFF7A7770), line = Color(0xFFE4E0D6), sidebar = Color(0xFF2E2E2B),
            accent = Color(0xFF8C6A2F), accentSoft = Color(0xFFF3E9D5),
            highlight = Color(0xFFB08A3E), highlightSoft = Color(0xFFEFE1C4),
            good = Color(0xFF2F7D5B), warn = Color(0xFFB05A3C)
        ),
        "Forest" to Palette(
            bg = Color(0xFFEDF3EE), card = Color(0xFFFBFDFB), text = Color(0xFF132A1D),
            muted = Color(0xFF5F7166), line = Color(0xFFCFDDD3), sidebar = Color(0xFF0C2317),
            accent = Color(0xFF1F7A43), accentSoft = Color(0xFFD9EBDD),
            highlight = Color(0xFFB9A145), highlightSoft = Color(0xFFEDE6C9),
            good = Color(0xFF1F7A43), warn = Color(0xFF9E5B3A)
        ),
        "Ocean" to Palette(
            bg = Color(0xFFEEF3F8), card = Color(0xFFFFFFFF), text = Color(0xFF14273A),
            muted = Color(0xFF5E7189), line = Color(0xFFCFDBE7), sidebar = Color(0xFF0C1E30),
            accent = Color(0xFF1F6DA8), accentSoft = Color(0xFFDCEAF6),
            highlight = Color(0xFF3E8FB8), highlightSoft = Color(0xFFDCEDF5),
            good = Color(0xFF1F8A6F), warn = Color(0xFFB0603C)
        ),
        "Plum" to Palette(
            bg = Color(0xFFF5F0F6), card = Color(0xFFFFFFFF), text = Color(0xFF2B1830),
            muted = Color(0xFF75657C), line = Color(0xFFE0D4E4), sidebar = Color(0xFF241029),
            accent = Color(0xFF7A3E8C), accentSoft = Color(0xFFF0E1F4),
            highlight = Color(0xFFB06FC0), highlightSoft = Color(0xFFF1E2F5),
            good = Color(0xFF2F7D5B), warn = Color(0xFFA85E48)
        ),
        "Midnight" to Palette(
            bg = Color(0xFF11161C), card = Color(0xFF1A2129), text = Color(0xFFE9EEF4),
            muted = Color(0xFF93A1B1), line = Color(0xFF2B3540), sidebar = Color(0xFF0B0F14),
            accent = Color(0xFF3FA897), accentSoft = Color(0xFF1D3330),
            highlight = Color(0xFFDFB25A), highlightSoft = Color(0xFF2C2718),
            good = Color(0xFF52C79E), warn = Color(0xFFE08A6B), dark = true
        ),
        "Midnight Gold" to Palette(
            bg = Color(0xFF16130E), card = Color(0xFF211C15), text = Color(0xFFF3EDE1),
            muted = Color(0xFFA79B86), line = Color(0xFF37301F), sidebar = Color(0xFF0E0C08),
            accent = Color(0xFFC99A45), accentSoft = Color(0xFF352A16),
            highlight = Color(0xFFDDB35E), highlightSoft = Color(0xFF352A16),
            good = Color(0xFF6FBF8F), warn = Color(0xFFE08A6B), dark = true
        )
    )

    val THEMES: List<String> get() = PALETTES.keys.toList()

    /** Small swatch used in Settings so a theme can be seen before applying it. */
    fun previewBg(name: String): Color = PALETTES[name]?.bg ?: NAVY
    fun previewAccent(name: String): Color = PALETTES[name]?.accent ?: ACCENT

    fun apply(name: String) {
        val key = PALETTES.keys.firstOrNull { it.equals(name.trim(), true) }
            ?: if (name.contains("midnight", true)) "Midnight" else "Editorial"   // legacy "Dark" keeps the current look
        val p = PALETTES.getValue(key)
        NAVY = p.bg
        CARD = p.card
        TEXT = p.text
        INK = p.text
        MUTED = p.muted
        LINE = p.line
        DEEP = p.sidebar
        ACCENT = p.accent
        ACCENT_SOFT = p.accentSoft
        HIGHLIGHT = p.highlight
        GOLD_SOFT = p.highlightSoft
        GOOD = p.good
        WARN = p.warn
        TEAL = p.accent
        TEAL_SOFT = p.accentSoft
        RUST = p.warn
        RUST_SOFT = p.dark
            .let { if (it) Color(0xFF3A241C) else Color(0xFFF4DFD8) }
        DARK = p.dark
    }
}


internal val LocalSchoolLevel = androidx.compose.runtime.compositionLocalOf { Level.PRIMARY }

/** Opens a file dialog, copies the chosen image into the data folder as logo/stamp, returns the new path. */
private fun pickAsset(kind: String, onPicked: (String) -> Unit) {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select $kind image", java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") } }
    dialog.isVisible = true
    val chosen = dialog.file
    val dir = dialog.directory
    if (chosen != null && dir != null) {
        try {
            val src = java.nio.file.Path.of(dir, chosen)
            val assetsDir = dataDir.resolve("assets")
            java.nio.file.Files.createDirectories(assetsDir)
            val ext = chosen.substringAfterLast('.', "png")
            val dest = assetsDir.resolve("$kind.$ext")
            java.nio.file.Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            onPicked(dest.toAbsolutePath().toString())
        } catch (e: Exception) { e.printStackTrace() }
    }
}

internal val dataDir: Path = Path.of(System.getProperty("user.home"), "SchoolReportMaker")
internal val dataFile: Path = dataDir.resolve("school-data.json")

// ─────────────────────────────────────────────────────────────────────────────
// App state
// ─────────────────────────────────────────────────────────────────────────────

class AppState(val repo: SchoolRepository) {
    var unlocked by mutableStateOf(false)
    var screen by mutableStateOf("dashboard")
    var refreshTick by mutableStateOf(0)
    /** Which levels this school actually runs (Primary / Secondary / Combined). */
    val scope: SchoolScope get() = SchoolScope.of(data.school)
    /** School level chosen in the top bar. Primary classes never mix with secondary. */
    var level by mutableStateOf(Level.PRIMARY)
    /** Active classes of the chosen level only, always inside the school's levels. */
    fun levelClasses() = data.classes.filter { it.active && it.level == scope.coerce(level) }
    /** Every active class the school runs, across its levels (secondary: S1–S6). */
    fun scopeClasses() = scope.classes(data.classes)
    var updateAvailable by mutableStateOf<com.derycode.srs.core.support.UpdateInfo?>(null)

    // Observable snapshot of repo.data — every screen reading state.data
    // recomposes whenever refresh() is called. Without this, saves were
    // written to disk but screens never re-read them (the "not taking data" bug).
    private var rawData by mutableStateOf(repo.data)

    /**
     * The school's working data, already narrowed to the school category.
     * A secondary school never sees Primary classes, subjects, learners,
     * timetable periods or marks anywhere, because every screen reads this
     * one view. Nothing is deleted: [repo].data still holds everything, and
     * all writes go to the repository, so switching category brings it back.
     */
    val data: SchoolData get() = scopedView(rawData)

    fun refresh() {
        rawData = repo.data
        refreshTick++
        CloudSync.scheduleAutoPush(repo)   // debounced cloud auto-backup after every save
    }
}

fun main() = application {
    DesktopCrashTracker.install()
    val store = JsonStore()
    val repo = SchoolRepository(store, dataFile, deviceId = "admin-desktop")
    repo.seedIfNeeded(Seeds.ALL_SUBJECTS, Seeds.COMPONENTS)

    Theme.apply(repo.data.settings.theme.ifBlank { "Dark" })
    // v2.2.35 — name the default templates after the school, not generic words
    run {
        val generic = setOf("Classic", "Modern", "Compact", "Plain")   // "Standard" keeps its name: it is the school's own card
        val sname = repo.data.school.name.ifBlank { "School" }
        if (repo.data.templates.any { it.name in generic }) {
            repo.mutate("TEMPLATES_AUTONAMED", "ReportTemplate") { dd ->
                dd.copy(templates = dd.templates.map { if (it.name in generic) it.copy(name = "$sname ${it.name}") else it })
            }
        }
    }
    val state = remember { AppState(repo) }
    // A secondary school starts on O-level, a primary school on Primary: the
    // school's own ladder decides, never a level the school does not run.
    LaunchedEffect(state.data.school.category) { state.level = state.scope.coerce(state.level) }
    Thread { state.updateAvailable = DesktopUpdateChecker.check() }.start()   // auto-check on launch
    // Auto-connect: on launch, pull every teacher who has joined via an invite
    // code into the local teachers list (they stay connected until disconnected).
    if (repo.data.settings.cloudSchoolId.isNotBlank() && repo.data.settings.setupComplete) {
        CloudSync.syncLinkedTeachers(repo) { ok, _ -> if (ok) state.refresh() }
    }
    val windowIcon = remember {
        try {
            val bytes = Thread.currentThread().contextClassLoader?.getResourceAsStream("icon.png")?.readBytes()
            if (bytes != null) BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap()) else null
        } catch (_: Exception) { null }
    }

    Window(
        title = "DeryCode School Report Maker — Admin Console",
        icon = windowIcon,
        state = rememberWindowState(placement = WindowPlacement.Maximized),
        onCloseRequest = ::exitApplication
    ) {
        // ── Launch screen: brand navy + logo until the console paints ──
        var splash by remember { mutableStateOf(true) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1100)
            splash = false
        }
        Box(Modifier.fillMaxSize()) {
            App(state)
            if (splash) LaunchSplash(windowIcon)
        }
    }
}

/** Full-window launch screen: the DeryCode logo on the brand navy, so the
 *  console opens on brand instead of a grey window. */
@Composable
private fun LaunchSplash(logo: androidx.compose.ui.graphics.painter.Painter?) {
    val navy = Color(0xFF021951)
    Box(Modifier.fillMaxSize().background(navy), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (logo != null) {
                Image(logo, contentDescription = null, modifier = Modifier.size(200.dp))
            } else {
                Text("DERYCODE", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Text("School Report Maker", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Admin Console", color = Color(0xFF9CC3FF), fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Text("SIMPLE · FAST · RELIABLE", color = Color(0xFF6E9BE0), fontSize = 11.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
fun App(state: AppState) {
    // ── Update prompt: ask, don't just hint ──
    var askUpdate by remember { mutableStateOf(state.updateAvailable != null) }
    LaunchedEffect(UpdatePrompt.open) { if (UpdatePrompt.open) { askUpdate = true; UpdatePrompt.open = false } }
    var updateMsg by remember { mutableStateOf("") }
    val upd = state.updateAvailable
    if (upd != null && askUpdate) {
        AlertDialog(
            onDismissRequest = { askUpdate = false },
            title = { Text("Update available", color = Theme.TEXT, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("School Report Maker ${upd.consoleVersion} is ready (you have ${com.derycode.srs.admin.DesktopUpdateChecker.CURRENT_VERSION}).",
                        color = Theme.TEXT, fontSize = 14.sp)
                    if (upd.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(upd.releaseNotes.take(400), color = Theme.MUTED, fontSize = 12.sp)
                    }
                    Text("Your school data stays exactly as it is.", color = Theme.MUTED, fontSize = 12.sp)
                    if (updateMsg.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(updateMsg, color = Theme.ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton({
                    // Stay open and show progress: the download runs here, then the
                    // installer starts and this console closes itself.
                    updateMsg = "Downloading…"
                    Thread {
                        val res = runCatching {
                            DesktopUpdateChecker.downloadAndRunInstaller(upd.consoleMsiUrl.ifBlank { com.derycode.srs.core.support.Support.RELEASES_PAGE }) { line -> updateMsg = line }
                        }
                        updateMsg = res.getOrElse { "Update failed: ${it.message?.take(80)}" }
                    }.start()
                }) { Text(if (updateMsg.startsWith("Downloading")) "Downloading…" else "Update now", color = Theme.ACCENT, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton({ askUpdate = false }) { Text("Later", color = Theme.MUTED) } }
        )
    }
    // ── First-run gate (v2.2.0): mandatory setup wizard before dashboard ──
    if (!state.data.settings.setupComplete) {
        SetupWizard(state)
        return
    }
    // ── Security gate (Section 12): PIN lock on startup ──
    if (state.data.settings.adminPinHash.isNotBlank() && !state.unlocked) {
        PinLockScreen(state)
        return
    }
    // ── School category gate (v2.3.3): an install that never said what kind of
    //    school it is must say so once, so Primary never shows in a secondary school. ──
    if (state.data.school.category.isBlank()) {
        SchoolCategoryPrompt(state)
        return
    }
    val navGroups = listOf(
        "OVERVIEW" to listOf("dashboard" to "Dashboard"),
        "SCHOOL" to listOf("setup" to "School Setup", "students" to "Students",
            "classes" to "Classes & Streams", "subjects" to "Subjects", "teachers" to "Teachers"),
        "ACADEMICS" to listOf("marks" to "Marks Grid", "grading" to "Grading Schemes",
            "results" to "Results Review", "analytics" to "Analytics", "attendance" to "Attendance", "promotion" to "Promotion & Rollover", "fees" to "School Fees", "calendar" to "Calendar", "timetable" to "Timetable"),
        "REPORTS" to listOf("reports" to "Reports", "templates" to "Templates", "commentbank" to "Comment Bank",
            "preview" to "Report Preview", "archive" to "Report Archive"),
        "PLANS" to listOf("plans" to "Plans & Pricing"),
        "SYSTEM" to listOf("sync" to "Sync & Backup", "cloud" to "Cloud & Online", "users" to "Users",
            "notifications" to "Notifications", "backups" to "Backup History",
            "importexport" to "Import / Export", "audit" to "Audit Log", "settings" to "Settings"),
        "HELP" to listOf("docs" to "Help & Documentation", "support" to "Support & Licence")
    )
    // Big & easy to use: 1.25x scale on everything (text, buttons, rows, spacing) together.
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
            density = baseDensity.density * 1.25f, fontScale = baseDensity.fontScale
        )
    ) {
    Row(Modifier.fillMaxSize().background(Theme.NAVY)) {
        // ── Sidebar (Editorial: deep green, gold crest) ──
        Column(
            Modifier.width(250.dp).fillMaxHeight().background(Theme.DEEP)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 22.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val initials = state.data.school.name.split(" ").filter { it.isNotBlank() }
                .take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
                .ifBlank { "SR" }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                val brand = remember {
                    try {
                        val bytes = Thread.currentThread().contextClassLoader?.getResourceAsStream("icon.png")?.readBytes()
                        if (bytes != null) BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap()) else null
                    } catch (_: Exception) { null }
                }
                if (brand != null) {
                    Image(
                        brand, contentDescription = "DeryCode School Report Maker",
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
                    )
                } else {
                    Box(
                        Modifier.size(38.dp).background(Theme.GOLD, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initials, color = Theme.DEEP, fontWeight = FontWeight.Black, fontSize = 15.sp, fontFamily = FontFamily.Serif)
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("School Report", color = Color(0xFFE8EFE8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Maker · Admin console", color = Color(0xFF9DB6AB), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            navGroups.forEach { (group, entries) ->
                Text(group, color = Color(0xFF6F9386), fontSize = 12.sp, fontWeight = FontWeight.Black,
                    style = TextStyle(letterSpacing = 1.3.sp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                entries.forEach { (key, label) ->
                    val selected = state.screen == key
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .background(if (selected) Theme.GOLD else Color.Transparent, RoundedCornerShape(9.dp))
                            .clickable { state.screen = key }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(label, color = if (selected) Theme.DEEP else Color(0xFFACC1B8), fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = Color(0x1EFFFFFF), modifier = Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                val adminName = state.data.school.headTeacher.ifBlank { "Administrator" }
                val adminInitials = adminName.split(" ").filter { it.isNotBlank() }
                    .take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("").ifBlank { "A" }
                Box(Modifier.size(30.dp).background(Color(0xFFD6A15A), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Text(adminInitials, color = Theme.DEEP, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(adminName.take(22), color = Color(0xFFE6EEE9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Administrator · Offline account", color = Color(0xFF83A297), fontSize = 9.sp)
                }
            }
        }

        // ── Content ──
        // v2.2.1 fix: this MUST be a Column, not a Box. Every screen composes
        // ScreenTitle() followed by its body as two siblings expecting to be
        // stacked vertically. A Box stacks children on top of each other
        // (z-order) instead of one below the other — that was the cause of
        // titles/subtitles visually overlapping with the screen content below them.
        // Screens used to be placed in a fixed-height Column.  A number of them
        // are longer than a laptop-sized window, so their bottom controls were
        // clipped (and could appear to overlap the content above).  Keep the
        // navigation fixed and give the complete page one reliable scroll area.
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(state)
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 24.dp)
            ) {
                androidx.compose.runtime.CompositionLocalProvider(LocalSchoolLevel provides state.level) {
                when (state.screen) {
                    "dashboard" -> DashboardScreen(state)
                    "setup" -> SetupScreen(state)
                    "students" -> StudentsScreen(state)
                    "classes" -> ClassesScreen(state)
                    "subjects" -> SubjectsScreen(state)
                    "teachers" -> TeachersScreen(state)
                    "marks" -> MarksScreen(state)
                    "fees" -> FeesScreen(state)
                    "grading" -> GradingScreen(state)
                    "results" -> ResultsScreen(state)
                    "analytics" -> AnalyticsScreen(state)
                    "attendance" -> AttendanceScreen(state)
                    "promotion" -> PromotionScreen(state)
                    "commentbank" -> CommentBankScreen(state)
                    "reports" -> ReportsScreen(state)
                    "sync" -> SyncScreen(state)
                    "cloud" -> CloudScreen(state)
                    "templates" -> TemplatesScreen(state)
                    "preview" -> PreviewScreen(state)
                    "audit" -> AuditScreen(state)
                    "archive" -> ReportsArchiveScreen(state)
                    "backups" -> BackupHistoryScreen(state)
                    "users" -> UsersScreen(state)
                    "notifications" -> NotificationsScreen(state)
                    "calendar" -> CalendarScreen(state)
                    "timetable" -> TimetableScreen(state)
                    "importexport" -> ImportExportScreen(state)
                    "settings" -> SettingsScreen(state)
                    "plans" -> PlansScreen(state)
                    "docs" -> HelpDocsScreen(state)
                    "support" -> SupportScreenDesktop(state)
                }
                }
            }
        }
    }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI helpers
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Top bar (Editorial): school kicker, page title, sync pill
// ─────────────────────────────────────────────────────────────────────────────

private val SCREEN_LABELS: Map<String, String> = mapOf(
    "dashboard" to "Overview", "setup" to "School Setup", "students" to "Students",
    "classes" to "Classes & Streams", "subjects" to "Subjects", "teachers" to "Teachers",
    "marks" to "Marks Grid", "grading" to "Grading Schemes", "results" to "Results Review",
    "analytics" to "Analytics", "attendance" to "Attendance", "promotion" to "Promotion & Rollover",
    "fees" to "School Fees", "calendar" to "Calendar", "timetable" to "Timetable",
    "reports" to "Reports", "templates" to "Templates", "commentbank" to "Comment Bank",
    "preview" to "Report Preview", "archive" to "Report Archive", "plans" to "Plans & Pricing",
    "sync" to "Sync & Backup", "cloud" to "Cloud & Online", "users" to "Users",
    "notifications" to "Notifications", "backups" to "Backup History",
    "importexport" to "Import / Export", "audit" to "Audit Log", "settings" to "Settings",
    "docs" to "Help & Documentation", "support" to "Support & Licence"
)

private fun screenLabel(key: String): String = SCREEN_LABELS[key] ?: "Admin console"

@Composable
private fun TopBar(state: AppState) {
    val schoolName = state.data.school.name.ifBlank { "Set up your school profile" }
    val year = state.data.academicYears.firstOrNull { it.currentTermId != null } ?: state.data.academicYears.firstOrNull()
    val term = year?.terms?.firstOrNull { it.id == year.currentTermId } ?: year?.terms?.firstOrNull()
    val kicker = listOfNotNull(schoolName, year?.year?.let { y -> term?.let { "Term ${it.number} · $y" } })
        .joinToString("  ·  ").uppercase().ifBlank { schoolName.uppercase() }
    Row(
        Modifier.fillMaxWidth().background(Theme.CARD.copy(alpha = 0.94f))
            .border(1.dp, Theme.LINE)
            .padding(horizontal = 36.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(kicker, color = Theme.MUTED, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                style = TextStyle(letterSpacing = 1.4.sp), maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(screenLabel(state.screen), color = Theme.INK, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        // ── Update ready pill: one click to download and run, like the phone app ──
        state.updateAvailable?.let { u ->
            Row(
                Modifier.background(Theme.ACCENT, RoundedCornerShape(12.dp))
                    .clickable { UpdatePrompt.open = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("\u2193", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("Update ${u.consoleVersion}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
        }
        // ── Old cloud backend: teachers cannot reach this console's codes ──
        if (state.data.settings.cloudSchoolId.isNotBlank() && CloudSync.onOldCloud(state.data.settings)) {
            Row(
                Modifier.background(Theme.WARN.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                    .border(1.dp, Theme.WARN.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { state.screen = "cloud" }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Old cloud — tap to move", color = Theme.WARN, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
        }
        // ── School level switch. A secondary school only offers O-Level (S1–S4)
        //    and A-Level (S5–S6); a primary school only offers Primary. ──
        val scopeLevels = state.scope.levels
        if (scopeLevels.size > 1) {
            Row(
                Modifier.background(Theme.ACCENT_SOFT, RoundedCornerShape(14.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                scopeLevels.forEach { lvl ->
                    val on = state.level == state.scope.coerce(lvl)
                    Column(
                        Modifier
                            .background(if (on) Theme.ACCENT else Color.Transparent, RoundedCornerShape(11.dp))
                            .clickable { state.level = lvl }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            when (lvl) {
                                Level.PRIMARY -> "Primary"
                                Level.O_LEVEL -> "O-Level"
                                Level.A_LEVEL -> "A-Level"
                            },
                            fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            color = if (on) Color.White else Theme.ACCENT
                        )
                        Text(
                            lvl.shortRange(),
                            fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = if (on) Color(0xFFD9E6FF) else Theme.MUTED
                        )
                    }
                }
            }
        } else {
            Row(
                Modifier.background(Theme.ACCENT_SOFT, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    scopeLevels.firstOrNull()?.let { if (it == Level.PRIMARY) "Primary" else "Secondary" } ?: "School",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Theme.ACCENT
                )
                Text(
                    if (state.scope.isSecondaryOnly) "S1–S6" else "P1–P7",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Theme.MUTED
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Row(
            Modifier.background(Theme.TEAL_SOFT, RoundedCornerShape(20.dp))
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).background(Theme.TEAL, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(7.dp))
            Text("Saved locally", color = Theme.TEAL, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier.size(33.dp).border(1.dp, Theme.LINE, RoundedCornerShape(9.dp))
                .clickable { state.screen = "notifications" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) { Icon(Icons.Filled.Notifications, "Notifications", Modifier.size(15.dp), tint = Theme.MUTED) }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String = "") {
    Column(Modifier.padding(bottom = 22.dp)) {
        if (subtitle.isNotBlank()) Text(
            subtitle.uppercase(), color = Theme.MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            style = TextStyle(letterSpacing = 1.4.sp)
        )
        Text(
            title, color = Theme.INK, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif, modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Theme.CARD, RoundedCornerShape(16.dp))
            .border(1.dp, Theme.LINE, RoundedCornerShape(16.dp)).padding(22.dp),
        content = content
    )
}

@Composable
fun StatCard(label: String, value: String, tint: Color = Theme.ACCENT, detail: String = "") {
    Column(
        Modifier.background(Theme.CARD, RoundedCornerShape(14.dp))
            .border(1.dp, Theme.LINE, RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        Text(label, color = Theme.MUTED, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(7.dp))
        Text(value, color = tint, fontSize = 27.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(detail, color = Theme.MUTED, fontSize = 14.sp)
        }
    }
}

@Composable
fun Row3(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun FieldLabel(text: String) = Text(text, color = Theme.MUTED, fontSize = 16.sp, fontWeight = FontWeight.Medium)

@Composable
fun TextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "") {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = Theme.MUTED, fontSize = 16.sp) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = Theme.TEXT, fontSize = 17.sp)
    )
}

@Composable
fun Btn(label: String, primary: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        colors = if (primary) ButtonDefaults.buttonColors(containerColor = Theme.ACCENT, contentColor = Color.White)
        else ButtonDefaults.buttonColors(containerColor = Theme.ACCENT_SOFT, contentColor = Theme.TEXT)
    ) { Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun HeaderCell(text: String) = Text(text, color = Theme.MUTED, fontSize = 15.sp, fontWeight = FontWeight.Bold)

@Composable
fun Cell(text: String, bold: Boolean = false, color: Color = Theme.TEXT) =
    Text(text, color = color, fontSize = 16.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)

@Composable
fun ErrorText(msg: String) = Text(msg, color = Theme.WARN, fontSize = 16.sp)

/**
 * Class picker chips grouped by level (Primary / O-Level / A-Level) that WRAP
 * onto the next line instead of sliding sideways — screens stay clean and the
 * full list is always visible.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ClassPickerChips(classes: List<SchoolClass>, selectedId: String, onSelect: (String) -> Unit) {
    val lvlNow = LocalSchoolLevel.current
    Level.entries.filter { it == lvlNow }.forEach { lvl ->
        val inLevel = classes.filter { it.level == lvl }
        if (inLevel.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Text(levelLabel(lvl), color = Theme.ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    inLevel.forEach { c ->
                        FilterChip(
                            selected = selectedId == c.id,
                            onClick = { onSelect(c.id) },
                            label = { Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screens — setup & dashboard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SetupScreen(state: AppState) {
    val d = state.data
    var school by remember { mutableStateOf(d.school) }
    var error by remember { mutableStateOf("") }
    var savedMsg by remember { mutableStateOf("") }

    ScreenTitle("School Setup", "Offline school account — everything stays on this computer")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {  // v2.2.15 fix: unbounded LazyColumn inside verticalScroll crashed on open
        // ── School category: decides which levels the whole console runs ──
        CardBox {
            Text("School category", color = Theme.ACCENT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("This decides which classes, students, subjects, teachers and timetable periods the console shows. A secondary school runs Senior 1 → Senior 6 only, split O-level (S1–S4) and A-level (S5–S6). Nothing is deleted when you switch — classes outside the choice are simply hidden and come back intact.", color = Theme.MUTED, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SchoolCategory.entries.forEach { cat ->
                    val on = SchoolScope.fromStored(school.category).category == cat
                    Column(
                        Modifier.weight(1f)
                            .background(if (on) Theme.ACCENT_SOFT else Theme.CARD, RoundedCornerShape(12.dp))
                            .border(if (on) 2.dp else 1.dp, if (on) Theme.ACCENT else Theme.LINE, RoundedCornerShape(12.dp))
                            .clickable { school = school.copy(category = cat.name); applySchoolCategory(state, cat); savedMsg = "" }
                            .padding(14.dp)
                    ) {
                        Text(cat.label, color = if (on) Theme.ACCENT else Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(cat.detail, color = Theme.MUTED, fontSize = 11.sp)
                        if (on) {
                            Spacer(Modifier.height(6.dp))
                            Text("IN USE", color = Theme.GOOD, fontSize = 10.sp, fontWeight = FontWeight.Black, style = TextStyle(letterSpacing = 1.sp))
                        }
                    }
                }
            }
            val sc = state.scope
            Spacer(Modifier.height(10.dp))
            Text("Active ladder: ${sc.summary}", color = Theme.ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // ── School identity ──
        CardBox {
            Text("1. School identity", color = Theme.ACCENT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            FieldLabel("School name (exactly as it should appear on documents)")
            TextField(school.name, { school = school.copy(name = it) }, Modifier.fillMaxWidth().padding(bottom = 10.dp), "e.g. St. Mary's College, Fort Portal")
            Row3 {
                Column(Modifier.weight(2f)) { FieldLabel("Motto"); TextField(school.motto, { school = school.copy(motto = it) }, Modifier.fillMaxWidth(), "e.g. Knowledge is light") }
                Column(Modifier.weight(1f)) { FieldLabel("Founded year"); TextField(school.foundedYear, { school = school.copy(foundedYear = it) }, Modifier.fillMaxWidth(), "1985") }
                Column(Modifier.weight(1f)) { FieldLabel("School type"); TextField(school.schoolType, { school = school.copy(schoolType = it) }, Modifier.fillMaxWidth(), "Day & Boarding") }
            }
            Spacer(Modifier.height(10.dp))
            Row3 {
                Column(Modifier.weight(1f)) { FieldLabel("Vision"); TextField(school.vision, { school = school.copy(vision = it) }, Modifier.fillMaxWidth(), "Where the school is going") }
                Column(Modifier.weight(1f)) { FieldLabel("Mission"); TextField(school.mission, { school = school.copy(mission = it) }, Modifier.fillMaxWidth(), "What the school does daily") }
            }
        }

        // ── Location ──
        CardBox {
            Text("2. Location", color = Theme.ACCENT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row3 {
                Column(Modifier.weight(1f)) { FieldLabel("P.O. Box / street address"); TextField(school.address, { school = school.copy(address = it) }, Modifier.fillMaxWidth(), "P.O. Box 123") }
                Column(Modifier.weight(1f)) { FieldLabel("District"); TextField(school.district, { school = school.copy(district = it) }, Modifier.fillMaxWidth(), "Kabarole") }
                Column(Modifier.weight(1f)) { FieldLabel("County / Sub-county"); TextField(school.subCounty, { school = school.copy(subCounty = it) }, Modifier.fillMaxWidth()) }
            }
            Spacer(Modifier.height(10.dp))
            Row3 {
                Column(Modifier.weight(1f)) { FieldLabel("Village / Parish"); TextField(school.village, { school = school.copy(village = it) }, Modifier.fillMaxWidth()) }
            }
        }

        // ── Contact ──
        CardBox {
            Text("3. Contact", color = Theme.ACCENT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row3 {
                Column(Modifier.weight(1f)) { FieldLabel("Telephone 1"); TextField(school.phone, { school = school.copy(phone = it) }, Modifier.fillMaxWidth(), "0772 000 000") }
                Column(Modifier.weight(1f)) { FieldLabel("Telephone 2"); TextField(school.phone2, { school = school.copy(phone2 = it) }, Modifier.fillMaxWidth()) }
                Column(Modifier.weight(1f)) { FieldLabel("Email"); TextField(school.email, { school = school.copy(email = it) }, Modifier.fillMaxWidth(), "info@school.ac.ug") }
            }
            Spacer(Modifier.height(10.dp))
            Row3 {
                Column(Modifier.weight(1f)) { FieldLabel("Website"); TextField(school.website, { school = school.copy(website = it) }, Modifier.fillMaxWidth(), "www.school.ac.ug") }
            }
        }

        // ── Official records ──
        CardBox {
            Text("4. Official records (as printed on report cards)", color = Theme.ACCENT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row3 {
                Column(Modifier.weight(1f)) { FieldLabel("Registration no."); TextField(school.regNo, { school = school.copy(regNo = it) }, Modifier.fillMaxWidth(), "ME/XX/123") }
                Column(Modifier.weight(1f)) { FieldLabel("EMIS no."); TextField(school.emisNo, { school = school.copy(emisNo = it) }, Modifier.fillMaxWidth()) }
                Column(Modifier.weight(1f)) { FieldLabel("UNEB centre no."); TextField(school.unebCentreNo, { school = school.copy(unebCentreNo = it) }, Modifier.fillMaxWidth()) }
            }
        }

        // ── Leadership ──
        CardBox {
            Text("5. Leadership", color = Theme.ACCENT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row3 {
                Column(Modifier.weight(1f)) { FieldLabel("Head teacher"); TextField(school.headTeacher, { school = school.copy(headTeacher = it) }, Modifier.fillMaxWidth()) }
                Column(Modifier.weight(1f)) { FieldLabel("Deputy head teacher"); TextField(school.deputyHeadTeacher, { school = school.copy(deputyHeadTeacher = it) }, Modifier.fillMaxWidth()) }
            }
        }

        // ── Branding ──
        CardBox {
            Text("6. Logo, stamp & colours", color = Theme.ACCENT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // logo
                Column {
                    FieldLabel("School logo (top of every document)")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        val logoBytes = remember(school.logoPath) {
                            school.logoPath?.let { p -> try { java.nio.file.Files.readAllBytes(java.nio.file.Path.of(p)) } catch (_: Exception) { null } }
                        }
                        Box(Modifier.size(52.dp).background(Theme.ACCENT_SOFT, RoundedCornerShape(8.dp)).border(1.dp, Theme.LINE, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            if (logoBytes != null) {
                                Image(BitmapPainter(Image.makeFromEncoded(logoBytes).toComposeImageBitmap()), contentDescription = "School logo", modifier = Modifier.size(48.dp))
                            } else Text("No\nlogo", color = Theme.MUTED, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        Column {
                            Btn("Upload logo", primary = false) { pickAsset("logo") { np -> school = school.copy(logoPath = np) } }
                            if (school.logoPath != null) Text("Logo saved \u2713", color = Theme.GOOD, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                // stamp
                Column(Modifier.weight(1f)) {
                    FieldLabel("School stamp / seal (signature area)")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        val stampBytes = remember(school.stampPath) {
                            school.stampPath?.let { p -> try { java.nio.file.Files.readAllBytes(java.nio.file.Path.of(p)) } catch (_: Exception) { null } }
                        }
                        Box(Modifier.size(52.dp).background(Theme.ACCENT_SOFT, RoundedCornerShape(8.dp)).border(1.dp, Theme.LINE, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            if (stampBytes != null) {
                                Image(BitmapPainter(Image.makeFromEncoded(stampBytes).toComposeImageBitmap()), contentDescription = "School stamp", modifier = Modifier.size(48.dp))
                            } else Text("No\nstamp", color = Theme.MUTED, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        Column {
                            Btn("Upload stamp", primary = false) { pickAsset("stamp") { np -> school = school.copy(stampPath = np) } }
                            if (school.stampPath != null) Text("Stamp saved \u2713", color = Theme.GOOD, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row3 {
                Column(Modifier.weight(1f)) { FieldLabel("Primary colour (headings)"); TextField(school.colorPrimary, { school = school.copy(colorPrimary = it) }, Modifier.fillMaxWidth(), "#1F6FEB") }
                Column(Modifier.weight(1f)) { FieldLabel("Secondary colour (text)"); TextField(school.colorSecondary, { school = school.copy(colorSecondary = it) }, Modifier.fillMaxWidth(), "#111827") }
            }
        }

        // ── Save ──
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Btn("Save school profile") {
                state.repo.mutate("SCHOOL_UPDATED", "School") { it.copy(school = school) }
                state.refresh()
                savedMsg = "School profile saved \u2713"
            }
            if (savedMsg.isNotBlank()) Text(savedMsg, color = Theme.GOOD, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (error.isNotBlank()) ErrorText(error)
        }
        Spacer(Modifier.height(4.dp))
        AcademicYearsSection(state)
        }
}

@Composable
fun AcademicYearsSection(state: AppState) {
    val d = state.data
    var newYear by remember { mutableStateOf("") }

    CardBox {
        Text("Academic Years & Terms", color = Theme.TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (d.academicYears.isEmpty()) {
            Text("No academic year yet. Create one to start entering marks.", color = Theme.MUTED, fontSize = 14.sp)
        }
        d.academicYears.forEach { year ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(year.year, color = Theme.TEXT, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (year.terms.isEmpty()) {
                        Btn("Add 3 terms", primary = false) {
                            val terms = (1..3).map { n -> Term(id = state.repo.nextId(), yearId = year.id, number = n) }
                            state.repo.mutate("YEAR_TERMS_ADDED", "AcademicYear", year.id) { dd ->
                                dd.copy(academicYears = dd.academicYears.map { if (it.id == year.id) it.copy(terms = terms, currentTermId = terms.first().id) else it })
                            }
                            state.refresh()
                        }
                    }
                    year.currentTermId?.let { ct ->
                        val t = year.terms.firstOrNull { it.id == ct }
                        if (t != null) Text("Current term: Term ${t.number}", color = Theme.GOOD, fontSize = 14.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    year.terms.forEach { term ->
                        Button(
                            onClick = {
                                state.repo.mutate("CURRENT_TERM_SET", "AcademicYear", year.id, new = "Term ${term.number}") { dd ->
                                    dd.copy(academicYears = dd.academicYears.map { if (it.id == year.id) it.copy(currentTermId = term.id) else it })
                                }
                                state.refresh()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (year.currentTermId == term.id) Theme.GOOD else Theme.ACCENT_SOFT,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) { Text("Term ${term.number}", fontSize = 13.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(newYear, { newYear = it }, Modifier.width(160.dp), "e.g. 2027")
            Btn("Create academic year") {
                if (newYear.isBlank()) return@Btn
                val id = state.repo.nextId()
                state.repo.mutate("YEAR_CREATED", "AcademicYear", id, new = newYear) { dd ->
                    // claim enrollments made before any year existed so students stay visible in the new year
                    dd.copy(
                        academicYears = dd.academicYears + AcademicYear(id = id, year = newYear),
                        enrollments = dd.enrollments.map { if (it.academicYearId == "no-year") it.copy(academicYearId = id) else it }
                    )
                }
                newYear = ""
                state.refresh()
            }
        }
    }
}

@Composable
fun DashboardScreen(state: AppState) {
    val d = state.data
    val year = d.academicYears.firstOrNull { y -> y.currentTermId != null }
    val term = year?.terms?.firstOrNull { it.id == year.currentTermId } ?: year?.terms?.firstOrNull()

    // ── Real markbook coverage for the current term ──
    val activeClasses = state.scopeClasses()
    val activeSubjects = d.subjects.filter { it.active }
    val activeStudents = d.students.filter { it.status == StudentStatus.ACTIVE }
    val termMarks = if (term != null) d.marks.filter { it.termId == term.id && it.type == MarkType.VALUE } else emptyList()
    val capturedPairs = termMarks.map { it.studentId to it.subjectId }.toSet()
    val subjectLevel = activeSubjects.associate { it.id to it.level }
    val classStudents: Map<String, List<Student>> =
        if (year == null) emptyMap()
        else d.enrollments.filter { it.academicYearId == year.id }
            .groupBy { it.classId }
            .mapValues { (_, en) -> en.mapNotNull { e -> d.students.firstOrNull { st -> st.id == e.studentId && st.status == StudentStatus.ACTIVE } } }

    val classTeacher = { c: SchoolClass ->
        c.classTeacherId?.let { id -> d.teachers.firstOrNull { it.id == id }?.name }
            ?: d.assignments.firstOrNull { it.classId == c.id && it.subjectId == null }
                ?.let { a -> d.teachers.firstOrNull { it.id == a.teacherId }?.name }
            ?: "Unassigned"
    }

    // Per-class readiness: captured student-subject pairs vs offered subjects for that level
    data class Ready(val cls: SchoolClass, val students: Int, val captured: Int, val expected: Int, val pct: Int, val reports: Int, val teacher: String)
    val perClass = activeClasses.map { c ->
        val students = classStudents[c.id].orEmpty().distinctBy { it.id }
        val studentIds = students.map { it.id }.toSet()
        val levelSubjectIds = activeSubjects.filter { it.level == c.level }.map { it.id }.toSet()
        val expected = students.size * levelSubjectIds.size
        val captured = capturedPairs.count { (sid, subj) -> sid in studentIds && subj in levelSubjectIds }
        val reports = if (term == null) 0
            else d.reports.count { r -> r.termId == term.id && r.studentId in studentIds }
        val pct = if (expected > 0) captured * 100 / expected else 0
        Ready(c, students.size, captured, expected, pct, reports, classTeacher(c))
    }
    val totalExpected = perClass.sumOf { it.expected }
    val totalCaptured = perClass.sumOf { it.captured }
    val overallPct = if (totalExpected > 0) (totalCaptured * 100.0 / totalExpected).toInt().coerceAtMost(100) else 0

    // Level breakdown — only the levels this school actually runs
    val levels = state.scope.levels.map { lv ->
        val label = when (lv) {
            Level.PRIMARY -> "Primary"
            Level.O_LEVEL -> "O-Level (S1–S4)"
            Level.A_LEVEL -> "A-Level (S5–S6)"
        }
        val expected = activeClasses.filter { it.level == lv }.sumOf { c ->
            classStudents[c.id].orEmpty().distinctBy { it.id }.size * activeSubjects.count { it.level == lv }
        }
        val captured = capturedPairs.count { (_, subj) -> subjectLevel[subj] == lv }
        Triple(label, if (expected > 0) captured * 100 / expected else 0, expected)
    }

    // Term averages across all years (real marks, VALUE entries only)
    val termStats = d.academicYears.sortedBy { it.year }.flatMap { y -> y.terms.sortedBy { it.number }.map { t -> y to t } }
        .mapNotNull { (y, t) ->
            val ms = d.marks.filter { it.termId == t.id && it.type == MarkType.VALUE && it.maxScore > 0 }
            if (ms.isEmpty()) null
            else Triple("T${t.number} '${y.year.takeLast(2)}", (ms.sumOf { it.score / it.maxScore * 100.0 } / ms.size).toFloat(), t.id == term?.id)
        }.takeLast(8)
    val currentAvg = termStats.lastOrNull()?.second

    var dismissed by remember { mutableStateOf(false) }
    val incomplete = perClass.count { it.pct < 100 && it.students > 0 }

    // ── Page head ──
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(
                java.text.SimpleDateFormat("EEEE, d MMMM yyyy").format(java.util.Date()).uppercase(),
                color = Theme.MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                style = TextStyle(letterSpacing = 1.4.sp)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "A clear view of the term.", color = Theme.INK, fontSize = 27.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Keep marks moving, spot gaps early, and prepare reports without leaving the school office.",
                color = Theme.MUTED, fontSize = 12.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(
                onClick = { state.screen = "students" },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Theme.INK)
            ) { Text("Add student", fontSize = 12.sp) }
            Button(
                onClick = { state.screen = "reports" },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Theme.GOLD, contentColor = Theme.DEEP)
            ) { Text("Generate reports", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }

    // ── Reporting-window note ──
    if (!dismissed && incomplete > 0 && perClass.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().background(Theme.GOLD_SOFT, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Term ${term?.number ?: 1} reporting window is open. $incomplete class markbook" +
                    (if (incomplete == 1) "" else "s") + " still need marks before report cards can be generated.",
                color = Theme.TEXT, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Dismiss", color = Theme.TEXT, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { dismissed = true }.padding(horizontal = 4.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
    }

    // ── Stat cards ──
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(
            Triple("Students on roll", activeStudents.size.toString(),
                "${activeStudents.count { it.sex == "M" }} boys · ${activeStudents.count { it.sex == "F" }} girls"),
            Triple("Classes & streams", activeClasses.size.toString(),
                "${activeClasses.count { it.stream.isNotBlank() }} streams · " +
                    (if (term != null) d.markSheets.count { it.termId == term.id } else 0) + " markbooks"),
            Triple("Marks captured", capturedPairs.size.toString(), "$overallPct% of expected entries"),
            Triple("Reports ready", (if (term != null) d.reports.count { it.termId == term.id } else 0).toString(),
                "${if (term != null) d.reports.count { it.termId == term.id && it.approved } else 0} approved")
        ).forEach { (label, value, detail) ->
            Column(Modifier.weight(1f)) { StatCard(label, value, Theme.INK, detail) }
        }
    }
    Spacer(Modifier.height(16.dp))

    // ── Reporting progress + term average ──
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CardBox(Modifier.weight(1.15f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reporting progress", color = Theme.INK, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (term != null) "Term ${term.number}, ${year?.year ?: ""}" else "",
                    color = Theme.MUTED, fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Donut(overallPct, "captured")
                Column(Modifier.weight(1f)) {
                    val least = perClass.filter { it.students > 0 }.minByOrNull { it.pct }
                    val leastName = least?.let { it.cls.name + (if (it.cls.stream.isBlank()) "" else " ${it.cls.stream}") } ?: ""
                    Text(
                        if (least != null && least.pct < 100)
                            "$leastName is the least complete markbook — it needs marks before its reports can be prepared."
                        else "Every class markbook is complete. You are ready to generate reports.",
                        color = Theme.MUTED, fontSize = 11.sp, lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    levels.forEach { (label, pct, _) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = Theme.MUTED, fontSize = 10.sp, modifier = Modifier.width(112.dp))
                            MiniProgress(pct, Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text("$pct%", color = Theme.INK, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = Theme.TEAL, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text("Offline first — every change is saved on this computer", color = Theme.MUTED, fontSize = 10.sp)
            }
        }
        CardBox(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("School average by term", color = Theme.INK, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Pill("across all published marks")
            }
            Spacer(Modifier.height(10.dp))
            if (termStats.size >= 2) {
                Sparkline(termStats.map { it.second })
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    termStats.forEachIndexed { i, t ->
                        Text(
                            t.first, color = if (t.third) Theme.INK else Theme.MUTED,
                            fontSize = if (t.third) 10.sp else 9.sp,
                            fontWeight = if (t.third) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            } else {
                Text(
                    "Averages appear here once marks are entered in two or more terms.",
                    color = Theme.MUTED, fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (currentAvg != null) "Current term average: " + "%.1f".format(currentAvg) + "%"
                else "No marks yet this term.",
                color = Theme.MUTED, fontSize = 10.sp
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    // ── Class readiness table ──
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Class readiness", color = Theme.INK, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("The next actions for your reporting desk", color = Theme.MUTED, fontSize = 10.sp)
            }
            Text(
                "View all classes", color = Theme.TEAL, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { state.screen = "classes" }
            )
        }
        Spacer(Modifier.height(12.dp))
        if (perClass.isEmpty()) {
            Text("No classes yet — create classes and enroll students to see reporting readiness here.", color = Theme.MUTED, fontSize = 12.sp)
        } else {
            Row(Modifier.fillMaxWidth().background(Theme.ACCENT_SOFT, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("Class & stream", color = Theme.MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                Text("Teacher", color = Theme.MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                Text("Marks entered", color = Theme.MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Reports", color = Theme.MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                Text("State", color = Theme.MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
            }
            perClass.sortedBy { it.pct }.take(8).forEachIndexed { i, r ->
                val (stateLabel, tone) = when {
                    r.students == 0 -> "No students" to "muted"
                    r.pct >= 99 -> "Ready" to "teal"
                    r.pct >= 60 -> "In progress" to "gold"
                    r.pct > 0 -> "Needs marks" to "rust"
                    else -> "Not started" to "muted"
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)
                        .background(if (i % 2 == 1) Theme.ACCENT_SOFT.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(6.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1.2f)) {
                        Text(
                            r.cls.name + (if (r.cls.stream.isBlank()) "" else " · ${r.cls.stream}"),
                            color = Theme.INK, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${r.students} learner" + (if (r.students == 1) "" else "s") + " · " +
                                when (r.cls.level) {
                                    Level.PRIMARY -> "Primary"
                                    Level.O_LEVEL -> "O-Level · S1–S4"
                                    Level.A_LEVEL -> "A-Level · S5–S6"
                                },
                            color = Theme.MUTED, fontSize = 9.sp
                        )
                    }
                    Text(r.teacher.take(18), color = Theme.MUTED, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        MiniProgress(r.pct, Modifier.width(70.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${r.pct}%", color = Theme.INK, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (r.students == 0) "—" else "${r.reports} / ${r.students}",
                        color = Theme.MUTED, fontSize = 11.sp, modifier = Modifier.width(70.dp)
                    )
                    Box(Modifier.width(100.dp)) { Pill(stateLabel, tone) }
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("✓ Offline first — data never leaves this computer unless you back it up", color = Theme.MUTED, fontSize = 10.sp)
        Text("✓ " + (if (d.settings.autoBackupEnabled) "Automatic local backup is on" else "Automatic backup is off — turn it on in Sync & Backup"), color = Theme.MUTED, fontSize = 10.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Security gate (Section 12) — PIN lock shown at startup
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PinLockScreen(state: AppState) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val school = state.data.school.name.ifBlank { "School Report Maker" }

    Box(Modifier.fillMaxSize().background(Theme.NAVY), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(360.dp).background(Theme.CARD, RoundedCornerShape(16.dp)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val lockLogo = remember {
                try {
                    val bytes = Thread.currentThread().contextClassLoader?.getResourceAsStream("icon.png")?.readBytes()
                    if (bytes != null) BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap()) else null
                } catch (_: Exception) { null }
            }
            if (lockLogo != null) {
                Image(lockLogo, contentDescription = "DeryCode School Report Maker",
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
            } else {
                Icon(Icons.Filled.Lock, "Locked", Modifier.size(36.dp), tint = Theme.ACCENT)
            }
            Spacer(Modifier.height(10.dp))
            Text(school, color = Theme.TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("This console is locked with a PIN.", color = Theme.MUTED, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6); err = false },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                placeholder = { Text("Enter PIN", color = Theme.MUTED, fontSize = 13.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Theme.TEXT, fontSize = 16.sp),
                isError = err
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    if (com.derycode.srs.core.support.LicenseKeys.hash("pin|$pin") == state.data.settings.adminPinHash) {
                        state.unlocked = true
                    } else { err = true }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) { Text("Unlock", fontSize = 14.sp) }
            if (err) Spacer(Modifier.height(8.dp))
            if (err) Text("Wrong PIN — try again.", color = Color(0xFFFF6B6B), fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Text("Forgot the PIN? Restore a backup made before the PIN was set, or use factory reset.", color = Theme.MUTED, fontSize = 10.sp)
        }
    }
}
