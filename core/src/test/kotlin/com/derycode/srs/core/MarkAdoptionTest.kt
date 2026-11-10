package com.derycode.srs.core

import com.derycode.srs.core.entry.MarkLedger
import com.derycode.srs.core.model.AcademicYear
import com.derycode.srs.core.model.AssessmentComponent
import com.derycode.srs.core.model.Enrollment
import com.derycode.srs.core.model.Level
import com.derycode.srs.core.model.Mark
import com.derycode.srs.core.model.SchoolClass
import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.model.Student
import com.derycode.srs.core.model.Subject
import com.derycode.srs.core.model.Term
import com.derycode.srs.core.store.MarkAdoption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Sent marks should go on the marks grid." A phone's ids can be older than the
 * console's, and the row then never appeared where the head teacher was looking.
 */
class MarkAdoptionTest {

    private val year = AcademicYear(id = "y-2027", year = "2027", currentTermId = "t1-2027",
        terms = listOf(Term(id = "t1-2027", yearId = "y-2027", number = 1)))
    private val cls = SchoolClass(id = "c-s3w", name = "Senior 3", stream = "West")
    private val esther = Student(id = "st-1", admissionNo = "1042", firstName = "Esther", lastName = "Atuhaire")
    private val subject = Subject(id = "sub-math", name = "Mathematics", code = "MTC")
    private val a2 = AssessmentComponent(id = "comp-a2", code = "A2", name = "A2", weightPercent = 20)
    private val a3 = AssessmentComponent(id = "comp-a3", code = "A3", name = "A3", weightPercent = 80)

    private fun console() = SchoolData(
        academicYears = listOf(year), classes = listOf(cls), students = listOf(esther),
        enrollments = listOf(Enrollment(id = "e1", studentId = "st-1", academicYearId = "y-2027", classId = "c-s3w")),
        subjects = listOf(subject), components = listOf(a2, a3)
    )

    private fun mark(student: String = "st-1", subjectId: String = "sub-math",
                     component: String = "comp-a2", term: String = "t1-2027", score: Double = 78.0) =
        Mark(id = "m-$student-$component", studentId = student, subjectId = subjectId,
            termId = term, componentId = component, score = score, updatedAt = 5_000)

    @Test fun `ids that already match land untouched`() {
        val r = MarkAdoption.adopt(console(), listOf(mark()))
        assertEquals(0, r.remapped)
        assertEquals(0, r.dropped)
        assertEquals("st-1", r.marks.single().studentId)
        assertEquals("t1-2027", r.marks.single().termId)
    }

    @Test fun `a rebuilt learner id is matched by admission number`() {
        val oldPhoneMark = mark(student = "st-old-version-of-esther")
        val r = MarkAdoption.adopt(
            console(), listOf(oldPhoneMark),
            incomingStudents = listOf(esther.copy(id = "st-old-version-of-esther")),
            sameSchool = true
        )
        assertEquals(1, r.remapped)
        assertEquals("st-1", r.marks.single().studentId)
        // and the grid lookup the console uses now finds it
        assertEquals(78.0, MarkLedger.get(r.marks, "st-1", "sub-math", "t1-2027", "comp-a2")?.score)
    }

    @Test fun `an old term id is moved into the term the console is running`() {
        val r = MarkAdoption.adopt(console(), listOf(mark(term = "t1-2026")), sameSchool = true)
        assertEquals("t1-2027", r.marks.single().termId)
        assertEquals(1, r.remapped)
    }

    @Test fun `a rebuilt assessment id is matched by code`() {
        val r = MarkAdoption.adopt(
            console(), listOf(mark(component = "old-comp")),
            incomingComponents = listOf(a2.copy(id = "old-comp", code = "a2")),
            sameSchool = true
        )
        assertEquals("comp-a2", r.marks.single().componentId)
    }

    @Test fun `a rebuilt subject id is matched by code`() {
        val r = MarkAdoption.adopt(
            console(), listOf(mark(subjectId = "old-math")),
            incomingSubjects = listOf(subject.copy(id = "old-math", code = "mtc")),
            sameSchool = true
        )
        assertEquals("sub-math", r.marks.single().subjectId)
    }

    @Test fun `marks for a learner this school does not have are dropped, never guessed`() {
        val r = MarkAdoption.adopt(console(), listOf(mark(student = "stranger"), mark()))
        assertEquals(1, r.dropped)
        assertEquals(1, r.marks.size)
        assertEquals("st-1", r.marks.single().studentId)
    }

    @Test fun `two copies of the same sent mark collapse to one`() {
        val r = MarkAdoption.adopt(console(), listOf(mark(score = 60.0), mark(score = 82.0)))
        assertEquals(1, r.marks.size)
        assertEquals(82.0, r.marks.single().score)
    }

    @Test fun `another school's bundle is never re-pointed by admission number`() {
        // Same admission number 1042 exists at both schools; ids differ.
        val r = MarkAdoption.adopt(
            console(), listOf(mark(student = "other-school-learner")),
            incomingStudents = listOf(esther.copy(id = "other-school-learner")),
            sameSchool = false
        )
        assertTrue(r.marks.isEmpty(), "a foreign learner must not land on our Esther")
        assertEquals(1, r.dropped)
    }

    @Test fun `an empty send changes nothing`() {
        val r = MarkAdoption.adopt(console(), emptyList())
        assertTrue(r.marks.isEmpty())
        assertEquals(0, r.remapped)
        assertEquals(0, r.dropped)
    }
}
