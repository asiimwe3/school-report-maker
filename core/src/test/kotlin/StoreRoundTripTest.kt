package com.derycode.srs.core

import com.derycode.srs.core.store.SchoolRepository
import com.derycode.srs.core.store.JsonStore
import com.derycode.srs.core.model.*
import com.derycode.srs.core.seed.Seeds
import java.nio.file.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreRoundTripTest {
    @Test fun `full admin flow persists to disk`() {
        val dir = Files.createTempDirectory("srs-test")
        val file = dir.resolve("school-data.json")
        val repo = SchoolRepository(JsonStore(), file, deviceId = "test")
        repo.seedIfNeeded(Seeds.ALL_SUBJECTS, Seeds.COMPONENTS)
        repo.mutate("SCHOOL_UPDATED", "School") { it.copy(school = it.school.copy(name = "Test SS")) }
        val yid = repo.nextId()
        repo.mutate("YEAR_CREATED", "AcademicYear", yid, new = "2026") { it.copy(academicYears = it.academicYears + AcademicYear(id = yid, year = "2026")) }
        val terms = (1..3).map { n -> Term(id = repo.nextId(), yearId = yid, number = n) }
        repo.mutate("YEAR_TERMS_ADDED", "AcademicYear", yid) { dd ->
            dd.copy(academicYears = dd.academicYears.map { if (it.id == yid) it.copy(terms = terms, currentTermId = terms.first().id) else it })
        }
        val cid = repo.nextId()
        repo.mutate("CLASS_ADDED", "SchoolClass", cid) { it.copy(classes = it.classes + SchoolClass(id = cid, name = "Senior 1", level = Level.O_LEVEL)) }
        val sid = repo.nextId()
        repo.mutate("STUDENT_ADDED", "Student", sid) { dd ->
            dd.copy(students = dd.students + Student(id = sid, admissionNo = "001", firstName = "Jane", lastName = "Doe"),
                enrollments = dd.enrollments + Enrollment(id = repo.nextId(), studentId = sid, academicYearId = yid, classId = cid))
        }
        val subj = repo.data.subjects.first { it.active }.id
        val comp = repo.data.components.first().id
        repo.upsertMark(Mark(id = repo.nextId(), studentId = sid, subjectId = subj, termId = terms[0].id, componentId = comp, score = 87.0, maxScore = 100))
        repo.upsertMark(Mark(id = repo.nextId(), studentId = sid, subjectId = subj, termId = terms[0].id, componentId = comp, score = 91.0, maxScore = 100))
        assertEquals(1, repo.data.marks.size)

        val d2 = SchoolRepository(JsonStore(), file).data
        assertEquals("Test SS", d2.school.name)
        assertEquals(Seeds.DEFAULT_CLASSES.size + 1, d2.classes.size)
        assertEquals(1, d2.students.size)
        assertEquals(1, d2.marks.size)
        assertEquals(91.0, d2.marks.first().score)
        assertTrue(d2.auditLog.isNotEmpty())
    }
}
