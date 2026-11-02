package com.derycode.srs.admin

import com.derycode.srs.core.model.Level
import com.derycode.srs.core.model.SchoolCategory
import com.derycode.srs.core.model.SchoolScope
import com.derycode.srs.core.model.standardClassesFor

/**
 * Switch the school between Primary, Secondary and Combined.
 *
 * The standard ladder for the chosen category is switched ON (P1–P7, or S1–S6
 * with S1–S4 at O-level and S5–S6 at A-level) and every class outside it is
 * switched OFF rather than deleted, so streams, teachers and marks that were
 * already entered for those classes are never destroyed — they simply leave the
 * console's working set. Switching back restores them untouched.
 */
fun applySchoolCategory(state: AppState, category: SchoolCategory) {
    val repo = state.repo
    val scope = SchoolScope(category)
    val allowed = scope.levels.toSet()
    val standard = standardClassesFor(category)
    val current = state.data.school.category.ifBlank { SchoolCategory.COMBINED.name }

    repo.mutate(
        action = "SCHOOL_CATEGORY_SET",
        entity = "School",
        entityId = state.data.school.id,
        old = current,
        new = category.name
    ) { d ->
        val onStandardLadder = d.classes.map { c ->
            val isStandardForThisCategory = standard.any { it.name == c.name && it.level == c.level }
            val keepId = standard.firstOrNull { it.name == c.name && it.level == c.level }?.id ?: c.id
            c.copy(id = keepId, active = c.level in allowed || isStandardForThisCategory)
        }
        val missing = standard.filter { std -> d.classes.none { it.name == std.name && it.level == std.level } }
        d.copy(
            school = d.school.copy(category = category.name),
            classes = onStandardLadder + missing
        )
    }
    // A school that no longer runs the current level jumps to one it does run.
    state.level = scope.coerce(state.level)
    state.refresh()
}

/** True when a class belongs to a level this school runs. */
fun AppState.inScope(level: Level): Boolean = scope.allows(level)
