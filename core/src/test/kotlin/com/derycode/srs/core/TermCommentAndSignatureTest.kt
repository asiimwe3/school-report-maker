package com.derycode.srs.core

import com.derycode.srs.core.model.*
import com.derycode.srs.core.report.ReportEngine
import com.derycode.srs.core.results.ResultEngine
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * A whole class prints the same closing remark without the school typing it
 * student by student, the standard card shows when the term began as well as
 * when it closes, and a scanned signature prints on the card.
 */
class TermCommentAndSignatureTest {

    private fun sample(termComment: String, headComment: String): SchoolData {
        val school = School(name = "Tooro Royal College", motto = "Together for a Brighter Future",
            address = "P.O. Box 123, Fort Portal", district = "Kabarole")
        val year = AcademicYear(id = "y1", year = "2026", currentTermId = "t1", terms = listOf(
            Term(id = "t1", yearId = "y1", number = 2,
                startDate = "2026-05-24", endDate = "2026-08-14", nextTermBegins = "2026-09-12",
                classTeacherComment = termComment, headTeacherComment = headComment)))
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
            academicYears = listOf(year),
            classes = listOf(cls),
            students = listOf(student),
            subjects = listOf(subject),
            teachers = listOf(teacher),
            components = comps,
            marks = marks,
            gradingSchemes = listOf(scheme),
            enrollments = listOf(Enrollment(id = "e1", studentId = "s1", academicYearId = "y1", classId = "c1")),
            comments = emptyList()
        )
    }

    private fun result(d: SchoolData) =
        ResultEngine.classResults(d, "c1", "t1", "g1").first()

    @Test
    fun standardCardCarriesTheTermWideClassCommentForEveryStudent() {
        val d = sample("A steady term. Keep working on Mathematics.", "")
        val html = ReportEngine.studentReport(d, result(d), TemplateLayout.SUMMARY)
        assertTrue(html.contains("A steady term. Keep working on Mathematics."),
            "the term-wide class comment must print on the standard card")
    }

    @Test
    fun standardCardCarriesTheTermWideHeadComment() {
        val d = sample("", "Promoted to Senior 2. Well done.")
        val html = ReportEngine.studentReport(d, result(d), TemplateLayout.SUMMARY)
        assertTrue(html.contains("Promoted to Senior 2. Well done."),
            "the term-wide head comment must print on the standard card")
    }

    @Test
    fun aStudentsOwnCommentWinsOverTheTermWideOne() {
        val base = sample("Default closing remark for the class.", "")
        val d = base.copy(comments = listOf(Comment(
            id = "c1", studentId = "s1", termId = "t1",
            type = CommentType.CLASS_TEACHER, text = "Amina led the science club this term.")))
        val html = ReportEngine.studentReport(d, result(d), TemplateLayout.SUMMARY)
        assertTrue(html.contains("Amina led the science club this term."))
        assertFalse(html.contains("Default closing remark for the class."),
            "the individual remark replaces the class default, not stacks on it")
    }

    @Test
    fun everyLayoutShowsTheTermWideComment() {
        val d = sample("Class closing remark.", "Head closing remark.")
        for (layout in listOf(TemplateLayout.CLASSIC, TemplateLayout.MODERN,
                              TemplateLayout.COMPACT, TemplateLayout.PLAIN, TemplateLayout.SUMMARY)) {
            val html = ReportEngine.studentReport(d, result(d), layout)
            assertTrue(html.contains("Class closing remark."), "$layout must show the class comment")
            assertTrue(html.contains("Head closing remark."), "$layout must show the head comment")
        }
    }

    @Test
    fun standardCardShowsWhenTheTermBeganAndWhenItCloses() {
        val d = sample("x", "")
        val html = ReportEngine.studentReport(d, result(d), TemplateLayout.SUMMARY)
        assertTrue(html.contains("Term Began"), "the card must label the opening date")
        assertTrue(html.contains("2026-05-24"), "the term start date must print")
        assertTrue(html.contains("2026-08-14"), "the closing date must print")
        assertTrue(html.contains("2026-09-12"), "the next term opening date must print")
    }

    @Test
    fun scannedSignatureIsEmbeddedInTheCard() {
        val png = java.io.File.createTempFile("sig", ".png")
        // 1x1 transparent PNG
        png.writeBytes(java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="))
        val base = sample("x", "")
        val signed = base.copy(teachers = listOf(
            Teacher(id = "te1", name = "Francis Mwesige", role = Role.HEAD_TEACHER, signaturePath = png.absolutePath)))
        val html = ReportEngine.studentReport(signed, result(signed), TemplateLayout.SUMMARY)
        assertTrue(html.contains("data:image/png;base64,"), "the scanned signature must be embedded for printing")
        assertTrue(html.contains("class=\"sigimg\""), "it must render as a signature image")
        png.delete()
    }

    @Test
    fun missingSignatureFileDegradesQuietly() {
        val d = sample("x", "")
        val signed = d.copy(teachers = listOf(
            Teacher(id = "te1", name = "Francis Mwesige", role = Role.HEAD_TEACHER, signaturePath = "/nope/gone.png")))
        val html = ReportEngine.studentReport(signed, result(signed), TemplateLayout.SUMMARY)
        assertFalse(html.contains("data:image"), "a missing file must not produce a broken image tag")
        assertTrue(html.contains("Head Teacher"), "the card still prints its signature line")
    }
}
