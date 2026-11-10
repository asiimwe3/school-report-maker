package com.derycode.srs.core

import com.derycode.srs.core.model.Comment
import com.derycode.srs.core.model.CommentType
import com.derycode.srs.core.store.JsonStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "In the teacher app the comment must be text" — and it must actually reach the
 * console. Remarks were never in the bundle, so a typed comment died on the phone.
 */
class CommentBundleTest {

    private fun comment(student: String, text: String, term: String = "t1") =
        Comment(id = "c-$student-$term", studentId = student, termId = term,
            type = CommentType.CLASS_TEACHER, text = text)

    @Test fun `a typed remark survives the bundle round trip`() {
        val tmp = kotlin.io.path.createTempFile(suffix = ".json")
        val store = JsonStore()
        store.exportBundle(tmp, "Bagu Secondary", "teacher-phone",
            marks = emptyList(), comments = listOf(comment("s1", "Esther has improved steadily this term.")))
        val bundle = store.readBundle(tmp)
        assertEquals(1, bundle.comments.size, "the bundle must carry the remark")
        assertEquals("Esther has improved steadily this term.", bundle.comments.first().text)
        tmp.toFile().delete()
    }

    @Test fun `a remark with commas, quotes and apostrophes stays intact`() {
        val tmp = kotlin.io.path.createTempFile(suffix = ".json")
        val store = JsonStore()
        val tricky = "She's \"very\" good, especially in Maths; keep it up."
        store.exportBundle(tmp, "School", "phone", marks = emptyList(), comments = listOf(comment("s1", tricky)))
        assertEquals(tricky, store.readBundle(tmp).comments.first().text)
        tmp.toFile().delete()
    }

    @Test fun `importing keeps other learners' remarks and replaces the same one`() {
        val store = JsonStore()
        val existing = listOf(
            comment("s1", "Old text"), comment("s2", "Second learner's remark")
        )
        val incoming = listOf(comment("s1", "New text from the phone"))
        val merged = store.mergeComments(existing, incoming)
        assertEquals(2, merged.size)
        assertEquals("New text from the phone", merged.first { it.studentId == "s1" }.text)
        assertEquals("Second learner's remark", merged.first { it.studentId == "s2" }.text)
    }

    @Test fun `an empty incoming list never wipes remarks already on the console`() {
        val store = JsonStore()
        val existing = listOf(comment("s1", "Keep me"))
        assertEquals(existing, store.mergeComments(existing, emptyList()))
        assertTrue(store.mergeComments(emptyList(), emptyList()).isEmpty())
    }

    @Test fun `a bundle written before remarks existed still reads`() {
        val tmp = kotlin.io.path.createTempFile(suffix = ".json")
        tmp.toFile().writeText("""{"format":"DERYCODE-SRS-BUNDLE","bundleVersion":1,"schoolName":"S","marks":[]}""")
        val bundle = JsonStore().readBundle(tmp)
        assertTrue(bundle.comments.isEmpty())
        tmp.toFile().delete()
    }
}
