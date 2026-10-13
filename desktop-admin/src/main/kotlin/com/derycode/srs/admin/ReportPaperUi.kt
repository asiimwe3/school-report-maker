package com.derycode.srs.admin

// ─────────────────────────────────────────────────────────────────────────────
// ReportPaper — LIVE on-screen preview of a printed report card.
// Mirrors the DocxReport engine (same layouts, same accent colors, same
// header lines) so what the admin sees is what prints on paper.
// Designed at a fixed 600 × 848 dp (A4 proportions) and scaled to any size.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.*
import java.nio.file.Files
import java.nio.file.Path

/** The template a given class should print with (class choice → school default → any). */
internal fun templateFor(d: SchoolData, cls: SchoolClass?): ReportTemplate? {
    val byClass = cls?.takeIf { it.templateId.isNotBlank() }
        ?.let { c -> d.templates.firstOrNull { it.id == c.templateId && !it.archived } }
    return byClass ?: d.templates.firstOrNull { it.isDefault && !it.archived }
        ?: d.templates.firstOrNull { !it.archived }
}

internal fun templateAccent(t: ReportTemplate?, d: SchoolData): Color {
    val hex = t?.accentColor?.takeIf { it.isNotBlank() } ?: d.school.colorPrimary
    return try { Color(hex.removePrefix("#").toLong(16) or 0xFF000000) } catch (_: Exception) { Color(0xFF1F4E79) }
}

@Composable
internal fun ReportPaper(
    d: SchoolData,
    template: ReportTemplate?,
    cls: SchoolClass?,
    term: Pair<AcademicYear, Term>?,
    student: Student?,
    result: TermResult?,
    widthDp: Int
) {
    val scale = widthDp / 600f
    Box(
        Modifier.width(widthDp.dp).height((widthDp * 848f / 600f).dp)
            .clip(RoundedCornerShape(3.dp))
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .width(600.dp).height(848.dp)
            .background(Color.White).border(1.dp, Color(0xFFE5E5E5), RectangleShape)
            .padding(30.dp)
    ) {
        PaperContent(d, template, cls, term, student, result)
    }
}

