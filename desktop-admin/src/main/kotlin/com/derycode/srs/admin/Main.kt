package com.derycode.srs.admin

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import org.jetbrains.skia.Image
import androidx.compose.ui.window.application
import com.derycode.srs.core.model.*
import com.derycode.srs.core.seed.Seeds
import com.derycode.srs.core.store.JsonStore
import com.derycode.srs.core.store.SchoolRepository
import java.nio.file.Path

// ─────────────────────────────────────────────────────────────────────────────
// Theme
// ─────────────────────────────────────────────────────────────────────────────

internal val NAVY = Color(0xFF0B1220)
internal val CARD = Color(0xFF111A2E)
internal val ACCENT = Color(0xFF4F8CFF)
internal val ACCENT_SOFT = Color(0xFF1B2A4A)
internal val TEXT = Color(0xFFE8EDF7)
internal val MUTED = Color(0xFF8B98B4)
internal val GOOD = Color(0xFF3FCF8E)
internal val WARN = Color(0xFFF5A623)

internal val dataDir: Path = Path.of(System.getProperty("user.home"), "SchoolReportMaker")
internal val dataFile: Path = dataDir.resolve("school-data.json")

// ─────────────────────────────────────────────────────────────────────────────
// App state
// ─────────────────────────────────────────────────────────────────────────────

class AppState(val repo: SchoolRepository) {
    var screen by mutableStateOf("dashboard")
    var refreshTick by mutableStateOf(0)
    var updateAvailable by mutableStateOf<com.derycode.srs.core.support.UpdateInfo?>(null)
    fun refresh() { refreshTick++ }

    val data: SchoolData get() = repo.data
}

fun main() = application {
    DesktopCrashTracker.install()
    val store = JsonStore()
    val repo = SchoolRepository(store, dataFile, deviceId = "admin-desktop")
    repo.seedIfNeeded(Seeds.ALL_SUBJECTS, Seeds.COMPONENTS)

    val state = remember { AppState(repo) }
    Thread { state.updateAvailable = DesktopUpdateChecker.check() }.start()   // auto-check on launch
    val windowIcon = remember {
        try {
            val bytes = Thread.currentThread().contextClassLoader?.getResourceAsStream("icon.png")?.readBytes()
            if (bytes != null) BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap()) else null
        } catch (_: Exception) { null }
    }

    Window(
        title = "DeryCode School Report Maker — Admin Console",
        icon = windowIcon,
        onCloseRequest = ::exitApplication
    ) {
        App(state)
    }
}

