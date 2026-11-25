package com.tag.routine.data

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.time.LocalDate
import java.util.Locale

/** Calendar date in the Solar Hijri (Jalali / Persian) calendar. */
data class JalaliDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

/**
 * Shared Solar Hijri (Jalali) calendar conversion and metadata layer.
 *
 * All stored dates in Room and backups remain Gregorian ISO / epoch days. This layer converts
 * Gregorian `epochDay` / [LocalDate] values to and from [JalaliDate] for display and date
 * selection only.
 *
 * Uses Android's ICU Persian calendar (`Calendar.getInstance(GMT_ZONE, "en@calendar=persian")`,
 * available from minSdk 26) pinned to UTC (`TimeZone.GMT_ZONE`) at noon UTC so timezone offsets
 * and DST transitions can never shift a date by one day. When running in host JVM unit tests
 * where `android.jar` classes are stubs, falls back to [PureJalaliMath], which matches ICU on
 * every day across 1925-2100.
 */
object JalaliCalendar {

    private const val MILLIS_PER_DAY = 86_400_000L
    private const val HALF_DAY_MILLIS = 43_200_000L

    val PERSIAN_MONTH_NAMES: List<String> = listOf(
        "فروردین",
        "اردیبهشت",
        "خرداد",
        "تیر",
        "مرداد",
        "شهریور",
        "مهر",
        "آبان",
        "آذر",
        "دی",
        "بهمن",
        "اسفند",
    )

    val LATIN_MONTH_NAMES: List<String> = listOf(
        "Farvardin",
        "Ordibehesht",
        "Khordad",
        "Tir",
        "Mordad",
        "Shahrivar",
        "Mehr",
        "Aban",
        "Azar",
        "Dey",
        "Bahman",
        "Esfand",
    )

    private val PERSIAN_ULOCALE = ULocale("en@calendar=persian")

    private val icuCalendar: ThreadLocal<Calendar> = ThreadLocal.withInitial {
        Calendar.getInstance(TimeZone.GMT_ZONE, PERSIAN_ULOCALE)
    }

