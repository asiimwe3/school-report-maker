package com.derycode.srs.core.support

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared support/licensing constants and documentation (Section 40–41, support flows).
 * Both apps display these; keeping text in core guarantees they never disagree.
 */
object Support {
    const val SUPPORT_WHATSAPP = "256762306675"
    const val SUPPORT_LINK = "https://wa.me/256762306675"
    const val VERSION_URL = "https://github.com/asiimwe3/school-report-maker/releases/latest/download/version.json"
    const val RELEASES_PAGE = "https://github.com/asiimwe3/school-report-maker/releases"
    const val VENDOR = "DeryCode"
}

@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val msiUrl: String = "",
    val releaseNotes: String = ""
)

fun parseVersionJson(text: String): UpdateInfo? = try {
    Json { ignoreUnknownKeys = true }.decodeFromString(UpdateInfo.serializer(), text)
} catch (_: Exception) { null }

object Plans {
    val TIERS = listOf(
        Tier(
            name = "Trial",
            price = "Free",
            period = "30 days",
            features = "Full desktop console + teacher app for one school. All features unlocked so you can test with real data."
        ),
        Tier(
            name = "School Licence",
            price = "UGX 250,000",
            period = "per year, per school",
            features = "Admin console on 2 computers, unlimited teacher phones, all grading schemes, free updates during the licence, WhatsApp support."
        ),
        Tier(
            name = "Multi-Branch Premium",
            price = "UGX 600,000",
            period = "per year, per school group",
            features = "Everything in School Licence for up to 5 campuses, report branding, priority support, help setting up grading schemes."
        )
    )

    data class Tier(val name: String, val price: String, val period: String, val features: String)

    const val SUMMARY = """
SCHOOL REPORT MAKER — SUBSCRIPTION PLANS

1. TRIAL — Free for 30 days
Full desktop console + teacher app for one school. All features unlocked so you can test with real data before paying.

2. SCHOOL LICENCE — UGX 250,000 per year
Admin console on 2 computers, unlimited teacher phones, all grading schemes (PLE, UCE, UACE), free updates during the licence period, WhatsApp support.

3. MULTI-BRANCH PREMIUM — UGX 600,000 per year
Everything in the School Licence for up to 5 campuses, custom report branding, priority support, and help setting up your grading schemes.

Payments are made to DeryCode via mobile money or bank. The licence covers one school (or school group for Premium). Renewal reminders appear in the app before expiry. Prices may change; your paid price stays fixed for the licence period.
"""
}

object Terms {
    const val SUMMARY = """
SCHOOL REPORT MAKER — TERMS & CONDITIONS

1. Licence. The software is licensed, not sold. One licence covers one school (up to 5 campuses on Premium). You may not resell, redistribute, or reverse-engineer the software.

2. Your data. All school data is stored on your own devices. We never receive, host, or see your marks, students, or reports unless you voluntarily send us a file for support.

3. Updates. The app checks GitHub for updates and lets you install them when available. Updating is recommended but optional.

4. Support. Support is provided via WhatsApp during the licence period. Support covers the software itself, not your computer, phone, or printer.

5. Backups. The app creates automatic rotating backups, but you remain responsible for keeping extra copies (USB drive, external disk). We are not liable for data lost due to device failure or misuse.

6. Acceptable use. The licence permits use for your own school's academic records only. Using the software to produce falsified results is prohibited.

7. Trial. The 30-day trial has full features. After it expires, continue by purchasing a licence.

8. Changes. Terms may be updated with new versions. Continued use after an update means you accept the updated terms.
"""
}

object Privacy {
    const val SUMMARY = """
SCHOOL REPORT MAKER — PRIVACY POLICY

1. We store nothing about you. The software is fully offline. Student names, marks, comments, and reports live only on your school's computers, phones, and USB drives.

2. No accounts, no tracking, no analytics. The app has no sign-in, no advertising, and no behaviour tracking. It does not know who is using it.

3. Update checks. On startup the app checks our public GitHub page for a newer version. Only the request for that file is made — no school data is ever sent.

4. Crash reports. If the app crashes, the technical error is saved in a file on your device. Nothing is sent automatically. The "Send crash report" button opens WhatsApp so YOU choose to share the file with support.

5. WhatsApp support. When you contact support on WhatsApp, normal WhatsApp privacy applies to that conversation.

6. Student data. If your school shares student data with staff phones, the school is responsible for those devices. The app keeps marks on the phone's internal storage, which stays when the phone is offline.

7. Deletion. Uninstalling the app removes its files. Restoring a backup replaces the current database — keep your own extra copies.
"""
}
