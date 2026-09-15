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
        var logoBytes: ByteArray? = null
        var logoExt: String = "png"
        /** Shown centered in every printed page footer, with page numbers. */
        var footerText: String = ""

        /** Inserts the school logo, centered, at the current position. Safe to call multiple times
         *  (e.g. once per student page) — they all reference the same embedded image part. */
        fun logo(bytes: ByteArray, ext: String) {
            logoBytes = bytes
            logoExt = if (ext.lowercase() in listOf("png", "jpg", "jpeg")) ext.lowercase() else "png"
            val img = try { javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)) } catch (_: Exception) { null }
            val targetHeightPx = 72
            val widthPx = if (img != null && img.height > 0) (img.width.toDouble() / img.height * targetHeightPx).toInt().coerceAtLeast(1) else targetHeightPx
            val emuW = widthPx.toLong() * 9525L
            val emuH = targetHeightPx.toLong() * 9525L
            body.append("""<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="80"/></w:pPr><w:r><w:drawing>""")
                .append("""<wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" distT="0" distB="0" distL="0" distR="0">""")
                .append("""<wp:extent cx="$emuW" cy="$emuH"/><wp:docPr id="1" name="SchoolLogo"/>""")
                .append("""<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">""")
                .append("""<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">""")
                .append("""<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">""")
                .append("""<pic:nvPicPr><pic:cNvPr id="1" name="SchoolLogo"/><pic:cNvPicPr/></pic:nvPicPr>""")
                .append("""<pic:blipFill><a:blip r:embed="rIdLogo0" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>""")
                .append("""<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$emuW" cy="$emuH"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>""")
                .append("""</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""")
        }

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
            body.append("<w:tr><w:trPr><w:cantSplit/>")
            if (header) body.append("<w:tblHeader/>")
            body.append("</w:trPr>")
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
            val hasLogo = logoBytes != null
            val hasFooter = footerText.isNotBlank()
            val logoMime = if (logoExt == "png") "png" else "jpeg"
            val footerXml = if (hasFooter) """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="0"/></w:pPr><w:r><w:rPr><w:color w:val="999999"/><w:sz w:val="14"/></w:rPr><w:t xml:space="preserve">""" + esc(footerText) + """ — Page </w:t></w:r><w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r><w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:rPr><w:color w:val="999999"/><w:sz w:val="14"/></w:rPr><w:t>1</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r></w:p></w:ftr>""" else ""
            val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<w:body>${body}<w:sectPr>${if (hasFooter) """<w:footerReference w:type="default" r:id="rIdFooter1"/>""" else ""}<w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="680" w:right="680" w:bottom="680" w:left="680" w:header="0" w:footer="340" w:gutter="0"/></w:sectPr></w:body></w:document>"""
            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>${if (hasLogo) """<Default Extension="$logoExt" ContentType="image/$logoMime"/>""" else ""}<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>${if (hasFooter) """<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>""" else ""}</Types>"""
            val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
            val documentRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${if (hasLogo) """<Relationship Id="rIdLogo0" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/logo.$logoExt"/>""" else ""}${if (hasFooter) """<Relationship Id="rIdFooter1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>""" else ""}</Relationships>"""
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("[Content_Types].xml"))
                zip.write(contentTypes.toByteArray(StandardCharsets.UTF_8)); zip.closeEntry()
                zip.putNextEntry(ZipEntry("_rels/.rels"))
                zip.write(rels.toByteArray(StandardCharsets.UTF_8)); zip.closeEntry()
                if (hasLogo) {
                    zip.putNextEntry(ZipEntry("word/media/logo.$logoExt"))
                    zip.write(logoBytes!!); zip.closeEntry()
                }
                if (hasLogo || hasFooter) {
                    zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
                    zip.write(documentRels.toByteArray(StandardCharsets.UTF_8)); zip.closeEntry()
                }
                if (hasFooter) {
                    zip.putNextEntry(ZipEntry("word/footer1.xml"))
                    zip.write(footerXml.toByteArray(StandardCharsets.UTF_8)); zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(document.toByteArray(StandardCharsets.UTF_8)); zip.closeEntry()
            }
            return out.toByteArray()
        }
    }

    /** Reads the school's logo bytes off disk if one is configured (safe: never throws). */
    private fun readLogo(school: com.derycode.srs.core.model.School): Pair<ByteArray, String>? {
        val path = school.logoPath ?: return null
        return try {
            val bytes = Files.readAllBytes(Path.of(path))
            val ext = path.substringAfterLast('.', "png").lowercase()
            bytes to (if (ext in listOf("png", "jpg", "jpeg")) ext else "png")
        } catch (_: Exception) { null }
    }

    // ── class merit list (broadsheet) ──────────────────────────────────────
    /** Word document: full class broadsheet — students x subjects with positions. */
    fun writeMeritList(out: Path, data: SchoolData, results: List<TermResult>, classLabel: String, termLabel: String): Path {
        val doc = Doc()
        val s = data.school
        readLogo(s)?.let { (bytes, ext) -> doc.logo(bytes, ext) }
        doc.p(s.name, bold = true, size = 32, align = "center", spacingAfter = 20)
        val contact = listOf(s.address, s.phone).filter { it.isNotBlank() }.joinToString(" • ")
        if (contact.isNotBlank()) doc.p(contact, size = 18, align = "center", color = "666666", spacingAfter = 20)
        doc.p("CLASS MERIT LIST", bold = true, size = 26, align = "center", spacingAfter = 40)
        doc.p("$classLabel — $termLabel (${results.size} students)", size = 20, align = "center", color = "444444", spacingAfter = 200)
        val subjectIds = data.subjects.map { it.id }.filter { id -> results.any { r -> r.subjectResults.any { it.subjectId == id } } }
        val code = { id: String -> (data.subjects.firstOrNull { it.id == id }?.name ?: "?").take(4).uppercase() }
        val header = listOf("#", "Student") + subjectIds.map(code) + listOf("Avg %", "Result", "Pos")
        val sorted = results.sortedBy { if (it.classPosition > 0) it.classPosition else Int.MAX_VALUE }
        val rows = sorted.mapIndexed { i, r ->
            val stu = data.students.firstOrNull { it.id == r.studentId }
            listOf((i + 1).toString(), stu?.fullName ?: r.studentId) +
                subjectIds.map { id -> r.subjectResults.firstOrNull { it.subjectId == id }?.let { it.percentage.toInt().toString() } ?: "-" } +
                listOf(r.averagePercent.toInt().toString(),
                    r.division ?: r.uceResult ?: r.uaceResult ?: (r.uacePoints?.let { "$it pts" } ?: r.aggregate?.let { "Agg $it" } ?: "-"),
                    if (r.classPosition > 0) r.classPosition.toString() else "-")
        }
        doc.table(header, rows)
        val avg = if (results.isEmpty()) 0.0 else results.map { it.averagePercent }.average()
        doc.p("Class average: ${"%.1f".format(avg)}% — generated by ${s.name.ifBlank { "School Report Maker" }}", size = 18, color = "666666", spacingAfter = 0)
        Files.createDirectories(out.toAbsolutePath().parent)
        Files.write(out, doc.toBytes())
        return out
    }

    // ── fee defaulter list ─────────────────────────────────────────────────
    /** Word document: fee defaulters for one class & term, with class totals. */
    fun writeDefaulterList(out: Path, data: SchoolData, classLabel: String, termLabel: String,
                           currency: String, rows: List<List<String>>, totals: List<String>): Path {
        val doc = Doc()
        val s = data.school
        readLogo(s)?.let { (bytes, ext) -> doc.logo(bytes, ext) }
        doc.p(s.name, bold = true, size = 32, align = "center", spacingAfter = 20)
        val contact = listOf(s.address, s.phone).filter { it.isNotBlank() }.joinToString(" • ")
        if (contact.isNotBlank()) doc.p(contact, size = 18, align = "center", color = "666666", spacingAfter = 20)
        doc.p("FEE DEFAULTERS LIST", bold = true, size = 26, align = "center", spacingAfter = 40)
        doc.p("$classLabel — $termLabel", size = 20, align = "center", color = "444444", spacingAfter = 200)
        if (rows.isEmpty()) {
            doc.p("All students in this class have cleared their fees. No defaulters.", size = 22, align = "center", color = "2E7D32")
        } else {
            doc.table(listOf("Student", "Term fee ($currency)", "Paid ($currency)", "Balance ($currency)"), rows)
            doc.p("Students on the list: ${rows.size}", bold = true, size = 20, spacingAfter = 20)
            doc.p("Expected: $currency ${totals[0]}    Collected: $currency ${totals[1]}    Outstanding: $currency ${totals[2]}",
                size = 20, spacingAfter = 60)
            doc.p("Signature (Bursar): ..............................        Date: ..............................",
                size = 18, color = "666666", spacingAfter = 0)
        }
        Files.createDirectories(out.toAbsolutePath().parent)
        Files.write(out, doc.toBytes())
        return out
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
        val cls = data.enrollments.lastOrNull { it.studentId == student.id }
            ?.let { e -> data.classes.firstOrNull { it.id == e.classId } }
        val clsName = cls?.let { if (it.stream.isBlank()) it.name else "${it.name} ${it.stream}" } ?: "—"
        // scheme early: the grade description (indicator) column and the grading key both read from it
        val gradeScheme = data.gradingSchemes.firstOrNull { it.id == result.schemeId }
            ?: data.gradingSchemes.firstOrNull { it.level == cls?.level && it.active }
            ?: data.gradingSchemes.firstOrNull()
        doc.footerText = school.name.ifBlank { "School Report Maker" }

        // ── header — compact: the whole card must print on ONE page ──
        readLogo(school)?.let { (bytes, ext) -> doc.logo(bytes, ext) }
        doc.p(school.name, bold = true, size = 28, align = "center", color = ink, spacingAfter = 10)
        val contact = listOfNotNull(
            school.address.ifBlank { null },
            school.phone.ifBlank { null }?.let { "Tel: $it" }).joinToString(" · ")
        val mottoLine = school.motto.ifBlank { null }?.let { "\u201C$it\u201D" }
        val line2 = listOfNotNull(mottoLine, contact.ifBlank { null }).joinToString(" · ")
        if (line2.isNotBlank()) doc.p(line2, italic = true, size = 16, align = "center", color = "777777", spacingAfter = 20)
        doc.p("TERMLY REPORT CARD", bold = true, size = 22, align = "center", color = accent, spacingAfter = 30)

        // ── student identity ──
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

        // ── subject performance: per-assessment columns + grade description (indicator) ──
        val subjectIds = result.subjectResults.map { it.subjectId }.toSet()
        val comps = data.components.filter { c -> c.active && data.marks.any { m ->
            m.studentId == student.id && m.termId == result.termId && m.componentId == c.id && m.subjectId in subjectIds } }
        val compList = comps.take(4)
        val compCode = { id: String -> data.components.firstOrNull { it.id == id }?.code?.ifBlank { null } ?: data.components.firstOrNull { it.id == id }?.name?.take(6) ?: "?" }
        val num = { d: Double -> if (d == Math.floor(d)) d.toLong().toString() else fmt(d) }

        val subjectRows = data.subjects.filter { s -> result.subjectResults.any { it.subjectId == s.id } }
            .map { s ->
                val r = result.subjectResults.first { it.subjectId == s.id }
                val perComp = compList.map { c ->
                    val m = data.marks.firstOrNull { mm -> mm.studentId == student.id && mm.subjectId == s.id && mm.termId == result.termId && mm.componentId == c.id }
                    when {
                        m == null -> "—"
                        m.type == com.derycode.srs.core.model.MarkType.ABS -> "ABS"
                        m.type == com.derycode.srs.core.model.MarkType.MISSING -> "MISS"
                        else -> num(m.score)
                    }
                }
                val desc = gradeScheme?.boundaries?.firstOrNull { it.grade == r.grade }
                    ?.let { it.label.ifBlank { it.remark } }.orEmpty()
                listOf(s.name) + perComp + listOf(r.total.toInt().toString(), fmt(r.percentage) + "%", r.grade, desc,
                    if (r.positionInSubject > 0) r.positionInSubject.toString() else "",
                    if (r.remark.isBlank()) "" else r.remark)
            }
        doc.p("Subject Performance", bold = true, size = 20, color = accent, spacingAfter = 20)
        doc.table(
            listOf("Subject") + compList.map { compCode(it.id) } + listOf("Total", "%", "Grade", "Description", "Pos", "Remark"),
            subjectRows.ifEmpty { listOf(listOf("No marks recorded")) }
        )

        // ── overall summary ──
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

        // ── class performance analysis ──
        val termObj = term?.second
        val yearObj = term?.first
        val classmates = data.enrollments
            .filter { e -> e.academicYearId == (yearObj?.id ?: "") && e.classId == (cls?.id ?: "") }
            .map { it.studentId }.toSet()
        val classAverages = classmates.mapNotNull { sid ->
            val ms = data.marks.filter { m -> m.studentId == sid && m.termId == result.termId && m.type == com.derycode.srs.core.model.MarkType.VALUE && m.maxScore > 0 }
            if (ms.isEmpty()) null else ms.sumOf { it.score } / ms.sumOf { it.maxScore.toDouble() } * 100.0
        }
        if (classAverages.size >= 2) {
            val classAvg = classAverages.average()
            doc.p("Class Performance Analysis", bold = true, size = 20, color = accent, spacingAfter = 20)
            doc.table(
                listOf("Class average", "Highest in class", "Lowest in class", "Students in class"),
                listOf(listOf(fmt(classAvg) + "%", fmt(classAverages.max()) + "%", fmt(classAverages.min()) + "%", classmates.size.toString()))
            )
        }

        // ── grading key + how to read the marks (indicator & description) ──
        if (gradeScheme != null && gradeScheme.boundaries.isNotEmpty()) {
            val keyLine = gradeScheme.boundaries.sortedByDescending { it.minScore }
                .joinToString("  ·  ") { "${it.grade}: ${num(it.minScore)}–${num(it.maxScore)}% — ${it.label.ifBlank { it.remark }}" }
            doc.p("Grading key (${gradeScheme.name}): $keyLine", size = 14, color = "555555", spacingAfter = 20)
        }
        val legend = buildList {
            compList.forEach { c -> add("${compCode(c.id)} = ${c.name}") }
            add("Total = sum of assessments")
            add("% = share of maximum mark")
            add("Pos = position in subject")
            add("ABS = absent · MISS = missing mark")
        }
        doc.p("How to read the marks: " + legend.joinToString("  ·  "), size = 14, color = "555555", spacingAfter = 30)

        // ── fees — one compact table, all categories (each stays its own ledger) ──
        val enrollment = data.enrollments.lastOrNull { it.studentId == student.id }
        val feeLines = enrollment?.let { e -> data.feeStructures.filter { it.classId == e.classId && it.termId == result.termId && it.amount > 0 } }.orEmpty()
        if (feeLines.isNotEmpty()) {
            val feeRows = feeLines.map { fee ->
                val paid = data.feePayments.filter { it.studentId == student.id && it.termId == result.termId && it.category == fee.category }.sumOf { it.amount }
                listOf(fee.category.label, fmt(fee.amount), fmt(paid), fmt((fee.amount - paid).coerceAtLeast(0.0)))
            }
            doc.p("Fees (${data.settings.currencySymbol})", bold = true, size = 20, color = accent, spacingAfter = 20)
            doc.table(listOf("Category", "Term fees", "Paid to date", "Balance"), feeRows)
        }

        // ── attendance — one compact line ──
        val att = data.attendance.filter { it.studentId == student.id }
        if (att.isNotEmpty()) {
            val present = att.count { it.status == "PRESENT" }
            val late = att.count { it.status == "LATE" }
            val absent = att.count { it.status == "ABSENT" }
            val pct = (present * 100.0 / att.size)
            doc.p("Attendance: $present of ${att.size} days present (${fmt(pct)}%) · late $late · absent $absent",
                size = 14, color = "555555", spacingAfter = 30)
        }

        // ── comments & signatures ──
        val classComments = data.comments
            .filter { it.studentId == student.id && it.termId == result.termId }
            .joinToString("\n") { it.text }
        doc.p("Class Teacher's Comments", bold = true, size = 20, color = accent, spacingAfter = 20)
        doc.p(classComments.ifBlank { " " }, size = 18, spacingAfter = 40)

        doc.p("Head Teacher's Remarks", bold = true, size = 20, color = accent, spacingAfter = 20)
        doc.p(" ", size = 18, spacingAfter = 40)

        val head = data.teachers.firstOrNull { it.role == com.derycode.srs.core.model.Role.HEAD_TEACHER }
        val classTeacher = cls?.classTeacherId?.let { id -> data.teachers.firstOrNull { it.id == id } }
        doc.p("Class Teacher: ${classTeacher?.name ?: "____________________"}          Head Teacher: ${head?.name ?: "____________________"}",
            size = 18, spacingAfter = 80)
        doc.p("Sign: ______________________          Sign: ______________________", size = 18, spacingAfter = 40)

        val serial = reportSerial(data, result)
        doc.p("Report Serial No: $serial", bold = true, size = 16, align = "center", color = ink, spacingAfter = 10)
        doc.p("Verify this report with the school administration using serial $serial — any report without it is invalid.",
            size = 12, align = "center", color = "999999", spacingAfter = 10)
        val today = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date())
        doc.p("Printed $today by DeryCode School Report Maker", size = 14, align = "center", color = "999999", spacingAfter = 0)
    }

    /** Unique, tamper-evident serial for a report — derived from school + student + term. */
    fun reportSerial(data: SchoolData, result: TermResult): String {
        val src = "${data.school.id}|${data.school.name}|${result.studentId}|${result.termId}|${result.averagePercent}|${result.classPosition}"
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(src.toByteArray())
        return "SRS-" + d.joinToString("") { "%02x".format(it) }.take(10).uppercase()
    }

    /** Accepts hex like #1F4E79 or 1F4E79; falls back to Word's dark blue. */
    private fun colorHex(c: String): String {
        val h = c.removePrefix("#").trim()
        return if (h.length == 6 && h.all { Character.isLetterOrDigit(it) }) h.uppercase() else "1F4E79"
    }

    private fun fmt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else String.format("%.1f", d)
}
