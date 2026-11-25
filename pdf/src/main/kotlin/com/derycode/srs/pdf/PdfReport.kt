package com.derycode.srs.pdf

import com.derycode.srs.core.model.CommentType
import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.model.Subject
import com.derycode.srs.core.model.SubjectResult
import com.derycode.srs.core.model.TermResult
import com.derycode.srs.core.report.ReportEngine
import com.derycode.srs.core.report.SubjectInitials
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Real PDF output — one A4 card per learner, laid out like the Standard report.
 *
 * The page is measured before anything is drawn: the footer, signatures and the
 * two comment boxes are pinned to the bottom, then the marks table takes exactly
 * the space that is left. Every subject therefore fits and the card is balanced
 * top to bottom, whatever the subject count. A class of 15 subjects fills the
 * page; a class of 4 spreads the same way instead of leaving a hole.
 */
object PdfReport {

    private const val DASH = "\u2014"           // em dash, the printed "not sat" mark
    private const val MARGIN = 26f
    private val A4 = PDRectangle.A4             // 595 x 842 pt

    private val BODY: PDFont = PDType1Font.HELVETICA
    private val BOLD: PDFont = PDType1Font.HELVETICA_BOLD
    private val ITALIC: PDFont = PDType1Font.HELVETICA_OBLIQUE

    private val NAVY = floatArrayOf(0.043f, 0.165f, 0.353f)
    private val SOFT = floatArrayOf(0.863f, 0.922f, 0.980f)
    private val LINE = floatArrayOf(0.498f, 0.690f, 0.878f)
    private val INK = floatArrayOf(0.043f, 0.165f, 0.290f)

    // column widths, A4 width minus margins = 543 pt
    private val COLS = floatArrayOf(132f, 30f, 30f, 30f, 44f, 44f, 44f, 34f, 110f, 45f)
    private val HEADERS = arrayOf(
        "SUBJECT", "A1", "A2", "A3", "FRNTV\n20%", "EXAM\n80%", "TOTAL\n100%", "GRADE",
        "TEACHER COMMENT", "INITIALS"
    )

    /** Bottom-up anchors, so nothing is ever squeezed off the page. */
    private const val FOOTER_Y = MARGIN + 2f
    private const val SIG_LABEL_Y = MARGIN + 20f
    private const val SIG_LINE_Y = MARGIN + 31f
    private const val BOX_H = 46f
    private const val BOX_BOTTOM = MARGIN + 46f
    private const val SUMMARY_Y = BOX_BOTTOM + BOX_H + 15f
    private const val TABLE_BOTTOM = SUMMARY_Y + 13f
    private const val MIN_ROW = 11.5f
    private const val MAX_ROW = 34f
    private const val MAX_TOP_GAP = 26f
    private const val MAX_BOTTOM_GAP = 54f

    /** One file, one page per learner (a second page only if a class has 30+ subjects). */
    fun writeClassReport(
        out: Path,
        data: SchoolData,
        results: List<TermResult>,
        accentHex: String? = null
    ): Path {
        PDDocument().use { doc ->
            results.forEach { r -> drawCard(doc, data, r) }
            if (results.isEmpty()) doc.addPage(PDPage(A4))
            Files.createDirectories(out.toAbsolutePath().parent)
            doc.save(out.toFile())
        }
        return out
    }

    fun writeStudentReport(out: Path, data: SchoolData, result: TermResult, accentHex: String? = null): Path =
        writeClassReport(out, data, listOf(result))

    fun classReportBytes(data: SchoolData, results: List<TermResult>, accentHex: String? = null): ByteArray {
        PDDocument().use { doc ->
            results.forEach { r -> drawCard(doc, data, r) }
            if (results.isEmpty()) doc.addPage(PDPage(A4))
            val buf = java.io.ByteArrayOutputStream()
            doc.save(buf)
            return buf.toByteArray()
        }
    }

    // ── one card ─────────────────────────────────────────────────────────────

