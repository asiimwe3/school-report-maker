package com.derycode.srs.core

import com.derycode.srs.core.model.*
import com.derycode.srs.core.report.DocxReport
import com.derycode.srs.core.results.AssessmentWeighting
import com.derycode.srs.core.seed.Seeds
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The school's paper marksheet reads  No. | NAME | A1 | A2 | A3 | X/20 | X/80 | X/100,
 * and the final score is A2 × 20% + A3 × 80%. These tests hold the figures the
 * teacher sees, and the sheet that comes out of the printer, in place.
 */
class PaperMarksheetTest {

    private fun school(marks: List<Mark>): SchoolData {
        var d = SchoolData()
        d = d.copy(school = d.school.copy(name = "Tooro Royal College SS"))
        val term = Term(id = "t1", yearId = "y1", number = 3)
        d = d.copy(academicYears = listOf(AcademicYear(id = "y1", year = "2026", terms = listOf(term), currentTermId = "t1")))
        d = d.copy(classes = listOf(SchoolClass(id = "c1", name = "S.3", level = Level.O_LEVEL)))
        d = d.copy(subjects = listOf(Subject(id = "s1", name = "Mathematics", code = "MATH", level = Level.O_LEVEL)))
        d = d.copy(components = Seeds.COMPONENTS)
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

    private fun mark(student: String, code: String, score: Double) = Mark(
        id = "m-$student-$code", studentId = student, subjectId = "s1", termId = "t1",
        componentId = Seeds.COMPONENTS.first { it.code == code }.id,
        score = score, type = MarkType.VALUE, maxScore = 100
    )

    /** X/20 and X/80 as the sheet shows them: the share each assessment adds to the total. */
    private fun contribution(mark: Mark?, weight: Int): Double? =
        mark?.let { AssessmentWeighting.round1(AssessmentWeighting.percent(it) / 100.0 * weight) }

    @Test fun `A2 is 20 percent and A3 is 80 percent of the final score`() {
        val a2 = mark("st1", "A2", 70.0)
        val a3 = mark("st1", "A3", 80.0)
        val score = AssessmentWeighting.forMarks(listOf(a2, a3), Seeds.COMPONENTS)
        // 70 × 0.20 = 14 out of 20 ; 80 × 0.80 = 64 out of 80 ; total 78
        assertEquals(14.0, contribution(a2, 20)!!, 0.001)
        assertEquals(64.0, contribution(a3, 80)!!, 0.001)
        assertEquals(78.0, score.final!!, 0.001)
        assertEquals("78", AssessmentWeighting.display(score))
    }

    @Test fun `half a sheet shows what is there and a dash for the total`() {
        val onlyA2 = AssessmentWeighting.forMarks(listOf(mark("st2", "A2", 70.0)), Seeds.COMPONENTS)
        assertNull(onlyA2.final, "no total until both assessments are in")
        assertTrue(onlyA2.incomplete)
        assertEquals(listOf("A3"), onlyA2.missing)
        assertEquals("—", AssessmentWeighting.display(onlyA2))

        // a real zero is a score, not a missing mark
        val zeros = AssessmentWeighting.forMarks(
            listOf(mark("st2", "A2", 0.0), mark("st2", "A3", 0.0)), Seeds.COMPONENTS
        )
        assertEquals(0.0, assertNotNull(zeros.final), 0.001)
    }

    @Test fun `the school's own weights are used, not a hard-coded pair`() {
        val marks = listOf(mark("st1", "A2", 60.0), mark("st1", "A3", 90.0))
        val standard = AssessmentWeighting.forMarks(marks, Seeds.COMPONENTS)
        assertEquals(0.2 * 60 + 0.8 * 90, standard.final!!, 0.001)

        // a school that has not set weights on its own columns gets the school setting
        val bare = Seeds.COMPONENTS.map {
            if (it.code == "A2" || it.code == "A3") it.copy(weightPercent = 0) else it
        }
        val half = AssessmentWeighting.score(marks, AssessmentWeighting.rules(bare, 50, 50, true))
        assertEquals(75.0, half.final!!, 0.001, "60 and 90 at half each")
    }

    @Test fun `the printed sheet is the paper, with names typed in and 33 numbered lines`() {
        val d = school(emptyList())
        val out = Files.createTempFile("marksheet", ".docx")
        val headers = listOf("No.", "NAME", "A1", "A2", "A3", "X/20", "X/80", "X/100")
        DocxReport.writeMarkSheet(
            out = out, data = d, classLabel = "S.3", subjectName = "Mathematics",
            termLabel = "2026 Term 3", headers = headers,
            names = listOf("Amina Nabirye", "Brian Okello")
        )
        val xml = ZipFile(out.toFile()).use { z ->
            z.getInputStream(z.getEntry("word/document.xml")).readBytes().decodeToString()
        }
        assertTrue(xml.contains("Tooro Royal College SS"), "the sheet must carry the school's name")
        assertTrue(xml.contains("MARKSHEET"))
        assertTrue(xml.contains("CLASS: S.3") && xml.contains("SUBJECT: Mathematics"))
        assertTrue(xml.contains("Amina Nabirye") && xml.contains("Brian Okello"), "names are typed in")
        headers.forEach { assertTrue(xml.contains(it), "column $it must be on the sheet") }
        // the paper rules 33 lines, so the sheet rules 33 lines
        for (n in 1..33) assertTrue(xml.contains(">${n}.</w:t>"), "line $n must be numbered")
        assertTrue(!xml.contains(">34.</w:t>"), "no lines past the paper's 33")
    }
}
