package com.derycode.srs.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.derycode.srs.core.support.LicenseKeys
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// Mandatory first-run setup wizard (v2.2.0) — gates the dashboard until the
// school profile, online account, PIN and licence are all set up.
// ─────────────────────────────────────────────────────────────────────────────

private val STEPS = listOf("Welcome", "School Profile", "Online Account", "Security PIN", "Licence", "Done")

@Composable
fun SetupWizard(state: AppState) {
    var step by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().background(Theme.NAVY)) {
        Column(Modifier.fillMaxSize().padding(40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SRM", color = Theme.ACCENT, fontWeight = FontWeight.Black, fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column { Text("School Report Maker", color = Theme.TEXT, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("First-time setup", color = Theme.MUTED, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(16.dp))
            // Stepper
            Row(verticalAlignment = Alignment.CenterVertically) {
                STEPS.forEachIndexed { i, label ->
                    val active = i == step
                    val done = i < step
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = done) { step = i }) {
                        Box(Modifier.size(22.dp).background(
                            when { active -> Theme.ACCENT; done -> Theme.GOOD; else -> Color(0xFF232F4A) },
                            RoundedCornerShape(11.dp)
                        ), contentAlignment = Alignment.Center) {
                            Text(if (done) "✓" else "${i + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = if (active) Theme.TEXT else Theme.MUTED, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                    }
                    if (i < STEPS.lastIndex) { Spacer(Modifier.width(6.dp)); Box(Modifier.width(28.dp).height(2.dp).background(Color(0xFF232F4A))) ; Spacer(Modifier.width(6.dp)) }
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().weight(1f).background(Theme.CARD, RoundedCornerShape(14.dp)).padding(28.dp)
                .verticalScroll(rememberScrollState())) {
                when (step) {
                    0 -> StepWelcome(state) { step = 1 }
                    1 -> StepSchool(state) { step = 2 }
                    2 -> StepAccount(state) { step = 3 }
                    3 -> StepPin(state) { step = 4 }
                    4 -> StepLicence(state) { step = 5 }
                    else -> StepDone(state)
                }
            }
        }
    }
}

@Composable
private fun StepWelcome(state: AppState, next: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text("Welcome to School Report Maker 🎓", color = Theme.TEXT, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("This short setup creates your school's admin console. It takes about 3 minutes and is required before the dashboard opens.", color = Theme.MUTED, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        WizardInfo("1", "School profile", "Name, district and head teacher details — printed on every report.")
        WizardInfo("2", "Online account", "Cloud backups + teacher phone login with your school code.")
        WizardInfo("3", "Security PIN", "Locks this console so only you can open it.")
        WizardInfo("4", "Licence", "Activate a paid plan or start a 30-day trial.")
        Spacer(Modifier.height(24.dp))
        Button({ next() }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(46.dp).width(220.dp)) {
            Text("Get Started", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WizardInfo(n: String, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.size(30.dp).background(Theme.ACCENT_SOFT, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text(n, color = Theme.ACCENT, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column { Text(title, color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(sub, color = Theme.MUTED, fontSize = 12.sp) }
    }
}

@Composable
private fun StepSchool(state: AppState, next: () -> Unit) {
    var s by remember { mutableStateOf(state.data.school) }
    Column(Modifier.fillMaxWidth()) {
        Text("School Profile", color = Theme.TEXT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("These details appear on report cards and leaving certificates.", color = Theme.MUTED, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { FieldLabel("School name *"); TextField(s.name, { s = s.copy(name = it) }, Modifier.fillMaxWidth(), "e.g. St. Peter's Primary School") }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { FieldLabel("District"); TextField(s.address.ifBlank { "" }, { s = s.copy(address = it) }, Modifier.fillMaxWidth(), "Town, district") }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { FieldLabel("Head teacher"); TextField(s.headTeacher, { s = s.copy(headTeacher = it) }, Modifier.fillMaxWidth(), "Full name") }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { FieldLabel("Phone"); TextField(s.phone, { s = s.copy(phone = it) }, Modifier.fillMaxWidth(), "07XX XXX XXX") }
        }
        Spacer(Modifier.height(10.dp))
        Column { FieldLabel("School email"); TextField(s.email, { s = s.copy(email = it) }, Modifier.fillMaxWidth(), "school@email.com") }
        Spacer(Modifier.height(18.dp))
        var err by remember { mutableStateOf(false) }
        if (err) Text("Please enter the school name.", color = Color(0xFFFF6B6B), fontSize = 12.sp)
        Row {
            Button({ if (s.name.isBlank()) err = true else { state.repo.mutate("SETUP_SCHOOL", "School") { d -> d.copy(school = s) }; next() } },
                shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(44.dp).width(200.dp)) {
                Text("Save & Continue", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StepAccount(state: AppState, next: () -> Unit) {
    val st = state.data.settings
    var url by remember { mutableStateOf(CloudSync.url(st)) }
    var key by remember { mutableStateOf(CloudSync.key(st)) }
    var email by remember { mutableStateOf(st.cloudEmail.ifBlank { state.data.school.email }) }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    var linked by remember { mutableStateOf(st.cloudSchoolId.isNotBlank()) }
    val invite = st.cloudInviteCode

    Column(Modifier.fillMaxWidth()) {
        Text("Online Account", color = Theme.TEXT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Cloud backups + teacher phone login. Requires internet — once, now.", color = Theme.MUTED, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        if (linked) {
            Box(Modifier.fillMaxWidth().background(Color(0xFF12321F), RoundedCornerShape(10.dp)).padding(16.dp)) {
                Column {
                    Text("✓ Online account created", color = Theme.GOOD, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("School code for your teachers: ", color = Theme.MUTED, fontSize = 12.sp)
                    Text(invite, color = Theme.ACCENT, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("Teachers enter this code in the phone app to join your school.", color = Theme.MUTED, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Button({ next() }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(44.dp).width(200.dp)) { Text("Continue", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            return@Column
        }
        FieldLabel("Cloud server URL")
        TextField(url, { url = it }, Modifier.fillMaxWidth(), "https://xxxx.supabase.co")
        Spacer(Modifier.height(10.dp))
        FieldLabel("Server key")
        TextField(key, { key = it }, Modifier.fillMaxWidth(), "anon key")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { FieldLabel("Admin email *"); TextField(email, { email = it }, Modifier.fillMaxWidth(), "you@school.com") }
            Spacer(Modifier.width(10.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { FieldLabel("Password *"); PassField(pass, { pass = it }) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { FieldLabel("Confirm password *"); PassField(pass2, { pass2 = it }) }
        }
        Spacer(Modifier.height(6.dp))
        if (msg.isNotEmpty()) Text(msg, color = if (msgErr) Color(0xFFFF6B6B) else Theme.GOOD, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row {
            Button({
                msg = ""; msgErr = false
                if (email.isBlank() || pass.length < 6) { msg = "Enter a valid email and a password of at least 6 characters."; msgErr = true; return@Button }
                if (pass != pass2) { msg = "Passwords do not match."; msgErr = true; return@Button }
                busy = true
                Thread {
                    var r = CloudApi.signUp(url, key, email.trim().lowercase(), pass)
                    if (!r.ok && r.error.contains("already", true)) r = CloudApi.login(url, key, email.trim().lowercase(), pass)
                    if (!r.ok) { busy = false; msg = r.error; msgErr = true; return@Thread }
                    val code = (1..8).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
                    val sc = state.data.school
                    val (sid, serr) = CloudApi.createSchool(url, key, r.accessToken, sc.name, sc.address, sc.address, sc.headTeacher, st.licencePlan.ifBlank { "Trial" }, st.licenceRef, state.data.students.size, code)
                    busy = false
                    if (sid.isBlank()) { msg = serr; msgErr = true; return@Thread }
                    state.repo.mutate("CLOUD_SETUP", "Settings") { d -> d.copy(settings = d.settings.copy(
                        cloudUrl = url, cloudKey = key, cloudEmail = email.trim().lowercase(),
                        cloudAccessToken = r.accessToken, cloudRefreshToken = r.refreshToken,
                        cloudSchoolId = sid, cloudInviteCode = code, teacherCloudEnabled = true)) }
                    linked = true
                }.start()
            }, enabled = !busy, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(44.dp).width(220.dp)) {
                Text(if (busy) "Creating account…" else "Create Online Account", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton({
                state.repo.mutate("CLOUD_SKIP", "Settings") { d -> d.copy(settings = d.settings.copy(cloudUrl = url, cloudKey = key, teacherCloudEnabled = false)) }
                next()
            }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(44.dp)) {
                Text("Set up later (offline mode)", fontSize = 12.sp, color = Theme.MUTED)
            }
        }
        if (msgErr && msg.contains("internet", true) || msgErr) Spacer(Modifier.height(6.dp))
        if (msgErr) Text("No internet? Choose offline mode — you can add the cloud account later under Sync & Backup.", color = Theme.MUTED, fontSize = 11.sp)
    }
}

@Composable
private fun StepPin(state: AppState, next: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text("Security PIN", color = Theme.TEXT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Locks this console. You will type this PIN every time you open the app.", color = Theme.MUTED, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { FieldLabel("PIN (4–6 digits)"); TextField(pin, { pin = it.filter { c -> c.isDigit() }.take(6) }, Modifier.fillMaxWidth(), "digits only") }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { FieldLabel("Confirm PIN"); TextField(pin2, { pin2 = it.filter { c -> c.isDigit() }.take(6) }, Modifier.fillMaxWidth(), "repeat") }
        }
        Spacer(Modifier.height(8.dp))
        if (msg.isNotEmpty()) Text(msg, color = if (err) Color(0xFFFF6B6B) else Theme.MUTED, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row {
            Button({
                msg = ""; err = false
                if (pin.length < 4) { msg = "PIN must be 4–6 digits."; err = true }
                else if (pin != pin2) { msg = "PINs do not match."; err = true }
                else {
                    state.repo.mutate("SETUP_PIN", "Settings") { d -> d.copy(settings = d.settings.copy(adminPinHash = LicenseKeys.hash("pin|$pin"))) }
                    next()
                }
            }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(44.dp).width(220.dp)) { Text("Set PIN & Continue", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            OutlinedButton({ next() }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(44.dp)) { Text("Skip for now", fontSize = 12.sp, color = Theme.MUTED) }
        }
    }
}

@Composable
private fun StepLicence(state: AppState, next: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text("Licence", color = Theme.TEXT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Activate a plan key from DeryCode, or start with a free 30-day trial (100 students).", color = Theme.MUTED, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        FieldLabel("Licence key (e.g. ST-20270601-A1B2C3D4)")
        TextField(key, { key = it.uppercase() }, Modifier.fillMaxWidth(), "paste key here")
        Spacer(Modifier.height(8.dp))
        if (msg.isNotEmpty()) Text(msg, color = if (err) Color(0xFFFF6B6B) else Theme.GOOD, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row {
            Button({
                msg = ""; err = false
                val v = LicenseKeys.validate(key)
                if (v == null) { msg = "Invalid or expired key — check it and try again."; err = true; return@Button }
                val (plan, expiry) = v
                state.repo.mutate("LICENCE_ACTIVATE", "Settings") { d -> d.copy(settings = d.settings.copy(licencePlan = plan.name, licenceRef = key, licenceExpiry = expiry)) }
                next()
            }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(44.dp).width(170.dp)) { Text("Activate Key", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            OutlinedButton({
                val expiry = java.time.LocalDate.now().plusDays(30).toString().replace("-", "")
                state.repo.mutate("TRIAL_START", "Settings") { d -> d.copy(settings = d.settings.copy(licencePlan = "Trial", licenceRef = "TRIAL", licenceExpiry = expiry)) }
                next()
            }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(44.dp)) { Text("Start 30-day Trial", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun StepDone(state: AppState) {
    val s = state.data.settings
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text("Setup complete 🎉", color = Theme.TEXT, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().background(Theme.ACCENT_SOFT, RoundedCornerShape(10.dp)).padding(16.dp)) {
            Column {
                Text("School: ${state.data.school.name}", color = Theme.TEXT, fontSize = 13.sp)
                Text("Plan: ${s.licencePlan}  (expires ${s.licenceExpiry})", color = Theme.MUTED, fontSize = 12.sp)
                if (s.teacherCloudEnabled && s.cloudInviteCode.isNotBlank()) {
                    Text("Teacher school code: ", color = Theme.MUTED, fontSize = 12.sp)
                    Text(s.cloudInviteCode, color = Theme.ACCENT, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("Teachers download the SRM Teacher app, create an account with this code, and their marks sync automatically.", color = Theme.MUTED, fontSize = 11.sp)
                } else {
                    Text("Offline mode — cloud backup can be enabled later under Sync & Backup.", color = Theme.MUTED, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Button({
            state.repo.mutate("SETUP_COMPLETE", "Settings") { d -> d.copy(settings = d.settings.copy(setupComplete = true)) }
            state.screen = "dashboard"
            state.unlocked = true
            // initial cloud backup if account exists
            if (s.cloudSchoolId.isNotBlank()) CloudSync.pushNow(state.repo)
        }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.GOOD, contentColor = Color(0xFF06281A)), modifier = Modifier.height(48.dp).width(240.dp)) { Text("Open Dashboard", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cloud screen (nav: SYSTEM → Cloud & Online)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CloudScreen(state: AppState) {
    val s = state.data.settings
    var msg by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun say(e: Boolean, m: String) { err = e; msg = m }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Cloud & Online Backup", color = Theme.TEXT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Supabase cloud — automatic off-site backup and teacher sync.", color = Theme.MUTED, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))

        // Status card
        Box(Modifier.fillMaxWidth().background(Theme.CARD, RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF22304E), RoundedCornerShape(12.dp)).padding(18.dp)) {
            Column {
                if (s.cloudSchoolId.isNotBlank()) {
                    Text("● Connected as ${s.cloudEmail}", color = Theme.GOOD, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Teacher school code", color = Theme.MUTED, fontSize = 11.sp)
                    Text(s.cloudInviteCode, color = Theme.ACCENT, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("Share this code with your teachers for phone app access.", color = Theme.MUTED, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Last cloud backup: ${s.cloudLastBackup.ifBlank { "never" }}", color = Theme.MUTED, fontSize = 12.sp)
                } else {
                    Text("● Offline mode — no cloud account", color = Theme.WARN, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Complete the account section below to enable cloud backup and teacher login.", color = Theme.MUTED, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        if (msg.isNotEmpty()) { Text(msg, color = if (err) Color(0xFFFF6B6B) else Theme.GOOD, fontSize = 12.sp); Spacer(Modifier.height(10.dp)) }

        Row {
            Button({ busy = true; say(false, "Uploading backup…"); CloudSync.pushNow(state.repo) { ok, m -> busy = false; say(!ok, if (ok) "✓ Backed up to cloud" else m) } },
                enabled = s.cloudSchoolId.isNotBlank() && !busy, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(42.dp)) { Text("Back Up Now", fontSize = 13.sp) }
            Spacer(Modifier.width(10.dp))
            OutlinedButton({ busy = true; say(false, "Downloading cloud backup…"); CloudSync.pullNow(state.repo) { ok, m -> busy = false; say(!ok, m); if (ok) state.refresh() } },
                enabled = s.cloudSchoolId.isNotBlank() && !busy, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(42.dp)) { Text("Restore From Cloud", fontSize = 13.sp) }
        }
        Spacer(Modifier.height(14.dp))

        // Teacher marks from cloud
        Text("Teacher Marks", color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("Marks your teachers submit from the phone app appear here.", color = Theme.MUTED, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Button({
            busy = true; say(false, "Checking for teacher marks…")
            Thread {
                val st = state.data.settings
                val (bundles, m) = CloudApi.pullTeacherBundles(CloudSync.url(st), CloudSync.key(st), st.cloudAccessToken, st.cloudSchoolId)
                if (bundles.isEmpty()) { busy = false; say(m != "ok", if (m == "ok") "No new teacher marks." else m); return@Thread }
                var imported = 0
                var summary = ""
                val tmp = File.createTempFile("srs-tmarks", ".json")
                for (b in bundles) {
                    tmp.writeText(b)
                    val report = state.repo.store.importBundle(state.repo.data, tmp.toPath())
                    if (report.success) {
                        imported++
                        state.repo.mutate("TEACHER_MARKS_IMPORTED", "Marks", new = report.message) { d ->
                            d.copy(marks = report.merged, students = report.mergedStudents, enrollments = report.mergedEnrollments,
                                enrollmentRequests = report.mergedRequests, dutyRecords = report.mergedDutyRecords,
                                gatePasses = report.mergedGatePasses, attendance = report.mergedAttendance)
                        }
                        summary = report.message
                    }
                }
                tmp.delete()
                if (imported > 0) CloudApi.consumeTeacherBundles(CloudSync.url(st), CloudSync.key(st), st.cloudAccessToken, st.cloudSchoolId)
                busy = false
                say(imported == 0, if (imported > 0) "✓ Imported $imported teacher submission(s). $summary" else "Nothing imported.")
                state.refresh()
            }.start()
        }, enabled = s.cloudSchoolId.isNotBlank() && !busy, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.GOOD, contentColor = Color(0xFF06281A)), modifier = Modifier.height(42.dp)) { Text("Pull Teacher Marks", fontSize = 13.sp) }
        Spacer(Modifier.height(18.dp))

        // Account setup / edit (when not connected)
        if (s.cloudSchoolId.isBlank()) {
            var url by remember { mutableStateOf(CloudSync.url(s)) }
            var key by remember { mutableStateOf(CloudSync.key(s)) }
            var email by remember { mutableStateOf("") }
            var pass by remember { mutableStateOf("") }
            Box(Modifier.fillMaxWidth().background(Theme.CARD, RoundedCornerShape(12.dp)).padding(18.dp)) {
                Column {
                    Text("Create / Connect Cloud Account", color = Theme.TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    FieldLabel("Cloud server URL"); TextField(url, { url = it }, Modifier.fillMaxWidth(), "https://xxxx.supabase.co")
                    Spacer(Modifier.height(8.dp))
                    FieldLabel("Server key"); TextField(key, { key = it }, Modifier.fillMaxWidth(), "anon key")
                    Spacer(Modifier.height(8.dp))
                    FieldLabel("Admin email"); TextField(email, { email = it }, Modifier.fillMaxWidth(), "you@school.com")
                    Spacer(Modifier.height(8.dp))
                    FieldLabel("Password"); PassField(pass, { pass = it })
                    Spacer(Modifier.height(12.dp))
                    Button({
                        busy = true; say(false, "Connecting…")
                        Thread {
                            var em = email.trim().lowercase()
                            var r = CloudApi.signUp(url, key, em, pass)
                            if (!r.ok && r.error.contains("already", true)) r = CloudApi.login(url, key, em, pass)
                            if (!r.ok) { busy = false; say(true, r.error); return@Thread }
                            val code = (1..8).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
                            val sc = state.data.school
                            val (sid, serr) = CloudApi.createSchool(url, key, r.accessToken, sc.name, sc.address, sc.address, sc.headTeacher, s.licencePlan.ifBlank { "Trial" }, s.licenceRef, state.data.students.size, code)
                            if (sid.isBlank()) { busy = false; say(true, serr); return@Thread }
                            state.repo.mutate("CLOUD_CONNECT", "Settings") { d -> d.copy(settings = d.settings.copy(
                                cloudUrl = url, cloudKey = key, cloudEmail = em, cloudAccessToken = r.accessToken, cloudRefreshToken = r.refreshToken,
                                cloudSchoolId = sid, cloudInviteCode = code, teacherCloudEnabled = true)) }
                            busy = false
                            say(false, "✓ Cloud account connected")
                        }.start()
                    }, enabled = !busy && email.isNotBlank() && pass.length >= 6, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(42.dp)) { Text("Connect", fontSize = 13.sp) }
                }
            }
        }
    }
}


// Material3 password field with named args (avoids positional-arg ambiguity with the local TextField wrapper)
@Composable
private fun PassField(value: String, onChange: (String) -> Unit) {
    androidx.compose.material3.TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        placeholder = { Text("min 6 chars", color = Theme.MUTED, fontSize = 13.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(color = Theme.TEXT, fontSize = 14.sp)
    )
}
