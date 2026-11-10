package com.derycode.srs.core

import com.derycode.srs.core.model.School
import com.derycode.srs.core.model.SchoolClass
import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.store.JsonStore
import com.derycode.srs.core.store.SchoolRecovery
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchoolRecoveryTest {

    private fun tempDir() = Files.createTempDirectory("srs-recovery")

    @Test fun `a console that opened an empty file is recognised as empty`() {
        assertTrue(SchoolRecovery.looksEmpty(SchoolData()))
        val real = SchoolData(school = School(name = "Tooro Royal College"),
            classes = listOf(SchoolClass(id = "c1", name = "S.1")))
        assertTrue(!SchoolRecovery.looksEmpty(real), "a school with classes is not empty")
    }

    @Test fun `backups are found and described well enough to choose one`() {
        val dir = tempDir(); val backups = dir.resolve("backups"); Files.createDirectories(backups)
        val store = JsonStore()
        val full = SchoolData(
            school = School(name = "Tooro Royal College"),
            classes = listOf(SchoolClass(id = "c1", name = "S.1"), SchoolClass(id = "c2", name = "S.2"))
        )
        store.save(backups.resolve("backup-1800000000000.json"), full)
        store.save(backups.resolve("backup-1800000100000.json"), full)

        val found = SchoolRecovery.scan(listOf(dir))
        assertEquals(2, found.size)
        assertEquals("Tooro Royal College", found[0].school)
        assertEquals(2, found[0].classes)
        assertEquals("Backup", found[0].origin)
        assertTrue(found[0].savedAt > found[1].savedAt, "newest first")
    }

    @Test fun `empty backups and junk files are not offered as a rescue`() {
        val dir = tempDir(); val backups = dir.resolve("backups"); Files.createDirectories(backups)
        val store = JsonStore()
        store.save(backups.resolve("backup-1800000000000.json"), SchoolData())      // brand-new, nothing in it
        Files.writeString(backups.resolve("notes.json"), "{\"hello\":\"world\",\"students\":[]}")
        Files.writeString(backups.resolve("backup-notes.json"), "not json at all, just a sentence")
        assertTrue(SchoolRecovery.scan(listOf(dir)).isEmpty(), "nothing worth restoring")
    }

    @Test fun `the best candidate prefers the school asked for and the fullest file`() {
        val dir = tempDir()
        val store = JsonStore()
        val tooro = SchoolData(school = School(name = "Tooro Royal College"),
            classes = listOf(SchoolClass(id = "c1", name = "S.1")))
        val other = SchoolData(school = School(name = "Another School"),
            classes = (1..4).map { SchoolClass(id = "o$it", name = "S.$it") })
        store.save(dir.resolve("a-1800000000000.json"), tooro)
        store.save(dir.resolve("b-1800000200000.json"), other)
        val found = SchoolRecovery.scan(listOf(dir))
        assertEquals(2, found.size)
        assertEquals("Tooro Royal College", assertNotNull(SchoolRecovery.best(found, "tooro")).school)
        assertEquals("Another School", assertNotNull(SchoolRecovery.best(found)).school)
    }

    @Test fun `a USB folder copied onto the desktop is scanned too`() {
        val dir = tempDir(); val usb = dir.resolve("from-usb"); Files.createDirectories(usb)
        JsonStore().save(usb.resolve("school.json"), SchoolData(
            school = School(name = "Tooro Royal College"),
            classes = listOf(SchoolClass(id = "c1", name = "S.3"))))
        val found = SchoolRecovery.scan(listOf(dir))
        assertEquals(1, found.size)
        assertEquals("Tooro Royal College", found[0].school)
        assertEquals(1, found[0].classes)
    }
}
