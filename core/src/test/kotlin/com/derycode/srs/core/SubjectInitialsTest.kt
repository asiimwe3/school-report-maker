package com.derycode.srs.core

import com.derycode.srs.core.model.*
import com.derycode.srs.core.report.ReportEngine
import com.derycode.srs.core.results.ResultEngine
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The INITIALS column must print the subject's own initials, because a school
 * can have a sign-off per subject with no teacher record behind it. When the
 * subject has none, the assigned teacher's initials are used instead.
 */
class SubjectInitialsTest {

    private fun dataWith(initials: String, assignedTeacher: Boolean): SchoolData {
        val year = AcademicYear(id = "y1", year = "2026", currentTermId = "t1",
            terms = listOf(Term(id = "t1", yearId = "y1", number = 3)))
        val scheme = GradingScheme(id = "g1", name = "UCE", level = Level.O_LEVEL,
            aggregateRule = AggregateRule.UCE_INDICATOR, caWeightPercent = 20, examWeightPercent = 80,
            boundaries = listOf(GradeBoundary(minScore = 0.0, maxScore = 100.0, grade = "A", label = "Outstanding")))
        return SchoolData(
            school = School(name = "Tooro Royal College"),
            classes = listOf(SchoolClass(id = "c1", name = "Senior 1", stream = "A", level = Level.O_LEVEL)),
            students = listOf(Student(id = "s1", admissionNo = "TRC1001", firstName = "Ahaisibwe", lastName = "Tom")),
            subjects = listOf(Subject(id = "sub1", name = "Entrepreneurship", code = "ENT", initials = initials, level = Level.O_LEVEL)),
            teachers = listOf(Teacher(id = "te1", name = "Francis Mwesige")),
            components = listOf(AssessmentComponent(id = "comp-eot", code = "EOT", name = "Exam", weightPercent = 100)),
            marks = listOf(Mark(id = "m1", studentId = "s1", subjectId = "sub1", termId = "t1", componentId = "comp-eot", score = 70.0, maxScore = 100)),
            gradingSchemes = listOf(scheme),
            academicYears = listOf(year),
            enrollments = listOf(Enrollment(id = "e1", studentId = "s1", classId = "c1", academicYearId = "y1")),
            assignments = if (assignedTeacher) listOf(TeacherAssignment(id = "a1", teacherId = "te1", subjectId = "sub1", classId = "c1")) else emptyList()
        )
    }

    @Test
    fun subjectInitialsArePrintedEvenWithNoTeacherAssigned() {
        val data = dataWith(initials = "AKD", assignedTeacher = false)
        val result = ResultEngine.classResults(data, "c1", "t1", "g1").first()
        val html = ReportEngine.studentReport(data, result, TemplateLayout.SUMMARY)
        assertTrue(html.contains(">AKD<"), "the subject's own initials were not printed")
    }

    @Test
    fun subjectInitialsWinOverTheTeacherInitials() {
        val data = dataWith(initials = "kl", assignedTeacher = true)
        val result = ResultEngine.classResults(data, "c1", "t1", "g1").first()
        val html = ReportEngine.studentReport(data, result, TemplateLayout.SUMMARY)
        assertTrue(html.contains(">KL<"), "subject initials should print uppercased")
        assertTrue(!html.contains(">FM<"), "teacher initials must not win when the subject has its own")
    }

    @Test
    fun blankInitialsFallBackToTheTeacher() {
        val data = dataWith(initials = "", assignedTeacher = true)
        val result = ResultEngine.classResults(data, "c1", "t1", "g1").first()
        val html = ReportEngine.studentReport(data, result, TemplateLayout.SUMMARY)
        assertTrue(html.contains(">FM<"), "teacher initials should be the fallback")
    }
}
