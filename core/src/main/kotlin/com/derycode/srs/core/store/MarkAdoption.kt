package com.derycode.srs.core.store

import com.derycode.srs.core.entry.MarkLedger
import com.derycode.srs.core.model.AssessmentComponent
import com.derycode.srs.core.model.Mark
import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.model.Student
import com.derycode.srs.core.model.Subject

/**
 * "Marks a teacher sends must land on the marks grid."
 *
 * A phone sends marks for the learner ids, subject ids, assessment ids and term id
 * as they stood on that phone. The console owns those records and rebuilds them — a
 * rollover creates a new academic year with new term ids, a fresh setup rebuilds
 * classes, students and subjects — so an incoming row can arrive pointing at ids the
 * console no longer has. It imported cleanly, sat in the file, and never appeared in
 * the grid the head teacher was looking at.
 *
 * [adopt] pins each incoming mark onto the console's own records by natural identity
 * (admission number, subject code, assessment code, an existing term or the current
 * one) and reports what it had to change. A learner the school does not have is
 * dropped: never guessed onto somebody else.
 */
object MarkAdoption {

    data class Result(
        val marks: List<Mark>,
        /** Rows re-pointed at the console's own ids. */
        val remapped: Int = 0,
        /** Rows for learners this school does not have. */
        val dropped: Int = 0
    )

    fun adopt(
        data: SchoolData,
        incoming: List<Mark>,
        incomingStudents: List<Student> = emptyList(),
        incomingSubjects: List<Subject> = emptyList(),
        incomingComponents: List<AssessmentComponent> = emptyList(),
        /** True only when the bundle comes from this same school. */
        sameSchool: Boolean = false
    ): Result {
        if (incoming.isEmpty()) return Result(emptyList())

        val studentsById = data.students.associateBy { it.id }
        val studentsByAdmission = data.students
            .filter { it.admissionNo.isNotBlank() }
            .associateBy { normal(it.admissionNo) }

        val phoneStudents = incomingStudents.associateBy { it.id }
        val phoneSubjects = incomingSubjects.associateBy { it.id }
        val phoneComponents = incomingComponents.associateBy { it.id }

        val subjectsById = data.subjects.associateBy { it.id }
        val subjectsByCode = data.subjects.filter { it.code.isNotBlank() }.groupBy { normal(it.code) }
        val componentsById = data.components.associateBy { it.id }
        val componentsByCode = data.components.filter { it.code.isNotBlank() }.groupBy { normal(it.code) }

        val termIds = data.academicYears.flatMap { y -> y.terms.map { it.id } }.toSet()
        val currentTerm = data.academicYears.firstOrNull()?.currentTermId
            ?: data.academicYears.firstOrNull()?.terms?.firstOrNull()?.id

        var remapped = 0
        var dropped = 0
        val adopted = ArrayList<Mark>(incoming.size)

        for (m in incoming) {
            // Learner: the console's id, else the same admission number, else drop.
            val student = studentsById[m.studentId]
                ?: if (sameSchool) phoneStudents[m.studentId]?.admissionNo?.let { studentsByAdmission[normal(it)] } else null
            if (student == null) { dropped++; continue }

            // Subject: keep a known id, else match by the code the phone used.
            val subjectId =
                if (subjectsById.containsKey(m.subjectId)) m.subjectId
                else if (!sameSchool) m.subjectId
                else phoneSubjects[m.subjectId]?.code?.let { code ->
                    if (code.isBlank()) null else subjectsByCode[normal(code)]?.firstOrNull()?.id
                } ?: m.subjectId

            // Assessment: same rule, by component code (A1 / A2 / A3 / BOT / EOT …).
            val componentId =
                if (componentsById.containsKey(m.componentId)) m.componentId
                else if (!sameSchool) m.componentId
                else phoneComponents[m.componentId]?.code?.let { code ->
                    componentsByCode[normal(code)]?.firstOrNull()?.id
                } ?: m.componentId

            // Term: keep it when the console has it, else put it in the term now running
            // so it shows on the grid instead of hiding in a term nobody opens.
            val termId = when {
                m.termId in termIds -> m.termId
                sameSchool -> currentTerm ?: m.termId
                else -> m.termId
            }

            if (student.id != m.studentId || subjectId != m.subjectId ||
                componentId != m.componentId || termId != m.termId
            ) remapped++

            adopted.add(
                m.copy(studentId = student.id, subjectId = subjectId, componentId = componentId, termId = termId)
            )
        }

        return Result(MarkLedger.newestPerKey(adopted), remapped, dropped)
    }

    private fun normal(s: String): String = s.trim().uppercase()
}
