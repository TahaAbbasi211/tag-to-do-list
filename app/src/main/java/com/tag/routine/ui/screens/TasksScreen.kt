package com.tag.routine.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tag.routine.R
import com.tag.routine.data.CalendarType
import com.tag.routine.data.Dates
import com.tag.routine.data.Priorities
import com.tag.routine.data.TaskEntity
import com.tag.routine.ui.DueState
import com.tag.routine.ui.TaskFilter
import com.tag.routine.ui.TaskRow
import com.tag.routine.ui.UiState
import com.tag.routine.ui.accessibility.AccessibilityLabels
import com.tag.routine.ui.components.AnimatedCheck
import com.tag.routine.ui.components.AppButton
import com.tag.routine.ui.components.AppButtonVariant
import com.tag.routine.ui.components.AppCard
import com.tag.routine.ui.components.AppChip
import com.tag.routine.ui.components.EmptyState
import com.tag.routine.ui.components.LargeScreenFrame
import com.tag.routine.ui.components.MetaPill
import com.tag.routine.ui.components.ScreenHeader
import com.tag.routine.ui.components.SectionHeader
import com.tag.routine.ui.components.SwipeToDelete
import com.tag.routine.ui.theme.spacing
import com.tag.routine.ui.theme.tagBrand
import java.time.LocalDate
import java.util.Locale

