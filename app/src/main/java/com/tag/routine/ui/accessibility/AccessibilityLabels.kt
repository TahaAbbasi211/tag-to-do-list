package com.tag.routine.ui.accessibility

import com.tag.routine.data.CalendarType
import com.tag.routine.data.Dates
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure, framework-free builders for the sentences TalkBack reads out.
 *
 * The UI only supplies already-localised words (via `stringResource`) plus the active
 * [Locale]; every number, date and calendar conversion goes through [Dates] so Persian
 * digits and the selected Jalali/Gregorian calendar stay consistent with the visible text.
 * Keeping this free of Compose and Android resources makes it directly unit-testable.
 */
object AccessibilityLabels {

    /** Persian uses the Arabic comma; every other shipped language uses a Latin comma. */
    private fun separator(locale: Locale): String =
        if (locale.language.equals("fa", ignoreCase = true)) "، " else ", "

    /**
     * One Today timeline row read as a single sentence: title, clock time (or the
     * "no time" wording) and completion state.
     */
    fun routineRow(
        title: String,
        timeLabel: String?,
        done: Boolean,
        doneLabel: String,
        notDoneLabel: String,
        noTimeLabel: String,
        locale: Locale,
    ): String {
        val parts = buildList {
            add(title.trim())
            add(timeLabel?.trim()?.takeIf { it.isNotEmpty() } ?: noTimeLabel)
            add(if (done) doneLabel else notDoneLabel)
        }
        return Dates.formatDigits(parts.joinToString(separator(locale)), locale)
    }

    /** State text for the completion control (TalkBack announces it as "checked/unchecked"). */
    fun checkState(
        done: Boolean,
        doneLabel: String,
        notDoneLabel: String,
    ): String = if (done) doneLabel else notDoneLabel

    /**
     * One heatmap cell: calendar-aware date plus completion percentage and counts.
     *
     * [cellPattern] must contain `%1$s` (date), `%2$s` (percentage), `%3$d` (completed)
     * and `%4$d` (scheduled). With no scheduled items the [noItemsLabel] is used instead.
     */
    fun heatmapCell(
        date: LocalDate,
        scheduled: Int?,
        completed: Int?,
        locale: Locale,
        calendarType: CalendarType,
        noItemsLabel: String,
        cellPattern: String,
    ): String {
        val dateText = Dates.dayMonth(date, locale, calendarType)
        if (scheduled == null || scheduled <= 0 || completed == null) {
            return Dates.formatDigits("$dateText${separator(locale)}$noItemsLabel", locale)
        }
        val percent = (completed.toFloat() / scheduled.toFloat() * 100f)
            .roundToInt()
            .coerceIn(0, 100)
        val text = String.format(
            Locale.ROOT,
            cellPattern,
            dateText,
            Dates.formatPercent(percent, locale),
            completed,
            scheduled,
        )
        return Dates.formatDigits(text, locale)
    }

    /**
     * The seven-day bar chart as text: one "weekday percentage" pair per bar, using the
     * no-data label for days without scheduled items.
     */
    fun chartSeries(
        entries: List<Pair<LocalDate, Int?>>,
        locale: Locale,
        noDataLabel: String,
    ): String = entries.joinToString(separator(locale)) { (date, percent) ->
        val day = Dates.dayOfWeekLabel(date, locale)
        val value = percent?.let { Dates.formatPercent(it, locale) } ?: noDataLabel
        "$day $value"
    }
}
