package com.derycode.srs.core

import com.derycode.srs.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A teacher who joins with the school code is usable immediately — no waiting for a role. */
class TeacherJoinTest {

    private fun school() = SchoolData(
        school = School(name = "Tooro Royal College"),
        classes = listOf(
            SchoolClass(id = "c1", name = "Senior 1", stream = "A", level = Level.O_LEVEL),
            SchoolClass(id = "c2", name = "Senior 2", stream = "B", level = Level.O_LEVEL),
            SchoolClass(id = "c3", name = "Senior 5", stream = "A", level = Level.A_LEVEL)
        ),
        subjects = listOf(
            Subject(id = "s1", name = "Mathematics", level = Level.O_LEVEL),
            Subject(id = "s2", name = "English", level = Level.O_LEVEL),
            Subject(id = "s3", name = "Physics", level = Level.A_LEVEL)
        ),
        students = listOf(Student(id = "st1", admissionNo = "1", firstName = "A", lastName = "B")),
        enrollments = listOf(Enrollment(id = "e1", studentId = "st1", classId = "c1", academicYearId = "y1"))
    )

    @Test
    fun joiningWithNothingPickedGivesTheWholeSchoolAtOnce() {
        val (data, id) = TeacherJoin.apply(school(), "Ainebyoona Grace", Role.TEACHER, phone = "0772000111").let { it.data to it.teacherId }
        val me = data.teachers.first { it.id == id }
        assertTrue(me.active, "joined teacher must be active")
        assertEquals(3, TeacherJoin.classesFor(data, id).size, "should teach every class")
        assertEquals(2, TeacherJoin.subjectsFor(data, id, "c1").size, "class-level assignment sees all O-level subjects")
        assertEquals(1, TeacherJoin.subjectsFor(data, id, "c3").size, "A-level class only sees A-level subjects")
    }

    @Test
    fun pickedClassAndSubjectIsHonouredExactly() {
        val j = TeacherJoin.apply(school(), "Kabakurasi Aida", Role.TEACHER, classIds = listOf("c1"), subjectIds = listOf("s1"))
        val data = j.data
        assertEquals(listOf("c1"), TeacherJoin.classesFor(data, j.teacherId).map { it.id })
        assertEquals(listOf("s1"), TeacherJoin.subjectsFor(data, j.teacherId, "c1").map { it.id })
        assertTrue(TeacherJoin.subjectsFor(data, j.teacherId, "c2").isEmpty() || TeacherJoin.classesFor(data, j.teacherId).none { it.id == "c2" })
    }

    @Test
    fun rejoiningDoesNotDuplicateTheTeacherOrTheirWork() {
        val first = TeacherJoin.apply(school(), "Tumusiime David", Role.DOS, phone = "0784504690", classIds = listOf("c1"))
        val second = TeacherJoin.apply(first.data, "tumusiime  david", Role.TEACHER, phone = "0784504690", classIds = listOf("c1", "c2"))
        assertTrue(!second.created, "a repeat join must link the existing record")
        assertEquals(1, second.data.teachers.count { it.name.equals("Tumusiime David", true) })
        val staff = second.data.teachers.first { it.id == second.teacherId }
        assertEquals(Role.DOS, staff.role, "a later join must not knock their role back to plain teacher")
        assertEquals(setOf("c1", "c2"), TeacherJoin.classesFor(second.data, staff.id).map { it.id }.toSet())
        assertEquals(second.data.assignments.size, second.data.assignments.distinctBy { it.classId to it.subjectId }.size,
            "no duplicate assignments")
    }

    @Test
    fun cloudUidLinksTheSamePhoneAccountAcrossReinstalls() {
        val a = TeacherJoin.apply(school(), "Ainebyoona Grace", Role.TEACHER, cloudUid = "uid-1")
        val b = TeacherJoin.apply(a.data, "Grace A.", Role.TEACHER, cloudUid = "uid-1")
        assertTrue(!b.created)
        assertEquals(1, b.data.teachers.size)
        assertNotNull(b.data.teachers.firstOrNull { it.cloudUid == "uid-1" })
    }
}
