package com.tag.routine.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tag.routine.R
import com.tag.routine.data.Dates
import com.tag.routine.data.Priorities
import com.tag.routine.ui.DueState
import com.tag.routine.ui.TaskFilter
import com.tag.routine.ui.TaskRow
import com.tag.routine.ui.UiState
import com.tag.routine.ui.components.AnimatedCheck
import com.tag.routine.ui.components.AppCard
import com.tag.routine.ui.components.AppChip
import com.tag.routine.ui.components.EmptyState
import com.tag.routine.ui.components.MetaPill
import com.tag.routine.ui.components.SwipeToDelete
import com.tag.routine.ui.theme.spacing

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
    onEditTask: (com.tag.routine.data.TaskEntity) -> Unit,
    onAddTask: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm),
            placeholder = { Text(stringResource(R.string.tasks_search_hint)) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onSearch("") }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )

        // Filter chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
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

        Spacer(Modifier.height(8.dp))

        // Tag filter chips
        if (state.tags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                item {
                    AppChip(
                        text = stringResource(R.string.tasks_filter_all),
                        selected = tagFilter == null,
                        onClick = { onTagFilter(null) },
                    )
                }
                items(state.tags) { tag ->
                    AppChip(
                        text = tag.name,
                        selected = tagFilter == tag.id,
                        onClick = { onTagFilter(if (tagFilter == tag.id) null else tag.id) },
                        dotColor = Color(tag.colorHex),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Task list
        if (state.tasks.isEmpty()) {
            EmptyState(
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                },
                title = if (query.isNotBlank()) {
                    stringResource(R.string.tasks_no_matches)
                } else {
                    stringResource(R.string.tasks_empty_title)
                },
                message = if (query.isNotBlank()) {
                    stringResource(R.string.tasks_try_different_search)
                } else {
                    stringResource(R.string.tasks_empty_message)
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(MaterialTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                items(state.tasks, key = { it.task.id }) { row ->
                    SwipeToDelete(
                        onDelete = { onDeleteTask(row.task.id) },
                    ) {
                        TaskRowCard(row, onToggleTask, onDeleteTask, onEditTask)
                    }
                }
                item { Spacer(Modifier.height(80.dp)) } // FAB clearance
            }
        }
    }

    // FAB
    FloatingActionButton(
        onClick = onAddTask,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(MaterialTheme.spacing.lg),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(20.dp),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.tasks_add_task))
    }
    }
}

@Composable
private fun TaskRowCard(
    row: TaskRow,
    onToggleTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onEditTask: (com.tag.routine.data.TaskEntity) -> Unit,
) {
    val task = row.task
    val done = task.done
    val priorityColor = when (task.priority) {
        Priorities.HIGH -> Color(0xFFFF6B6B)
        Priorities.NORMAL -> Color(0xFFFFB65C)
        else -> Color(0xFF4FD9C0)
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Priority indicator pill
            Box(
                modifier = Modifier
                    .size(4.dp, 36.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(priorityColor),
            )
            Spacer(Modifier.size(12.dp))

            // Checkbox
            AnimatedCheck(
                checked = done,
                onClick = { onToggleTask(task.id) },
                size = 28.dp,
                accent = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(12.dp))

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEditTask(task) },
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (done) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = task.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.tag != null) {
                        MetaPill(
                            text = row.tag.name,
                            container = Color(row.tag.colorHex).copy(alpha = 0.15f),
                            contentColor = Color(row.tag.colorHex),
                        )
                    }
                    if (row.dueLabel != null) {
                        val dueColor = when (row.dueState) {
                            DueState.OVERDUE -> Color(0xFFFF6B6B)
                            DueState.TODAY -> Color(0xFFFFB65C)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        MetaPill(
                            text = row.dueLabel,
                            container = dueColor.copy(alpha = 0.12f),
                            contentColor = dueColor,
                        )
                    }
                }
            }

            // Delete button
            IconButton(
                onClick = { onDeleteTask(task.id) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.tasks_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
