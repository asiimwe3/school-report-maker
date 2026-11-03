// ─────────────────────────────────────────────────────────────────────────────
// A teacher leaves, a new one takes over. Moving the work by hand means
// re-typing every timetable cell and every subject/class link — this moves all
// of it in one step and switches the leaver's access off.
//
// Example: Abigaba Joseph (ICT) left and Sande Joseph took over. Point the
// handover at Sande and every ICT period, subject link and class-teacher post
// follows him, while Abigaba stays in the archive for old report cards.
// ─────────────────────────────────────────────────────────────────────────────

package com.derycode.srs.admin

import com.derycode.srs.core.model.*

object TeacherHandover {

    /** What will move, shown to the admin before they commit. */
    data class Preview(
        val subjectLinks: Int,
        val timetablePeriods: Int,
        val classTeacherOf: List<String>,
        val duties: Int
    ) {
        val isEmpty: Boolean get() = subjectLinks == 0 && timetablePeriods == 0 && classTeacherOf.isEmpty() && duties == 0
    }

    fun preview(d: SchoolData, fromId: String): Preview = Preview(
        subjectLinks = d.assignments.count { it.teacherId == fromId },
        timetablePeriods = d.timetable.count { it.teacherId == fromId },
        classTeacherOf = d.classes.filter { it.classTeacherId == fromId }.map { it.name },
        duties = d.dutyRecords.count { it.teacherId == fromId }
    )

    data class Result(val data: SchoolData, val moved: Preview)

    /**
     * Moves everything [fromId] owns to [toId]. Duplicate subject/class links are
     * collapsed instead of doubled. Historical records (marks, comments, report
     * cards, gate passes) stay with the leaver so past documents stay truthful.
     * The leaver is deactivated, never deleted.
     */
    fun apply(d: SchoolData, fromId: String, toId: String, nextId: () -> String): Result {
        require(fromId != toId) { "Pick two different teachers" }
        val before = preview(d, fromId)

        // subject + class links: move, skipping any the target already has
        val targetHas = d.assignments.filter { it.teacherId == toId }
            .map { it.subjectId to it.classId }.toSet()
        val movedLinks = mutableListOf<TeacherAssignment>()
        val keptLinks = d.assignments.filter { it.teacherId != fromId }
        d.assignments.filter { it.teacherId == fromId }.forEach { a ->
            val key = a.subjectId to a.classId
            if (key in targetHas) return@forEach
            movedLinks += a.copy(teacherId = toId)
        }

        // timetable cells
        val timetable = d.timetable.map { if (it.teacherId == fromId) it.copy(teacherId = toId) else it }

        // class-teacher posts
        val classes = d.classes.map { if (it.classTeacherId == fromId) it.copy(classTeacherId = toId) else it }

        // duty roster
        val duties = d.dutyRecords.map { if (it.teacherId == fromId) it.copy(teacherId = toId) else it }

        val teachers = d.teachers.map {
            when (it.id) {
                fromId -> it.copy(active = false)
                toId -> it.copy(active = true)
                else -> it
            }
        }

        return Result(
            data = d.copy(
                teachers = teachers,
                assignments = keptLinks + movedLinks,
                timetable = timetable,
                classes = classes,
                dutyRecords = duties
            ),
            moved = before
        )
    }
}