    private fun drawCard(doc: PDDocument, data: SchoolData, result: TermResult) {
        val student = data.students.firstOrNull { it.id == result.studentId }
        val school = data.school
        val cls = data.classes.firstOrNull { c ->
            data.enrollments.any { e -> e.studentId == result.studentId && e.classId == c.id }
        }
        val termPair = data.academicYears.asSequence()
            .flatMap { y -> y.terms.asSequence().map { y to it } }
            .firstOrNull { it.second.id == result.termId }
        val subjects = result.subjectResults.mapNotNull { r ->
            data.subjects.firstOrNull { it.id == r.subjectId }?.let { it to r }
        }
        val classSize = data.enrollments.count { it.classId == cls?.id }.coerceAtLeast(1)

        val page = PDPage(A4)
        doc.addPage(page)

        currentDoc = doc
        var y: Float
        var cs = PDPageContentStream(doc, page)

        // header + student block on the first page
        y = drawHeader(cs, school, student, cls, termPair, result)

        // how much room the table gets, and how tall each row can be
        val tableTop = y
        val area = tableTop - TABLE_BOTTOM
        // Two spacers travel with the table, so leftover space is shared top and
        // bottom instead of pooling into one hole above the comment boxes.
        val slots = subjects.size + 2.5f
        var rowH = min(MAX_ROW, area / slots)
        var perPage = subjects.size
        if (rowH < MIN_ROW) {
            // 30+ subjects: keep the rows readable and continue on another page
            rowH = MIN_ROW
            perPage = (area / rowH).toInt().coerceAtLeast(1)
        }
        val tableHeight = perPage * rowH + 20f
        val leftover = (area - tableHeight).coerceAtLeast(0f)
        val topGap = min(MAX_TOP_GAP, leftover * 0.3f)
        val bottomGap = min(MAX_BOTTOM_GAP, leftover - topGap)
        y -= topGap
        val chunks = subjects.chunked(perPage).ifEmpty { listOf(emptyList()) }

        chunks.forEachIndexed { index, chunk ->
            val last = index == chunks.lastIndex
            if (index > 0) {
                cs.close()
                val p = PDPage(A4)
                doc.addPage(p)
                cs = PDPageContentStream(doc, p)
                y = A4.height - MARGIN
                centered(cs, "${school.name.uppercase()} — continued", BOLD, 8.5f, MARGIN, usableWidth(), y)
                y -= 14f
            }
            y = drawTable(cs, data, result, chunk, y, rowH, cls?.id, index == 0)

            if (last) {
                y = drawSummary(cs, data, result, classSize, gapBefore = bottomGap)
                drawFooterBlocks(cs, data, result, classSize, school, termPair)
                drawFooterLine(cs, school, termPair)
            }
        }
        cs.close()
    }

    private fun usableWidth() = A4.width - MARGIN * 2

    // ── header ───────────────────────────────────────────────────────────────

    private var currentDoc: PDDocument? = null

    private fun docFor(cs: PDPageContentStream): PDDocument? = currentDoc

