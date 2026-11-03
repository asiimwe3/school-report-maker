import com.derycode.srs.admin.TimetableBulk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The timetable paste parser must read the paper grid exactly as teachers type it. */
class TimetableBulkTest {

    @Test fun `reads a normal time range`() {
        assertEquals("08:00" to "08:40", TimetableBulk.parseTime("08:00-08:40"))
        assertEquals("10:40" to "11:20", TimetableBulk.parseTime(" 10:40 - 11:20 "))
    }

    @Test fun `reads am pm ranges and en dashes`() {
        assertEquals("08:00" to "08:40", TimetableBulk.parseTime("8:00am-8:40am"))
        assertEquals("13:20" to "14:00", TimetableBulk.parseTime("1:20pm–2:00pm"))
        assertEquals("12:00" to "12:40", TimetableBulk.parseTime("12:00pm-12:40pm"))
    }

    @Test fun `single time assumes a forty minute period`() {
        assertEquals("10:20" to "11:00", TimetableBulk.parseTime("10:20"))
    }

    @Test fun `rejects text that is not a time`() {
        assertNull(TimetableBulk.parseTime("MONDAY"))
        assertNull(TimetableBulk.parseTime(""))
    }

    @Test fun `splits subject and teacher code`() {
        assertEquals("MATH" to "8", TimetableBulk.splitCell("MATH 8"))
        assertEquals("MATH" to "8", TimetableBulk.splitCell("MATH8"))
        assertEquals("ENG" to "2", TimetableBulk.splitCell("ENG 2"))
    }

    @Test fun `splits subject and teacher name`() {
        val (subj, teacher) = TimetableBulk.splitCell("ENG TR.WASUKIRA ASHIMU")
        assertEquals("ENG", subj)
        assertEquals("TR.WASUKIRA ASHIMU", teacher)
    }

    @Test fun `subject alone leaves the teacher blank`() {
        assertEquals("ICT" to "", TimetableBulk.splitCell("ICT"))
    }

    @Test fun `handles the paper's superscript style`() {
        // On the printed grid the code sits tight against the subject: "CHEM10", "AGRI3"
        assertEquals("CHEM" to "10", TimetableBulk.splitCell("CHEM10"))
        assertEquals("AGRI" to "3", TimetableBulk.splitCell("AGRI3"))
    }
}

/** Codes printed on real school sheets must land on the right subject. */
class TimetableSubjectAliasTest {

    private fun data(): com.derycode.srs.core.model.SchoolData {
        val d = com.derycode.srs.core.model.SchoolData()
        return d.copy(subjects = listOf(
            com.derycode.srs.core.model.Subject(id = "s-agri", name = "Agriculture", code = "AGRI"),
            com.derycode.srs.core.model.Subject(id = "s-math", name = "Mathematics", code = "MATH"),
            com.derycode.srs.core.model.Subject(id = "s-ent", name = "Entrepreneurship", code = "ENT")
        ))
    }

    private fun cls() = com.derycode.srs.core.model.SchoolClass(
        id = "c1", name = "Senior 1", level = com.derycode.srs.core.model.Level.O_LEVEL
    )

    @Test fun `SAGRI on the sheet is Agriculture`() {
        val s = TimetableBulk.resolveSubject(data(), cls(), "SAGRI")
        assertEquals("s-agri", s?.id)
        assertEquals("s-agri", TimetableBulk.resolveSubject(data(), cls(), "S/AGRI")?.id)
        assertEquals("s-agri", TimetableBulk.resolveSubject(data(), cls(), "AGRI")?.id)
    }

    @Test fun `ENT and ART column resolves to Entrepreneurship`() {
        assertEquals("s-ent", TimetableBulk.resolveSubject(data(), cls(), "ENT&ART")?.id)
    }

    @Test fun `plain codes still resolve`() {
        assertEquals("s-math", TimetableBulk.resolveSubject(data(), cls(), "MATH")?.id)
    }
}
