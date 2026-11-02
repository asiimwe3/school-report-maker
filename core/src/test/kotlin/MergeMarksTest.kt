package com.derycode.srs.core

import com.derycode.srs.core.model.*
import com.derycode.srs.core.store.JsonStore
import kotlin.test.Test
import kotlin.test.assertEquals

class MergeMarksTest {
    private fun mark(student: String, subject: String, term: String = "T1", component: String = "EXAM", score: Double, at: Long) =
        Mark(id = "$student|$subject|$term|$component", studentId = student, subjectId = subject, termId = term, componentId = component,
            type = MarkType.VALUE, score = score, updatedAt = at)

    @Test fun `importing one subject never deletes marks of other classes`() {
        val store = JsonStore()
        // school already has marks for Class A (S1 Maths) and Class B (S1 English)
        val existing = listOf(
            mark("s1", "maths", score = 80.0, at = 1),
            mark("s2", "maths", score = 70.0, at = 1),
            mark("s3", "english", score = 65.0, at = 1)
        )
        val data = SchoolData(marks = existing)

        // a teacher bundle arrives with maths marks only
        val incoming = listOf(mark("s1", "maths", score = 90.0, at = 10))

        val merged = store.mergeMarks(data, incoming)

        assertEquals(3, merged.size, "every existing mark must survive the import")
        assertEquals(90.0, merged.first { it.studentId == "s1" && it.subjectId == "maths" }.score, "newest wins")
        assertEquals(65.0, merged.first { it.studentId == "s3" && it.subjectId == "english" }.score, "other class untouched")
    }

    @Test fun `older bundle never overwrites newer local mark`() {
        val store = JsonStore()
        val data = SchoolData(marks = listOf(mark("s1", "maths", score = 88.0, at = 20)))
        val merged = store.mergeMarks(data, listOf(mark("s1", "maths", score = 40.0, at = 5)))
        assertEquals(88.0, merged.single().score)
    }

    @Test fun `brand new marks are added as well as kept`() {
        val store = JsonStore()
        val data = SchoolData(marks = listOf(mark("s1", "maths", score = 80.0, at = 1)))
        val merged = store.mergeMarks(data, listOf(mark("s9", "maths", score = 55.0, at = 2)))
        assertEquals(2, merged.size)
    }
}
