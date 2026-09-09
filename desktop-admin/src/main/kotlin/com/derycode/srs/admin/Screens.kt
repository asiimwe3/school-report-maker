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

    val year = d.academicYears.firstOrNull()
    val enrollmentByClass: Map<String, List<Enrollment>> =
        d.enrollments.filter { year == null || it.academicYearId == year.id }.groupBy { it.classId }

    val visible = d.students.filter { s ->
        (search.isBlank() || s.fullName.contains(search, true) || s.admissionNo.contains(search, true)) &&
        (classFilter.isBlank() || enrollmentByClass[classFilter]?.any { it.studentId == s.id } == true)
    }

    ScreenTitle("Students", "${d.students.count { it.status == StudentStatus.ACTIVE }} active · archived students are kept forever")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(search, { search = it }, Modifier.width(220.dp), "Search name / adm no…")
            Level.entries.forEach { lvl ->
                FilterChip(selected = levelFilter == lvl && classFilter.isBlank(), onClick = { levelFilter = lvl; classFilter = "" },
                    label = { Text(lvl.name, fontSize = 11.sp) })
            }
            Spacer(Modifier.weight(1f))
            Btn(if (adding) "Cancel" else "+ Add student") { adding = !adding; error = "" }
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
                            FilterChip(selected = newStudent.sex == "M", onClick = { newStudent = newStudent.copy(sex = "M") }, label = { Text("M", fontSize = 11.sp) })
                            FilterChip(selected = newStudent.sex == "F", onClick = { newStudent = newStudent.copy(sex = "F") }, label = { Text("F", fontSize = 11.sp) })
                        }
                    }
                    Column(Modifier.weight(1f)) { FieldLabel("Guardian name"); TextField(newStudent.guardian, { newStudent = newStudent.copy(guardian = it) }, Modifier.fillMaxWidth()) }
                    Column(Modifier.weight(1f)) { FieldLabel("Guardian phone"); TextField(newStudent.phone, { newStudent = newStudent.copy(phone = it) }, Modifier.fillMaxWidth()) }
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
                                    label = { Text(if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}", fontSize = 10.sp) }
                                )
                            }
                        }
                        if (classes.size > 8) Text("+ ${classes.size - 8} more classes…", color = MUTED, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn("Save student") {
                        if (newStudent.first.isBlank() || newStudent.last.isBlank() || newStudent.classId.isBlank()) {
                            error = "First name, last name and class are required."
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
                        Box(Modifier.weight(1f)) { Cell(s.admissionNo, color = MUTED) }
                        Box(Modifier.weight(0.5f)) { Cell(s.sex, color = MUTED) }
                        Box(Modifier.weight(1.5f)) { Cell(cls?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" } ?: "—", color = MUTED) }
                        Box(Modifier.weight(1f)) { Cell(s.status.name, color = if (s.status == StudentStatus.ACTIVE) GOOD else WARN) }
                        Box(Modifier.weight(1f)) {
                            if (s.status == StudentStatus.ACTIVE) {
                                TextButton(onClick = {
                                    state.repo.mutate("STUDENT_ARCHIVED", "Student", s.id) { dd ->
                                        dd.copy(students = dd.students.map { if (it.id == s.id) it.copy(status = StudentStatus.WITHDRAWN) else it })
                                    }
                                    state.refresh()
                                }) { Text("Archive", color = WARN, fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class NewStudentForm(
    val first: String = "", val middle: String = "", val last: String = "",
    val adm: String = "", val sex: String = "M", val guardian: String = "",
    val phone: String = "", val classId: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// Classes & Streams (+ A-level combinations)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ClassesScreen(state: AppState) {
    val d = state.data
    var name by remember { mutableStateOf("") }
    var stream by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(Level.O_LEVEL) }
    var teacherId by remember { mutableStateOf("") }

    ScreenTitle("Classes & Streams", "Level → Class → Stream. A-level classes follow a combination.")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Text("Add class / stream", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row3 {
                Column(Modifier.weight(1f)) { FieldLabel("Class name"); TextField(name, { name = it }, Modifier.fillMaxWidth(), "Senior 1") }
                Column(Modifier.weight(1f)) { FieldLabel("Stream (optional)"); TextField(stream, { stream = it }, Modifier.fillMaxWidth(), "East") }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Level")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Level.entries.forEach { lvl ->
                            FilterChip(selected = level == lvl, onClick = { level = lvl }, label = { Text(if (lvl == Level.O_LEVEL) "O-Level" else if (lvl == Level.PRIMARY) "Primary" else "A-Level", fontSize = 10.sp) })
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Class teacher")
                    if (d.teachers.isEmpty()) { Text("Add teachers first", color = MUTED, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
                    else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        d.teachers.take(6).forEach { t ->
                            FilterChip(selected = teacherId == t.id, onClick = { teacherId = t.id }, label = { Text(t.name.take(10), fontSize = 10.sp) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Btn("Add class") {
                if (name.isBlank()) return@Btn
                val id = state.repo.nextId()
                val comb = if (level == Level.A_LEVEL) d.combinations.firstOrNull() else null
                state.repo.mutate("CLASS_ADDED", "SchoolClass", id, new = name) { dd ->
                    dd.copy(classes = dd.classes + SchoolClass(id = id, name = name, stream = stream, level = level, classTeacherId = teacherId.ifBlank { null }, combinationId = comb?.id))
                }
                name = ""; stream = ""; state.refresh()
            }
        }

        CardBox {
            Text("Classes", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Level.entries.forEach { lvl ->
                val inLevel = d.classes.filter { it.level == lvl && it.active }
                if (inLevel.isNotEmpty()) {
                    Text(levelLabel(lvl), color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    inLevel.groupBy { it.name }.forEach { (clsName, streams) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("• $clsName", color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            streams.forEach { s ->
                                Text(if (s.stream.isBlank()) "(whole class)" else s.stream, color = ACCENT, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (d.classes.isEmpty()) Text("No classes yet.", color = MUTED, fontSize = 12.sp)
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
        Text("A-Level Combinations", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("e.g. PCM = Physics + Chemistry + Mathematics, plus subsidiary (General Paper).", color = MUTED, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        d.combinations.forEach { c ->
            val names = (c.principalSubjectIds + c.subsidiarySubjectIds).mapNotNull { id -> d.subjects.firstOrNull { it.id == id }?.code }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                Text("${c.name} — ${names.joinToString(" + ")}", color = TEXT, fontSize = 12.sp)
                if (d.classes.any { it.combinationId == c.id }) Text("(in use)", color = GOOD, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(name, { name = it }, Modifier.width(90.dp), "PCM")
            Column(Modifier.width(340.dp)) {
                Text("Pick 3 principal subjects:", color = MUTED, fontSize = 10.sp)
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
            FilterChip(selected = gp, onClick = { gp = !gp }, label = { Text("+GP", fontSize = 10.sp) })
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
            Text("Add subject", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                        FilterChip(selected = compulsory, onClick = { compulsory = true }, label = { Text("Yes", fontSize = 11.sp) })
                        FilterChip(selected = !compulsory, onClick = { compulsory = false }, label = { Text("Optional", fontSize = 11.sp) })
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
                    Text(levelLabel(lvl), color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.wrapContentWidth()) {
                        subs.forEach { s ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.background(ACCENT_SOFT, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp).clickable { }
                            ) {
                                Text("${s.name}", color = TEXT, fontSize = 11.sp)
                                if (!s.compulsory) Text(" · optional", color = WARN, fontSize = 10.sp)
                                TextButton(onClick = {
                                    state.repo.mutate("SUBJECT_REMOVED", "Subject", s.id, old = s.name) { dd ->
                                        dd.copy(subjects = dd.subjects.map { if (it.id == s.id) it.copy(active = false) else it })
                                    }
                                    state.refresh()
                                }) { Text("×", color = MUTED, fontSize = 12.sp) }
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
            Text("Add teacher", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(name, { name = it }, Modifier.width(220.dp), "Teacher name")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Role.TEACHER, Role.HEAD_TEACHER, Role.SCHOOL_ADMIN, Role.DATA_ENTRY).forEach { r ->
                        FilterChip(selected = role == r, onClick = { role = r }, label = { Text(roleLabel(r), fontSize = 10.sp) })
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
            Text("Assign teacher → subject → class", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (d.teachers.isEmpty() || d.classes.isEmpty()) {
                Text("Add teachers and classes first.", color = MUTED, fontSize = 12.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(180.dp)) {
                        FieldLabel("Teacher")
                        d.teachers.forEach { t -> Row(Modifier.fillMaxWidth().clickable { assignTeacher = t.id }.padding(vertical = 3.dp)) {
                            Text((if (assignTeacher == t.id) "● " else "○ ") + t.name, color = if (assignTeacher == t.id) ACCENT else TEXT, fontSize = 12.sp)
                            Text("  ${roleLabel(t.role)}", color = MUTED, fontSize = 10.sp)
                        }
                    }
                    Column(Modifier.width(180.dp)) {
                        FieldLabel("Subject")
                        d.subjects.filter { it.active }.take(20).forEach { s ->
                            Text((if (assignSubject == s.id) "● " else "○ ") + s.name,
                                color = if (assignSubject == s.id) ACCENT else TEXT, fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().clickable { assignSubject = s.id }.padding(vertical = 3.dp))
                        }
                    }
                    Column(Modifier.width(180.dp)) {
                        FieldLabel("Class")
                        d.classes.filter { it.active }.take(20).forEach { c ->
                            Text((if (assignClass == c.id) "● " else "○ ") + if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}",
                                color = if (assignClass == c.id) ACCENT else TEXT, fontSize = 12.sp,
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
                Text("Current assignments", color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                d.assignments.forEach { a ->
                    val t = d.teachers.firstOrNull { it.id == a.teacherId }
                    val s = a.subjectId?.let { d.subjects.firstOrNull { x -> x.id == it } }
                    val c = d.classes.firstOrNull { it.id == a.classId }
                    val line = "${t?.name ?: "?"} → ${s?.name ?: "class teacher"} → ${c?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" } ?: "?"}"
                    Text("• $line", color = TEXT, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
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
