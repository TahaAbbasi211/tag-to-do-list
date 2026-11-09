package com.tag.routine.data

import android.content.res.Resources
import com.tag.routine.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// ---------------------------------------------------------------- constants

object Groups {
    // These strings are persisted in the database as identifiers. Never change them;
    // only the *labels* shown in the UI are localised (see `label`).
    const val MORNING = "Morning"
    const val AFTERNOON = "Afternoon"
    const val EVENING = "Evening"
    const val ANYTIME = "Anytime"

    val ordered = listOf(MORNING, AFTERNOON, EVENING, ANYTIME)

    fun rank(name: String): Int = ordered.indexOf(name).let { if (it < 0) ordered.size else it }

    /** Localised display label for a stored group key. */
    fun label(name: String, resources: Resources? = null): String {
        val fallback = when (name) {
            MORNING -> "Morning"
            AFTERNOON -> "Afternoon"
            EVENING -> "Evening"
            ANYTIME -> "Anytime"
            else -> name
        }
        val id = when (name) {
            MORNING -> R.string.group_morning
            AFTERNOON -> R.string.group_afternoon
            EVENING -> R.string.group_evening
            ANYTIME -> R.string.group_anytime
            else -> return fallback
        }
        return resources?.getString(id) ?: fallback
    }
}

object Priorities {
    const val LOW = 0
    const val NORMAL = 1
    const val HIGH = 2

    /** Localised display label for a stored priority value. */
    fun label(value: Int, resources: Resources? = null): String {
        val fallback = when (value) {
            LOW -> "Low"
            HIGH -> "High"
            else -> "Normal"
        }
        val id = when (value) {
            LOW -> R.string.priority_low
            HIGH -> R.string.priority_high
            else -> R.string.priority_medium
        }
        return resources?.getString(id) ?: fallback
    }
}

/** Icon keys are resolved to vector icons in the UI layer. */
object RoutineIcons {
    val all = listOf(
        "sunrise", "water", "run", "gym", "book", "meditate", "journal", "walk",
        "pill", "coffee", "code", "music", "sleep", "broom", "call", "heart",
        "brain", "leaf", "target", "spark",
    )

    fun defaultFor(group: String): String = when (group) {
        Groups.MORNING -> "sunrise"
        Groups.AFTERNOON -> "target"
        Groups.EVENING -> "sleep"
        else -> "spark"
    }
}

/** Colour palette used for tags and routine accents. */
object Palette {
    const val VIOLET = 0xFF7C6CFF
    const val INDIGO = 0xFF5B7CFF
    const val TEAL = 0xFF2ED3B7
    const val MINT = 0xFF4FD9C0
    const val AMBER = 0xFFFFB65C
    const val CORAL = 0xFFFF7A6B
    const val ROSE = 0xFFFF6B9D
    const val SKY = 0xFF4CC9F0
    const val LIME = 0xFF9BE15D
    const val LAVENDER = 0xFFB39DFF
    const val SLATE = 0xFF8C9BB5
    const val GOLD = 0xFFF5C451

    val swatches = listOf(
        VIOLET, INDIGO, SKY, TEAL, MINT, LIME, AMBER, GOLD, CORAL, ROSE, LAVENDER, SLATE,
    )

    fun at(index: Int): Long = swatches[((index % swatches.size) + swatches.size) % swatches.size]
}

// ---------------------------------------------------------------- settings

data class AppSettings(
    val themeMode: Int = ThemeMode.SYSTEM,
    val themePalette: String = Palettes.VIOLET,
    val language: String = "",
    val userName: String = "",
    val notificationsEnabled: Boolean = true,
    val dailyDigestEnabled: Boolean = true,
    val dailyDigestMinutes: Int = 8 * 60,
    val hapticsEnabled: Boolean = true,
    /** True once the first-launch onboarding has been completed or skipped. */
    val onboarded: Boolean = false,
    /** Last focus length the user chose, in minutes. Drives the Focus section default. */
    val focusMinutes: Int = AppConstants.DEFAULT_FOCUS_MINUTES,
) {
    object ThemeMode {
        const val SYSTEM = 0
        const val LIGHT = 1
        const val DARK = 2
    }

    /** Colour-theme keys resolved in the UI layer. */
    object Palettes {
        const val VIOLET = "violet"
        const val SKY = "sky"
        const val PURPLE = "purple"
        const val PINK = "pink"
        const val TEAL = "teal"
        const val SUNSET = "sunset"
    }
}

