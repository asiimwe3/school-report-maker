package com.derycode.srs.core.report

import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.model.Student
import com.derycode.srs.core.model.TemplateLayout
import com.derycode.srs.core.model.TermResult
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Word .docx report engine — every exported report is a genuine Word document
 * (Office Open XML), generated offline with zero dependencies.
 */
object DocxReport {

    // ── low-level document builder ─────────────────────────────────────────

    private class Doc {
        val body = StringBuilder()
        private var tableOpen = false

        private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")

        fun p(text: String, bold: Boolean = false, size: Int = 22, align: String = "left",
              color: String? = null, italic: Boolean = false, spacingAfter: Int = 60) {
            body.append("<w:p><w:pPr><w:spacing w:after=\"$spacingAfter\"/>")
            if (align != "left") body.append("<w:jc w:val=\"$align\"/>")
            body.append("<w:rPr>")
            if (bold) body.append("<w:b/>")
            if (italic) body.append("<w:i/>")
            if (color != null) body.append("<w:color w:val=\"$color\"/>")
            body.append("<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/></w:rPr></w:pPr>")
            run(text, bold, size, color, italic)
            body.append("</w:p>")
        }

        private fun run(text: String, bold: Boolean, size: Int, color: String?, italic: Boolean) {
            body.append("<w:r><w:rPr>")
            if (bold) body.append("<w:b/>")
            if (italic) body.append("<w:i/>")
            if (color != null) body.append("<w:color w:val=\"$color\"/>")
            body.append("<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/></w:rPr>")
            body.append("<w:t xml:space=\"preserve\">").append(esc(text)).append("</w:t></w:r>")
        }

        fun table(headers: List<String>, rows: List<List<String>>, widths: List<Int>? = null,
                  accent: String = "1F4E79") {
            body.append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>")
            for (edge in listOf("top", "left", "bottom", "right", "insideH", "insideV"))
                body.append("<w:$edge w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"AFAFAF\"/>")
            body.append("</w:tblBorders></w:tblPr>")
            // header row
            tr(true, headers, accent)
            rows.forEach { tr(false, it, accent) }
            body.append("</w:tbl>")
            body.append("<w:p/>") // spacer after table (Word requires a paragraph between tables)
        }

