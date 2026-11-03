package com.derycode.srs.core.entry

import com.derycode.srs.core.model.*

// ─────────────────────────────────────────────────────────────────────────────
// Marks entry sheets — the template a teacher downloads, fills (in Excel, by
// hand or on paper) and brings back either as a file or as a photo.
//
// Everything here is plain Kotlin so the parsing and the name matching are
// unit-tested once and behave the same on the phone and on the console.
// ─────────────────────────────────────────────────────────────────────────────

/** One marks column on the sheet: a school assessment (CA, MID, EOT …). */
data class SheetColumn(
    val componentId: String,
    val label: String,
    val maxScore: Int = 100
)

/** One line read from a sheet — either a CSV row or an OCR line. */
data class ParsedRow(
    val nameText: String,
    val admissionNo: String = "",
    /** componentId → what was written ("", "12", "ABS", "EXEMPT"). */
    val values: Map<String, String> = emptyMap()
) {
    fun valueFor(componentId: String): String = values[componentId]?.trim().orEmpty()
}

object MarksSheet {

    /** The columns a sheet for this level carries: the school's active components. */
    fun columns(data: SchoolData, level: Level, maxScore: Int = 100): List<SheetColumn> =
        data.components
            .filter { it.active && (it.levels.isEmpty() || level in it.levels) }
            .map { SheetColumn(it.id, it.name.ifBlank { it.code }, maxScore) }

    fun termLabel(data: SchoolData, termId: String): String {
        data.academicYears.forEach { y ->
            y.terms.firstOrNull { it.id == termId }?.let { return "${y.year} Term ${it.number}" }
        }
        return ""
    }

    // ── Template out ─────────────────────────────────────────────────────────

    private fun csvCell(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v

    /**
     * The downloadable template: one row per learner, admission number and name
     * filled in, one column per assessment. The first line is a comment the
     * parser reads back so an old sheet can never be loaded into the wrong class.
     */
    fun templateCsv(
        school: School,
        klass: SchoolClass,
        subject: Subject,
        termLabel: String,
        students: List<Student>,
        columns: List<SheetColumn>
    ): String = buildString {
        appendLine("#SRS|${school.name}|${klass.name}|${subject.name}|$termLabel")
        appendLine((listOf("ADM NO", "NAME") + columns.map { "${it.label} (out of ${it.maxScore})" }).joinToString(",") { csvCell(it) })
        students.forEach { s ->
            appendLine((listOf(s.admissionNo, s.fullName) + columns.map { "" }).joinToString(",") { csvCell(it) })
        }
        appendLine()
        appendLine("# Type the score in each column. Use ABS for absent, EXEMPT for exempt.")
        appendLine("# Keep this file as CSV and give it back with Import filled template.")
    }

    /** Header of a template: which school, class, subject and term it belongs to. */
    data class TemplateMeta(val school: String, val klass: String, val subject: String, val term: String)

    fun readTemplateMeta(csv: String): TemplateMeta? =
        csv.lineSequence().firstOrNull { it.startsWith("#SRS|") }?.split('|')?.takeIf { it.size >= 5 }
            ?.let { TemplateMeta(it[1], it[2], it[3], it[4]) }

    // ── Reading a filled CSV back ────────────────────────────────────────────

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    /**
     * Reads the filled template. Columns are matched by their header label so a
     * school that re-orders columns (or adds one) still imports correctly.
     */
    fun parseCsv(text: String, columns: List<SheetColumn>): List<ParsedRow> {
        val lines = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("# Type") && !it.startsWith("# Keep") }.toList()
        if (lines.isEmpty()) return emptyList()
        val headerIdx = lines.indexOfFirst { it.uppercase().startsWith("ADM NO") || it.uppercase().startsWith("ADMISSION") }
            .let { if (it >= 0) it else 0 }
        val header = splitCsvLine(lines[headerIdx]).map { it.trim().uppercase() }
        // header column index per component, matched on the label before " ("
        val indexFor = columns.associate { col ->
            col.componentId to header.indexOfFirst { h -> h.substringBefore(" (").trim() == col.label.trim().uppercase() }
        }
        return lines.drop(headerIdx + 1).mapNotNull { line ->
            val cells = splitCsvLine(line)
            if (cells.all { it.isBlank() }) return@mapNotNull null
            val adm = cells.getOrElse(0) { "" }.trim()
            val name = cells.getOrElse(1) { "" }.trim()
            if (name.isBlank() && adm.isBlank()) return@mapNotNull null
            val values = indexFor.mapNotNull { (cid, idx) ->
                if (idx <= 1) null else cells.getOrNull(idx)?.trim()?.takeIf { it.isNotEmpty() }?.let { cid to it }
            }.toMap()
            ParsedRow(nameText = name, admissionNo = adm, values = values)
        }
    }

