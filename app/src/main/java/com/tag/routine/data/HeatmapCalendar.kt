package com.tag.routine.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Visual bucket for a single day cell inside the 12-week activity heatmap. */
enum class HeatmapIntensity {
    /** Calendar day strictly after today (placeholder in the active week). */
    FUTURE,

    /** Day with zero scheduled items — neutral, never treated as a failure. */
    NEUTRAL,

    /** Scheduled items existed, but none were checked off (0%). */
    ZERO_DONE,

    /** Greater than 0% and below 35% completed. */
    LEVEL_1,

    /** 35% to 69% completed. */
    LEVEL_2,

    /** 70% to 99% completed. */
    LEVEL_3,

    /** 100% of scheduled items completed. */
    COMPLETE,
}

/** One day inside a calendar-aligned heatmap column. */
data class HeatmapCell(
    val date: LocalDate,
    val stat: DayStat?,
    val isFuture: Boolean,
    val isToday: Boolean,
    val intensity: HeatmapIntensity,
)

/** One 7-day calendar week column starting on [weekStart]. */
data class HeatmapWeekColumn(
    val weekStart: LocalDate,
    val cells: List<HeatmapCell>,
)

/**
 * Pure calendar builder for the trailing 12-week heatmap.
 *
 * Aligns weeks to [firstDayOfWeek] (Saturday for Persian, Monday otherwise) so weekday rows
 * stay consistent across month and year boundaries, distinguishes neutral empty days from
 * 0%-completion days, and pads trailing future days in the current week as non-interactive
 * placeholders.
 */
object HeatmapCalendar {

    const val DEFAULT_WEEKS = 12

    fun build(
        stats: List<DayStat>,
        today: LocalDate = Dates.today(),
        weeks: Int = DEFAULT_WEEKS,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): List<HeatmapWeekColumn> {
        val safeWeeks = weeks.coerceAtLeast(1)
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val gridStart = currentWeekStart.minusWeeks((safeWeeks - 1).toLong())
        val statsByDate = stats.associateBy { it.date }

        return (0 until safeWeeks).map { weekOffset ->
            val weekStart = gridStart.plusWeeks(weekOffset.toLong())
            val cells = (0 until 7).map { dayOffset ->
                val date = weekStart.plusDays(dayOffset.toLong())
                if (date.isAfter(today)) {
                    HeatmapCell(
                        date = date,
                        stat = null,
                        isFuture = true,
                        isToday = false,
                        intensity = HeatmapIntensity.FUTURE,
                    )
                } else {
                    val stat = statsByDate[date] ?: DayStat(
                        date = date,
                        scheduled = 0,
                        completed = 0,
                        taskCompleted = 0,
                    )
                    HeatmapCell(
                        date = date,
                        stat = stat,
                        isFuture = false,
                        isToday = date == today,
                        intensity = classify(stat),
                    )
                }
            }
            HeatmapWeekColumn(weekStart = weekStart, cells = cells)
        }
    }

    /** Seven consecutive dates starting at [firstDayOfWeek], suitable for row labels. */
    fun weekdayDates(firstDayOfWeek: DayOfWeek): List<LocalDate> {
        val anchor = LocalDate.of(2024, 1, 1).with(TemporalAdjusters.nextOrSame(firstDayOfWeek))
        return (0 until 7).map { anchor.plusDays(it.toLong()) }
    }

    fun classify(stat: DayStat): HeatmapIntensity = when {
        !stat.hasData -> HeatmapIntensity.NEUTRAL
        stat.completed <= 0 -> HeatmapIntensity.ZERO_DONE
        stat.ratio >= 0.999f -> HeatmapIntensity.COMPLETE
        stat.ratio >= 0.70f -> HeatmapIntensity.LEVEL_3
        stat.ratio >= 0.35f -> HeatmapIntensity.LEVEL_2
        else -> HeatmapIntensity.LEVEL_1
    }
}
