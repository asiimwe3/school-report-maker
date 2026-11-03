// ─────────────────────────────────────────────────────────────────────────────
// Paste the school's staff list once and every teacher appears with their
// phone number, subjects and classes already assigned.
//
// Handles the format heads of department actually type and WhatsApp around:
//
//   * Mwesige Francis — English (S1, S2, S3) — 0777109213
//   * Karungi Lucy — Agriculture (S1, S2, S3), Geography (S2) — 0777876671
//   * Isingoma John — Crossed out on the original list        (skipped)
//
// Names are matched against staff already on the console so a re-paste updates
// phones and assignments instead of creating a second copy of the same person.
// ─────────────────────────────────────────────────────────────────────────────

package com.derycode.srs.admin

import com.derycode.srs.core.model.*

object StaffImport {

    data class Entry(
        val raw: String,
        val name: String,
        val phone: String,
        val subjects: List<Pair<String, List<String>>>   // subject name → class tokens
    )

    data class Plan(
        val entries: List<Entry>,
        val skipped: List<Pair<String, String>>          // raw line → why not imported
    )

    // ── parsing ──────────────────────────────────────────────────────────────

    private val SUBJECT_GROUP = Regex("""([A-Za-z][A-Za-z&/.\- ]*?)\s*\(([^)]*)\)""")
    private val PHONE = Regex("""(\+?\d[\d\s\-]{6,}\d)""")
    private val CLASS_TOKEN = Regex("""(?i)\bS\.?\s?([1-6])\b""")

    fun parse(text: String): Plan {
        val entries = mutableListOf<Entry>()
        val skipped = mutableListOf<Pair<String, String>>()

        text.lines()
            .map { it.trim().removePrefix("*").removePrefix("•").removePrefix("-").trim() }
            .filter { it.isNotBlank() }
            .filterNot { line ->
                line.startsWith("#") ||
                    line.lowercase().startsWith("key:") ||
                    (line.lowercase().contains(" = ") && line.lowercase().contains("senior"))
            }
            .forEach { line ->
                when {
                    line.lowercase().contains("crossed out") ||
                        line.startsWith("~~") || line.endsWith("~~") ->
                        skipped += line to "crossed out on the list"

                    else -> {
                        val parts = line.split("—", "–", "|").map { it.trim() }.filter { it.isNotEmpty() }
                        val dashParts = if (parts.size == 1) line.split(Regex("""\s+-\s+""")).map { it.trim() }.filter { it.isNotEmpty() } else parts
                        if (dashParts.size < 2) {
                            skipped += line to "no subjects or phone found"
                        } else {
                            val name = dashParts.first().trim()
                            val phone = dashParts.drop(1).joinToString(" ")
                                .let { PHONE.find(it)?.value ?: "" }
                            val subjectText = dashParts.drop(1)
                                .filterNot { it == phone }
                                .joinToString(",")
                            val subjects = parseSubjects(subjectText)
                            when {
                                name.isBlank() -> skipped += line to "no name"
                                subjects.isEmpty() -> skipped += line to "no subjects recognised"
                                else -> entries += Entry(line, titleCaseName(name), normalisePhone(phone), subjects)
                            }
                        }
                    }
                }
            }
        return Plan(entries, skipped)
    }

    /** "English (S1, S2, S3), Geography (S2)" → [English → [1,2,3], Geography → [2]] */
    internal fun parseSubjects(text: String): List<Pair<String, List<String>>> {
        val out = mutableListOf<Pair<String, List<String>>>()
        SUBJECT_GROUP.findAll(text).forEach { m ->
            val subject = m.groupValues[1].trim().trim(',', ';')
            if (subject.isBlank()) return@forEach
            val classes = CLASS_TOKEN.findAll(m.groupValues[2]).map { it.groupValues[1] }.toList()
            out += subject to classes
        }
        return out
    }

    /** "MWESIGE FRANCIS" → "Mwesige Francis", "Sande Joseph" stays as typed. */
    internal fun titleCaseName(raw: String): String =
        raw.split(" ").filter { it.isNotBlank() }
            .joinToString(" ") { w ->
                if (w.length > 1 && w == w.uppercase()) w.lowercase().replaceFirstChar { it.uppercase() } else w
            }

