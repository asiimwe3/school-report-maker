package com.derycode.srs.core.grading

import com.derycode.srs.core.model.AggregateRule
import com.derycode.srs.core.model.DivisionBoundary
import com.derycode.srs.core.model.GradeBoundary
import com.derycode.srs.core.model.GradingScheme
import com.derycode.srs.core.model.Level

/**
 * The grading engine works ONLY from GradingScheme data (Sections 18–19, 59).
 * No boundary, label or point is hard-coded into the logic below — the seeds
 * are starter configurations schools can edit or replace.
 */
object GradingSchemes {

    // ── PLE: grades 1–9, aggregate = best 4, divisions 1–4/U ──────────────────
    val PLE: GradingScheme = GradingScheme(
        id = "scheme-ple",
        name = "PLE (Primary Leaving Examination)",
        level = Level.PRIMARY,
        aggregateRule = AggregateRule.PLE_AGGREGATE,
        aggregateBestSubjects = 4,
        boundaries = listOf(
            GradeBoundary(90.0, 100.0, "1", "Distinction", 1, "Excellent"),
            GradeBoundary(80.0, 89.0, "2", "Distinction", 2, "Very good"),
            GradeBoundary(70.0, 79.0, "3", "Credit", 3, "Good"),
            GradeBoundary(60.0, 69.0, "4", "Credit", 4, "Good"),
            GradeBoundary(50.0, 59.0, "5", "Credit", 5, "Fair"),
            GradeBoundary(40.0, 49.0, "6", "Pass", 6, "Pass"),
            GradeBoundary(30.0, 39.0, "7", "Pass", 7, "Weak pass"),
            GradeBoundary(20.0, 29.0, "8", "Fail", 8, "Fail"),
            GradeBoundary(0.0, 19.0, "9", "Fail", 9, "Fail")
        ),
        divisionTable = listOf(
            DivisionBoundary(4, "Division 1", "Best possible"),
            DivisionBoundary(12, "Division 2"),
            DivisionBoundary(17, "Division 3"),
            DivisionBoundary(22, "Division 4"),
            DivisionBoundary(Int.MAX_VALUE, "Division U", "Ungraded")
        )
    )

    // ── UCE competency-based (UNEB 2025): A–E, 20% CA + 80% exam ─────────────
    val UCE: GradingScheme = GradingScheme(
        id = "scheme-uce",
        name = "UCE Competency-Based (2025)",
        level = Level.O_LEVEL,
        aggregateRule = AggregateRule.UCE_INDICATOR,
        caWeightPercent = 20,
        examWeightPercent = 80,
        boundaries = listOf(
            GradeBoundary(80.0, 100.0, "A", "Exceptional", 5, "Outstanding mastery of competencies"),
            GradeBoundary(70.0, 79.0, "B", "Outstanding", 4, "Very good performance"),
            GradeBoundary(60.0, 69.0, "C", "Satisfactory", 3, "Satisfactory performance"),
            GradeBoundary(50.0, 59.0, "D", "Basic", 2, "Minimum competency achieved"),
            GradeBoundary(0.0, 49.0, "E", "Elementary", 1, "Below minimum competency")
        )
    )

    // ── UACE: principals A–E with points, subsidiary earns a point if useful ──
    val UACE: GradingScheme = GradingScheme(
        id = "scheme-uace",
        name = "UACE (Advanced Certificate)",
        level = Level.A_LEVEL,
        aggregateRule = AggregateRule.UACE_POINTS,
        principalCount = 3,
        subsidiaryCounts = true,
        subsidiaryThresholdGrade = "F",
        boundaries = listOf(
            GradeBoundary(80.0, 100.0, "A", "Excellent", 6, "Excellent"),
            GradeBoundary(70.0, 79.0, "B", "Very good", 5, "Very good"),
            GradeBoundary(60.0, 69.0, "C", "Good", 4, "Good"),
            GradeBoundary(50.0, 59.0, "D", "Credit", 3, "Credit"),
            GradeBoundary(40.0, 49.0, "E", "Pass", 2, "Pass"),
            GradeBoundary(30.0, 39.0, "O", "Ordinary", 1, "Ordinary pass"),
            GradeBoundary(0.0, 29.0, "F", "Fail", 0, "Fail")
        )
    )

    val ALL: List<GradingScheme> = listOf(PLE, UCE, UACE)

    fun defaultSchemeFor(level: Level): GradingScheme? = ALL.firstOrNull { it.level == level }
}

/**
 * Pure functions that compute grades from scheme data. Deterministic & testable
 * (Sections 20, 53).
 */
object GradingEngine {

    /**
     * Find the boundary a percentage falls into.
     * Values between boundary ranges (e.g. 89.9 in an ..89 scheme) fall into the
     * highest boundary that starts at or below them; out-of-range values clamp
     * to the nearest boundary instead of returning a wrong grade.
     */
    fun boundaryFor(scheme: GradingScheme, percentage: Double): GradeBoundary? =
        scheme.boundaries.firstOrNull { percentage >= it.minScore && percentage <= it.maxScore }
            ?: scheme.boundaries.filter { percentage >= it.minScore }.maxByOrNull { it.minScore }
            ?: scheme.boundaries.minByOrNull { it.minScore }

    fun grade(scheme: GradingScheme, percentage: Double): String =
        boundaryFor(scheme, percentage)?.grade ?: "—"

    fun points(scheme: GradingScheme, percentage: Double): Int =
        boundaryFor(scheme, percentage)?.points ?: 0

    fun remark(scheme: GradingScheme, percentage: Double): String =
        boundaryFor(scheme, percentage)?.remark ?: ""

    fun label(scheme: GradingScheme, percentage: Double): String =
        boundaryFor(scheme, percentage)?.label ?: ""

    /** PLE division from aggregate, driven entirely by the scheme's division table. */
    fun divisionForAggregate(scheme: GradingScheme, aggregate: Int): String =
        scheme.divisionTable.firstOrNull { aggregate <= it.maxAggregate }?.division ?: "Division U"

    /**
     * UCE result indicator (Section: Result 1/2/3).
     *  result 2 = missing compulsory subject/project marks,
     *  result 3 = all E,
     *  result 1 = qualified (at least one D or better among compulsory subjects).
     */
    fun uceResultIndicator(allE: Boolean, missingCompulsory: Boolean): String = when {
        missingCompulsory -> "Result 2"
        allE -> "Result 3"
        else -> "Result 1"
    }
}
