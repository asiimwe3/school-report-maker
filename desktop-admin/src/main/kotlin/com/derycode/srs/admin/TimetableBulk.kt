package com.derycode.srs.admin

import com.derycode.srs.core.model.*

/**
 * Bulk timetable entry.
 *
 * A printed school timetable is a grid: rows are time slots, columns are classes.
 * The admin pastes those rows exactly as they read on paper and every period for
 * the day is created in one action — no more adding 40 periods by hand.
 *
 * Line format:   08:00-08:40 | MATH 8 | ENG 2 | GEO 3 | BIO 4
 *   • first cell is the time range (08:00-08:40, or 08:00 08:40, or 08:00)
 *   • following cells are the classes, in the order shown on screen
 *   • a cell is "SUBJECT TEACHER" where teacher is a number code or a name
 *   • blank, "-", "FREE", "BREAK", "LUNCH" or "=" leave that class free
 */
internal object TimetableBulk {

    private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    /**
     * Codes as they are printed on school sheets, mapped to the subject code the
     * console keeps. "S/AGRI" and "SAGRI" are how Tooro Royal College prints
     * Agriculture; "ENT&ART" is one column shared by Entrepreneurship and Art.
     */
    private val ALIASES = mapOf(
        "SAGRI" to "AGRI", "S.AGRI" to "AGRI", "AGRI" to "AGRI", "AGRIC" to "AGRI",
        "ENT&ART" to "ENT", "ENT&ART" to "ENT", "ENTA" to "ENT",
        "PE" to "PE", "PE'S" to "PE", "PHYED" to "PE",
        "HIS" to "HIST", "KIS" to "KISW", "MATH" to "MATH"
    )

    private class TeacherMap(
        val byCode: Map<String, String>,   // "8" -> teacher id
        val byName: Map<String, String>    // "musinguzi lodrick" -> teacher id
    )

    fun importDay(
        state: AppState,
        day: Int,
        text: String,
        codeText: String,
        classes: List<SchoolClass>,
        dryRun: Boolean = false
    ): String {
        if (classes.isEmpty()) return "No classes for this level yet — add classes first."
        val d = state.data

        // ── teacher codes: "8 = TR.MUSINGUZI LODRICK" (also "8:TR...", "8 TR...") ──
        val codesToCreate = LinkedHashMap<String, String>()   // code -> teacher name
        for (raw in codeText.lines()) {
            val line = raw.trim()
            if (line.isBlank()) continue
            val m = Regex("^(\\d{1,2})\\s*[=:.\\-]\\s*(.+)$").find(line)
            if (m != null) codesToCreate[m.groupValues[1]] = m.groupValues[2].trim()
            else {
                val m2 = Regex("^(\\d{1,2})\\s+(.+)$").find(line)
                if (m2 != null) codesToCreate[m2.groupValues[1]] = m2.groupValues[2].trim()
            }
        }

        // Resolve each code to an existing teacher by name, or create the teacher.
        val codeToId = LinkedHashMap<String, String>()
        val createdNames = ArrayList<String>()
        for ((code, name) in codesToCreate) {
            val existing = d.teachers.firstOrNull { samePerson(it.name, name) }
            if (existing != null) codeToId[code] = existing.id
            else {
                val id = state.repo.nextId()
                if (!dryRun) state.repo.mutate("TEACHER_ADDED", "Teacher", name) { dd ->
                    dd.copy(teachers = dd.teachers + Teacher(id = id, name = clean(name)))
                }
                codeToId[code] = id
                createdNames += name
            }
        }
        val after = if (dryRun) d else state.data
        val tmap = TeacherMap(
            byCode = codeToId,
            byName = after.teachers.associate { normalize(it.name) to it.id }
        )

        // ── parse the pasted grid ────────────────────────────────────────────
        val rows = text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
        if (rows.isEmpty()) return "Paste at least one line, e.g. 08:00-08:40 | MATH 8 | ENG 2"

        val periods = ArrayList<TimetablePeriod>()
        val warnings = ArrayList<String>()
        val unknownTeacher = ArrayList<String>()
        val unknownSubject = ArrayList<String>()

        for ((idx, line) in rows.withIndex()) {
            val cells = line.split("|", "\\t").map { it.trim() }.filter { it.isNotEmpty() }
            if (cells.size < 2) { warnings += "Line ${idx + 1}: needs a time and at least one class column"; continue }
            val (start, end) = parseTime(cells.first()) ?: run {
                warnings += "Line ${idx + 1}: '${cells.first()}' is not a time (use 08:00-08:40)"; return@run null
            } ?: continue

            val classCells = cells.drop(1)
            classCells.forEachIndexed { col, cell ->
                val cls = classes.getOrNull(col) ?: return@forEachIndexed
                val c = cell.trim()
                if (c.isBlank() || c == "-" || c == "=" || c.equals("free", true) ||
                    c.equals("break", true) || c.equals("lunch", true) || c.equals("prep", true)) return@forEachIndexed

                val (subjectToken, teacherToken) = splitCell(c)
                val subject = resolveSubject(after, cls, subjectToken)
                if (subject == null) { unknownSubject += subjectToken; return@forEachIndexed }
                val teacherId = resolveTeacher(tmap, teacherToken)
                if (teacherToken.isNotBlank() && teacherId == null) unknownTeacher += teacherToken

                periods += TimetablePeriod(
                    id = if (dryRun) "" else state.repo.nextId(),
                    day = day, start = start, end = end,
                    classId = cls.id, subjectId = subject.id, teacherId = teacherId ?: "", notes = ""
                )
            }
        }

        if (periods.isEmpty()) {
            val why = when {
                unknownSubject.isNotEmpty() -> "Subjects not found: " + unknownSubject.distinct().joinToString(", ")
                warnings.isNotEmpty() -> warnings.first()
                else -> "No usable cells in that paste."
            }
            return "Nothing to add. " + why
        }

        if (!dryRun && periods.isNotEmpty()) {
            val hadIds = periods.count { p -> d.timetable.any { it.id == p.id } }
            state.repo.mutate("TIMETABLE_BULK_IMPORTED", "Timetable", "${periods.size} periods") { dd ->
                // replace any existing periods for this day + these classes (re-import is safe)
                val keep = dd.timetable.filterNot { p -> p.day == day && periods.any { it.classId == p.classId && it.start == p.start } }
                dd.copy(timetable = keep + periods)
            }
            state.refresh()
            if (hadIds > 0) warnings += "$hadIds period(s) replaced"
        }

        val summary = buildString {
            append(if (dryRun) "PREVIEW" else "OK")
            append(" · ${periods.size} period(s) for ${DAYS[day - 1]}")
            if (createdNames.isNotEmpty()) append(" · ${createdNames.size} teacher(s) created: ").append(createdNames.joinToString(", "))
            if (unknownSubject.isNotEmpty()) append(" · subject not found: ").append(unknownSubject.distinct().joinToString(", "))
            if (unknownTeacher.isNotEmpty()) append(" · teacher not found: ").append(unknownTeacher.distinct().joinToString(", "))
            if (warnings.isNotEmpty()) append(" · ").append(warnings.joinToString("; "))
        }
        return summary
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun clean(n: String) = n.trim().removePrefix("TR.").removePrefix("Tr.").removePrefix("tr.")
        .trim().replace("\\s+".toRegex(), " ")

    private fun normalize(n: String) = clean(n).lowercase()

    private fun samePerson(a: String, b: String): Boolean {
        val x = normalize(a); val y = normalize(b)
        if (x == y) return true
        val xs = x.split(" ").filter { it.isNotBlank() }.toSet()
        val ys = y.split(" ").filter { it.isNotBlank() }.toSet()
        return xs.isNotEmpty() && ys.isNotEmpty() && (xs.containsAll(ys) || ys.containsAll(xs))
    }

    /** "08:00-08:40" | "08:00 - 08:40" | "8:00am-8:40am" | "08:00 08:40" | "08:00" */
    internal fun parseTime(cellRaw: String): Pair<String, String>? {
        val cell = cellRaw.trim()
            .replace("\\u2013", "-").replace("\\u2014", "-")
            .replace("\\s+".toRegex(), "")
            .lowercase()
        val parts = cell.split("-", "–", "—", "to").filter { it.isNotBlank() }
        val start = to24(parts.firstOrNull() ?: return null) ?: return null
        val end = parts.getOrNull(1)?.let { to24(it) } ?: addMinutes(start, 40)
        return start to end
    }

    private fun to24(t: String): String? {
        val m = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm|a\\.m|p\\.m)?$").find(t.trim()) ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: 0
        val ap = m.groupValues[3].replace(".", "")
        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0
        if (h > 23 || min > 59) return null
        return "%02d:%02d".format(h, min)
    }

