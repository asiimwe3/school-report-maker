package com.derycode.srs.teacher

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.derycode.srs.core.entry.MarksSheet
import com.derycode.srs.core.entry.ParsedRow
import com.derycode.srs.core.entry.RosterMatcher
import com.derycode.srs.core.model.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// Marks sheets: download the template, import it back, or photograph the class
// list and let the phone read names and scores into the system.
// ─────────────────────────────────────────────────────────────────────────────

/** One mark waiting to be saved, already tied to a learner and an assessment. */
internal data class SheetRow(
    val id: Int,
    val written: String,
    val studentId: String?,
    val studentName: String,
    val componentId: String,
    val scoreText: String
)

@Composable
internal fun SheetToolsCard(
    state: TeacherState,
    classId: String,
    subjectId: String,
    termId: String,
    componentId: String,
    maxMarks: Int,
    locked: Boolean,
    onMessage: (String) -> Unit
) {
    val ctx = LocalContext.current
    val d = state.data
    val cls = d.classes.firstOrNull { it.id == classId }
    val subject = d.subjects.firstOrNull { it.id == subjectId }
    val students = state.studentsIn(classId)
    val columns = remember(cls?.level, maxMarks) { MarksSheet.columns(d, cls?.level ?: Level.O_LEVEL, maxMarks) }
    val rows = remember { mutableStateListOf<SheetRow>() }
    var pickFor by remember { mutableStateOf<SheetRow?>(null) }
    var busy by remember { mutableStateOf(false) }
    var counter by remember { mutableStateOf(0) }

    fun fileName(): String =
        "SRS-${(subject?.name ?: "Marks").replace(' ', '-')}-${(cls?.name ?: "Class").replace(' ', '-')}-" +
            (MarksSheet.termLabel(d, termId).replace(' ', '-')) + ".csv"

    fun templateText(): String = MarksSheet.templateCsv(
        school = d.school,
        klass = cls ?: SchoolClass(id = classId, name = "Class", level = Level.O_LEVEL),
        subject = subject ?: Subject(id = subjectId, name = "Subject"),
        termLabel = MarksSheet.termLabel(d, termId),
        students = students,
        columns = columns
    )

    fun addRow(written: String, studentId: String?, component: String, score: String) {
        rows += SheetRow(
            id = counter++, written = written, studentId = studentId,
            studentName = students.firstOrNull { it.id == studentId }?.fullName ?: written,
            componentId = component, scoreText = score
        )
    }

    /** Turns parsed lines into rows, matching every name against this class register. */
    fun load(parsed: List<ParsedRow>, oneColumn: Boolean) {
        rows.clear()
        var matched = 0
        parsed.forEach { p ->
            val student = RosterMatcher.best(p, students)
            if (student != null) matched++
            val pairs: List<Pair<String, String>> = when {
                p.values.isEmpty() -> listOf(componentId to "")
                oneColumn || p.values.size == 1 -> listOf(componentId to p.values.values.first())
                else -> p.values.toList()
            }
            pairs.forEach { (cid, value) -> addRow(p.nameText, student?.id, cid, value) }
        }
        onMessage(
            when {
                parsed.isEmpty() -> "Nothing could be read from that sheet"
                matched == 0 -> "Read ${parsed.size} lines but matched no learner — check the class"
                else -> "Read ${parsed.size} lines, matched $matched learners"
            }
        )
    }

    // ── Save the template to wherever the teacher chooses ────────────────────
    var pendingCsv by remember { mutableStateOf("") }
    val saveTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(pendingCsv.toByteArray()) }
            }.onSuccess { onMessage("Template saved \u2713") }
                .onFailure { onMessage("Could not save the template") }
        }
    }

    // ── Read a filled template back ──────────────────────────────────────────
    val openTemplate = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            }.getOrDefault("")
            if (text.isBlank()) {
                onMessage("That file is empty")
            } else {
                val meta = MarksSheet.readTemplateMeta(text)
                if (meta != null && subject != null && meta.subject.trim().isNotEmpty() &&
                    !meta.subject.equals(subject.name, ignoreCase = true)
                ) {
                    onMessage("That template is for ${meta.subject}, not ${subject.name}")
                } else {
                    load(MarksSheet.parseCsv(text, columns), oneColumn = false)
                }
            }
        }
    }

    // ── Photograph the sheet and read it ─────────────────────────────────────
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    fun readPhoto(uri: Uri) {
        busy = true
        val image = runCatching { InputImage.fromFilePath(ctx, uri) }.getOrNull()
        if (image == null) {
            busy = false; onMessage("Could not open that photo"); return
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                busy = false
                load(MarksSheet.parseScan(result.text.lines(), columns), oneColumn = false)
            }
            .addOnFailureListener {
                busy = false
                onMessage("Could not read that photo — try again in good light")
            }
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) readPhoto(uri)
    }

    fun photographSheet() {
        val dir = File(ctx.cacheDir, "sheets").apply { mkdirs() }
        val file = File(dir, "sheet-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        photoUri = uri
        runCatching { takePhoto.launch(uri) }.onFailure { onMessage("No camera app available on this phone") }
    }

    CardBox {
        Cell("Enter marks from a sheet", MUTED, true)
        Spacer(Modifier.height(8.dp))
        Text("Scan to upload marks: download the class list, fill the scores by hand, then photograph the page. Names are matched to the register for you.",
            color = MUTED, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        OptionRowAction(Icons.Filled.Download, "Download marks entry template") {
            pendingCsv = templateText()
            runCatching { saveTemplate.launch(fileName()) }
                .onFailure { onMessage("No file manager available to save the template") }
        }
        Divider(color = STROKE, modifier = Modifier.padding(vertical = 4.dp))
        OptionRowAction(Icons.Filled.UploadFile, "Import a filled template") {
            openTemplate.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*"))
        }
        Divider(color = STROKE, modifier = Modifier.padding(vertical = 4.dp))
        OptionRowAction(Icons.Filled.PhotoCamera, "Scan to upload marks (photo)") { photographSheet() }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = BLUE, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reading the sheet…", color = MUTED, fontSize = 14.sp)
            }
        }
    }

    if (rows.isNotEmpty()) {
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cell("Review (${rows.size})", NAVY, true, Modifier.weight(1f))
                TextButton(onClick = { rows.clear() }) { Text("Clear", color = RED, fontSize = 14.sp) }
            }
            Spacer(Modifier.height(4.dp))
            rows.forEachIndexed { index, row ->
                val comp = columns.firstOrNull { it.componentId == row.componentId }?.label
                    ?: d.components.firstOrNull { it.id == row.componentId }?.name ?: ""
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.studentName, color = if (row.studentId == null) RED else NAVY, fontSize = 15.sp,
                            fontWeight = if (row.studentId == null) FontWeight.Bold else FontWeight.Normal)
                        Text(
                            if (row.studentId == null) "Not matched — tap to pick the learner"
                            else "${students.firstOrNull { it.id == row.studentId }?.admissionNo ?: ""} · $comp",
                            color = MUTED, fontSize = 12.sp
                        )
                    }
                    if (row.studentId == null) {
                        TextButton(onClick = { pickFor = row }) { Text("Find", color = BLUE, fontSize = 14.sp) }
                    }
                    Box(Modifier.width(70.dp)) {
                        DarkTextFieldSmall(
                            row.scoreText,
                            { text -> rows[index] = row.copy(scoreText = text) },
                            enabled = !locked
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                enabled = !locked,
                onClick = {
                    var saved = 0
                    rows.forEach { row ->
                        val sid = row.studentId ?: return@forEach
                        val raw = row.scoreText.trim()
                        if (raw.isEmpty()) return@forEach
                        val type: MarkType
                        val score: Double
                        when (raw.uppercase()) {
                            "ABS", "AB" -> { type = MarkType.ABS; score = 0.0 }
                            "EXEMPT", "EX" -> { type = MarkType.EXEMPT; score = 0.0 }
                            else -> {
                                val num = raw.replace(',', '.').toDoubleOrNull() ?: return@forEach
                                if (num < 0 || num > maxMarks) return@forEach
                                type = MarkType.VALUE; score = num
                            }
                        }
                        state.upsertMark(
                            Mark(
                                id = "m-$sid|$subjectId|$termId|${row.componentId}",
                                studentId = sid, subjectId = subjectId, termId = termId,
                                componentId = row.componentId, score = score, type = type, maxScore = maxMarks
                            )
                        )
                        saved++
                    }
                    if (saved > 0) {
                        rows.clear()
                        onMessage("Saved $saved marks from the sheet \u2713")
                    } else {
                        onMessage("Nothing to save — check the scores")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BLUE),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save these marks") }
        }
    }

    // ── Pick the learner for a line that did not match ───────────────────────
    pickFor?.let { target ->
        var query by remember(target.id) { mutableStateOf(if (target.studentId == null) "" else target.studentName) }
        val found = students.filter {
            query.isBlank() || it.fullName.contains(query, ignoreCase = true) || it.admissionNo.contains(query, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { pickFor = null },
            title = { Text("Who is \u201C${target.written}\u201D?", fontSize = 17.sp) },
            text = {
                Column(Modifier.heightIn(max = 380.dp)) {
                    DarkTextField(query, { query = it })
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        items(found) { s ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    rows.indexOfFirst { it.id == target.id }.takeIf { it >= 0 }?.let { i ->
                                        rows[i] = target.copy(studentId = s.id, studentName = s.fullName)
                                    }
                                    pickFor = null
                                }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(s.firstName.take(1) + s.lastName.take(1), avatarColor(s.id), 28)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(s.fullName, color = NAVY, fontSize = 15.sp)
                                    Text(s.admissionNo, color = MUTED, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickFor = null }) { Text("Cancel", color = MUTED) } }
        )
    }
}

@Composable
private fun OptionRowAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BLUE, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = NAVY, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MUTED, modifier = Modifier.size(16.dp))
    }
}
