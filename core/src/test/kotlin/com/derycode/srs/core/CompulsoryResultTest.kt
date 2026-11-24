package com.derycode.srs.core

import com.derycode.srs.core.model.*
import com.derycode.srs.core.results.ResultEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A learner who has sat every compulsory subject must get a result, not a blank card. */
class CompulsoryResultTest {

    private fun data(marks: (String, String) -> Double?): SchoolData {
        val scheme = GradingScheme(
            id = "g1", name = "PLE", level = Level.PRIMARY,
            aggregateRule = AggregateRule.PLE_AGGREGATE, aggregateBestSubjects = 4
        )
        val subjects = listOf(
            Subject("s1", "English", compulsory = true, level = Level.PRIMARY),
            Subject("s2", "Maths", compulsory = true, level = Level.PRIMARY),
            Subject("s3", "Science", compulsory = true, level = Level.PRIMARY),
            Subject("s4", "SST", compulsory = true, level = Level.PRIMARY),
            Subject("s5", "RE", compulsory = false, level = Level.PRIMARY)
        )
        val comp = AssessmentComponent(id = "c1", code = "EOT", name = "Exam", weightPercent = 100, active = true)
        val m = mutableListOf<Mark>()
        subjects.forEach { sub ->
            marks("st1", sub.id)?.let { m += Mark(id = "m-${sub.id}", studentId = "st1", subjectId = sub.id, termId = "t1", componentId = "c1", score = it, maxScore = 100, type = MarkType.VALUE) }
        }
        return SchoolData(
            school = School(name = "Test"),
            students = listOf(Student(id = "st1", admissionNo = "001", firstName = "Learner", lastName = "One")),
            subjects = subjects,
            classes = listOf(SchoolClass("c1", "P7", Level.PRIMARY)),
            enrollments = listOf(Enrollment("e1", "st1", "ay1", "c1")),
            academicYears = listOf(AcademicYear("ay1", "2026", terms = listOf(Term("t1", "ay1", 1)), currentTermId = "t1")),
            gradingSchemes = listOf(scheme),
            components = listOf(comp),
            marks = m
        )
    }

    @Test
    fun compulsoryMarksAloneProduceADivision() {
        // only the four compulsory subjects are marked; RE is blank
        val d = data { _, sub -> if (sub == "s5") null else 70.0 }
        val r = ResultEngine.classResults(d, "c1", "t1", "g1").first()
        assertNotNull(r.division, "a learner with all compulsory marks must get a division")
        // four compulsory subjects is already the best-four basis PLE uses, so this
        // one is a complete result; the provisional flag belongs to the thinner case below
        assertEquals(false, r.provisional)
    }

    @Test
    fun fullMarksAreNotProvisional() {
        val d = data { _, _ -> 70.0 }
        val r = ResultEngine.classResults(d, "c1", "t1", "g1").first()
        assertNotNull(r.division)
        assertEquals(false, r.provisional)
    }

    @Test
    fun aLearnerWithNothingMarkedStillHasNoDivision() {
        val d = data { _, _ -> null }
        val r = ResultEngine.classResults(d, "c1", "t1", "g1").first()
        assertEquals(null, r.division, "no marks must never invent a division")
    }

    @Test
    fun thinnerCompulsoryCoverageIsFlaggedProvisional() {
        // a best-five scheme with only the four compulsory subjects marked: still a
        // result, but honestly labelled provisional
        val base = data { _, sub -> if (sub == "s5") null else 70.0 }
        val five = base.gradingSchemes.map { it.copy(aggregateBestSubjects = 5) }
        val d = base.copy(gradingSchemes = five)
        val r = ResultEngine.classResults(d, "c1", "t1", "g1").first()
        assertNotNull(r.division, "compulsory coverage must still produce a division")
        assertTrue(r.provisional, "a short best-five basis must be flagged provisional")
    }
}
