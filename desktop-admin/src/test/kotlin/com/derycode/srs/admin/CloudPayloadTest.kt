package com.derycode.srs.admin

import com.derycode.srs.core.model.AppSettings
import com.derycode.srs.core.model.SchoolData
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The console keeps the school account's password locally so a head teacher can
 * sign in again on another PC, but that password must never leave the machine:
 * the cloud copy can be pulled, read or handed to support during a repair.
 */
class CloudPayloadTest {

    private val secret = "k7-m2p9qz-4xrt8b-wd3vc6"

    private fun school() = SchoolData(
        settings = AppSettings(
            cloudEmail = "school-abc123@srs.derycode.online",
            cloudPassword = secret,
            cloudSchoolId = "school-1",
            cloudInviteCode = "TRC7X2K9"
        )
    )

    @Test
    fun password_never_travels_in_the_cloud_copy() {
        val payload = CloudApi.cloudPayload(school())
        assertFalse(payload.contains(secret), "the school account password leaked into the cloud payload")
        assertFalse(payload.contains("cloudPassword"), "the password field is still serialised")
    }

    @Test
    fun the_rest_of_the_school_still_travels_so_a_restore_still_works() {
        val payload = CloudApi.cloudPayload(school())
        assertTrue(payload.contains("school-abc123@srs.derycode.online"), "the account email must survive, it is how support identifies the school")
        assertTrue(payload.contains("TRC7X2K9"), "the teacher school code must survive so phones can still join after a restore")
        assertTrue(payload.contains("school-1"), "the cloud school id must survive or a restore would create a second school")
    }

    @Test
    fun payload_is_still_valid_school_json() {
        val payload = CloudApi.cloudPayload(school())
        val back = kotlinx.serialization.json.Json.decodeFromString(SchoolData.serializer(), payload)
        assertTrue(back.settings.cloudPassword.isBlank())
        assertTrue(back.settings.cloudInviteCode == "TRC7X2K9")
    }
}
