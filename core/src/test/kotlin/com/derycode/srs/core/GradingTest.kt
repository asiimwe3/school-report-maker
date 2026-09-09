package com.derycode.srs.core

import com.derycode.srs.core.grading.GradingEngine
import com.derycode.srs.core.grading.GradingSchemes
import com.derycode.srs.core.model.AssessmentComponent
import com.derycode.srs.core.model.Level
import com.derycode.srs.core.model.Mark
import com.derycode.srs.core.model.MarkType
import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.results.ResultEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deterministic calculation fixtures (Sections 53–54):
 * normal student, boundary grades, missing marks, weighted UCE, PLE aggregate,
 * ranking ties. Never rely on visual inspection to validate calculations.
 */
class GradingTest {

    // ── PLE boundaries driven by scheme data ──────────────────────────────────
    @Test fun pleGrades() {
        val s = GradingSchemes.PLE
        assertEquals("1", GradingEngine.grade(s, 95.0))   // distinction 1
        assertEquals("2", GradingEngine.grade(s, 85.0))
        assertEquals("5", GradingEngine.grade(s, 55.0))   // credit
        assertEquals("7", GradingEngine.grade(s, 35.0))   // pass
        assertEquals("9", GradingEngine.grade(s, 10.0))   // fail
    }

    @Test fun pleBoundaryEdges() {
        val s = GradingSchemes.PLE
        assertEquals("1", GradingEngine.grade(s, 90.0))   // exact lower boundary of 1
        assertEquals("2", GradingEngine.grade(s, 89.9))
        assertEquals("6", GradingEngine.grade(s, 49.999))
        assertEquals("6", GradingEngine.grade(s, 40.0))   // exact boundary of 6
    }

    @Test fun pleAggregateBest4() {
        val s = GradingSchemes.PLE
        // 90→1, 85→2, 75→3, 65→4, 55→5, 45→6 : best 4 = 1+2+3+4 = 10 → Division 2
        val points = listOf(90.0, 85.0, 75.0, 65.0, 55.0, 45.0)
            .map { GradingEngine.points(s, it) }
        val aggregate = points.sorted().take(4).sum()
        assertEquals(10, aggregate)
        assertEquals("Division 2", GradingEngine.divisionForAggregate(s, aggregate))
    }

    @Test fun pleDivisionOne() {
        assertEquals("Division 1", GradingEngine.divisionForAggregate(GradingSchemes.PLE, 4))
        assertEquals("Division U", GradingEngine.divisionForAggregate(GradingSchemes.PLE, 30))
    }

    // ── UCE competency-based (A–E, 20/80) ─────────────────────────────────────
    @Test fun uceGrades() {
        val s = GradingSchemes.UCE
        assertEquals("A", GradingEngine.grade(s, 80.0))   // exact boundary of A
        assertEquals("B", GradingEngine.grade(s, 79.9))
        assertEquals("D", GradingEngine.grade(s, 50.0))   // minimum competency
        assertEquals("E", GradingEngine.grade(s, 49.9))
        assertEquals("E", GradingEngine.grade(s, 0.0))
    }

    @Test fun uceWeighted20_80() {
        val components = listOf(
            AssessmentComponent(id = "ca", code = "CA", name = "CA", weightPercent = 20, levels = listOf(Level.O_LEVEL)),
            AssessmentComponent(id = "eot", code = "EOT", name = "EOT", weightPercent = 80, levels = listOf(Level.O_LEVEL))
        )
        val marks = listOf(
            Mark("m1", "s1", "math", "t1", "ca", score = 15.0, type = MarkType.VALUE, maxScore = 20),
            Mark("m2", "s1", "math", "t1", "eot", score = 60.0, type = MarkType.VALUE, maxScore = 75)
        )
        // CA = 75% → 20% of 75 = 15 ; EOT = 80% → 80% of 80 = 64 ; total = 79 → B
        val (pct, incomplete) = ResultEngine.subjectPercentage(
            marks, components, caWeight = 20, examWeight = 80, weighted = true
        )
        assertEquals(false, incomplete)
        assertEquals(79.0, pct, 0.01)
        assertEquals("B", GradingEngine.grade(GradingSchemes.UCE, pct))
    }

    @Test fun uceResultIndicators() {
        assertEquals("Result 1", GradingEngine.uceResultIndicator(allE = false, missingCompulsory = false))
        assertEquals("Result 2", GradingEngine.uceResultIndicator(allE = false, missingCompulsory = true))
        assertEquals("Result 3", GradingEngine.uceResultIndicator(allE = true, missingCompulsory = false))
    }

