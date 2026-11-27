package com.tag.routine.data

import android.content.res.Resources
import com.tag.routine.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
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

/** Display calendar used across date pickers, strips, heatmap, and summaries. */
enum class CalendarType(val key: String) {
    GREGORIAN("gregorian"),
    JALALI("jalali");

    companion object {
        fun fromKey(value: String?): CalendarType =
            if (value == JALALI.key) JALALI else GREGORIAN
    }
}

/** User preference for UI animation intensity. */
enum class AnimationSetting(val key: String) {
    AUTO("auto"),
    FULL("full"),
    REDUCED("reduced"),
    OFF("off");

    companion object {
        fun fromKey(value: String?): AnimationSetting =
            entries.firstOrNull { it.key == value } ?: AUTO
    }
}

data class AppSettings(
    val themeMode: Int = ThemeMode.SYSTEM,
    val themePalette: String = Palettes.VIOLET,
    val dynamicColor: Boolean = false,
    val language: String = "",
    val calendarType: CalendarType = CalendarType.GREGORIAN,
    val userName: String = "",
    val notificationsEnabled: Boolean = true,
    val dailyDigestEnabled: Boolean = true,
    val dailyDigestMinutes: Int = 8 * 60,
    val hapticsEnabled: Boolean = true,
    /** True once the first-launch onboarding has been completed or skipped. */
    val onboarded: Boolean = false,
    /** Last focus length the user chose, in minutes. Drives the Focus section default. */
    val focusMinutes: Int = AppConstants.DEFAULT_FOCUS_MINUTES,
    /** ISO date ("yyyy-MM-dd") of the first day the app was launched with templates support. */
    val firstLaunchDate: String = "",
    /** True once the user explicitly dismissed the first-day templates card on Today. */
    val templatesCardDismissed: Boolean = false,
    /** User-selected animation level preference (Auto / Full / Reduced / Off). */
    val animationSetting: AnimationSetting = AnimationSetting.AUTO,
    /** Optional Pomodoro-style break timer. Off keeps the original focus-only timer. */
    val breakModeEnabled: Boolean = false,
    val shortBreakMinutes: Int = AppConstants.DEFAULT_SHORT_BREAK_MINUTES,
    val longBreakMinutes: Int = AppConstants.DEFAULT_LONG_BREAK_MINUTES,
    val longBreakInterval: Int = AppConstants.DEFAULT_LONG_BREAK_INTERVAL,
    /** Start the break automatically when a focus session ends. */
    val autoStartBreak: Boolean = true,
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

/** Phase of the optional Pomodoro cycle; [FOCUS] is the only recorded phase. */
enum class FocusPhase(val key: String) {
    FOCUS("focus"),
    SHORT_BREAK("short_break"),
    LONG_BREAK("long_break");

    val isBreak: Boolean get() = this != FOCUS

    companion object {
        fun fromKey(value: String?): FocusPhase =
            entries.firstOrNull { it.key == value } ?: FOCUS
    }
}

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
    /** Focus, short break, or long break. Persisted runs from before v2.2 decode as focus. */
    val phase: FocusPhase = FocusPhase.FOCUS,
    /** 1-based index of the focus session in the current cycle. */
    val cycleCount: Int = 1,
    /** The focus length this cycle uses; keeps the next focus correct after a break. */
    val focusMinutes: Int = plannedMinutes,
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

    private const val VERSION = "2"
    private const val VERSION_PHASE_AWARE = "2"
    private const val VERSION_LEGACY = "1"
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
        run.phase.key,
        run.cycleCount.toString(),
        run.focusMinutes.toString(),
    ).joinToString(SEPARATOR)

    fun decode(raw: String?): ActiveFocusRun? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(SEPARATOR)
        if (parts.isEmpty()) return null
        return when (parts[0]) {
            VERSION_LEGACY -> decodeLegacy(parts)
            VERSION_PHASE_AWARE -> decodePhaseAware(parts)
            else -> null
        }
    }

    /** Runs stored by 2.1 and earlier have no phase fields; they are always focus runs. */
    private fun decodeLegacy(parts: List<String>): ActiveFocusRun? {
        if (parts.size != 8) return null
        return runCatching {
            ActiveFocusRun(
                id = parts[1],
                routineId = decodeText(parts[2]),
                routineTitle = decodeText(parts[3]),
                plannedMinutes = AppConstants.clampFocusMinutes(parts[4].toInt()),
                segmentStartedAt = parts[5].toLong().takeIf { it > 0L },
                accumulatedSeconds = parts[6].toInt().coerceAtLeast(0),
                startedAt = parts[7].toLong(),
                phase = FocusPhase.FOCUS,
                cycleCount = 1,
                focusMinutes = AppConstants.clampFocusMinutes(parts[4].toInt()),
            )
        }.getOrNull()?.takeIf { it.id.isNotBlank() }
    }

    private fun decodePhaseAware(parts: List<String>): ActiveFocusRun? {
        if (parts.size != 11) return null
        return runCatching {
            val phase = FocusPhase.fromKey(parts[8])
            val planned = if (phase.isBreak) {
                AppConstants.clampShortBreakMinutes(parts[4].toInt())
            } else {
                AppConstants.clampFocusMinutes(parts[4].toInt())
            }
            ActiveFocusRun(
                id = parts[1],
                routineId = decodeText(parts[2]),
                routineTitle = decodeText(parts[3]),
                plannedMinutes = planned,
                segmentStartedAt = parts[5].toLong().takeIf { it > 0L },
                accumulatedSeconds = parts[6].toInt().coerceAtLeast(0),
                startedAt = parts[7].toLong(),
                phase = phase,
                cycleCount = parts[9].toInt().coerceIn(1, 999),
                focusMinutes = AppConstants.clampFocusMinutes(parts[10].toInt()),
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

/** Display metadata for one weekday toggle in routine pickers and calendar grids. */
data class WeekdayOption(
    val dayOfWeek: DayOfWeek,
    val bit: Int,
    val shortLabel: String,
)

object Dates {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): LocalDate = LocalDate.now()

    fun iso(date: LocalDate): String = date.format(iso)

    fun parse(value: String): LocalDate? = runCatching { LocalDate.parse(value, iso) }.getOrNull()

    /** Monday based bit for [daysMask] (bit 0 == Monday). */
    fun weekdayBit(date: LocalDate): Int = 1 shl (date.dayOfWeek.value - 1)

    /** Monday based bit for [dayOfWeek] (bit 0 == Monday). */
    fun weekdayBit(dayOfWeek: DayOfWeek): Int = 1 shl (dayOfWeek.value - 1)

    const val EVERY_DAY = 0b1111111

    fun epochDayToJalali(epochDay: Long): JalaliDate = JalaliCalendar.fromEpochDay(epochDay)

    fun jalaliToEpochDay(year: Int, month: Int, day: Int): Long =
        JalaliCalendar.toEpochDay(year, month, day)

    fun toJalali(date: LocalDate): JalaliDate = JalaliCalendar.fromLocalDate(date)

    fun fromJalali(year: Int, month: Int, day: Int): LocalDate =
        JalaliCalendar.toLocalDate(year, month, day)

    fun persianMonthLength(year: Int, month: Int): Int = JalaliCalendar.daysInMonth(year, month)

    fun isPersianLeapYear(year: Int): Boolean = JalaliCalendar.isLeapYear(year)

    fun persianMonthName(month: Int): String = JalaliCalendar.monthName(month)

    fun persianMonthName(month: Int, locale: Locale): String = JalaliCalendar.monthName(month, locale)

    fun persianMonthNames(): List<String> = JalaliCalendar.PERSIAN_MONTH_NAMES

    fun persianMonthNames(locale: Locale): List<String> = JalaliCalendar.monthNames(locale)

    /** First day of week: always Saturday in Jalali mode; Saturday for `fa` or Monday otherwise in Gregorian. */
    fun firstDayOfWeek(
        calendarType: CalendarType,
        locale: Locale = Locale.getDefault(),
    ): DayOfWeek = when (calendarType) {
        CalendarType.JALALI -> DayOfWeek.SATURDAY
        CalendarType.GREGORIAN ->
            if (locale.language.equals("fa", ignoreCase = true)) DayOfWeek.SATURDAY else DayOfWeek.MONDAY
    }

    /**
     * Formatters are built per call rather than cached, because the active locale changes
     * at runtime when the user switches language inside the app.
     */
    private fun formatter(pattern: String, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern, locale)

    fun dayOfMonth(
        date: LocalDate,
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): Int = when (calendarType) {
        CalendarType.GREGORIAN -> date.dayOfMonth
        CalendarType.JALALI -> JalaliCalendar.fromLocalDate(date).day
    }

    fun monthNumber(
        date: LocalDate,
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): Int = when (calendarType) {
        CalendarType.GREGORIAN -> date.monthValue
        CalendarType.JALALI -> JalaliCalendar.fromLocalDate(date).month
    }

    fun yearNumber(
        date: LocalDate,
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): Int = when (calendarType) {
        CalendarType.GREGORIAN -> date.year
        CalendarType.JALALI -> JalaliCalendar.fromLocalDate(date).year
    }

    fun monthName(
        date: LocalDate,
        locale: Locale = Locale.getDefault(),
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): String = when (calendarType) {
        CalendarType.GREGORIAN -> date.format(formatter("MMMM", locale))
        CalendarType.JALALI -> JalaliCalendar.monthName(JalaliCalendar.fromLocalDate(date).month, locale)
    }

    fun shortMonthName(
        date: LocalDate,
        locale: Locale = Locale.getDefault(),
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): String = when (calendarType) {
        CalendarType.GREGORIAN -> date.format(formatter("MMM", locale))
        CalendarType.JALALI -> JalaliCalendar.monthName(JalaliCalendar.fromLocalDate(date).month, locale)
    }

    fun monthYearTitle(
        year: Int,
        month: Int,
        locale: Locale = Locale.getDefault(),
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): String = when (calendarType) {
        CalendarType.GREGORIAN ->
            monthYearTitle(LocalDate.of(year, month, 1), locale, CalendarType.GREGORIAN)
        CalendarType.JALALI ->
            formatDigits("${JalaliCalendar.monthName(month, locale)} $year", locale)
    }

    fun monthYearTitle(
        date: LocalDate,
        locale: Locale = Locale.getDefault(),
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): String = when (calendarType) {
        CalendarType.GREGORIAN -> formatDigits(date.format(formatter("MMMM yyyy", locale)), locale)
        CalendarType.JALALI -> {
            val j = JalaliCalendar.fromLocalDate(date)
            formatDigits("${JalaliCalendar.monthName(j.month, locale)} ${j.year}", locale)
        }
    }

    fun monthYearRange(
        start: LocalDate,
        end: LocalDate,
        locale: Locale = Locale.getDefault(),
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): String {
        val startYear = yearNumber(start, calendarType)
        val endYear = yearNumber(end, calendarType)
        val startMonth = monthNumber(start, calendarType)
        val endMonth = monthNumber(end, calendarType)
        return when {
            startYear == endYear && startMonth == endMonth ->
                monthYearTitle(startYear, startMonth, locale, calendarType)
            startYear == endYear ->
                formatDigits(
                    "${shortMonthName(start, locale, calendarType)} – ${shortMonthName(end, locale, calendarType)} $endYear",
                    locale,
                )
            else ->
                formatDigits(
                    "${monthYearTitle(startYear, startMonth, locale, calendarType)} – ${monthYearTitle(endYear, endMonth, locale, calendarType)}",
                    locale,
                )
        }
    }

    fun dayMonth(
        date: LocalDate,
        locale: Locale = Locale.getDefault(),
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): String = when (calendarType) {
        CalendarType.GREGORIAN -> formatDigits(date.format(formatter("EEE, d MMM", locale)), locale)
        CalendarType.JALALI -> {
            val j = JalaliCalendar.fromLocalDate(date)
            val dow = dayOfWeekLabel(date, locale)
            val sep = if (locale.language.equals("fa", ignoreCase = true)) "، " else ", "
            formatDigits("$dow$sep${j.day} ${JalaliCalendar.monthName(j.month, locale)}", locale)
        }
    }

    fun full(
        date: LocalDate,
        locale: Locale = Locale.getDefault(),
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): String = when (calendarType) {
        CalendarType.GREGORIAN -> formatDigits(date.format(formatter("EEEE, d MMMM", locale)), locale)
        CalendarType.JALALI -> {
            val j = JalaliCalendar.fromLocalDate(date)
            val dow = date.format(formatter("EEEE", locale))
            val sep = if (locale.language.equals("fa", ignoreCase = true)) "، " else ", "
            formatDigits("$dow$sep${j.day} ${JalaliCalendar.monthName(j.month, locale)} ${j.year}", locale)
        }
    }

    /** Single-letter weekday used by the compact week strip. */
    fun shortDayName(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(formatter("EEEEE", locale)).uppercase(locale)

    /**
     * Ordered weekday options for the routine editor's day picker.
     *
     * Starts on Saturday when [calendarType] is [CalendarType.JALALI] and on Monday in
     * [CalendarType.GREGORIAN], while each option keeps its stable Monday-based [daysMask] bit.
     */
    fun weekdayOptions(
        calendarType: CalendarType,
        locale: Locale = Locale.getDefault(),
    ): List<WeekdayOption> {
        val start = if (calendarType == CalendarType.JALALI) DayOfWeek.SATURDAY else DayOfWeek.MONDAY
        val anchor = LocalDate.of(2024, 1, 1).with(TemporalAdjusters.nextOrSame(start))
        return (0..6).map { offset ->
            val date = anchor.plusDays(offset.toLong())
            WeekdayOption(
                dayOfWeek = date.dayOfWeek,
                bit = weekdayBit(date.dayOfWeek),
                shortLabel = shortDayName(date, locale),
            )
        }
    }

    /**
     * Monday-first single-letter weekday labels for the routine editor's day picker.
     *
     * These used to be a hardcoded `listOf("M","T","W","T","F","S","S")`, which stayed
     * English in every language. They are derived from the active locale instead, so a
     * Persian user sees the Persian initials. Monday comes first because the stored
     * `daysMask` uses bit 0 for Monday (see [weekdayBit]).
     */
    fun weekdayInitials(locale: Locale = Locale.getDefault()): List<String> {
        val monday = LocalDate.of(2024, 1, 1) // a Monday, used only as a label source
        return (0..6).map { offset -> shortDayName(monday.plusDays(offset.toLong()), locale) }
    }

    fun shortDate(
        date: LocalDate,
        locale: Locale = Locale.getDefault(),
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): String = when (calendarType) {
        CalendarType.GREGORIAN -> formatDigits(date.format(formatter("d MMM", locale)), locale)
        CalendarType.JALALI -> {
            val j = JalaliCalendar.fromLocalDate(date)
            formatDigits("${j.day} ${JalaliCalendar.monthName(j.month, locale)}", locale)
        }
    }

    /** Localised short date+time label respecting [calendarType] and [locale]. */
    fun shortDateTime(
        epochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): String {
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return when (calendarType) {
            CalendarType.GREGORIAN -> formatDigits(
                zoned.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withLocale(locale)),
                locale,
            )
            CalendarType.JALALI -> {
                val datePart = shortDate(zoned.toLocalDate(), locale, CalendarType.JALALI)
                val timePart = zoned.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
                formatDigits("$datePart · $timePart", locale)
            }
        }
    }

    /**
     * Normalises all digits and percent signs in [text]: Persian digits (`۰..۹`) and
     * Persian percent sign (`٪`, U+066A) when [locale] is Persian, or Latin ASCII
     * digits (`0..9`) and `%` for every other language.
     */
    fun formatDigits(text: String, locale: Locale = Locale.getDefault()): String {
        val usePersian = locale.language.equals("fa", ignoreCase = true)
        return buildString(text.length) {
            for (ch in text) {
                when {
                    usePersian && ch in '0'..'9' -> append(('۰'.code + (ch - '0')).toChar())
                    usePersian && ch in '\u0660'..'\u0669' -> append(('۰'.code + (ch - '\u0660')).toChar())
                    usePersian && ch == '%' -> append('\u066A')
                    !usePersian && ch in '۰'..'۹' -> append(('0'.code + (ch - '۰')).toChar())
                    !usePersian && ch in '\u0660'..'\u0669' -> append(('0'.code + (ch - '\u0660')).toChar())
                    !usePersian && ch == '\u066A' -> append('%')
                    else -> append(ch)
                }
            }
        }
    }

    /**
     * Converts Persian (۰..۹) and Arabic-Indic (٠..٩) digits to ASCII without touching
     * any other character. Used by numeric inputs, which must accept both keyboards.
     */
    fun toLatinDigits(text: String): String = buildString(text.length) {
        for (ch in text) {
            when {
                ch in '۰'..'۹' -> append(('0'.code + (ch - '۰')).toChar())
                ch in '\u0660'..'\u0669' -> append(('0'.code + (ch - '\u0660')).toChar())
                else -> append(ch)
            }
        }
    }

    /** Locale-appropriate percent symbol (`٪` for Persian, `%` otherwise). */
    fun percentSymbol(locale: Locale = Locale.getDefault()): String =
        if (locale.language.equals("fa", ignoreCase = true)) "\u066A" else "%"

    /** Formats an integer percentage using [formatDigits] for the active [locale]. */
    fun formatPercent(value: Int, locale: Locale = Locale.getDefault()): String =
        formatDigits("$value%", locale)

    fun formatMinutes(minutes: Int, locale: Locale = Locale.ROOT): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return formatDigits("%02d:%02d".format(Locale.ROOT, h, m), locale)
    }

    /** Alias kept for call sites that describe a clock label rather than an offset. */
    fun timeLabel(minutes: Int, locale: Locale = Locale.ROOT): String = formatMinutes(minutes, locale)

    /** Formats a focus duration (e.g. "25m" or "1h 15m") with localised units and digits. */
    fun formatDuration(
        totalMinutes: Int,
        resources: Resources? = null,
        locale: Locale = Locale.getDefault(),
    ): String {
        val safe = totalMinutes.coerceAtLeast(0)
        val hours = safe / 60
        val mins = safe % 60
        val raw = when {
            hours == 0 -> str(resources, R.string.duration_m, "${mins}m", mins)
            mins == 0 -> str(resources, R.string.duration_h, "${hours}h", hours)
            else -> str(resources, R.string.duration_hm, "${hours}h ${mins}m", hours, mins)
        }
        return formatDigits(raw, locale)
    }

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
        locale: Locale? = null,
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): String {
        val days = ChronoUnit.DAYS.between(today, date)
        val resLocale = locale ?: resources?.configuration?.locales?.get(0) ?: Locale.getDefault()
        val raw = when {
            days == 0L -> str(resources, R.string.day_today, "Today")
            days == 1L -> str(resources, R.string.day_tomorrow, "Tomorrow")
            days == -1L -> str(resources, R.string.day_yesterday, "Yesterday")
            days in 2..6 -> str(resources, R.string.day_in_days, "In $days days", days.toInt())
            days in -6..-2 ->
                str(resources, R.string.day_days_ago, "${-days} days ago", (-days).toInt())
            else -> dayMonth(date, resLocale, calendarType)
        }
        return formatDigits(raw, resLocale)
    }

    fun dayOfWeekLabel(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(formatter("EEE", locale))

    /**
     * Returns this weekend's target date for [calendarType]: Friday in Solar Hijri (Jalali),
     * and Saturday in Gregorian. When [today] is already that day, [today] is returned.
     */
    fun nextWeekend(
        today: LocalDate = today(),
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): LocalDate {
        val target = when (calendarType) {
            CalendarType.JALALI -> DayOfWeek.FRIDAY
            CalendarType.GREGORIAN -> DayOfWeek.SATURDAY
        }
        return today.with(TemporalAdjusters.nextOrSame(target))
    }

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

/**
 * Normalizes and matches comma-separated ISO dates stored in `RoutineEntity.excludedDates`,
 * tolerating whitespace around tokens in legacy or externally formatted data.
 */
object ExcludedDates {

    fun isExcluded(excludedDates: String, dateIso: String): Boolean {
        val target = dateIso.trim()
        if (target.isEmpty() || excludedDates.isBlank()) return false
        return excludedDates.split(',').any { it.trim() == target }
    }

    fun normalize(raw: String): String =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")

    fun withExcluded(excludedDates: String, dateIso: String): String {
        val target = dateIso.trim()
        val existing = excludedDates.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (target.isEmpty() || target in existing) return existing.joinToString(",")
        return (existing + target).joinToString(",")
    }
}

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

    fun isScheduledOn(routine: RoutineEntity, date: LocalDate): Boolean {
        if (routine.archived) return false
        val dayEpoch = date.toEpochDay()
        if (!routine.repeatDaily) {
            return routine.dateEpochDay == dayEpoch
        }
        val bit = Dates.weekdayBit(date)
        if ((routine.daysMask and bit) == 0) return false
        val startOk = if (routine.dateEpochDay > 0L) {
            dayEpoch >= routine.dateEpochDay
        } else {
            val endOfDay = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            routine.createdAt <= endOfDay
        }
        if (!startOk) return false
        val endOk = routine.endDateEpochDay == null || dayEpoch <= routine.endDateEpochDay
        if (!endOk) return false
        return !ExcludedDates.isExcluded(routine.excludedDates, Dates.iso(date))
    }

    fun scheduledOn(date: LocalDate, routines: List<RoutineEntity>): List<RoutineEntity> =
        routines.filter { isScheduledOn(it, date) }

    fun daySummary(
        date: LocalDate,
        routines: List<RoutineEntity>,
        logsByDate: Map<String, Set<String>>,
        taskCount: Int = 0,
    ): DayStat {
        val scheduled = scheduledOn(date, routines)
        val done = logsByDate[Dates.iso(date)].orEmpty()
        val completed = scheduled.count { it.id in done }
        return DayStat(
            date = date,
            scheduled = scheduled.size,
            completed = completed,
            taskCompleted = taskCount,
        )
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
            daySummary(date, routines, logsByDate, tasksByDate[Dates.iso(date)] ?: 0)
        }

        val currentStreak = run {
            var streak = 0
            // Days with zero scheduled items are neutral (neither break nor extend).
            // Today is still in progress, so it only breaks once it fails after today.
            for ((index, day) in days.reversed().withIndex()) {
                if (!day.hasData) continue
                if (day.ratio >= STREAK_THRESHOLD) {
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
                if (!day.hasData) continue
                if (day.ratio >= STREAK_THRESHOLD) {
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
            val scheduledDays = dates.filter { date -> isScheduledOn(routine, date) }
            if (scheduledDays.isEmpty()) {
                routine to 0f
            } else {
                val hit = scheduledDays.count { dates0 -> routine.id in logsByDate[Dates.iso(dates0)].orEmpty() }
                routine to (hit.toFloat() / scheduledDays.size)
            }
        }.sortedByDescending { it.second }
    }
}
