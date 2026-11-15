package com.tag.routine.data

/**
 * Eight-colour routine palette tuned for clear contrast in both light and dark themes.
 */
data class RoutineColorSpec(
    val id: Int,
    val lightHex: Long,
    val darkHex: Long,
)

object RoutineColors {
    val all: List<RoutineColorSpec> = listOf(
        RoutineColorSpec(id = 0, lightHex = 0xFF6352F5, darkHex = 0xFF8C7DFF), // Violet
        RoutineColorSpec(id = 1, lightHex = 0xFF0F766E, darkHex = 0xFF2DD4BF), // Teal
        RoutineColorSpec(id = 2, lightHex = 0xFFB45309, darkHex = 0xFFFBBF24), // Amber
        RoutineColorSpec(id = 3, lightHex = 0xFFBE123C, darkHex = 0xFFFB7185), // Rose / Coral
        RoutineColorSpec(id = 4, lightHex = 0xFF0369A1, darkHex = 0xFF38BDF8), // Sky
        RoutineColorSpec(id = 5, lightHex = 0xFF047857, darkHex = 0xFF34D399), // Emerald
        RoutineColorSpec(id = 6, lightHex = 0xFF7E22CE, darkHex = 0xFFC084FC), // Purple
        RoutineColorSpec(id = 7, lightHex = 0xFFC2410C, darkHex = 0xFFFB923C), // Sunset
    )

    fun byId(id: Int): RoutineColorSpec = all.getOrElse(id) { all[0] }

    fun hexFor(id: Int, isDark: Boolean): Long {
        val spec = byId(id)
        return if (isDark) spec.darkHex else spec.lightHex
    }
}

/**
 * Emoji icons for routines, with seamless fallback mapping for legacy v1/v2 icon keys.
 */
object RoutineEmojis {
    const val DEFAULT = "✨"

    val all: List<String> = listOf(
        "☀️", "💧", "🏃", "💪",
        "📖", "🧘", "✍️", "🚶",
        "💊", "☕", "💻", "🎵",
        "😴", "🧹", "📞", "❤️",
        "🧠", "🌿", "🎯", "✨",
    )

    private val legacyMap: Map<String, String> = mapOf(
        "sunrise" to "☀️",
        "water" to "💧",
        "run" to "🏃",
        "gym" to "💪",
        "book" to "📖",
        "meditate" to "🧘",
        "journal" to "✍️",
        "walk" to "🚶",
        "pill" to "💊",
        "coffee" to "☕",
        "code" to "💻",
        "music" to "🎵",
        "sleep" to "😴",
        "broom" to "🧹",
        "call" to "📞",
        "heart" to "❤️",
        "brain" to "🧠",
        "leaf" to "🌿",
        "target" to "🎯",
        "spark" to "✨",
    )

    fun resolve(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return DEFAULT
        return legacyMap[trimmed] ?: trimmed
    }
}
