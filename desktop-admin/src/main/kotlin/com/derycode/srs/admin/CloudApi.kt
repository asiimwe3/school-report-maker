package com.derycode.srs.admin

import com.derycode.srs.core.store.SchoolRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Online cloud sync (v2.2.0) — Supabase-backed.
 *
 * Contract (tables created by docs/supabase-setup.sql):
 *  - auth/v1/signup + auth/v1/token     → school admin account
 *  - rest/v1/srs_schools                → school record (owner_uid = auth.uid())
 *  - rest/v1/srs_backups               → full SchoolData JSON push/pull
 *  - rest/v1/srs_teacher_marks          → teacher bundle payloads, pulled by console
 *  - rpc/srs_link_teacher               → teacher joins a school via invite code
 */
object CloudApi {

    // Defaults for a fresh install — editable in the setup wizard / Cloud screen.
    const val DEFAULT_URL = "https://ntdghazoglldkynlcndg.supabase.co"
    const val DEFAULT_KEY = "sb_publishable_efREJ4m-7d5Wkv5MARESIA_KJfr6ARn"


    data class AuthResult(val ok: Boolean, val error: String = "", val accessToken: String = "", val refreshToken: String = "")

    // HttpURLConnection instead of java.net.http.HttpClient — works on Java 8 too,
    // so cloud sync never crashes on school PCs with an old Java installed (fix for
    // NoClassDefFoundError: java/net/http/HttpClient on Java 8).
    private fun http(method: String, url: String, key: String, token: String?, body: String?): Pair<Int, String> {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("apikey", key)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=representation")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = try { conn.responseCode } catch (e: java.io.IOException) {
                return Pair(-1, "No internet connection — check the school's network or data bundle.")
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = try { stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "" } catch (_: Exception) { "" }
            return Pair(code, text)
        } finally {
            conn.disconnect()
        }
    }

    fun signUp(url: String, key: String, email: String, password: String): AuthResult {
        val r = http("POST", "$url/auth/v1/signup", key, null, "{\"email\":\"$email\",\"password\":\"$password\"}")
        return parseAuth(r.first, r.second, "Sign-up failed (account may already exist)")
    }

    fun login(url: String, key: String, email: String, password: String): AuthResult {
        val r = http("POST", "$url/auth/v1/token?grant_type=password", key, null, "{\"email\":\"$email\",\"password\":\"$password\"}")
        return parseAuth(r.first, r.second, "Login failed — check email and password")
    }

    fun refresh(url: String, key: String, refreshToken: String): AuthResult {
        val r = http("POST", "$url/auth/v1/token?grant_type=refresh_token", key, null, "{\"refresh_token\":\"$refreshToken\"}")
        return parseAuth(r.first, r.second, "Session expired — please sign in again")
    }

    private fun parseAuth(code: Int, body: String, failMsg: String): AuthResult {
        if (code < 200 || code > 299) {
            val m = extractString(body, "msg")
            return AuthResult(false, error = m.ifBlank { failMsg })
        }
        val at = extractString(body, "access_token")
        val rt = extractString(body, "refresh_token")
        return if (at.isNotEmpty()) AuthResult(true, accessToken = at, refreshToken = rt)
        else AuthResult(false, error = failMsg)
    }

    /** Extract a top-level string member from a JSON object body (defensive, no fancy APIs). */
    private fun extractString(body: String, member: String): String {
        return try {
            val root = Json.parseToJsonElement(body)
            if (root is JsonObject) {
                val v = root[member]
                if (v != null) return v.jsonPrimitive.content
            }
            ""
        } catch (e: Exception) { "" }
    }

    /** Create the school row (owner_uid defaults to auth.uid() server-side). Returns school id or error. */
    fun createSchool(url: String, key: String, token: String, name: String, district: String, address: String,
                     headTeacher: String, plan: String, licenseKey: String, studentCount: Int, inviteCode: String): Pair<String, String> {
        val body = "{\"name\":\"${esc(name)}\",\"district\":\"${esc(district)}\",\"address\":\"${esc(address)}\",\"head_teacher\":\"${esc(headTeacher)}\",\"plan\":\"${esc(plan)}\",\"license_key\":\"${esc(licenseKey)}\",\"student_count\":$studentCount,\"invite_code\":\"${esc(inviteCode)}\"}"
        val r = http("POST", "$url/rest/v1/srs_schools?select=id", key, token, body)
        if (r.first < 200 || r.first > 299) return Pair("", "Server error (${r.first}): ${r.second.take(120)}")
        return try {
            val root = Json.parseToJsonElement(r.second)
            var id = ""
            if (root is JsonArray) {
                val first = root.firstOrNull()
                if (first is JsonObject) {
                    val v = first["id"]
                    if (v != null) id = v.jsonPrimitive.content
                }
            }
            if (id.isNotEmpty()) Pair(id, "ok") else Pair("", "School created but no id returned")
        } catch (e: Exception) { Pair("", "Unexpected server response") }
    }

    /** Push full SchoolData JSON. Deletes previous cloud copy first (single-version cloud mirror). */
    fun pushBackup(url: String, key: String, token: String, schoolId: String, payload: String): Pair<Boolean, String> {
        http("DELETE", "$url/rest/v1/srs_backups?school_id=eq.$schoolId", key, token, null)
        val body = "{\"school_id\":\"$schoolId\",\"payload\":${kotlinx.serialization.json.JsonPrimitive(payload)},\"size\":${payload.length},\"version\":${System.currentTimeMillis()}}"
        val r = http("POST", "$url/rest/v1/srs_backups", key, token, body)
        return if (r.first in 200..299) Pair(true, "ok") else Pair(false, "Push failed (${r.first}): ${r.second.take(120)}")
    }

    /** Pull latest SchoolData JSON from the cloud. Empty string if none. */
    fun pullBackup(url: String, key: String, token: String, schoolId: String): Pair<String, String> {
        val r = http("GET", "$url/rest/v1/srs_backups?school_id=eq.$schoolId&order=version.desc&limit=1&select=payload", key, token, null)
        if (r.first < 200 || r.first > 299) return Pair("", "Pull failed (${r.first}): ${r.second.take(120)}")
        return try {
            val root = Json.parseToJsonElement(r.second)
            if (root is JsonArray) {
                val first = root.firstOrNull()
                if (first is JsonObject) {
                    val v = first["payload"]
                    if (v != null) return Pair(v.jsonPrimitive.content, "ok")
                }
            }
            Pair("", "ok")   // no backup yet
        } catch (e: Exception) { Pair("", "Unexpected server response") }
    }

    /** Teacher-submitted mark bundles. Returns list of bundle payloads. */
    fun pullTeacherBundles(url: String, key: String, token: String, schoolId: String): Pair<List<String>, String> {
        val r = http("GET", "$url/rest/v1/srs_teacher_marks?school_id=eq.$schoolId&order=created_at.asc&select=payload", key, token, null)
        if (r.first < 200 || r.first > 299) return Pair(emptyList<String>(), "Pull failed (${r.first}): ${r.second.take(120)}")
        return try {
            val root = Json.parseToJsonElement(r.second)
            val out = ArrayList<String>()
            if (root is JsonArray) {
                for (el in root) {
                    if (el is JsonObject) {
                        val v = el["payload"]
                        if (v != null) {
                            val p = v.jsonPrimitive.content
                            if (p.isNotEmpty()) out.add(p)
                        }
                    }
                }
            }
            Pair(out as List<String>, "ok")
        } catch (e: Exception) { Pair(emptyList<String>(), "Unexpected server response") }
    }

    /** Delete teacher bundles after successful import (consumed). */
    fun consumeTeacherBundles(url: String, key: String, token: String, schoolId: String): Boolean {
        val r = http("DELETE", "$url/rest/v1/srs_teacher_marks?school_id=eq.$schoolId", key, token, null)
        return r.first in 200..299
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}

