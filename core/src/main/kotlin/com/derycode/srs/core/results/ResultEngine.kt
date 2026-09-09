package com.derycode.srs.core.results

import com.derycode.srs.core.grading.GradingEngine
import com.derycode.srs.core.model.AggregateRule
import com.derycode.srs.core.model.AssessmentComponent
import com.derycode.srs.core.model.Enrollment
import com.derycode.srs.core.model.Level
import com.derycode.srs.core.model.Mark
import com.derycode.srs.core.model.MarkType
import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.model.SubjectResult
import com.derycode.srs.core.model.TermResult
import kotlin.math.roundToInt

/**
 * Deterministic, testable result pipeline (Sections 20–21).
 * Raw marks → subject totals → grades → points → aggregate/indicator → position.
 * Contains NO UI and no hard-coded boundaries — everything comes from the scheme.
 */
object ResultEngine {

    // ── Subject totals ───────────────────────────────────────────────────────

    /**
     * Compute a subject total (0..100 percentage) from one student's marks.
     * Weighted rules come from the scheme (UCE: CA 20% + EOT 80%).
     * Special marks (ABS/MISSING/EXEMPT/NA) are never treated as zero (Section 16).
     */
    fun subjectPercentage(
        marks: List<Mark>,
        components: List<AssessmentComponent>,
        caWeight: Int,
        examWeight: Int,
        weighted: Boolean
    ): Pair<Double, Boolean> {   // (percentage, incomplete)
        val usable = marks.filter { it.type == MarkType.VALUE }
        val incomplete = marks.any { it.type == MarkType.ABS || it.type == MarkType.MISSING }
        if (usable.isEmpty()) return 0.0 to true

        val componentById = components.associateBy { it.id }
        fun pct(m: Mark): Double = if (m.maxScore <= 0) 0.0 else (m.score / m.maxScore) * 100.0

        return if (weighted) {
            // Weighted scheme: CA components share caWeight, exam components share examWeight.
            val caMarks = usable.filter { componentById[it.componentId]?.code == "CA" }
            val examMarks = usable.filter { componentById[it.componentId]?.code != "CA" }
            val caPart = if (caMarks.isEmpty()) 0.0 else caMarks.map(::pct).average() * caWeight / 100.0
            val examPart = if (examMarks.isEmpty()) 0.0 else examMarks.map(::pct).average() * examWeight / 100.0
            (caPart + examPart) to incomplete
        } else {
            // Unweighted: average of percentages of everything entered so far.
            usable.map(::pct).average() to incomplete
        }
    }

    // ── Full term result for one student ─────────────────────────────────────

    /**
     * Compute one student's TermResult for the given term/scheme.
     * `subjects` must already be scoped to what the student should sit
     * (level subjects, or their A-level combination's principals + subsidiary).
     */
    fun studentTermResult(
        data: SchoolData,
        studentId: String,
        termId: String,
        schemeId: String,
        principalSubjectIds: List<String>,
        compulsorySubjectIds: Set<String> = emptySet()
    ): TermResult {
        val scheme = data.gradingSchemes.firstOrNull { it.id == schemeId }
            ?: return TermResult(studentId = studentId, termId = termId, schemeId = schemeId)
        val studentMarks = data.marks.filter { it.studentId == studentId && it.termId == termId }

        val weighted = scheme.aggregateRule == AggregateRule.UCE_INDICATOR

        val subjectResults = principalSubjectIds.mapNotNull { subjectId ->
            val sm = studentMarks.filter { it.subjectId == subjectId }
            val (pct, incomplete) = subjectPercentage(
                sm, data.components, scheme.caWeightPercent, scheme.examWeightPercent, weighted
            )
            SubjectResult(
                subjectId = subjectId,
                total = sm.filter { it.type == MarkType.VALUE }.sumOf { it.score },
                percentage = (pct * 10).roundToInt() / 10.0,
                grade = GradingEngine.grade(scheme, pct),
                points = GradingEngine.points(scheme, pct),
                remark = GradingEngine.remark(scheme, pct),
                incomplete = incomplete || sm.isEmpty()
            )
        }
        if (subjectResults.isEmpty()) {
            return TermResult(studentId = studentId, termId = termId, schemeId = scheme.id, schemeVersion = scheme.version)
        }

        val avg = subjectResults.map { it.percentage }.average()
        var aggregate: Int? = null
        var division: String? = null
        var uce: String? = null
        var uacePoints: Int? = null

        when (scheme.aggregateRule) {
            AggregateRule.PLE_AGGREGATE -> {
                // Aggregate = best N subjects (default 4), lower is better.
                val best = subjectResults.sortedBy { it.points }.take(scheme.aggregateBestSubjects)
                aggregate = best.sumOf { it.points }
                division = GradingEngine.divisionForAggregate(scheme, aggregate)
            }
            AggregateRule.UCE_INDICATOR -> {
                val compulsoryGrades = subjectResults.filter { it.subjectId in compulsorySubjectIds }.map { it.grade }
                val missingCompulsory = subjectResults.any { it.incomplete && it.subjectId in compulsorySubjectIds }
                val allE = compulsoryGrades.isNotEmpty() && compulsoryGrades.all { it == "E" }
                uce = GradingEngine.uceResultIndicator(allE, missingCompulsory)
            }
            AggregateRule.UACE_POINTS -> {
                // 3 principals + subsidiary point if grade better than threshold.
                val principals = subjectResults.sortedByDescending { it.points }.take(scheme.principalCount)
                val principalPoints = principals.sumOf { it.points }
                uacePoints = principalPoints
                uce = null
            }
            AggregateRule.CUSTOM -> { /* school-defined; average only */ }
        }

        return TermResult(
            studentId = studentId,
            termId = termId,
            subjectResults = subjectResults,
            averagePercent = (avg * 10).roundToInt() / 10.0,
            aggregate = aggregate,
            division = division,
            uceResult = uce,
            uacePoints = uacePoints,
            schemeId = scheme.id,
            schemeVersion = scheme.version
        )
    }

