package com.derycode.srs.core.store

import com.derycode.srs.core.model.SchoolData
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** v2.2.26 — corrupt/truncated data files must never crash apps at startup. */
class CorruptRecoveryTest {
    private val store = JsonStore()
    private fun tempDir() = Files.createTempDirectory("srstest").let { it.toFile() }

    @Test
    fun saveThenLoadRoundTrips() {
        val dir = tempDir()
        val file = dir.toPath().resolve("school-data.json")
        store.save(file, SchoolData())
        assertTrue(Files.exists(file))
        assertNotNull(store.load(file))
    }

    @Test
    fun corruptFileThrowsSoFallbackTriggers() {
        val dir = tempDir()
        val file = dir.toPath().resolve("school-data.json")
        Files.writeString(file, "{\"school\": TRUNCATED GARBAGE")
        assertFailsWith<Exception> { store.load(file) }
    }

    @Test
    fun newestUsableBackupPatternRecovers() {
        val dir = tempDir()
        val file = dir.toPath().resolve("school-data.json")
        Files.writeString(file, "GARBAGE")
        val bdir = dir.toPath().resolve("backups")
        Files.createDirectories(bdir)
        store.save(bdir.resolve("backup-1700000000000.json"), SchoolData().let { it.copy(school = it.school.copy(name = "Recovered")) })

        // same pattern SchoolRepository uses
        val recovered = try { store.load(file); null }
        catch (e: Exception) {
            val backups = bdir.toFile().listFiles { f -> f.name.startsWith("backup-") }!!
                .sortedByDescending { it.name }
            var r: SchoolData? = null
            for (b in backups) { try { r = store.load(b.toPath()); break } catch (_: Exception) { } }
            r
        }
        assertNotNull(recovered)
        assertEquals("Recovered", recovered.school.name)
    }
}