/**
 * High-level sync helper bound to the repository. Runs on background threads.
 */
object CloudSync {

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var pending: ScheduledFuture<*>? = null

    fun url(s: com.derycode.srs.core.model.AppSettings) = s.cloudUrl.ifBlank { CloudApi.DEFAULT_URL }
    fun key(s: com.derycode.srs.core.model.AppSettings) = s.cloudKey.ifBlank { CloudApi.DEFAULT_KEY }

    fun scheduleAutoPush(repo: SchoolRepository) {
        val s = repo.data.settings
        if (s.cloudSchoolId.isBlank() || s.cloudAccessToken.isBlank()) return
        pending?.cancel(false)
        pending = executor.schedule({ pushNow(repo) }, 10, TimeUnit.SECONDS)
    }

    fun pushNow(repo: SchoolRepository, onResult: ((Boolean, String) -> Unit)? = null) {
        val s = repo.data.settings
        if (s.cloudSchoolId.isBlank()) { onResult?.invoke(false, "Cloud not set up yet"); return }
        val payload = Json.encodeToString(com.derycode.srs.core.model.SchoolData.serializer(), repo.data)
        Thread {
            var res = CloudApi.pushBackup(url(s), key(s), s.cloudAccessToken, s.cloudSchoolId, payload)
            var okNow = res.first
            var msg = res.second
            if (!okNow && (msg.contains("401") || msg.contains("403"))) {
                // silent refresh + one retry
                if (s.cloudRefreshToken.isNotBlank()) {
                    val r = CloudApi.refresh(url(s), key(s), s.cloudRefreshToken)
                    if (r.ok) {
                        val cur = repo.data
                        repo.mutate("cloud-refresh", "settings") { d -> d.copy(settings = d.settings.copy(cloudAccessToken = r.accessToken, cloudRefreshToken = r.refreshToken)) }
                        val retry = CloudApi.pushBackup(url(s), key(s), r.accessToken, s.cloudSchoolId, payload)
                        if (retry.first) { markPushed(repo, payload.length); onResult?.invoke(true, "ok"); return@Thread }
                        msg = retry.second
                    }
                }
                onResult?.invoke(false, msg); return@Thread
            }
            if (okNow) markPushed(repo, payload.length)
            onResult?.invoke(okNow, if (okNow) "ok" else msg)
        }.start()
    }

    private fun markPushed(repo: SchoolRepository, size: Int) {
        try {
            val stamp = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").format(java.time.LocalDateTime.now())
            repo.mutate("cloud-backup", "settings") { d -> d.copy(settings = d.settings.copy(cloudLastBackup = "$stamp (${size / 1024} KB)")) }
        } catch (_: Exception) {}
    }

    fun pullNow(repo: SchoolRepository, onResult: (Boolean, String) -> Unit) {
        val s = repo.data.settings
        if (s.cloudSchoolId.isBlank()) { onResult(false, "Cloud not set up yet"); return }
        Thread {
            val r = CloudApi.pullBackup(url(s), key(s), s.cloudAccessToken, s.cloudSchoolId)
            val payload = r.first
            if (payload.isEmpty()) { onResult(false, if (r.second == "ok") "No cloud backup found yet" else r.second); return@Thread }
            try {
                val data = Json.decodeFromString(com.derycode.srs.core.model.SchoolData.serializer(), payload)
                repo.mutate("cloud-restore", "settings") { data }
                onResult(true, "Cloud backup restored to this computer")
            } catch (e: Exception) { onResult(false, "Cloud backup could not be read: ${e.message}") }
        }.start()
    }
}
