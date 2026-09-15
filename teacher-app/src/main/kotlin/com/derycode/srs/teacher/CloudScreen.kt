package com.derycode.srs.teacher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Cloud — teacher account, join school with invite code, pull setup, send marks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CloudScreen(state: TeacherState) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgGood by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(if (state.cloud.signedIn) "main" else "quick") }
    var schools by remember { mutableStateOf(state.cloudSchools) }
    var inviteCode by remember { mutableStateOf("") }

    var email by remember { mutableStateOf(state.cloud.account.email) }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf(state.me?.name ?: "") }
    var phone by remember { mutableStateOf(state.me?.phone ?: "") }

    fun report(ok: Boolean, text: String) { msgGood = ok; msg = text }
    fun run(block: suspend () -> Unit) { if (!busy) { busy = true; scope.launch(Dispatchers.IO) { block(); busy = false } } }

    fun loadSchools() = run {
        val t = state.cloud.freshToken()
        if (!t.ok) { report(false, t.msg); return@run }
        schools = state.cloud.linkedSchools(t.token)
    }

    // ── v2.2.7: ONE-STEP join — invite code does everything ──
    if (mode == "quick" && !state.cloud.signedIn) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = BLUE, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Cell("Join your school", MUTED, true)
                }
                Spacer(Modifier.height(4.dp))
                Cell("Enter the invite code from your school's admin console (Cloud & Online). The code registers you, links you to the school and downloads your classes, students and subjects — no email or password needed.", MUTED)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(inviteCode, { inviteCode = it }, label = { Text("School code — 8 characters, e.g. 7KPQ3MB2") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(fullName, { fullName = it }, label = { Text("Your full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    run {
                        val codeUp = inviteCode.trim().uppercase()
                        if (codeUp.isBlank()) { report(false, "Enter the invite code from your school's admin console"); return@run }
                        if (fullName.isBlank()) { report(false, "Enter your full name so the school knows who you are"); return@run }
                        // 1. automatic cloud account — teacher never sees email/password
                        report(true, "Creating your account…")
                        val suffix = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
                        val autoEmail = "t$suffix@srs-teacher.app"
                        val autoPass = java.util.UUID.randomUUID().toString()
                        val r = state.cloud.signUp(autoEmail, autoPass, fullName)
                        if (!r.ok) { report(false, r.msg); return@run }
                        // 2. link to the school
                        report(true, "Linking you to your school…")
                        val j = state.cloud.joinSchool(r.token, codeUp, fullName)
                        if (!j.ok) { report(false, j.msg); return@run }
                        // 3. school details
                        val list = state.cloud.linkedSchools(r.token)
                        schools = list
                        val target = list.firstOrNull()
                        if (target == null) { mode = "main"; report(false, "Joined ✓ — tap 'Pull school setup' below to download the school"); return@run }
                        // 4. pull the school's data (classes, students, subjects, config)
                        report(true, "Downloading ${target.name}'s setup — classes, students, subjects…")
                        val (payload, err) = state.cloud.pullSchoolConfig(r.token, target.id)
                        if (payload.isBlank()) { mode = "main"; report(false, "Joined ${target.name} ✓ — now tap 'Pull school setup' below ($err)"); return@run }
                        val res = state.importCloudConfig(payload)
                        mode = "main"
                        if (res.first) {
                            val upd = try { UpdateChecker.checkBlocking(BuildConfig.VERSION_NAME) } catch (_: Exception) { null }
                            report(true, "Joined ${target.name} ✓ — your classes, students and subjects are on this phone. Welcome, ${fullName.split(" ").first()}!" +
                                (if (upd != null) "  A newer app version is available — update it from Support." else ""))
                        } else {
                            report(false, "Joined ${target.name} ✓ but the setup could not be imported: ${res.second} — tap 'Pull school setup' below")
                        }
                    }
                }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = BLUE), modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Please wait…" else "Join & set up my phone", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { mode = "login" }) {
                    Text("I already have a cloud account", color = MUTED, fontSize = 15.sp)
                }
            }
            if (msg.isNotEmpty()) {
                CardBox {
                    Cell(if (msgGood) "Status" else "Attention", MUTED, true)
                    Spacer(Modifier.height(4.dp))
                    Cell(msg, if (msgGood) GOOD else ORANGE)
                }
            }
        }
        return
    }

    if (mode == "signup" || mode == "login") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cloud, contentDescription = null, tint = BLUE, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Cell(if (mode == "signup") "Create your cloud account" else "Sign in to your cloud account", MUTED, true)
                }
                Spacer(Modifier.height(4.dp))
                Cell("One account works for every school you teach in. Your email and password stay private — the school never sees your password.", MUTED)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text(if (mode == "signup") "Choose a password (min 6 characters)" else "Password") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (mode == "signup") {
                    OutlinedTextField(fullName, { fullName = it }, label = { Text("Your full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        run {
                            if (email.isBlank() || password.length < 6) { report(false, "Enter a valid email and a password of at least 6 characters"); return@run }
                            val r = if (mode == "signup") state.cloud.signUp(email, password, fullName.ifBlank { "Teacher" })
                                    else state.cloud.login(email, password)
                            if (!r.ok) { report(false, r.msg); return@run }
                            mode = "main"
                            schools = state.cloud.linkedSchools(r.token)
                            report(true, if (mode == "signup") "" else "Signed in ✓")
                            msg = if (schools.isEmpty()) "Signed in ✓ — now join your school with its invite code below."
                                  else "Signed in ✓ — ${schools.size} school(s) linked."
                        }
                    }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = BLUE)) {
                        Text(if (busy) "Please wait…" else if (mode == "signup") "Create account" else "Sign in", color = Color.White)
                    }
                    TextButton(onClick = { mode = if (mode == "signup") "login" else "signup" }) {
                        Text(if (mode == "signup") "I already have an account" else "Create a new account", color = MUTED, fontSize = 15.sp)
                    }
                    TextButton(onClick = { mode = "quick" }) {
                        Text("Use a school invite code instead (easiest)", color = BLUE, fontSize = 15.sp)
                    }
                }
                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Cell(msg, if (msgGood) GOOD else ORANGE)
                }
            }
        }
        return
    }

    // ── Signed-in main cloud view ──
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudDone, contentDescription = null, tint = GOOD, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Cell("Cloud account", MUTED, true)
                    Cell(state.cloud.account.email.ifBlank { "signed in" }, MUTED)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (schools.isEmpty()) {
                Cell("No school linked yet. Ask your head teacher for the school's invite code (it's shown on the Admin Console), then enter it below.", MUTED)
            } else {
                Cell("Your schools:", MUTED, true)
                schools.forEach { Cell("• ${it.name.ifBlank { it.id.take(8) }}", NAVY) }
                Cell("School on this phone: ${state.data.school.name.ifBlank { "not imported yet" }}", MUTED)
            }
        }

        CardBox {
            Cell("Join a school", MUTED, true)
            Spacer(Modifier.height(4.dp))
            Cell("The head teacher finds the invite code on the console: Cloud → School account.", MUTED)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(inviteCode, { inviteCode = it }, label = { Text("Invite code (e.g. ABC-1234)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                run {
                    val t = state.cloud.freshToken()
                    if (!t.ok) { report(false, t.msg); return@run }
                    val r = state.cloud.joinSchool(t.token, inviteCode, state.me?.name ?: fullName.ifBlank { "Teacher" })
                    if (!r.ok) { report(false, r.msg); return@run }
                    schools = state.cloud.linkedSchools(t.token)
                    report(true, "Joined ✓ — pull the school's setup below.")
                }
            }, enabled = !busy && inviteCode.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = BLUE)) {
                Text(if (busy) "Please wait…" else "Join school", color = Color.White)
            }
        }

        CardBox {
            Cell("Sync with the school", MUTED, true)
            Spacer(Modifier.height(4.dp))
            Cell("Pull school setup: downloads classes, students and subject config pushed by the console. Your own marks are always kept.", MUTED)
            Spacer(Modifier.height(6.dp))
            Cell("Send my marks: uploads your marks bundle to the school cloud — the head teacher imports it with one tap on the console.", MUTED)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    run {
                        val t = state.cloud.freshToken()
                        if (!t.ok) { report(false, t.msg); return@run }
                        val target = schools.firstOrNull()
                        if (target == null) { report(false, "Join a school first"); return@run }
                        val (payload, err) = state.cloud.pullSchoolConfig(t.token, target.id)
                        if (payload.isBlank()) { report(false, err); return@run }
                        val res = state.importCloudConfig(payload)
                        report(res.first, res.second)
                    }
                }, enabled = !busy && schools.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = BLUE)) {
                    Text("Pull school setup", color = Color.White, fontSize = 15.sp)
                }
                Button(onClick = {
                    run {
                        val t = state.cloud.freshToken()
                        if (!t.ok) { report(false, t.msg); return@run }
                        val target = schools.firstOrNull()
                        if (target == null) { report(false, "Join a school first"); return@run }
                        val payload = state.buildMarksBundle()
                        if (payload == null) { report(false, "Nothing to send — no marks on this phone yet"); return@run }
                        val r = state.cloud.pushMarks(t.token, target.id, state.me?.name ?: "Teacher", payload)
                        report(r.ok, r.msg)
                    }
                }, enabled = !busy && schools.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = GOOD)) {
                    Text("Send my marks", color = Color(0xFF06281A), fontSize = 15.sp)
                }
            }
        }

        if (msg.isNotEmpty()) {
            CardBox {
                Text(msg, color = if (msgGood) GOOD else ORANGE, fontSize = 16.sp)
            }
        }

        CardBox {
            Button(onClick = { state.cloud.signOut(); mode = "signup"; schools = emptyList(); report(true, "Signed out. Your marks stay on this phone.") },
                colors = ButtonDefaults.buttonColors(containerColor = CARD_ALT)) {
                Text("Sign out (marks stay on phone)", color = NAVY, fontSize = 15.sp)
            }
        }
    }
}
