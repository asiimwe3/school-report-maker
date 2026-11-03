package com.derycode.srs.core.results

import com.derycode.srs.core.model.AssessmentComponent
import com.derycode.srs.core.model.Mark
import com.derycode.srs.core.model.MarkType
import kotlin.math.roundToInt

/**
 * The O-Level final score is the weighted sum of two assessments:
 *
 *     final = A2 × 20% + A3 × 80%
 *
 * The two marks are stored separately (one [Mark] row per component, as always) and
 * the final score is only ever derived — it is never saved as a mark of its own, so
 * a teacher can correct A2 or A3 later and every total, grade, report and export
 * follows immediately.
 *
 * A missing mark is never read as zero: with A2 = 70 and no A3 we know the final is
 * incomplete, and a genuine A2 = 0, A3 = 0 is a real score of 0. The two cases look
 * different here ([Score.final] is null in the first, 0.0 in the second) and in the
 * grids (a dash instead of a grade).
 *
 * Schools that do not run A2/A3 (primary schools, or an A-Level class) simply get no
 * rules back, and every caller falls back to the calculation that was there before.
 */
object AssessmentWeighting {

    const val A2 = "A2"
    const val A3 = "A3"
    const val A2_DEFAULT_WEIGHT = 20
    const val A3_DEFAULT_WEIGHT = 80

    /** Marks below zero or above the paper's maximum are not marks; they are refused. */
    const val MIN_MARK = 0.0
    const val MAX_MARK = 100.0

    data class Rule(val component: AssessmentComponent, val weightPercent: Int) {
        val code: String get() = component.code
    }

    /**
     * The weighted final score for one student, one subject, one term.
     * [final] is null when either assessment is missing and [incomplete] is true.
     */
    data class Score(
        val final: Double? = null,
        val incomplete: Boolean = true,
        /** Mark per component code (unweighted percentage), only for components that have one. */
        val byCode: Map<String, Double> = emptyMap(),
        val missing: List<String> = emptyList(),
        val weighted: Boolean = false,
        /** What the marks that ARE there come to, so a half-marked sheet can still show progress. */
        val partial: Double? = null
    ) {
        /** Grade for a complete score, else "—" (never a grade for half a card). */
        fun grade(scheme: com.derycode.srs.core.model.GradingScheme?): String =
            if (final == null || scheme == null) "—"
            else com.derycode.srs.core.grading.GradingEngine.grade(scheme, final)
    }

    /** A2 and A3, with the school's own weights when it has set them. */
    fun rules(
        components: List<AssessmentComponent>,
        a2Weight: Int = A2_DEFAULT_WEIGHT,
        a3Weight: Int = A3_DEFAULT_WEIGHT,
        enabled: Boolean = true
    ): List<Rule> {
        if (!enabled) return emptyList()
        val a2 = components.firstOrNull { it.active && it.code.equals(A2, ignoreCase = true) }
        val a3 = components.firstOrNull { it.active && it.code.equals(A3, ignoreCase = true) }
        if (a2 == null || a3 == null) return emptyList()
        val w2 = a2.weightPercent.takeIf { it > 0 } ?: a2Weight
        val w3 = a3.weightPercent.takeIf { it > 0 } ?: a3Weight
        return listOf(Rule(a2, w2), Rule(a3, w3))
    }

    /** Percentage of the paper, so a paper marked out of 20 still counts the same. */
    fun percent(m: Mark): Double = if (m.maxScore <= 0) 0.0 else (m.score / m.maxScore) * 100.0

    fun score(marks: List<Mark>, rules: List<Rule>): Score {
        if (rules.isEmpty()) return Score()
        val byComponent = marks.associateBy { it.componentId }
        val byCode = mutableMapOf<String, Double>()
        val missing = mutableListOf<String>()
        var weighted = 0.0
        var weight = 0.0
        rules.forEach { r ->
            val m = byComponent[r.component.id]
            if (m == null || m.type != MarkType.VALUE) {
                missing += r.code
            } else {
                val p = percent(m)
                byCode[r.code] = round1(p)
                weighted += p * r.weightPercent
                weight += r.weightPercent
            }
        }
        val partial = if (weight <= 0) null else round2(weighted / weight)
        return Score(
            final = if (missing.isEmpty() && partial != null) partial else null,
            incomplete = missing.isNotEmpty(),
            byCode = byCode,
            missing = missing,
            weighted = true,
            partial = partial
        )
    }

    /** Convenience for a grid row: A2 + A3 marks in, final score and grade out. */
    fun forMarks(marks: List<Mark>, components: List<AssessmentComponent>, enabled: Boolean = true): Score =
        score(marks, rules(components, enabled = enabled))

    /**
     * A mark a teacher can actually store: 0..100, or null when the text is blank or
     * outside the range. Blank stays blank — it is never turned into a zero.
     */
    fun sanitize(raw: String): Double? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val v = t.toDoubleOrNull() ?: return null
        if (v < MIN_MARK || v > MAX_MARK) return null
        return v
    }

    fun valid(raw: String): Boolean = raw.isBlank() || sanitize(raw) != null

    fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0
    fun round2(v: Double): Double = (v * 100).roundToInt() / 100.0

    /** "78.5" for a complete score, "—" when the row is still incomplete. */
    fun display(s: Score): String = when {
        s.final == null -> "—"
        kotlin.math.abs(s.final - s.final.roundToInt()) < 0.005 -> s.final.roundToInt().toString()
        else -> String.format(java.util.Locale.US, "%.2f", s.final)
    }
}
