package com.derycode.srs.core

import com.derycode.srs.core.model.*
import com.derycode.srs.core.report.DocxReport
import com.derycode.srs.core.report.ReportEngine
import com.derycode.srs.core.seed.Seeds
import com.derycode.srs.core.results.ResultEngine
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/** Report cards must always be A4, and must never print a fee balance. */
class ReportOutputTest {

    private fun fixture(): Triple<SchoolData, TermResult, String> {
        var d = SchoolData()
        d = d.copy(school = d.school.copy(name = "Test High School"), settings = d.settings.copy(currencySymbol = "UGX"))
        val year = AcademicYear(id = "y1", year = "2026")
        val term = Term(id = "t1", yearId = "y1", number = 1)
        d = d.copy(academicYears = listOf(year.copy(terms = listOf(term), currentTermId = "t1")))
        d = d.copy(classes = listOf(SchoolClass(id = "c1", name = "Senior 1", level = Level.O_LEVEL)))
        d = d.copy(subjects = listOf(Subject(id = "s1", name = "Mathematics", code = "MTC", level = Level.O_LEVEL)))
        d = d.copy(components = Seeds.COMPONENTS)
        d = d.copy(students = listOf(Student(id = "st1", firstName = "Amina", lastName = "Nabirye", admissionNo = "ADM-001", sex = "F")))
        d = d.copy(enrollments = listOf(Enrollment(id = "e1", studentId = "st1", academicYearId = "y1", classId = "c1")))
        val comp = Seeds.COMPONENTS.first().id
        d = d.copy(marks = listOf(Mark(id = "m1", studentId = "st1", subjectId = "s1", termId = "t1",
            componentId = comp, score = 78.0, maxScore = 100, type = MarkType.VALUE)))
        // a fee structure and a part payment: this must NOT appear on the report card
        d = d.copy(feeStructures = listOf(FeeStructure(id = "f1", classId = "c1", termId = "t1", category = FeeCategory.SCHOOL_FEES, amount = 500000.0)))
        d = d.copy(feePayments = listOf(FeePayment(id = "p1", studentId = "st1", termId = "t1", category = FeeCategory.SCHOOL_FEES, amount = 200000.0, date = 1767225600000L)))
        val result = ResultEngine.studentTermResult(d, "st1", "t1", schemeId = "g1", principalSubjectIds = listOf("s1"))
        return Triple(d, result, Compose.run { "" })
    }

    private object Compose { fun run(b: () -> String) = b() }

    @Test fun `docx report card is A4 and carries no fee balance`() {
        val (d, r, _) = fixture()
        val zip = Files.createTempFile("report", ".docx")
        Files.write(zip, DocxReport.studentReportBytes(d, r))
        val docXml = ZipFile(zip.toFile()).use { z -> z.getInputStream(z.getEntry("word/document.xml")).readBytes().decodeToString() }
        assertTrue(docXml.contains("""w:pgSz w:w="11906" w:h="16838""""), "page size must be A4 portrait")
        assertFalse(docXml.contains("Balance"), "no fee balance on the report card")
        assertFalse(docXml.contains("Paid to date"), "no fee paid column on the report card")
        assertTrue(docXml.contains("Subject Performance"), "academic content still present")
    }

    @Test fun `html report card prints on A4`() {
        val (d, r, _) = fixture()
        val html = ReportEngine.studentReport(d, r, TemplateLayout.CLASSIC)
        assertTrue(html.contains("size: A4 portrait"), "print page must be A4")
        assertFalse(html.contains("Balance"), "no fee balance in the HTML report")
        assertTrue(html.contains("page-break-inside: avoid"), "rows must not split across A4 sheets")
    }

    @Test fun `report renders even with no marks`() {
        val (d0, _, _) = fixture()
        val d = d0.copy(marks = emptyList())
        val r = ResultEngine.studentTermResult(d, "st1", "t1", schemeId = "g1", principalSubjectIds = listOf("s1"))
        val bytes = DocxReport.studentReportBytes(d, r)
        assertTrue(bytes.size > 500)
        val html = ReportEngine.studentReport(d, r, TemplateLayout.CLASSIC)
        assertTrue(html.contains("<html"))
    }
}
