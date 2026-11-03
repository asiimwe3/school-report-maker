package com.derycode.srs.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.SchoolIsolation
import com.derycode.srs.core.model.SchoolCategory
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
    fun go(i: Int) { if (i < step) step = i }
    Row(Modifier.fillMaxSize().background(Theme.NAVY)) {
        WizardRail(step, ::go)
        Column(Modifier.weight(1f).fillMaxHeight().padding(26.dp)) {
            WizardTopSteps(step, ::go)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().weight(1f).background(Theme.CARD, RoundedCornerShape(16.dp)).padding(30.dp)
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

/** The branded rail: logo, the six steps down the page, the school slogan at the foot. */
@Composable
private fun WizardRail(step: Int, onGo: (Int) -> Unit) {
    val light = Color.White
    Column(Modifier.width(252.dp).fillMaxHeight().background(Theme.DEEP).padding(horizontal = 20.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(Theme.CARD, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                GradCapIcon(Modifier.size(28.dp), Theme.DEEP)
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text("DERYCODE", color = light, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                Text("SCHOOL REPORT SYSTEM", color = light.copy(alpha = 0.72f), fontSize = 9.sp, letterSpacing = 0.6.sp)
                Text("Simple \u2022 Fast \u2022 Reliable", color = light.copy(alpha = 0.5f), fontSize = 9.5.sp)
            }
        }
        Spacer(Modifier.height(30.dp))
        STEPS.forEachIndexed { i, label ->
            val done = i < step
            val active = i == step
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                    .clickable(enabled = done) { onGo(i) }
                    .background(if (active) light.copy(alpha = 0.1f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(24.dp).background(
                        when { done -> Theme.GOOD; active -> Theme.GOLD; else -> Color.Transparent },
                        RoundedCornerShape(12.dp)
                    ).then(if (!done && !active) Modifier.border(1.dp, light.copy(alpha = 0.35f), RoundedCornerShape(12.dp)) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (done) "\u2713" else "${i + 1}",
                        color = if (done || active) Color.White else light.copy(alpha = 0.6f),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    color = when { active -> light; done -> light.copy(alpha = 0.85f); else -> light.copy(alpha = 0.5f) },
                    fontSize = 12.5.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (i < STEPS.lastIndex) {
                Box(Modifier.padding(start = 19.dp).width(2.dp).height(14.dp).background(light.copy(alpha = 0.15f)))
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Smart Schools", color = light.copy(alpha = 0.55f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        Text("Stronger Communities", color = light.copy(alpha = 0.4f), fontSize = 10.sp)
    }
}

/** Compact progress strip across the top of the working area. */
@Composable
private fun WizardTopSteps(step: Int, onGo: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
        STEPS.forEachIndexed { i, label ->
            val active = i == step
            val done = i < step
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = done) { onGo(i) }) {
                Box(
                    Modifier.size(22.dp).background(
                        when { active -> Theme.ACCENT; done -> Theme.GOOD; else -> Theme.LINE },
                        RoundedCornerShape(11.dp)
                    ), contentAlignment = Alignment.Center
                ) {
                    Text(if (done) "\u2713" else "${i + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (active) Theme.TEXT else Theme.MUTED, fontSize = 12.sp, softWrap = false,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
            if (i < STEPS.lastIndex) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.width(26.dp).height(2.dp).background(Theme.LINE))
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

/** One of the four "what this setup does" chips on the welcome screen. */
@Composable
private fun WelcomeCheck(label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).background(Theme.ACCENT_SOFT, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Text("\u2713", color = Theme.ACCENT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = Theme.MUTED, fontSize = 11.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** An eye glyph for the PIN fields — drawn, so it needs no icon font. */
@Composable
private fun EyeGlyph(hidden: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val stroke = w * 0.09f
        val path = Path().apply {
            moveTo(w * 0.08f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.1f, w * 0.92f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.9f, w * 0.08f, h * 0.5f)
        }
        drawPath(path, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        if (hidden) {
            drawLine(tint, Offset(w * 0.1f, h * 0.9f), Offset(w * 0.9f, h * 0.1f), strokeWidth = stroke)
        } else {
            drawCircle(tint, radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
        }
    }
}

/** PIN box: masked until the eye is tapped. */
@Composable
private fun PinField(value: String, onChange: (String) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (shown) androidx.compose.ui.text.input.VisualTransformation.None
        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
        placeholder = { Text("4\u20136 digits", color = Theme.MUTED, fontSize = 13.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(color = Theme.TEXT, fontSize = 15.sp),
        trailingIcon = {
            IconButton({ shown = !shown }, modifier = Modifier.size(34.dp)) {
                EyeGlyph(hidden = !shown, tint = Theme.MUTED, modifier = Modifier.size(18.dp))
            }
        },
        shape = RoundedCornerShape(9.dp)
    )
}

@Composable
private fun StepWelcome(state: AppState, next: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradCapIcon(Modifier.size(38.dp), Theme.ACCENT)
            Spacer(Modifier.width(12.dp))
            Text("Welcome to DeryCode School Report Maker", color = Theme.TEXT, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("This short setup creates your school's admin console. It takes about 3 minutes and is required before the dashboard opens.",
            color = Theme.MUTED, fontSize = 13.sp, modifier = Modifier.widthIn(max = 640.dp))
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth().widthIn(max = 640.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WelcomeCheck("School Profile", Modifier.weight(1f))
            WelcomeCheck("Online Account", Modifier.weight(1f))
            WelcomeCheck("Security PIN", Modifier.weight(1f))
            WelcomeCheck("Licence", Modifier.weight(1f))
        }
        Spacer(Modifier.height(32.dp))
        Button({ next() }, shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Theme.GOOD, contentColor = Color.White),
            modifier = Modifier.height(48.dp).width(230.dp)) {
            Text("Get Started  \u203A", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text("Only the online account needs internet. School profile, PIN and licence can be changed later in Settings.",
            color = Theme.MUTED, fontSize = 11.sp, modifier = Modifier.widthIn(max = 640.dp))
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
        Spacer(Modifier.height(14.dp))
        // ── Primary / Secondary / Combined: the console only ever shows this ladder ──
        Text("What kind of school is this?", color = Theme.TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SchoolCategory.entries.forEach { cat ->
                val on = com.derycode.srs.core.model.SchoolScope.fromStored(s.category).category == cat
                Column(
                    Modifier.weight(1f)
                        .background(if (on) Theme.ACCENT_SOFT else Theme.CARD, RoundedCornerShape(10.dp))
                        .border(if (on) 2.dp else 1.dp, if (on) Theme.ACCENT else Theme.LINE, RoundedCornerShape(10.dp))
                        .clickable { s = s.copy(category = cat.name) }
                        .padding(11.dp)
                ) {
                    Text(cat.label, color = if (on) Theme.ACCENT else Theme.TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(cat.detail, color = Theme.MUTED, fontSize = 10.sp)
                }
            }
        }
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
            Button({ if (s.name.isBlank()) err = true else {
                    state.repo.mutate("SETUP_SCHOOL", "School") { d -> d.copy(school = s) }
                    applySchoolCategory(state, com.derycode.srs.core.model.SchoolScope.fromStored(s.category).category)
                    next()
                } },
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
            Box(Modifier.fillMaxWidth().background(Theme.ACCENT_SOFT, RoundedCornerShape(10.dp)).padding(16.dp)) {
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
                    state.refresh()   // make the invite code visible immediately
                    linked = true
                }.start()
            }, enabled = !busy, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(44.dp).width(220.dp)) {
                Text(if (busy) "Creating account…" else "Create Online Account", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton({
                state.repo.mutate("CLOUD_SKIP", "Settings") { d -> d.copy(settings = d.settings.copy(cloudUrl = url, cloudKey = key, teacherCloudEnabled = false)) }; state.refresh()
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
            Column(Modifier.weight(1f)) { FieldLabel("PIN (4–6 digits)"); PinField(pin, { pin = it.filter { c -> c.isDigit() }.take(6) }) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { FieldLabel("Confirm PIN"); PinField(pin2, { pin2 = it.filter { c -> c.isDigit() }.take(6) }) }
        }
        Spacer(Modifier.height(10.dp))
        if (msg.isNotEmpty()) Text(msg, color = if (err) Color(0xFFFF6B6B) else Theme.MUTED, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().widthIn(max = 560.dp).background(Theme.ACCENT_SOFT, RoundedCornerShape(9.dp)).padding(11.dp)) {
            Text("Use a PIN that is easy for you to remember but hard for others to guess. Avoid simple numbers like 1234 or 0000.",
                color = Theme.MUTED, fontSize = 11.5.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row {
            Button({
                msg = ""; err = false
                if (pin.length < 4) { msg = "PIN must be 4–6 digits."; err = true }
                else if (pin != pin2) { msg = "PINs do not match."; err = true }
                else {
                    state.repo.mutate("SETUP_PIN", "Settings") { d -> d.copy(settings = d.settings.copy(adminPinHash = LicenseKeys.hash("pin|$pin"))) }; state.refresh()
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
                state.repo.mutate("LICENCE_ACTIVATE", "Settings") { d -> d.copy(settings = d.settings.copy(licencePlan = plan.name, licenceRef = key, licenceExpiry = expiry)) }; state.refresh()
                next()
            }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(44.dp).width(170.dp)) { Text("Activate Key", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            OutlinedButton({
                val expiry = java.time.LocalDate.now().plusDays(30).toString().replace("-", "")
                state.repo.mutate("TRIAL_START", "Settings") { d -> d.copy(settings = d.settings.copy(licencePlan = "Trial", licenceRef = "TRIAL", licenceExpiry = expiry)) }; state.refresh()
                next()
            }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(44.dp)) { Text("Start 30-day Trial", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun StepDone(state: AppState) {
    val s = state.data.settings
    val school = state.data.school
    val online = s.cloudSchoolId.isNotBlank()
    val pinSet = s.adminPinHash.isNotBlank()
    val licensed = s.licencePlan.isNotBlank() && s.licencePlan != "None"
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradCapIcon(Modifier.size(38.dp), Theme.ACCENT)
            Spacer(Modifier.width(12.dp))
            Text("Setup complete!", color = Theme.TEXT, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(26.dp).background(Theme.GOOD, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Text("\u2713", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Your DeryCode School Report Maker is ready to use. You can now open your dashboard and start managing your school reports.",
            color = Theme.MUTED, fontSize = 13.sp, modifier = Modifier.widthIn(max = 660.dp))
        Spacer(Modifier.height(22.dp))

        // ── School Profile Summary ───────────────────────────────────────────
        Box(Modifier.fillMaxWidth().widthIn(max = 520.dp).background(Theme.ACCENT_SOFT, RoundedCornerShape(14.dp)).padding(18.dp)) {
            Column {
                Text("School Profile Summary", color = Theme.TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                SummaryRow("School name", school.name.ifBlank { "—" })
                SummaryRow("District / Town", school.address.ifBlank { "—" })
                SummaryRow("Head teacher", school.headTeacher.ifBlank { "—" })
                SummaryRow("Phone", school.phone.ifBlank { "—" })
                SummaryRow("School email", school.email.ifBlank { "—" }, last = true)
            }
        }
        Spacer(Modifier.height(18.dp))

        // ── What is connected ────────────────────────────────────────────────
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
            StatusRow("Online Account", if (online) "Connected" else "Offline mode", online)
            StatusRow("Security PIN", if (pinSet) "Set" else "Not set", pinSet)
            StatusRow("Licence", if (licensed) "${s.licencePlan} · Active" else "Not activated", licensed)
        }

        if (online && s.cloudInviteCode.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().widthIn(max = 520.dp).background(Theme.CARD, RoundedCornerShape(12.dp)).border(1.dp, Theme.LINE, RoundedCornerShape(12.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Teacher school code", color = Theme.MUTED, fontSize = 11.sp)
                        Text(s.cloudInviteCode, color = Theme.ACCENT, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(4.dp))
                        Text("Every teacher scans this one code with the SRS Teacher app — they are connected instantly and their marks sync automatically.",
                            color = Theme.MUTED, fontSize = 11.sp, modifier = Modifier.width(300.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    InviteQr(s.cloudInviteCode, size = 118)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button({
            state.repo.mutate("SETUP_COMPLETE", "Settings") { d -> d.copy(settings = d.settings.copy(setupComplete = true)) }
            state.refresh()   // without this the first-run gate never lifted — v2.2.1 fix
            state.screen = "dashboard"
            state.unlocked = true
            if (s.cloudSchoolId.isNotBlank()) CloudSync.pushNow(state.repo)
        }, shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Theme.GOOD, contentColor = Color.White),
            modifier = Modifier.height(48.dp).width(250.dp)) {
            Text("Open Dashboard  \u203A", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, last: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Theme.MUTED, fontSize = 11.5.sp, modifier = Modifier.width(130.dp))
            Text(value, color = Theme.TEXT, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.LINE.copy(alpha = 0.5f)))
    }
}

@Composable
private fun StatusRow(label: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(18.dp).background(if (ok) Theme.GOOD else Theme.LINE, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) { Text(if (ok) "\u2713" else "!", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Text(label, color = Theme.TEXT, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (ok) Theme.GOOD else Theme.WARN, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
/** Opens WhatsApp with a ready-to-send teacher invite message ("make it simple to use"). */
fun shareTeacherInvite(schoolName: String, code: String, teacherName: String) {
    val msg = "Hello ${teacherName.ifBlank { ""}}! Here is YOUR personal join code for $schoolName on the SRS Teacher app:" +
        "\n\nCode: $code\n\n1. Install the SRS Teacher app\n2. Open the Cloud tab" +
        "\n3. Enter this code: $code\n4. Choose your role and tap Join" +
        "\n\nYour classes and students download automatically. This code is yours alone — it stops working once you use it." +
        "\n\n— $schoolName"
    try {
        java.awt.Desktop.getDesktop().browse(java.net.URI(
            "https://wa.me/?text=" + java.net.URLEncoder.encode(msg, "UTF-8")))
    } catch (e: Exception) { e.printStackTrace() }
}

fun shareInviteOnWhatsApp(schoolName: String, code: String, headTeacher: String) {
    val msg = "Hello! Join $schoolName on the SRS Teacher app in 4 steps:" +
        "\n\n1. Install the SRS Teacher app\n2. Open the Cloud tab" +
        "\n3. Enter this school code: $code\n4. Type your full name and tap Join" +
        "\n\nYour classes and students download automatically." +
        "\n\n— $headTeacher"
    try {
        java.awt.Desktop.getDesktop().browse(java.net.URI(
            "https://wa.me/?text=" + java.net.URLEncoder.encode(msg, "UTF-8")))
    } catch (e: Exception) { e.printStackTrace() }
}

// Cloud screen (nav: SYSTEM → Cloud & Online)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CloudScreen(state: AppState) {
    val s = state.data.settings
    var msg by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // v2.2.18 — personal teacher codes
    var invName by remember { mutableStateOf("") }
    var invList by remember { mutableStateOf(emptyList<CloudApi.TeacherInvite>()) }
    var invMsg by remember { mutableStateOf("") }
    var invErr by remember { mutableStateOf(false) }
    var invBusy by remember { mutableStateOf(false) }
    // The school account's password is the only way back to this school's cloud
    // copy on another PC, so it can be revealed, copied and written down.
    var showAccountPw by remember { mutableStateOf(false) }

    fun say(e: Boolean, m: String) { err = e; msg = m }

    // Cloud self-check — answers "is my cloud working?" without needing any keys.
    var checks by remember { mutableStateOf(emptyList<CloudApi.CloudCheck>()) }
    var checkBusy by remember { mutableStateOf(false) }

    // v2.2.23 — self-heal an orphaned cloud link. If the school this console
    // points to was deleted/reset on the server (support cleanup, a fresh
    // demo reset, etc.) the console kept saying "Connected" forever and every
    // action below failed with a confusing error (e.g. "needs v2 script" on
    // Generate code, when the real cause is: this school no longer exists).
    // Detect it once when the screen opens and drop back to "not connected"
    // so the Create/Connect box reappears and the admin can reconnect.
    LaunchedEffect(s.cloudSchoolId) {
        if (s.cloudSchoolId.isNotBlank()) {
            val exists = CloudSync.withFreshToken(state.repo, errorOf = { "" }) { tok ->
                CloudApi.schoolExists(CloudSync.url(s), CloudSync.key(s), tok, s.cloudSchoolId)
            }
            if (!exists) {
                if (s.cloudInviteCode.isNotBlank()) {
                    // Keep the SAME code so teachers who already have it keep working.
                    say(true, "The cloud copy of your school went missing. Restoring it under the same code ${s.cloudInviteCode}…")
                    CloudSync.republish(state.repo) { ok, m ->
                        state.refresh()
                        say(ok, if (ok) "School restored online. Code ${s.cloudInviteCode} works again." else "Could not restore online: $m")
                    }
                } else {
                    state.repo.mutate("cloud-orphan-reset", "Settings") { d -> d.copy(settings = d.settings.copy(
                        cloudSchoolId = "", cloudInviteCode = "", cloudAccessToken = "", cloudRefreshToken = "")) }
                    say(true, "Your cloud school link is no longer valid on the server. Your local data is safe. Reconnect below, then tap Back Up Now.")
                }
            }
        }
    }

    // v2.2.16 fix: this screen lives inside the page-level verticalScroll column in
    // Main.kt — its own verticalScroll created infinite height constraints and crashed
    // the app the moment Cloud & Online was opened. The page already scrolls.
    Column(Modifier.fillMaxWidth()) {
        Text("Cloud & Online Backup", color = Theme.TEXT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Supabase cloud — automatic off-site backup and teacher sync.", color = Theme.MUTED, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))

        // ── Self-check: prove the cloud works, no keys needed ──
        Box(Modifier.fillMaxWidth().background(Theme.CARD, RoundedCornerShape(12.dp)).border(1.dp, Theme.LINE, RoundedCornerShape(12.dp)).padding(18.dp)) {
            Column {
                Text("Cloud status", color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Check the connection yourself. Nothing here changes your data.", color = Theme.MUTED, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Button({
                    if (checkBusy) return@Button
                    checkBusy = true
                    Thread {
                        checks = runCatching { CloudApi.diagnose(CloudSync.url(s), CloudSync.key(s)) }.getOrElse { emptyList() }
                        checkBusy = false
                    }.start()
                }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(38.dp)) {
                    Text(if (checkBusy) "Checking…" else "Run check", fontSize = 12.sp)
                }
                checks.forEach { c ->
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (c.ok) "✓" else "✕", color = if (c.ok) Theme.GOOD else Theme.WARN, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(c.label, color = Theme.TEXT, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(c.detail, color = if (c.ok) Theme.MUTED else Theme.WARN, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // Status card
        Box(Modifier.fillMaxWidth().background(Theme.CARD, RoundedCornerShape(12.dp)).border(1.dp, Theme.LINE, RoundedCornerShape(12.dp)).padding(18.dp)) {
            Column {
                if (s.cloudSchoolId.isNotBlank()) {
                    Text("● Connected as ${s.cloudEmail}", color = Theme.GOOD, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    // The school account is what holds this school's cloud copy. It is
                    // created with a generated password that used to be thrown away, so
                    // show it here: on a new PC or after a reset this is the only way in
                    // (the address has no mailbox, so "forgot password" cannot help).
                    Text("School account (keep this safe)", color = Theme.TEXT, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(s.cloudEmail, color = Theme.MUTED, fontSize = 12.sp)
                    if (s.cloudPassword.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (showAccountPw) s.cloudPassword else "•".repeat(s.cloudPassword.length.coerceIn(8, 18)),
                                color = Theme.MUTED, fontSize = 12.sp)
                            Text(if (showAccountPw) "hide" else "show", color = Theme.ACCENT, fontSize = 11.sp,
                                modifier = Modifier.clickable { showAccountPw = !showAccountPw })
                            Text("copy", color = Theme.ACCENT, fontSize = 11.sp, modifier = Modifier.clickable {
                                try {
                                    val sel = java.awt.datatransfer.StringSelection("${s.cloudEmail}\n${s.cloudPassword}")
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
                                    say(false, "School account copied. Paste it somewhere safe — a notebook or your phone's notes.")
                                } catch (e: Exception) { say(true, "Could not copy: ${e.message?.take(40)}") }
                            })
                        }
                        Text("Write this down. It is how a new PC gets this school's cloud data back.", color = Theme.MUTED, fontSize = 11.sp)
                    } else {
                        Text("Password not stored for this account (created before this update). If you ever need to sign in on a new PC, use Cloud → Restore from Cloud on a console that still has this school, or ask support to re-link it.", color = Theme.WARN, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Teacher school code", color = Theme.MUTED, fontSize = 11.sp)
                    Text(s.cloudInviteCode, color = Theme.ACCENT, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("Share this code with your teachers for phone app access.", color = Theme.MUTED, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button({
                            try {
                                val sel = java.awt.datatransfer.StringSelection(s.cloudInviteCode)
                                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
                            } catch (e: Exception) { e.printStackTrace() }
                        }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(40.dp)) { Text("Copy code", fontSize = 12.sp) }
                        Button({ shareInviteOnWhatsApp(state.data.school.name, s.cloudInviteCode, state.data.school.headTeacher.ifBlank { "The Head Teacher" }) },
                            shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FAF55)), modifier = Modifier.height(40.dp)) { Text("Share on WhatsApp", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Last cloud backup: ${s.cloudLastBackup.ifBlank { "never" }}", color = Theme.MUTED, fontSize = 12.sp)
                } else {
                    Text("● Cloud is off", color = Theme.WARN, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Teachers cannot join until this is on. One tap — no email, no password.", color = Theme.MUTED, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Button({
                        busy = true; say(false, "Turning on cloud… creating the school account and publishing your setup")
                        CloudSync.turnOn(state.repo) { ok, m ->
                            busy = false
                            say(!ok, m)
                            if (ok) state.refresh()
                        }
                    }, enabled = !busy, shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(44.dp)) {
                        Text(if (busy) "Turning on…" else "Turn on cloud \u2014 one tap", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("After this you get a school code. Teachers scan it and their phone fills with your classes, learners, subjects and timetable.", color = Theme.MUTED, fontSize = 11.sp)
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
        Text("Teacher Submissions", color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("Marks, attendance, duty logs, gate passes & new students your teachers submit from the phone app appear here.", color = Theme.MUTED, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Button({
            busy = true; say(false, "Checking for teacher submissions…")
            Thread {
                val st = state.data.settings
                val (bundles, m) = CloudSync.withFreshToken(state.repo, errorOf = { it.second }) { tok ->
                    CloudApi.pullTeacherBundles(CloudSync.url(st), CloudSync.key(st), tok, st.cloudSchoolId)
                }
                if (bundles.isEmpty()) { busy = false; say(m != "ok", if (m == "ok") "No new teacher submissions." else m); return@Thread }
                var imported = 0
                var foreign = 0
                var droppedRows = 0
                var summary = ""
                val tmp = File.createTempFile("srs-tmarks", ".json")
                for (b in bundles) {
                    tmp.writeText(b)
                    // Which school sent this? A bundle from another school is never
                    // merged into this one, however it reached the cloud.
                    val bundleSchool = try { state.repo.store.peekBundleSchool(tmp.toPath()) } catch (_: Exception) { "" }
                    // Silence is not proof of another school: an older bundle with no
                    // school name is accepted and then pruned row by row.
                    val fromThisSchool = bundleSchool.isBlank() || SchoolIsolation.sameSchool(
                        com.derycode.srs.core.model.SchoolData(
                            school = com.derycode.srs.core.model.School(name = bundleSchool)
                        ),
                        state.repo.data
                    )
                    if (!fromThisSchool) {
                        foreign++
                        continue
                    }
                    val report = state.repo.store.importBundle(state.repo.data, tmp.toPath())
                    if (report.success) {
                        imported++
                        state.repo.mutate("TEACHER_MARKS_IMPORTED", "Marks", new = report.message) { d ->
                            val merged = d.copy(marks = report.merged, students = report.mergedStudents, enrollments = report.mergedEnrollments,
                                enrollmentRequests = report.mergedRequests, dutyRecords = report.mergedDutyRecords,
                                gatePasses = report.mergedGatePasses, attendance = report.mergedAttendance)
                            // any row pointing at a learner this school does not have
                            // is another school's information: drop it, never show it
                            val (clean, dropped) = SchoolIsolation.prune(merged)
                            droppedRows += dropped
                            clean
                        }
                        summary = report.message
                    }
                }
                tmp.delete()
                if (imported > 0) CloudApi.consumeTeacherBundles(CloudSync.url(st), CloudSync.key(st), state.repo.data.settings.cloudAccessToken, st.cloudSchoolId)
                busy = false
                val extra = buildString {
                    if (foreign > 0) append(" Skipped $foreign submission(s) that belong to another school.")
                    if (droppedRows > 0) append(" Dropped $droppedRows row(s) that did not belong to this school.")
                }
                say(imported == 0, if (imported > 0) "✓ Imported $imported teacher submission(s). $summary$extra" else "Nothing imported.$extra" + if (foreign > 0) " Those marks stay with their own school." else "")
                state.refresh()
            }.start()
        }, enabled = s.cloudSchoolId.isNotBlank() && !busy, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.GOOD, contentColor = Color.White), modifier = Modifier.height(42.dp)) { Text("Pull Teacher Submissions", fontSize = 13.sp) }
        Spacer(Modifier.height(18.dp))

        // ── Personal teacher codes (v2.2.18) ─────────────────────────────────
        // Each teacher gets their own single-use code. It dies after first
        // use, so a code leaked on a WhatsApp group can never be reused by
        // someone else. The school-wide code above still works as fallback.
        if (s.cloudSchoolId.isNotBlank()) {
            Text("Personal Teacher Codes", color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Generate a separate one-time code for each teacher — it works once, then dies.", color = Theme.MUTED, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().background(Theme.CARD, RoundedCornerShape(12.dp)).border(1.dp, Theme.LINE, RoundedCornerShape(12.dp)).padding(18.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextField(invName, { invName = it }, Modifier.width(240.dp), "Teacher name")
                        Button({
                            if (invName.isBlank()) { invErr = true; invMsg = "Enter the teacher's name first."; return@Button }
                            invBusy = true; invMsg = ""
                            val url = CloudSync.url(s); val key = CloudSync.key(s)
                            val token = s.cloudAccessToken; val sid = s.cloudSchoolId
                            val nm = invName.trim()
                            Thread {
                                val code = (1..8).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
                                val (inv, m) = CloudSync.withFreshToken(state.repo, errorOf = { it.second }) { tok ->
                                    CloudApi.createTeacherInvite(url, key, tok, sid, nm, code)
                                }
                                if (inv.code.isNotEmpty()) {
                                    val (list, _) = CloudSync.withFreshToken(state.repo, errorOf = { it.second }) { tok ->
                                        CloudApi.listTeacherInvites(url, key, tok, sid)
                                    }
                                    invList = list
                                }
                                invBusy = false
                                invErr = inv.code.isEmpty()
                                invMsg = if (inv.code.isNotEmpty()) "✓ Code for $nm: ${inv.code} — share it with them once." else m
                            }.start()
                        }, enabled = !invBusy, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(40.dp)) { Text("Generate code", fontSize = 12.sp) }
                        OutlinedButton({
                            invBusy = true
                            val url = CloudSync.url(s); val key = CloudSync.key(s)
                            val token = s.cloudAccessToken; val sid = s.cloudSchoolId
                            Thread {
                                val (list, m) = CloudSync.withFreshToken(state.repo, errorOf = { it.second }) { tok ->
                                    CloudApi.listTeacherInvites(url, key, tok, sid)
                                }
                                invList = list
                                invBusy = false
                                invErr = list.isEmpty() && m != "ok"
                                invMsg = if (list.isEmpty() && m != "ok") m else ""
                            }.start()
                        }, enabled = !invBusy, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(40.dp)) { Text("Refresh list", fontSize = 12.sp) }
                    }
                    if (invMsg.isNotEmpty()) Text(invMsg, color = if (invErr) Color(0xFFFF6B6B) else Theme.GOOD, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(10.dp))
                    if (invList.isEmpty()) {
                        Text("No personal codes yet — generate one above. The school-wide code still works as a fallback.", color = Theme.MUTED, fontSize = 11.sp)
                    } else invList.forEach { inv ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(inv.code, color = if (inv.used) Theme.MUTED else Theme.ACCENT, fontSize = 15.sp, fontWeight = FontWeight.Black)
                            Text(inv.name, color = Theme.TEXT, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Text(if (inv.used) "used ✓" else "not used yet", color = if (inv.used) Theme.MUTED else Theme.GOOD, fontSize = 11.sp)
                            if (!inv.used) Button({
                                try {
                                    val sel = java.awt.datatransfer.StringSelection(inv.code)
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
                                } catch (e: Exception) { e.printStackTrace() }
                            }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Theme.ACCENT), modifier = Modifier.height(32.dp)) { Text("Copy", fontSize = 11.sp) }
                            if (!inv.used) Button({
                                shareInviteOnWhatsApp(state.data.school.name, inv.code, state.data.school.headTeacher.ifBlank { "The Head Teacher" })
                            }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FAF55)), modifier = Modifier.height(32.dp)) { Text("WhatsApp", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
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
                                cloudUrl = url, cloudKey = key, cloudEmail = em,
                                // keep the password the head teacher typed, so this console can
                                // show it later instead of locking the school out of its own cloud
                                cloudPassword = pass,
                                cloudAccessToken = r.accessToken, cloudRefreshToken = r.refreshToken,
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

// ─────────────────────────────────────────────────────────────────────────────
/** Vector graduation-cap icon drawn with Canvas — replaces emoji that break on
 *  systems without an emoji font. Scales with Modifier.size(). */
@Composable
fun GradCapIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val cx = w / 2f; val top = h * 0.16f
        val cap = Path().apply {
            // mortarboard (diamond)
            moveTo(cx, top)
            lineTo(w * 0.92f, top + h * 0.24f)
            lineTo(cx, top + h * 0.48f)
            lineTo(w * 0.08f, top + h * 0.24f)
            close()
        }
        val base = Path().apply {
            // head band (trapezoid)
            moveTo(w * 0.26f, top + h * 0.34f)
            lineTo(cx, top + h * 0.56f)
            lineTo(cx, top + h * 0.78f)
            lineTo(w * 0.26f, top + h * 0.56f)
            close()
            moveTo(w * 0.74f, top + h * 0.34f)
            lineTo(w * 0.74f, top + h * 0.56f)
            lineTo(cx, top + h * 0.78f)
            lineTo(cx, top + h * 0.56f)
            close()
        }
        drawPath(cap, tint)
        drawPath(base, tint)
        // tassel
        drawLine(tint, Offset(w * 0.92f, top + h * 0.26f), Offset(w * 0.92f, top + h * 0.72f), strokeWidth = h * 0.045f)
        drawCircle(tint, radius = h * 0.035f, center = Offset(w * 0.92f, top + h * 0.76f))
    }
}
