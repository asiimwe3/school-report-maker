package com.derycode.srs.core.model

import java.util.UUID

/**
 * A teacher who joins a school with the code is a member of staff from that
 * moment. Nobody has to assign them a role, a class or a subject afterwards:
 * their own staff record and assignments are created on the spot, which is what
 * unlocks marks entry, the class register, reports, duty and the rest.
 *
 * The console still lets the head teacher change the role or classes later; this
 * only removes the "waiting to be set up" state.
 */
object TeacherJoin {

    data class Joined(
        val data: SchoolData,
        val teacherId: String,
        /** True when this join created a new staff record, false when it linked an existing one. */
        val created: Boolean
    )

    private fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * Puts [name] on staff with [role] and gives them the classes and subjects they
     * picked. Empty picks mean the whole school (every active class, every subject),
     * so a teacher can always start working the moment they scan.
     */
    fun apply(
        d: SchoolData,
        name: String,
        role: Role = Role.TEACHER,
        phone: String = "",
        cloudUid: String = "",
        roleTitle: String = "",
        classIds: List<String> = emptyList(),
        subjectIds: List<String> = emptyList(),
        photoPath: String? = null
    ): Joined {
        val cleanName = name.trim().ifBlank { "Teacher" }
        val wanted = norm(cleanName)

        // link to an existing staff record when we clearly have the same person
        val existing = d.teachers.firstOrNull { t -> cloudUid.isNotBlank() && t.cloudUid == cloudUid }
            ?: d.teachers.firstOrNull { t -> norm(t.name) == wanted }
            ?: d.teachers.firstOrNull { t -> phone.isNotBlank() && t.phone.filter { it.isDigit() } == phone.filter { it.isDigit() } }

        val teacher = existing?.copy(
            // the name already on the roster wins when it is the same person, so a
            // join never rewrites "Tumusiime David" into how it was typed on a phone
            name = if (norm(existing.name) == wanted) existing.name else cleanName,
            phone = phone.trim().ifBlank { existing.phone },
            role = if (role != Role.TEACHER) role else existing.role,
            roleTitle = roleTitle.trim().ifBlank { existing.roleTitle },
            photoPath = photoPath ?: existing.photoPath,
            cloudUid = cloudUid.ifBlank { existing.cloudUid },
            active = true                      // joined staff can work straight away
        ) ?: Teacher(
            id = "tch-" + UUID.randomUUID().toString().take(8),
            name = cleanName,
            phone = phone.trim(),
            role = role,
            roleTitle = roleTitle.trim(),
            cloudUid = cloudUid,
            photoPath = photoPath,
            active = true
        )

        val teachers = if (existing == null) d.teachers + teacher
        else d.teachers.map { if (it.id == existing.id) teacher else it }

        // classes: what they picked, otherwise every class the school runs
        val classes = d.classes.filter { it.active }
        val chosenClasses = classIds.mapNotNull { id -> classes.firstOrNull { it.id == id } }
            .ifEmpty { classes }
        val chosenSubjects = d.subjects.filter { it.active && it.id in subjectIds }

        val additions = buildList {
            chosenClasses.forEach { c ->
                if (chosenSubjects.isEmpty()) {
                    // no subject picked: they teach their class's subjects
                    add(TeacherAssignment(id = "asg-${teacher.id}-${c.id}-all", teacherId = teacher.id, subjectId = null, classId = c.id))
                } else {
                    chosenSubjects.filter { it.level == c.level }.forEach { s ->
                        add(TeacherAssignment(id = "asg-${teacher.id}-${c.id}-${s.id}", teacherId = teacher.id, subjectId = s.id, classId = c.id))
                    }
                }
            }
        }
        val held = d.assignments.filter { it.teacherId == teacher.id }
            .map { it.classId to it.subjectId }.toSet()
        val fresh = additions.filter { (it.classId to it.subjectId) !in held }

        return Joined(
            data = d.copy(teachers = teachers, assignments = d.assignments + fresh),
            teacherId = teacher.id,
            created = existing == null
        )
    }

    /** Classes this teacher can work in, using the same rule as the phone app. */
    fun classesFor(d: SchoolData, teacherId: String): List<SchoolClass> =
        d.assignments.filter { it.teacherId == teacherId }
            .mapNotNull { a -> d.classes.firstOrNull { it.id == a.classId && it.active } }
            .distinctBy { it.id }

    /** Subjects this teacher teaches in [classId]; a class-level assignment means all of them. */
    fun subjectsFor(d: SchoolData, teacherId: String, classId: String): List<Subject> {
        val cls = d.classes.firstOrNull { it.id == classId }
        val base = d.subjects.filter { it.level == cls?.level && it.active }
        val mine = d.assignments.filter { it.teacherId == teacherId && it.classId == classId }
        if (mine.any { it.subjectId == null }) return base
        val ids = mine.mapNotNull { it.subjectId }.toSet()
        return base.filter { it.id in ids }
    }
}
