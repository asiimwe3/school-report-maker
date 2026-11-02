package com.derycode.srs.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopedViewTest {
    private fun data(category: String) = SchoolData(
        school = School(name = "Tooro Royal College", category = category),
        classes = listOf(
            SchoolClass("p1", "Primary 1", Level.PRIMARY),
            SchoolClass("p7", "Primary 7", Level.PRIMARY),
            SchoolClass("s1", "Senior 1", Level.O_LEVEL),
            SchoolClass("s5", "Senior 5", Level.A_LEVEL)
        ),
        subjects = listOf(
            Subject("sp", "English P", "ENGP", Level.PRIMARY),
            Subject("so", "Mathematics", "MATH", Level.O_LEVEL)
        ),
        students = listOf(
            Student(id = "a", admissionNo = "a1", firstName = "Pri", lastName = "L"),
            Student(id = "b", admissionNo = "b1", firstName = "Sec", lastName = "L"),
            Student(id = "c", admissionNo = "c1", firstName = "Unplaced", lastName = "L")
        ),
        enrollments = listOf(
            Enrollment(id = "e1", studentId = "a", classId = "p1", academicYearId = "y"),
            Enrollment(id = "e2", studentId = "b", classId = "s1", academicYearId = "y")
        )
    )

    @Test fun `secondary school sees no primary anywhere`() {
        val v = scopedView(data("SECONDARY"))
        assertEquals(listOf("s1", "s5"), v.classes.map { it.id })
        assertTrue(v.classes.none { it.level == Level.PRIMARY })
        assertTrue(v.subjects.none { it.level == Level.PRIMARY })
        assertEquals(listOf("b", "c"), v.students.map { it.id })
        assertEquals(listOf("s1"), v.enrollments.map { it.classId })
    }

    @Test fun `primary school sees no senior classes`() {
        val v = scopedView(data("PRIMARY"))
        assertEquals(listOf("p1", "p7"), v.classes.map { it.id })
        assertTrue(v.subjects.all { it.level == Level.PRIMARY })
    }

    @Test fun `combined school is untouched and nothing is lost`() {
        val raw = data("COMBINED")
        assertEquals(raw, scopedView(raw))
        assertEquals(4, scopedView(data("")).classes.size)
    }
}
