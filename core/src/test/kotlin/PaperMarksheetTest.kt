package com.derycode.srs.core

import com.derycode.srs.core.model.*
import com.derycode.srs.core.report.DocxReport
import com.derycode.srs.core.results.ResultEngine
import com.derycode.srs.core.seed.Seeds
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The school's own marksheet rules A1, A2, A3 (each out of 20), then X/20, X/80
 * and X/100. These tests hold the arithmetic and the paper's shape in place.
 */
class PaperMarksheetTest {

    private fun school(components: List<AssessmentComponent>, marks: List<Mark>): SchoolData {
        var d = SchoolData()
        d = d.copy(school = d.school.copy(name = "Tooro Royal College SS"))
        val term = Term(id = "t1", yearId = "y1", number = 3)
        d = d.copy(academicYears = listOf(AcademicYear(id = "y1", year = "2026", terms = listOf(term), currentTermId = "t1")))
        d = d.copy(classes = listOf(SchoolClass(id = "c1", name = "S.3", level = Level.O_LEVEL)))
        d = d.copy(subjects = listOf(Subject(id = "s1", name = "Mathematics", code = "MATH", level = Level.O_LEVEL)))
        d = d.copy(components = components)
        d = d.copy(students = listOf(
            Student(id = "st1", firstName = "Amina", lastName = "Nabirye", admissionNo = "ADM-001", sex = "F"),
            Student(id = "st2", firstName = "Brian", lastName = "Okello", admissionNo = "ADM-002", sex = "M")
        ))
        d = d.copy(enrollments = listOf(
            Enrollment(id = "e1", studentId = "st1", academicYearId = "y1", classId = "c1"),
            Enrollment(id = "e2", studentId = "st2", academicYearId = "y1", classId = "c1")
        ))
        return d.copy(marks = marks)
    }

    private fun mark(student: String, component: String, score: Double, max: Int) = Mark(
        id = "m-$student-$component", studentId = student, subjectId = "s1", termId = "t1",
        componentId = component, score = score, type = MarkType.VALUE, maxScore = max
    )

    @Test fun `A1 A2 A3 average to X-20 and add to the exam for X-100`() {
        val marks = listOf(
            mark("st1", "c-a1", 15.0, 20), mark("st1", "c-a2", 18.0, 20), mark("st1", "c-a3", 12.0, 20),
            mark("st1", "c-eot", 64.0, 80)
        )
        val d = school(Seeds.COMPONENTS, marks)
        val bd = ResultEngine.subjectBreakdown(marks, d.components, 20, 80, weighted = true)
        assertEquals(listOf(75.0, 90.0, 60.0), bd.assessments, "each assessment prints as a percentage of 20")
        // X/20 = (15+18+12)/3 = 15 ; X/80 = 64 ; X/100 = 79
        val x20 = (15.0 + 18.0 + 12.0) / 3
        assertEquals(79.0, x20 + 64.0, 0.001)
        val (pct, _) = ResultEngine.subjectPercentage(marks, d.components, 20, 80, weighted = true)
        assertEquals(79.0, pct, 0.001, "20% of the assessment average plus 80% of the exam")
    }

    @Test fun `a school set up before A1-A3 gets them, empty, and its results do not move`() {
        val old = Seeds.COMPONENTS.filter { it.code !in setOf("A1", "A2", "A3") }
        val marks = listOf(mark("st1", "c-ca", 16.0, 20), mark("st1", "c-eot", 64.0, 80))
        val before = school(old, marks)
        val (pctBefore, _) = ResultEngine.subjectPercentage(marks, before.components, 20, 80, weighted = true)

        val after = before.withPaperAssessments()
        assertEquals(3, after.components.count { it.code in setOf("A1", "A2", "A3") })
        assertTrue(after.components.indexOfFirst { it.code == "A1" } < after.components.indexOfFirst { it.code == "CA" })
        val (pctAfter, _) = ResultEngine.subjectPercentage(after.marks, after.components, 20, 80, weighted = true)
        assertEquals(pctBefore, pctAfter, 0.001, "filling in empty columns must not change a result")

        // running it twice changes nothing
        assertEquals(after.components.size, after.withPaperAssessments().components.size)
    }

    @Test fun `the printed sheet is the paper, with names typed in and 33 numbered lines`() {
        val d = school(Seeds.COMPONENTS, emptyList())
        val out = Files.createTempFile("marksheet", ".docx")
        val headers = listOf("No.", "NAME", "A1", "A2", "A3", "X/20", "X/80", "X/100")
        DocxReport.writeMarkSheet(out, d, "S.3", "Mathematics", "2026 Term 3", headers,
            names = listOf("Amina Nabirye", "Brian Okello"))
        val xml = ZipFile(out.toFile()).use { z ->
            z.getInputStream(z.getEntry("word/document.xml")).readBytes().decodeToString()
        }
        assertTrue(xml.contains("Tooro Royal College SS"), "the sheet must carry the school's name")
        assertTrue(xml.contains("MARKSHEET"))
        assertTrue(xml.contains("CLASS: S.3") && xml.contains("SUBJECT: Mathematics"))
        assertTrue(xml.contains("Amina Nabirye") && xml.contains("Brian Okello"), "names are typed in")
        headers.forEach { assertTrue(xml.contains(it), "column $it must be on the sheet") }
        // the paper rules 33 lines: two names plus 31 blank numbered lines
        for (n in 1..33) assertTrue(xml.contains(">${n}.</w:t>"), "line $n must be numbered")
        assertTrue(!xml.contains(">34.</w:t>"), "no lines past the paper's 33")
    }
}
