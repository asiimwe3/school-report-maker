package com.derycode.srs.core.seed

import com.derycode.srs.core.model.AssessmentComponent
import com.derycode.srs.core.model.CommentTemplate
import com.derycode.srs.core.model.Level
import com.derycode.srs.core.model.Subject

/**
 * Starter configurations only (Section 59) — schools edit or replace these.
 * The calculation engine makes no assumptions about them.
 */
object Seeds {

    val COMPONENTS: List<AssessmentComponent> = listOf(
        AssessmentComponent(id = "c-bot", code = "BOT", name = "Beginning of Term", levels = listOf(Level.O_LEVEL, Level.A_LEVEL)),
        AssessmentComponent(id = "c-mid", code = "MID", name = "Mid Term", levels = listOf(Level.O_LEVEL, Level.A_LEVEL)),
        AssessmentComponent(id = "c-eot", code = "EOT", name = "End of Term", levels = listOf(Level.PRIMARY, Level.O_LEVEL, Level.A_LEVEL)),
        AssessmentComponent(id = "c-ca", code = "CA", name = "Continuous Assessment", weightPercent = 20, levels = listOf(Level.O_LEVEL, Level.A_LEVEL)),
        AssessmentComponent(id = "c-aoi", code = "AOI", name = "Activity of Integration", levels = listOf(Level.O_LEVEL)),
        AssessmentComponent(id = "c-proj", code = "PROJECT", name = "Project Work", levels = listOf(Level.O_LEVEL, Level.A_LEVEL))
    )

    fun subjectsFor(level: Level): List<Subject> = when (level) {
        Level.PRIMARY -> listOf(
            "Mathematics" to "MATH", "English" to "ENG", "Science" to "SCI",
            "Social Studies" to "SST", "Integrated Science" to "ISC", "Religious Education" to "CRE"
        ).map { (n, c) -> Subject(id = "sub-${c.lowercase()}", name = n, code = c, level = level, maxMarks = 100) }
        Level.O_LEVEL -> listOf(
            "Mathematics" to "MATH", "English" to "ENG", "Physics" to "PHY", "Chemistry" to "CHE",
            "Biology" to "BIO", "History" to "HIST", "Geography" to "GEO", "Agriculture" to "AGRI",
            "Entrepreneurship" to "ENT", "Luganda" to "LUG"
        ).map { (n, c) -> Subject(id = "sub-${c.lowercase()}", name = n, code = c, level = level, maxMarks = 100) }
        Level.A_LEVEL -> listOf(
            "Mathematics" to "MATH", "Physics" to "PHY", "Chemistry" to "CHE", "Biology" to "BIO",
            "Economics" to "ECON", "Geography" to "GEO", "History" to "HIST", "Divinity" to "DIV",
            "Literature" to "LIT", "General Paper" to "GP", "Sub-ICT" to "ICT"
        ).map { (n, c) ->
            Subject(
                id = "sub-${c.lowercase()}", name = n, code = c, level = level,
                principal = c !in listOf("GP", "ICT"),          // GP & Sub-ICT are subsidiary
                subsidiary = c in listOf("GP", "ICT")
            )
        }
    }

    val COMMENT_TEMPLATES: List<CommentTemplate> = listOf(
        CommentTemplate("cb-t1", "TEACHER", "A hardworking and disciplined student. Keep it up!"),
        CommentTemplate("cb-t2", "TEACHER", "Has improved greatly this term. Continue with the same spirit."),
        CommentTemplate("cb-t3", "TEACHER", "Bright but needs to be more attentive in class."),
        CommentTemplate("cb-t4", "TEACHER", "Quiet and cooperative. Participate more in class discussions."),
        CommentTemplate("cb-t5", "TEACHER", "Performance dropped this term — more revision at home is advised."),
        CommentTemplate("cb-t6", "TEACHER", "Excellent leader among peers. Very well behaved."),
        CommentTemplate("cb-t7", "TEACHER", "Capable of much better results if attendance and effort improve."),
        CommentTemplate("cb-t8", "TEACHER", "Punctual, neat and polite. A good example to others."),
        CommentTemplate("cb-h1", "HEAD", "Promoted to the next class. We are proud of your progress."),
        CommentTemplate("cb-h2", "HEAD", "A good result. Aim even higher next term."),
        CommentTemplate("cb-h3", "HEAD", "Must work harder to realize full potential."),
        CommentTemplate("cb-h4", "HEAD", "Excellent performance. Keep the school flag flying."),
        CommentTemplate("cb-h5", "HEAD", "Parents are advised to support revision at home."),
        CommentTemplate("cb-h6", "HEAD", "Report to school with a parent/guardian at opening of next term.")
    )

    val ALL_SUBJECTS: List<Subject> = Level.entries.flatMap(::subjectsFor)
}
