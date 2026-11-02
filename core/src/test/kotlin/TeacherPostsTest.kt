package com.derycode.srs.core

import com.derycode.srs.core.model.*
import com.derycode.srs.core.store.JsonStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Custom posts and duties the admin gives a teacher must survive save, load and old files. */
class TeacherPostsTest {

    @Test fun `role title and duties survive a save and reload`() {
        val dir = Files.createTempDirectory("srs-posts")
        val file = dir.resolve("school-data.json")
        val store = JsonStore()
        var d = SchoolData(school = School(name = "Test SS"))
        d = d.copy(teachers = listOf(
            Teacher(id = "t1", name = "Okello Peter", role = Role.DOS, roleTitle = "Director of Studies",
                duties = listOf("Director of Studies", "Timetable", "Patron — Debate")),
            Teacher(id = "t2", name = "Nabirye Amina", role = Role.TEACHER, duties = listOf("Class Teacher"))
        ))
        store.save(file, d)

        val back = store.load(file)
        val t1 = back.teachers.first { it.id == "t1" }
        assertEquals("Director of Studies", t1.roleTitle)
        assertEquals(Role.DOS, t1.role)
        assertEquals(3, t1.duties.size)
        assertTrue(t1.duties.contains("Timetable"))
        assertEquals(listOf("Class Teacher"), back.teachers.first { it.id == "t2" }.duties)
    }

    @Test fun `an older data file without duties still loads`() {
        val dir = Files.createTempDirectory("srs-legacy")
        val file = dir.resolve("school-data.json")
        // exactly what v2.2.38 wrote: no roleTitle, no duties
        Files.writeString(file, """
        {
          "school": { "name": "Old School" },
          "teachers": [ { "id": "t1", "name": "Bwambale John", "role": "TEACHER", "active": true } ]
        }
        """.trimIndent())
        val d = JsonStore().load(file)
        assertEquals("Old School", d.school.name)
        assertEquals("Bwambale John", d.teachers.single().name)
        assertTrue(d.teachers.single().duties.isEmpty())
        assertEquals("", d.teachers.single().roleTitle)
    }

    @Test fun `every duty preset is plain text and unique`() {
        val duties = listOf("Director of Studies", "Deputy Head Teacher", "Class Teacher", "Games Master",
            "Examination Officer", "Discipline", "Patron — Clubs", "Timekeeper", "Library", "Sanitation",
            "Stores", "Chaplaincy", "Transport", "Boarding", "Guidance & Counselling", "ICT / Computer Lab", "Sports Captain")
        assertEquals(duties.size, duties.distinct().size)
        assertTrue(duties.all { it.isNotBlank() })
    }
}