    internal fun normalisePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 12 && digits.startsWith("256") -> "0" + digits.drop(3)
            digits.length == 13 && digits.startsWith("256") -> "0" + digits.drop(3)
            digits.length == 9 -> "0" + digits
            else -> digits
        }
    }

    // ── name matching ────────────────────────────────────────────────────────

    private fun letters(s: String) = s.lowercase().filter { it.isLetter() }

    /** Typo tolerance: Geofrey/Godfrey and Agnes/Agness are the same person. */
    private fun closeEnough(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 4 || b.length < 4) return false
        if (a.startsWith(b.take(4)) || b.startsWith(a.take(4))) return true
        return editDistance(a, b) <= 2
    }

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val swap = prev; prev = cur; cur = swap
        }
        return prev[b.length]
    }

    /** Finds the same person spelled "Geofrey"/"Godfrey" or "Agnes"/"AGNESS". */
    internal fun matchTeacher(teachers: List<Teacher>, name: String): Teacher? {
        val want = name.split(" ").filter { it.isNotBlank() }.map(::letters)
        if (want.isEmpty()) return null
        val surname = want.first()
        val other = want.drop(1)
        return teachers.firstOrNull { t ->
            val have = t.name.split(" ").filter { it.isNotBlank() }.map(::letters)
            if (have.isEmpty()) return@firstOrNull false
            val haveSurname = have.first()
            val haveOther = have.drop(1)
            val sameSurname = closeEnough(surname, haveSurname)
            val sameOther = other.isEmpty() || haveOther.isEmpty() ||
                other.zip(haveOther).all { (x, y) -> closeEnough(x, y) }
            sameSurname && sameOther
        }
    }

    // ── applying ─────────────────────────────────────────────────────────────

    data class Result(
        val data: SchoolData,
        val created: List<String>,
        val updated: List<String>,
        val warnings: List<String>
    )

    fun apply(d: SchoolData, plan: Plan, nextId: () -> String): Result {
        var data = d
        val created = mutableListOf<String>()
        val updated = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        plan.entries.forEach { e ->
            val existing = matchTeacher(data.teachers, e.name)
            val teacher = existing ?: Teacher(id = nextId(), name = e.name).also {
                data = data.copy(teachers = data.teachers + it)
                created += it.name
            }
            if (existing != null) {
                if (e.phone.isNotBlank() && existing.phone != e.phone) {
                    data = data.copy(teachers = data.teachers.map { if (it.id == existing.id) it.copy(phone = e.phone) else it })
                }
                updated += existing.name
            } else if (e.phone.isNotBlank()) {
                data = data.copy(teachers = data.teachers.map { if (it.id == teacher.id) it.copy(phone = e.phone) else it })
            }

            e.subjects.forEach { (subjectName, classTokens) ->
                val classes = classTokens.mapNotNull { token ->
                    val cls = resolveClass(data, token)
                    if (cls == null) warnings += "${e.name}: no class for S$token"
                    cls
                }.distinctBy { it.id }
                if (classes.isEmpty()) {
                    warnings += "${e.name}: ${subjectName} has no class, subject skipped"
                } else {
                    classes.forEach { cls ->
                        val subject = TimetableBulk.resolveSubject(data, cls, subjectName)
                        if (subject == null) {
                            warnings += "${e.name}: subject \"$subjectName\" is not in the subject list"
                        } else {
                            val already = data.assignments.any {
                                it.teacherId == teacher.id && it.subjectId == subject.id && it.classId == cls.id
                            }
                            if (!already) {
                                data = data.copy(assignments = data.assignments +
                                    TeacherAssignment(id = nextId(), teacherId = teacher.id, subjectId = subject.id, classId = cls.id))
                            }
                        }
                    }
                }
            }
        }
        return Result(data, created.distinct(), updated.distinct() - created.toSet(), warnings.distinct())
    }

    /** "S1" → the Senior 1 class (first stream if the school splits streams). */
    internal fun resolveClass(d: SchoolData, token: String): SchoolClass? {
        val n = token.filter { it.isDigit() }.ifBlank { return null }
        val named = listOf("Senior $n", "S.$n", "S$n", "Senior$n", "P$n", "Primary $n")
        return d.classes.firstOrNull { c ->
            named.any { it.equals(c.name.trim(), true) } ||
                (c.name.filter { ch -> ch.isDigit() } == n && c.name.lowercase().let {
                    it.contains("senior") || it.contains("primary") || it.startsWith("s") || it.startsWith("p")
                })
        }
    }
}
