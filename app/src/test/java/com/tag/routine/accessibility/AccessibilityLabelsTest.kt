package com.tag.routine.accessibility

import com.tag.routine.data.CalendarType
import com.tag.routine.ui.accessibility.AccessibilityLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class AccessibilityLabelsTest {

    private val en = Locale.ENGLISH
    private val fa = Locale.forLanguageTag("fa")

    private val done = "Done"
    private val notDone = "Not done"
    private val noTime = "No time set"
    private val noItems = "No items"
    private val cellPattern = "%1\$s · %2\$s done (%3\$d of %4\$d)"

    @Test
    fun `completed row reads title, time and status`() {
        val label = AccessibilityLabels.routineRow(
            title = "Deep study",
            timeLabel = "09:00",
            done = true,
            doneLabel = done,
            notDoneLabel = notDone,
            noTimeLabel = noTime,
            locale = en,
        )
        assertEquals("Deep study, 09:00, Done", label)
    }

    @Test
    fun `pending row without a time says so instead of skipping the slot`() {
        val label = AccessibilityLabels.routineRow(
            title = "Evening walk",
            timeLabel = null,
            done = false,
            doneLabel = done,
            notDoneLabel = notDone,
            noTimeLabel = noTime,
            locale = en,
        )
        assertEquals("Evening walk, No time set, Not done", label)
    }

    @Test
    fun `persian row uses persian digits and separator`() {
        val label = AccessibilityLabels.routineRow(
            title = "مطالعه",
            timeLabel = "۰۷:۳۰",
            done = false,
            doneLabel = "انجام‌شده",
            notDoneLabel = "انجام‌نشده",
            noTimeLabel = "بدون ساعت",
            locale = fa,
        )
        assertTrue("unexpected join: $label", label.contains("، "))
        assertTrue("missing status: $label", label.endsWith("انجام‌نشده"))
        assertFalse("latin digits leaked: $label", label.any { it in '0'..'9' })
    }

    @Test
    fun `check state follows the completion flag`() {
        assertEquals(done, AccessibilityLabels.checkState(true, done, notDone))
        assertEquals(notDone, AccessibilityLabels.checkState(false, done, notDone))
    }

    @Test
    fun `heatmap cell in gregorian calendar carries date, percent and counts`() {
        val label = AccessibilityLabels.heatmapCell(
            date = LocalDate.of(2024, 1, 15),
            scheduled = 4,
            completed = 2,
            locale = en,
            calendarType = CalendarType.GREGORIAN,
            noItemsLabel = noItems,
            cellPattern = cellPattern,
        )
        assertEquals("Mon, 15 Jan · 50% done (2 of 4)", label)
    }

    @Test
    fun `heatmap cell in jalali calendar uses persian digits and month name`() {
        // 2024-03-20 is 1 Farvardin 1403 in the Solar Hijri calendar.
        val label = AccessibilityLabels.heatmapCell(
            date = LocalDate.of(2024, 3, 20),
            scheduled = 4,
            completed = 3,
            locale = fa,
            calendarType = CalendarType.JALALI,
            noItemsLabel = "بدون مورد",
            cellPattern = "%1\$s · %2\$s انجام‌شده (%3\$d از %4\$d)",
        )
        assertTrue("missing jalali month: $label", label.contains("فروردین"))
        assertTrue("missing persian percent: $label", label.contains("۷۵٪"))
        assertTrue("missing counts: $label", label.contains("۳ از ۴"))
        assertFalse("latin digits leaked: $label", label.any { it in '0'..'9' })
    }

    @Test
    fun `heatmap cell in jalali calendar uses Latin month name for non-Persian locales`() {
        val label = AccessibilityLabels.heatmapCell(
            date = LocalDate.of(2024, 3, 20),
            scheduled = 4,
            completed = 3,
            locale = en,
            calendarType = CalendarType.JALALI,
            noItemsLabel = noItems,
            cellPattern = cellPattern,
        )
        assertTrue("missing Latin jalali month: $label", label.contains("Farvardin"))
        assertTrue("missing percent: $label", label.contains("75%"))
    }

    @Test
    fun `heatmap cell without scheduled items reports the neutral state`() {
        val label = AccessibilityLabels.heatmapCell(
            date = LocalDate.of(2024, 1, 15),
            scheduled = 0,
            completed = 0,
            locale = en,
            calendarType = CalendarType.GREGORIAN,
            noItemsLabel = noItems,
            cellPattern = cellPattern,
        )
        assertEquals("Mon, 15 Jan, No items", label)
    }

    @Test
    fun `chart series lists every day with a percentage or a no-data marker`() {
        val entries = listOf(
            LocalDate.of(2024, 1, 15) to 50,
            LocalDate.of(2024, 1, 16) to null,
        )
        val label = AccessibilityLabels.chartSeries(entries, en, "No data")
        assertEquals("Mon 50%, Tue No data", label)
    }

    @Test
    fun `chart series localises digits for persian`() {
        val label = AccessibilityLabels.chartSeries(
            entries = listOf(LocalDate.of(2024, 1, 15) to 40),
            locale = fa,
            noDataLabel = "بدون داده",
        )
        assertTrue("missing persian percent: $label", label.contains("۴۰٪"))
        assertFalse("latin digits leaked: $label", label.any { it in '0'..'9' })
    }
}
