package com.derycode.srs.core.model

/**
 * Putting a teacher back on the staff list.
 *
 * A phone remembers the teacher by an id. That id goes stale the moment the head
 * teacher's console exports a fresh setup: the console builds its own staff ids,
 * a join builds another, and an id that no longer exists in the file leaves the
 * teacher staring at an empty app - no classes, no learners, nothing to type into.
 *
 * So the id is treated as a hint, never as the truth. When it no longer matches,
 * the teacher is found again by cloud id, then by phone number, then by name, and
 * the phone is told which record is really theirs. Someone with no assignment
 * rows at all is given their classes rather than shown an empty timetable, because
 * a teacher who can see nothing cannot ask for help from inside the app.
 */
object StaffLink {

    data class Result(
        val data: SchoolData,
        /** The staff record this phone belongs to, or null when nobody could be found. */
        val teacherId: String?,
        /** True when the staff list had to change (a record created or relinked). */
        val changed: Boolean = false,
        /** True when assignment rows were created so the teacher can actually work. */
        val gaveClasses: Boolean = false,
        /** Plain words for the teacher, shown on the screen that needed the answer. */
        val note: String = ""
    )

    private fun norm(s: String) = s.trim().lowercase()
        .replace(Regex("[.,]"), "")
        .replace(Regex("\\s+"), " ")
    private fun digits(s: String) = s.filter { it.isDigit() }.takeLast(9)

    /** Names survive "Mr." vs "Mr", capitals, double spaces and swapped word order. */
    private fun sameName(a: String, b: String): Boolean {
        if (norm(a) == norm(b)) return true
        val x = norm(a).split(" ").filter { it.length > 1 && it !in setOf("mr", "mrs", "ms", "miss", "sir", "madam") }.toSet()
        val y = norm(b).split(" ").filter { it.length > 1 && it !in setOf("mr", "mrs", "ms", "miss", "sir", "madam") }.toSet()
        if (x.isEmpty() || y.isEmpty()) return false
        // every word of the shorter name appears in the longer one
        val (short, long) = if (x.size <= y.size) x to y else y to x
        return long.containsAll(short)
    }

    private fun find(d: SchoolData, meId: String?, name: String, phone: String, cloudUid: String): Teacher? {
        meId?.let { id -> d.teachers.firstOrNull { it.id == id } }?.let { return it }
        if (cloudUid.isNotBlank()) d.teachers.firstOrNull { it.cloudUid.isNotBlank() && it.cloudUid == cloudUid }?.let { return it }
        if (phone.filter { it.isDigit() }.length >= 9) {
            d.teachers.firstOrNull { digits(it.phone) == digits(phone) && digits(phone).length >= 9 }?.let { return it }
        }
        if (name.isNotBlank()) d.teachers.firstOrNull { sameName(it.name, name) }?.let { return it }
        return null
    }

    /**
     * Resolves the phone's teacher and, when they have been left with nothing to do,
     * gives them the classes they picked when they joined (or the whole school).
     */
    fun resolve(
        d: SchoolData,
        meId: String?,
        name: String = "",
        phone: String = "",
        cloudUid: String = "",
        classIds: List<String> = emptyList(),
        subjectIds: List<String> = emptyList()
    ): Result {
        if (d.teachers.isEmpty() && d.classes.isEmpty()) return Result(d, null)

        val found = find(d, meId, name, phone, cloudUid)
            // a school with a single teacher is not ambiguous
            ?: d.teachers.singleOrNull()

        if (found == null) {
            // nobody matches: never guess, let the app ask which name is theirs
            val why = if (d.teachers.isEmpty()) "" else "Your staff record is not on this phone yet. Tap your name to continue."
            return Result(d, null, note = why)
        }

        var data = d
        var changed = false
        // the record exists but this phone's id points somewhere else: relink by joining
        if (meId != found.id) {
            val joined = TeacherJoin.apply(
                d, found.name, found.role, phone = found.phone.ifBlank { phone },
                cloudUid = found.cloudUid.ifBlank { cloudUid },
                classIds = classIds, subjectIds = subjectIds
            )
            data = joined.data
            changed = true
        }

        val mine = data.assignments.filter { it.teacherId == found.id }
        var gaveClasses = false
        if (mine.isEmpty() && data.classes.any { it.active }) {
            val joined = TeacherJoin.apply(
                data, found.name, found.role, phone = found.phone.ifBlank { phone },
                cloudUid = found.cloudUid.ifBlank { cloudUid },
                classIds = classIds, subjectIds = subjectIds
            )
            data = joined.data
            gaveClasses = true
            changed = true
        }

        val count = data.assignments.filter { it.teacherId == found.id }
            .map { it.classId }.distinct().size
        val note = when {
            gaveClasses -> "Your classes were not on this phone, so they have been added. Ask the head teacher to confirm them."
            changed -> "You were found again on the staff list: $count class(es)."
            else -> ""
        }
        return Result(data, found.id, changed = changed, gaveClasses = gaveClasses, note = note)
    }
}
