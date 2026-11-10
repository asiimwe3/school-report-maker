package com.derycode.srs.core

import com.derycode.srs.core.entry.MarkLedger
import com.derycode.srs.core.model.Mark
import com.derycode.srs.core.model.MarkType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "The marks grid brings back old marks" — every case that produced it. */
class MarkLedgerTest {

    private fun mark(
        student: String = "s1",
        subject: String = "math",
        term: String = "t1",
        component: String = "a1",
        score: Double,
        updatedAt: Long = 0,
        type: MarkType = MarkType.VALUE
    ) = Mark(
        id = "m-$student-$subject-$component-${updatedAt}",
        studentId = student, subjectId = subject, termId = term,
        componentId = component, score = score, maxScore = 100,
        type = type, updatedAt = updatedAt
    )

    @Test fun `an edited mark is read back, not the copy beneath it`() {
        // Legacy duplicate: the old row sits first in the file, the edit after it.
        val marks = listOf(
            mark(score = 55.0, updatedAt = 1_000),
            mark(score = 78.0, updatedAt = 2_000)
        )
        assertEquals(78.0, MarkLedger.get(marks, "s1", "math", "t1", "a1")?.score)
    }

    @Test fun `upsert leaves exactly one row for the key`() {
        val marks = listOf(
            mark(score = 55.0, updatedAt = 1_000),
            mark(score = 78.0, updatedAt = 2_000),
            mark(student = "s2", score = 60.0, updatedAt = 1_500)
        )
        val after = MarkLedger.upsert(marks, mark(score = 82.0, updatedAt = 0), stamp = 3_000)
        assertEquals(2, after.size)
        assertEquals(82.0, MarkLedger.get(after, "s1", "math", "t1", "a1")?.score)
        assertEquals(60.0, MarkLedger.get(after, "s2", "math", "t1", "a1")?.score)
        assertFalse(MarkLedger.hasDuplicates(after))
    }

    @Test fun `the write is stamped so it always outranks what it replaced`() {
        val marks = listOf(mark(score = 55.0, updatedAt = 9_999))
        val after = MarkLedger.upsert(marks, mark(score = 61.0, updatedAt = 0), stamp = 10_000)
        assertEquals(10_000, MarkLedger.get(after, "s1", "math", "t1", "a1")?.updatedAt)
        assertEquals(61.0, MarkLedger.get(after, "s1", "math", "t1", "a1")?.score)
    }

    @Test fun `other components, subjects and learners are untouched`() {
        val marks = listOf(
            mark(component = "a1", score = 50.0, updatedAt = 1_000),
            mark(component = "a2", score = 40.0, updatedAt = 1_000),
            mark(subject = "eng", score = 70.0, updatedAt = 1_000),
            mark(student = "s9", score = 90.0, updatedAt = 1_000)
        )
        val after = MarkLedger.upsert(marks, mark(component = "a1", score = 88.0), stamp = 2_000)
        assertEquals(4, after.size)
        assertEquals(88.0, MarkLedger.get(after, "s1", "math", "t1", "a1")?.score)
        assertEquals(40.0, MarkLedger.get(after, "s1", "math", "t1", "a2")?.score)
        assertEquals(70.0, MarkLedger.get(after, "s1", "eng", "t1", "a1")?.score)
        assertEquals(90.0, MarkLedger.get(after, "s9", "math", "t1", "a1")?.score)
    }

    @Test fun `a missing mark is null, never a neighbour's score`() {
        val marks = listOf(mark(score = 55.0, updatedAt = 1_000))
        assertNull(MarkLedger.get(marks, "s2", "math", "t1", "a1"))
        assertNull(MarkLedger.get(marks, "s1", "math", "t2", "a1"))
    }

    @Test fun `ABS and EXEMPT still win as the newest value`() {
        val marks = listOf(mark(score = 55.0, updatedAt = 1_000))
        val after = MarkLedger.upsert(marks, mark(score = 0.0, updatedAt = 0, type = MarkType.ABS), stamp = 2_000)
        val standing = MarkLedger.get(after, "s1", "math", "t1", "a1")
        assertEquals(MarkType.ABS, standing?.type)
    }

    @Test fun `newestPerKey cleans a file that already carries duplicates`() {
        val marks = listOf(
            mark(score = 10.0, updatedAt = 100),
            mark(score = 20.0, updatedAt = 200),
            mark(score = 30.0, updatedAt = 150),
            mark(component = "a2", score = 40.0, updatedAt = 120)
        )
        assertTrue(MarkLedger.hasDuplicates(marks))
        val clean = MarkLedger.newestPerKey(marks)
        assertEquals(2, clean.size)
        assertFalse(MarkLedger.hasDuplicates(clean))
        assertEquals(20.0, MarkLedger.get(clean, "s1", "math", "t1", "a1")?.score)
        assertEquals(40.0, MarkLedger.get(clean, "s1", "math", "t1", "a2")?.score)
    }

    @Test fun `an empty ledger stays empty`() {
        assertTrue(MarkLedger.newestPerKey(emptyList()).isEmpty())
        assertFalse(MarkLedger.hasDuplicates(emptyList()))
    }
}
