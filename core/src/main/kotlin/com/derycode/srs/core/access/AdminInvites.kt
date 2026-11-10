package com.derycode.srs.core.access

import com.derycode.srs.core.model.AdminInvite
import com.derycode.srs.core.model.Role
import kotlin.random.Random

/**
 * Codes that invite a colleague to be a school admin.
 *
 * A code is generated on the console, written into the school file, and redeemed
 * once. It carries no secret beyond itself: it only works on a console that holds
 * this school's data (the same machine, or one restored from the school backup),
 * so a leaked code cannot open somebody else's school. Redeeming it creates the
 * user account and stamps the invite, which the audit log records.
 */
object AdminInvites {

    const val PREFIX = "ADM"

    /** No I, O, 0 or 1: a code read over the phone must not become a different code. */
    private const val ALPHABET = "ACDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** ADM plus eight characters: the dashes are decoration, a teacher may type them or not. */
    private val SHAPE = Regex("^ADM[A-Z0-9]{8}$")

    /** Roles a head teacher may hand out. SUPER_ADMIN is never inviteable. */
    val INVITEABLE = listOf(
        Role.SCHOOL_ADMIN, Role.HEAD_TEACHER, Role.DEPUTY_HEAD, Role.DOS,
        Role.BURSAR, Role.CLASS_TEACHER, Role.TEACHER, Role.DATA_ENTRY
    )

    fun newCode(random: Random = Random.Default): String {
        val body = (1..8).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return "$PREFIX-${body.take(4)}-${body.drop(4)}"
    }

    /** Everything a person might type as one canonical string: no spaces, no dashes, uppercase. */
    fun canonical(raw: String): String = raw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")

    fun normalise(raw: String): String {
        val c = canonical(raw)
        return if (SHAPE.matches(c)) "$PREFIX-${c.substring(3, 7)}-${c.substring(7)}" else c
    }

    fun looksLikeCode(raw: String): Boolean = SHAPE.matches(canonical(raw))

    /** Human readable state, so the console can show it in the list. */
    fun state(invite: AdminInvite, now: Long): String = when {
        invite.revoked -> "Revoked"
        invite.usedAt > 0 -> "Used"
        invite.expiresAt in 1..now -> "Expired"
        else -> "Active"
    }

    fun create(
        id: String,
        name: String,
        phone: String = "",
        role: Role = Role.SCHOOL_ADMIN,
        daysValid: Int = 7,
        now: Long = System.currentTimeMillis(),
        random: Random = Random.Default
    ): AdminInvite {
        val safeRole = if (role in INVITEABLE) role else Role.SCHOOL_ADMIN
        val days = daysValid.coerceIn(1, 60)
        return AdminInvite(
            id = id,
            code = newCode(random),
            name = name.trim(),
            phone = phone.trim(),
            role = safeRole,
            createdAt = now,
            expiresAt = now + days * 86_400_000L
        )
    }

    sealed interface Check {
        data class Ok(val invite: AdminInvite) : Check
        data class No(val message: String) : Check
    }

    /** Does this code still let somebody in? */
    fun check(invites: List<AdminInvite>, rawCode: String, now: Long = System.currentTimeMillis()): Check {
        val code = normalise(rawCode)
        if (code.isBlank()) return Check.No("Type the invite code first.")
        if (!looksLikeCode(code)) return Check.No("That is not an invite code. They look like ADM-4K7M-92QX.")
        val invite = invites.firstOrNull { canonical(it.code) == canonical(code) }
            ?: return Check.No("No invite with that code exists in this school. Ask for a fresh one.")
        return when {
            invite.revoked -> Check.No("That invite was cancelled. Ask the head teacher for a new code.")
            invite.usedAt > 0 -> Check.No("That code was already used on ${invite.usedOn.ifBlank { "another console" }}. It works once only.")
            invite.expiresAt in 1..now -> Check.No("That code expired. Ask for a new one; they last ${(invite.expiresAt - invite.createdAt) / 86_400_000L} days.")
            else -> Check.Ok(invite)
        }
    }

    /** A username nobody in the school already answers to. */
    fun uniqueUsername(wanted: String, taken: List<String>): String {
        val base = wanted.trim().lowercase().replace(Regex("[^a-z0-9.]"), "").ifBlank { "admin" }
        if (taken.none { it.equals(base, true) }) return base
        var n = 2
        while (taken.any { it.equals("$base$n", true) }) n++
        return "$base$n"
    }

    /** Marks the invite redeemed. The console adds the user account alongside it. */
    fun redeem(invite: AdminInvite, userId: String, on: String, now: Long = System.currentTimeMillis()): AdminInvite =
        invite.copy(usedAt = now, usedOn = on, userId = userId)
}
