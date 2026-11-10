package com.derycode.srs.core

import com.derycode.srs.core.model.Enrollment
import com.derycode.srs.core.model.Level
import com.derycode.srs.core.model.Roster
import com.derycode.srs.core.model.SchoolCategory
import com.derycode.srs.core.model.SchoolClass
import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.model.SchoolScope
import com.derycode.srs.core.model.Student
import com.derycode.srs.core.model.StudentStatus
import com.derycode.srs.core.model.TeacherAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two things teachers kept reporting: no classes on some phones, and classes
 * that open onto an empty learner list on others.
 */
class RosterTest {

    private val secondary = SchoolScope(SchoolCategory.SECONDARY)

    private fun cls(id: String, name: String, stream: String = "", level: Level = Level.O_LEVEL, teacher: String? = null) =
        SchoolClass(id = id, name = name, level = level, stream = stream, classTeacherId = teacher)

    private fun student(id: String, admissionNo: String, last: String = "Atuhaire") =
        Student(id = id, admissionNo = admissionNo, firstName = "Esther", lastName = last)

    private fun enroll(id: String, studentId: String, classId: String, year: String = "y1") =
        Enrollment(id = id, studentId = studentId, academicYearId = year, classId = classId)

    @Test fun `assignments decide the class list`() {
        val data = SchoolData(
            classes = listOf(cls("c1", "Senior 1"), cls("c2", "Senior 2"), cls("c3", "Senior 3")),
            assignments = listOf(TeacherAssignment(id = "a1", teacherId = "t1", classId = "c2", subjectId = "s1"))
        )
        val load = Roster.classesFor(data, "t1", secondary)
        assertEquals(Roster.Source.ASSIGNMENT, load.source)
        assertTrue(load.matched)
        assertEquals(listOf("Senior 2"), load.classes.map { it.name })
    }

    @Test fun `a class teacher with no subject rows still sees their class`() {
        val data = SchoolData(
            classes = listOf(cls("c1", "Senior 1", teacher = "t1"), cls("c2", "Senior 2")),
        )
        val load = Roster.classesFor(data, "t1", secondary)
        assertEquals(Roster.Source.CLASS_TEACHER, load.source)
        assertEquals(listOf("Senior 1"), load.classes.map { it.name })
    }

    @Test fun `an unmatched teacher sees the school instead of an empty screen`() {
        val data = SchoolData(
            classes = listOf(cls("c1", "Senior 1"), cls("c2", "Senior 2", level = Level.A_LEVEL))
        )
        val load = Roster.classesFor(data, "ghost", secondary)
        assertEquals(Roster.Source.ALL_CLASSES, load.source)
        assertFalse(load.matched)
        assertEquals(2, load.classes.size)
    }

    @Test fun `a primary school never shows a secondary class`() {
        val data = SchoolData(
            classes = listOf(cls("c1", "Primary 7", level = Level.PRIMARY), cls("c2", "Senior 1"))
        )
        val load = Roster.classesFor(data, null, SchoolScope(SchoolCategory.PRIMARY))
        assertEquals(listOf("Primary 7"), load.classes.map { it.name })
    }

    @Test fun `students come from the current enrolments`() {
        val data = SchoolData(
            classes = listOf(cls("c1", "Senior 1")),
            students = listOf(student("s1", "101"), student("s2", "12"), student("s3", "100")),
            enrollments = listOf(enroll("e1", "s1", "c1"), enroll("e2", "s2", "c1"), enroll("e3", "s3", "c1"))
        )
        val roster = Roster.studentsFor(data, "c1")
        // 12 sorts before 100, and 100 before 101 — not string order.
        assertEquals(listOf("12", "100", "101"), roster.map { it.admissionNo })
    }

    @Test fun `after a rollover the same class still holds its learners`() {
        // Last year's class id keeps the enrolments; this year's class has none.
        val data = SchoolData(
            classes = listOf(
                cls("old-s1", "Senior 1", stream = "West"),
                cls("new-s1", "Senior 1", stream = "West")
            ),
            students = listOf(student("s1", "101"), student("s2", "102")),
            enrollments = listOf(enroll("e1", "s1", "old-s1", "y1"), enroll("e2", "s2", "old-s1", "y1"))
        )
        val roster = Roster.studentsFor(data, "new-s1")
        assertEquals(listOf("101", "102"), roster.map { it.admissionNo })
    }

    @Test fun `a different stream is never used as a stand-in`() {
        val data = SchoolData(
            classes = listOf(cls("old", "Senior 1", stream = "East"), cls("new", "Senior 1", stream = "West")),
            students = listOf(student("s1", "101")),
            enrollments = listOf(enroll("e1", "s1", "old"))
        )
        assertTrue(Roster.studentsFor(data, "new").isEmpty())
    }

    @Test fun `learners who left stay off the register`() {
        val data = SchoolData(
            classes = listOf(cls("c1", "Senior 1")),
            students = listOf(
                student("s1", "101"),
                student("s2", "102").copy(status = StudentStatus.WITHDRAWN)
            ),
            enrollments = listOf(enroll("e1", "s1", "c1"), enroll("e2", "s2", "c1"))
        )
        assertEquals(listOf("101"), Roster.studentsFor(data, "c1").map { it.admissionNo })
    }

    @Test fun `an unknown class is empty, not the whole school`() {
        val data = SchoolData(
            classes = listOf(cls("c1", "Senior 1")),
            students = listOf(student("s1", "101")),
            enrollments = listOf(enroll("e1", "s1", "c1"))
        )
        assertTrue(Roster.studentsFor(data, "does-not-exist").isEmpty())
    }
}
