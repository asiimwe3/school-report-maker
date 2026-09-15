package com.derycode.srs.teacher

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

// ─────────────────────────────────────────────────────────────────────────────
// Cloud sync — teacher online account + invite-code join + marks push/pull.
// Same Supabase project as the Admin Console (identical branding & docs).
// Marks NEVER leave the phone unless the teacher taps "Send".
// ─────────────────────────────────────────────────────────────────────────────

object CloudCfg {
    const val URL = "https://ntdghazoglldkynlcndg.supabase.co"
    const val KEY = "sb_publishable_efREJ4m-7d5Wkv5MARESIA_KJfr6ARn"
}

data class CloudAccount(
    val email: String = "",
    val uid: String = "",
    val refreshToken: String = ""
)

data class SchoolLite(val id: String = "", val name: String = "")

/** Result of a cloud call: token on auth success, message for the UI. */
data class CloudResult(val ok: Boolean, val token: String = "", val msg: String = "")

class TeacherCloud(private val context: Context) {

    private val accFile: File get() = File(context.filesDir, "cloud-account.json")

    var account: CloudAccount = load()
        private set

    val signedIn: Boolean get() = account.refreshToken.isNotBlank()

    private fun load(): CloudAccount {
        return try {
            if (!accFile.exists()) return CloudAccount()
            val root = Json.parseToJsonElement(accFile.readText())
            if (root is JsonObject) CloudAccount(
                email = root["email"]?.jsonPrimitive?.content ?: "",
                uid = root["uid"]?.jsonPrimitive?.content ?: "",
                refreshToken = root["refresh_token"]?.jsonPrimitive?.content ?: ""
            ) else CloudAccount()
        } catch (_: Exception) { CloudAccount() }
    }

    private fun save(a: CloudAccount) {
        account = a
        accFile.writeText(buildJsonObject {
            put("email", a.email); put("uid", a.uid); put("refresh_token", a.refreshToken)
        }.toString())
    }

    fun signOut() {
        accFile.delete()
        account = CloudAccount()
    }

    // ── HTTP ──────────────────────────────────────────────────────────────
    private fun http(method: String, path: String, token: String? = null, body: String?): Pair<Int, String> = try {
        val conn = URL(CloudCfg.URL + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.setRequestProperty("apikey", CloudCfg.KEY)
        conn.setRequestProperty("Content-Type", "application/json")
        if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
        body?.let {
            conn.doOutput = true
            conn.outputStream.use { s -> s.write(it.toByteArray(StandardCharsets.UTF_8)) }
        }
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
        conn.disconnect()
        Pair(code, text)
    } catch (e: Exception) { Pair(0, e.message ?: "No internet connection") }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")

    private fun member(body: String, name: String): String {
        return try {
            val root = Json.parseToJsonElement(body)
            if (root is JsonObject) (root[name] ?: "").let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } else ""
        } catch (_: Exception) { "" }
    }

    // ── Auth ──────────────────────────────────────────────────────────────
    fun signUp(email: String, password: String, fullName: String): CloudResult {
        val b = "{\"email\":\"${esc(email.trim().lowercase())}\",\"password\":\"${esc(password)}\",\"data\":{\"full_name\":\"${esc(fullName)}\"}}"
        val (code, body) = http("POST", "/auth/v1/signup", null, b)
        return authResult(code, body, "Could not create the account — email may already be registered", email.trim().lowercase())
    }

    fun login(email: String, password: String): CloudResult {
        val b = "{\"email\":\"${esc(email.trim().lowercase())}\",\"password\":\"${esc(password)}\"}"
        val (code, body) = http("POST", "/auth/v1/token?grant_type=password", null, b)
        return authResult(code, body, "Sign-in failed — check email and password", email.trim().lowercase())
    }

