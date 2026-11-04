package com.tag.routine.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tag.routine.R
import com.tag.routine.data.AppConstants
import com.tag.routine.data.RoutineEntity
import com.tag.routine.ui.FocusState
import com.tag.routine.ui.components.RingWithLabel
import com.tag.routine.ui.components.RoutineIconMap

/** Bottom sheet that picks a routine and a duration, then runs a focus timer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSheet(
    routines: List<RoutineEntity>,
    focus: FocusState,
    onStart: (RoutineEntity, Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf<RoutineEntity?>(null) }
    var minutes by remember { mutableIntStateOf(AppConstants.DEFAULT_FOCUS_MINUTES) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.focus_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            if (focus.routineId != null) {
                RunningFocus(
                    focus = focus,
                    onPause = onPause,
                    onResume = onResume,
                    onFinish = onFinish,
                )
            } else {
                if (routines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.focus_pick_routine),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.focus_pick_routine),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    LazyColumn(
                        modifier = Modifier.height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(routines, key = { it.id }) { routine ->
                            val isSelected = selected?.id == routine.id
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                onClick = { selected = routine },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = RoutineIconMap.icon(routine.icon),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.size(12.dp))
                                    Text(
                                        text = routine.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.focus_minutes, minutes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppConstants.FOCUS_PRESETS.forEach { option ->                            FilterChip(
                                selected = minutes == option,
                                onClick = { minutes = option },
                                label = { Text(stringResource(R.string.focus_minutes, option)) },
                            )
                        }
                    }

                    Button(
                        onClick = { selected?.let { onStart(it, minutes) } },
                        enabled = selected != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.focus_start))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RunningFocus(
    focus: FocusState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.focus_running, focus.routineTitle.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RingWithLabel(
            progress = focus.progress,
            ringSize = 180.dp,
            strokeWidth = 14.dp,
        ) {
            val mins = focus.remainingSeconds / 60
            val secs = focus.remainingSeconds % 60
            Text(
                text = "%02d:%02d".format(mins, secs),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onFinish, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.focus_give_up))
            }
            Button(
                onClick = { if (focus.running) onPause() else onResume() },
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    stringResource(
                        if (focus.running) R.string.focus_pause else R.string.focus_resume,
                    ),
                )
            }
        }
    }
}
