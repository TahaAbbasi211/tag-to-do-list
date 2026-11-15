package com.tag.routine.ui.editors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tag.routine.R
import com.tag.routine.data.AppConstants
import com.tag.routine.data.Dates
import com.tag.routine.data.Groups
import com.tag.routine.data.RoutineColors
import com.tag.routine.data.RoutineEmojis
import com.tag.routine.data.RoutineEntity
import com.tag.routine.ui.components.AppButton
import com.tag.routine.ui.components.AppButtonVariant
import com.tag.routine.ui.components.FilterPill
import com.tag.routine.ui.i18n.ProvideAppLocale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorSheet(
    existing: RoutineEntity?,
    localizedContext: android.content.Context,
    onDismiss: () -> Unit,
    onOpenTemplates: (() -> Unit)? = null,
    onSave: (
        title: String,
        group: String,
        icon: String,
        timeMinutes: Int?,
        daysMask: Int,
        reminder: Boolean,
        colorHex: Long,
        colorId: Int,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var group by remember { mutableStateOf(existing?.groupName ?: Groups.MORNING) }
    var icon by remember { mutableStateOf(RoutineEmojis.resolve(existing?.icon ?: RoutineEmojis.DEFAULT)) }
    var timeMinutes by remember { mutableStateOf(existing?.timeMinutes) }
    var daysMask by remember { mutableStateOf(existing?.daysMask ?: 0b1111111) } // all days
    var reminder by remember { mutableStateOf(existing?.reminder ?: false) }
    var colorId by remember { mutableStateOf(existing?.colorId ?: 0) }
    var colorHex by remember {
        mutableStateOf(existing?.colorHex ?: RoutineColors.hexFor(0, isDark))
    }

    val groups = listOf(Groups.MORNING, Groups.AFTERNOON, Groups.EVENING)
    var hourText by remember {
        mutableStateOf(existing?.timeMinutes?.let { "%02d".format(it / 60) } ?: "07")
    }
    var minuteText by remember {
        mutableStateOf(existing?.timeMinutes?.let { "%02d".format(it % 60) } ?: "00")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        // The sheet is hosted in its own window, which replaces LocalContext with the
        // Activity's system-locale context. Re-provide the app language so the editor
        // matches the rest of the app instead of falling back to English.
        ProvideAppLocale(localizedContext) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    if (existing == null) R.string.routine_editor_new else R.string.routine_editor_edit,
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            if (existing == null && onOpenTemplates != null) {
                AppButton(
                    text = stringResource(R.string.routine_editor_from_template),
                    onClick = {
                        onDismiss()
                        onOpenTemplates()
                    },
                    variant = AppButtonVariant.Tonal,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            }

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.routine_editor_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            // Group selector
            Column {
                Text(
                    text = stringResource(R.string.routine_editor_group),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { g ->
                        FilterPill(
                            text = Groups.label(g, LocalContext.current.resources),
                            selected = group == g,
                            onClick = { group = g },
                        )
                    }
                }
            }

            // Emoji icon selector
            Column {
                Text(
                    text = stringResource(R.string.routine_editor_icon),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RoutineEmojis.all.forEach { emoji ->
                        val isSelected = icon == emoji
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { icon = emoji },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = emoji, fontSize = 22.sp)
                        }
                    }
                }
            }

            // 8-Color selector
            Column {
                Text(
                    text = stringResource(R.string.routine_editor_color),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RoutineColors.all.forEach { spec ->
                        val isSelected = colorId == spec.id
                        val resolvedHex = RoutineColors.hexFor(spec.id, isDark)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(resolvedHex))
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    colorId = spec.id
                                    colorHex = resolvedHex
                                },
                        )
                    }
                }
            }

            // Days of week selector
            Column {
                Text(
                    text = stringResource(R.string.routine_editor_days),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val dayLabels = Dates.weekdayInitials()
                    dayLabels.forEachIndexed { index, label ->
                        val bit = 1 shl index
                        val selected = (daysMask and bit) != 0
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable {
                                    daysMask = if (selected) daysMask and bit.inv() else daysMask or bit
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // Reminder time
            Column {
                Text(
                    text = stringResource(R.string.routine_editor_reminder_time),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { value ->
                            val digits = value.filter { it.isDigit() }.take(2)
                            hourText = digits
                            val h = digits.toIntOrNull()
                            if (h != null && h in 0 until AppConstants.HOURS_PER_DAY) {
                                timeMinutes = h * 60 + (minuteText.toIntOrNull() ?: 0)
                            }
                        },
                        label = { Text(stringResource(R.string.routine_editor_hour)) },
                        modifier = Modifier.width(96.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Text(":", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { value ->
                            val digits = value.filter { it.isDigit() }.take(2)
                            minuteText = digits
                            val m = digits.toIntOrNull()
                            if (m != null && m in 0..59) {
                                timeMinutes = (hourText.toIntOrNull() ?: 0) * 60 + m
                            }
                        },
                        label = { Text(stringResource(R.string.routine_editor_minute)) },
                        modifier = Modifier.width(96.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    if (timeMinutes != null) {
                        TextButton(onClick = { timeMinutes = null }) {
                            Text(stringResource(R.string.action_clear))
                        }
                    }
                }
            }

            // Reminder toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.routine_editor_notify),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (timeMinutes == null) {
                            stringResource(R.string.routine_editor_set_time_first)
                        } else {
                            stringResource(
                                R.string.routine_editor_remind_at,
                                "%02d:%02d".format((timeMinutes!!) / 60, (timeMinutes!!) % 60),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = reminder && timeMinutes != null,
                    onCheckedChange = { reminder = it },
                    enabled = timeMinutes != null,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onSave(title, group, icon, timeMinutes, daysMask, reminder, colorHex, colorId)
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
    }
}