// ---------------------------------------------------------------- focus run

/**
 * A focus run that is still in progress, exactly as it is stored on disk.
 *
 * The timer is described by wall-clock timestamps rather than a tick counter, because
 * the app process can be frozen or killed at any moment while the user is away. The
 * only durable truth is *when* the current segment began and how many seconds earlier
 * segments accumulated, so elapsed time is always recomputed from the clock.
 *
 * [id] makes finishing idempotent: the foreground service and the backup alarm can both
 * try to close the same run, and only the first one to still find it on disk records it.
 */
data class ActiveFocusRun(
    val id: String,
    val routineId: String?,
    val routineTitle: String?,
    val plannedMinutes: Int,
    /** Epoch millis the running segment began, or null while paused. */
    val segmentStartedAt: Long?,
    /** Seconds banked by segments that already ended (i.e. before the last pause). */
    val accumulatedSeconds: Int,
    /** Epoch millis the run was first started, used to date the recorded session. */
    val startedAt: Long,
) {
    val running: Boolean get() = segmentStartedAt != null

    val totalSeconds: Int get() = plannedMinutes * 60

    /**
     * Seconds elapsed by [now], counting the live segment when the run is running.
     * Clamped so a backwards clock jump can never produce a negative time.
     */
    fun elapsedSeconds(now: Long = System.currentTimeMillis()): Int {
        val segmentStart = segmentStartedAt ?: return accumulatedSeconds
        val live = ((now - segmentStart) / 1000L).coerceIn(0L, Int.MAX_VALUE.toLong())
        return (accumulatedSeconds + live).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun remainingSeconds(now: Long = System.currentTimeMillis()): Int =
        (totalSeconds - elapsedSeconds(now)).coerceAtLeast(0)

    /** True once the run has reached its planned length. */
    fun isComplete(now: Long = System.currentTimeMillis()): Boolean =
        elapsedSeconds(now) >= totalSeconds
}

/**
 * Serialises [ActiveFocusRun] to a single preferences string.
 *
 * The free-text fields are Base64-encoded so a routine title containing the field
 * separator can never corrupt the record. A run that fails to parse is treated as
 * absent rather than crashing the app on launch.
 */
object ActiveFocusRunCodec {

    private const val VERSION = "1"
    private const val SEPARATOR = "\t"

    fun encode(run: ActiveFocusRun): String = listOf(
        VERSION,
        run.id,
        encodeText(run.routineId),
        encodeText(run.routineTitle),
        run.plannedMinutes.toString(),
        (run.segmentStartedAt ?: 0L).toString(),
        run.accumulatedSeconds.toString(),
        run.startedAt.toString(),
    ).joinToString(SEPARATOR)

    fun decode(raw: String?): ActiveFocusRun? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(SEPARATOR)
        if (parts.size != 8 || parts[0] != VERSION) return null
        return runCatching {
            ActiveFocusRun(
                id = parts[1],
                routineId = decodeText(parts[2]),
                routineTitle = decodeText(parts[3]),
                plannedMinutes = AppConstants.clampFocusMinutes(parts[4].toInt()),
                segmentStartedAt = parts[5].toLong().takeIf { it > 0L },
                accumulatedSeconds = parts[6].toInt().coerceAtLeast(0),
                startedAt = parts[7].toLong(),
            )
        }.getOrNull()?.takeIf { it.id.isNotBlank() }
    }

    private fun encodeText(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val bytes = value.toByteArray(Charsets.UTF_8)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun decodeText(value: String): String? {
        if (value.isEmpty()) return null
        return runCatching {
            String(java.util.Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        }.getOrNull()
    }
}

// ---------------------------------------------------------------- dates

object Dates {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): LocalDate = LocalDate.now()

    fun iso(date: LocalDate): String = date.format(iso)

    fun parse(value: String): LocalDate? = runCatching { LocalDate.parse(value, iso) }.getOrNull()

    /** Monday based bit for [daysMask] (bit 0 == Monday). */
    fun weekdayBit(date: LocalDate): Int = 1 shl (date.dayOfWeek.value - 1)

    const val EVERY_DAY = 0b1111111

    /**
     * Formatters are built per call rather than cached, because the active locale changes
     * at runtime when the user switches language inside the app.
     */
    private fun formatter(pattern: String, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern, locale)

    fun dayMonth(date: LocalDate): String = date.format(formatter("EEE, d MMM"))

    fun full(date: LocalDate): String = date.format(formatter("EEEE, d MMMM"))

    /** Single-letter weekday used by the compact week strip. */
    fun shortDayName(date: LocalDate): String =
        date.format(formatter("EEEEE")).uppercase(Locale.getDefault())

    fun shortDate(date: LocalDate): String = date.format(formatter("d MMM"))

    fun formatMinutes(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }

    /** Alias kept for call sites that describe a clock label rather than an offset. */
    fun timeLabel(minutes: Int): String = formatMinutes(minutes)

    /**
     * Relative label for [date] compared with [today].
     *
     * [resources] is optional: pass the Compose/Android resources to get a localised label,
     * or omit it and the English fallback is used (useful in unit tests).
     */
    fun relativeDayLabel(
        date: LocalDate,
        today: LocalDate,
        resources: Resources? = null,
    ): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> str(resources, R.string.day_today, "Today")
            days == 1L -> str(resources, R.string.day_tomorrow, "Tomorrow")
            days == -1L -> str(resources, R.string.day_yesterday, "Yesterday")
            days in 2..6 -> str(resources, R.string.day_in_days, "In $days days", days.toInt())
            days in -6..-2 ->
                str(resources, R.string.day_days_ago, "${-days} days ago", (-days).toInt())
            else -> dayMonth(date)
        }
    }

    fun dayOfWeekLabel(date: LocalDate): String = date.format(formatter("EEE"))

    fun greeting(
        hour: Int = java.time.LocalTime.now().hour,
        resources: Resources? = null,
    ): String = when {
        hour < 5 -> str(resources, R.string.greeting_night, "Good night")
        hour < 12 -> str(resources, R.string.greeting_morning, "Good morning")
        hour < 17 -> str(resources, R.string.greeting_afternoon, "Good afternoon")
        hour < 22 -> str(resources, R.string.greeting_evening, "Good evening")
        else -> str(resources, R.string.greeting_night, "Good night")
    }

    /** Greeting plus the user's name when they have set one. */
    fun greetingWithName(
        name: String,
        resources: Resources?,
        hour: Int = java.time.LocalTime.now().hour,
    ): String {
        val base = greeting(hour, resources)
        if (name.isBlank()) return base
        return str(resources, R.string.greeting_with_name, "$base, $name", base, name)
    }

    private fun str(res: Resources?, id: Int, fallback: String, vararg args: Any): String =
        if (res == null) fallback else res.getString(id, *args)
}

