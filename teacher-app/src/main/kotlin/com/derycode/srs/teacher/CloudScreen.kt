package com.derycode.srs.teacher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

// ─────────────────────────────────────────────────────────────────────────────
// Cloud — teacher account, join school with invite code, pull setup, send marks
// v3 flow: invite code → CREATE ACCOUNT (name, phone, role, photo, password)
// → pick classes & subjects → stays active until the school disconnects you.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CloudScreen(state: TeacherState) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgGood by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(if (state.cloud.signedIn) "main" else "quick") }
    var schools by remember { mutableStateOf(state.cloudSchools) }

    // registration form
    var inviteCode by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf(state.me?.name ?: "") }
    var phone by remember { mutableStateOf(state.me?.phone ?: "") }
    var contacts by remember { mutableStateOf("") }
    var myRole by remember { mutableStateOf("TEACHER") }
    var password by remember { mutableStateOf("") }
    var photoB64 by remember { mutableStateOf("") }

    // login form
    var loginId by remember { mutableStateOf(state.cloud.account.email) }
    var loginPass by remember { mutableStateOf("") }

    // step 2: classes & subjects picked
    val pickedClasses = remember { mutableStateListOf<String>() }
    val pickedSubjects = remember { mutableStateListOf<String>() }

    // school the registration is finishing against
    var pendingSchoolId by remember { mutableStateOf("") }

    fun report(ok: Boolean, text: String) { msgGood = ok; msg = text }
    fun run(block: suspend () -> Unit) { if (!busy) { busy = true; scope.launch(Dispatchers.IO) { block(); busy = false } } }

    fun loadSchools() = run {
        val t = state.cloud.freshToken()
        if (!t.ok) { report(false, t.msg); return@run }
        schools = state.cloud.linkedSchools(t.token)
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@rememberLauncherForActivityResult
            val maxSide = 400
            val scale = minOf(1f, maxSide.toFloat() / maxOf(raw.width, raw.height))
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 78, out)
            photoB64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) { }
    }

    // ── STEP 1: invite code → create your account ──
    if (mode == "quick" && !state.cloud.signedIn) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = BLUE, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Cell("Join your school", MUTED, true)
                }
                Spacer(Modifier.height(4.dp))
                Cell("Enter YOUR personal invite code from your head teacher. It creates your account, registers you with the school, and downloads your classes, students and subjects.", MUTED)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(inviteCode, { inviteCode = it }, label = { Text("Your personal code") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(fullName, { fullName = it }, label = { Text("Your full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone number — you will use it to log in") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(contacts, { contacts = it }, label = { Text("Other contacts — email / WhatsApp (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Choose a password (min 6 characters)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Cell("Your role at the school", MUTED, true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Teacher" to "TEACHER", "Head Teacher" to "HEAD_TEACHER", "Admin" to "SCHOOL_ADMIN", "Data Entry" to "DATA_ENTRY").forEach { (label, value) ->
                        FilterChip(selected = myRole == value, onClick = { myRole = value }, label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = if (myRole == value) BLUE else Color.White))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(64.dp).background(NAVY, CircleShape), contentAlignment = Alignment.Center) {
                        if (photoB64.isNotBlank()) {
                            val bmp = remember(photoB64) {
                                try { BitmapFactory.decodeByteArray(android.util.Base64.decode(photoB64, android.util.Base64.NO_WRAP), 0, 10_000_000).asImageBitmap() } catch (_: Exception) { null }
                            }
                            if (bmp != null) Image(bmp, contentDescription = "My photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
                        } else Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Column {
                        Cell("Passport photo (optional)", MUTED, true)
                        TextButton(onClick = { photoPicker.launch("image/*") }) { Text("Add photo", color = BLUE) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    run {
                        val codeUp = inviteCode.trim().uppercase()
                        if (codeUp.isBlank()) { report(false, "Enter the invite code from your school's admin console"); return@run }
                        if (fullName.isBlank()) { report(false, "Enter your full name so the school knows who you are"); return@run }
                        if (phone.filter { it.isLetterOrDigit() }.length < 7) { report(false, "Enter your phone number — you will use it to log in"); return@run }
                        if (password.length < 6) { report(false, "Choose a password of at least 6 characters"); return@run }
                        // 1. create the teacher's own account (phone = login)
                        report(true, "Creating your account…")
                        var r = state.cloud.signUp(phone, password, fullName)
                        if (!r.ok && r.msg.contains("already", true)) r = state.cloud.login(phone, password)
                        if (!r.ok) { report(false, r.msg); return@run }
                        // 2. register with the school via the invite code
                        report(true, "Registering you with your school…")
                        val j = state.cloud.registerTeacher(r.token, codeUp, fullName, myRole, phone.trim(), contacts.trim(), "", "", photoB64)
                        if (!j.ok) { report(false, j.msg); return@run }
                        // 3. download the school's setup
                        val list = state.cloud.linkedSchools(r.token)
                        schools = list
                        val target = list.firstOrNull()
                        if (target == null) { mode = "main"; report(false, "Registered ✓ — tap 'Pull school setup' below to download the school"); return@run }
                        pendingSchoolId = target.id
                        report(true, "Downloading ${target.name}'s setup — classes, students, subjects…")
                        val (payload, err) = state.cloud.pullSchoolConfig(r.token, target.id)
                        if (payload.isBlank()) { mode = "main"; report(false, "Registered with ${target.name} ✓ — now tap 'Pull school setup' below ($err)"); return@run }
                        val res = state.importCloudConfig(payload)
                        if (!res.first) { mode = "main"; report(false, "Registered ✓ but the setup could not be imported: ${res.second} — tap 'Pull school setup' below"); return@run }
                        // 4. step 2: pick classes & subjects
                        msg = ""
                        mode = "pick"
                    }
                }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = BLUE), modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Please wait…" else "Create account & join school", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { mode = "login" }) {
                    Text("I already have an account — sign in", color = MUTED, fontSize = 15.sp)
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

    // ── STEP 2: pick the classes & subjects you teach ──
    if (mode == "pick") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FactCheck, contentDescription = null, tint = BLUE, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Cell("What do you teach?", MUTED, true)
                }
                Spacer(Modifier.height(4.dp))
                Cell("Tap every class you teach, then every subject you teach. A teacher can teach many classes and different subjects. You can change this any time.", MUTED)
                Spacer(Modifier.height(12.dp))
                Cell("My classes", MUTED, true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.data.classes.filter { it.active }.forEach { c ->
                        val nm = if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}"
                        FilterChip(selected = pickedClasses.contains(c.id), onClick = {
                            if (pickedClasses.contains(c.id)) pickedClasses.remove(c.id) else pickedClasses.add(c.id)
                        }, label = { Text(nm, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = if (pickedClasses.contains(c.id)) BLUE else Color.White))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Cell("My subjects", MUTED, true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.data.subjects.filter { it.active }.forEach { sb ->
                        FilterChip(selected = pickedSubjects.contains(sb.id), onClick = {
                            if (pickedSubjects.contains(sb.id)) pickedSubjects.remove(sb.id) else pickedSubjects.add(sb.id)
                        }, label = { Text(sb.name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = if (pickedSubjects.contains(sb.id)) BLUE else Color.White))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    run {
                        val t = state.cloud.freshToken()
                        if (!t.ok) { report(false, t.msg); return@run }
                        if (pendingSchoolId.isBlank()) pendingSchoolId = schools.firstOrNull()?.id ?: ""
                        val clsNames = pickedClasses.mapNotNull { id -> state.data.classes.firstOrNull { it.id == id } }
                            .joinToString(", ") { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" }
                        val subjNames = pickedSubjects.mapNotNull { id -> state.data.subjects.firstOrNull { it.id == id } }.joinToString(", ") { it.name }
                        val r = state.cloud.updateMyProfile(t.token, pendingSchoolId, fullName, myRole, phone.trim(), contacts.trim(), clsNames, subjNames, "")
                        if (!r.ok) { report(false, r.msg); return@run }
                        mode = "main"
                        report(true, "You are all set ✓ — your account stays active until the head teacher disconnects you. Send your marks any time from below.")
                    }
                }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = BLUE), modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Saving…" else "Save & finish", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { mode = "main" }) { Text("Skip for now — I'll choose later", color = MUTED, fontSize = 14.sp) }
            }
            if (msg.isNotEmpty()) {
                CardBox { Cell(msg, if (msgGood) GOOD else ORANGE) }
            }
        }
        return
    }

    // ── sign in (existing account: phone or email + password) ──
    if (mode == "login" && !state.cloud.signedIn) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cloud, contentDescription = null, tint = BLUE, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Cell("Sign in to your cloud account", MUTED, true)
                }
                Spacer(Modifier.height(4.dp))
                Cell("Use the phone number or email you registered with, and your password.", MUTED)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(loginId, { loginId = it }, label = { Text("Phone number or email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(loginPass, { loginPass = it }, label = { Text("Password") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    run {
                        if (loginId.isBlank() || loginPass.length < 6) { report(false, "Enter your phone/email and password"); return@run }
                        val r = state.cloud.login(loginId, loginPass)
                        if (!r.ok) { report(false, r.msg); return@run }
                        mode = "main"
                        loadSchools()
                        report(true, "Signed in ✓ — your account stays active until you sign out.")
                    }
                }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = BLUE), modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Please wait…" else "Sign in", color = Color.White)
                }
                TextButton(onClick = { mode = "quick" }) {
                    Text("I have an invite code — register me instead", color = BLUE, fontSize = 15.sp)
                }
                if (msg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Cell(msg, if (msgGood) GOOD else ORANGE) }
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
            Cell("You stay signed in until the head teacher disconnects you or you sign out — no need to log in every time.", MUTED)
            Spacer(Modifier.height(10.dp))
            if (schools.isEmpty()) {
                Cell("No school linked yet. Ask your head teacher for YOUR personal invite code (Teachers screen on the Admin Console), then enter it below.", MUTED)
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
                    val r = state.cloud.registerTeacher(t.token, inviteCode, state.me?.name ?: fullName.ifBlank { "Teacher" }, myRole, phone.trim(), contacts.trim(), "", "", photoB64)
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
            Cell("Send my marks: uploads your marks bundle to the school cloud — the head teacher imports it with one tap on the console. You can send any time, as many times as you want.", MUTED)
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
            Button(onClick = { state.cloud.signOut(); mode = "quick"; schools = emptyList(); report(true, "Signed out. Your marks stay on this phone.") },
                colors = ButtonDefaults.buttonColors(containerColor = CARD_ALT)) {
                Text("Sign out (marks stay on phone)", color = NAVY, fontSize = 15.sp)
            }
        }
    }
}
