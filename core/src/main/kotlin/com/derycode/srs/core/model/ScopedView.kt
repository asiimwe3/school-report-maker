package com.derycode.srs.core.model

/**
 * Narrow [raw] to the levels this school runs. COMBINED returns the data untouched.
 * Learners are kept only when enrolled in a class that is in scope (or not enrolled
 * anywhere yet), and marks/sheets/reports/timetable/assignments follow their class
 * or subject. Hidden rows stay in the repository.
 */
fun scopedView(raw: SchoolData): SchoolData {
    val scope = SchoolScope.of(raw.school)
    if (scope.category == SchoolCategory.COMBINED) return raw
    val classes = raw.classes.filter { scope.allows(it.level) }
    val classIds = classes.map { it.id }.toSet()
    val subjects = raw.subjects.filter { scope.allows(it.level) }
    val subjectIds = subjects.map { it.id }.toSet()
    val enrolledAnywhere = raw.enrollments.map { it.studentId }.toSet()
    val enrollments = raw.enrollments.filter { it.classId in classIds }
    val inScopeStudents = enrollments.map { it.studentId }.toSet()
    val students = raw.students.filter { it.id in inScopeStudents || it.id !in enrolledAnywhere }
    val studentIds = students.map { it.id }.toSet()
    return raw.copy(
        classes = classes,
        subjects = subjects,
        enrollments = enrollments,
        students = students,
        assignments = raw.assignments.filter { it.classId in classIds && (it.subjectId == null || it.subjectId in subjectIds) },
        marks = raw.marks.filter { it.studentId in studentIds && it.subjectId in subjectIds },
        timetable = raw.timetable.filter { it.classId in classIds },
    )
}