    private fun addMinutes(hhmm: String, minutes: Int): String {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        val total = h * 60 + m + minutes
        return "%02d:%02d".format((total / 60) % 24, total % 60)
    }

    /** "MATH 8" -> ("MATH","8");  "MATH" -> ("MATH","");  "MATH TR.MUSINGUZI" -> ("MATH","TR.MUSINGUZI") */
    internal fun splitCell(cell: String): Pair<String, String> {
        val cleaned = cell.trim()
        // subject then trailing code number: "MATH8" or "MATH 8"
        Regex("^([A-Za-z][A-Za-z ./&]+?)\\s*[.:]?\\s*(\\d{1,2})$").find(cleaned)?.let {
            return it.groupValues[1].trim() to it.groupValues[2]
        }
        val bySpace = cleaned.split(" ").filter { it.isNotBlank() }
        if (bySpace.size >= 2) return bySpace.first() to bySpace.drop(1).joinToString(" ")
        return cleaned to ""
    }

    internal fun resolveSubject(d: SchoolData, cls: SchoolClass, token: String): Subject? {
        val raw = token.trim()
        if (raw.isEmpty()) return null
        val alias = ALIASES[raw.uppercase().replace(" ", "").replace("/", "").replace("&", "&")]
        val t = (alias ?: raw).lowercase()
        val forLevel = d.subjects.filter { it.active && (it.level == cls.level) }
        val pool = if (forLevel.isEmpty()) d.subjects.filter { it.active } else forLevel
        return pool.firstOrNull { it.code.equals(token.trim(), true) }
            ?: pool.firstOrNull { it.code.lowercase().startsWith(t) && t.length >= 3 }
            ?: pool.firstOrNull { it.name.lowercase() == t }
            ?: pool.firstOrNull { it.name.lowercase().startsWith(t) && t.length >= 3 }
            ?: pool.firstOrNull { it.name.lowercase().contains(t) && t.length >= 4 }
            ?: d.subjects.firstOrNull { it.code.equals(token.trim(), true) || it.name.lowercase() == t }
    }

    private fun resolveTeacher(m: TeacherMap, token: String): String? {
        val t = token.trim()
        if (t.isEmpty()) return null
        if (t.all { it.isDigit() }) m.byCode[t]?.let { return it }
        val n = normalize(t)
        m.byName[n]?.let { return it }
        // partial: "MUSINGUZI" matches "MUSINGUZI LODRICK"
        return m.byName.entries.firstOrNull { (name, _) ->
            name.split(" ").any { part -> part.length >= 4 && (n.contains(part) || part.contains(n)) }
        }?.value
    }
}
