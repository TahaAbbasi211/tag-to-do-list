package com.tag.routine.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tag.routine.AppContainer
import com.tag.routine.data.AppConstants
import com.tag.routine.data.AppSettings
import com.tag.routine.data.Dates
import com.tag.routine.data.FocusEngine
import com.tag.routine.data.FocusStats
import com.tag.routine.data.Groups
import com.tag.routine.data.ImportOutcome
import com.tag.routine.data.Insights
import com.tag.routine.data.Palette
import com.tag.routine.data.Priorities
import com.tag.routine.data.RoutineEntity
import com.tag.routine.data.RoutineIcons
import com.tag.routine.data.Snapshot
import com.tag.routine.data.StatsEngine
import com.tag.routine.data.TagEntity
import com.tag.routine.data.TaskEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

enum class Tab { TODAY, TASKS, FOCUS, INSIGHTS, SETTINGS }

enum class TaskFilter { ALL, TODAY, UPCOMING, OVERDUE, DONE }

enum class DueState { NONE, OVERDUE, TODAY, SOON, LATER }

data class TaskRow(
    val task: TaskEntity,
    val tag: TagEntity?,
    val dueLabel: String?,
    val dueState: DueState,
)

data class TodayRoutine(val routine: RoutineEntity, val done: Boolean)

data class RoutineGroupSection(val name: String, val items: List<TodayRoutine>)

data class UiState(
    val loaded: Boolean = false,
    val selectedDate: LocalDate = Dates.today(),
    val today: LocalDate = Dates.today(),
    val groups: List<RoutineGroupSection> = emptyList(),
    val tasks: List<TaskRow> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val insights: Insights? = null,
    val settings: AppSettings = AppSettings(),
    val routineRates: List<Pair<RoutineEntity, Float>> = emptyList(),
    val dayRatios: Map<String, Float> = emptyMap(),
    val todayTasks: List<TaskRow> = emptyList(),
    val allRoutines: List<RoutineEntity> = emptyList(),
    val totalTasks: Int = 0,
    val doneTasks: Int = 0,
    val focusStats: FocusStats = FocusStats(),
) {
    val completedCount: Int get() = groups.sumOf { section -> section.items.count { it.done } }
    val routineCount: Int get() = groups.sumOf { it.items.size }
    val progress: Float get() = if (routineCount == 0) 0f else completedCount.toFloat() / routineCount
}

private data class Filters(val query: String, val filter: TaskFilter, val tagId: String?)