    // ── UACE points ───────────────────────────────────────────────────────────
    @Test fun uacePoints() {
        val s = GradingSchemes.UACE
        assertEquals(6, GradingEngine.points(s, 85.0))   // A
        assertEquals(5, GradingEngine.points(s, 75.0))   // B
        assertEquals(2, GradingEngine.points(s, 45.0))   // E
        assertEquals(0, GradingEngine.points(s, 10.0))   // F
        // 3 principals A B C = 6+5+4 = 15
        val principals = listOf(85.0, 75.0, 65.0).map { GradingEngine.points(s, it) }
        assertEquals(15, principals.sum())
    }

    // ── Special marks never count as zero (Section 16) ───────────────────────
    @Test fun absentNeverCountsAsZero() {
        val components = listOf(AssessmentComponent(id = "eot", code = "EOT", name = "EOT"))
        val marks = listOf(
            Mark("m1", "s1", "math", "t1", "eot", score = 0.0, type = MarkType.ABS, maxScore = 100),
            Mark("m2", "s1", "math", "t1", "ca", score = 50.0, type = MarkType.VALUE, maxScore = 100)
        )
        val (pct, incomplete) = ResultEngine.subjectPercentage(marks, components, 0, 100, weighted = false)
        assertEquals(true, incomplete)                 // flagged incomplete
        assertEquals(50.0, pct, 0.01)                   // only the VALUE mark averaged
    }

    // ── Ranking ties: 1, 2, 2, 4 (Section 21) ────────────────────────────────
    @Test fun rankingHandlesTies() {
        val results = listOf(
            com.derycode.srs.core.model.TermResult(studentId = "a", termId = "t", averagePercent = 90.0, schemeId = "x"),
            com.derycode.srs.core.model.TermResult(studentId = "b", termId = "t", averagePercent = 80.0, schemeId = "x"),
            com.derycode.srs.core.model.TermResult(studentId = "c", termId = "t", averagePercent = 80.0, schemeId = "x"),
            com.derycode.srs.core.model.TermResult(studentId = "d", termId = "t", averagePercent = 70.0, schemeId = "x")
        )
        val ranked = ResultEngine.rank(results).associateBy { it.studentId }
        assertEquals(1, ranked["a"]!!.classPosition)
        assertEquals(2, ranked["b"]!!.classPosition)
        assertEquals(2, ranked["c"]!!.classPosition)
        assertEquals(4, ranked["d"]!!.classPosition)
    }

    // ── Validation (Section 15) ──────────────────────────────────────────────
    @Test fun markValidation() {
        assertEquals(null, ResultEngine.validateMark("72", 100))
        assertEquals(null, ResultEngine.validateMark("", 100))          // blank allowed
        assertEquals("Mark cannot exceed 100", ResultEngine.validateMark("101", 100))
        assertEquals("Mark cannot be negative", ResultEngine.validateMark("-3", 100))
        assertEquals("Must be a number", ResultEngine.validateMark("abc", 100))
        assertEquals("Value required", ResultEngine.validateMark("", 100, allowBlank = false))
    }

    // ── Full student pipeline on a minimal school (Section 20) ───────────────
    @Test fun fullPlePipeline() {
        val data = SchoolData(
            subjects = listOf(
                com.derycode.srs.core.model.Subject(id = "math", name = "Mathematics", level = Level.PRIMARY, compulsory = true),
                com.derycode.srs.core.model.Subject(id = "eng", name = "English", level = Level.PRIMARY, compulsory = true),
                com.derycode.srs.core.model.Subject(id = "sci", name = "Science", level = Level.PRIMARY, compulsory = true),
                com.derycode.srs.core.model.Subject(id = "sst", name = "SST", level = Level.PRIMARY, compulsory = true)
            ),
            gradingSchemes = listOf(GradingSchemes.PLE),
            marks = listOf(90.0, 85.0, 75.0, 65.0).mapIndexed { i, v ->
                Mark("m$i", "s1", listOf("math", "eng", "sci", "sst")[i], "t1", "eot", v, MarkType.VALUE, 100)
            }
        )
        val r = ResultEngine.studentTermResult(
            data, studentId = "s1", termId = "t1", schemeId = "scheme-ple",
            principalSubjectIds = listOf("math", "eng", "sci", "sst")
        )
        assertEquals(10, r.aggregate)                    // 1+2+3+4 best 4
        assertEquals("Division 2", r.division)
        assertTrue(r.averagePercent > 0)
    }
}
