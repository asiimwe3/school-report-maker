package com.derycode.srs.core.model

/**
 * Who teaches what, and who sits in a class — answered defensively.
 *
 * Two complaints from the field drove this file:
 *
 * 1. "Some teachers are not seeing classes." A phone's teacher id is a hint, not
 *    a fact: the console mints its own staff ids, a join mints another, and a
 *    fresh setup replaces them all. [StaffLink] puts the teacher back on staff.
 *    When even that cannot find them, the app must still show the school's
 *    classes instead of an empty screen — see [classesFor].
 *
 * 2. "Some teachers are not seeing students." A class can hold learners through
 *    enrolments that point at a class id from an earlier year (rollover builds
 *    brand new class ids). Looking only at the current id shows a class of zero
 *    learners while the names sit right there in the file. [studentsFor] reads
 *    the current enrolments first, then the same class in any other year, matched
 *    by level + name + stream rather than by id.
 */
object Roster {

    /** Where a teacher's class list came from — the UI says so in plain words. */
    enum class Source {
        /** Assignments name this teacher. The normal, exact answer. */
        ASSIGNMENT,
        /** No assignment rows, but this teacher is the class teacher. */
        CLASS_TEACHER,
        /** We could not match the teacher at all, so they see the whole school. */
        ALL_CLASSES;

        val matched: Boolean get() = this != ALL_CLASSES
    }

    data class Load(val classes: List<SchoolClass>, val source: Source) {
        val matched: Boolean get() = source.matched
    }

    /** Active classes this teacher should see, never empty when the school has classes. */
    fun classesFor(data: SchoolData, teacherId: String?, scope: SchoolScope): Load {
        val active = data.classes
            .filter { it.active && scope.allows(it.level) }
            .sortedWith(compareBy({ it.level.ordinal }, { it.name }, { it.stream }))

        if (teacherId.isNullOrBlank()) return Load(active, Source.ALL_CLASSES)

        val assigned = data.assignments
            .filter { it.teacherId == teacherId }
            .mapNotNull { a -> active.firstOrNull { c -> c.id == a.classId } }
            .distinctBy { it.id }
        if (assigned.isNotEmpty()) {
            return Load(assigned.sortedWith(compareBy({ it.level.ordinal }, { it.name })), Source.ASSIGNMENT)
        }

        val asClassTeacher = active.filter { it.classTeacherId == teacherId }
        if (asClassTeacher.isNotEmpty()) return Load(asClassTeacher, Source.CLASS_TEACHER)

        return Load(active, Source.ALL_CLASSES)
    }

    /**
     * Active learners in a class, newest enrolments first, then the same class in
     * an earlier year. Empty only when the school genuinely holds nobody for it.
     */
    fun studentsFor(data: SchoolData, classId: String): List<Student> {
        val byId = data.students.associateBy { it.id }
        fun active(ids: List<String>): List<Student> = ids
            .mapNotNull { byId[it] }
            .filter { it.status == StudentStatus.ACTIVE }
            .distinctBy { it.id }

        val direct = active(data.enrollments.filter { it.classId == classId }.map { it.studentId })
        if (direct.isNotEmpty()) return sorted(direct)

        val target = data.classes.firstOrNull { it.id == classId } ?: return emptyList()
        val sameClassOtherYear = data.classes
            .filter { it.id != classId && sameClass(it, target) }
            .map { it.id }
            .toSet()
        if (sameClassOtherYear.isEmpty()) return emptyList()

        val carried = active(
            data.enrollments.filter { it.classId in sameClassOtherYear }.map { it.studentId }
        )
        return sorted(carried)
    }

    /** Two class records describe the same class even when their ids differ. */
    fun sameClass(a: SchoolClass, b: SchoolClass): Boolean =
        a.level == b.level &&
            a.name.trim().equals(b.name.trim(), ignoreCase = true) &&
            a.stream.trim().equals(b.stream.trim(), ignoreCase = true)

    private fun sorted(students: List<Student>): List<Student> =
        students.sortedWith(compareBy({ admissionKey(it.admissionNo) }, { it.lastName }, { it.firstName }))

    /** "12" sorts before "100", and letters stay out of the way. */
    private fun admissionKey(admissionNo: String): String {
        val digits = admissionNo.filter { it.isDigit() }
        return if (digits.isEmpty()) admissionNo.uppercase()
        else digits.padStart(10, '0')
    }
}
