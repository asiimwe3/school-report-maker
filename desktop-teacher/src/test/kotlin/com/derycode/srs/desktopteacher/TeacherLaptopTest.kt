package com.derycode.srs.desktopteacher

import com.derycode.srs.core.model.*
import com.derycode.srs.core.store.JsonStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The laptop copy has to behave exactly like the phone copy around other
 * schools, because the same files travel between them.
 */
class TeacherLaptopTest {

    private val store = JsonStore()

    private fun school(name: String, id: String): SchoolData {
        val cls = SchoolClass(id = "c-$id", name = "S.1", level = Level.O_LEVEL, active = true)
        val subject = Subject(id = "s-$id", name = "English", code = "ENG", level = Level.O_LEVEL, active = true)
        val teacher = Teacher(id = "t-$id", name = "Mwesige Francis")
        val student = Student(id = "st-$id", admissionNo = "A1", firstName = "Test", lastName = "Learner")
        val year = AcademicYear(
            id = "y1", year = "2026", currentTermId = "term1",
            terms = listOf(Term(id = "term1", yearId = "y1", number = 1))
        )
        return SchoolData(
            school = School(id = id, name = name),
            classes = listOf(cls), subjects = listOf(subject), teachers = listOf(teacher),
            students = listOf(student),
            enrollments = listOf(Enrollment(id = "e-$id", studentId = student.id, academicYearId = year.id, classId = cls.id)),
            academicYears = listOf(year),
            assignments = listOf(TeacherAssignment(id = "a-$id", teacherId = teacher.id, classId = cls.id, subjectId = subject.id)),
            components = listOf(AssessmentComponent(id = "comp", code = "EOT", name = "EOT", active = true))
        )
    }

    private fun writeSchool(dir: Path, data: SchoolData, file: String = "school-data-from-admin.json"): Path {
        val p = dir.resolve(file)
        store.save(p, data)
        return p
    }

    @Test
    fun a_laptop_starts_empty_and_then_learns_the_school_from_the_admin_file() {
        val root = Files.createTempDirectory("srs-laptop")
        val state = TeacherStore(root)
        assertFalse(state.connected, "a brand new laptop must not look connected")

        val admin = Files.createTempDirectory("srs-admin")
        val file = writeSchool(admin, school("Tooro Royal College", "school-a"))
        val (ok, msg) = state.importSchoolSetup(file)

        assertTrue(ok, msg)
        assertEquals("Tooro Royal College", state.data.school.name)
        assertEquals(1, state.studentsIn("c-school-a").size)
    }

    @Test
    fun a_teacher_marks_a_learner_and_the_console_can_read_the_bundle_back() {
        val root = Files.createTempDirectory("srs-laptop-marks")
        val state = TeacherStore(root)
        val admin = Files.createTempDirectory("srs-admin-marks")
        state.importSchoolSetup(writeSchool(admin, school("Tooro Royal College", "school-a")))
        state.setMe("t-school-a")

        state.upsertMark(
            Mark(id = "m1", studentId = "st-school-a", subjectId = "s-school-a",
                termId = "term1", componentId = "comp", score = 72.0, maxScore = 100,
                enteredBy = state.meId)
        )
        assertEquals(72.0, state.markFor("st-school-a", "s-school-a", "term1", "comp")?.score)

        val (ok, path) = state.exportBundle()
        assertTrue(ok, "the export must succeed once a mark exists")
        val payload = Files.readString(path!!)
        val bundle = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<JsonStore.Bundle>(payload)
        assertEquals("DERYCODE-SRS-BUNDLE", bundle.format, "the console only imports its own bundle format")
        assertEquals("Tooro Royal College", bundle.schoolName, "the bundle must say which school it came from")
        assertEquals(1, bundle.marks.size, "the learner's mark must be inside the file")
        assertEquals(72.0, bundle.marks.first().score)
    }

    @Test
    fun another_schools_file_never_lands_on_top_of_this_schools_marks() {
        val root = Files.createTempDirectory("srs-laptop-switch")
        val state = TeacherStore(root)
        val admin = Files.createTempDirectory("srs-admin-switch")
        state.importSchoolSetup(writeSchool(admin, school("Tooro Royal College", "school-a")))
        state.upsertMark(Mark(id = "m1", studentId = "st-school-a", subjectId = "s-school-a",
            termId = "term1", componentId = "comp", score = 55.0))

        val other = writeSchool(admin, school("Kyenjojo High School", "school-b"), "other-school.json")
        val (ok, msg) = state.importSchoolSetup(other)

        assertFalse(ok, "a different school's file must be refused by a plain import")
        assertTrue(msg.contains("Kyenjojo"), "the message must name the school in the file: $msg")
        assertEquals("Tooro Royal College", state.data.school.name, "the laptop must still hold its own school")
        assertEquals(1, state.data.marks.size, "its marks must still be there")
    }

    @Test
    fun switching_school_is_explicit_and_carries_nothing_across() {
        val root = Files.createTempDirectory("srs-laptop-move")
        val state = TeacherStore(root)
        val admin = Files.createTempDirectory("srs-admin-move")
        state.importSchoolSetup(writeSchool(admin, school("Tooro Royal College", "school-a")))
        state.upsertMark(Mark(id = "m1", studentId = "st-school-a", subjectId = "s-school-a",
            termId = "term1", componentId = "comp", score = 55.0))
        state.setMe("t-school-a")

        val other = writeSchool(admin, school("Kyenjojo High School", "school-b"), "other-school.json")
        val (ok, msg) = state.switchSchool(other)

        assertTrue(ok, msg)
        assertEquals("Kyenjojo High School", state.data.school.name)
        assertTrue(state.data.marks.isEmpty(), "the old school's marks must not travel to the new school")
        assertEquals(null, state.meId, "the teacher must be picked again for the new school")
    }
}
