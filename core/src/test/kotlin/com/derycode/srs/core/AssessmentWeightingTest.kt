package com.derycode.srs.core

import com.derycode.srs.core.grading.GradingEngine
import com.derycode.srs.core.grading.GradingSchemes
import com.derycode.srs.core.model.*
import com.derycode.srs.core.results.AssessmentWeighting
import com.derycode.srs.core.results.ResultEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O-Level final score = A2 × 20% + A3 × 80%.
 * The raw marks are never added together, and a missing mark is never a zero.
 */
class AssessmentWeightingTest {

    private val a2 = AssessmentComponent(id = "c-a2", code = "A2", name = "Assessment 2", weightPercent = 20, levels = listOf(Level.O_LEVEL))
    private val a3 = AssessmentComponent(id = "c-a3", code = "A3", name = "Assessment 3", weightPercent = 80, levels = listOf(Level.O_LEVEL))
    private val bot = AssessmentComponent(id = "c-bot", code = "BOT", name = "Beginning of Term", levels = listOf(Level.O_LEVEL))
    private val components = listOf(a2, a3, bot)

    private fun marks(a2Score: Double?, a3Score: Double?, subject: String = "math") = listOfNotNull(
        a2Score?.let { Mark("m-a2", "s1", subject, "t1", "c-a2", score = it, type = MarkType.VALUE, maxScore = 100) },
        a3Score?.let { Mark("m-a3", "s1", subject, "t1", "c-a3", score = it, type = MarkType.VALUE, maxScore = 100) }
    )

    /** The five cases in the acceptance list. */
    @Test
    fun theWeightedFinalScoreIsA2TwentyPlusA3Eighty() {
        val cases = listOf(
            Triple(70.0, 80.0, 78.0),
            Triple(100.0, 100.0, 100.0),
            Triple(0.0, 0.0, 0.0),
            Triple(50.0, 75.0, 70.0),
            Triple(80.0, 60.0, 64.0)
        )
        cases.forEach { (m2, m3, expected) ->
            val (pct, incomplete) = ResultEngine.subjectPercentage(
                marks(m2, m3), components, caWeight = 20, examWeight = 80, weighted = true
            )
            assertEquals(false, incomplete, "A2=$m2 A3=$m3 should be complete")
            assertEquals(expected, pct, 0.01, "A2=$m2 A3=$m3")
        }
    }

    /** The marks are not simply added: 70 + 80 is 150, not 78. */
    @Test
    fun rawMarksAreNotSummed() {
        val s = AssessmentWeighting.forMarks(marks(70.0, 80.0), components)
        assertEquals(78.0, s.final!!, 0.001)
        assertEquals(14.0, 70.0 * 0.20, 0.001)
        assertEquals(64.0, 80.0 * 0.80, 0.001)
    }

    /** A missing mark stays missing: no final score, no grade. */
    @Test
    fun aMissingAssessmentIsIncompleteNotZero() {
        val onlyA2 = AssessmentWeighting.forMarks(marks(70.0, null), components)
        assertNull(onlyA2.final, "A2 alone must not produce a final score")
        assertTrue(onlyA2.incomplete)
        assertEquals(listOf("A3"), onlyA2.missing)
        assertEquals(70.0, onlyA2.byCode["A2"]!!, 0.001)
        assertEquals("—", onlyA2.grade(GradingSchemes.UCE))

        val none = AssessmentWeighting.forMarks(emptyList(), components)
        assertNull(none.final)
        assertTrue(none.incomplete)

        // A genuine zero is a real score, and grades as one
        val bothZero = AssessmentWeighting.forMarks(marks(0.0, 0.0), components)
        assertEquals(0.0, bothZero.final!!, 0.001)
        assertEquals(false, bothZero.incomplete)
    }

    /** ABS / MISSING are special marks, never zero. */
    @Test
    fun absentIsIncomplete() {
        val m = marks(70.0, 80.0).map { if (it.componentId == "c-a3") it.copy(type = MarkType.ABS) else it }
        val s = AssessmentWeighting.score(m, AssessmentWeighting.rules(components))
        assertNull(s.final)
        assertTrue(s.incomplete)
    }

