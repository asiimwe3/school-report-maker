package com.derycode.srs.teacher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Duty Desk — teacher on duty, gate passes, duty log
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DutyScreen(state: TeacherState) {
    val d = state.data
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { Spacer(Modifier.height(4.dp)) }
        // ── On-duty toggle + duty log ──
        item {
            CardBox {
                Row {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = BLUE, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Cell("Teacher on duty", MUTED, true)
                }
                Spacer(Modifier.height(6.dp))
                val duty = state.myDutyToday
                if (state.me == null) {
                    Cell("Pick your name on the Home screen first.", ORANGE)
                } else if (duty == null) {
                    Cell("You are NOT marked on duty today.", ORANGE)
                    Spacer(Modifier.height(8.dp))
                    var role by remember { mutableStateOf("Teacher on duty") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Teacher on duty", "Games", "Compound", "Library").forEach { r ->
                            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r, fontSize = 10.sp) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { state.startDuty(role) }, colors = ButtonDefaults.buttonColors(containerColor = GREEN)) {
                        Text("I'm on duty")
                    }
                } else {
                    Cell("\u2713 ON DUTY \u2014 ${duty.role}", GREEN, true)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { state.endDuty() }, colors = ButtonDefaults.buttonColors(containerColor = CARD_ALT)) {
                        Text("End duty", color = Color.White)
                    }
                    Spacer(Modifier.height(10.dp))
                    Cell("Duty log", MUTED, true)
                    duty.incidents.reversed().forEach {
                        Cell("\u2022 ${SimpleDateFormat("HH:mm", Locale.US).format(Date(it.at))} \u2014 ${it.note}", MUTED)
                    }
                    var note by remember { mutableStateOf("") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = note, onValueChange = { note = it },
                        placeholder = { Text("Log an incident or note\u2026", color = MUTED) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { if (note.isNotBlank()) { state.addIncident(note); note = "" } },
                        colors = ButtonDefaults.buttonColors(containerColor = BLUE)) { Text("Add to log") }
                }
            }
        }
        // ── Gate passes ──
        item {
            CardBox {
                Cell("Gate passes \u2014 students leaving school", MUTED, true)
                Spacer(Modifier.height(6.dp))
                if (state.myDutyToday == null) {
                    Cell("Only the teacher on duty issues gate passes.", ORANGE)
                } else {
                    var q by remember { mutableStateOf("") }
                    var sel by remember { mutableStateOf<String?>(null) }
                    var reason by remember { mutableStateOf("Sick bay") }
                    var dest by remember { mutableStateOf("") }
                    var back by remember { mutableStateOf("") }
                    var msg by remember { mutableStateOf("") }
                    OutlinedTextField(value = q, onValueChange = { q = it },
                        placeholder = { Text("Search student name\u2026", color = MUTED) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    val matches = d.students.filter {
                        (it.firstName + " " + it.lastName).contains(q, true) || it.admissionNo.contains(q, true)
                    }.take(6)
                    if (matches.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            matches.forEach { s ->
                                FilterChip(selected = sel == s.id, onClick = { sel = s.id },
                                    label = { Text("${s.firstName} ${s.lastName}".take(14), fontSize = 10.sp) })
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Sick bay", "Home pass", "Errand", "Other").forEach { r ->
                            FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r, fontSize = 10.sp) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = dest, onValueChange = { dest = it },
                        placeholder = { Text("Destination (e.g. clinic, home)\u2026", color = MUTED) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = back, onValueChange = { back = it },
                        placeholder = { Text("Expected back (e.g. 15:00)\u2026", color = MUTED) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (sel == null) msg = "Select the student first."
                        else { state.issueGatePass(sel!!, reason, dest, back); sel = null; dest = ""; back = ""; msg = "\u2713 Pass issued." }
                    }, colors = ButtonDefaults.buttonColors(containerColor = BLUE)) { Text("Issue gate pass") }
                    if (msg.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Cell(msg, if (msg.startsWith("\u2713")) GREEN else ORANGE) }
                    Spacer(Modifier.height(10.dp))
                }
                val todays = d.gatePasses.filter { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.outAt)) == state.todayKey }
                Cell("Today's passes (${todays.size})", MUTED, true)
                if (todays.isEmpty()) Cell("None today.", MUTED)
                todays.reversed().forEach { pass ->
                    val s = d.students.firstOrNull { it.id == pass.studentId }
                    val ret = pass.returnedAt
                    val status = if (ret == null) "OUT" else "back ${SimpleDateFormat("HH:mm", Locale.US).format(Date(ret))}"
                    Cell("${s?.firstName ?: "?"} ${s?.lastName ?: ""} \u2014 ${pass.reason}" +
                        (if (pass.destination.isBlank()) "" else " \u2192 ${pass.destination}") + " \u2014 $status",
                        if (pass.returnedAt == null) ORANGE else GREEN)
                    if (pass.returnedAt == null) {
                        TextButton(onClick = { state.markReturned(pass.id) }) { Text("Mark returned", color = GREEN) }
                    }
                }
            }
        }
        // ── All staff on duty today ──
        item {
            CardBox {
                Cell("Staff on duty today", MUTED, true)
                Spacer(Modifier.height(4.dp))
                val all = d.dutyRecords.filter { it.date == state.todayKey }
                if (all.isEmpty()) Cell("No one has marked themselves on duty yet.", MUTED)
                all.forEach { r ->
                    val t = d.teachers.firstOrNull { it.id == r.teacherId }
                    Cell("\u2022 ${t?.name ?: r.teacherId} \u2014 ${r.role} \u2014 ${r.incidents.size} log entries", Color.White)
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Register student — teacher-side enrollment
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RegisterScreen(state: TeacherState) {
    val d = state.data
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("M") }
    var classId by remember { mutableStateOf(d.classes.firstOrNull { it.active }?.id ?: "") }
    var guardian by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            CardBox {
                Row {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = BLUE, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Cell("Register a new student", MUTED, true)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = firstName, onValueChange = { firstName = it },
                    placeholder = { Text("First name\u2026", color = MUTED) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = lastName, onValueChange = { lastName = it },
                    placeholder = { Text("Last name\u2026", color = MUTED) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Cell("Sex", MUTED)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("M", "F").forEach { s ->
                        FilterChip(selected = sex == s, onClick = { sex = s }, label = { Text(s, fontSize = 10.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Cell("Class", MUTED)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.classes.filter { it.active }.forEach { c ->
                        FilterChip(selected = classId == c.id, onClick = { classId = c.id },
                            label = { Text((if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}").take(12), fontSize = 10.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = guardian, onValueChange = { guardian = it },
                    placeholder = { Text("Guardian name\u2026", color = MUTED) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    placeholder = { Text("Guardian phone\u2026", color = MUTED) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    msg = state.registerStudent(firstName, lastName, sex, classId, guardian, phone)
                    if (msg.startsWith("\u2713")) { firstName = ""; lastName = ""; guardian = ""; phone = "" }
                }, colors = ButtonDefaults.buttonColors(containerColor = BLUE)) { Text("Register student") }
                if (msg.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Cell(msg, if (msg.startsWith("\u2713")) GREEN else ORANGE) }
            }
        }
        item {
            CardBox {
                Cell("My registrations", MUTED, true)
                Spacer(Modifier.height(4.dp))
                val mine = d.enrollmentRequests.filter { it.teacherId == (state.me?.id ?: "") }
                if (mine.isEmpty()) Cell("None yet.", MUTED)
                mine.reversed().forEach { r ->
                    Cell("\u2022 ${r.student.firstName} ${r.student.lastName} \u2014 " +
                        (if (r.status == "APPROVED") "approved \u2713" else "pending admin import"), 
                        if (r.status == "APPROVED") GREEN else ORANGE)
                }
                Spacer(Modifier.height(6.dp))
                Cell("New students sync to the admin when you export the marks bundle \u2014 then they appear in reports and analytics.", MUTED)
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}
