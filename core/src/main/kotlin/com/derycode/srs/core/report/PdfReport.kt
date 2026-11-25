package com.derycode.srs.core.report

import com.derycode.srs.core.model.CommentType
import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.model.Subject
import com.derycode.srs.core.model.SubjectResult
import com.derycode.srs.core.model.TermResult
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt

/**
 * Real PDF output — one A4 card per learner, no browser and no print dialog.
 *
 * A whole class is one file: every learner starts a new page, so a head teacher
 * presses one button and gets a single PDF ready for the printer. The same rules
 * as the printed HTML card apply: a subject nobody sat prints a dash, never a
 * zero, and comments and initials are never blank.
 */
object PdfReport {

    private const val DASH = "\u2014"          // em dash, the printed "not sat" mark
    private const val MARGIN = 26f
    private val A4 = PDRectangle.A4            // 595 x 842 pt

    private val BODY: PDFont = PDType1Font.HELVETICA
    private val BOLD: PDFont = PDType1Font.HELVETICA_BOLD
    private val ITALIC: PDFont = PDType1Font.HELVETICA_OBLIQUE

    private val NAVY = floatArrayOf(0.043f, 0.165f, 0.353f)
    private val SOFT = floatArrayOf(0.863f, 0.922f, 0.980f)
    private val LINE = floatArrayOf(0.498f, 0.690f, 0.878f)
    private val INK = floatArrayOf(0.043f, 0.165f, 0.290f)

    /** One file, one page per learner. */
    fun writeClassReport(
        out: Path,
        data: SchoolData,
        results: List<TermResult>,
        accentHex: String? = null
    ): Path {
        PDDocument().use { doc ->
            results.forEach { r -> drawCard(doc, data, r, accentHex) }
            if (results.isEmpty()) doc.addPage(PDPage(A4)) // never write a zero-page file
            Files.createDirectories(out.toAbsolutePath().parent)
            doc.save(out.toFile())
        }
        return out
    }

    fun writeStudentReport(out: Path, data: SchoolData, result: TermResult, accentHex: String? = null): Path =
        writeClassReport(out, data, listOf(result), accentHex)

    /** Bytes, for callers that stream the file rather than write it. */
    fun classReportBytes(data: SchoolData, results: List<TermResult>, accentHex: String? = null): ByteArray {
        PDDocument().use { doc ->
            results.forEach { r -> drawCard(doc, data, r, accentHex) }
            if (results.isEmpty()) doc.addPage(PDPage(A4))
            val buf = java.io.ByteArrayOutputStream()
            doc.save(buf)
            return buf.toByteArray()
        }
    }

    // ── one card ─────────────────────────────────────────────────────────────

