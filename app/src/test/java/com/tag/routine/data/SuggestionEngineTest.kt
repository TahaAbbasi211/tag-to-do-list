package com.tag.routine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the quick-add suggestions: they are built only from routines that already exist,
 * they come back in a stable, useful order, and there are never more than five.
 */
class SuggestionEngineTest {

    private var createdAt = 0L

    private fun routine(
        id: String,
        title: String,
        icon: String = "☀️",
        colorId: Int = 1,
        timeMinutes: Int? = 7 * 60,
        archived: Boolean = false,
    ) = RoutineEntity(
        id = id,
        title = title,
        groupName = Groups.MORNING,
        icon = icon,
        timeMinutes = timeMinutes,
        daysMask = Dates.EVERY_DAY,
        reminder = false,
        colorHex = RoutineColors.hexFor(colorId, false),
        colorId = colorId,
        sortOrder = 0,
        archived = archived,
        createdAt = createdAt++,
        repeatDaily = true,
    )

    @Test
    fun `a blank query suggests nothing`() {
        val routines = listOf(routine("1", "Drink water"))
        assertEquals(emptyList<RoutineSuggestion>(), SuggestionEngine.suggest(routines, ""))
        assertEquals(emptyList<RoutineSuggestion>(), SuggestionEngine.suggest(routines, "   "))
    }

    @Test
    fun `suggestions come from existing routines only`() {
        val routines = listOf(routine("1", "Drink water"), routine("2", "Read a book"))
        val result = SuggestionEngine.suggest(routines, "drink")
        assertEquals(listOf("Drink water"), result.map { it.title })
    }

    @Test
    fun `at most five suggestions are returned`() {
        val routines = (1..9).map { routine("$it", "Stretch $it") }
        val result = SuggestionEngine.suggest(routines, "stretch")
        assertEquals(SuggestionEngine.MAX_SUGGESTIONS, result.size)
        assertEquals(5, result.size)
    }

    @Test
    fun `an exact match ranks first`() {
        val routines = listOf(
            routine("1", "Drink water now"),
            routine("2", "Drink"),
            routine("3", "Drink water"),
        )
        val result = SuggestionEngine.suggest(routines, "Drink water")
        assertEquals("Drink water", result.first().title)
    }

    @Test
    fun `prefix matches rank above inner matches`() {
        val routines = listOf(
            routine("1", "Morning water"),
            routine("2", "Water the plants"),
        )
        val result = SuggestionEngine.suggest(routines, "water")
        assertEquals(listOf("Water the plants", "Morning water"), result.map { it.title })
    }

    @Test
    fun `more used titles rank above less used ones at the same match level`() {
        val routines = listOf(
            routine("1", "Evening walk"),
            routine("2", "Walk the dog"),
            routine("3", "Walk the dog"),
            routine("4", "Walk the dog"),
        )
        val result = SuggestionEngine.suggest(routines, "walk")
        assertEquals("Walk the dog", result.first().title)
        assertEquals(3, result.first().uses)
        assertEquals("Evening walk", result[1].title)
        assertEquals(1, result[1].uses)
    }

    @Test
    fun `the same title is grouped into one suggestion`() {
        val routines = listOf(
            routine("1", "Meditate"),
            routine("2", "meditate "),
            routine("3", "MEDITATE"),
        )
        val result = SuggestionEngine.suggest(routines, "med")
        assertEquals(1, result.size)
        assertEquals(3, result.single().uses)
    }

    @Test
    fun `the suggestion carries the latest routine so icon colour and time can be applied`() {
        val routines = listOf(
            routine("old", "Drink water", icon = "💧", colorId = 0, timeMinutes = 6 * 60),
            routine("new", "Drink water", icon = "☕", colorId = 5, timeMinutes = 9 * 60),
        )
        val suggestion = SuggestionEngine.suggest(routines, "drink").single()
        assertEquals("☕", suggestion.routine.icon)
        assertEquals(5, suggestion.routine.colorId)
        assertEquals(9 * 60, suggestion.routine.timeMinutes)
    }

    @Test
    fun `archived routines are not suggested`() {
        val routines = listOf(routine("1", "Old habit", archived = true))
        assertEquals(emptyList<RoutineSuggestion>(), SuggestionEngine.suggest(routines, "old"))
    }

    @Test
    fun `matching is case insensitive and tolerates extra spaces`() {
        val routines = listOf(routine("1", "  Drink   Water  "))
        val result = SuggestionEngine.suggest(routines, "drink water")
        assertEquals(1, result.size)
        assertEquals("Drink   Water", result.single().title)
    }

    @Test
    fun `a multi word query matches a title containing every word`() {
        val routines = listOf(routine("1", "Stretch and breathe"))
        val result = SuggestionEngine.suggest(routines, "breathe stretch")
        assertEquals(1, result.size)
        assertTrue(result.single().title.contains("Stretch"))
    }

    @Test
    fun `persian normalization matches zwnj and arabic letter variants`() {
        val routines = listOf(
            routine("1", "برنامه‌ریزی روزانه"),
            routine("2", "نوشیدن آب"),
            routine("3", "نوشيدن آب"), // Arabic yeh variant
        )
        val planHits = SuggestionEngine.suggest(routines, "برنامه ریزی")
        assertEquals(listOf("برنامه‌ریزی روزانه"), planHits.map { it.title })

        val waterHits = SuggestionEngine.suggest(routines, "نوشیدن")
        assertEquals(1, waterHits.size)
        assertEquals(2, waterHits.single().uses)
    }

    @Test
    fun `ranking combines match tier then frequency then alphabetical tie break within five items`() {
        val routines = listOf(
            routine("1", "Run"),
            routine("2", "Run fast"),
            routine("3", "Run fast"),
            routine("4", "Run slow"),
            routine("5", "Morning run"),
            routine("6", "Morning run"),
            routine("7", "Morning run"),
            routine("8", "Evening run"),
            routine("9", "Night run"),
        )
        val result = SuggestionEngine.suggest(routines, "run")
        assertEquals(5, result.size)
        // Exact ("Run") -> Prefix by frequency ("Run fast" x2, "Run slow" x1) -> Substring by frequency ("Morning run" x3, "Evening run" x1)
        assertEquals(
            listOf("Run", "Run fast", "Run slow", "Morning run", "Evening run"),
            result.map { it.title },
        )
        assertEquals(emptyList<RoutineSuggestion>(), SuggestionEngine.suggest(routines, "run", limit = 0))
    }
}
