package com.tag.routine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tag.routine.R
import com.tag.routine.data.Dates
import com.tag.routine.data.Groups
import com.tag.routine.data.RoutineEntity
import com.tag.routine.ui.RoutineGroupSection
import com.tag.routine.ui.TodayRoutine
import com.tag.routine.ui.UiState
import com.tag.routine.ui.components.AnimatedCheck
import com.tag.routine.ui.components.EmptyState
import com.tag.routine.ui.components.RingWithLabel
import com.tag.routine.ui.components.RoutineIconMap
import com.tag.routine.ui.components.SectionHeader
import com.tag.routine.ui.components.SwipeToDelete
import com.tag.routine.ui.components.WeekStrip
import com.tag.routine.ui.theme.tagBrand

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    state: UiState,
    onToggleRoutine: (String, Boolean) -> Unit,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onAddRoutine: () -> Unit,
    onEditRoutine: (RoutineEntity) -> Unit,
    onDeleteRoutine: (RoutineEntity) -> Unit,
    onStartFocus: () -> Unit,
    onMoveRoutine: (String, Boolean) -> Unit,
) {
    val today = state.today
    val selected = state.selectedDate
    val isToday = selected == today
    val context = LocalContext.current
    val resources = context.resources

    // Build the greeting: cap the name so the line never overflows the hero card.
    val greetingText = if (isToday) {
        Dates.greetingWithName(
            name = state.settings.userName.take(18),
            resources = resources,
        )
    } else {
        Dates.relativeDayLabel(selected, today, resources)
    }

    var query by remember { mutableStateOf("") }

    // Filter routines by the search box; group sections with no match disappear.
    val visibleGroups = remember(state.groups, query) {
        if (query.isBlank()) {
            state.groups
        } else {
            val needle = query.trim().lowercase()
            state.groups.mapNotNull { section ->
                val hits = section.items.filter { it.routine.title.lowercase().contains(needle) }
                if (hits.isEmpty()) null else section.copy(items = hits)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Hero progress card, painted with the brand gradient.
            val brand = tagBrand()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(brand.heroStart, brand.heroMid, brand.heroEnd),
                        ),
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greetingText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = brand.onHero,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.today_complete_count,
                                state.completedCount,
                                state.routineCount,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = brand.onHeroMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        // FlowRow keeps each pill intact when the row runs out of width.
                        // A plain Row squeezes the last pill towards zero width, its text
                        // wraps line after line and the hero card balloons in height —
                        // which is exactly what happened once the Perfect-day badge
                        // joined the row on a 100% completed day.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val streak = state.insights?.currentStreak ?: 0
                            if (streak > 0) {
                                HeroPill(
                                    text = "🔥 " + stringResource(R.string.today_streak_days, streak),
                                    color = brand.streak,
                                )
                            }
                            if (state.routineCount > 0 && state.progress >= 1f) {
                                HeroPill(
                                    text = stringResource(R.string.today_perfect_day),
                                    color = brand.success,
                                )
                            }
                            HeroPill(
                                text = "▶ " + stringResource(R.string.today_focus_start),
                                color = brand.onHero,
                                onClick = onStartFocus,
                            )
                        }
                    }
                    RingWithLabel(
                        progress = state.progress,
                        ringSize = 116.dp,
                        strokeWidth = 12.dp,
                        trackColor = brand.onHero.copy(alpha = 0.14f),
                        startColor = brand.ringStart,
                        endColor = brand.ringEnd,
                    ) {
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = brand.onHero,
                        )
                    }
                }
            }

            // Week strip
            WeekStrip(
                selected = selected,
                today = today,
                dotFor = { date -> state.dayRatios[Dates.iso(date)] ?: 0f },
                onSelect = onSelectDate,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(8.dp))

            if (state.groups.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.today_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            // Routine groups
            if (state.groups.isEmpty()) {
                EmptyState(
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    },
                    title = stringResource(R.string.today_empty_title),
                    message = stringResource(R.string.today_empty_message),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(visibleGroups, key = { it.name }) { section ->
                        RoutineGroupCard(
                            section = section,
                            onToggleRoutine = onToggleRoutine,
                            onEditRoutine = onEditRoutine,
                            onDeleteRoutine = onDeleteRoutine,
                            onMoveRoutine = onMoveRoutine,
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) } // FAB clearance
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = onAddRoutine,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.today_add_routine))
        }
    }
}

@Composable
private fun HeroPill(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.22f))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun RoutineGroupCard(
    section: RoutineGroupSection,
    onToggleRoutine: (String, Boolean) -> Unit,
    onEditRoutine: (RoutineEntity) -> Unit,
    onDeleteRoutine: (RoutineEntity) -> Unit,
    onMoveRoutine: (String, Boolean) -> Unit,
) {
    val resources = LocalContext.current.resources
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                title = Groups.label(section.name, resources),
                subtitle = "${section.items.count { it.done }} / ${section.items.size}",
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                section.items.forEachIndexed { index, item ->
                    SwipeToDelete(onDelete = { onDeleteRoutine(item.routine) }) {
                        RoutineRow(
                            item = item,
                            canMoveUp = index > 0,
                            canMoveDown = index < section.items.lastIndex,
                            onToggleRoutine = onToggleRoutine,
                            onEditRoutine = onEditRoutine,
                            onDeleteRoutine = onDeleteRoutine,
                            onMoveRoutine = onMoveRoutine,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineRow(
    item: TodayRoutine,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleRoutine: (String, Boolean) -> Unit,
    onEditRoutine: (RoutineEntity) -> Unit,
    onDeleteRoutine: (RoutineEntity) -> Unit,
    onMoveRoutine: (String, Boolean) -> Unit,
) {
    val routine = item.routine
    val done = item.done
    val icon = RoutineIconMap.icon(routine.icon)
    val timeLabel = routine.timeMinutes?.let { Dates.timeLabel(it) }
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { onToggleRoutine(routine.id, !done) },
                onLongClick = { menuOpen = true },
            ),
        color = if (done) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (done) MaterialTheme.colorScheme.primary else Color(routine.colorHex),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (done) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (done) FontWeight.Normal else FontWeight.Medium,
                    color = if (done) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (timeLabel != null) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_up)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.ArrowUpward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        enabled = canMoveUp,
                        onClick = {
                            menuOpen = false
                            onMoveRoutine(routine.id, true)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_down)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        enabled = canMoveDown,
                        onClick = {
                            menuOpen = false
                            onMoveRoutine(routine.id, false)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            menuOpen = false
                            onEditRoutine(routine)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDeleteRoutine(routine)
                        },
                    )
                }
            }

            AnimatedCheck(
                checked = done,
                onClick = { onToggleRoutine(routine.id, !done) },
                size = 28.dp,
                accent = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
