package com.tag.routine.ui.editors

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tag.routine.R
import com.tag.routine.data.CalendarType
import com.tag.routine.data.Dates
import com.tag.routine.data.JalaliCalendar
import com.tag.routine.data.JalaliDate
import com.tag.routine.ui.components.AppCard
import com.tag.routine.ui.i18n.ProvideAppLocale
import com.tag.routine.ui.motion.AnimatedDialogSurface
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * Solar Hijri (Jalali) calendar date picker dialog.
 *
 * Weeks always begin on Saturday, month names are displayed in Persian (`فروردین..اسفند`),
 * digits use [Dates.formatDigits], and the selected date is returned as a standard Gregorian
 * [LocalDate] so Room persistence remains calendar-independent.
 */
@Composable
fun JalaliDatePickerDialog(
    initialDate: LocalDate,
    localizedContext: Context,
    today: LocalDate = Dates.today(),
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val initialJalali = remember(initialDate) { JalaliCalendar.fromLocalDate(initialDate) }
    val todayJalali = remember(today) { JalaliCalendar.fromLocalDate(today) }

    var selected by remember(initialDate) { mutableStateOf(initialJalali) }
    var displayYear by remember(initialDate) { mutableIntStateOf(initialJalali.year) }
    var displayMonth by remember(initialDate) { mutableIntStateOf(initialJalali.month) }
    var pickingYear by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        AnimatedDialogSurface {
            ProvideAppLocale(localizedContext) {
            val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
            val weekdayOptions = remember(appLocale) {
                Dates.weekdayOptions(CalendarType.JALALI, appLocale)
            }
            val selectedLocal = remember(selected) { JalaliCalendar.toLocalDate(selected) }

            AppCard(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                elevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.task_editor_pick_date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = Dates.full(selectedLocal, appLocale, CalendarType.JALALI),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Month/year navigation bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val selectYearDesc = stringResource(R.string.date_picker_select_year)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.semantics { contentDescription = selectYearDesc },
                            onClick = { pickingYear = !pickingYear },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = Dates.monthYearTitle(
                                        year = displayYear,
                                        month = displayMonth,
                                        locale = appLocale,
                                        calendarType = CalendarType.JALALI,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (!pickingYear) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (displayMonth == 1) {
                                            displayMonth = 12
                                            displayYear -= 1
                                        } else {
                                            displayMonth -= 1
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                        contentDescription = stringResource(R.string.date_picker_prev_month),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (displayMonth == 12) {
                                            displayMonth = 1
                                            displayYear += 1
                                        } else {
                                            displayMonth += 1
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = stringResource(R.string.date_picker_next_month),
                                    )
                                }
                            }
                        }
                    }

                    if (pickingYear) {
                        val years = remember(todayJalali.year) {
                            ((todayJalali.year - 10)..(todayJalali.year + 25)).toList()
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            years.chunked(3).forEach { rowYears ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rowYears.forEach { yr ->
                                        val isCurrentYear = yr == displayYear
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isCurrentYear) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                            onClick = {
                                                displayYear = yr
                                                val maxD = JalaliCalendar.daysInMonth(yr, displayMonth)
                                                if (selected.year == yr && selected.month == displayMonth && selected.day > maxD) {
                                                    selected = selected.copy(day = maxD)
                                                }
                                                pickingYear = false
                                            },
                                        ) {
                                            Text(
                                                text = Dates.formatDigits(yr.toString(), appLocale),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isCurrentYear) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isCurrentYear) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(vertical = 10.dp),
                                            )
                                        }
                                    }
                                    repeat(3 - rowYears.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        // Weekday headers starting Saturday
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            weekdayOptions.forEach { option ->
                                Text(
                                    text = option.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        // Month days grid
                        val daysInMonth = JalaliCalendar.daysInMonth(displayYear, displayMonth)
                        val firstDayOfMonth = JalaliCalendar.toLocalDate(displayYear, displayMonth, 1)
                        val leadingEmpty = (firstDayOfMonth.dayOfWeek.value - DayOfWeek.SATURDAY.value + 7) % 7
                        val totalSlots = leadingEmpty + daysInMonth
                        val trailingEmpty = (7 - (totalSlots % 7)) % 7
                        val cells: List<Int?> = buildList(totalSlots + trailingEmpty) {
                            repeat(leadingEmpty) { add(null) }
                            for (d in 1..daysInMonth) add(d)
                            repeat(trailingEmpty) { add(null) }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            cells.chunked(7).forEach { week ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    week.forEach { day ->
                                        if (day == null) {
                                            Spacer(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp),
                                            )
                                        } else {
                                            val candidate = JalaliDate(displayYear, displayMonth, day)
                                            val isSelected = candidate == selected
                                            val isToday = candidate == todayJalali
                                            val bgColor = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.Transparent
                                            }
                                            val borderModifier = if (!isSelected && isToday) {
                                                Modifier.border(
                                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                                    RoundedCornerShape(percent = 50),
                                                )
                                            } else {
                                                Modifier
                                            }
                                            val fgColor = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                isToday -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .sizeIn(minHeight = 48.dp)
                                                    .clip(RoundedCornerShape(percent = 50))
                                                    .background(bgColor)
                                                    .then(borderModifier)
                                                    .clickable { selected = candidate }
                                                    .padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = Dates.formatDigits(day.toString(), appLocale),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                    color = fgColor,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = {
                                selected = todayJalali
                                displayYear = todayJalali.year
                                displayMonth = todayJalali.month
                                pickingYear = false
                            },
                        ) {
                            Text(stringResource(R.string.date_picker_today))
                        }
                        Row {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            TextButton(onClick = { onConfirm(selectedLocal) }) {
                                Text(stringResource(R.string.action_ok))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
