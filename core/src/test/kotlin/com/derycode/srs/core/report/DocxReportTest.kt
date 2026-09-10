package com.derycode.srs.core.report

import com.derycode.srs.core.model.*
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertTrue

class DocxReportTest {
    @Test
    fun `student report is a valid docx zip with Word document inside`() {
        val data = SchoolData(
            school = School(name = "Test Primary School", motto = "Excel", address = "Kyenjojo", phone = "0762306675"),
            subjects = listOf(Subject(id = "sub-mat", name = "Mathematics", code = "MAT", level = Level.PRIMARY)),
            students = listOf(Student(id = "st-1", admissionNo = "2026/1", firstName = "Asiimwe", lastName = "Derick")),
            gradingSchemes = listOf(com.derycode.srs.core.grading.GradingSchemes.PLE)
        )
        val result = TermResult(
            studentId = "st-1", termId = "t-1",
            subjectResults = listOf(SubjectResult(subjectId = "sub-mat", total = 78.0, percentage = 78.0, grade = "D1", points = 1, remark = "Good")),
            averagePercent = 78.0, aggregate = 5, division = "Division One", classPosition = 1
        )
        val tmp = Files.createTempDirectory("srs-docx").resolve("report.docx")
        DocxReport.writeStudentReport(tmp, data, result)

        assertTrue(tmp.toFile().length() > 1000, "docx too small — likely empty")
        ZipFile(tmp.toFile()).use { zip ->
            assertTrue(zip.getEntry("word/document.xml") != null, "missing word/document.xml")
            assertTrue(zip.getEntry("[Content_Types].xml") != null, "missing [Content_Types].xml")
            val xml = zip.getInputStream(zip.getEntry("word/document.xml")).readBytes().decodeToString()
            assertTrue(xml.contains("Test Primary School"), "school name missing")
            assertTrue(xml.contains("Mathematics"), "subject row missing")
            assertTrue(xml.contains("Aggregate 5"), "aggregate missing")
            assertTrue(xml.contains("<w:tbl>"), "no table markup")
        }
    }
}
