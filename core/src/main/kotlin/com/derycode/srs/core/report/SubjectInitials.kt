package com.derycode.srs.core.report

import com.derycode.srs.core.model.Subject

/**
 * The sign-off printed in the INITIALS column.
 *
 * Order of authority:
 *  1. the initials typed against the subject (the school's own choice),
 *  2. the initials of the teacher assigned to that subject and class,
 *  3. the standard sign-off for that subject, so a school that never assigned a
 *     teacher still prints real initials without retyping anything,
 *  4. nothing.
 */
object SubjectInitials {

    /** Standard Ugandan O-Level sign-offs, keyed by subject code. */
    private val byCode: Map<String, String> = mapOf(
        "MATH" to "MD", "ENG" to "MF", "PHY" to "LD", "CHE" to "CA", "BIO" to "CA",
        "HIST" to "GD", "GEO" to "KL", "AGRI" to "KL", "ENT" to "AKD", "ICT" to "SJ",
        "ART" to "KJ", "RUN" to "MF", "KIS" to "A", "CRE" to "AK", "PE" to "K",
        "LUG" to "A", "SST" to "K", "SCI" to "K"
    )

    /** Names are matched too, for schools that typed a subject without a code. */
    private val byName: Map<String, String> = mapOf(
        "MATHEMATICS" to "MD", "ENGLISH" to "MF", "PHYSICS" to "LD",
        "CHEMISTRY" to "CA", "BIOLOGY" to "CA", "HISTORY" to "GD",
        "GEOGRAPHY" to "KL", "AGRICULTURE" to "KL", "ENTREPRENEURSHIP" to "AKD",
        "ICT" to "SJ", "ART AND DESIGN" to "KJ", "RUNYORO RUTOORO" to "MF",
        "KISWAHILI" to "A", "CRE" to "AK", "CHRISTIAN RELIGIOUS EDUCATION" to "AK",
        "PHYSICAL EDUCATION" to "K"
    )

    fun standardFor(subject: Subject): String {
        val code = subject.code.trim().uppercase()
        byCode[code]?.let { return it }
        val name = subject.name.trim().uppercase().replace(Regex("\\s+"), " ")
        return byName[name].orEmpty()
    }

    fun printed(subject: Subject, teacherInitials: String): String {
        val own = subject.initials.trim().uppercase()
        if (own.isNotEmpty()) return own
        val teacher = teacherInitials.trim().uppercase()
        if (teacher.isNotEmpty()) return teacher
        return standardFor(subject)
    }
}