class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository
    private val application = container.appContext

    private val _selectedDate = MutableStateFlow(Dates.today())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val search = MutableStateFlow("")
    private val filter = MutableStateFlow(TaskFilter.ALL)
    private val tagFilter = MutableStateFlow<String?>(null)

    val query: StateFlow<String> = search.asStateFlow()
    val activeFilter: StateFlow<TaskFilter> = filter.asStateFlow()
    val activeTag: StateFlow<String?> = tagFilter.asStateFlow()

    private val filters = combine(search, filter, tagFilter) { q, f, t -> Filters(q, f, t) }

    // ------------------------------------------------------------ focus timer

    private val focusController = FocusController(repository, viewModelScope)

    /** Live focus-timer state, delegated to [FocusController]. */
    val focus: StateFlow<FocusState> = focusController.state

    fun startFocus(routine: RoutineEntity, minutes: Int) {
        val clamped = AppConstants.clampFocusMinutes(minutes)
        viewModelScope.launch { repository.setFocusMinutes(clamped) }
        focusController.start(routine, clamped)
    }

    fun pauseFocus() = focusController.pause()

    fun resumeFocus() = focusController.resume()

    fun stopFocus() = focusController.stop()

    fun finishFocus() = focusController.finish()

    private val selectedFiltersLock = Any()

    val state: StateFlow<UiState> = combine(
        repository.snapshot,
        _selectedDate,
        filters,
    ) { snapshot, selected, active ->
        buildState(snapshot, selected, active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(AppConstants.STATE_TIMEOUT_MILLIS), UiState())

    private fun buildState(snapshot: Snapshot, selected: LocalDate, active: Filters): UiState {
        val today = Dates.today()
        val selectedIso = Dates.iso(selected)
        val doneIds = snapshot.routineLogs.filter { it.date == selectedIso }.map { it.routineId }.toSet()

        val scheduledSelected = StatsEngine.scheduledOn(selected, snapshot.routines)
        val groups = scheduledSelected
            .groupBy { it.groupName }
            .toList()
            .sortedBy { (name, _) -> Groups.rank(name) }
            .map { (name, items) ->
                RoutineGroupSection(
                    name = name,
                    // Timed routines come first in clock order; untimed ones keep the
                    // manual order the user set with the move up/down actions.
                    items = items
                        .sortedWith(
                            compareBy<RoutineEntity> { it.timeMinutes ?: Int.MAX_VALUE }
                                .thenBy { it.sortOrder },
                        )
                        .map { TodayRoutine(it, it.id in doneIds) },
                )
            }

        val tagById = snapshot.tags.associateBy { it.id }
        val scoped = snapshot.tasks.filter { active.tagId == null || it.tagId == active.tagId }
        val searched = if (active.query.isBlank()) scoped else scoped.filter {
            it.title.contains(active.query, ignoreCase = true) ||
                it.note.contains(active.query, ignoreCase = true)
        }

        val filtered = searched.filter { task ->
            when (active.filter) {
                TaskFilter.ALL -> true
                TaskFilter.DONE -> task.done
                TaskFilter.TODAY -> !task.done && task.dueDate == Dates.iso(today)
                TaskFilter.OVERDUE -> !task.done && task.dueDate != null &&
                    (Dates.parse(task.dueDate)?.isBefore(today) == true)
                TaskFilter.UPCOMING -> !task.done && task.dueDate != null &&
                    (Dates.parse(task.dueDate)?.isAfter(today) == true)
            }
        }

        val rows = filtered
            .map { toRow(it, tagById[it.tagId], today) }
            .sortedWith(
                compareBy<TaskRow> { it.task.done }
                    .thenByDescending { dueWeight(it.dueState) }
                    .thenByDescending { it.task.priority }
                    .thenBy { it.task.dueDate ?: "9999-12-31" },
            )

        val dueToday = snapshot.tasks
            .filter { !it.done && it.dueDate == Dates.iso(today) }
            .map { toRow(it, tagById[it.tagId], today) }

        val insights = StatsEngine
            .compute(snapshot.routines, snapshot.routineLogs, snapshot.taskLogs, today)
            .copy(focusMinutes = snapshot.focusMinutes)

        val focusStats = FocusEngine.compute(
            sessions = snapshot.focusSessions,
            routines = snapshot.routines,
            today = today,
        )

        return UiState(
            loaded = snapshot.loaded,
            selectedDate = selected,
            today = today,
            groups = groups,
            tasks = rows,
            tags = snapshot.tags,
            insights = insights,
            settings = snapshot.settings,
            routineRates = StatsEngine.routineRates(snapshot.routines, snapshot.routineLogs, today),
            dayRatios = insights.days.associate { Dates.iso(it.date) to it.ratio },
            todayTasks = dueToday,
            allRoutines = snapshot.routines.filter { !it.archived },
            totalTasks = snapshot.tasks.size,
            doneTasks = snapshot.tasks.count { it.done },
            focusStats = focusStats,
        )
    }

    private fun toRow(task: TaskEntity, tag: TagEntity?, today: LocalDate): TaskRow {
        val due = task.dueDate?.let { Dates.parse(it) }
        val dueState = when {
            task.done || due == null -> DueState.NONE
            due.isBefore(today) -> DueState.OVERDUE
            due == today -> DueState.TODAY
            due.isBefore(today.plusDays(7)) -> DueState.SOON
            else -> DueState.LATER
        }
        return TaskRow(task, tag, due?.let { Dates.relativeDayLabel(it, today) }, dueState)
    }

    private fun dueWeight(state: DueState): Int = when (state) {
        DueState.OVERDUE -> 4
        DueState.TODAY -> 3
        DueState.SOON -> 2
        DueState.LATER -> 1
        DueState.NONE -> 0
    }

    // ------------------------------------------------------------- actions

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun setSearch(value: String) {
        search.value = value
    }

    fun setFilter(value: TaskFilter) {
        filter.value = value
    }

    fun setTagFilter(value: String?) {
        tagFilter.value = value
    }

    fun toggleRoutine(routineId: String, done: Boolean) {
        val date = _selectedDate.value
        viewModelScope.launch { repository.toggleRoutine(routineId, date, done) }
    }

    fun toggleTask(taskId: String) = viewModelScope.launch {
        val task = repository.task(taskId) ?: return@launch
        repository.setTaskDone(taskId, !task.done)
    }

    fun deleteTask(taskId: String) = viewModelScope.launch { repository.deleteTask(taskId) }

    fun deleteRoutine(routineId: String) = viewModelScope.launch {
        repository.deleteRoutine(routineId)
        rescheduleReminders()
    }

    // ------------------------------------------------------------ undo support

    /** Re-inserts a routine the user just deleted, preserving its id and order. */
    fun restoreRoutine(routine: RoutineEntity) = viewModelScope.launch {
        repository.saveRoutine(routine)
        rescheduleReminders()
    }

    /** Re-inserts a task the user just deleted. */
    fun restoreTask(task: TaskEntity) = viewModelScope.launch { repository.saveTask(task) }

    /** Re-inserts a tag the user just deleted. */
    fun restoreTag(tag: TagEntity) = viewModelScope.launch { repository.saveTag(tag) }

    /** Moves a routine one slot earlier ([up] = true) or later within its group. */
    fun moveRoutine(routineId: String, up: Boolean) = viewModelScope.launch {
        repository.moveRoutine(routineId, up)
    }

    // ------------------------------------------------------------ backup

    /** Applies a restore that already happened in the file picker: just re-arm reminders. */
    fun importBackup(outcome: ImportOutcome) {
        if (outcome.ok) rescheduleReminders()
    }

    fun saveTask(
        existing: TaskEntity?,
        title: String,
        note: String,
        tagId: String?,
        priority: Int,
        dueDate: String?,
        dueTimeMinutes: Int?,
        remindAt: Long?,
    ) = viewModelScope.launch {
        val base = existing ?: TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            note = note,
            tagId = tagId,
            priority = priority,
            dueDate = dueDate,
            dueTimeMinutes = dueTimeMinutes,
            remindAt = remindAt,
            done = false,
            doneAt = null,
            createdAt = System.currentTimeMillis(),
        )
        repository.saveTask(
            base.copy(
                title = title.trim(),
                note = note.trim(),
                tagId = tagId,
                priority = priority,
                dueDate = dueDate,
                dueTimeMinutes = dueTimeMinutes,
                remindAt = remindAt,
            ),
        )
    }

    fun saveRoutine(
        existing: RoutineEntity?,
        title: String,
        group: String,
        icon: String,
        timeMinutes: Int?,
        daysMask: Int,
        reminder: Boolean,
        colorHex: Long,
    ) = viewModelScope.launch {
        val entity = (existing ?: RoutineEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            groupName = group,
            icon = icon,
            timeMinutes = timeMinutes,
            daysMask = daysMask,
            reminder = reminder,
            colorHex = colorHex,
            sortOrder = repository.newRoutineOrder(),
            archived = false,
            createdAt = System.currentTimeMillis(),
        )).copy(
            title = title.trim(),
            groupName = group,
            icon = icon,
            timeMinutes = timeMinutes,
            daysMask = daysMask,
            reminder = reminder,
            colorHex = colorHex,
        )
        repository.saveRoutine(entity)
        rescheduleReminders()
    }

    fun addTag(name: String): String {
        val id = UUID.randomUUID().toString()
        viewModelScope.launch {
            val order = repository.newTagOrder()
            repository.saveTag(TagEntity(id, name.trim(), Palette.at(order), order))
        }
        return id
    }

    fun deleteTag(tagId: String) = viewModelScope.launch { repository.deleteTag(tagId) }

    fun setThemeMode(mode: Int) = viewModelScope.launch { repository.setThemeMode(mode) }

    fun setTheme(palette: String) = viewModelScope.launch { repository.setTheme(palette) }

    fun setLanguage(tag: String) = viewModelScope.launch { repository.setLanguage(tag) }

    fun setUserName(name: String) = viewModelScope.launch { repository.setUserName(name) }

    /** Completes first-launch onboarding, storing the name the user entered. */
    fun completeOnboarding(name: String) = viewModelScope.launch {
        repository.completeOnboarding(name)
    }

    /** Dismisses first-launch onboarding without a name. */
    fun skipOnboarding() = viewModelScope.launch { repository.skipOnboarding() }

    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setNotificationsEnabled(enabled)
        rescheduleReminders()
    }

    fun setDigestEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setDigestEnabled(enabled)
        rescheduleReminders()
    }

    fun setDigestMinutes(minutes: Int) = viewModelScope.launch {
        repository.setDigestMinutes(minutes)
        rescheduleReminders()
    }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch { repository.setHaptics(enabled) }

    /** Stores the default focus length used when the Focus screen opens. */
    fun setFocusMinutes(minutes: Int) = viewModelScope.launch {
        repository.setFocusMinutes(AppConstants.clampFocusMinutes(minutes))
    }

    fun resetProgress() = viewModelScope.launch { repository.resetProgress() }

    /** Writes a JSON backup into the app's external files directory and returns the path. */
    fun exportBackup(): String {
        val json = repository.exportJson(repository.current())
        val dir = java.io.File(application.getExternalFilesDir(null) ?: application.filesDir, "backups")
        dir.mkdirs()
        val file = java.io.File(dir, "tag-backup-${Dates.iso(Dates.today())}.json")
        file.writeText(json)
        return file.absolutePath
    }

    fun rescheduleReminders() {
        val snapshot = repository.current()
        val settings = snapshot.settings
        com.tag.routine.notifications.ReminderScheduler.rescheduleAll(
            context = application,
            routines = snapshot.routines,
            digestMinutes = settings.dailyDigestMinutes,
            digestEnabled = settings.notificationsEnabled && settings.dailyDigestEnabled,
        )
    }

    fun defaultIconFor(group: String): String = RoutineIcons.defaultFor(group)

    fun priorityLabel(value: Int): String = Priorities.label(value)
}
