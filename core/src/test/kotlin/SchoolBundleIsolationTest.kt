import com.derycode.srs.core.model.*
import com.derycode.srs.core.store.JsonStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A real round trip: school A's teacher sends a bundle, school B's console must
 * end up with nothing of A — not a mark, not a learner.
 */
class SchoolBundleIsolationTest {

    private fun student(id: String, surname: String) =
        Student(id = id, admissionNo = id, firstName = "Learner", lastName = surname)

    private fun mark(id: String, studentId: String) =
        Mark(id = id, studentId = studentId, subjectId = "s-eng", termId = "t1", componentId = "c1", score = 66.0)

    private val tooro = SchoolData(
        school = School(id = "school", name = "Tooro Royal College"),
        students = listOf(student("t-stu1", "Tooro")),
        marks = listOf(mark("t-m1", "t-stu1"))
    )
    private val kyenjojo = SchoolData(
        school = School(id = "school", name = "Kyenjojo High School"),
        students = listOf(student("k-stu1", "Kyenjojo"))
    )

    @Test fun `a bundle from another school contributes nothing it does not own`() {
        val store = JsonStore()
        val dir = Files.createTempDirectory("srs-bundle")
        val bundle = dir.resolve("bundle.json")

        // Tooro's teacher sends their marks
        store.exportBundle(bundle, "Tooro Royal College", "teacher-cloud",
            marks = tooro.marks, students = tooro.students)

        // Kyenjojo's console is told it is a bundle from Tooro, so it refuses it
        val bundleSchool = store.peekBundleSchool(bundle)
        val fromThisSchool = bundleSchool.isBlank() || SchoolIsolation.sameSchool(
            SchoolData(school = School(name = bundleSchool)), kyenjojo
        )
        assertEquals("Kyenjojo High School", kyenjojo.school.name)
        assertEquals(false, fromThisSchool, "the console must refuse a bundle from another school")

        // and even if the marks alone slipped in (its learners not accepted), pruning
        // drops every row that points at a learner Kyenjojo does not have
        val report = store.importBundle(kyenjojo, bundle)
        val merged = kyenjojo.copy(marks = report.merged)
        val (clean, removed) = SchoolIsolation.prune(merged)
        assertTrue(clean.marks.none { it.id == "t-m1" }, "Tooro's mark must not survive in Kyenjojo")
        assertEquals(0, clean.marks.size)
        // Since v2.3.31 the importer drops a foreign learner's rows on the way in
        // (it will not re-point them by admission number), so there is nothing left
        // for prune to remove — either way nothing of Tooro's reaches the grid.
        assertTrue(removed >= 0)
        assertTrue(merged.marks.isEmpty(), "the foreign rows must be refused at import, not merely hidden")
    }

    @Test fun `the same school's bundle is accepted and its marks survive`() {
        val store = JsonStore()
        val dir = Files.createTempDirectory("srs-bundle")
        val bundle = dir.resolve("bundle.json")
        store.exportBundle(bundle, "Tooro Royal College", "teacher-cloud",
            marks = tooro.marks, students = tooro.students)

        val bundleSchool = store.peekBundleSchool(bundle)
        assertTrue(SchoolIsolation.sameSchool(
            SchoolData(school = School(name = bundleSchool)), tooro
        ), "a teacher from this school must be accepted")

        val report = store.importBundle(tooro, bundle)
        val (clean, _) = SchoolIsolation.prune(tooro.copy(marks = report.merged, students = report.mergedStudents))
        assertTrue(clean.marks.any { it.id == "t-m1" })
    }
}
