package com.derycode.srs.admin

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
import com.derycode.srs.core.model.*

// ─────────────────────────────────────────────────────────────────────────────
// Students
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StudentsScreen(state: AppState) {
    val d = state.data
    var search by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf(Level.O_LEVEL) }
    var classFilter by remember { mutableStateOf("") } // "" = all
    var adding by remember { mutableStateOf(false) }
    var newStudent by remember { mutableStateOf(NewStudentForm()) }
    var error by remember { mutableStateOf("") }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var deletingStudent by remember { mutableStateOf<Student?>(null) }
    var bulkOpen by remember { mutableStateOf(false) }
    var bulkText by remember { mutableStateOf("") }
    var bulkClassId by remember { mutableStateOf("") }
    var bulkOk by remember { mutableStateOf("") }
    var bulkErr by remember { mutableStateOf("") }
    val planLimit = com.derycode.srs.core.support.LicenseKeys.limitFor(d.settings.licencePlan)
    val activeCount = d.students.count { it.status == StudentStatus.ACTIVE }

    val year = d.academicYears.firstOrNull()
    val enrollmentByClass: Map<String, List<Enrollment>> =
        d.enrollments.filter { year == null || it.academicYearId == year.id }.groupBy { it.classId }

    val visible = d.students.filter { s ->
        (search.isBlank() || s.fullName.contains(search, true) || s.admissionNo.contains(search, true)) &&
        (classFilter.isBlank() || enrollmentByClass[classFilter]?.any { it.studentId == s.id } == true)
    }

    ScreenTitle("Students", "$activeCount active · plan limit $planLimit · archived students are kept forever")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(search, { search = it }, Modifier.width(220.dp), "Search name / adm no…")
            Level.entries.forEach { lvl ->
                FilterChip(selected = levelFilter == lvl && classFilter.isBlank(), onClick = { levelFilter = lvl; classFilter = "" },
                    label = { Text(lvl.name, fontSize = 13.sp) })
            }
            Spacer(Modifier.weight(1f))
            Btn(if (adding) "Cancel" else "+ Add student") {
                if (!adding && activeCount >= planLimit) {
                    error = "Plan limit reached — $planLimit students on ${d.settings.licencePlan}. Upgrade in Plans & Pricing."
                } else { adding = !adding; error = ""; if (adding) bulkOpen = false }
            }
            Btn(if (bulkOpen) "Close bulk import" else "+ Bulk import", primary = false) {
                bulkOpen = !bulkOpen; bulkOk = ""; bulkErr = ""; if (bulkOpen) { adding = false; error = "" }
            }
        }

        if (adding) {
            CardBox {
                Row3 {
                    Column(Modifier.weight(1f)) { FieldLabel("First name"); TextField(newStudent.first, { newStudent = newStudent.copy(first = it) }, Modifier.fillMaxWidth()) }
                    Column(Modifier.weight(1f)) { FieldLabel("Middle name"); TextField(newStudent.middle, { newStudent = newStudent.copy(middle = it) }, Modifier.fillMaxWidth()) }
                    Column(Modifier.weight(1f)) { FieldLabel("Last name"); TextField(newStudent.last, { newStudent = newStudent.copy(last = it) }, Modifier.fillMaxWidth()) }
                }
                Spacer(Modifier.height(8.dp))
                Row3 {
                    Column(Modifier.weight(1f)) { FieldLabel("Admission no."); TextField(newStudent.adm, { newStudent = newStudent.copy(adm = it) }, Modifier.fillMaxWidth()) }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Sex")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = newStudent.sex == "M", onClick = { newStudent = newStudent.copy(sex = "M") }, label = { Text("M", fontSize = 13.sp) })
                            FilterChip(selected = newStudent.sex == "F", onClick = { newStudent = newStudent.copy(sex = "F") }, label = { Text("F", fontSize = 13.sp) })
                        }
                    }
                    Column(Modifier.weight(1f)) { FieldLabel("Parent / Guardian full name"); TextField(newStudent.guardian, { newStudent = newStudent.copy(guardian = it) }, Modifier.fillMaxWidth(), "e.g. Namuli Sarah Grace") }
                    Column(Modifier.weight(1f)) { FieldLabel("Guardian phone"); TextField(newStudent.phone, { newStudent = newStudent.copy(phone = it) }, Modifier.fillMaxWidth(), "07XXXXXXXX") }
                }
                Spacer(Modifier.height(8.dp))
                Row3 {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Enroll in class (current year)")
                        val classes = d.classes.filter { it.level == levelFilter && it.active }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            classes.take(8).forEach { c ->
                                FilterChip(
                                    selected = newStudent.classId == c.id,
                                    onClick = { newStudent = newStudent.copy(classId = c.id) },
                                    label = { Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 12.sp) }
                                )
                            }
                        }
                        if (classes.size > 8) Text("+ ${classes.size - 8} more classes…", color = Theme.MUTED, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn("Save student") {
                        if (activeCount >= planLimit) {
                            error = "Plan limit reached — $planLimit students on ${d.settings.licencePlan}. Upgrade in Plans & Pricing."
                        } else if (newStudent.first.isBlank() || newStudent.last.isBlank() || newStudent.classId.isBlank() || newStudent.guardian.isBlank()) {
                            error = "First name, last name, class and parent/guardian full name are required."
                        } else {
                            val sid = state.repo.nextId()
                            state.repo.mutate("STUDENT_ADDED", "Student", sid, new = newStudent.first) { dd ->
                                val student = Student(
                                    id = sid, admissionNo = newStudent.adm.ifBlank { sid },
                                    firstName = newStudent.first, middleName = newStudent.middle, lastName = newStudent.last,
                                    sex = newStudent.sex, guardianName = newStudent.guardian, guardianPhone = newStudent.phone
                                )
                                val enroll = Enrollment(
                                    id = state.repo.nextId(), studentId = sid,
                                    academicYearId = year?.id ?: "no-year", classId = newStudent.classId
                                )
                                dd.copy(students = dd.students + student, enrollments = dd.enrollments + enroll)
                            }
                            newStudent = NewStudentForm(); adding = false
                            state.refresh()
                        }
                    }
                    if (error.isNotBlank()) ErrorText(error)
                }
            }
        }

        if (bulkOpen) {
            CardBox {
                FieldLabel("Enroll all imported students in (current year)")
                val bulkClasses = d.classes.filter { it.level == levelFilter && it.active }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    bulkClasses.take(10).forEach { c ->
                        FilterChip(
                            selected = bulkClassId == c.id,
                            onClick = { bulkClassId = if (bulkClassId == c.id) "" else c.id },
                            label = { Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                FieldLabel("Paste students — one per line. Copy straight from Excel (Admission No, Name, Stream, Contacts, Gender) or write \"TRC1127, ABARUHANGA EDMON, M\".")
                OutlinedTextField(
                    value = bulkText,
                    onValueChange = { bulkText = it },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    placeholder = { Text("TRC1127\tABARUHANGA EDMON\tA\t\tMale\nTRC1154\tAINOMUGISHA RONAH\tA\t\tFemale", color = Color(0xFF5A6B8C), fontSize = 13.sp) },
                    singleLine = false,
                    shape = RoundedCornerShape(8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Theme.TEXT, fontSize = 14.sp)
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn("Import ${'$'}{bulkText.lines().count { it.isNotBlank() }} students") {
                        bulkOk = ""; bulkErr = ""
                        if (bulkClassId.isBlank()) { bulkErr = "Pick the class to enroll them in first."; return@Btn }
                        val lines = bulkText.lines().map { it.trim() }.filter { it.isNotBlank() }
                        if (lines.isEmpty()) { bulkErr = "Nothing to import — paste the list first."; return@Btn }
                        val existing = d.students.map { it.admissionNo.lowercase() }.toSet()
                        val seen = mutableSetOf<String>()
                        val bad = mutableListOf<String>()
                        val rows = mutableListOf<BulkRow>()
                        lines.forEachIndexed { i, line ->
                            val parts = line.split(Regex("[,\t]")).map { it.trim() }
                            if (parts.size < 2 || parts[0].isBlank()) { bad.add("Line ${'$'}{i + 1}: needs admission no. and name"); return@forEachIndexed }
                            val adm = parts[0]
                            val tokens = parts[1].split(Regex("\\s+")).filter { it.isNotBlank() }
                            if (tokens.isEmpty()) { bad.add("Line ${'$'}{i + 1}: empty name"); return@forEachIndexed }
                            val sexField = parts.drop(2).lastOrNull { bulkSex(it).isNotBlank() }
                            val phone = parts.drop(2).firstOrNull { it.matches(Regex("[+0][0-9 +()\\-]{6,}")) } ?: ""
                            if (adm.lowercase() in existing) { bad.add("Line ${'$'}{i + 1}: ${'$'}adm already exists — skipped"); return@forEachIndexed }
                            if (!seen.add(adm.lowercase())) { bad.add("Line ${'$'}{i + 1}: ${'$'}adm appears twice in this paste — skipped"); return@forEachIndexed }
                            rows.add(BulkRow(adm, tokens[0], tokens.drop(1).dropLast(1).joinToString(" "), if (tokens.size > 1) tokens.last() else "", if (sexField != null) bulkSex(sexField) else "M", phone))
                        }
                        val slots = planLimit - activeCount
                        if (rows.size > slots) { bulkErr = "Plan limit — only ${'$'}slots student slots left on ${'$'}{d.settings.licencePlan}. Upgrade in Plans & Pricing."; return@Btn }
                        if (rows.isEmpty()) { bulkErr = "Nothing imported:\n" + bad.joinToString("\n"); return@Btn }
                        val clsName = d.classes.firstOrNull { it.id == bulkClassId }?.let { if (it.stream.isBlank()) it.name else "${'$'}{it.name} ${'$'}{it.stream}" } ?: "class"
                        state.repo.mutate("STUDENTS_BULK_IMPORTED", "Students", new = "${'$'}{rows.size} into ${'$'}clsName") { dd ->
                            var students = dd.students
                            var enrollments = dd.enrollments
                            rows.forEach { r ->
                                val sid = state.repo.nextId()
                                students = students + Student(
                                    id = sid, admissionNo = r.adm,
                                    firstName = r.first, middleName = r.middle, lastName = r.last,
                                    sex = r.sex, guardianPhone = r.phone
                                )
                                enrollments = enrollments + Enrollment(id = state.repo.nextId(), studentId = sid, academicYearId = year?.id ?: "no-year", classId = bulkClassId)
                            }
                            dd.copy(students = students, enrollments = enrollments)
                        }
                        state.refresh()
                        bulkOk = "Imported ${'$'}{rows.size} students into ${'$'}clsName" + (if (bad.isEmpty()) "" else "  ·  skipped ${'$'}{bad.size}") + (if (bad.isEmpty()) "" else "\n" + bad.joinToString("\n"))
                        bulkText = ""
                    }
                    if (bulkOk.isNotBlank()) Text(bulkOk, color = Theme.GOOD, fontSize = 13.sp)
                    if (bulkErr.isNotBlank()) Text(bulkErr, color = Theme.WARN, fontSize = 13.sp)
                }
            }
        }

        CardBox {
            val header = @Composable {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(2f)) { HeaderCell("Name") }
                    Box(Modifier.weight(1f)) { HeaderCell("Adm No") }
                    Box(Modifier.weight(0.5f)) { HeaderCell("Sex") }
                    Box(Modifier.weight(1.5f)) { HeaderCell("Class") }
                    Box(Modifier.weight(1f)) { HeaderCell("Status") }
                    Box(Modifier.weight(1f)) { HeaderCell("Actions") }
                }
            }
            header()
            LazyColumn(Modifier.height(420.dp)) {
                items(visible) { s ->
                    val enroll = year?.let { y -> d.enrollments.lastOrNull { it.studentId == s.id && it.academicYearId == y.id } }
                    val cls = enroll?.let { d.classes.firstOrNull { c -> c.id == it.classId } }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(2f)) { Cell(s.fullName, bold = true) }
                        Box(Modifier.weight(1f)) { Cell(s.admissionNo, color = Theme.MUTED) }
                        Box(Modifier.weight(0.5f)) { Cell(s.sex, color = Theme.MUTED) }
                        Box(Modifier.weight(1.5f)) { Cell(cls?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" } ?: "—", color = Theme.MUTED) }
                        Box(Modifier.weight(1f)) { Cell(s.status.name, color = if (s.status == StudentStatus.ACTIVE) Theme.GOOD else Theme.WARN) }
                        Column(Modifier.weight(1f)) {
                            TextButton(onClick = { editingStudent = s }) { Text("Edit", color = Theme.ACCENT, fontSize = 13.sp) }
                            if (s.status == StudentStatus.ACTIVE) {
                                TextButton(onClick = {
                                    state.repo.mutate("STUDENT_ARCHIVED", "Student", s.id) { dd ->
                                        dd.copy(students = dd.students.map { if (it.id == s.id) it.copy(status = StudentStatus.WITHDRAWN) else it })
                                    }
                                    state.refresh()
                                }) { Text("Archive", color = Theme.WARN, fontSize = 13.sp) }
                            }
                            TextButton(onClick = { deletingStudent = s }) { Text("Delete", color = Theme.WARN, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }

    editingStudent?.let { student ->
        StudentEditorDialog(student, onDismiss = { editingStudent = null }) { updated ->
            state.repo.mutate("STUDENT_UPDATED", "Student", student.id, old = student.fullName, new = updated.fullName) { dd ->
                dd.copy(students = dd.students.map { if (it.id == updated.id) updated else it })
            }
            editingStudent = null
            state.refresh()
        }
    }
    deletingStudent?.let { student ->
        AlertDialog(
            onDismissRequest = { deletingStudent = null },
            title = { Text("Delete ${student.fullName}?") },
            text = { Text("This permanently removes the student and their enrolments, marks, results and saved reports. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    state.repo.mutate("STUDENT_DELETED", "Student", student.id, old = student.fullName) { dd ->
                        dd.copy(
                            students = dd.students.filter { it.id != student.id },
                            enrollments = dd.enrollments.filter { it.studentId != student.id },
                            marks = dd.marks.filter { it.studentId != student.id },
                            termResults = dd.termResults.filter { it.studentId != student.id },
                            reports = dd.reports.filter { it.studentId != student.id }
                        )
                    }
                    deletingStudent = null
                    state.refresh()
                }) { Text("Delete", color = Theme.WARN) }
            },
            dismissButton = { TextButton(onClick = { deletingStudent = null }) { Text("Cancel") } }
        )
    }
}

data class NewStudentForm(
    val first: String = "", val middle: String = "", val last: String = "",
    val adm: String = "", val sex: String = "M", val guardian: String = "",
    val phone: String = "", val classId: String = ""
)

@Composable
private fun StudentEditorDialog(student: Student, onDismiss: () -> Unit, onSave: (Student) -> Unit) {
    var first by remember(student.id) { mutableStateOf(student.firstName) }
    var middle by remember(student.id) { mutableStateOf(student.middleName) }
    var last by remember(student.id) { mutableStateOf(student.lastName) }
    var admissionNo by remember(student.id) { mutableStateOf(student.admissionNo) }
    var guardian by remember(student.id) { mutableStateOf(student.guardianName) }
    var phone by remember(student.id) { mutableStateOf(student.guardianPhone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit student") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(first, { first = it }, Modifier.fillMaxWidth(), "First name")
                TextField(middle, { middle = it }, Modifier.fillMaxWidth(), "Middle name")
                TextField(last, { last = it }, Modifier.fillMaxWidth(), "Last name")
                TextField(admissionNo, { admissionNo = it }, Modifier.fillMaxWidth(), "Admission number")
                TextField(guardian, { guardian = it }, Modifier.fillMaxWidth(), "Parent / Guardian")
                TextField(phone, { phone = it }, Modifier.fillMaxWidth(), "Guardian phone")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (first.isNotBlank() && last.isNotBlank()) {
                    onSave(student.copy(firstName = first, middleName = middle, lastName = last,
                        admissionNo = admissionNo.ifBlank { student.id }, guardianName = guardian, guardianPhone = phone))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Classes & Streams (+ A-level combinations)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ClassesScreen(state: AppState) {
    val d = state.data
    var streamOpenFor by remember { mutableStateOf("") }   // class name+level key currently adding a stream
    var streamName by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var customLevel by remember { mutableStateOf(Level.O_LEVEL) }
    var showCustom by remember { mutableStateOf(false) }
    var teacherPickerFor by remember { mutableStateOf("") }  // class id currently picking a teacher for
    // v2.2.3: pick one level at a time so the page doesn't need scrolling through P1-S6 every time.
    var selectedLevel by remember { mutableStateOf<Level?>(null) }

    ScreenTitle("Classes & Streams", "Primary 1 → Senior 6 already set up for every school. Just add streams (East/West, A/B…) where a class is split.")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Level:", color = Theme.MUTED, fontSize = 14.sp)
            FilterChip(selected = selectedLevel == null, onClick = { selectedLevel = null }, label = { Text("All", fontSize = 13.sp) })
            Level.entries.forEach { lvl ->
                FilterChip(selected = selectedLevel == lvl, onClick = { selectedLevel = lvl }, label = { Text(levelLabel(lvl), fontSize = 13.sp) })
            }
        }
        Level.entries.filter { selectedLevel == null || it == selectedLevel }.forEach { lvl ->
            val inLevel = d.classes.filter { it.level == lvl && it.active }
            if (inLevel.isNotEmpty()) {
                CardBox {
                    Text(levelLabel(lvl), color = Theme.ACCENT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    inLevel.groupBy { it.name }.toSortedMap(compareBy { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }).forEach { (clsName, streams) ->
                        val key = "$clsName|$lvl"
                        Column(Modifier.padding(bottom = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(clsName, color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(140.dp))
                                streams.forEach { s ->
                                    val ct = s.classTeacherId?.let { id -> d.teachers.firstOrNull { it.id == id } }
                                    Column {
                                        Box(Modifier.background(Theme.ACCENT_SOFT, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                            Text(if (s.stream.isBlank()) "Whole class" else s.stream, color = Theme.ACCENT, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Text(if (ct != null) "Teacher: ${ct.name}" else "No class teacher",
                                            color = if (ct != null) Theme.GOOD else Theme.MUTED, fontSize = 11.sp,
                                            modifier = Modifier.clickable { teacherPickerFor = if (teacherPickerFor == s.id) "" else s.id }.padding(top = 2.dp))
                                        if (teacherPickerFor == s.id) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                                d.teachers.take(8).forEach { t ->
                                                    FilterChip(selected = s.classTeacherId == t.id, onClick = {
                                                        state.repo.mutate("CLASS_TEACHER_SET", "SchoolClass", s.id, new = t.name) { dd ->
                                                            dd.copy(classes = dd.classes.map { c -> if (c.id == s.id) c.copy(classTeacherId = t.id) else c })
                                                        }
                                                        teacherPickerFor = ""; state.refresh()
                                                    }, label = { Text(t.name.take(12), fontSize = 10.sp) })
                                                }
                                                if (d.teachers.isEmpty()) Text("Add teachers first", color = Theme.MUTED, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                                Btn(if (streamOpenFor == key) "Cancel" else "+ Add stream", primary = false) {
                                    streamOpenFor = if (streamOpenFor == key) "" else key; streamName = ""
                                }
                            }
                            if (streamOpenFor == key) {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TextField(streamName, { streamName = it }, Modifier.width(180.dp), "e.g. East, A, Blue")
                                    Btn("Save stream") {
                                        if (streamName.isNotBlank()) {
                                            val id = state.repo.nextId()
                                            state.repo.mutate("CLASS_STREAM_ADDED", "SchoolClass", id, new = "$clsName $streamName") { dd ->
                                                dd.copy(classes = dd.classes + SchoolClass(id = id, name = clsName, stream = streamName, level = lvl))
                                            }
                                            streamName = ""; streamOpenFor = ""; state.refresh()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Need a class outside P1–S6?", color = Theme.TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Btn(if (showCustom) "Cancel" else "+ Add custom class", primary = false) { showCustom = !showCustom }
            }
            if (showCustom) {
                Spacer(Modifier.height(10.dp))
                Row3 {
                    Column(Modifier.weight(1f)) { FieldLabel("Class name"); TextField(customName, { customName = it }, Modifier.fillMaxWidth(), "e.g. Nursery, Baby Class") }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Level")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Level.entries.forEach { lvl ->
                                FilterChip(selected = customLevel == lvl, onClick = { customLevel = lvl }, label = { Text(if (lvl == Level.O_LEVEL) "O-Level" else if (lvl == Level.PRIMARY) "Primary" else "A-Level", fontSize = 12.sp) })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Btn("Add class") {
                    if (customName.isBlank()) return@Btn
                    val id = state.repo.nextId()
                    state.repo.mutate("CLASS_ADDED", "SchoolClass", id, new = customName) { dd ->
                        dd.copy(classes = dd.classes + SchoolClass(id = id, name = customName, level = customLevel))
                    }
                    customName = ""; showCustom = false; state.refresh()
                }
            }
        }

        CombinationsCard(state)
    }
}

@Composable
fun CombinationsCard(state: AppState) {
    val d = state.data
    var name by remember { mutableStateOf("") }
    var aSubs by remember { mutableStateOf(setOf<String>()) }
    var gp by remember { mutableStateOf(true) }
    val aSubjects = d.subjects.filter { it.level == Level.A_LEVEL && it.principal }

    CardBox {
        Text("A-Level Combinations", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("e.g. PCM = Physics + Chemistry + Mathematics, plus subsidiary (General Paper).", color = Theme.MUTED, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        d.combinations.forEach { c ->
            val names = (c.principalSubjectIds + c.subsidiarySubjectIds).mapNotNull { id -> d.subjects.firstOrNull { it.id == id }?.code }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                Text("${c.name} — ${names.joinToString(" + ")}", color = Theme.TEXT, fontSize = 14.sp)
                if (d.classes.any { it.combinationId == c.id }) Text("(in use)", color = Theme.GOOD, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(name, { name = it }, Modifier.width(90.dp), "PCM")
            Column(Modifier.width(340.dp)) {
                Text("Pick 3 principal subjects:", color = Theme.MUTED, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    aSubjects.take(10).forEach { s ->
                        FilterChip(
                            selected = s.id in aSubs,
                            onClick = { aSubs = if (s.id in aSubs) aSubs - s.id else aSubs + s.id },
                            label = { Text(s.code, fontSize = 9.sp) }
                        )
                    }
                }
            }
            FilterChip(selected = gp, onClick = { gp = !gp }, label = { Text("+GP", fontSize = 12.sp) })
            Btn("Add combination") {
                if (name.isBlank() || aSubs.size != 3) return@Btn
                val id = state.repo.nextId()
                val gpSub = d.subjects.firstOrNull { it.code == "GP" }
                state.repo.mutate("COMBINATION_ADDED", "Combination", id, new = name) { dd ->
                    dd.copy(combinations = dd.combinations + Combination(
                        id = id, name = name, level = Level.A_LEVEL,
                        principalSubjectIds = aSubs.toList(),
                        subsidiarySubjectIds = if (gp && gpSub != null) listOf(gpSub.id) else emptyList()
                    ))
                }
                name = ""; aSubs = emptySet(); state.refresh()
            }
        }
    }
}

fun levelLabel(l: Level) = when (l) {
    Level.PRIMARY -> "PRIMARY (PLE)"
    Level.O_LEVEL -> "O-LEVEL (UCE)"
    Level.A_LEVEL -> "A-LEVEL (UACE)"
}

// ─────────────────────────────────────────────────────────────────────────────
// Subjects
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SubjectsScreen(state: AppState) {
    val d = state.data
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(Level.O_LEVEL) }
    var compulsory by remember { mutableStateOf(true) }

    ScreenTitle("Subjects", "Preloaded defaults — add or remove anything your school offers")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Text("Add subject", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row3 {
                Column(Modifier.weight(2f)) { FieldLabel("Subject name"); TextField(name, { name = it }, Modifier.fillMaxWidth(), "Further Mathematics") }
                Column(Modifier.weight(1f)) { FieldLabel("Code"); TextField(code, { code = it }, Modifier.fillMaxWidth(), "F/MATH") }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Level")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Level.entries.forEach { lvl -> FilterChip(selected = level == lvl, onClick = { level = lvl }, label = { Text(levelLabel(lvl), fontSize = 9.sp) }) }
                    }
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Compulsory")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = compulsory, onClick = { compulsory = true }, label = { Text("Yes", fontSize = 13.sp) })
                        FilterChip(selected = !compulsory, onClick = { compulsory = false }, label = { Text("Optional", fontSize = 13.sp) })
                    }
                }
                Column(Modifier.weight(1f)) {
                    Spacer(Modifier.height(22.dp))
                    Btn("Add") {
                        if (name.isBlank()) return@Btn
                        val id = state.repo.nextId()
                        state.repo.mutate("SUBJECT_ADDED", "Subject", id, new = name) { dd ->
                            dd.copy(subjects = dd.subjects + Subject(id = id, name = name, code = code, level = level, compulsory = compulsory))
                        }
                        name = ""; code = ""; state.refresh()
                    }
                }
            }
        }
        CardBox {
            Level.entries.forEach { lvl ->
                val subs = d.subjects.filter { it.level == lvl && it.active }
                if (subs.isNotEmpty()) {
                    Text(levelLabel(lvl), color = Theme.MUTED, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.wrapContentWidth()) {
                        subs.forEach { s ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.background(Theme.ACCENT_SOFT, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp).clickable { }
                            ) {
                                Text("${s.name}", color = Theme.TEXT, fontSize = 13.sp)
                                if (!s.compulsory) Text(" · optional", color = Theme.WARN, fontSize = 12.sp)
                                TextButton(onClick = {
                                    state.repo.mutate("SUBJECT_REMOVED", "Subject", s.id, old = s.name) { dd ->
                                        dd.copy(subjects = dd.subjects.map { if (it.id == s.id) it.copy(active = false) else it })
                                    }
                                    state.refresh()
                                }) { Text("×", color = Theme.MUTED, fontSize = 14.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Teachers & assignments
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TeachersScreen(state: AppState) {
    val d = state.data
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.TEACHER) }
    var assignTeacher by remember { mutableStateOf("") }
    var assignSubject by remember { mutableStateOf("") }
    var assignClass by remember { mutableStateOf("") }

    ScreenTitle("Teachers", "Teachers, roles and subject/class assignments")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Text("Staff roster", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Every teacher, at a glance: role, classes taught, subjects taught.", color = Theme.MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            if (d.teachers.isEmpty()) Text("No teachers yet — add one below.", color = Theme.MUTED, fontSize = 14.sp)
            d.teachers.forEach { t ->
                val classesTaught = (d.classes.filter { it.classTeacherId == t.id } +
                    d.assignments.filter { it.teacherId == t.id }.mapNotNull { a -> d.classes.firstOrNull { it.id == a.classId } }).distinctBy { it.id }
                val subjectsTaught = d.assignments.filter { it.teacherId == t.id }
                    .mapNotNull { a -> a.subjectId?.let { sid -> d.subjects.firstOrNull { it.id == sid } } }.distinctBy { it.id }
                Column(Modifier.fillMaxWidth().background(Theme.ACCENT_SOFT.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(14.dp).padding(bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(t.name, color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Box(Modifier.background(Theme.ACCENT, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(roleLabel(t.role), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Classes: " + if (classesTaught.isEmpty()) "none assigned yet" else classesTaught.joinToString(", ") { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" },
                        color = Theme.TEXT, fontSize = 13.sp)
                    Text("Subjects: " + if (subjectsTaught.isEmpty()) "none assigned yet" else subjectsTaught.joinToString(", ") { it.name },
                        color = Theme.MUTED, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        CardBox {
            Text("Add teacher", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(name, { name = it }, Modifier.width(220.dp), "Teacher name")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Role.TEACHER, Role.HEAD_TEACHER, Role.SCHOOL_ADMIN, Role.DATA_ENTRY).forEach { r ->
                        FilterChip(selected = role == r, onClick = { role = r }, label = { Text(roleLabel(r), fontSize = 12.sp) })
                    }
                }
                Btn("Add") {
                    if (name.isBlank()) return@Btn
                    val id = state.repo.nextId()
                    state.repo.mutate("TEACHER_ADDED", "Teacher", id, new = name) { dd ->
                        dd.copy(teachers = dd.teachers + Teacher(id = id, name = name, role = role))
                    }
                    name = ""; state.refresh()
                }
            }
        }

        CardBox {
            Text("Assign teacher → subject → class", color = Theme.TEXT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (d.teachers.isEmpty() || d.classes.isEmpty()) {
                Text("Add teachers and classes first.", color = Theme.MUTED, fontSize = 14.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(180.dp)) {
                        FieldLabel("Teacher")
                        d.teachers.forEach { t -> Row(Modifier.fillMaxWidth().clickable { assignTeacher = t.id }.padding(vertical = 3.dp)) {
                            Text((if (assignTeacher == t.id) "● " else "○ ") + t.name, color = if (assignTeacher == t.id) Theme.ACCENT else Theme.TEXT, fontSize = 14.sp)
                            Text("  ${roleLabel(t.role)}", color = Theme.MUTED, fontSize = 12.sp)
                        }
                    }
                    Column(Modifier.width(180.dp)) {
                        FieldLabel("Subject")
                        d.subjects.filter { it.active }.take(20).forEach { s ->
                            Text((if (assignSubject == s.id) "● " else "○ ") + s.name,
                                color = if (assignSubject == s.id) Theme.ACCENT else Theme.TEXT, fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().clickable { assignSubject = s.id }.padding(vertical = 3.dp))
                        }
                    }
                    Column(Modifier.width(180.dp)) {
                        FieldLabel("Class")
                        d.classes.filter { it.active }.take(20).forEach { c ->
                            Text((if (assignClass == c.id) "● " else "○ ") + if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}",
                                color = if (assignClass == c.id) Theme.ACCENT else Theme.TEXT, fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().clickable { assignClass = c.id }.padding(vertical = 3.dp))
                        }
                    }
                    Column {
                        Spacer(Modifier.height(22.dp))
                        Btn("Assign") {
                            if (assignTeacher.isBlank() || assignClass.isBlank()) return@Btn
                            val id = state.repo.nextId()
                            state.repo.mutate("TEACHER_ASSIGNED", "TeacherAssignment", id) { dd ->
                                dd.copy(assignments = dd.assignments + TeacherAssignment(
                                    id = id, teacherId = assignTeacher,
                                    subjectId = assignSubject.ifBlank { null }, classId = assignClass
                                ))
                            }
                            state.refresh()
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Current assignments", color = Theme.MUTED, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                d.assignments.forEach { a ->
                    val t = d.teachers.firstOrNull { it.id == a.teacherId }
                    val s = a.subjectId?.let { d.subjects.firstOrNull { x -> x.id == it } }
                    val c = d.classes.firstOrNull { it.id == a.classId }
                    val line = "${t?.name ?: "?"} → ${s?.name ?: "class teacher"} → ${c?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" } ?: "?"}"
                    Text("• $line", color = Theme.TEXT, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
    }
}

fun roleLabel(r: Role) = when (r) {
    Role.SUPER_ADMIN -> "Super Admin"
    Role.SCHOOL_ADMIN -> "School Admin"
    Role.HEAD_TEACHER -> "Head Teacher"
    Role.TEACHER -> "Teacher"
    Role.DATA_ENTRY -> "Data Entry"
}

private data class BulkRow(
    val adm: String, val first: String, val middle: String,
    val last: String, val sex: String, val phone: String
)

private fun bulkSex(s: String): String = when (s.trim().lowercase()) {
    "m", "male", "boy" -> "M"
    "f", "female", "girl" -> "F"
    else -> ""
}
