package com.derycode.srs.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * "Sign in on another computer" only works if the console can find the school the
 * account already owns. It cannot be asked for an id - a new PC has none - so it
 * reads whatever row-level security hands back and must not misread the answer.
 */
class CloudSignInTest {

    @Test
    fun reads_the_school_the_account_owns() {
        val mine = CloudApi.parseMySchool(
            """[{"id":"8f2c1d","name":"Tooro Royal College","invite_code":"TRC7X2K9"}]"""
        )
        assertEquals("8f2c1d", mine?.id)
        assertEquals("Tooro Royal College", mine?.name)
        assertEquals("TRC7X2K9", mine?.inviteCode)
    }

    @Test
    fun an_account_without_a_school_is_not_a_school() {
        assertNull(CloudApi.parseMySchool("[]"), "an empty list must not look like a school")
    }

    @Test
    fun an_error_body_is_not_a_school() {
        assertNull(CloudApi.parseMySchool("""{"message":"JWT expired"}"""))
        assertNull(CloudApi.parseMySchool("not json at all"))
    }
}
