package com.tag.routine.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/** Comparison outcome between the current calendar week and the previous calendar week. */
enum class WeekComparison {
    /** Current week is complete and finished higher than the previous week. */
    IMPROVED,

    /** Current week is complete and finished lower than the previous week. */
    DECLINED,

    /** Current week is complete and matched the previous week's completion percentage. */
    STEADY,

    /** Current week has not ended yet, so a full-week comparison would be misleading. */
    INCOMPLETE_WEEK,

    /** One or both weeks have no scheduled items, so there is no valid baseline. */
    NO_BASELINE,
}

/** One routine item highlighted in the weekly report along with its completion percentage. */
data class ReportedItem(
    val routine: RoutineEntity,
    val rate: Float,
    val percent: Int,
)

/** Pure summary of the current and previous calendar weeks derived from [StatsEngine]. */
data class WeeklyReport(
    val currentWeekStart: LocalDate,
    val currentWeekEnd: LocalDate,
    val previousWeekStart: LocalDate,
    val previousWeekEnd: LocalDate,
    val elapsedDaysInCurrentWeek: Int,
    val isCurrentWeekIncomplete: Boolean,
    val currentWeekPercent: Int?,
    val previousWeekPercent: Int?,
    val deltaPercent: Int?,
    val comparison: WeekComparison,
    val perfectDaysCurrentWeek: Int,
    val activeDaysCurrentWeek: Int,
    val perfectDaysPreviousWeek: Int,
    val activeDaysPreviousWeek: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val bestItem: ReportedItem?,
    val weakestItem: ReportedItem?,
) {
    val hasAnyWeekData: Boolean
        get() = activeDaysCurrentWeek > 0 || activeDaysPreviousWeek > 0
}

/**
 * Computes weekly report metrics purely from existing [StatsEngine] outputs ([Insights] and
 * [StatsEngine.routineRates]). Days with zero scheduled items are treated as neutral rather than
 * failures, and an incomplete current week is never compared misleadingly against a full previous
 * week.
 */
object WeeklyReportEngine {

    const val DAYS_IN_WEEK = 7
    const val DEFAULT_ROUTINE_RATE_WINDOW = 30

    /**
     * Week start day used by the report and heatmap: always Saturday in Jalali mode;
     * in Gregorian mode, Saturday for Persian (`fa`) and Monday otherwise.
     */
    fun defaultFirstDayOfWeek(
        calendarType: CalendarType,
        locale: Locale = Locale.getDefault(),
    ): DayOfWeek = Dates.firstDayOfWeek(calendarType, locale)

    fun defaultFirstDayOfWeek(locale: Locale = Locale.getDefault()): DayOfWeek =
        Dates.firstDayOfWeek(CalendarType.GREGORIAN, locale)