        private fun tr(header: Boolean, cells: List<String>, accent: String) {
            body.append("<w:tr>")
            if (header) body.append("<w:trPr><w:tblHeader/></w:trPr>")
            cells.forEach { c ->
                body.append("<w:tc><w:tcPr><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"")
                    .append(if (header) accent else "FFFFFF").append("\"/></w:tcPr>")
                body.append("<w:p><w:pPr><w:spacing w:after=\"20\"/></w:pPr><w:r><w:rPr>")
                if (header) body.append("<w:b/><w:color w:val=\"FFFFFF\"/>")
                body.append("<w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/></w:rPr>")
                body.append("<w:t xml:space=\"preserve\">")
                    .append(c.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                    .append("</w:t></w:r></w:p></w:tc>")
            }
            body.append("</w:tr>")
        }

        fun pageBreak() {
            body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
        }

        fun toBytes(): ByteArray {
            val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>${body}<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="680" w:right="680" w:bottom="680" w:left="680" w:header="0" w:footer="0" w:gutter="0"/></w:sectPr></w:body></w:document>"""
            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
            val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("[Content_Types].xml"))
                zip.write(contentTypes.toByteArray(StandardCharsets.UTF_8)); zip.closeEntry()
                zip.putNextEntry(ZipEntry("_rels/.rels"))
                zip.write(rels.toByteArray(StandardCharsets.UTF_8)); zip.closeEntry()
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(document.toByteArray(StandardCharsets.UTF_8)); zip.closeEntry()
            }
            return out.toByteArray()
        }
    }

    // ── report card content ────────────────────────────────────────────────

    fun writeStudentReport(out: Path, data: SchoolData, result: TermResult, layout: TemplateLayout = TemplateLayout.CLASSIC): Path {
        Files.createDirectories(out.toAbsolutePath().parent)
        Files.write(out, studentReportBytes(data, result))
        return out
    }

    fun writeClassReport(out: Path, data: SchoolData, results: List<TermResult>): Path {
        val doc = Doc()
        results.forEachIndexed { i, r ->
            if (i > 0) doc.pageBreak()
            render(doc, data, r)
        }
        Files.createDirectories(out.toAbsolutePath().parent)
        Files.write(out, doc.toBytes())
        return out
    }

    fun studentReportBytes(data: SchoolData, result: TermResult): ByteArray {
        val doc = Doc()
        render(doc, data, result)
        return doc.toBytes()
    }

    private fun render(doc: Doc, data: SchoolData, result: TermResult) {
        val student = data.students.firstOrNull { it.id == result.studentId } ?: return
        val school = data.school
        val accent = if (school.colorPrimary.isBlank()) "1F4E79" else colorHex(school.colorPrimary)
        val ink = if (school.colorSecondary.isBlank()) "222222" else colorHex(school.colorSecondary)
        val term = data.academicYears.asSequence()
            .flatMap { y -> y.terms.asSequence().map { t -> y to t } }
            .firstOrNull { it.second.id == result.termId }

        doc.p(school.name, bold = true, size = 32, align = "center", color = ink, spacingAfter = 20)
        if (school.motto.isNotBlank()) doc.p("\u201C${school.motto}\u201D", italic = true, size = 20, align = "center", spacingAfter = 20)
        val addr = school.address + if (school.phone.isNotBlank()) " · Tel: ${school.phone}" else ""
        if (addr.isNotBlank()) doc.p(addr, size = 18, align = "center", color = "777777", spacingAfter = 60)
        doc.p("TERMLY REPORT CARD", bold = true, size = 24, align = "center", color = accent, spacingAfter = 120)

        val cls = data.enrollments.lastOrNull { it.studentId == student.id }
            ?.let { e -> data.classes.firstOrNull { it.id == e.classId } }
        val clsName = cls?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" } ?: "—"

        doc.table(
            listOf("Student", "Admission No", "Class", "Term", "Year"),
            listOf(listOf(
                student.fullName,
                student.admissionNo.ifBlank { "—" },
                clsName,
                "Term ${term?.second?.number ?: 1}",
                term?.first?.year ?: ""
            ))
        )

        // subject marks table
        val subjectRows = data.subjects.filter { s -> result.subjectResults.any { it.subjectId == s.id } }
            .map { s ->
                val r = result.subjectResults.first { it.subjectId == s.id }
                listOf(
                    s.name, r.total.toString(), fmt(r.percentage) + "%", r.grade,
                    if (r.points > 0) r.points.toString() else "",
                    if (r.remark.isBlank()) "" else r.remark
                )
            }
        doc.p("Subject Performance", bold = true, size = 22, color = accent, spacingAfter = 40)
        doc.table(
            listOf("Subject", "Total", "%", "Grade", "Pts", "Remark"),
            subjectRows.ifEmpty { listOf(listOf("No marks recorded", "", "", "", "", "")) }
        )

        // summary
        val overall = buildString {
            result.aggregate?.let { append("Aggregate $it") }
            result.division?.let { if (isNotEmpty()) append(" · "); append(it) }
            result.uceResult?.let { if (isNotEmpty()) append(" · "); append(it) }
            result.uacePoints?.let { if (isNotEmpty()) append(" · "); append("Points $it") }
            result.uaceResult?.let { if (isNotEmpty()) append(" · "); append(it) }
            if (isEmpty()) append("Average ${fmt(result.averagePercent)}%")
        }
        val posLine = buildList {
            if (result.classPosition > 0) add("Class position: ${result.classPosition}")
            if (result.streamPosition > 0) add("Stream position: ${result.streamPosition}")
        }.joinToString(" · ")

        doc.table(
            listOf("Overall Result", "Position", "Average"),
            listOf(listOf(overall, posLine.ifBlank { "—" }, fmt(result.averagePercent) + "%"))
        )

        // comments
        val classComments = data.comments
            .filter { it.studentId == student.id && it.termId == result.termId }
            .joinToString("\n") { it.text }
        doc.p("Class Teacher's Comments", bold = true, size = 22, color = accent, spacingAfter = 40)
        doc.p(classComments.ifBlank { " " }, size = 22, spacingAfter = 120)

        val head = data.teachers.firstOrNull { it.role == com.derycode.srs.core.model.Role.HEAD_TEACHER }
        val classTeacher = cls?.classTeacherId?.let { id -> data.teachers.firstOrNull { it.id == id } }
        doc.p("Class Teacher: ${classTeacher?.name ?: "____________________"}          Head Teacher: ${head?.name ?: "____________________"}",
            size = 20, spacingAfter = 200)
        doc.p("Sign: ______________________          Sign: ______________________", size = 20, spacingAfter = 100)

        val scheme = data.gradingSchemes.firstOrNull { it.id == result.schemeId }
        val key = scheme?.boundaries?.sortedByDescending { it.minScore }
            ?.joinToString(" · ") { "${it.grade} ${it.minScore.toInt()}-${it.maxScore.toInt()}%" }
        doc.p((if (key != null) "Grading: $key — " else "") + "Printed by DeryCode School Report Maker",
            size = 14, align = "center", color = "999999")
    }

    /** Accepts hex like #1F4E79 or 1F4E79; falls back to Word's dark blue. */
    private fun colorHex(c: String): String {
        val h = c.removePrefix("#").trim()
        return if (h.length == 6 && h.all { Character.isLetterOrDigit(it) }) h.uppercase() else "1F4E79"
    }

    private fun fmt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else String.format("%.1f", d)
}
