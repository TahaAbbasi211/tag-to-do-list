package com.tag.routine.ui.editors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tag.routine.R
import com.tag.routine.data.Dates
import com.tag.routine.data.Priorities
import com.tag.routine.data.TagEntity
import com.tag.routine.data.TaskEntity
import com.tag.routine.ui.components.FilterPill
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    existing: TaskEntity?,
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
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
    val dueTimeMinutes = existing?.dueTimeMinutes
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                OutlinedTextField(
                    value = dueDate?.let { Dates.parse(it)?.let(Dates::dayMonth) ?: it } ?: "",
                    onValueChange = { },
                    readOnly = true,
                    placeholder = { Text(stringResource(R.string.task_editor_due_none)) },
                    trailingIcon = {
                        if (dueDate == null) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = stringResource(R.string.task_editor_pick_date),
                            )
                        } else {
                            androidx.compose.material3.IconButton(onClick = { dueDate = null }) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickDateChip(
                        label = stringResource(R.string.task_editor_quick_today),
                        active = dueDate == Dates.iso(Dates.today()),
                        onClick = { dueDate = Dates.iso(Dates.today()) },
                    )
                    QuickDateChip(
                        label = stringResource(R.string.task_editor_quick_tomorrow),
                        active = dueDate == Dates.iso(Dates.today().plusDays(1)),
                        onClick = { dueDate = Dates.iso(Dates.today().plusDays(1)) },
                    )
                    QuickDateChip(
                        label = stringResource(R.string.task_editor_quick_weekend),
                        active = false,
                        onClick = { dueDate = Dates.iso(nextSaturday()) },
                    )
                    QuickDateChip(
                        label = stringResource(R.string.task_editor_pick_date),
                        active = false,
                        onClick = { showDatePicker = true },
                    )
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
                        if (title.isNotBlank()) {
                            onSave(title, note, selectedTagId, priority, dueDate, dueTimeMinutes, null)
                            onDismiss()
                        }
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

    if (showDatePicker) {
        val initial = dueDate?.let(Dates::parse) ?: Dates.today()
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
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun QuickDateChip(label: String, active: Boolean, onClick: () -> Unit) {
    FilterPill(text = label, selected = active, onClick = onClick)
}

/** The next Saturday, which is the start of the weekend in Iran. */
private fun nextSaturday(): LocalDate {
    val today = Dates.today()
    var candidate = today
    while (candidate.dayOfWeek != DayOfWeek.SATURDAY) {
        candidate = candidate.plusDays(1)
    }
    return candidate
}
