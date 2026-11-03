package com.derycode.srs.teacher

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What this teacher told us when they joined: their name, role, and the classes
 * and subjects they picked. Kept on the phone so pulling the school's setup from
 * the console cannot quietly remove them from staff and take their access away.
 *
 * Stored with org.json rather than kotlinx so this module needs no extra plugin.
 */
data class JoinProfile(
    val name: String = "",
    val role: String = "TEACHER",
    val phone: String = "",
    val cloudUid: String = "",
    val classIds: List<String> = emptyList(),
    val classNames: List<String> = emptyList(),
    val subjectIds: List<String> = emptyList(),
    val subjectNames: List<String> = emptyList()
) {
    private fun toJson(): String = JSONObject().apply {
        put("name", name); put("role", role); put("phone", phone); put("cloudUid", cloudUid)
        put("classIds", JSONArray(classIds)); put("classNames", JSONArray(classNames))
        put("subjectIds", JSONArray(subjectIds)); put("subjectNames", JSONArray(subjectNames))
    }.toString()

    companion object {
        private fun file(filesDir: File) = File(filesDir, "me-join.json")

        private fun list(o: JSONObject, key: String): List<String> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf { it.isNotBlank() } }
        }

        fun read(filesDir: File): JoinProfile? = runCatching {
            val f = file(filesDir)
            if (!f.exists() || f.readText().isBlank()) return null
            val o = JSONObject(f.readText())
            JoinProfile(
                name = o.optString("name"), role = o.optString("role", "TEACHER"),
                phone = o.optString("phone"), cloudUid = o.optString("cloudUid"),
                classIds = list(o, "classIds"), classNames = list(o, "classNames"),
                subjectIds = list(o, "subjectIds"), subjectNames = list(o, "subjectNames")
            ).takeIf { it.name.isNotBlank() }
        }.getOrNull()

        fun write(filesDir: File, p: JoinProfile) {
            runCatching { file(filesDir).writeText(p.toJson()) }
        }
    }
}