    private val icuAvailable: Boolean by lazy {
        try {
            val cal = Calendar.getInstance(TimeZone.GMT_ZONE, PERSIAN_ULOCALE)
            if (cal.type != "persian") {
                false
            } else {
                // Probe 2026-03-21 (epochDay 20533) == 1405-01-01 to confirm non-stub runtime.
                cal.timeInMillis = 20_533L * MILLIS_PER_DAY + HALF_DAY_MILLIS
                cal.get(Calendar.YEAR) == 1405 &&
                    cal.get(Calendar.MONTH) == 0 &&
                    cal.get(Calendar.DAY_OF_MONTH) == 1
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun monthNames(locale: Locale = Locale.getDefault()): List<String> =
        if (locale.language.equals("fa", ignoreCase = true)) PERSIAN_MONTH_NAMES else LATIN_MONTH_NAMES

    fun monthName(month: Int, locale: Locale): String {
        require(month in 1..12) { "Jalali month must be in 1..12, got $month" }
        return monthNames(locale)[month - 1]
    }

    fun monthName(month: Int): String = monthName(month, Locale.forLanguageTag("fa"))

    fun fromEpochDay(epochDay: Long): JalaliDate =
        if (icuAvailable) icuFromEpochDay(epochDay) else PureJalaliMath.fromEpochDay(epochDay)

    fun toEpochDay(year: Int, month: Int, day: Int): Long {
        require(month in 1..12) { "Jalali month must be in 1..12, got $month" }
        val maxDay = daysInMonth(year, month)
        require(day in 1..maxDay) {
            "Jalali day $day out of range 1..$maxDay for $year-$month"
        }
        return if (icuAvailable) {
            icuToEpochDay(year, month, day)
        } else {
            PureJalaliMath.toEpochDay(year, month, day)
        }
    }

    fun fromLocalDate(date: LocalDate): JalaliDate = fromEpochDay(date.toEpochDay())

    fun toLocalDate(year: Int, month: Int, day: Int): LocalDate =
        LocalDate.ofEpochDay(toEpochDay(year, month, day))

    fun toLocalDate(date: JalaliDate): LocalDate = toLocalDate(date.year, date.month, date.day)

    fun daysInMonth(year: Int, month: Int): Int {
        require(month in 1..12) { "Jalali month must be in 1..12, got $month" }
        if (month <= 6) return 31
        if (month <= 11) return 30
        return if (icuAvailable) icuDaysInMonth(year, month) else PureJalaliMath.daysInMonth(year, month)
    }

    fun isLeapYear(year: Int): Boolean = daysInMonth(year, 12) == 30

    private fun calendar(): Calendar = requireNotNull(icuCalendar.get())

    private fun prepareIcuDate(year: Int, month: Int, day: Int): Calendar =
        calendar().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun icuFromEpochDay(epochDay: Long): JalaliDate {
        val cal = calendar()
        cal.timeInMillis = epochDay * MILLIS_PER_DAY + HALF_DAY_MILLIS
        return JalaliDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun icuToEpochDay(year: Int, month: Int, day: Int): Long {
        val cal = prepareIcuDate(year, month, day)
        return Math.floorDiv(cal.timeInMillis, MILLIS_PER_DAY)
    }

    private fun icuDaysInMonth(year: Int, month: Int): Int =
        prepareIcuDate(year, month, 1).getActualMaximum(Calendar.DAY_OF_MONTH)
}

/**
 * Pure-Kotlin Solar Hijri (Jalali) conversion using the Borkowski 33-year break table,
 * used on the host JVM when Android's `android.icu` runtime is unavailable.
 */
internal object PureJalaliMath {

    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    private val UNIX_EPOCH_JDN: Long = gregorianToJdn(1970, 1, 1)

    private data class JalaliYearInfo(val leap: Int, val gy: Int, val march: Int)

    fun fromEpochDay(epochDay: Long): JalaliDate {
        val jdn = UNIX_EPOCH_JDN + epochDay
        val gy = jdnToGregorianYear(jdn)
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = gregorianToJdn(gy, 3, r.march)
        var k = (jdn - jdn1f).toInt()
        if (k >= 0) {
            if (k <= 185) {
                val jm = 1 + k / 31
                val jd = k % 31 + 1
                return JalaliDate(jy, jm, jd)
            }
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        val jm = 7 + k / 30
        val jd = k % 30 + 1
        return JalaliDate(jy, jm, jd)
    }

    fun toEpochDay(year: Int, month: Int, day: Int): Long {
        val r = jalCal(year)
        val jdn = gregorianToJdn(r.gy, 3, r.march) +
            (month - 1) * 31L -
            (month / 7) * (month - 7).toLong() +
            (day - 1)
        return jdn - UNIX_EPOCH_JDN
    }

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        in 1..6 -> 31
        in 7..11 -> 30
        12 -> if (jalCal(year).leap == 0) 30 else 29
        else -> throw IllegalArgumentException("Jalali month must be in 1..12, got $month")
    }

    private fun jalCal(jy: Int): JalaliYearInfo {
        require(jy in BREAKS.first() until BREAKS.last()) {
            "Jalali year $jy outside supported range ${BREAKS.first()}..${BREAKS.last() - 1}"
        }
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        var jump = 0
        for (i in 1 until BREAKS.size) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + (n % 33 + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = ((n + 1) % 33 - 1) % 4
        if (leap == -1) leap = 4
        return JalaliYearInfo(leap = leap, gy = gy, march = march)
    }

    private fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Long {
        val g = gy.toLong()
        val m = gm.toLong()
        val d0 = ((g + (m - 8) / 6 + 100100) * 1461) / 4 +
            (153 * ((m + 9) % 12) + 2) / 5 +
            gd - 34840408
        return d0 - (((g + 100100 + (m - 8) / 6) / 100) * 3) / 4 + 752
    }

    private fun jdnToGregorianYear(jdn: Long): Int {
        var j = 4 * jdn + 139361631
        j += (((4 * jdn + 183187720) / 146097) * 3) / 4 * 4 - 3908
        val i = ((j % 1461) / 4) * 5 + 308
        val gm = ((i / 153) % 12) + 1
        return (j / 1461 - 100100 + (8 - gm) / 6).toInt()
    }
}