    private fun drawLogo(
        cs: PDPageContentStream, doc: PDDocument?, path: String?, x: Float, y: Float, size: Float, schoolName: String
    ) {
        val image = path?.takeIf { it.isNotBlank() }?.let { p ->
            try {
                val f = java.io.File(p)
                if (f.exists()) javax.imageio.ImageIO.read(f) else null
            } catch (_: Throwable) { null }
        }
        if (image != null && doc != null) {
            try {
                val pd = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, image)
                cs.drawImage(pd, x, y, size, size)
                return
            } catch (_: Throwable) { /* fall through to the badge */ }
        }
        // no logo on file: a tidy badge with the school's initials, never an empty hole
        val initials = schoolName.split(' ').filter { it.isNotBlank() }
            .map { it.first().uppercaseChar() }.take(3).joinToString("")
        cs.setNonStrokingColor(SOFT[0], SOFT[1], SOFT[2])
        cs.addRect(x, y, size, size); cs.fill()
        cs.setStrokingColor(LINE[0], LINE[1], LINE[2])
        cs.setLineWidth(0.8f)
        cs.addRect(x, y, size, size); cs.stroke()
        cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
        centered(cs, initials, BOLD, 13f, x, size, y + size / 2f - 4.5f)
    }

    private fun drawHeader(
        cs: PDPageContentStream,
        school: com.derycode.srs.core.model.School,
        student: com.derycode.srs.core.model.Student?,
        cls: com.derycode.srs.core.model.SchoolClass?,
        termPair: Pair<com.derycode.srs.core.model.AcademicYear, com.derycode.srs.core.model.Term>?,
        result: TermResult
    ): Float {
        val width = usableWidth()
        var y = A4.height - MARGIN - 8f

        // the school's own logo, left of the name — a real picture when one was uploaded
        val logoSize = 46f
        val logoY = y - logoSize + 12f
        drawLogo(cs, docFor(cs), school.logoPath, MARGIN, logoY, logoSize, school.name)

        cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
        centered(cs, school.name.uppercase(), BOLD, 15.5f, MARGIN + logoSize + 6f, width - logoSize - 6f, y)
        y -= 14f

        val contact = listOfNotNull(
            school.phone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
            school.email.takeIf { it.isNotBlank() },
            school.address.takeIf { it.isNotBlank() }
        ).joinToString("   ·   ")
        if (contact.isNotBlank()) {
            cs.setNonStrokingColor(INK[0], INK[1], INK[2])
            centered(cs, contact, BODY, 7.5f, MARGIN, width, y)
            y -= 10f
        }
        if (school.motto.isNotBlank()) {
            centered(cs, school.motto, ITALIC, 7.5f, MARGIN, width, y)
            y -= 10f
        }

        // term box, top right
        val boxW = 134f
        val boxX = A4.width - MARGIN - boxW
        val boxY = (A4.height - MARGIN) - 40f
        cs.setStrokingColor(LINE[0], LINE[1], LINE[2])
        cs.setLineWidth(0.9f)
        cs.addRect(boxX, boxY, boxW, 32f); cs.stroke()
        cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
        centered(cs, "TERM ${termPair?.second?.number ?: "-"} SUMMARY REPORT", BOLD, 7.4f, boxX, boxW, boxY + 20f)
        centered(cs, termPair?.first?.year.orEmpty(), BODY, 7.4f, boxX, boxW, boxY + 9f)

        // student information
        y -= 5f
        cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
        cs.addRect(MARGIN, y - 14f, width, 14f); cs.fill()
        cs.setNonStrokingColor(1f, 1f, 1f)
        text(cs, "STUDENT INFORMATION", BOLD, 7.4f, MARGIN + 5f, y - 10f)
        y -= 14f

        val name = listOfNotNull(student?.firstName, student?.lastName).joinToString(" ").ifBlank { "-" }
        val rows = listOf(
            "STUDENT NAME" to name.uppercase(),
            "ADMISSION NO." to (student?.admissionNo ?: "-"),
            "CLASS" to (cls?.name ?: "-"),
            "STREAM" to (cls?.stream?.ifBlank { "-" } ?: "-"),
            "RESULT" to resultLabel(result)
        )
        val colW = width / 2f
        rows.chunked(2).forEach { pair ->
            pair.forEachIndexed { idx, (k, v) ->
                val colX = MARGIN + idx * colW
                cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
                text(cs, "$k :", BOLD, 8f, colX + 3f, y - 10f)
                cs.setNonStrokingColor(INK[0], INK[1], INK[2])
                val kw = BOLD.getStringWidth(sanitize("$k :")) / 1000f * 8f
                text(cs, clip(v, BOLD, 8f, colW - kw - 8f), BOLD, 8f, colX + 3f + kw + 3f, y - 10f)
            }
            y -= 12.5f
        }
        return y - 3f
    }

    // ── marks table ──────────────────────────────────────────────────────────

    private fun drawTable(
        cs: PDPageContentStream,
        data: SchoolData,
        result: TermResult,
        rows: List<Pair<Subject, SubjectResult>>,
        top: Float,
        rowH: Float,
        classId: String?,
        firstChunk: Boolean
    ): Float {
        val width = usableWidth()
        var y = top
        val headH = 20f

        cs.setNonStrokingColor(SOFT[0], SOFT[1], SOFT[2])
        cs.addRect(MARGIN, y - headH, width, headH); cs.fill()
        var cx = MARGIN
        HEADERS.forEachIndexed { i, h ->
            cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
            val lines = h.split("\n")
            if (lines.size == 1) {
                centered(cs, h, BOLD, 6.3f, cx, COLS[i], y - 12.5f)
            } else {
                centered(cs, lines[0], BOLD, 6.3f, cx, COLS[i], y - 8.5f)
                centered(cs, lines[1], BOLD, 6.3f, cx, COLS[i], y - 15.5f)
            }
            cx += COLS[i]
        }
        y -= headH

        val fontSize = (rowH * 0.5f).coerceIn(6f, 7.6f)
        cs.setStrokingColor(LINE[0], LINE[1], LINE[2])
        cs.setLineWidth(0.5f)

        rows.forEachIndexed { rowIndex, (subj, r) ->
            if (rowIndex % 2 == 1) {
                cs.setNonStrokingColor(0.976f, 0.988f, 1f)
                cs.addRect(MARGIN, y - rowH, width, rowH); cs.fill()
            }
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
                initialsFor(data, subj, classId)
            )
            var x = MARGIN
            cells.forEachIndexed { i, value ->
                cs.setStrokingColor(LINE[0], LINE[1], LINE[2])
                cs.addRect(x, y - rowH, COLS[i], rowH); cs.stroke()
                val font = if (i == 0 || i == 6 || i == 7) BOLD else BODY
                val size = if (i == 8) (fontSize - 0.8f).coerceAtLeast(5.2f) else fontSize
                val maxW = COLS[i] - 5f
                val shown = if (i == 8) wrapToWidth(value, font, size, maxW).firstOrNull() ?: ""
                            else clip(value, font, size, maxW)
                cs.setNonStrokingColor(INK[0], INK[1], INK[2])
                val baseline = y - rowH / 2f - size * 0.34f
                text(cs, shown, font, size, x + 2.5f, baseline)
                x += COLS[i]
            }
            y -= rowH
        }
        return y
    }

    // ── totals, comments, signatures ─────────────────────────────────────────

    private fun drawSummary(
        cs: PDPageContentStream, data: SchoolData, result: TermResult, classSize: Int, gapBefore: Float = 0f
    ): Float {
        val width = usableWidth()
        val summaryY = SUMMARY_Y
        // average row inside the table, exactly like the printed standard card
        cs.setNonStrokingColor(SOFT[0], SOFT[1], SOFT[2])
        cs.addRect(MARGIN, summaryY - 2f, width, 14f); cs.fill()
        cs.setStrokingColor(LINE[0], LINE[1], LINE[2])
        cs.addRect(MARGIN, summaryY - 2f, width, 14f); cs.stroke()
        cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
        text(cs, "AVERAGE SCORE", BOLD, 7.2f, MARGIN + 4f, summaryY + 2.4f)

        val avgX = MARGIN + COLS.take(6).sum()
        text(cs, "${fmt(result.averagePercent)}%", BOLD, 7.6f, avgX + 4f, summaryY + 2.4f)

        val bits = buildList {
            if (result.classPosition > 0) add("Position ${result.classPosition} of $classSize")
            result.aggregate?.let { add("Aggregate $it") }
            result.division?.let { add("Division $it") }
            result.uceResult?.let { add("Result $it") }
            result.uacePoints?.let { add("Points $it") }
        }
        if (bits.isNotEmpty()) {
            text(cs, bits.joinToString("   ·   "), BOLD, 7.2f, avgX + 46f, summaryY + 2.4f)
        }
        return summaryY
    }

    private fun drawFooterBlocks(
        cs: PDPageContentStream,
        data: SchoolData,
        result: TermResult,
        classSize: Int,
        school: com.derycode.srs.core.model.School,
        termPair: Pair<com.derycode.srs.core.model.AcademicYear, com.derycode.srs.core.model.Term>?
    ) {
        val width = usableWidth()
        val halfW = (width - 8f) / 2f
        val auto = autoRemark(result, classSize)
        val classTeacherName = data.teachers.firstOrNull { t ->
            data.classes.any { c -> c.classTeacherId == t.id && data.enrollments.any { e -> e.studentId == result.studentId && e.classId == c.id } }
        }?.name.orEmpty()

        commentBox(
            cs, MARGIN, BOX_BOTTOM, halfW, BOX_H,
            "CLASS TEACHER'S COMMENT${if (classTeacherName.isBlank()) "" else " — $classTeacherName"}",
            ReportEngine.classCommentFor(data, result.studentId, result.termId).ifBlank { auto }
        )
        commentBox(
            cs, MARGIN + halfW + 8f, BOX_BOTTOM, halfW, BOX_H, "HEAD TEACHER'S COMMENT",
            ReportEngine.headCommentFor(data, result.studentId, result.termId).ifBlank { auto }
        )

        // three signature lines, evenly spread
        val thirdW = (width - 24f) / 3f
        cs.setStrokingColor(INK[0], INK[1], INK[2])
        cs.setLineWidth(0.6f)
        listOf("CLASS TEACHER", "HEAD TEACHER", "SCHOOL STAMP").forEachIndexed { i, label ->
            val sx = MARGIN + i * (thirdW + 12f)
            cs.moveTo(sx, SIG_LINE_Y); cs.lineTo(sx + thirdW, SIG_LINE_Y); cs.stroke()
            centered(cs, label, BOLD, 6.4f, sx, thirdW, SIG_LABEL_Y)
        }
    }

    private fun drawFooterLine(
        cs: PDPageContentStream,
        school: com.derycode.srs.core.model.School,
        termPair: Pair<com.derycode.srs.core.model.AcademicYear, com.derycode.srs.core.model.Term>?
    ) {
        centered(
            cs,
            "DeryCode School Report Maker   ·   ${school.name}   ·   Term ${termPair?.second?.number ?: "-"} ${termPair?.first?.year.orEmpty()}",
            BODY, 6f, MARGIN, usableWidth(), FOOTER_Y
        )
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

    private fun autoRemark(r: TermResult, classSize: Int): String = buildString {
        append("Average ${fmt(r.averagePercent)}%")
        if (r.classPosition > 0) append(" · position ${r.classPosition} of $classSize")
        r.division?.let { append(" · $it") }
        r.aggregate?.let { append(" · aggregate $it") }
    }

    private fun commentBox(
        cs: PDPageContentStream, x: Float, y: Float, w: Float, h: Float, label: String, body: String
    ) {
        cs.setStrokingColor(LINE[0], LINE[1], LINE[2])
        cs.setLineWidth(0.9f)
        cs.addRect(x, y, w, h); cs.stroke()
        cs.setNonStrokingColor(SOFT[0], SOFT[1], SOFT[2])
        cs.addRect(x, y + h - 12f, w, 12f); cs.fill()
        cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2])
        text(cs, clip(label, BOLD, 6.4f, w - 8f), BOLD, 6.4f, x + 4f, y + h - 8.6f)
        cs.setNonStrokingColor(INK[0], INK[1], INK[2])
        wrapToWidth(body, BODY, 7f, w - 8f).take(3).forEachIndexed { i, line ->
            text(cs, line, BODY, 7f, x + 4f, y + h - 23f - i * 9.2f)
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

    /** Cut to the column with an ellipsis so nothing bleeds into the next cell. */
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

    /** WinAnsi cannot encode every character a school may type; never throw mid-print. */
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
