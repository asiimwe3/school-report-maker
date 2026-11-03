package com.derycode.srs.core

import com.derycode.srs.core.model.*
import com.derycode.srs.core.report.ReportEngine
import com.derycode.srs.core.results.ResultEngine
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The summary card must carry the school's own layout: A1–A3 assessments,
 * the 20% formative and 80% exam split, totals, per-subject teacher comment
 * and initials, both comments, fees and term dates.
 */
class SummaryReportTest {

    private fun sample(): SchoolData {
        val school = School(name = "Tooro Royal College", motto = "Together for a Brighter Future",
            phone = "+256 700 000 000", email = "info@trc.ac.ug", address = "P.O. Box 123, Fort Portal",
            district = "Kabarole", colorPrimary = "#1F4E79")
        val year = AcademicYear(id = "y1", year = "2026", currentTermId = "t1", terms = listOf(
            Term(id = "t1", yearId = "y1", number = 2, endDate = "2026-08-14", nextTermBegins = "2026-09-12")))
        val cls = SchoolClass(id = "c1", name = "Senior 1", stream = "A", level = Level.O_LEVEL)
        val student = Student(id = "s1", admissionNo = "TRC/001", firstName = "Amina", lastName = "Kembabazi")
        val subject = Subject(id = "sub1", name = "Mathematics", code = "MTC", level = Level.O_LEVEL)
        val teacher = Teacher(id = "te1", name = "Francis Mwesige", role = Role.TEACHER)
        val comps = listOf(
            AssessmentComponent(id = "comp-ca", code = "CA", name = "Coursework", weightPercent = 20),
            AssessmentComponent(id = "comp-eot", code = "EOT", name = "End of term", weightPercent = 80)
        )
        val marks = listOf(
            Mark(id = "m1", studentId = "s1", subjectId = "sub1", termId = "t1", componentId = "comp-ca", score = 16.0, maxScore = 20),
            Mark(id = "m2", studentId = "s1", subjectId = "sub1", termId = "t1", componentId = "comp-eot", score = 64.0, maxScore = 80)
        )
        val scheme = GradingScheme(id = "g1", name = "UCE", level = Level.O_LEVEL,
            aggregateRule = AggregateRule.UCE_INDICATOR, caWeightPercent = 20, examWeightPercent = 80,
            boundaries = listOf(GradeBoundary(minScore = 80.0, maxScore = 100.0, grade = "A", label = "Outstanding"),
                                GradeBoundary(minScore = 60.0, maxScore = 79.9, grade = "B", label = "Good"),
                                GradeBoundary(minScore = 0.0, maxScore = 59.9, grade = "F", label = "Needs support")))
        return SchoolData(
            school = school,
            classes = listOf(cls),
            students = listOf(student),
            subjects = listOf(subject),
            teachers = listOf(teacher),
            components = comps,
            marks = marks,
            gradingSchemes = listOf(scheme),
            academicYears = listOf(year),
            enrollments = listOf(Enrollment(id = "e1", studentId = "s1", classId = "c1", academicYearId = "y1")),
            assignments = listOf(TeacherAssignment(id = "a1", teacherId = "te1", subjectId = "sub1", classId = "c1")),
            comments = listOf(
                Comment(id = "cm1", studentId = "s1", termId = "t1", type = CommentType.CLASS_TEACHER, text = "A steady term."),
                Comment(id = "cm2", studentId = "s1", termId = "t1", type = CommentType.HEAD_TEACHER, text = "Keep pushing."),
                Comment(id = "cm3", studentId = "s1", termId = "t1", subjectId = "sub1", type = CommentType.SUBJECT, text = "Improve algebra.")
            )
        )
    }

    @Test
    fun summaryCardCarriesTheSchoolLayout() {
        val data = sample()
        val result = ResultEngine.classResults(data, "c1", "t1", "g1").first()
        val html = ReportEngine.studentReport(data, result, TemplateLayout.SUMMARY)

        // the split is real: 16/20 = 80% formative, 64/80 = 80% exam
        val subj = result.subjectResults.first()
        assertTrue(subj.formativePercent > 79.0 && subj.formativePercent < 81.0, "formative was ${subj.formativePercent}")
        assertTrue(subj.examPercent > 79.0 && subj.examPercent < 81.0, "exam was ${subj.examPercent}")

        listOf("FRNTV", "20%", "80%", "A1", "A2", "A3", "TEACHER COMMENT", "TEACHER", "AVERAGE SCORE",
               "Class Teacher", "Head Teacher Remarks", "CURRENT TERM CLOSING DATE", "NEXT TERM OPENING DATE",
               "Improve algebra.", "2026-08-14", "2026-09-12", "SCHOOL", "Together for a Brighter Future",
               "MATHEMATICS").forEach {
            assertTrue(html.contains(it), "summary card missing: $it")
        }
        // initials of the subject teacher: F.M. -> FM
        assertTrue(html.contains(">FM<"), "teacher initials missing")

        // keep a copy so the layout can be eyeballed after a build
        java.io.File("build/summary-preview.html").writeText(html)
    }
}