    private fun authResult(code: Int, body: String, fail: String, accountEmail: String = ""): CloudResult {
        if (code !in 200..299) {
            val m = member(body, "msg").ifBlank { member(body, "error").ifBlank { fail } }
            return CloudResult(false, msg = m)
        }
        val tok = member(body, "access_token")
        val rt = member(body, "refresh_token")
        val uid = try {
            val root = Json.parseToJsonElement(body)
            val u = (root as? JsonObject)?.get("user") as? JsonObject
            (u?.get("id") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
        } catch (_: Exception) { "" }
        if (tok.isBlank() || rt.isBlank()) return CloudResult(false, msg = fail)
        return CloudResult(true, token = tok, msg = "ok").also {
            save(CloudAccount(email = accountEmail.ifBlank { member(body, "email") }, uid = uid, refreshToken = rt))
        }
    }

    /** Refresh the access token from the stored refresh token. */
    fun freshToken(): CloudResult {
        if (!signedIn) return CloudResult(false, msg = "Not signed in")
        val b = "{\"refresh_token\":\"${esc(account.refreshToken)}\"}"
        val (code, body) = http("POST", "/auth/v1/token?grant_type=refresh_token", null, b)
        if (code !in 200..299) return CloudResult(false, msg = "Session expired — sign in again")
        val tok = member(body, "access_token")
        val rt = member(body, "refresh_token")
        if (tok.isBlank()) return CloudResult(false, msg = "Session expired — sign in again")
        if (rt.isNotBlank()) save(account.copy(refreshToken = rt))
        return CloudResult(true, token = tok, msg = "ok")
    }

    // ── Schools ───────────────────────────────────────────────────────────
    /** Schools this teacher is linked to (RLS: only linked schools visible). */
    fun linkedSchools(token: String): List<SchoolLite> {
        val (code, body) = http("GET", "/rest/v1/srs_schools?select=id,name,invite_code", token, null)
        if (code !in 200..299) return emptyList()
        return try {
            val root = Json.parseToJsonElement(body)
            val out = ArrayList<SchoolLite>()
            if (root is JsonArray) for (el in root) if (el is JsonObject) {
                val id = (el["id"] ?: continue).jsonPrimitive.content
                val nm = el["name"]?.jsonPrimitive?.content ?: ""
                out.add(SchoolLite(id = id, name = nm))
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    /** Join a school with its invite code (head teacher shares the code). */
    fun joinSchool(token: String, code: String, myName: String): CloudResult {
        // Simple to use: accept the code in any format — lowercase, spaces,
        // dashes from WhatsApp, trailing periods… ("ABCD-2345" == "abcd 2345").
        val clean = code.uppercase().filter { it.isLetterOrDigit() }
        if (clean.isBlank()) return CloudResult(false, msg = "Enter the school code from your head teacher's console")
        val b = "{\"p_code\":\"${esc(clean)}\",\"p_name\":\"${esc(myName)}\"}"
        val (code, body) = http("POST", "/rest/v1/rpc/srs_link_teacher", token, b)
        if (code !in 200..299) {
            val m = member(body, "message").ifBlank { "Invalid school code — ask the head teacher for the correct code" }
            return CloudResult(false, msg = m)
        }
        return CloudResult(true, token = token, msg = "Linked to your school ✓")
    }

    /** Latest school setup backup from the console (classes, students, config). */
    fun pullSchoolConfig(token: String, schoolId: String): Pair<String, String> {
        val (code, body) = http("GET", "/rest/v1/srs_backups?school_id=eq.$schoolId&order=version.desc&limit=1&select=payload", token, null)
        if (code !in 200..299) return Pair("", "Pull failed — check internet and try again")
        return try {
            val root = Json.parseToJsonElement(body)
            if (root is JsonArray) {
                val first = root.firstOrNull() as? JsonObject ?: return Pair("", "The school has not pushed a backup yet — ask the head teacher to run a cloud backup on the console")
                val payload = first["payload"]?.jsonPrimitive?.content ?: ""
                if (payload.isBlank()) Pair("", "The school has not pushed a backup yet")
                else Pair(payload, "ok")
            } else Pair("", "Pull failed — unexpected server response")
        } catch (_: Exception) { Pair("", "Pull failed — wrong data format") }
    }

    /** Push the teacher's marks bundle to the school console. */
    fun pushMarks(token: String, schoolId: String, teacherName: String, payload: String): CloudResult {
        val uid = account.uid
        if (uid.isBlank()) return CloudResult(false, msg = "Account error — sign in again")
        val b = "{\"school_id\":\"$schoolId\",\"teacher_uid\":\"$uid\",\"teacher_name\":\"${esc(teacherName)}\",\"payload\":$payload}"
        val (code, body) = http("POST", "/rest/v1/srs_teacher_marks", token, b)
        return if (code in 200..299) CloudResult(true, token = token, msg = "Marks sent ✓ — the head teacher taps 'Pull Teacher Marks' on the console to import them")
        else CloudResult(false, msg = "Send failed — check internet and try again (${member(body, "message")})")
    }
}
