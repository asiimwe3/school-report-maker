package com.derycode.srs.teacher

import androidx.compose.ui.graphics.Color

/**
 * The phone's own palettes — the same seven the Admin console ships, so a school
 * can look identical everywhere. A teacher can also pick a different one on the
 * phone alone (Some mark at night, some like paper white).
 */
internal data class PhonePalette(
    val bg: Color,
    val card: Color,
    val cardAlt: Color,
    val stroke: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val teal: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val purple: Color,
    val pink: Color,
    val dark: Boolean
)

internal data class ThemeChoice(val name: String, val blurb: String)

internal val THEME_CHOICES = listOf(
    ThemeChoice("Editorial", "Cream paper with deep green ink \u2014 the classroom classic"),
    ThemeChoice("Ivory", "Warm ivory with sepia accents, easiest on the eyes outdoors"),
    ThemeChoice("Forest", "Soft green wash with forest ink"),
    ThemeChoice("Ocean", "Cool blue wash with navy ink"),
    ThemeChoice("Plum", "Muted purple \u2014 quiet and scholarly"),
    ThemeChoice("Midnight", "Dark slate for marking at night"),
    ThemeChoice("Midnight Gold", "Near-black with gold, our premium look")
)

internal fun phonePalette(name: String): PhonePalette {
    val n = name.trim().lowercase()
    return when {
        n.contains("midnight") && n.contains("gold") -> PhonePalette(
            bg = Color(0xFF16130E), card = Color(0xFF211C15), cardAlt = Color(0xFF2A241B),
            stroke = Color(0xFF37301F), ink = Color(0xFFF3EDE1), muted = Color(0xFFA79B86),
            accent = Color(0xFFC99A45), teal = Color(0xFFDDB35E), green = Color(0xFF6FBF8F),
            red = Color(0xFFE08A6B), orange = Color(0xFFE3A44F), purple = Color(0xFFC0A0E8),
            pink = Color(0xFFE8A0BC), dark = true
        )
        n.contains("midnight") || n.contains("dark") -> PhonePalette(
            bg = Color(0xFF11161C), card = Color(0xFF1A2129), cardAlt = Color(0xFF232C36),
            stroke = Color(0xFF2B3540), ink = Color(0xFFE9EEF4), muted = Color(0xFF93A1B1),
            accent = Color(0xFF3FA897), teal = Color(0xFF4FB3A5), green = Color(0xFF52C79E),
            red = Color(0xFFEF6E6E), orange = Color(0xFFE3A44F), purple = Color(0xFFA78BFA),
            pink = Color(0xFFF472B6), dark = true
        )
        n.contains("ivory") -> PhonePalette(
            bg = Color(0xFFFBF8F1), card = Color(0xFFFFFFFF), cardAlt = Color(0xFFF3EEE4),
            stroke = Color(0xFFE4E0D6), ink = Color(0xFF2A2A28), muted = Color(0xFF7A7770),
            accent = Color(0xFF8C6A2F), teal = Color(0xFF1F8A6F), green = Color(0xFF2F7D5B),
            red = Color(0xFFB05A3C), orange = Color(0xFFC98A2E), purple = Color(0xFF7A5AA8),
            pink = Color(0xFFB0567F), dark = false
        )
        n.contains("forest") -> PhonePalette(
            bg = Color(0xFFEDF3EE), card = Color(0xFFFBFDFB), cardAlt = Color(0xFFE3EEE7),
            stroke = Color(0xFFCFDDD3), ink = Color(0xFF132A1D), muted = Color(0xFF5F7166),
            accent = Color(0xFF1F7A43), teal = Color(0xFF2A8F72), green = Color(0xFF1F7A43),
            red = Color(0xFF9E5B3A), orange = Color(0xFFB98A2E), purple = Color(0xFF6B5AA8),
            pink = Color(0xFFA8507F), dark = false
        )
        n.contains("ocean") -> PhonePalette(
            bg = Color(0xFFEEF3F8), card = Color(0xFFFFFFFF), cardAlt = Color(0xFFE4EDF6),
            stroke = Color(0xFFCFDBE7), ink = Color(0xFF14273A), muted = Color(0xFF5E7189),
            accent = Color(0xFF1F6DA8), teal = Color(0xFF2A8FA8), green = Color(0xFF1F8A6F),
            red = Color(0xFFB0603C), orange = Color(0xFFC98A2E), purple = Color(0xFF6B5AA8),
            pink = Color(0xFFA8507F), dark = false
        )
        n.contains("plum") -> PhonePalette(
            bg = Color(0xFFF5F0F6), card = Color(0xFFFFFFFF), cardAlt = Color(0xFFEFE6F2),
            stroke = Color(0xFFE0D4E4), ink = Color(0xFF2B1830), muted = Color(0xFF75657C),
            accent = Color(0xFF7A3E8C), teal = Color(0xFF2A8F8C), green = Color(0xFF2F7D5B),
            red = Color(0xFFA8506B), orange = Color(0xFFB98A2E), purple = Color(0xFF7A3E8C),
            pink = Color(0xFFB0567F), dark = false
        )
        else -> PhonePalette(
            bg = Color(0xFFF4F0E7), card = Color(0xFFFBFAF7), cardAlt = Color(0xFFEDE8DC),
            stroke = Color(0xFFD7DDD5), ink = Color(0xFF18352E), muted = Color(0xFF6D7D75),
            accent = Color(0xFF237B71), teal = Color(0xFF2A8F8A), green = Color(0xFF1F7A43),
            red = Color(0xFFA8503C), orange = Color(0xFFB98A2E), purple = Color(0xFF6B5AA8),
            pink = Color(0xFFA8507F), dark = false
        )
    }
}