    // ── Ranking (competition ranking: 1, 2, 2, 4) ────────────────────────────

    fun rank(results: List<TermResult>): List<TermResult> {
        val sorted = results.sortedByDescending { it.averagePercent }
        return sorted.mapIndexed { idx, r ->
            val tieCount = sorted.count { it.averagePercent == r.averagePercent }
            val firstIdx = sorted.indexOfFirst { it.averagePercent == r.averagePercent }
            r.copy(classPosition = if (tieCount > 1) firstIdx + 1 else idx + 1)
        }
    }

    fun rankSubject(sr: List<SubjectResult>): List<SubjectResult> {
        val sorted = sr.sortedByDescending { it.percentage }
        return sorted.mapIndexed { idx, s ->
            val tieCount = sorted.count { it.percentage == s.percentage }
            val firstIdx = sorted.indexOfFirst { it.percentage == s.percentage }
            s.copy(positionInSubject = if (tieCount > 1) firstIdx + 1 else idx + 1)
        }
    }

    // ── Class results (whole class in one call) ──────────────────────────────

    fun classResults(
        data: SchoolData,
        classId: String,
        termId: String,
        schemeId: String
    ): List<TermResult> {
        val klass = data.classes.firstOrNull { it.id == classId } ?: return emptyList()
        val year = data.academicYears.firstOrNull { y -> termId in y.terms.map { it.id } }
        val students = data.enrollments
            .filter { it.classId == classId && (year == null || it.academicYearId == year.id) }
            .mapNotNull { e -> data.students.firstOrNull { s -> s.id == e.studentId } }
            .filter { it.status.name == "ACTIVE" }
        if (students.isEmpty()) return emptyList()

        // Subjects the student sits: combination principals+subsidiary, or level subjects.
        val subjectIds: List<String> = klass.combinationId
            ?.let { cid ->
                val comb = data.combinations.firstOrNull { it.id == cid }
                (comb?.principalSubjectIds ?: emptyList()) + (comb?.subsidiarySubjectIds ?: emptyList())
            }
            ?: data.subjects.filter { it.level == klass.level && it.active }.map { it.id }

        val compulsory = data.subjects.filter { it.compulsory }.map { it.id }.toSet()
        val raw = students.map {
            studentTermResult(data, it.id, termId, schemeId, subjectIds, compulsory)
        }
        val ranked = rank(raw)
        return ranked
    }

    // ── Validation helpers (Section 15) ──────────────────────────────────────

    /** Returns an error message, or null when valid. */
    fun validateMark(scoreText: String, max: Int, allowBlank: Boolean = true): String? {
        val t = scoreText.trim()
        if (t.isEmpty()) return if (allowBlank) null else "Value required"
        val v = t.toDoubleOrNull() ?: return "Must be a number"
        if (v < 0) return "Mark cannot be negative"
        if (v > max) return "Mark cannot exceed $max"
        return null
    }

    /** Students with missing marks for a subject — used by report gating (Section 48). */
    fun studentsWithMissingMarks(
        data: SchoolData, classId: String, termId: String, subjectId: String
    ): List<String> {
        val year = data.academicYears.firstOrNull { y -> termId in y.terms.map { it.id } }
        return data.enrollments
            .filter { it.classId == classId && (year == null || it.academicYearId == year.id) }
            .map { it.studentId }
            .filter { sid ->
                data.marks.none { it.studentId == sid && it.termId == termId && it.subjectId == subjectId }
            }
    }
}
