// ─────────────────────────────────────────────────────────────────────────────
// One school's information must never appear inside another school.
//
// A teacher's phone holds one school at a time, but three doors used to let a
// second school's rows in:
//   1. joining by code carried the previous school's typed marks across
//   2. the "same school?" test compared school.id, which defaults to "school"
//      for every new school, so two unrelated schools looked identical
//   3. a marks bundle was built from every mark on the phone, not from the
//      students of the current school
// This is the single place that decides identity, and the only merge rule.
// ─────────────────────────────────────────────────────────────────────────────

package com.derycode.srs.core.model

object SchoolIsolation {

    /** Every school created by the setup wizard shares this placeholder id. */
    private const val PLACEHOLDER_ID = "school"

    private fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Human identity of a school: the name a head teacher typed, plus the id when real. */
    fun identity(d: SchoolData): String {
        val name = norm(d.school.name)
        val id = d.school.id.trim()
        return if (id.isBlank() || id == PLACEHOLDER_ID) name else "$name|$id"
    }

    /**
     * Same school means: the same name, or the same real (non-placeholder) id.
     * A blank name is never "the same" — we cannot prove it, so we refuse.
     */
    fun sameSchool(a: SchoolData, b: SchoolData): Boolean {
        val na = norm(a.school.name)
        val nb = norm(b.school.name)
        if (na.isBlank() || nb.isBlank()) return false
        if (na == nb) return true
        val ia = a.school.id.trim()
        val ib = b.school.id.trim()
        return ia.isNotBlank() && ia == ib && ia != PLACEHOLDER_ID
    }

    private fun markKey(m: Mark) = "${m.studentId}|${m.subjectId}|${m.termId}|${m.componentId}"

    /** Only the marks that belong to learners actually in this school. */
    fun scopedMarks(d: SchoolData): List<Mark> {
        val ids = d.students.map { it.id }.toSet()
        return d.marks.filter { it.studentId in ids }
    }

    /**
     * Drops every row that points at a learner the school does not have:
     * marks, comments, attendance, term results, gate passes and reports.
     * Console-owned things (classes, subjects, teachers, timetable) are untouched.
     */
    fun prune(d: SchoolData): Pair<SchoolData, Int> {
        val ids = d.students.map { it.id }.toSet()
        fun keep(id: String) = id in ids
        val marks = d.marks.filter { keep(it.studentId) }
        val comments = d.comments.filter { keep(it.studentId) }
        val attendance = d.attendance.filter { keep(it.studentId) }
        val termResults = d.termResults.filter { keep(it.studentId) }
        val gatePasses = d.gatePasses.filter { keep(it.studentId) }
        val reports = d.reports.filter { keep(it.studentId) }
        val requests = d.enrollmentRequests.filter { keep(it.student.id) }
        val removed = (d.marks.size - marks.size) + (d.comments.size - comments.size) +
            (d.attendance.size - attendance.size) + (d.termResults.size - termResults.size) +
            (d.gatePasses.size - gatePasses.size) + (d.reports.size - reports.size) +
            (d.enrollmentRequests.size - requests.size)
        return d.copy(
            marks = marks, comments = comments, attendance = attendance,
            termResults = termResults, gatePasses = gatePasses, reports = reports,
            enrollmentRequests = requests
        ) to removed
    }

    /**
     * The one merge rule when a school setup arrives from the console or the cloud.
     *
     * Same school  → keep the marks this phone typed that the setup does not have
     *               (a re-pull must never wipe a teacher's afternoon of work).
     * Other school → start from the incoming school exactly as published. Not one
     *               row of the old school travels, so nothing can be mistaken for
     *               the new school's learners.
     */
    fun adopt(incoming: SchoolData, current: SchoolData): SchoolData {
        val ids = incoming.students.map { it.id }.toSet()
        if (!sameSchool(incoming, current)) return prune(incoming).first

        val published = incoming.marks.map { markKey(it) }.toSet()
        val mine = current.marks.filter { it.studentId in ids && markKey(it) !in published }
        return prune(incoming.copy(marks = incoming.marks + mine)).first
    }
}