    /**
     * Builds a [WeeklyReport] from existing [StatsEngine] outputs.
     */
    fun compute(
        insights: Insights,
        routineRates: List<Pair<RoutineEntity, Float>>,
        today: LocalDate = Dates.today(),
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        routineRateWindow: Int = DEFAULT_ROUTINE_RATE_WINDOW,
    ): WeeklyReport {
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val currentWeekEnd = currentWeekStart.plusDays((DAYS_IN_WEEK - 1).toLong())
        val previousWeekStart = currentWeekStart.minusWeeks(1)
        val previousWeekEnd = currentWeekStart.minusDays(1)

        val elapsedDays = (ChronoUnit.DAYS.between(currentWeekStart, today).toInt() + 1)
            .coerceIn(1, DAYS_IN_WEEK)
        val isCurrentWeekIncomplete = today.isBefore(currentWeekEnd)

        val daysByDate = insights.days.associateBy { it.date }
        val currentDays = (0 until elapsedDays).mapNotNull { offset ->
            daysByDate[currentWeekStart.plusDays(offset.toLong())]
        }
        val previousDays = (0 until DAYS_IN_WEEK).mapNotNull { offset ->
            daysByDate[previousWeekStart.plusDays(offset.toLong())]
        }

        // Days with zero scheduled items (`!hasData`) are neutral and never pull the average down.
        val currentActive = currentDays.filter { it.hasData }
        val previousActive = previousDays.filter { it.hasData }

        val currentWeekPercent = averagePercent(currentActive)
        val previousWeekPercent = averagePercent(previousActive)

        val deltaPercent: Int?
        val comparison: WeekComparison
        when {
            currentWeekPercent == null || previousWeekPercent == null -> {
                deltaPercent = null
                comparison = WeekComparison.NO_BASELINE
            }
            isCurrentWeekIncomplete -> {
                deltaPercent = null
                comparison = WeekComparison.INCOMPLETE_WEEK
            }
            else -> {
                val delta = currentWeekPercent - previousWeekPercent
                deltaPercent = delta
                comparison = when {
                    delta > 0 -> WeekComparison.IMPROVED
                    delta < 0 -> WeekComparison.DECLINED
                    else -> WeekComparison.STEADY
                }
            }
        }

        val safeWindow = routineRateWindow.coerceAtLeast(1)
        val windowDates = (0 until safeWindow).map { today.minusDays(it.toLong()) }
        val scheduledRates = routineRates
            .filter { (routine, rate) ->
                !routine.archived &&
                    !rate.isNaN() &&
                    windowDates.any { date -> StatsEngine.isScheduledOn(routine, date) }
            }
            .map { (routine, rate) ->
                val clamped = rate.coerceIn(0f, 1f)
                ReportedItem(
                    routine = routine,
                    rate = clamped,
                    percent = (clamped * 100f).roundToInt().coerceIn(0, 100),
                )
            }
            .sortedWith(
                compareByDescending<ReportedItem> { it.rate }
                    .thenBy { it.routine.title.lowercase(Locale.ROOT) }
                    .thenBy { it.routine.id },
            )

        val bestItem = scheduledRates.firstOrNull { it.rate > 0f }
        val weakestCandidate = scheduledRates
            .filter { it.routine.id != bestItem?.routine?.id && it.rate < 1f }
            .minWithOrNull(
                compareBy<ReportedItem> { it.rate }
                    .thenBy { it.routine.title.lowercase(Locale.ROOT) }
                    .thenBy { it.routine.id },
            )
        val weakestItem = weakestCandidate?.takeIf { candidate ->
            bestItem == null || candidate.rate < bestItem.rate
        }

        return WeeklyReport(
            currentWeekStart = currentWeekStart,
            currentWeekEnd = currentWeekEnd,
            previousWeekStart = previousWeekStart,
            previousWeekEnd = previousWeekEnd,
            elapsedDaysInCurrentWeek = elapsedDays,
            isCurrentWeekIncomplete = isCurrentWeekIncomplete,
            currentWeekPercent = currentWeekPercent,
            previousWeekPercent = previousWeekPercent,
            deltaPercent = deltaPercent,
            comparison = comparison,
            perfectDaysCurrentWeek = currentActive.count { it.isPerfect },
            activeDaysCurrentWeek = currentActive.size,
            perfectDaysPreviousWeek = previousActive.count { it.isPerfect },
            activeDaysPreviousWeek = previousActive.size,
            currentStreak = insights.currentStreak,
            bestStreak = insights.bestStreak,
            bestItem = bestItem,
            weakestItem = weakestItem,
        )
    }

    /** Convenience overload computing [StatsEngine] inputs directly from raw entities. */
    fun compute(
        routines: List<RoutineEntity>,
        routineLogs: List<RoutineLogEntity>,
        taskLogs: List<TaskLogEntity> = emptyList(),
        today: LocalDate = Dates.today(),
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        routineRateWindow: Int = DEFAULT_ROUTINE_RATE_WINDOW,
    ): WeeklyReport {
        val insights = StatsEngine.compute(routines, routineLogs, taskLogs, today)
        val rates = StatsEngine.routineRates(routines, routineLogs, today, routineRateWindow)
        return compute(
            insights = insights,
            routineRates = rates,
            today = today,
            firstDayOfWeek = firstDayOfWeek,
            routineRateWindow = routineRateWindow,
        )
    }

    private fun averagePercent(activeDays: List<DayStat>): Int? {
        if (activeDays.isEmpty()) return null
        return (activeDays.map { it.ratio.toDouble() }.average() * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }
}
