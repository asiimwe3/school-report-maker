package com.derycode.srs.core.grading

import com.derycode.srs.core.model.MarkEntry
import com.derycode.srs.core.model.Student
import kotlin.math.roundToInt

/** Uganda-style PLE grading (Division 1-7 points, grades A-E). */
object Grading {

    fun grade(score: Double): String = when {
        score >= 80 -> "A"
        score >= 75 -> "B+"
        score >= 70 -> "B"
        score >= 65 -> "C+"
        score >= 60 -> "C"
        score >= 55 -> "D+"
        score >= 50 -> "D"
        score >= 45 -> "E+"
        score >= 40 -> "E"
        else -> "F"
    }

    /** Aggregate points: lower is better (like PLE aggregates 1..9). */
    fun points(score: Double): Int = when {
        score >= 80 -> 1
        score >= 75 -> 2
        score >= 70 -> 2
        score >= 65 -> 3
        score >= 60 -> 3
        score >= 55 -> 4
        score >= 50 -> 4
        score >= 45 -> 5
        score >= 40 -> 5
        else -> 7
    }

    fun remark(score: Double): String = when {
        score >= 80 -> "Excellent"
        score >= 70 -> "Very good"
        score >= 60 -> "Good"
        score >= 50 -> "Fair"
        score >= 40 -> "Pass"
        else -> "Needs improvement"
    }

    fun division(aggregate: Int): String = when (aggregate) {
        in 4..12 -> "Division 1"
        in 13..17 -> "Division 2"
        in 18..21 -> "Division 3"
        in 22..25 -> "Division 4"
        else -> "Division U"
    }

    /** Average of a student's subject totals for a term. */
    fun termAverage(marks: List<MarkEntry>, studentId: String, term: Int): Double {
        val perSubject = marks
            .filter { it.studentId == studentId && it.term == term }
            .groupBy { it.subjectId }
            .values
            .map { entries -> entries.sumOf { it.score } / entries.size }
        return if (perSubject.isEmpty()) 0.0 else perSubject.average()
    }

    /** Ranked class position of every student by term average. Returns studentId -> "3 / 45". */
    fun classPositions(
        students: List<Student>,
        classId: String,
        marks: List<MarkEntry>,
        term: Int
    ): Map<String, String> {
        val inClass = students.filter { it.classId == classId && it.active }
        val avgs = inClass.associate { it.id to termAverage(marks, it.id, term) }
        val sorted = avgs.entries.sortedByDescending { it.value }
        val out = mutableMapOf<String, String>()
        sorted.forEachIndexed { idx, e ->
            out[e.key] = "${idx + 1} / ${sorted.size}"
        }
        return out
    }

}


fun Double.round1(): Double = (this * 10).roundToInt() / 10.0
