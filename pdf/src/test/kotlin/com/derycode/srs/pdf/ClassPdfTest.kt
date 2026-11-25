package com.derycode.srs.pdf

import com.derycode.srs.core.model.*
import com.derycode.srs.core.results.ResultEngine
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Print all" must hand a head teacher one PDF for the whole class, with a page
 * per learner — not a Word file and not a browser print dialog.
 */
class ClassPdfTest {

    private fun data(): SchoolData {
        val school = School(name = "Tooro Royal College", motto = "Together for a Brighter Future",
            phone = "0771478274", email = "tooro@gmail.com", address = "KYENJOJO")
        val year = AcademicYear(id = "y1", year = "2026", currentTermId = "t1", terms = listOf(
            Term(id = "t1", yearId = "y1", number = 3, endDate = "2026-11-28", nextTermBegins = "2027-02-01")))
        val cls = SchoolClass(id = "c1", name = "Senior 1", stream = "", level = Level.O_LEVEL)
        val students = listOf(
            Student(id = "s1", admissionNo = "TRC1001", firstName = "Ahaisibwe", lastName = "Tom"),
            Student(id = "s2", admissionNo = "TRC1002", firstName = "Kembabazi", lastName = "Amina")
        )
        val subjects = listOf(
            Subject(id = "sub1", name = "Mathematics", code = "MATH", level = Level.O_LEVEL),
            Subject(id = "sub2", name = "English", code = "ENG", level = Level.O_LEVEL)
        )
        val comps = listOf(
            AssessmentComponent(id = "k-bot", code = "BOT", name = "Beginning of term", weightPercent = 20),
            AssessmentComponent(id = "k-eot", code = "EOT", name = "End of term", weightPercent = 80)
        )
        val scheme = GradingScheme(id = "g1", name = "UCE", level = Level.O_LEVEL,
            boundaries = listOf(
                GradeBoundary(80.0, 100.0, "A", "Exceptional", 1, "Exceptional"),
                GradeBoundary(60.0, 79.0, "C", "Satisfactory", 3, "Satisfactory"),
                GradeBoundary(0.0, 59.0, "E", "Elementary", 5, "Elementary")),
            aggregateRule = AggregateRule.UCE_INDICATOR)
        // Tom sat both subjects; Amina sat English only, so Mathematics must print a dash for her.
        val marks = listOf(
            Mark(id = "m1", studentId = "s1", subjectId = "sub1", termId = "t1", componentId = "k-bot", score = 80.0),
            Mark(id = "m2", studentId = "s1", subjectId = "sub1", termId = "t1", componentId = "k-eot", score = 80.0),
            Mark(id = "m3", studentId = "s1", subjectId = "sub2", termId = "t1", componentId = "k-bot", score = 60.0),
            Mark(id = "m4", studentId = "s1", subjectId = "sub2", termId = "t1", componentId = "k-eot", score = 60.0),
            Mark(id = "m5", studentId = "s2", subjectId = "sub2", termId = "t1", componentId = "k-bot", score = 70.0),
            Mark(id = "m6", studentId = "s2", subjectId = "sub2", termId = "t1", componentId = "k-eot", score = 70.0)
        )
        return SchoolData(
            school = school, classes = listOf(cls), students = students, subjects = subjects,
            components = comps, marks = marks, gradingSchemes = listOf(scheme),
            academicYears = listOf(year),
            enrollments = students.mapIndexed { i, s ->
                Enrollment(id = "e$i", studentId = s.id, classId = "c1", academicYearId = "y1")
            }
        )
    }

    @Test
    fun aWholeClassIsOnePdfWithOnePagePerLearner() {
        val d = data()
        val results = ResultEngine.classResults(d, "c1", "t1", "g1")
        assertEquals(2, results.size, "the class should produce two results")

        val out = Files.createTempFile("class-cards", ".pdf")
        PdfReport.writeClassReport(out, d, results)

        val bytes = Files.readAllBytes(out)
        assertTrue(bytes.size > 2_000, "the PDF is suspiciously small: ${bytes.size} bytes")
        assertEquals("%PDF", String(bytes.copyOfRange(0, 4)), "not a PDF file")

        PDDocument.load(out.toFile()).use { doc ->
            assertEquals(2, doc.numberOfPages, "one page per learner")
            val text = PDFTextStripper().apply { startPage = 1; endPage = 2 }.getText(doc)
            assertTrue(text.contains("TOORO ROYAL COLLEGE"), "the school header is missing")
            assertTrue(text.contains("AHAISIBWE TOM"), "the first learner is missing")
            assertTrue(text.contains("KEMBABAZI AMINA"), "the second learner is missing")
            assertTrue(text.contains("MATHEMATICS"), "the marks table is missing")
            // Amina never sat Mathematics: a dash, not 0
            val aminaPage = PDFTextStripper().apply { startPage = 2; endPage = 2 }.getText(doc)
            assertTrue(aminaPage.contains("\u2014"), "an unmarked subject must print a dash")
            // comments and initials are never blank
            assertTrue(text.contains("MD") && text.contains("MF"), "initials are missing: $text")
        }
    }

    @Test
    fun fifteenSubjectsAllPrintOnOneBalancedPage() {
        val d = data()
        // a real Senior 1 class sits about fifteen subjects
        val many = (1..15).map { i ->
            Subject(id = "sub$i", name = "Subject$i", code = "S$i", level = Level.O_LEVEL)
        }
        val withMany = d.copy(
            subjects = many,
            marks = many.flatMapIndexed { i, s ->
                listOf(
                    Mark(id = "b$i", studentId = "s1", subjectId = s.id, termId = "t1", componentId = "k-bot", score = 70.0),
                    Mark(id = "e$i", studentId = "s1", subjectId = s.id, termId = "t1", componentId = "k-eot", score = 70.0)
                )
            }
        )
        val results = ResultEngine.classResults(withMany, "c1", "t1", "g1").filter { it.studentId == "s1" }
        val out = Files.createTempFile("class-15", ".pdf")
        PdfReport.writeClassReport(out, withMany, results)

        PDDocument.load(out.toFile()).use { doc ->
            assertEquals(1, doc.numberOfPages, "fifteen subjects still fit one A4 page")
            val text = PDFTextStripper().getText(doc)
            (1..15).forEach { i ->
                assertTrue(text.contains("SUBJECT$i"), "subject $i was dropped from the card")
            }
            assertTrue(text.contains("AVERAGE SCORE"), "the average row is missing")
            assertTrue(text.contains("CLASS TEACHER"), "the signature block is missing")
        }
    }

    @Test
    fun aTinyClassStillFillsThePageEvenly() {
        val d = data().copy(subjects = listOf(Subject(id = "sub1", name = "Mathematics", code = "MATH", level = Level.O_LEVEL)))
        val results = ResultEngine.classResults(d, "c1", "t1", "g1").take(1)
        val out = Files.createTempFile("class-1", ".pdf")
        PdfReport.writeClassReport(out, d, results)
        PDDocument.load(out.toFile()).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val text = PDFTextStripper().getText(doc)
            assertTrue(text.contains("MATHEMATICS"))
            assertTrue(text.contains("HEAD TEACHER"), "the footer block must still sit at the bottom")
        }
    }

}
