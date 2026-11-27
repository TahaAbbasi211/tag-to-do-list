package com.tag.routine.ui.editors

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tag.routine.R
import com.tag.routine.data.CalendarType
import com.tag.routine.data.Dates
import com.tag.routine.data.Priorities
import com.tag.routine.data.RemindAtCalculator
import com.tag.routine.data.RemindError
import com.tag.routine.data.ReminderOffset
import com.tag.routine.data.TagEntity
import com.tag.routine.data.TaskEntity
import com.tag.routine.ui.components.FilterPill
import com.tag.routine.ui.i18n.ProvideAppLocale
import com.tag.routine.ui.motion.AnimatedSheetContent
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    existing: TaskEntity?,
    tags: List<TagEntity>,
    localizedContext: Context,
    calendarType: CalendarType = CalendarType.GREGORIAN,
    onDismiss: () -> Unit,
    onRequestNotificationPermission: () -> Unit = {},
    onSave: (
        title: String,
        note: String,
        tagId: String?,
        priority: Int,
        dueDate: String?,
        dueTimeMinutes: Int?,
        remindAt: Long?,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var selectedTagId by remember { mutableStateOf(existing?.tagId) }
    var priority by remember { mutableStateOf(existing?.priority ?: Priorities.NORMAL) }
    var dueDate by remember { mutableStateOf(existing?.dueDate) }
    var dueTimeMinutes by remember { mutableStateOf(existing?.dueTimeMinutes) }
    var reminderEnabled by remember { mutableStateOf(existing?.remindAt != null) }
    var reminderOffset by remember {
        mutableStateOf(
            existing?.let { task ->
                val parsed = task.dueDate?.let(Dates::parse)
                if (parsed != null) {
                    RemindAtCalculator.inferOffset(
                        dueDate = parsed,
                        dueTimeMinutes = task.dueTimeMinutes,
                        remindAt = task.remindAt,
                        zone = ZoneId.systemDefault(),
                    )
                } else {
                    null
                }
            } ?: ReminderOffset.AT_DUE,
        )
    }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        // Cap the sheet on tablets/landscape so it does not stretch edge to edge.
        sheetMaxWidth = 640.dp,
    ) {
        // The sheet lives in its own window, which resets LocalContext to the Activity's
        // (system-locale) context. Re-provide the app's language so the editor is not
        // rendered in English while the rest of the app is in the chosen language.
        AnimatedSheetContent {
        ProvideAppLocale(localizedContext) {
            val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(
                        if (existing == null) R.string.task_editor_new else R.string.task_editor_edit,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.task_editor_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.task_editor_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                )

                // Tag selector
                if (tags.isNotEmpty()) {
                    Column {
                        Text(
                            text = stringResource(R.string.task_editor_tag),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            FilterPill(
                                text = stringResource(R.string.task_editor_tag_none),
                                selected = selectedTagId == null,
                                onClick = { selectedTagId = null },
                            )
                            tags.forEach { tag ->
                                FilterPill(
                                    text = tag.name,
                                    selected = selectedTagId == tag.id,
                                    onClick = {
                                        selectedTagId = if (selectedTagId == tag.id) null else tag.id
                                    },
                                    dotColor = Color(tag.colorHex),
                                )
                            }
                        }
                    }
                }

                // Priority selector
                Column {
                    Text(
                        text = stringResource(R.string.task_editor_priority),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            Triple(Priorities.LOW, stringResource(R.string.priority_low), Color(0xFF4FD9C0)),
                            Triple(Priorities.NORMAL, stringResource(R.string.priority_medium), Color(0xFFFFB65C)),
                            Triple(Priorities.HIGH, stringResource(R.string.priority_high), Color(0xFFFF6B6B)),
                        ).forEach { (value, label, color) ->
                            FilterPill(
                                text = label,
                                selected = priority == value,
                                onClick = { priority = value },
                                dotColor = color,
                            )
                        }
                    }
                }

                // Due date: a read-only field that opens the Material date picker, plus
                // one-tap shortcuts for the dates people actually pick.
                Column {
                    Text(
                        text = stringResource(R.string.task_editor_due_date),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    val formattedDue = dueDate?.let { raw ->
                        Dates.parse(raw)?.let { Dates.dayMonth(it, appLocale, calendarType) } ?: raw
                    } ?: ""
                    OutlinedTextField(
                        value = formattedDue,
                        onValueChange = { },
                        readOnly = true,
                        placeholder = { Text(stringResource(R.string.task_editor_due_none)) },
                        trailingIcon = {
                            if (dueDate == null) {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarMonth,
                                        contentDescription = stringResource(R.string.task_editor_pick_date),
                                    )
                                }
                            } else {
                                IconButton(onClick = {
                                    dueDate = null
                                    dueTimeMinutes = null
                                    reminderEnabled = false
                                    errorRes = null
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.action_clear),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    val todayDate = Dates.today()
                    val weekendDate = Dates.nextWeekend(todayDate, calendarType)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickDateChip(
                            label = stringResource(R.string.task_editor_quick_today),
                            active = dueDate == Dates.iso(todayDate),
                            onClick = { dueDate = Dates.iso(todayDate) },
                        )
                        QuickDateChip(
                            label = stringResource(R.string.task_editor_quick_tomorrow),
                            active = dueDate == Dates.iso(todayDate.plusDays(1)),
                            onClick = { dueDate = Dates.iso(todayDate.plusDays(1)) },
                        )
                        QuickDateChip(
                            label = stringResource(R.string.task_editor_quick_weekend),
                            active = dueDate == Dates.iso(weekendDate) &&
                                weekendDate != todayDate &&
                                weekendDate != todayDate.plusDays(1),
                            onClick = { dueDate = Dates.iso(weekendDate) },
                        )
                        QuickDateChip(
                            label = stringResource(R.string.task_editor_pick_date),
                            active = false,
                            onClick = { showDatePicker = true },
                        )
                    }
                }

                if (dueDate != null) {
                    Column {
                        Text(
                            text = stringResource(R.string.task_editor_due_time),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = dueTimeMinutes
                                    ?.let { Dates.formatMinutes(it, appLocale) }
                                    .orEmpty(),
                                onValueChange = { },
                                readOnly = true,
                                placeholder = { Text(stringResource(R.string.task_editor_due_time_none)) },
                                trailingIcon = {
                                    if (dueTimeMinutes != null) {
                                        IconButton(onClick = {
                                            dueTimeMinutes = null
                                            if (reminderOffset != ReminderOffset.ONE_DAY_BEFORE) {
                                                reminderOffset = ReminderOffset.ONE_DAY_BEFORE
                                            }
                                            errorRes = null
                                        }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.task_editor_due_time_clear),
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                            )
                            TextButton(onClick = { showTimePicker = true }) {
                                Text(stringResource(R.string.task_editor_pick_time))
                            }
                        }
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.task_editor_reminder),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = stringResource(R.string.task_editor_reminder_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = reminderEnabled,
                                onCheckedChange = { enabled ->
                                    reminderEnabled = enabled
                                    errorRes = null
                                    if (enabled) {
                                        onRequestNotificationPermission()
                                        if (dueTimeMinutes == null) {
                                            reminderOffset = ReminderOffset.ONE_DAY_BEFORE
                                        }
                                    }
                                },
                            )
                        }
                        if (reminderEnabled) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ReminderOffset.entries.forEach { offset ->
                                    val label = when (offset) {
                                        ReminderOffset.AT_DUE -> R.string.task_editor_reminder_at_due
                                        ReminderOffset.TEN_MINUTES_BEFORE -> R.string.task_editor_reminder_10m
                                        ReminderOffset.ONE_HOUR_BEFORE -> R.string.task_editor_reminder_1h
                                        ReminderOffset.ONE_DAY_BEFORE -> R.string.task_editor_reminder_1d
                                    }
                                    FilterPill(
                                        text = stringResource(label),
                                        selected = reminderOffset == offset,
                                        onClick = {
                                            if (offset != ReminderOffset.ONE_DAY_BEFORE && dueTimeMinutes == null) {
                                                errorRes = R.string.task_editor_reminder_needs_time
                                            } else {
                                                reminderOffset = offset
                                                errorRes = null
                                            }
                                        },
                                    )
                                }
                            }
                            if (dueTimeMinutes == null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.task_editor_reminder_day_before_rule),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        errorRes?.let { message ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            if (title.isBlank()) return@Button
                            val parsedDue = dueDate?.let(Dates::parse)
                            val remindAt = if (reminderEnabled) {
                                if (parsedDue == null) {
                                    errorRes = R.string.task_editor_reminder_no_date
                                    return@Button
                                }
                                if (dueTimeMinutes == null && reminderOffset != ReminderOffset.ONE_DAY_BEFORE) {
                                    errorRes = R.string.task_editor_reminder_needs_time
                                    return@Button
                                }
                                val result = RemindAtCalculator.calculate(
                                    dueDate = parsedDue,
                                    dueTimeMinutes = dueTimeMinutes,
                                    offset = reminderOffset,
                                    zone = ZoneId.systemDefault(),
                                    nowMillis = System.currentTimeMillis(),
                                )
                                if (result.epochMillis == null) {
                                    errorRes = when (result.error) {
                                        RemindError.IN_PAST -> R.string.task_editor_reminder_past
                                        RemindError.INVALID_DUE_TIME -> R.string.task_editor_time_invalid
                                        RemindError.MISSING_DUE_TIME -> R.string.task_editor_reminder_needs_time
                                        null -> R.string.task_editor_reminder_past
                                    }
                                    return@Button
                                }
                                result.epochMillis
                            } else {
                                null
                            }
                            errorRes = null
                            onSave(title, note, selectedTagId, priority, dueDate, dueTimeMinutes, remindAt)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = title.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
        }
    }

    if (showDatePicker) {
        val initial = dueDate?.let(Dates::parse) ?: Dates.today()
        if (calendarType == CalendarType.JALALI) {
            JalaliDatePickerDialog(
                initialDate = initial,
                localizedContext = localizedContext,
                onDismiss = { showDatePicker = false },
                onConfirm = { picked ->
                    dueDate = Dates.iso(picked)
                    showDatePicker = false
                },
            )
        } else {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = initial
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                val picked = java.time.Instant.ofEpochMilli(millis)
                                    .atZone(java.time.ZoneOffset.UTC)
                                    .toLocalDate()
                                dueDate = Dates.iso(picked)
                            }
                            showDatePicker = false
                        },
                    ) {
                        ProvideAppLocale(localizedContext) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        ProvideAppLocale(localizedContext) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                },
            ) {
                // Same window problem as the sheet: the picker's own month and weekday names
                // come from Material's resources and follow LocalContext.
                ProvideAppLocale(localizedContext) {
                    DatePicker(state = pickerState)
                }
            }
        }
    }

    if (showTimePicker) {
        TimeInputDialog(
            initialMinutes = dueTimeMinutes,
            localizedContext = localizedContext,
            onDismiss = { showTimePicker = false },
            onConfirm = { minutes ->
                dueTimeMinutes = minutes
                showTimePicker = false
                errorRes = null
            },
        )
    }
}

