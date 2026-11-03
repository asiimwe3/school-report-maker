package com.derycode.srs.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.srs.core.model.Level
import com.derycode.srs.core.model.SchoolCategory
import com.derycode.srs.core.model.SchoolData
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

/** One-time question for installs that predate the category setting. */
@Composable
fun SchoolCategoryPrompt(state: AppState) {
    Box(Modifier.fillMaxSize().background(Theme.NAVY), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 760.dp).background(Theme.CARD, RoundedCornerShape(18.dp)).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("What kind of school is this?", color = Theme.TEXT, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(
                "The console will only show your own classes. A secondary school never sees Primary 1\u20137, and a primary school never sees Senior 1\u20136. Nothing is deleted, and you can change this later in School Setup.",
                color = Theme.MUTED, fontSize = 13.sp
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SchoolCategory.entries.forEach { cat ->
                    Column(
                        Modifier.weight(1f)
                            .background(Theme.ACCENT_SOFT, RoundedCornerShape(14.dp))
                            .border(1.dp, Theme.LINE, RoundedCornerShape(14.dp))
                            .clickable { applySchoolCategory(state, cat) }
                            .padding(18.dp)
                    ) {
                        Text(cat.label, color = Theme.ACCENT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(cat.detail, color = Theme.MUTED, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * A marks-grid column heading. The two weighted O-Level assessments say what they
 * are worth, so nobody has to remember that A2 counts 20% and A3 counts 80%.
 */
fun componentHeader(c: com.derycode.srs.core.model.AssessmentComponent): String =
    if (c.weightPercent > 0) "${c.code} (${c.weightPercent}%)" else c.code
