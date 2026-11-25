package com.derycode.srs.core

import com.derycode.srs.core.model.*
import com.derycode.srs.core.report.ReportEngine
import com.derycode.srs.core.results.ResultEngine
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A card must never lie about a learner:
 *  - a subject with no mark prints a dash, not a zero and not a grade,
 *  - a subject that was marked always prints a comment and an initial, even when
 *    the teacher typed neither,
 *  - the comment boxes are never empty on a card that has marks.
 */
class BlankColumnsTest {

    private fun data(): SchoolData {
        val school = School(name = "Tooro Royal College", motto = "Together for a Brighter Future")
        val year = AcademicYear(id = "y1", year = "2026", currentTermId = "t1", terms = listOf(
            Term(id = "t1", yearId = "y1", number = 3, endDate = "2026-11-28", nextTermBegins = "2027-02-01")))
        val cls = SchoolClass(id = "c1", name = "Senior 1", stream = "", level = Level.O_LEVEL)
        val student = Student(id = "s1", admissionNo = "TRC1001", firstName = "Ahaisibwe", lastName = "Tom")
        val maths = Subject(id = "sub-math", name = "Mathematics", code = "MATH", level = Level.O_LEVEL)
        val ict = Subject(id = "sub-ict", name = "ICT", code = "ICT", level = Level.O_LEVEL)
        val comps = listOf(
            AssessmentComponent(id = "k-bot", code = "BOT", name = "Beginning of Term", weightPercent = 20),
            AssessmentComponent(id = "k-eot", code = "EOT", name = "End of Term", weightPercent = 80)
        )
        val scheme = GradingScheme(id = "g1", name = "UCE 2025", level = Level.O_LEVEL, boundaries = listOf(
            GradeBoundary(80.0, 100.0, "A", "Exceptional", 1, "Exceptional"),
            GradeBoundary(70.0, 79.0, "B", "Outstanding", 2, "Outstanding"),
            GradeBoundary(60.0, 69.0, "C", "Satisfactory", 3, "Satisfactory"),
            GradeBoundary(50.0, 59.0, "D", "Basic", 4, "Basic"),
            GradeBoundary(0.0, 49.0, "E", "Elementary", 5, "Elementary")
        ))
        // Mathematics was marked (80%), ICT was never entered at all
        val marks = listOf(
            Mark(id = "m1", studentId = "s1", subjectId = "sub-math", termId = "t1", componentId = "k-bot", score = 80.0),
            Mark(id = "m2", studentId = "s1", subjectId = "sub-math", termId = "t1", componentId = "k-eot", score = 80.0)
        )
        return SchoolData(
            school = school,
            classes = listOf(cls),
            students = listOf(student),
            subjects = listOf(maths, ict),
            components = comps,
            marks = marks,
            gradingSchemes = listOf(scheme),
            academicYears = listOf(year),
            enrollments = listOf(Enrollment(id = "e1", studentId = "s1", classId = "c1", academicYearId = "y1")),
            teachers = emptyList(),
            assignments = emptyList(),
            comments = emptyList()
        )
    }

    private fun row(html: String, subject: String): String =
        Regex("<tr>(?:(?!</tr>).)*?" + Regex.escape(subject) + "(?:(?!</tr>).)*?</tr>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.value ?: error("no row for " + subject)

    @Test
    fun anUnmarkedSubjectPrintsADashNotAZero() {
        val d = data()
        val result = ResultEngine.classResults(d, "c1", "t1", "g1").first()
        val html = ReportEngine.studentReport(d, result, TemplateLayout.SUMMARY)

        val ictRow = row(html, "ICT")
        assertTrue(ictRow.contains("&mdash;"), "an unmarked subject must print a dash: $ictRow")
        assertFalse(ictRow.contains(">0<") || ictRow.contains(">0.0<"), "no zero may appear: $ictRow")
        assertFalse(ictRow.contains(">E<"), "an unmarked subject must not be graded: $ictRow")

        val mathsRow = row(html, "MATHEMATICS")
        assertTrue(mathsRow.contains("80"), "a marked subject still prints its total: $mathsRow")
    }

    @Test
    fun aMarkedSubjectAlwaysPrintsACommentAndAnInitial() {
        val d = data()
        val result = ResultEngine.classResults(d, "c1", "t1", "g1").first()
        val html = ReportEngine.studentReport(d, result, TemplateLayout.SUMMARY)

        val mathsRow = row(html, "MATHEMATICS")
        // 80% is grade A on the scheme, whose remark is Exceptional
        assertTrue(mathsRow.contains("Exceptional"), "the grade remark fills the comment column: $mathsRow")
        assertTrue(mathsRow.contains(">MD<"), "the standard sign-off prints when nobody was assigned: $mathsRow")
    }

    @Test
    fun theCommentBoxesAreNeverEmptyOnACardWithMarks() {
        val d = data()
        val result = ResultEngine.classResults(d, "c1", "t1", "g1").first()
        val html = ReportEngine.studentReport(d, result, TemplateLayout.SUMMARY)
        assertTrue(html.contains("Average 80"), "the head teacher box carries the learner's own figures")
    }

    @Test
    fun aLearnerWithNothingMarkedGetsNoComment() {
        val d = data().copy(marks = emptyList())
        val result = ResultEngine.classResults(d, "c1", "t1", "g1").first()
        assertTrue(ReportEngine.autoRemark(d, result).isEmpty(), "no marks, no invented remark")
    }
}