/**
 * A clock dialog with separate hour and minute fields.
 *
 * Material's TimePicker does not reliably follow the in-app language override for digits,
 * so the fields format through [Dates.formatDigits] and accept Persian/Arabic-Indic input
 * by normalising it before validation.
 */
@Composable
private fun TimeInputDialog(
    initialMinutes: Int?,
    localizedContext: Context,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hour by remember {
        mutableStateOf(initialMinutes?.let { "%02d".format(Locale.ROOT, it / 60) } ?: "")
    }
    var minute by remember {
        mutableStateOf(initialMinutes?.let { "%02d".format(Locale.ROOT, it % 60) } ?: "")
    }
    var invalid by remember { mutableStateOf(false) }

    ProvideAppLocale(localizedContext) {
        val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.task_editor_pick_time)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = Dates.formatDigits(hour, locale),
                            onValueChange = { raw ->
                                hour = Dates.toLatinDigits(raw).filter { it.isDigit() }.take(2)
                                invalid = false
                            },
                            label = { Text(stringResource(R.string.task_editor_time_hour)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = Dates.formatDigits(minute, locale),
                            onValueChange = { raw ->
                                minute = Dates.toLatinDigits(raw).filter { it.isDigit() }.take(2)
                                invalid = false
                            },
                            label = { Text(stringResource(R.string.task_editor_time_minute)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (invalid) {
                        Text(
                            text = stringResource(R.string.task_editor_time_invalid),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val h = hour.toIntOrNull()
                    val m = minute.toIntOrNull()
                    if (h != null && m != null && h in 0..23 && m in 0..59) {
                        onConfirm(h * 60 + m)
                    } else {
                        invalid = true
                    }
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun QuickDateChip(label: String, active: Boolean, onClick: () -> Unit) {
    FilterPill(text = label, selected = active, onClick = onClick)
}
