package com.tag.routine.data

import java.util.Locale

/**
 * One title the user has typed before, ready to be applied to the editor in a single tap.
 *
 * [routine] is the most recent routine carrying [title], so its icon, colour and time are the
 * ones the user last chose for that title. [uses] is how many routines share the title, which
 * is the "how often do I do this" signal used for ordering.
 */
data class RoutineSuggestion(
    val title: String,
    val routine: RoutineEntity,
    val uses: Int,
)

/**
 * Builds the quick-add suggestions offered while the user types a routine title.
 *
 * Everything is derived from routines that already exist — no extra storage and no schema
 * change. Titles are grouped case- and space-insensitively, so a title used five times is a
 * single suggestion with `uses = 5`, then ranked by how closely the title matches what has
 * been typed and, at equal closeness, by how often the user has used it.
 */
object SuggestionEngine {

    /** The most suggestions the editor ever shows. */
    const val MAX_SUGGESTIONS = 5

    private val WHITESPACE = Regex("[\\s\\u200C]+")

    /**
     * Case-, whitespace-, and Persian/Arabic-variant-insensitive form of a title, used for
     * grouping and matching without modifying stored display titles.
     */
    fun normalize(value: String): String =
        value
            .replace('\u064A', '\u06CC')
            .replace('\u0643', '\u06A9')
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)

    /**
     * Up to [limit] suggestions for [query], best first.
     *
     * Ranking, highest first: an exact title match, then a title starting with the query,
     * then a title containing it, then a title containing every word of it. Ties break on
     * how often the title was used, so the user's habits win over a one-off entry, and then
     * alphabetically so the list never reshuffles between keystrokes.
     */
    fun suggest(
        routines: List<RoutineEntity>,
        query: String,
        limit: Int = MAX_SUGGESTIONS,
    ): List<RoutineSuggestion> {
        val needle = normalize(query)
        if (needle.isEmpty() || limit <= 0) return emptyList()

        return routines
            .filter { !it.archived && it.title.isNotBlank() }
            .groupBy { normalize(it.title) }
            .mapNotNull { (key, items) ->
                val score = score(key, needle)
                if (score <= 0) return@mapNotNull null
                val representative = items.maxWithOrNull(
                    compareBy<RoutineEntity> { it.createdAt }.thenBy { it.sortOrder },
                ) ?: items.first()
                Ranked(
                    score = score,
                    suggestion = RoutineSuggestion(
                        title = representative.title.trim(),
                        routine = representative,
                        uses = items.size,
                    ),
                )
            }
            .sortedWith(
                compareByDescending<Ranked> { it.score }
                    .thenByDescending { it.suggestion.uses }
                    .thenBy { it.suggestion.title.lowercase(Locale.ROOT) },
            )
            .take(limit)
            .map { it.suggestion }
    }

    /** How closely [title] (already normalised) matches [needle] (already normalised). */
    private fun score(title: String, needle: String): Int = when {
        title == needle -> 4
        title.startsWith(needle) -> 3
        title.contains(needle) -> 2
        containsEveryWord(title, needle) -> 1
        else -> 0
    }

    /** True when every word of [needle] appears as a whole word of [title]. */
    private fun containsEveryWord(title: String, needle: String): Boolean {
        val haystack = title.split(' ').filter { it.isNotEmpty() }
        val words = needle.split(' ').filter { it.isNotEmpty() }
        return words.isNotEmpty() && words.all { word -> word in haystack }
    }

    private data class Ranked(val score: Int, val suggestion: RoutineSuggestion)
}
