package com.derycode.srs.core

import com.derycode.srs.core.access.AdminInvites
import com.derycode.srs.core.model.AdminInvite
import com.derycode.srs.core.model.Role
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdminInvitesTest {

    private val now = 1_800_000_000_000L
    private fun invite(days: Int = 7, role: Role = Role.SCHOOL_ADMIN) =
        AdminInvites.create("inv-1", "Mrs Namuli", "0772000111", role, days, now, Random(7))

    @Test fun `a code is readable over the phone and looks like an admin invite`() {
        val code = AdminInvites.newCode(Random(3))
        assertTrue(code.startsWith("ADM-"), code)
        assertTrue(AdminInvites.looksLikeCode(code), code)
        assertTrue(code.none { it in "IO01" }, "no look-alike characters: $code")
    }

    @Test fun `a fresh code is accepted for the role and expiry it was made with`() {
        val inv = invite(days = 7)
        val check = AdminInvites.check(listOf(inv), inv.code.lowercase(), now + 1000)
        assertIs<AdminInvites.Check.Ok>(check)
        assertEquals(Role.SCHOOL_ADMIN, check.invite.role)
        assertEquals(now + 7 * 86_400_000L, inv.expiresAt)
    }

    @Test fun `typing spaces or lowercase still finds the invite`() {
        val inv = invite()
        val messy = "  " + inv.code.lowercase().replace("-", " ") + " "
        assertIs<AdminInvites.Check.Ok>(AdminInvites.check(listOf(inv), messy, now))
    }

    @Test fun `a code works once and never twice`() {
        val inv = AdminInvites.redeem(invite(), userId = "u-9", on = "CONSOLE-01", now = now + 5000)
        val check = AdminInvites.check(listOf(inv), inv.code, now + 6000)
        assertIs<AdminInvites.Check.No>(check)
        assertTrue(check.message.contains("already used"), check.message)
        assertTrue(check.message.contains("CONSOLE-01"), "says where it was used")
    }

    @Test fun `an expired code is refused and says so`() {
        val inv = invite(days = 2)
        val check = AdminInvites.check(listOf(inv), inv.code, now + 3 * 86_400_000L)
        assertIs<AdminInvites.Check.No>(check)
        assertTrue(check.message.contains("expired"), check.message)
    }

    @Test fun `a cancelled code is refused`() {
        val inv = invite().copy(revoked = true)
        val check = AdminInvites.check(listOf(inv), inv.code, now)
        assertIs<AdminInvites.Check.No>(check)
        assertTrue(check.message.contains("cancelled"), check.message)
    }

    @Test fun `another school's code is not found here`() {
        val theirs = invite()
        val mine = listOf(AdminInvite(id = "inv-2", code = "ADM-AAAA-BBBB", createdAt = now, expiresAt = now + 86_400_000L))
        val check = AdminInvites.check(mine, theirs.code, now)
        assertIs<AdminInvites.Check.No>(check)
        assertTrue(check.message.contains("No invite"), check.message)
    }

    @Test fun `typos and empty input get plain words, not a crash`() {
        assertIs<AdminInvites.Check.No>(AdminInvites.check(emptyList(), "   ", now))
        assertIs<AdminInvites.Check.No>(AdminInvites.check(emptyList(), "hello", now))
    }

    @Test fun `a super admin can never be handed out by an invite`() {
        val inv = AdminInvites.create("inv-3", "Someone", role = Role.SUPER_ADMIN, now = now)
        assertEquals(Role.SCHOOL_ADMIN, inv.role)
    }

    @Test fun `usernames stay unique when two colleagues share a name`() {
        assertEquals("namuli", AdminInvites.uniqueUsername("Namuli", listOf("headteacher")))
        assertEquals("namuli2", AdminInvites.uniqueUsername("Namuli", listOf("namuli")))
        assertEquals("namuli3", AdminInvites.uniqueUsername("Namuli", listOf("namuli", "Namuli2")))
        assertEquals("admin", AdminInvites.uniqueUsername("   ", emptyList()))
    }

    @Test fun `state reads the way a head teacher would say it`() {
        val inv = invite()
        assertEquals("Active", AdminInvites.state(inv, now))
        assertEquals("Expired", AdminInvites.state(inv, now + 8 * 86_400_000L))
        assertEquals("Used", AdminInvites.state(inv.copy(usedAt = now + 10), now))
        assertEquals("Revoked", AdminInvites.state(inv.copy(revoked = true), now))
    }
}
