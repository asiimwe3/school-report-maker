package com.derycode.srs.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchoolScopeTest {

    @Test fun `secondary school runs only O and A level`() {
        val scope = SchoolScope(SchoolCategory.SECONDARY)
        assertEquals(listOf(Level.O_LEVEL, Level.A_LEVEL), scope.levels)
        assertFalse(scope.allows(Level.PRIMARY))
        assertTrue(scope.isSecondaryOnly)
    }

    @Test fun `primary school runs only primary`() {
        val scope = SchoolScope(SchoolCategory.PRIMARY)
        assertEquals(listOf(Level.PRIMARY), scope.levels)
        assertFalse(scope.allows(Level.O_LEVEL))
    }

    @Test fun `combined school runs the whole ladder`() {
        assertEquals(3, SchoolScope(SchoolCategory.COMBINED).levels.size)
    }

    @Test fun `secondary ladder is senior one to senior six only`() {
        val classes = standardClassesFor(SchoolCategory.SECONDARY)
        assertEquals(6, classes.size)
        assertEquals(
            listOf("Senior 1", "Senior 2", "Senior 3", "Senior 4", "Senior 5", "Senior 6"),
            classes.map { it.name }
        )
        assertEquals(
            listOf(Level.O_LEVEL, Level.O_LEVEL, Level.O_LEVEL, Level.O_LEVEL, Level.A_LEVEL, Level.A_LEVEL),
            classes.map { it.level }
        )
        assertTrue(classes.none { it.name.startsWith("Primary") })
    }

    @Test fun `primary ladder is primary one to seven`() {
        val classes = standardClassesFor(SchoolCategory.PRIMARY)
        assertEquals(7, classes.size)
        assertTrue(classes.all { it.level == Level.PRIMARY })
    }

    @Test fun `combined ladder keeps the seeded class ids`() {
        val ids = standardClassesFor(SchoolCategory.COMBINED).map { it.id }
        assertEquals((1..7).map { "cls-p$it" } + (1..6).map { "cls-s$it" }, ids)
    }

    @Test fun `stored values and unknown data fall back safely`() {
        assertEquals(SchoolCategory.SECONDARY, SchoolScope.fromStored("SECONDARY").category)
        assertEquals(SchoolCategory.SECONDARY, SchoolScope.fromStored("secondary").category)
        assertEquals(SchoolCategory.PRIMARY, SchoolScope.fromStored("PRIMARY").category)
        assertEquals(SchoolCategory.COMBINED, SchoolScope.fromStored("").category)
        assertEquals(SchoolCategory.COMBINED, SchoolScope.fromStored("whatever").category)
    }

    @Test fun `level coercion never lands on a level the school does not run`() {
        val secondary = SchoolScope(SchoolCategory.SECONDARY)
        assertEquals(Level.O_LEVEL, secondary.coerce(Level.PRIMARY))
        assertEquals(Level.A_LEVEL, secondary.coerce(Level.A_LEVEL))
        assertEquals(Level.PRIMARY, SchoolScope(SchoolCategory.PRIMARY).coerce(Level.O_LEVEL))
    }

    @Test fun `classes and subjects outside the scope are filtered`() {
        val scope = SchoolScope(SchoolCategory.SECONDARY)
        val classes = listOf(
            SchoolClass("p1", "Primary 1", Level.PRIMARY),
            SchoolClass("s1", "Senior 1", Level.O_LEVEL),
            SchoolClass("s5", "Senior 5", Level.A_LEVEL),
            SchoolClass("s2", "Senior 2", Level.O_LEVEL, active = false)
        )
        assertEquals(listOf("s1", "s5"), scope.classes(classes).map { it.id })
        val subjects = listOf(
            Subject("m", "Mathematics", "MATH", Level.O_LEVEL),
            Subject("e", "English", "ENG", Level.PRIMARY),
            Subject("f", "Further Mathematics", "FMATH", Level.A_LEVEL)
        )
        assertEquals(listOf("m", "f"), scope.subjects(subjects).map { it.id })
    }

    @Test fun `scope summary names the ladders`() {
        assertEquals("Secondary · O-level S1–S4 · A-level S5–S6", SchoolScope(SchoolCategory.SECONDARY).summary)
    }
}
