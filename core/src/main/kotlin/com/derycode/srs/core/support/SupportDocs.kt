package com.derycode.srs.core.support

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared support/licensing constants and documentation.
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
            features = "Full desktop console + teacher app for one school, up to 100 students. All features unlocked so you can test with real data."
        ),
        Tier(
            name = "Starter",
            price = "On request",
            period = "per year, per school",
            features = "Up to 100 students. Console + unlimited teacher phones."
        ),
        Tier(
            name = "Standard",
            price = "On request",
            period = "per year, per school",
            features = "Up to 300 students. Console + unlimited teacher phones."
        ),
        Tier(
            name = "Growth",
            price = "On request",
            period = "per year, per school",
            features = "Up to 800 students. All grading schemes + cloud backup."
        ),
        Tier(
            name = "School",
            price = "UGX 250,000",
            period = "per year, per school",
            features = "Up to 2,000 students. Admin console on 2 computers, unlimited teacher phones, all grading schemes (PLE, UCE, UACE), cloud backup, free updates during the licence, WhatsApp support."
        ),
        Tier(
            name = "Multi-Branch Premium",
            price = "UGX 600,000",
            period = "per year, per school group",
            features = "Unlimited students for up to 5 campuses, report branding, priority support, help setting up grading schemes."
        )
    )

    data class Tier(val name: String, val price: String, val period: String, val features: String)

    const val SUMMARY = """
SCHOOL REPORT MAKER — SUBSCRIPTION PLANS

The same plans apply to the Admin Console and the Teacher App. The student limit is enforced by the licence key on each plan.

1. TRIAL — Free for 30 days
Full desktop console + teacher app for one school, up to 100 students. All features unlocked so you can test with real data before paying.

2. STARTER — up to 100 students
Console on 1 computer, unlimited teacher phones, all grading schemes.

3. STANDARD — up to 300 students
Console on 1 computer, unlimited teacher phones, all grading schemes.

4. GROWTH — up to 800 students
Console on 2 computers, unlimited teacher phones, all grading schemes, cloud backup.

5. SCHOOL — UGX 250,000 per year — up to 2,000 students
Admin console on 2 computers, unlimited teacher phones, all grading schemes (PLE, UCE, UACE), cloud backup, free updates during the licence period, WhatsApp support.

6. MULTI-BRANCH PREMIUM — UGX 600,000 per year — unlimited students
Everything in the School plan for up to 5 campuses, custom report branding, priority support, and help setting up your grading schemes.

Payments are made to DeryCode via mobile money or bank. The licence covers one school (or school group for Premium). Renewal reminders appear in the app before expiry. Prices may change; your paid price stays fixed for the licence period. For Starter, Standard and Growth pricing contact DeryCode on WhatsApp 0762306675.
"""
}

object Terms {
    const val SUMMARY = """
SCHOOL REPORT MAKER — TERMS & CONDITIONS

1. Licence. The software is licensed, not sold. One licence covers one school (up to 5 campuses on Multi-Branch Premium). You may not resell, redistribute, or reverse-engineer the software.

2. Plans and limits. Each plan sets the maximum number of students the software will register (Trial 100, Starter 100, Standard 300, Growth 800, School 2,000, Multi-Branch unlimited). The limit is enforced by the licence key. Upgrading to a bigger plan keeps all your data.

3. Your data. All school data is stored on your own devices and, if you enable cloud sync, in your school's own cloud account. We never receive, host, or see your marks, students, or reports unless you voluntarily send us a file for support.

4. Cloud accounts. If your school enables cloud sync, the head teacher's account belongs to the school. You are responsible for keeping the account password safe and for who you invite as a teacher. Teacher invite codes should only be shared with your own staff.

5. Updates. The app checks GitHub for updates and lets you install them when available. Updating is recommended but optional.

6. Support. Support is provided via WhatsApp during the licence period. Support covers the software itself, not your computer, phone, or printer.

7. Backups. The app creates automatic rotating backups and can push a backup to your cloud account after every save, but you remain responsible for keeping extra copies (USB drive, external disk). We are not liable for data lost due to device failure or misuse.

8. Acceptable use. The licence permits use for your own school's academic records only. Using the software to produce falsified results is prohibited.

9. Trial. The 30-day trial has full features. After it expires, continue by purchasing a licence.

10. Changes. Terms may be updated with new versions. Continued use after an update means you accept the updated terms.
"""
}

object Privacy {
    const val SUMMARY = """
SCHOOL REPORT MAKER — PRIVACY POLICY

1. Offline first. The software works fully offline. Student names, marks, comments, and reports live only on your school's computers, phones, and USB drives unless you choose to enable cloud sync.

2. Cloud sync is your choice. If your school enables cloud sync, your backups and teachers' submissions are stored in your school's own cloud account. Your account password is stored only as a salted hash — nobody can read it. Cloud data is used only to restore your school's own data.

3. No advertising, no analytics, no tracking. The app has no advertising and no behaviour tracking. It does not profile students, teachers, or schools and never sells data.

4. Update checks. On startup the app checks our public GitHub page for a newer version. Only the request for that file is made — no school data is ever sent.

5. Crash reports. If the app crashes, the technical error is saved in a file on your device. Nothing is sent automatically. The "Send crash report" button opens WhatsApp so YOU choose to share the file with support.

6. WhatsApp support. When you contact support on WhatsApp, normal WhatsApp privacy applies to that conversation.

7. Student data. If your school shares student data with staff phones, the school is responsible for those devices. The app keeps marks on the phone's internal storage, which stays when the phone is offline.

8. Deletion. Uninstalling the app removes its files. Closing your cloud account deletes its backups from the cloud. Restoring a backup replaces the current database — keep your own extra copies.
"""
}
