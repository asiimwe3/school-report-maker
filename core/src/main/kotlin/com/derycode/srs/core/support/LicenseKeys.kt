package com.derycode.srs.core.support

/**
 * Offline licence keys (pricing enforcement).
 *
 * Key format:  PLAN-YYYYMMDD-CHECKSUM   e.g.  SD-20270909-8DE1C816
 * Checksum = djb2 hash of "secret|PLAN|expiry" — verifiable offline forever.
 *
 * Plans match the desktop "Plans & Pricing" tiers:
 *   ST Starter 100 · SD Standard 300 · GR Growth 800 · SC School 2000 · MB Multi-Branch unlimited
 */
object LicenseKeys {
    const val SECRET = "DeryCode-SRS-2026"

    data class Plan(val code: String, val name: String, val studentLimit: Int)

    val PLANS = listOf(
        Plan("ST", "Starter", 100),
        Plan("SD", "Standard", 300),
        Plan("GR", "Growth", 800),
        Plan("SC", "School", 2000),
        Plan("MB", "Multi-Branch", Int.MAX_VALUE)
    )

    fun hash(s: String): String {
        var h = 5381L
        for (c in s) h = ((h * 33) + c.code.toLong()) and 0xFFFFFFFFL
        return "%08x".format(h)
    }

    fun generate(code: String, expiry: String): String {
        val c = code.uppercase()
        return "$c-$expiry-" + hash("$SECRET|$c|$expiry").uppercase()
    }

    /** Returns the plan + expiry if the key is valid and not expired; null otherwise. */
    fun validate(key: String): Pair<Plan, String>? {
        val parts = key.trim().uppercase().split("-")
        if (parts.size != 3) return null
        val plan = PLANS.firstOrNull { it.code == parts[0] } ?: return null
        val expiry = parts[1]
        if (expiry.length != 8 || expiry.any { !it.isDigit() }) return null
        if (hash("$SECRET|${parts[0]}|$expiry").uppercase() != parts[2]) return null
        if (expiry < todayCompact()) return null
        return plan to expiry
    }

    fun todayCompact(): String = java.time.LocalDate.now().toString().replace("-", "")

    /** Student limit for a plan display name ("Trial" → 100 students, "Starter" → 100, …). */
    fun limitFor(planName: String): Int = when {
        planName.equals("Trial", true) -> 100
        else -> PLANS.firstOrNull { it.name.equals(planName, true) }?.studentLimit
            ?: PLANS[0].studentLimit
    }
}
