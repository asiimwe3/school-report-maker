package com.derycode.srs.core.entry

import com.derycode.srs.core.model.Mark

/**
 * One mark per learner, per subject, per term, per assessment — the newest one.
 *
 * Field bug, November 2026: "when changes are made the marks grid brings back old
 * marks". A teacher retyped a mark, the grid showed the old number again, and the
 * report printed the old number too.
 *
 * The cause was reading with `marks.firstOrNull { key matches }`. A key can exist
 * more than once: an older build appended a second row instead of replacing the
 * first, a bundle import added a row with a different id but the same key, and the
 * same edit saved twice in one session did it too. `firstOrNull` then returned
 * whichever row happened to come first in the list, which was usually the OLD one,
 * while the edit sat further down the list.
 *
 * So: reads go through [get] (newest updatedAt wins) and writes through [upsert]
 * (every row for that key is replaced), and [newestPerKey] cleans up files that
 * already carry duplicates.
 */
object MarkLedger {

    fun key(studentId: String, subjectId: String, termId: String, componentId: String): String =
        "$studentId|$subjectId|$termId|$componentId"

    fun key(m: Mark): String = key(m.studentId, m.subjectId, m.termId, m.componentId)

    /** The mark that stands for this key: newest `updatedAt`, later row on a tie. */
    fun get(
        marks: List<Mark>,
        studentId: String,
        subjectId: String,
        termId: String,
        componentId: String
    ): Mark? {
        val wanted = key(studentId, subjectId, termId, componentId)
        var best: Mark? = null
        for (m in marks) {
            if (key(m) != wanted) continue
            val current = best
            if (current == null || m.updatedAt >= current.updatedAt) best = m
        }
        return best
    }

    /** One row per key — legacy duplicates collapsed to the newest. File order is preserved. */
    fun newestPerKey(marks: List<Mark>): List<Mark> {
        val winners = LinkedHashMap<String, Mark>(marks.size)
        for (m in marks) {
            val k = key(m)
            val current = winners[k]
            if (current == null || m.updatedAt >= current.updatedAt) winners[k] = m
        }
        return winners.values.toList()
    }

    /**
     * Writes a mark: every row sharing its key disappears and the stamped row takes
     * their place, so an edit can never be shadowed by a leftover copy. Marks for
     * other keys are returned untouched and in their original order.
     */
    fun upsert(marks: List<Mark>, incoming: Mark, stamp: Long = System.currentTimeMillis()): List<Mark> {
        val k = key(incoming)
        val stamped = incoming.copy(updatedAt = stamp)
        return marks.filterNot { key(it) == k } + stamped
    }

    /** True when this list carries more than one row for some key. */
    fun hasDuplicates(marks: List<Mark>): Boolean =
        marks.size != marks.map { key(it) }.toSet().size
}
