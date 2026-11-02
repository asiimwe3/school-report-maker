package com.derycode.srs.core.model

/**
 * What kind of school this install belongs to.
 *
 * Uganda has three shapes that matter to the console: a primary school (P1–P7),
 * a secondary school (S1–S6, split O-level S1–S4 and A-level S5–S6) and a
 * combined school that runs both. The choice decides which classes, students,
 * subjects, teachers and timetable periods the console shows, so a secondary
 * school never sees Primary 1 sitting next to Senior 6.
 */
enum class SchoolCategory {
    PRIMARY, SECONDARY, COMBINED;

    val label: String get() = when (this) {
        PRIMARY -> "Primary school"
        SECONDARY -> "Secondary school"
        COMBINED -> "Primary & secondary"
    }

    /** One-line description used under the setting. */
    val detail: String get() = when (this) {
        PRIMARY -> "P1 – P7, PLE grading"
        SECONDARY -> "S1 – S6, O-level S1–S4 and A-level S5–S6, UCE / UACE grading"
        COMBINED -> "Both ladders in one school — switch level in the top bar"
    }
}

/**
 * The levels a school actually runs, plus the helpers every screen uses to
 * filter itself. Built from [School.category]; an empty category (older data)
 * is treated as COMBINED so nothing disappears from an existing install.
 */
data class SchoolScope(val category: SchoolCategory) {

    val levels: List<Level> get() = when (category) {
        SchoolCategory.PRIMARY -> listOf(Level.PRIMARY)
        SchoolCategory.SECONDARY -> listOf(Level.O_LEVEL, Level.A_LEVEL)
        SchoolCategory.COMBINED -> listOf(Level.PRIMARY, Level.O_LEVEL, Level.A_LEVEL)
    }

    val isPrimaryOnly: Boolean get() = category == SchoolCategory.PRIMARY
    val isSecondaryOnly: Boolean get() = category == SchoolCategory.SECONDARY

    fun allows(level: Level): Boolean = level in levels

    /** Falls back to the first level the school runs when the current one is not allowed. */
    fun coerce(level: Level): Level = if (allows(level)) level else levels.first()

    fun classes(all: List<SchoolClass>, includeInactive: Boolean = false): List<SchoolClass> =
        all.filter { allows(it.level) && (includeInactive || it.active) }

    fun subjects(all: List<Subject>): List<Subject> = all.filter { allows(it.level) }

    /** "Secondary school · O-level S1–S4 and A-level S5–S6" — for settings/headers. */
    val summary: String get() = when (category) {
        SchoolCategory.PRIMARY -> "Primary · P1–P7"
        SchoolCategory.SECONDARY -> "Secondary · O-level S1–S4 · A-level S5–S6"
        SchoolCategory.COMBINED -> "Combined · P1–P7 and S1–S6"
    }

    companion object {
        fun of(school: School): SchoolScope = fromStored(school.category)

        fun fromStored(raw: String): SchoolScope = when (raw.trim().uppercase()) {
            "PRIMARY", "P", "PLE" -> SchoolScope(SchoolCategory.PRIMARY)
            "SECONDARY", "S", "O_LEVEL", "A_LEVEL", "SEC" -> SchoolScope(SchoolCategory.SECONDARY)
            else -> SchoolScope(SchoolCategory.COMBINED)
        }
    }
}

/** The standard class ladder for a category, so a secondary school is seeded S1–S6 only. */
fun standardClassesFor(category: SchoolCategory): List<SchoolClass> = buildList {
    if (category != SchoolCategory.SECONDARY) {
        (1..7).forEach { n ->
            add(SchoolClass(id = "cls-p$n", name = "Primary $n", level = Level.PRIMARY))
        }
    }
    if (category != SchoolCategory.PRIMARY) {
        (1..4).forEach { n ->
            add(SchoolClass(id = "cls-s$n", name = "Senior $n", level = Level.O_LEVEL))
        }
        (5..6).forEach { n ->
            add(SchoolClass(id = "cls-s$n", name = "Senior $n", level = Level.A_LEVEL))
        }
    }
}

/** Short ladder label shown next to a level, e.g. "S1–S4". */
fun Level.shortRange(): String = when (this) {
    Level.PRIMARY -> "P1–P7"
    Level.O_LEVEL -> "S1–S4"
    Level.A_LEVEL -> "S5–S6"
}

/** Report/exam name of the level in Ugandan usage. */
fun Level.ladderName(): String = when (this) {
    Level.PRIMARY -> "Primary"
    Level.O_LEVEL -> "O-level"
    Level.A_LEVEL -> "A-level"
}
