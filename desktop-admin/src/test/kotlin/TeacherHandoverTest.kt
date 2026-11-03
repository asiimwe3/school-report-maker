import com.derycode.srs.admin.TeacherHandover
import com.derycode.srs.core.model.*
import org.junit.Assert.*
import org.junit.Test

/** Abigaba Joseph left; Sande Joseph takes over ICT without losing a period. */
class TeacherHandoverTest {

    private val school = SchoolData(
        teachers = listOf(
            Teacher(id = "abigaba", name = "Abigaba Joseph", phone = "0700000001"),
            Teacher(id = "sande", name = "Sande Joseph", phone = "0771391894")
        ),
        classes = listOf(
            SchoolClass(id = "c1", name = "Senior 1", classTeacherId = "abigaba"),
            SchoolClass(id = "c2", name = "Senior 2", classTeacherId = "sande")
        ),
        subjects = listOf(Subject(id = "s-ict", name = "ICT", code = "ICT")),
        assignments = listOf(
            TeacherAssignment(id = "a1", teacherId = "abigaba", subjectId = "s-ict", classId = "c1"),
            TeacherAssignment(id = "a2", teacherId = "abigaba", subjectId = "s-ict", classId = "c2"),
            TeacherAssignment(id = "a3", teacherId = "sande", subjectId = "s-ict", classId = "c2")  // already shared
        ),
        timetable = listOf(
            TimetablePeriod(id = "p1", classId = "c1", subjectId = "s-ict", teacherId = "abigaba"),
            TimetablePeriod(id = "p2", classId = "c2", subjectId = "s-ict", teacherId = "sande")
        ),
        dutyRecords = listOf(DutyRecord(id = "d1", teacherId = "abigaba", date = "2026-11-04"))
    )

    @Test fun `preview counts what will move`() {
        val p = TeacherHandover.preview(school, "abigaba")
        assertEquals(2, p.subjectLinks)
        assertEquals(1, p.timetablePeriods)
        assertEquals(listOf("Senior 1"), p.classTeacherOf)
        assertEquals(1, p.duties)
    }

    @Test fun `nothing is lost and nothing is doubled`() {
        var n = 0
        val r = TeacherHandover.apply(school, "abigaba", "sande") { "new$n" }

        // every period now belongs to Sande
        assertTrue(r.data.timetable.all { it.teacherId == "sande" })
        // Senior 1 class-teacher post moved, Senior 2 untouched
        assertEquals("sande", r.data.classes.first { it.id == "c1" }.classTeacherId)
        assertEquals("sande", r.data.classes.first { it.id == "c2" }.classTeacherId)
        // the shared S2 ICT link is not duplicated: Sande ends with 2 distinct links
        val sandeIct = r.data.assignments.filter { it.teacherId == "sande" && it.subjectId == "s-ict" }
        assertEquals(2, sandeIct.size)
        assertEquals(2, sandeIct.map { it.classId }.toSet().size)
        assertEquals(0, r.data.assignments.count { it.teacherId == "abigaba" })
        // duty roster follows
        assertEquals("sande", r.data.dutyRecords.single().teacherId)
        // leaver kept for history, access off; new teacher switched on
        assertFalse(r.data.teachers.first { it.id == "abigaba" }.active)
        assertTrue(r.data.teachers.first { it.id == "sande" }.active)
        assertEquals(2, r.data.teachers.size)
    }
}
