import com.derycode.srs.admin.StaffImport
import com.derycode.srs.core.model.*
import org.junit.Assert.*
import org.junit.Test

/** The real Tooro Royal College staff list 2026 must import without hand fixing. */
class StaffImportTest {

    private val staffList = """
        ### TOORO ROYAL COLLEGE
        ### STAFF LIST 2026
        * Mwesige Francis — English (S1, S2, S3) — 0777109213
        * Wasukira Ashimu — ENT (S1, S2, S3) — 0779686470
        * Karungi Lucy — Agriculture (S1, S2, S3), Geography (S2) — 0777876671
        * Sande Joseph — ICT (S1, S2, S3) — 0771391894
        * Kyomuhendo Geofrey — History (S1, S2, S3), Geography (S3) — 0784042818
        * Mutegeki David — Chemistry (S1, S2), Mathematics (S1, S2) — 0764514681
        * Musinguzi Lodrick — Physics (S1, S2, S3), Mathematics (S3) — 0789097367
        * Kabakurasi Aida — CRE (S1, S2, S3), Geography (S1) — 0775201489
        * Kwikiriza Charles — Chemistry (S3), Biology (S1, S2, S3) — 0747624945
        * Komujuni Janet — Fine Art (S1, S2, S3) — 0779840486
        * Isingoma John — Crossed out on the original list
        * Ninsiima Agnes — Kiswahili (S1, S2, S3) — 0762030757
        * Kukundakwe Beda — Physical Education (S1, S2) — 0774124177
        Key: S1 = Senior One, S2 = Senior Two, S3 = Senior Three.
    """.trimIndent()

    @Test fun `every real teacher is read and the crossed out one is skipped`() {
        val plan = StaffImport.parse(staffList)
        assertEquals("12 teachers should import", 12, plan.entries.size)
        assertEquals(listOf("Isingoma John"), plan.skipped.map { it.first.substringAfter("* ").substringBefore(" —") })
        assertFalse(plan.entries.any { it.name.contains("Isingoma", true) })
    }

    @Test fun `subjects keep their own class lists`() {
        val plan = StaffImport.parse(staffList)
        val karungi = plan.entries.first { it.name == "Karungi Lucy" }
        assertEquals("0777876671", karungi.phone)
        assertEquals(listOf("Agriculture" to listOf("1","2","3"), "Geography" to listOf("2")), karungi.subjects)
    }

    @Test fun `phone numbers normalise to 07 style`() {
        assertEquals("0777109213", StaffImport.normalisePhone("+256 777 109213"))
        assertEquals("0777109213", StaffImport.normalisePhone("256777109213"))
        assertEquals("0777109213", StaffImport.normalisePhone("777109213"))
    }

    @Test fun `names are titled and variants match the existing teacher`() {
        assertEquals("Mwesige Francis", StaffImport.titleCaseName("MWESIGE FRANCIS"))
        val existing = Teacher(id = "t1", name = "Kyomuhendo Godfrey")
        assertNotNull("Geofrey and Godfrey are the same person",
            StaffImport.matchTeacher(listOf(existing), "Kyomuhendo Geofrey"))
        assertNull("a different surname is a different teacher",
            StaffImport.matchTeacher(listOf(Teacher(id = "t2", name = "Abigaba Joseph")), "Sande Joseph"))
    }

    @Test fun `applying creates staff with their subject and class assignments`() {
        val d = SchoolData(
            classes = (1..4).map { SchoolClass(id = "c$it", name = "Senior $it", level = Level.O_LEVEL) },
            subjects = listOf(
                Subject(id = "s-eng", name = "English", code = "ENG"),
                Subject(id = "s-geo", name = "Geography", code = "GEO"),
                Subject(id = "s-agri", name = "Agriculture", code = "AGRI"),
                Subject(id = "s-ent", name = "Entrepreneurship", code = "ENT"),
                Subject(id = "s-ict", name = "ICT", code = "ICT"),
                Subject(id = "s-hist", name = "History", code = "HIST"),
                Subject(id = "s-chem", name = "Chemistry", code = "CHEM"),
                Subject(id = "s-math", name = "Mathematics", code = "MATH"),
                Subject(id = "s-phy", name = "Physics", code = "PHY"),
                Subject(id = "s-cre", name = "CRE", code = "CRE"),
                Subject(id = "s-bio", name = "Biology", code = "BIO"),
                Subject(id = "s-art", name = "Fine Art", code = "ART"),
                Subject(id = "s-kis", name = "Kiswahili", code = "KISW"),
                Subject(id = "s-pe", name = "Physical Education", code = "PE")
            )
        )
        var n = 0
        val res = StaffImport.apply(d, StaffImport.parse(staffList)) { "id${n++}" }
        assertEquals(12, res.created.size)
        assertEquals("no unresolved subjects", emptyList<String>(), res.warnings)
        val mwesige = res.data.teachers.first { it.name == "Mwesige Francis" }
        assertEquals("0777109213", mwesige.phone)
        val english = res.data.assignments.filter { it.teacherId == mwesige.id && it.subjectId == "s-eng" }
        assertEquals("English in S1, S2 and S3", 3, english.size)
        // re-running must not duplicate anything
        val again = StaffImport.apply(res.data, StaffImport.parse(staffList)) { "x${n++}" }
        assertEquals(12, again.data.teachers.size)
        assertEquals(res.data.assignments.size, again.data.assignments.size)
    }
}
