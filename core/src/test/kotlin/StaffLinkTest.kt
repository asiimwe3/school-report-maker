package com.derycode.srs.core

import com.derycode.srs.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Field bug, November 2026: teachers opened the app and saw no classes and no
 * learners. The phone's teacher id had gone stale when the console exported a
 * fresh setup, and nothing put them back on the staff list.
 */
class StaffLinkTest {

    private fun school(): SchoolData {
        var d = SchoolData()
        d = d.copy(school = d.school.copy(id = "sch-1", name = "Tooro Royal College SS"))
        d = d.copy(classes = listOf(
            SchoolClass(id = "c-s3", name = "S.3", level = Level.O_LEVEL),
            SchoolClass(id = "c-s4", name = "S.4", level = Level.O_LEVEL)
        ))
        d = d.copy(subjects = listOf(Subject(id = "sub-math", name = "Mathematics", code = "MATH", level = Level.O_LEVEL)))
        d = d.copy(teachers = listOf(
            Teacher(id = "tch-wasu", name = "Wasukira Ashimu", phone = "0772000111"),
            Teacher(id = "tch-naba", name = "Nabaasa Grace", phone = "0700555222")
        ))
        d = d.copy(assignments = listOf(
            TeacherAssignment(id = "a1", teacherId = "tch-naba", classId = "c-s4")
        ))
        return d
    }

    @Test fun `a stale id is relinked to the same person by name`() {
        val r = StaffLink.resolve(school(), meId = "tch-from-an-old-export", name = "Mr. Wasukira Ashimu")
        assertEquals("tch-wasu", r.teacherId)
        assertTrue(r.changed)
        // and they can work: a teacher with no assignment rows gets their classes
        assertEquals(2, r.data.assignments.count { it.teacherId == "tch-wasu" })
        assertTrue(r.note.isNotBlank())
    }

    @Test fun `a teacher who already has classes keeps exactly those classes`() {
        val r = StaffLink.resolve(school(), meId = "tch-naba", name = "Nabaasa Grace")
        assertEquals("tch-naba", r.teacherId)
        assertEquals(listOf("c-s4"), r.data.assignments.filter { it.teacherId == "tch-naba" }.map { it.classId })
        assertTrue(!r.changed)
        assertEquals("", r.note)
    }

    @Test fun `picked classes are used when the phone still knows them`() {
        val r = StaffLink.resolve(
            school(), meId = "tch-gone", name = "Wasukira Ashimu", classIds = listOf("c-s4")
        )
        assertEquals("tch-wasu", r.teacherId)
        assertEquals(listOf("c-s4"), r.data.assignments.filter { it.teacherId == "tch-wasu" }.map { it.classId }.distinct())
    }

    @Test fun `phone number links a teacher whose name was typed differently`() {
        val r = StaffLink.resolve(school(), meId = null, name = "Wasukira A.", phone = "+256 772 000 111")
        assertEquals("tch-wasu", r.teacherId)
    }

    @Test fun `an unknown name is never guessed`() {
        val r = StaffLink.resolve(school(), meId = null, name = "Someone Else")
        assertNull(r.teacherId, "the app must ask, not invent a staff record")
        assertNotNull(r.note)
    }

    @Test fun `a single-teacher school needs no guessing either`() {
        val one = school().let { it.copy(teachers = it.teachers.take(1)) }
        val r = StaffLink.resolve(one, meId = "stale")
        assertEquals("tch-wasu", r.teacherId)
    }
}