    /** The eight-level NCDC scale, every boundary, no gaps or overlaps. */
    @Test
    fun eightLevelScaleBoundaries() {
        val scheme = GradingScheme(
            id = "ncec-8", name = "NCDC eight-level", level = Level.O_LEVEL,
            boundaries = listOf(
                GradeBoundary(90.0, 100.0, "A*", points = 8),
                GradeBoundary(80.0, 89.99, "A", points = 7),
                GradeBoundary(70.0, 79.99, "B", points = 6),
                GradeBoundary(60.0, 69.99, "C", points = 5),
                GradeBoundary(50.0, 59.99, "D", points = 4),
                GradeBoundary(40.0, 49.99, "E", points = 3),
                GradeBoundary(30.0, 39.99, "F", points = 2),
                GradeBoundary(0.0, 29.99, "G", points = 1)
            )
        )
        val expectations = mapOf(
            100.0 to "A*", 90.0 to "A*", 89.99 to "A", 80.0 to "A", 79.99 to "B", 70.0 to "B",
            69.99 to "C", 60.0 to "C", 59.99 to "D", 50.0 to "D", 49.99 to "E", 40.0 to "E",
            39.99 to "F", 30.0 to "F", 29.99 to "G", 0.0 to "G"
        )
        expectations.forEach { (score, grade) ->
            assertEquals(grade, GradingEngine.grade(scheme, score), "score $score")
        }
    }

    /** Grade follows the weighted final score, not either raw mark. */
    @Test
    fun gradeComesFromTheWeightedScore() {
        val scheme = GradingScheme(
            id = "ncec-8", name = "NCDC eight-level", level = Level.O_LEVEL,
            boundaries = listOf(
                GradeBoundary(90.0, 100.0, "A*"), GradeBoundary(80.0, 89.99, "A"),
                GradeBoundary(70.0, 79.99, "B"), GradeBoundary(60.0, 69.99, "C"),
                GradeBoundary(50.0, 59.99, "D"), GradeBoundary(40.0, 49.99, "E"),
                GradeBoundary(30.0, 39.99, "F"), GradeBoundary(0.0, 29.99, "G")
            )
        )
        // A2 90 (would be A*) and A3 65 (C) → final 70 → B
        val s = AssessmentWeighting.forMarks(marks(90.0, 65.0), components)
        assertEquals(70.0, s.final!!, 0.001)
        assertEquals("B", s.grade(scheme))
    }

    /** Validation: 0..100 only, blank stays blank, negatives and over-100 refused. */
    @Test
    fun marksAreValidatedIndependently() {
        assertEquals(0.0, AssessmentWeighting.sanitize("0"))
        assertEquals(100.0, AssessmentWeighting.sanitize("100"))
        assertEquals(77.5, AssessmentWeighting.sanitize(" 77.5 "))
        assertNull(AssessmentWeighting.sanitize("101"))
        assertNull(AssessmentWeighting.sanitize("-1"))
        assertNull(AssessmentWeighting.sanitize("abc"))
        assertNull(AssessmentWeighting.sanitize(""))
        assertTrue(AssessmentWeighting.valid(""))
        assertTrue(!AssessmentWeighting.valid("120"))
    }

    /** A paper marked out of 20 still weighs the same. */
    @Test
    fun scaledPapersAreConvertedToPercent() {
        val m = listOf(
            Mark("m-a2", "s1", "math", "t1", "c-a2", score = 15.0, maxScore = 20),   // 75%
            Mark("m-a3", "s1", "math", "t1", "c-a3", score = 60.0, maxScore = 75)    // 80%
        )
        val s = AssessmentWeighting.forMarks(m, components)
        assertEquals(79.0, s.final!!, 0.001)   // 75×0.2 + 80×0.8
    }

    /** Schools without A2/A3 (or with the rule switched off) keep the old calculation. */
    @Test
    fun schoolsWithoutA2A3AreUnaffected() {
        val legacy = listOf(
            Mark("m-ca", "s1", "math", "t1", "c-bot", score = 80.0, maxScore = 100)
        )
        val rules = AssessmentWeighting.rules(listOf(bot))
        assertTrue(rules.isEmpty())
        val (pct, incomplete) = ResultEngine.subjectPercentage(legacy, listOf(bot), 20, 80, weighted = false)
        assertEquals(80.0, pct, 0.001)
        assertEquals(false, incomplete)

        val off = AssessmentWeighting.rules(components, enabled = false)
        assertTrue(off.isEmpty())
    }

    /** The school's own weights win, so the rule is reusable year after year. */
    @Test
    fun schoolWeightsAreHonoured() {
        val custom = listOf(
            a2.copy(weightPercent = 30),
            a3.copy(weightPercent = 70)
        )
        val s = AssessmentWeighting.forMarks(marks(70.0, 80.0), custom)
        assertEquals(77.0, s.final!!, 0.001)   // 70×0.3 + 80×0.7
    }

    /** The report card and the grid read the same numbers. */
    @Test
    fun breakdownMatchesTheGrid() {
        val bd = ResultEngine.subjectBreakdown(marks(70.0, 80.0), components, 20, 80, weighted = true)
        assertEquals(70.0, bd.formative, 0.001)   // A2
        assertEquals(80.0, bd.exam, 0.001)        // A3
        assertEquals(listOf(70.0, 80.0), bd.assessments)
        assertEquals(78.0, bd.final!!, 0.001)
        assertEquals(false, bd.incomplete)
        assertNotNull(bd.final)
    }
}