// ---------------------------------------------------------------- stats

data class DayStat(
    val date: LocalDate,
    val scheduled: Int,
    val completed: Int,
    val taskCompleted: Int,
) {
    val ratio: Float = if (scheduled == 0) 0f else (completed.toFloat() / scheduled).coerceIn(0f, 1f)
    val hasData: Boolean = scheduled > 0
    val isPerfect: Boolean = hasData && completed >= scheduled
}

data class Insights(
    val days: List<DayStat>,
    val currentStreak: Int,
    val bestStreak: Int,
    val weeklyConsistency: Int,
    val totalCompletions: Int,
    val completedToday: Int,
    val scheduledToday: Int,
    val perfectDays: Int,
    /** Weekday (1=Mon..7=Sun) with the highest average ratio, or null with no data. */
    val bestWeekday: Int? = null,
    /** Weekday with the lowest average ratio, or null with no data. */
    val worstWeekday: Int? = null,
    /** Projected streak if the trailing 30-day pace holds, in days. */
    val projectedStreak: Int = 0,
    /** Total completed focus minutes, filled in by the view model. */
    val focusMinutes: Int = 0,
) {
    val todayRatio: Float get() = if (scheduledToday == 0) 0f else completedToday.toFloat() / scheduledToday
}

object StatsEngine {
    /** Completion ratio required for a day to keep the streak alive. */
    const val STREAK_THRESHOLD = AppConstants.STREAK_THRESHOLD
    const val HISTORY_DAYS = AppConstants.HISTORY_DAYS

    fun scheduledOn(date: LocalDate, routines: List<RoutineEntity>): List<RoutineEntity> {
        val bit = Dates.weekdayBit(date)
        val endOfDay = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return routines.filter { !it.archived && (it.daysMask and bit) != 0 && it.createdAt <= endOfDay }
    }