@Composable
fun App(state: AppState) {
    val screens = listOf(
        "dashboard" to "Dashboard", "setup" to "School Setup", "students" to "Students",
        "classes" to "Classes & Streams", "subjects" to "Subjects", "teachers" to "Teachers",
        "marks" to "Marks Grid", "grading" to "Grading Schemes", "results" to "Results Review",
        "reports" to "Reports", "sync" to "Sync & Backup",
        "templates" to "Templates", "preview" to "Report Preview",
        "audit" to "Audit Log", "archive" to "Report Archive", "backups" to "Backup History",
        "users" to "Users", "notifications" to "Notifications", "calendar" to "Calendar",
        "importexport" to "Import / Export", "settings" to "Settings",
        "support" to "Support & Licence"
    )
    Row(Modifier.fillMaxSize().background(NAVY)) {
        // ── Navigation rail ──
        Column(
            Modifier.width(210.dp).fillMaxHeight().background(Color(0xFF0A0F1C))
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("SRM", color = ACCENT, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("School Report", color = TEXT, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Maker", color = MUTED, fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = Color(0xFF1B2540), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            screens.forEach { (key, label) ->
                val selected = state.screen == key
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .background(if (selected) ACCENT_SOFT else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { state.screen = key }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(Modifier.width(3.dp).height(18.dp).background(if (selected) ACCENT else Color.Transparent, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = if (selected) TEXT else MUTED, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                Box(Modifier.width(8.dp).height(8.dp).background(GOOD, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(8.dp))
                Text("Offline · saved locally", color = MUTED, fontSize = 10.sp)
            }
        }

        // ── Content ──
        Box(Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            when (state.screen) {
                "dashboard" -> DashboardScreen(state)
                "setup" -> SetupScreen(state)
                "students" -> StudentsScreen(state)
                "classes" -> ClassesScreen(state)
                "subjects" -> SubjectsScreen(state)
                "teachers" -> TeachersScreen(state)
                "marks" -> MarksScreen(state)
                "grading" -> GradingScreen(state)
                "results" -> ResultsScreen(state)
                "reports" -> ReportsScreen(state)
                "sync" -> SyncScreen(state)
                "templates" -> TemplatesScreen(state)
                "preview" -> PreviewScreen(state)
                "audit" -> AuditScreen(state)
                "archive" -> ReportsArchiveScreen(state)
                "backups" -> BackupHistoryScreen(state)
                "users" -> UsersScreen(state)
                "notifications" -> NotificationsScreen(state)
                "calendar" -> CalendarScreen(state)
                "importexport" -> ImportExportScreen(state)
                "settings" -> SettingsScreen(state)
                "support" -> SupportScreenDesktop(state)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ScreenTitle(title: String, subtitle: String = "") {
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(title, color = TEXT, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = MUTED, fontSize = 12.sp)
    }
}

@Composable
fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF1E2A47), RoundedCornerShape(14.dp)).padding(16.dp),
        content = content
    )
}

@Composable
fun StatCard(label: String, value: String, tint: Color = ACCENT) {
    Column(
        Modifier.background(CARD, RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF1E2A47), RoundedCornerShape(12.dp)).padding(14.dp).width(150.dp)
    ) {
        Text(label, color = MUTED, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Row3(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun FieldLabel(text: String) = Text(text, color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Medium)

@Composable
fun TextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "") {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = Color(0xFF5A6B8C), fontSize = 12.sp) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = TEXT, fontSize = 13.sp)
    )
}

@Composable
fun Btn(label: String, primary: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = if (primary) ButtonDefaults.buttonColors(containerColor = ACCENT, contentColor = Color.White)
        else ButtonDefaults.buttonColors(containerColor = ACCENT_SOFT, contentColor = TEXT)
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun HeaderCell(text: String) = Text(text, color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)

@Composable
fun Cell(text: String, bold: Boolean = false, color: Color = TEXT) =
    Text(text, color = color, fontSize = 12.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)

@Composable
fun ErrorText(msg: String) = Text(msg, color = WARN, fontSize = 11.sp)

// ─────────────────────────────────────────────────────────────────────────────
// Screens — setup & dashboard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SetupScreen(state: AppState) {
    val d = state.data
    var school by remember { mutableStateOf(d.school) }
    var error by remember { mutableStateOf("") }

    ScreenTitle("School Setup", "Offline school account — everything stays on this computer")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            CardBox {
                FieldLabel("School name"); TextField(school.name, { school = school.copy(name = it) }, Modifier.fillMaxWidth().padding(bottom = 10.dp), "e.g. St. Mary's College")
                Row3 {
                    Column(Modifier.weight(1f)) { FieldLabel("Address"); TextField(school.address, { school = school.copy(address = it) }, Modifier.fillMaxWidth()) }
                    Column(Modifier.weight(1f)) { FieldLabel("Telephone"); TextField(school.phone, { school = school.copy(phone = it) }, Modifier.fillMaxWidth()) }
                    Column(Modifier.weight(1f)) { FieldLabel("Email"); TextField(school.email, { school = school.copy(email = it) }, Modifier.fillMaxWidth()) }
                }
                Spacer(Modifier.height(10.dp))
                Row3 {
                    Column(Modifier.weight(1f)) { FieldLabel("Motto"); TextField(school.motto, { school = school.copy(motto = it) }, Modifier.fillMaxWidth()) }
                    Column(Modifier.weight(1f)) { FieldLabel("Head teacher"); TextField(school.headTeacher, { school = school.copy(headTeacher = it) }, Modifier.fillMaxWidth()) }
                    Column(Modifier.weight(1f)) { FieldLabel("Registration no."); TextField(school.regNo, { school = school.copy(regNo = it) }, Modifier.fillMaxWidth()) }
                }
                Spacer(Modifier.height(10.dp))
                Row3 {
                    Column(Modifier.weight(1f)) { FieldLabel("Primary color"); TextField(school.colorPrimary, { school = school.copy(colorPrimary = it) }, Modifier.fillMaxWidth(), "#1F6FEB") }
                    Column(Modifier.weight(1f)) { FieldLabel("Logo path (optional)"); TextField(school.logoPath ?: "", { school = school.copy(logoPath = it.ifBlank { null }) }, Modifier.fillMaxWidth(), "C:/logo.png") }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn("Save school profile") {
                        state.repo.mutate("SCHOOL_UPDATED", "School") { it.copy(school = school) }
                        state.refresh()
                    }
                    if (error.isNotBlank()) ErrorText(error)
                }
            }
        }
        item { AcademicYearsSection(state) }
    }
}

@Composable
fun AcademicYearsSection(state: AppState) {
    val d = state.data
    var newYear by remember { mutableStateOf("") }

    CardBox {
        Text("Academic Years & Terms", color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (d.academicYears.isEmpty()) {
            Text("No academic year yet. Create one to start entering marks.", color = MUTED, fontSize = 12.sp)
        }
        d.academicYears.forEach { year ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(year.year, color = TEXT, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                        if (t != null) Text("Current term: Term ${t.number}", color = GOOD, fontSize = 12.sp)
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
                                containerColor = if (year.currentTermId == term.id) GOOD else ACCENT_SOFT,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) { Text("Term ${term.number}", fontSize = 11.sp) }
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
                    dd.copy(academicYears = dd.academicYears + AcademicYear(id = id, year = newYear))
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
    val activeStudents = d.students.count { it.status == StudentStatus.ACTIVE }
    val classes = d.classes.count { it.active }
    val locked = d.markSheets.count { it.state == MarkSheetState.LOCKED }
    val pendingSync = d.syncQueue.count { it.status == "PENDING" }

    ScreenTitle("Dashboard", "Everything below works 100% offline")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row3 {
            StatCard("Academic year", year?.year ?: "—")
            StatCard("Students", activeStudents.toString(), GOOD)
            StatCard("Classes", classes.toString())
        }
        Spacer(Modifier.height(0.dp))
        Row3 {
            StatCard("Teachers", d.teachers.size.toString())
            StatCard("Subjects", d.subjects.count { it.active }.toString())
            StatCard("Locked sheets", locked.toString(), WARN)
            StatCard("Pending sync", pendingSync.toString(), WARN)
        }
        Spacer(Modifier.height(0.dp))
        CardBox {
            Text("Getting started", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val steps = listOf(
                "1. School Setup — enter the school profile and create an academic year with 3 terms",
                "2. Classes & Streams — add classes (S1, P7…) and streams (East, West…)",
                "3. Subjects — adjust the preloaded subjects to match what your school offers",
                "4. Students — add or import students and enroll them in classes",
                "5. Marks Grid — enter marks per class and subject",
                "6. Reports — generate printable report cards"
            )
            steps.forEach { Text(it, color = MUTED, fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp)) }
        }
    }
}