    // ── Reading a photo of the sheet ─────────────────────────────────────────

    private val MARK_TOKEN = Regex("^(ABS|EXEMPT|EX|AB|-|—|\\.|[0-9]{1,3}(\\.[0-9]{1,2})?)$", RegexOption.IGNORE_CASE)

    /**
     * Turns the lines of an OCR result into rows: the learner's name is what is
     * left once the serial number and the trailing marks are taken off. Values
     * land on [columns] in the order they appear, so a single-column sheet can be
     * read one assessment at a time.
     */
    fun parseScan(lines: List<String>, columns: List<SheetColumn>): List<ParsedRow> {
        val out = mutableListOf<ParsedRow>()
        lines.forEach { raw ->
            val line = raw.replace('\u00A0', ' ').trim()
            if (line.length < 3) return@forEach
            if (Regex("^[0-9]{1,2}[.)]?$").matches(line)) return@forEach      // page/serial noise
            val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            // walk from the end while tokens look like marks
            val marks = ArrayDeque<String>()
            var i = tokens.lastIndex
            while (i >= 0 && marks.size < columns.size && MARK_TOKEN.matches(tokens[i])) {
                marks.addFirst(tokens[i]); i--
            }
            if (i < 0 || marks.isEmpty()) return@forEach
            var nameTokens = tokens.subList(0, i + 1)
            // a leading plain number is the roll/serial, not part of the name
            if (nameTokens.size > 1 && nameTokens.first().matches(Regex("^[0-9]{1,3}[.)]?$"))) {
                nameTokens = nameTokens.drop(1)
            }
            val name = nameTokens.joinToString(" ").trim().trim('-', '.', ':', '|').trim()
            if (name.count { it.isLetter() } < 3) return@forEach
            val values = marks.toList().mapIndexedNotNull { idx, v ->
                if (v == "-" || v == "—" || v == ".") return@mapIndexedNotNull null
                columns.getOrNull(idx)?.let { col -> col.componentId to v }
            }.toMap()
            out += ParsedRow(nameText = name, admissionNo = "", values = values)
        }
        return out
    }
}

/**
 * Finds which learner a scanned name belongs to. Handles the ways registers
 * really get written: surnames first, initials, extra middle names, a missing
 * letter from a bad OCR pass, or the admission number on the line.
 */
object RosterMatcher {

    fun clean(s: String): String = s.uppercase()
        .replace(Regex("[^A-Z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun tokens(s: String): List<String> = clean(s).split(' ').filter { it.isNotBlank() }

    /** One written word matches another when they are equal, an initial, or a prefix. */
    private fun wordMatches(a: String, b: String): Boolean =
        a == b ||
            (a.length >= 2 && b.startsWith(a)) ||
            (b.length >= 2 && a.startsWith(b)) ||
            (a.length == 1 && b.startsWith(a)) ||
            (b.length == 1 && a.startsWith(b))

    /** 0.0 – 1.0 how strongly a written name matches a learner's name. */
    fun similarity(written: String, studentName: String): Double {
        val a = tokens(written); val b = tokens(studentName)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (clean(written) == clean(studentName)) return 1.0
        // how much of the register name the sheet line covers (surnames first, initials, extra names)
        val coverage = b.count { t -> a.any { wordMatches(it, t) } }.toDouble() / b.size
        // and how much of the line is really this learner (not somebody else's words)
        val precision = a.count { t -> b.any { wordMatches(t, it) } }.toDouble() / a.size
        val score = 0.65 * coverage + 0.35 * precision
        return if (score < 0.45) 0.0 else score
    }

    /**
     * The learner this line belongs to, or null. Admission numbers win outright;
     * otherwise the best name above [threshold].
     */
    fun best(row: ParsedRow, students: List<Student>, threshold: Double = 0.62): Student? {
        val written = listOf(row.admissionNo, row.nameText).filter { it.isNotBlank() }.joinToString(" ")
        val adm = clean(row.admissionNo.ifBlank { row.nameText })
        students.firstOrNull { s -> s.admissionNo.isNotBlank() && clean(s.admissionNo).let { a ->
            adm == a || " $a " in " $adm " || adm.contains(" $a ")
        } }?.let { return it }
        students.firstOrNull { s -> clean(s.admissionNo) == clean(row.nameText) }?.let { return it }
        val scored = students.map { it to similarity(written, it.fullName) }.sortedByDescending { it.second }
        val top = scored.firstOrNull() ?: return null
        if (top.second < threshold) return null
        // refuse to guess when two learners score almost the same
        val second = scored.getOrNull(1)
        if (second != null && second.second >= top.second - 0.02) return null
        return top.first
    }
}
