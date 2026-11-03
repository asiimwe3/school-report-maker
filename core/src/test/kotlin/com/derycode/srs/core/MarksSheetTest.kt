package com.derycode.srs.core

import com.derycode.srs.core.entry.MarksSheet
import com.derycode.srs.core.entry.ParsedRow
import com.derycode.srs.core.entry.RosterMatcher
import com.derycode.srs.core.entry.SheetColumn
import com.derycode.srs.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarksSheetTest {

    private val ca = SheetColumn("c-ca", "CA", 20)
    private val eot = SheetColumn("c-eot", "EOT", 80)
    private val students = listOf(
        Student(id = "s1", admissionNo = "TRC1012", firstName = "Ruhanga", middleName = "Ayebale", lastName = "John Paul"),
        Student(id = "s2", admissionNo = "TRC1013", firstName = "Kaba", lastName = "Aida"),
        Student(id = "s3", admissionNo = "TRC1014", firstName = "Ssempewo", lastName = "Joseph")
    )

    @Test
    fun templateListsEveryLearnerWithAColumnPerAssessment() {
        val csv = MarksSheet.templateCsv(
            school = School(name = "Tooro Royal College"),
            klass = SchoolClass(id = "c1", name = "Senior 1", level = Level.O_LEVEL),
            subject = Subject(id = "sub-eng", name = "English", code = "ENG"),
            termLabel = "2026 Term 2",
            students = students,
            columns = listOf(ca, eot)
        )
        val lines = csv.lines().filter { it.isNotBlank() }
        assertTrue(lines.first().startsWith("#SRS|Tooro Royal College|Senior 1|English|2026 Term 2"))
        assertTrue(lines.any { it.startsWith("ADM NO,NAME,CA (out of 20),EOT (out of 80)") })
        assertEquals(3, lines.count { it.startsWith("TRC") })
    }

    @Test
    fun aFilledTemplateReadsBackWithItsMarks() {
        val csv = """
            #SRS|Tooro Royal College|Senior 1|English|2026 Term 2
            ADM NO,NAME,CA (out of 20),EOT (out of 80)
            TRC1012,"Ruhanga Ayebale John Paul",16,35
            TRC1013,Kaba Aida,12,ABS
        """.trimIndent()
        val rows = MarksSheet.parseCsv(csv, listOf(ca, eot))
        assertEquals(2, rows.size)
        assertEquals("16", rows[0].valueFor("c-ca"))
        assertEquals("35", rows[0].valueFor("c-eot"))
        assertEquals("ABS", rows[1].valueFor("c-eot"))
        assertEquals("TRC1013", rows[1].admissionNo)
    }

    @Test
    fun aPhotoOfAClassListGivesNamesAndMarks() {
        val lines = listOf(
            "1. RUHANGA AYEBALE JOHN PAUL 16",
            "2 KABA AIDA 12",
            "3 SSEMPEWO JOSEPH ABS",
            "Senior 1 A - English CA"
        )
        val rows = MarksSheet.parseScan(lines, listOf(ca))
        assertEquals(3, rows.size)
        assertEquals("RUHANGA AYEBALE JOHN PAUL", rows[0].nameText)
        assertEquals("16", rows[0].valueFor("c-ca"))
        assertEquals("ABS", rows[2].valueFor("c-ca"))
    }

    @Test
    fun namesAreMatchedHoweverTheRegisterIsWritten() {
        fun row(text: String, adm: String = "") = ParsedRow(nameText = text, admissionNo = adm)
        assertEquals("s1", RosterMatcher.best(row("Ruhanga Ayebale John Paul"), students)?.id)
        assertEquals("s1", RosterMatcher.best(row("JOHN PAUL RUHANGA"), students)?.id)
        assertEquals("s1", RosterMatcher.best(row("RUHANGA AYEBALE J P"), students)?.id)
        assertEquals("s1", RosterMatcher.best(row("TRC1012 RUHANGA", adm = "TRC1012"), students)?.id)
        assertEquals("s3", RosterMatcher.best(row("SSEMPEWO JOSEPH"), students)?.id)
        assertEquals("s2", RosterMatcher.best(row("KABA AIDA"), students)?.id)
    }

    @Test
    fun aNameThatIsNotOnTheRegisterIsNotForced() {
        assertEquals(null, RosterMatcher.best(ParsedRow(nameText = "MUGISHA BRIAN"), students))
    }
}