    private fun drawCard(doc: PDDocument, data: SchoolData, result: TermResult, accentHex: String?) {
        val page = PDPage(A4)
        doc.addPage(page)
        val student = data.students.firstOrNull { it.id == result.studentId }
        val school = data.school
        val cls = data.classes.firstOrNull { c ->
            data.enrollments.any { e -> e.studentId == result.studentId && e.classId == c.id }
        }
        val term = data.academicYears.asSequence()
            .flatMap { y -> y.terms.asSequence().map { y to it } }
            .firstOrNull { it.second.id == result.termId }
        val subjects = result.subjectResults.mapNotNull { r ->
            data.subjects.firstOrNull { it.id == r.subjectId }?.let { it to r }
        }
        val classSize = data.enrollments.count { it.classId == cls?.id && it.academicYearId != null }
            .coerceAtLeast(1)

        PDPageContentStream(doc, page).use { cs ->
            val width = A4.width - MARGIN * 2
            var y = A4.height - MARGIN

            // ── header ──────────────────────────────────────────────────────
            cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
            centered(cs, school.name.uppercase(), BOLD, 15f, MARGIN, width, y)
            y -= 13f
            val contact = listOfNotNull(
                school.phone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
                school.email.takeIf { it.isNotBlank() },
                school.address.takeIf { it.isNotBlank() }
            ).joinToString("  ·  ")
            if (contact.isNotBlank()) {
                cs.setNonStrokingColor(INK[0], INK[1], INK[2])
                centered(cs, contact, BODY, 7.5f, MARGIN, width, y)
                y -= 11f
            }
            if (school.motto.isNotBlank()) {
                centered(cs, school.motto, ITALIC, 7.5f, MARGIN, width, y)
                y -= 10f
            }

            // term box, top right of the header band
            val termLabel = "TERM ${term?.second?.number ?: "-"} SUMMARY REPORT"
            val yearLabel = term?.first?.year ?: ""
            val boxW = 132f
            val boxX = A4.width - MARGIN - boxW
            val boxY = y + 22f
            cs.setStrokingColor(LINE[0], LINE[1], LINE[2])
            cs.setLineWidth(0.9f)
            cs.addRect(boxX, boxY, boxW, 30f); cs.stroke()
            cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
            centered(cs, termLabel, BOLD, 7.5f, boxX, boxW, boxY + 19f)
            centered(cs, yearLabel, BODY, 7.5f, boxX, boxW, boxY + 8f)

            // ── student information band ────────────────────────────────────
            y -= 6f
            band(cs, "STUDENT INFORMATION", MARGIN, y, width, 13f)
            y -= 13f

            val name = listOfNotNull(student?.firstName, student?.lastName)
                .joinToString(" ").ifBlank { student?.admissionNo ?: "-" }
            val infoRows = listOf(
                "STUDENT NAME" to name.uppercase(),
                "ADMISSION NO." to (student?.admissionNo ?: "-"),
                "CLASS" to (cls?.name ?: "-"),
                "STREAM" to (cls?.stream?.ifBlank { "-" } ?: "-"),
                "RESULT" to resultLabel(result)
            )
            infoRows.chunked(2).forEach { pair ->
                pair.forEachIndexed { idx, (k, v) ->
                    val colX = MARGIN + idx * (width / 2f)
                    cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
                    text(cs, "$k : ", BOLD, 8f, colX + 2f, y - 9f)
                    cs.setNonStrokingColor(INK[0], INK[1], INK[2])
                    val kw = BOLD.getStringWidth("$k : ") / 1000f * 8f
                    text(cs, v, BOLD, 8f, colX + 2f + kw, y - 9f)
                }
                y -= 11.5f
            }

            // ── marks table ─────────────────────────────────────────────────
            y -= 3f
            val cols = floatArrayOf(112f, 26f, 26f, 26f, 40f, 40f, 40f, 30f, 96f, 34f)
            val headers = arrayOf("SUBJECT", "A1", "A2", "A3", "FRNTV\n20%", "EXAM\n80%", "TOTAL\n100%", "GRADE", "TEACHER COMMENT", "INITIALS")
            cs.setNonStrokingColor(SOFT[0], SOFT[1], SOFT[2])
            cs.addRect(MARGIN, y - 20f, width, 20f); cs.fill()
            var cx = MARGIN
            headers.forEachIndexed { i, h ->
                cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
                val lines = h.split("\n")
                if (lines.size == 1) {
                    centered(cs, h, BOLD, 6.2f, cx, cols[i], y - 12.5f)
                } else {
                    centered(cs, lines[0], BOLD, 6.2f, cx, cols[i], y - 8.5f)
                    centered(cs, lines[1], BOLD, 6.2f, cx, cols[i], y - 15.5f)
                }
                cx += cols[i]
            }
            y -= 20f

            cs.setStrokingColor(LINE[0], LINE[1], LINE[2])
            cs.setLineWidth(0.5f)

            subjects.forEach { (subj, r) ->
                val rowH = 14.5f
                if (y - rowH < 250f) return@forEach // keep the footer blocks on this page
                val cells = arrayOf(
                    subj.name.uppercase(),
                    cellOf(r, r.assessments.getOrNull(0) ?: -1.0),
                    cellOf(r, r.assessments.getOrNull(1) ?: -1.0),
                    cellOf(r, r.assessments.getOrNull(2) ?: -1.0),
                    cellOf(r, r.formativePercent),
                    cellOf(r, r.examPercent),
                    if (r.hasMarks) fmt(r.percentage) else DASH,
                    if (r.hasMarks) r.grade else DASH,
                    commentFor(data, subj, r, result),
                    initialsFor(data, subj, cls?.id)
                )
                var x = MARGIN
                cells.forEachIndexed { i, value ->
                    cs.addRect(x, y - rowH, cols[i], rowH); cs.stroke()
                    val font = if (i == 0) BOLD else BODY
                    val size = if (i == 8) 5.8f else 6.8f
                    val maxW = cols[i] - 5f
                    val shown = if (i == 8) wrapToWidth(value, font, size, maxW).firstOrNull() ?: "" else clip(value, font, size, maxW)
                    val tx = if (i == 0 || i == 8) x + 2.5f else x + 2.5f
                    cs.setNonStrokingColor(INK[0], INK[1], INK[2])
                    text(cs, shown, font, size, tx, y - rowH + 4.6f)
                    x += cols[i]
                }
                y -= rowH
            }

            // ── totals line ─────────────────────────────────────────────────
            y -= 4f
            val summary = buildString {
                append("AVERAGE: ${fmt(result.averagePercent)}%")
                if (result.classPosition > 0) append("    ·    POSITION: ${result.classPosition} of $classSize")
                result.aggregate?.let { append("    ·    AGGREGATE: $it") }
                result.division?.let { append("    ·    DIVISION: $it") }
                result.uceResult?.let { append("    ·    RESULT: $it") }
                result.uacePoints?.let { append("    ·    POINTS: $it") }
            }
            cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
            text(cs, summary, BOLD, 7.6f, MARGIN, y)

            // ── comment boxes ───────────────────────────────────────────────
            y -= 12f
            val boxH = 44f
            val halfW = (width - 8f) / 2f
            commentBox(
                cs, MARGIN, y - boxH, halfW, boxH, "CLASS TEACHER'S COMMENT",
                ReportEngine.classCommentFor(data, result.studentId, result.termId)
                    .ifBlank { autoRemark(result, classSize) }
            )
            commentBox(
                cs, MARGIN + halfW + 8f, y - boxH, halfW, boxH, "HEAD TEACHER'S COMMENT",
                ReportEngine.headCommentFor(data, result.studentId, result.termId)
                    .ifBlank { autoRemark(result, classSize) }
            )
            y -= boxH + 16f

            // ── signatures ──────────────────────────────────────────────────
            val sigY = y - 18f
            cs.setStrokingColor(INK[0], INK[1], INK[2])
            cs.setLineWidth(0.6f)
            val thirdW = (width - 24f) / 3f
            listOf("CLASS TEACHER", "HEAD TEACHER", "SCHOOL STAMP").forEachIndexed { i, label ->
                val sx = MARGIN + i * (thirdW + 12f)
                cs.moveTo(sx, sigY); cs.lineTo(sx + thirdW, sigY); cs.stroke()
                centered(cs, label, BOLD, 6.4f, sx, thirdW, sigY - 9f)
            }

            // ── footer ──────────────────────────────────────────────────────
            centered(
                cs, "DeryCode School Report Maker · ${school.name} · Term ${term?.second?.number ?: "-"} ${term?.first?.year ?: ""}",
                BODY, 6f, MARGIN, width, MARGIN + 4f
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun resultLabel(r: TermResult): String = when {
        !r.division.isNullOrBlank() -> r.division!!
        !r.uceResult.isNullOrBlank() -> r.uceResult!!
        !r.uaceResult.isNullOrBlank() -> r.uaceResult!!
        r.uacePoints != null -> "${r.uacePoints} pts"
        else -> "${fmt(r.averagePercent)}%"
    }

    /** A subject with no mark at all prints a dash, never a zero. */
    private fun cellOf(r: SubjectResult, v: Double): String =
        if (!r.hasMarks || v < 0) DASH else fmt(v)

    private fun commentFor(data: SchoolData, subj: Subject, r: SubjectResult, result: TermResult): String {
        val own = data.comments.firstOrNull {
            it.studentId == result.studentId && it.termId == result.termId &&
                it.subjectId == subj.id && it.type == CommentType.SUBJECT
        }?.text?.trim().orEmpty()
        if (own.isNotEmpty()) return own
        return if (r.hasMarks) r.remark.trim() else ""
    }

    private fun initialsFor(data: SchoolData, subj: Subject, classId: String?): String {
        val teacher = data.teachers.firstOrNull { t ->
            data.assignments.any { a -> a.teacherId == t.id && a.subjectId == subj.id && a.classId == classId }
        }
        val teacherInitials = teacher?.name?.split(' ', '.', '-')
            ?.filter { it.isNotBlank() }?.map { it.first().uppercaseChar() }?.take(3)?.joinToString("")
            .orEmpty()
        return SubjectInitials.printed(subj, teacherInitials)
    }

    /** The figures the card can state without putting words in a teacher's mouth. */
    private fun autoRemark(r: TermResult, classSize: Int): String = buildString {
        append("Average ${fmt(r.averagePercent)}%")
        if (r.classPosition > 0) append(" · position ${r.classPosition} of $classSize")
        r.division?.let { append(" · $it") }
        r.aggregate?.let { append(" · aggregate $it") }
    }

    private fun band(cs: PDPageContentStream, label: String, x: Float, y: Float, w: Float, h: Float) {
        cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
        cs.addRect(x, y - h, w, h); cs.fill()
        cs.setNonStrokingColor(1f, 1f, 1f)
        text(cs, label, BOLD, 7.4f, x + 5f, y - h + 4.2f)
    }

    private fun commentBox(
        cs: PDPageContentStream, x: Float, y: Float, w: Float, h: Float, label: String, body: String
    ) {
        cs.setStrokingColor(LINE[0], LINE[1], LINE[2])
        cs.setLineWidth(0.9f)
        cs.addRect(x, y, w, h); cs.stroke()
        cs.setNonStrokingColor(SOFT[0], SOFT[1], SOFT[2])
        cs.addRect(x, y + h - 11f, w, 11f); cs.fill()
        cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
        text(cs, label, BOLD, 6.4f, x + 4f, y + h - 8f)
        cs.setNonStrokingColor(INK[0], INK[1], INK[2])
        wrapToWidth(body, BODY, 7f, w - 8f).take(3).forEachIndexed { i, line ->
            text(cs, line, BODY, 7f, x + 4f, y + h - 22f - i * 9f)
        }
    }

    private fun text(cs: PDPageContentStream, s: String, font: PDFont, size: Float, x: Float, y: Float) {
        if (s.isEmpty()) return
        cs.beginText()
        cs.setFont(font, size)
        cs.newLineAtOffset(x, y)
        cs.showText(sanitize(s))
        cs.endText()
    }

    private fun centered(cs: PDPageContentStream, s: String, font: PDFont, size: Float, x: Float, w: Float, y: Float) {
        val tw = font.getStringWidth(sanitize(s)) / 1000f * size
        text(cs, s, font, size, x + (w - tw) / 2f, y)
    }

    /** Cut to the column, with an ellipsis, so nothing bleeds into the next cell. */
    private fun clip(s: String, font: PDFont, size: Float, maxW: Float): String {
        if (s.isEmpty()) return s
        if (font.getStringWidth(sanitize(s)) / 1000f * size <= maxW) return s
        var cut = s
        while (cut.isNotEmpty() && font.getStringWidth(sanitize("$cut…")) / 1000f * size > maxW) {
            cut = cut.dropLast(1)
        }
        return if (cut.isEmpty()) "" else "$cut…"
    }

    private fun wrapToWidth(s: String, font: PDFont, size: Float, maxW: Float): List<String> {
        if (s.isBlank()) return emptyList()
        val words = s.replace("\n", " \n ").split(" ")
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        fun flush() { if (line.isNotEmpty()) { lines.add(line.toString()); line = StringBuilder() } }
        words.forEach { w ->
            if (w == "\n") { flush(); return@forEach }
            val candidate = if (line.isEmpty()) w else "$line $w"
            if (font.getStringWidth(sanitize(candidate)) / 1000f * size > maxW) {
                flush(); line = StringBuilder(w)
            } else {
                line = StringBuilder(candidate)
            }
        }
        flush()
        return lines
    }

    /** WinAnsi cannot encode every character a school might type; never throw mid-print. */
    private fun sanitize(s: String): String {
        val sb = StringBuilder(s.length)
        s.forEach { ch ->
            val ok = when (ch) {
                '\u2014', '\u2013', '\u2019', '\u201c', '\u201d', '\u00b7', '\u2022' -> true
                else -> ch.code in 32..126 || ch.code in 160..255
            }
            sb.append(if (ok) ch else '?')
        }
        return sb.toString()
    }

    private fun fmt(v: Double): String {
        val r = (v * 10).roundToInt() / 10.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }
}