@Composable
private fun PaperContent(
    d: SchoolData,
    template: ReportTemplate?,
    cls: SchoolClass?,
    term: Pair<AcademicYear, Term>?,
    student: Student?,
    result: TermResult?
) {
    val school = d.school
    val accent = templateAccent(template, d)
    val ink = try { Color(school.colorSecondary.removePrefix("#").toLong(16) or 0xFF000000) } catch (_: Exception) { Color(0xFF222222) }
    val muted = Color(0xFF777777)
    val layout = template?.layout ?: TemplateLayout.CLASSIC
    val clsName = cls?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" } ?: "—"
    val termNo = term?.second?.number ?: 1
    val year = term?.first?.year ?: ""
    val line = Color(0xFFDDDDDD)

    Column(Modifier.fillMaxSize()) {
        // ── header (mirrors DocxReport header variants) ──
        when (layout) {
            TemplateLayout.MODERN -> {
                schoolLogo(school, Modifier.align(Alignment.CenterHorizontally), 44.dp)
                Spacer(Modifier.height(4.dp))
                Text(school.name, color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                contactLine(school, muted, 10.sp)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().background(accent).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text("TERMLY REPORT CARD — TERM $termNo $year", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
            TemplateLayout.COMPACT -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    schoolLogo(school, Modifier, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(school.name, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                        contactLine(school, muted, 9.sp)
                    }
                    Box(Modifier.border(1.dp, accent).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("TERM $termNo $year", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(accent))
                Spacer(Modifier.height(4.dp))
                Text("TERMLY REPORT CARD", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                schoolLogo(school, Modifier.align(Alignment.CenterHorizontally), 44.dp)
                Spacer(Modifier.height(4.dp))
                Text(school.name, color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                contactLine(school, muted, 10.sp)
                val place = listOfNotNull(school.village.ifBlank { null }, school.subCounty.ifBlank { null }, school.district.ifBlank { null }).distinct().joinToString(", ")
                val contact = listOfNotNull(school.address.ifBlank { null }, place.ifBlank { null },
                    listOfNotNull(school.phone.ifBlank { null }, school.phone2.ifBlank { null }).joinToString(" / ").ifBlank { null }?.let { "Tel: $it" },
                    school.email.ifBlank { null }, school.website.ifBlank { null }).joinToString("  ·  ")
                if (contact.isNotBlank()) Text(contact, color = muted, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (school.motto.isNotBlank()) Text("\u201C${school.motto}\u201D", color = muted, fontSize = 9.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                val regLine = listOfNotNull(
                    school.regNo.ifBlank { null }?.let { "Reg. No: $it" },
                    school.emisNo.ifBlank { null }?.let { "EMIS: $it" },
                    school.unebCentreNo.ifBlank { null }?.let { "UNEB Centre: $it" },
                    school.foundedYear.ifBlank { null }?.let { "Est. $it" }).joinToString("  ·  ")
                if (regLine.isNotBlank()) Text(regLine, color = Color(0xFF999999), fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("TERMLY REPORT CARD", color = if (layout == TemplateLayout.PLAIN) ink else accent,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(if (layout == TemplateLayout.PLAIN) ink else accent))
            }
        }
        Spacer(Modifier.height(12.dp))

        // ── student identity ──
        val cells = listOf(
            "Student" to (student?.fullName ?: "—"),
            "Adm. No" to (student?.admissionNo?.ifBlank { "—" } ?: "—"),
            "Class" to clsName,
            "Term" to "Term $termNo",
            "Year" to (year.ifBlank { "—" }))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(1.dp, line)) {
            cells.forEachIndexed { i, (label, value) ->
                Column(Modifier.weight(if (i == 0) 1.6f else 1f).fillMaxHeight().padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text(label.uppercase(), color = muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Text(value, color = ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                if (i < cells.size - 1) Box(Modifier.width(1.dp).fillMaxHeight().background(line))
            }
        }
        Spacer(Modifier.height(12.dp))

        // ── subject performance ──
        Text("SUBJECT PERFORMANCE", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        // component columns exactly like the docx engine (CA / Exam / …)
        val comps: List<AssessmentComponent> = if (result != null) {
            val subjectIds = result.subjectResults.map { it.subjectId }.toSet()
            d.components.filter { c -> c.active && d.marks.any { m ->
                m.studentId == (student?.id ?: "") && m.termId == result.termId && m.componentId == c.id && m.subjectId in subjectIds } }.take(3)
        } else d.components.filter { it.active && (cls == null || it.levels.contains(cls.level)) }.take(2)
        val compCode = { c: AssessmentComponent -> c.code.ifBlank { c.name.take(6) } }
        val headers = listOf("Subject") + comps.map { compCode(it) } + listOf("Total", "%", "Grade", "Pos", "Remark")
        val w = listOf(2.1f) + comps.map { 0.7f } + listOf(0.7f, 0.7f, 0.85f, 0.6f, 1.5f)
        Row(Modifier.fillMaxWidth().background(if (layout == TemplateLayout.PLAIN) Color(0xFFEEEEEE) else accent)) {
            headers.forEachIndexed { i, h ->
                Text(h, color = if (layout == TemplateLayout.PLAIN) ink else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(w[i]).padding(horizontal = 4.dp, vertical = 5.dp))
            }
        }
        val num = { x: Double -> if (x == Math.floor(x)) x.toLong().toString() else String.format("%.1f", x) }
        val rows: List<List<String>> = if (result != null && result.subjectResults.isNotEmpty()) {
            result.subjectResults.map { r ->
                val sub = d.subjects.firstOrNull { it.id == r.subjectId }
                val perComp = comps.map { c ->
                    val m = d.marks.firstOrNull { mm -> mm.studentId == (student?.id ?: "") && mm.subjectId == r.subjectId && mm.termId == result.termId && mm.componentId == c.id }
                    when {
                        m == null -> "—"
                        m.type == MarkType.ABS -> "ABS"
                        m.type == MarkType.MISSING -> "MISS"
                        else -> num(m.score)
                    }
                }
                listOf(sub?.name ?: "—") + perComp + listOf(
                    r.total.toInt().toString(),
                    num(r.percentage) + "%",
                    r.grade,
                    if (r.positionInSubject > 0) r.positionInSubject.toString() else "",
                    r.remark)
            }
        } else {
            val sampleSubjects = d.subjects.filter { it.active && (cls == null || it.level == cls.level) }.take(7)
            (if (sampleSubjects.isEmpty()) listOf(null) else sampleSubjects.map { it as Subject? }).map { s ->
                List(headers.size) { idx -> if (idx == 0) (s?.name ?: "Subject name") else "—" }
            }
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().border(1.dp, line, RectangleShape)) {
                row.forEachIndexed { i, cell ->
                    Text(cell.ifBlank { "—" }, color = ink, fontSize = 9.sp,
                        modifier = Modifier.weight(w[i]).padding(horizontal = 4.dp, vertical = 5.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ── overall summary ──
        val summary = listOfNotNull(
            "Average" to "${if (result != null) (if (result.averagePercent % 1.0 == 0.0) result.averagePercent.toInt() else String.format("%.1f", result.averagePercent)) else "—"}%",
            when (cls?.level) {
                Level.PRIMARY -> "Aggregate" to (result?.aggregate?.toString() ?: "—")
                Level.O_LEVEL -> "UCE Result" to (result?.uceResult ?: "—")
                Level.A_LEVEL -> "UACE Points" to (result?.uacePoints?.toString() ?: "—")
                else -> "Aggregate" to (result?.aggregate?.toString() ?: "—")
            },
            "Class Position" to (if (result != null && result.classPosition > 0) result.classPosition.toString() else "—"),
            "Students in Class" to (d.enrollments.count { it.classId == (cls?.id ?: "") }.toString().ifBlank { "—" })
        )
        Row(Modifier.fillMaxWidth().border(1.dp, line)) {
            summary.forEachIndexed { i, (label, value) ->
                Column(Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 5.dp)) {
                    Text(label.uppercase(), color = muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Text(value, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (i < summary.size - 1) Box(Modifier.width(1.dp).fillMaxHeight().background(line))
            }
        }
        Spacer(Modifier.height(14.dp))

        // ── comments & signatures ──
        Column(Modifier.fillMaxWidth()) {
            Text("CLASS TEACHER'S REMARKS", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth().height(34.dp).border(1.dp, line))
            Spacer(Modifier.height(8.dp))
            Text("HEAD TEACHER'S REMARKS", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth().height(34.dp).border(1.dp, line))
        }
        Spacer(Modifier.weight(1f))
        // ── signatures + footer ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(150.dp).height(30.dp))
                Box(Modifier.width(150.dp).height(1.dp).background(ink))
                Text("Class Teacher", color = muted, fontSize = 8.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(150.dp).height(30.dp))
                Box(Modifier.width(150.dp).height(1.dp).background(ink))
                Text("Head Teacher", color = muted, fontSize = 8.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Grading key: A 80-100 · B 70-79 · C 60-69 · D 50-59 · E 40-49 · F 0-39    |    This report was generated with School Report Maker.",
            color = Color(0xFF999999), fontSize = 7.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun contactLine(school: School, muted: Color, size: androidx.compose.ui.unit.TextUnit) {
    val place = listOfNotNull(school.village.ifBlank { null }, school.subCounty.ifBlank { null }, school.district.ifBlank { null }).distinct().joinToString(", ")
    val contact = listOfNotNull(school.address.ifBlank { null }, place.ifBlank { null },
        listOfNotNull(school.phone.ifBlank { null }, school.phone2.ifBlank { null }).joinToString(" / ").ifBlank { null }?.let { "Tel: $it" },
        school.email.ifBlank { null }, school.website.ifBlank { null }).joinToString("  ·  ")
    if (contact.isNotBlank()) Text(contact, color = muted, fontSize = size, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (school.motto.isNotBlank()) Text("\u201C${school.motto}\u201D", color = muted, fontSize = size,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun schoolLogo(school: School, modifier: Modifier, size: androidx.compose.ui.unit.Dp) {
    val bmp = school.logoPath?.takeIf { it.isNotBlank() }?.let { p ->
        try {
            val path = Path.of(p)
            if (Files.exists(path)) org.jetbrains.skia.Image.makeFromEncoded(Files.readAllBytes(path)).toComposeImageBitmap() else null
        } catch (_: Exception) { null }
    }
    if (bmp != null) Image(BitmapPainter(bmp), contentDescription = "School logo", modifier = modifier.size(size))
    else Box(modifier.size(size))   // no logo — invisible spacer keeps the header symmetric
}