@Composable
fun TasksScreen(
    state: UiState,
    query: String,
    filter: TaskFilter,
    tagFilter: String?,
    onSearch: (String) -> Unit,
    onFilter: (TaskFilter) -> Unit,
    onTagFilter: (String?) -> Unit,
    onToggleTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onAddTask: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    val activeFilterLabel = stringResource(
        when (filter) {
            TaskFilter.ALL -> R.string.tasks_filter_all
            TaskFilter.TODAY -> R.string.tasks_filter_today
            TaskFilter.UPCOMING -> R.string.tasks_filter_upcoming
            TaskFilter.OVERDUE -> R.string.tasks_filter_overdue
            TaskFilter.DONE -> R.string.tasks_filter_done
        },
    )

    LargeScreenFrame {
        Column(modifier = Modifier.fillMaxSize()) {
            // Page header
            ScreenHeader(
                title = stringResource(R.string.nav_tasks),
                subtitle = stringResource(R.string.tasks_subtitle),
                modifier = Modifier.padding(
                    start = spacing.lg,
                    end = spacing.lg,
                    top = spacing.lg,
                    bottom = spacing.md,
                ),
                trailing = if (state.totalTasks > 0) {
                    {
                        MetaPill(
                            text = Dates.formatDigits("${state.doneTasks} / ${state.totalTasks}", appLocale),
                            container = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                } else {
                    null
                },
            )

            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg),
                placeholder = { Text(stringResource(R.string.tasks_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onSearch("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.action_clear),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Spacer(Modifier.height(spacing.md))

            // Primary status filters
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                item {
                    AppChip(
                        text = stringResource(R.string.tasks_filter_all),
                        selected = filter == TaskFilter.ALL,
                        onClick = { onFilter(TaskFilter.ALL) },
                        trailingCount = state.totalTasks,
                    )
                }
                item {
                    AppChip(
                        text = stringResource(R.string.tasks_filter_today),
                        selected = filter == TaskFilter.TODAY,
                        onClick = { onFilter(TaskFilter.TODAY) },
                    )
                }
                item {
                    AppChip(
                        text = stringResource(R.string.tasks_filter_upcoming),
                        selected = filter == TaskFilter.UPCOMING,
                        onClick = { onFilter(TaskFilter.UPCOMING) },
                    )
                }
                item {
                    AppChip(
                        text = stringResource(R.string.tasks_filter_overdue),
                        selected = filter == TaskFilter.OVERDUE,
                        onClick = { onFilter(TaskFilter.OVERDUE) },
                    )
                }
                item {
                    AppChip(
                        text = stringResource(R.string.tasks_filter_done),
                        selected = filter == TaskFilter.DONE,
                        onClick = { onFilter(TaskFilter.DONE) },
                        trailingCount = state.doneTasks,
                    )
                }
            }

            // Optional tag filters
            if (state.tags.isNotEmpty()) {
                Spacer(Modifier.height(spacing.sm))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    item {
                        AppChip(
                            text = stringResource(R.string.tasks_filter_all),
                            selected = tagFilter == null,
                            onClick = { onTagFilter(null) },
                        )
                    }
                    items(state.tags, key = { it.id }) { tag ->
                        AppChip(
                            text = tag.name,
                            selected = tagFilter == tag.id,
                            onClick = { onTagFilter(if (tagFilter == tag.id) null else tag.id) },
                            dotColor = Color(tag.colorHex),
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.md))

            // Task list or contextual empty state
            if (state.tasks.isEmpty()) {
                val hasActiveFilters = query.isNotBlank() || filter != TaskFilter.ALL || tagFilter != null
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    EmptyState(
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                        },
                        title = if (hasActiveFilters) {
                            stringResource(R.string.tasks_no_matches)
                        } else {
                            stringResource(R.string.tasks_empty_title)
                        },
                        message = if (hasActiveFilters) {
                            stringResource(R.string.tasks_try_different_search)
                        } else {
                            stringResource(R.string.tasks_empty_message)
                        },
                        action = {
                            if (hasActiveFilters) {
                                AppButton(
                                    text = stringResource(R.string.action_clear),
                                    onClick = {
                                        onSearch("")
                                        onFilter(TaskFilter.ALL)
                                        onTagFilter(null)
                                    },
                                    variant = AppButtonVariant.Tonal,
                                )
                            } else {
                                AppButton(
                                    text = stringResource(R.string.tasks_add_task),
                                    onClick = onAddTask,
                                    variant = AppButtonVariant.Tonal,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(88.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = spacing.lg,
                        end = spacing.lg,
                        top = spacing.xs,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    item(key = "section-header") {
                        SectionHeader(
                            title = activeFilterLabel,
                            trailing = {
                                MetaPill(
                                    text = Dates.formatDigits("${state.tasks.size}", appLocale),
                                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                    items(state.tasks, key = { it.task.id }) { row ->
                        SwipeToDelete(
                            onDelete = { onDeleteTask(row.task.id) },
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            TaskRowCard(
                                row = row,
                                today = state.today,
                                calendarType = state.settings.calendarType,
                                appLocale = appLocale,
                                onToggleTask = onToggleTask,
                                onDeleteTask = onDeleteTask,
                                onEditTask = onEditTask,
                            )
                        }
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = onAddTask,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(spacing.lg),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.tasks_add_task))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRowCard(
    row: TaskRow,
    today: LocalDate,
    calendarType: CalendarType,
    appLocale: Locale,
    onToggleTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
) {
    val task = row.task
    val done = task.done
    val brand = tagBrand()
    val spacing = MaterialTheme.spacing
    val resources = LocalContext.current.resources
    val formattedDueLabel = task.dueDate?.let(Dates::parse)?.let { due ->
        Dates.relativeDayLabel(due, today, resources, appLocale, calendarType)
    } ?: row.dueLabel?.let { Dates.formatDigits(it, appLocale) }

    val priorityColor = when (task.priority) {
        Priorities.HIGH -> MaterialTheme.colorScheme.error
        Priorities.NORMAL -> brand.warning
        else -> brand.success
    }

    val titleColor = if (done) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = if (brand.isDark) 0.78f else 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val noteColor = if (done) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val doneLabel = stringResource(R.string.a11y_status_done)
    val notDoneLabel = stringResource(R.string.a11y_status_not_done)
    val deleteActionLabel = stringResource(R.string.action_delete)
    val editActionLabel = stringResource(R.string.action_edit)

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        containerColor = if (done) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Priority indicator bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (done) priorityColor.copy(alpha = 0.45f) else priorityColor),
            )
            Spacer(Modifier.width(spacing.md))

            // Animated completion checkbox
            AnimatedCheck(
                checked = done,
                onClick = { onToggleTask(task.id) },
                size = 28.dp,
                accent = MaterialTheme.colorScheme.primary,
                stateDescription = AccessibilityLabels.checkState(done, doneLabel, notDoneLabel),
            )
            Spacer(Modifier.width(spacing.md))

            // Task details (tappable to edit)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onEditTask(task) }
                    // Swipe-to-delete has a TalkBack equivalent on the row itself.
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(deleteActionLabel) {
                                onDeleteTask(task.id)
                                true
                            },
                            CustomAccessibilityAction(editActionLabel) {
                                onEditTask(task)
                                true
                            },
                        )
                    }
                    .padding(vertical = spacing.xs),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (done) FontWeight.Normal else FontWeight.SemiBold,
                    color = titleColor,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.note.isNotBlank()) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = task.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = noteColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (row.tag != null || formattedDueLabel != null) {
                    Spacer(Modifier.height(spacing.sm))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        if (row.tag != null) {
                            val tagColor = Color(row.tag.colorHex)
                            MetaPill(
                                text = row.tag.name,
                                container = tagColor.copy(alpha = 0.16f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                leading = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(tagColor),
                                    )
                                },
                            )
                        }
                        if (formattedDueLabel != null) {
                            val dueColor = when (row.dueState) {
                                DueState.OVERDUE -> MaterialTheme.colorScheme.error
                                DueState.TODAY -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            MetaPill(
                                text = formattedDueLabel,
                                container = dueColor.copy(alpha = 0.12f),
                                contentColor = dueColor,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(spacing.xs))

            // Delete button
            IconButton(
                onClick = { onDeleteTask(task.id) },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.tasks_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