    fun compute(
        routines: List<RoutineEntity>,
        routineLogs: List<RoutineLogEntity>,
        taskLogs: List<TaskLogEntity>,
        today: LocalDate = Dates.today(),
    ): Insights {
        val logsByDate: Map<String, Set<String>> =
            routineLogs.groupBy({ it.date }, { it.routineId }).mapValues { it.value.toSet() }
        val tasksByDate: Map<String, Int> = taskLogs.groupBy({ it.date }).mapValues { it.value.size }

        val days = (HISTORY_DAYS downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val scheduled = scheduledOn(date, routines)
            val done = logsByDate[Dates.iso(date)].orEmpty()
            val completed = scheduled.count { it.id in done }
            DayStat(
                date = date,
                scheduled = scheduled.size,
                completed = completed,
                taskCompleted = tasksByDate[Dates.iso(date)] ?: 0,
            )
        }

        val currentStreak = run {
            var streak = 0
            // Today is still in progress, so it only extends a streak once it qualifies.
            for ((index, day) in days.reversed().withIndex()) {
                val qualifies = day.hasData && day.ratio >= STREAK_THRESHOLD
                if (qualifies) {
                    streak++
                } else if (index == 0) {
                    continue // grace: an unfinished today does not break yesterday's streak
                } else {
                    break
                }
            }
            streak
        }

        val bestStreak = run {
            var best = 0
            var run = 0
            for (day in days) {
                if (day.hasData && day.ratio >= STREAK_THRESHOLD) {
                    run++
                    best = max(best, run)
                } else {
                    run = 0
                }
            }
            best
        }

        val recent = days.takeLast(7).filter { it.hasData }
        val weeklyConsistency = if (recent.isEmpty()) 0
        else (recent.map { it.ratio }.average() * 100).roundToInt().coerceIn(0, 100)

        val todayStat = days.last()

        // Average completion ratio per weekday, so the UI can name the strongest and
        // weakest day. Days with nothing scheduled are ignored.
        val byWeekday = days.filter { it.hasData }.groupBy { it.date.dayOfWeek.value }
        val weekdayAverages = byWeekday.mapValues { (_, stats) -> stats.map { it.ratio }.average() }
        val bestWeekday = weekdayAverages.maxByOrNull { it.value }?.key
        val worstWeekday = weekdayAverages.minByOrNull { it.value }?.key

        // Simple projection: how long the current streak would reach if the trailing
        // month's qualification rate keeps up.
        val projectedStreak = run {
            val recent30 = days.takeLast(AppConstants.PROJECTION_DAYS).filter { it.hasData }
            if (recent30.isEmpty()) {
                currentStreak
            } else {
                val qualifyRate = recent30.count { it.ratio >= STREAK_THRESHOLD }.toFloat() / recent30.size
                currentStreak + (qualifyRate * 7).roundToInt()
            }
        }

        return Insights(
            days = days,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            weeklyConsistency = weeklyConsistency,
            totalCompletions = routineLogs.size,
            completedToday = todayStat.completed,
            scheduledToday = todayStat.scheduled,
            perfectDays = days.count { it.isPerfect },
            bestWeekday = bestWeekday,
            worstWeekday = worstWeekday,
            projectedStreak = projectedStreak,
        )
    }

    /** Per-routine completion rate across the trailing [window] days that the routine was scheduled. */
    fun routineRates(
        routines: List<RoutineEntity>,
        routineLogs: List<RoutineLogEntity>,
        today: LocalDate = Dates.today(),
        window: Int = 30,
    ): List<Pair<RoutineEntity, Float>> {
        val logsByDate: Map<String, Set<String>> =
            routineLogs.groupBy({ it.date }, { it.routineId }).mapValues { it.value.toSet() }
        val dates = (0 until window).map { today.minusDays(it.toLong()) }
        return routines.filter { !it.archived }.map { routine ->
            val scheduledDays = dates.filter { date ->
                (routine.daysMask and Dates.weekdayBit(date)) != 0
            }
            if (scheduledDays.isEmpty()) {
                routine to 0f
            } else {
                val hit = scheduledDays.count { dates0 -> routine.id in logsByDate[Dates.iso(dates0)].orEmpty() }
                routine to (hit.toFloat() / scheduledDays.size)
            }
        }.sortedByDescending { it.second }
    }
}
