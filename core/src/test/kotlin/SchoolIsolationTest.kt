import com.derycode.srs.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** One school's rows must never surface inside another school. */
class SchoolIsolationTest {

    private fun school(name: String, id: String = "school", students: Int = 0, marks: Int = 0): SchoolData {
        val kids = (1..students).map { Student(id = "$name-stu$it", admissionNo = "A$it", firstName = "Learner$it", lastName = name) }
        val ms = (1..marks).map {
            Mark(id = "$name-m$it", studentId = "$name-stu$it", subjectId = "s-eng", termId = "t1", componentId = "c1", score = 70.0)
        }
        return SchoolData(
            school = School(id = id, name = name),
            students = kids,
            marks = ms,
            classes = listOf(SchoolClass(id = "c1", name = "Senior 1")),
            subjects = listOf(Subject(id = "s-eng", name = "English", code = "ENG"))
        )
    }

    @Test fun `two schools sharing the default id are still different schools`() {
        val a = school("Tooro Royal College")
        val b = school("Kyenjojo High School")
        assertFalse(SchoolIsolation.sameSchool(a, b))
        assertNotEquals(SchoolIsolation.identity(a), SchoolIsolation.identity(b))
    }

    @Test fun `a renamed school with the same real id is still the same school`() {
        val before = school("Tooro Royal S S", id = "qr-9f2")
        val after = school("Tooro Royal College", id = "qr-9f2")
        assertTrue(SchoolIsolation.sameSchool(before, after))
    }

    @Test fun `a blank school name never counts as the same school`() {
        assertFalse(SchoolIsolation.sameSchool(SchoolData(), school("Tooro Royal College")))
    }

    @Test fun `joining another school carries none of the old marks`() {
        val mine = school("Tooro Royal College", students = 3, marks = 3)
        val theirs = school("Kyenjojo High School", students = 2, marks = 1)
        val adopted = SchoolIsolation.adopt(theirs, mine)
        assertEquals(1, adopted.marks.size)
        assertTrue(adopted.marks.all { it.studentId.startsWith("Kyenjojo") })
        assertEquals("Kyenjojo High School", adopted.school.name)
    }

    @Test fun `re-pulling the same school keeps the marks typed on the phone`() {
        val mine = school("Tooro Royal College", students = 2, marks = 2)
        val console = school("Tooro Royal College", students = 2, marks = 1)
        val adopted = SchoolIsolation.adopt(console, mine)
        assertEquals(2, adopted.marks.size, "the console mark plus the phone's own")
        assertTrue(adopted.marks.any { it.id == "Tooro Royal College-m2" })
    }

    @Test fun `a stray mark for an unknown learner is dropped and never sent`() {
        val d = school("Tooro Royal College", students = 2, marks = 2)
        val withStray = d.copy(marks = d.marks + Mark(id = "x", studentId = "someone-else-stu9", subjectId = "s-eng", termId = "t1", componentId = "c1"))
        assertEquals(2, SchoolIsolation.scopedMarks(withStray).size)
        val (clean, removed) = SchoolIsolation.prune(withStray)
        assertEquals(1, removed)
        assertEquals(2, clean.marks.size)
    }

    @Test fun `pruning only touches learner rows never the school's own setup`() {
        val d = school("Tooro Royal College", students = 1, marks = 1).copy(
            comments = listOf(
                Comment(id = "k1", studentId = "Tooro Royal College-stu1", termId = "t1", type = CommentType.CLASS_TEACHER, text = "Good work"),
                Comment(id = "k2", studentId = "foreign-stu", termId = "t1", type = CommentType.CLASS_TEACHER, text = "Good work")
            ),
            attendance = listOf(
                AttendanceRecord(id = "a1", studentId = "foreign-stu", classId = "c1", date = "2026-11-03", status = "PRESENT")
            )
        )
        val (clean, removed) = SchoolIsolation.prune(d)
        assertEquals(2, removed)
        assertEquals(1, clean.comments.size)
        assertTrue(clean.attendance.isEmpty())
        assertEquals(1, clean.classes.size)
        assertEquals(1, clean.subjects.size)
        assertEquals(1, clean.students.size)
    }
}
