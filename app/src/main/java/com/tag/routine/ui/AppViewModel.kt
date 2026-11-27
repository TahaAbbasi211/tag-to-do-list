package com.tag.routine.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tag.routine.AppContainer
import com.tag.routine.BuildConfig
import com.tag.routine.data.AnimationSetting
import com.tag.routine.data.AppConstants
import com.tag.routine.data.AppSettings
import com.tag.routine.data.BackupCodec
import com.tag.routine.data.BackupErrorCode
import com.tag.routine.data.BackupMode
import com.tag.routine.data.BackupPayload
import com.tag.routine.data.BackupPreview
import com.tag.routine.data.BackupRestoreReport
import com.tag.routine.data.BackupValidationException
import com.tag.routine.data.CalendarType
import com.tag.routine.data.CopyEngine
import com.tag.routine.data.DateRolloverPolicy
import com.tag.routine.data.Dates
import com.tag.routine.data.FocusEngine
import com.tag.routine.data.FocusStats
import com.tag.routine.data.FocusSummary
import com.tag.routine.data.FocusSummaryEngine
import com.tag.routine.data.Groups
import com.tag.routine.data.Insights
import com.tag.routine.data.Palette
import com.tag.routine.data.Priorities
import com.tag.routine.data.RoutineEntity
import com.tag.routine.data.RoutineIcons
import com.tag.routine.data.RoutineTemplate
import com.tag.routine.data.SafetyBackupStore
import com.tag.routine.data.Snapshot
import com.tag.routine.data.StatsEngine
import com.tag.routine.data.TagEntity
import com.tag.routine.data.TaskEntity
import com.tag.routine.notifications.FocusTimerAlarms
import com.tag.routine.notifications.FocusTimerRuntime
import com.tag.routine.notifications.FocusTimerService
import com.tag.routine.notifications.ReminderScheduler
import com.tag.routine.notifications.TaskReminderAction
import com.tag.routine.notifications.TaskReminderPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
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

data class CopyFeedback(
    val count: Int,
    val fromYesterday: Boolean,
    val eventId: Long,
)

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val isExport: Boolean) : BackupUiState
    data class ExportSuccess(val fileName: String) : BackupUiState
    data object ExportError : BackupUiState
    data class PreviewReady(val payload: BackupPayload, val preview: BackupPreview) : BackupUiState
    data class ConfirmReplace(val payload: BackupPayload, val preview: BackupPreview) : BackupUiState
    data class RestoreSuccess(val report: BackupRestoreReport) : BackupUiState
    data class RestoreError(val code: BackupErrorCode) : BackupUiState
}

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
    val scheduledDates: Set<String> = emptySet(),
    val todayTasks: List<TaskRow> = emptyList(),
    val allRoutines: List<RoutineEntity> = emptyList(),
    /**
     * All non-archived routines stored on disk (including past ended ones), read-only
     * source for quick-add suggestions.
     */
    val routineHistory: List<RoutineEntity> = emptyList(),
    /**
     * Items the previous day shows that the selected day does not, i.e. exactly what the
     * "copy yesterday to today" action would create. Empty when there is nothing to copy,
     * which is what hides the button.
     */
    val copyFromPreviousDay: List<RoutineEntity> = emptyList(),
    val totalTasks: Int = 0,
    val doneTasks: Int = 0,
    val focusStats: FocusStats = FocusStats(),
    val focusSummary: FocusSummary = FocusSummary(),
) {
    val completedCount: Int get() = groups.sumOf { section -> section.items.count { it.done } }
    val routineCount: Int get() = groups.sumOf { it.items.size }
    val progress: Float get() = if (routineCount == 0) 0f else completedCount.toFloat() / routineCount
}

private data class Filters(val query: String, val filter: TaskFilter, val tagId: String?)

private data class DateCursor(val today: LocalDate, val selected: LocalDate)

class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository
    private val application = container.appContext

    private val _dates = MutableStateFlow(
        DateCursor(
            today = container.currentDate.value,
            selected = container.currentDate.value,
        ),
    )
    val selectedDate: StateFlow<LocalDate> = _dates
        .map { it.selected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _dates.value.selected)

    init {
        viewModelScope.launch {
            container.currentDate.collect { observedToday ->
                applyObservedDate(observedToday)
            }
        }
    }

    private fun applyObservedDate(observedToday: LocalDate) {
        _dates.update { cursor ->
            val reconciled = DateRolloverPolicy.reconcile(
                currentToday = cursor.today,
                selectedDate = cursor.selected,
                observedToday = observedToday,
            )
            if (reconciled.changed) {
                DateCursor(today = reconciled.today, selected = reconciled.selectedDate)
            } else {
                cursor
            }
        }
    }

    fun refreshDateIfNeeded(observed: LocalDate = Dates.today()) {
        container.refreshCurrentDate(observed)
        applyObservedDate(observed)
    }

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

    fun startFocus(routine: RoutineEntity?, minutes: Int) {
        val clamped = AppConstants.clampFocusMinutes(minutes)
        viewModelScope.launch { repository.setFocusMinutes(clamped) }
        focusController.start(routine, clamped)
    }

    fun switchFocus(routine: RoutineEntity?, minutes: Int) {
        val clamped = AppConstants.clampFocusMinutes(minutes)
        viewModelScope.launch { repository.setFocusMinutes(clamped) }
        focusController.switchTo(routine, clamped)
    }

    fun pauseFocus() = focusController.pause()

    fun resumeFocus() = focusController.resume()

    fun skipFocusBreak() = focusController.skipBreak()

    fun stopFocus() = focusController.stop()

    fun finishFocus() = focusController.finish()

    private val selectedFiltersLock = Any()

    val state: StateFlow<UiState> = combine(
        repository.snapshot,
        _dates,
        filters,
    ) { snapshot, dates, active ->
        buildState(snapshot, dates.today, dates.selected, active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(AppConstants.STATE_TIMEOUT_MILLIS), UiState())

    private fun buildState(
        snapshot: Snapshot,
        today: LocalDate,
        selected: LocalDate,
        active: Filters,
    ): UiState {
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
        val filtered = TaskFilterEngine.apply(
            snapshot.tasks,
            TaskFilterEngine.Criteria(
                query = active.query,
                filter = active.filter,
                tagId = active.tagId,
                today = today,
            ),
        )

        val calendarType = snapshot.settings.calendarType
        val rows = filtered
            .map { toRow(it, tagById[it.tagId], today, calendarType) }
            .sortedWith(
                compareBy<TaskRow> { it.task.done }
                    .thenByDescending { dueWeight(it.dueState) }
                    .thenByDescending { it.task.priority }
                    .thenBy { it.task.dueDate ?: "9999-12-31" },
            )

        val dueToday = snapshot.tasks
            .filter { !it.done && it.dueDate == Dates.iso(today) }
            .map { toRow(it, tagById[it.tagId], today, calendarType) }

        val focusStats = FocusEngine.compute(
            sessions = snapshot.focusSessions,
            routines = snapshot.routines,
            today = today,
        )
        val focusSummary = FocusSummaryEngine.compute(
            sessions = snapshot.focusSessions,
            routines = snapshot.routines,
            today = today,
        )
        val insights = StatsEngine
            .compute(snapshot.routines, snapshot.routineLogs, snapshot.taskLogs, today)
            .copy(focusMinutes = focusSummary.totalMinutes)

        val logsByDate = snapshot.routineLogs
            .groupBy({ it.date }, { it.routineId })
            .mapValues { it.value.toSet() }
        val futureWeekDays = (1L..7L).map { offset ->
            StatsEngine.daySummary(today.plusDays(offset), snapshot.routines, logsByDate)
        }
        val allKnownDays = insights.days + futureWeekDays
        val dayRatios = allKnownDays.associate { Dates.iso(it.date) to it.ratio }
        val scheduledDates = allKnownDays.filter { it.hasData }.map { Dates.iso(it.date) }.toSet()

        val todayEpoch = today.toEpochDay()
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
            dayRatios = dayRatios,
            scheduledDates = scheduledDates,
            todayTasks = dueToday,
            allRoutines = snapshot.routines.filter {
                !it.archived && (it.endDateEpochDay == null || it.endDateEpochDay >= todayEpoch)
            },
            routineHistory = snapshot.routines.filter { !it.archived },
            copyFromPreviousDay = CopyEngine.plan(
                sourceDay = selected.minusDays(1),
                targetDay = selected,
                routines = snapshot.routines,
                orderStart = 0,
                now = 0L,
            ),
            totalTasks = snapshot.tasks.size,
            doneTasks = snapshot.tasks.count { it.done },
            focusStats = focusStats,
            focusSummary = focusSummary,
        )
    }

    private fun toRow(
        task: TaskEntity,
        tag: TagEntity?,
        today: LocalDate,
        calendarType: CalendarType = CalendarType.GREGORIAN,
    ): TaskRow {
        val due = task.dueDate?.let { Dates.parse(it) }
        val dueState = when {
            task.done || due == null -> DueState.NONE
            due.isBefore(today) -> DueState.OVERDUE
            due == today -> DueState.TODAY
            due.isBefore(today.plusDays(7)) -> DueState.SOON
            else -> DueState.LATER
        }
        return TaskRow(
            task,
            tag,
            due?.let { Dates.relativeDayLabel(it, today, calendarType = calendarType) },
            dueState,
        )
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
        _dates.update { it.copy(selected = date) }
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
        val date = _dates.value.selected
        viewModelScope.launch { repository.toggleRoutine(routineId, date, done) }
    }

    fun toggleTask(taskId: String) = viewModelScope.launch {
        val task = repository.task(taskId) ?: return@launch
        val becomingDone = !task.done
        repository.setTaskDone(taskId, becomingDone)
        val updated = task.copy(done = becomingDone)
        when (TaskReminderPolicy.actionForToggle(updated, becomingDone, System.currentTimeMillis())) {
            TaskReminderAction.SCHEDULE -> scheduleTaskReminder(updated)
            TaskReminderAction.CANCEL -> cancelTaskReminder(taskId)
            TaskReminderAction.NONE -> Unit
        }
    }

    fun deleteTask(taskId: String) = viewModelScope.launch {
        cancelTaskReminder(taskId)
        repository.deleteTask(taskId)
    }

    fun deleteRoutine(routineId: String) = viewModelScope.launch {
        repository.deleteRoutine(routineId)
        rescheduleReminders(removedRoutineIds = listOf(routineId))
    }

    fun excludeRoutineForDate(routineId: String, date: LocalDate) = viewModelScope.launch {
        repository.excludeRoutineForDate(routineId, date)
        rescheduleReminders()
    }

    fun endRoutineFromDate(routineId: String, date: LocalDate) = viewModelScope.launch {
        repository.endRoutineFromDate(routineId, date)
        rescheduleReminders()
    }

    // ------------------------------------------------------------ copy items

    private var copyEventSeq = 0L

    /** Outcome of the last copy action, consumed by the snackbar. */
    private val _copyResult = MutableStateFlow<CopyFeedback?>(null)
    val copyResult: StateFlow<CopyFeedback?> = _copyResult.asStateFlow()

    fun consumeCopyResult() {
        _copyResult.value = null
    }

    /**
     * Copies the previous day's items onto the day the user is looking at. Completion state
     * is never copied, and running it twice writes nothing the second time.
     */
    fun copyYesterdayToSelected() = viewModelScope.launch {
        val target = _dates.value.selected
        val copied = repository.copyRoutines(target.minusDays(1), target)
        if (copied > 0) rescheduleReminders()
        _copyResult.value = CopyFeedback(copied, fromYesterday = true, eventId = ++copyEventSeq)
    }

    /** Copies one routine onto every day the user picked in the copy sheet. */
    fun copyRoutineToDays(routineId: String, targetDays: List<LocalDate>) = viewModelScope.launch {
        val copied = repository.copyRoutineToDays(routineId, targetDays)
        if (copied > 0) rescheduleReminders()
        _copyResult.value = CopyFeedback(copied, fromYesterday = false, eventId = ++copyEventSeq)
    }

    // ------------------------------------------------------------ undo support

    /** Re-inserts a routine the user just deleted, preserving its id and order. */
    fun restoreRoutine(routine: RoutineEntity) = viewModelScope.launch {
        repository.saveRoutine(routine)
        rescheduleReminders()
    }

    /** Re-inserts a task the user just deleted. */
    fun restoreTask(task: TaskEntity) = viewModelScope.launch {
        repository.saveTask(task)
        rescheduleReminders()
    }

    /** Re-inserts a tag the user just deleted. */
    fun restoreTag(tag: TagEntity) = viewModelScope.launch { repository.saveTag(tag) }

    /** Renames or recolours a tag without touching the tasks that use it. */
    fun updateTag(tag: TagEntity) = viewModelScope.launch { repository.saveTag(tag) }

    /** Moves a routine one slot earlier ([up] = true) or later within its group. */
    fun moveRoutine(routineId: String, up: Boolean) = viewModelScope.launch {
        repository.moveRoutine(routineId, up)
    }

    // ------------------------------------------------------------ backup

    private val _backupUiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupUiState: StateFlow<BackupUiState> = _backupUiState.asStateFlow()

    fun dismissBackupDialog() {
        _backupUiState.value = BackupUiState.Idle
    }

    fun exportBackupToUri(uri: Uri) {
        if (_backupUiState.value is BackupUiState.Working) return
        viewModelScope.launch {
            _backupUiState.value = BackupUiState.Working(isExport = true)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = runCatching {
                        application.contentResolver.openOutputStream(uri, "wt")
                    }.getOrNull() ?: application.contentResolver.openOutputStream(uri)
                        ?: error("Could not open destination file")
                    stream.use { out ->
                        BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8)).use { writer ->
                            repository.exportBackupTo(
                                writer = writer,
                                appVersion = BuildConfig.VERSION_NAME,
                                appVersionCode = BuildConfig.VERSION_CODE,
                            )
                        }
                    }
                    resolveDisplayName(uri)
                }
            }
            _backupUiState.value = result.fold(
                onSuccess = { name -> BackupUiState.ExportSuccess(name) },
                onFailure = { BackupUiState.ExportError },
            )
        }
    }

    fun inspectBackupUri(uri: Uri) {
        if (_backupUiState.value is BackupUiState.Working) return
        viewModelScope.launch {
            _backupUiState.value = BackupUiState.Working(isExport = false)
            val next = withContext(Dispatchers.IO) {
                try {
                    val declaredLength = runCatching {
                        application.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                    }.getOrNull() ?: -1L
                    val payload = application.contentResolver.openInputStream(uri)?.use { stream ->
                        BackupCodec.readAndValidate(stream, declaredLength)
                    } ?: throw BackupValidationException(BackupErrorCode.IO_ERROR, "Could not open file")
                    val preview = BackupCodec.buildPreview(payload)
                    BackupUiState.PreviewReady(payload, preview)
                } catch (e: BackupValidationException) {
                    BackupUiState.RestoreError(e.code)
                } catch (_: Throwable) {
                    BackupUiState.RestoreError(BackupErrorCode.IO_ERROR)
                }
            }
            _backupUiState.value = next
        }
    }

    fun requestReplaceConfirmation() {
        val current = _backupUiState.value
        if (current is BackupUiState.PreviewReady) {
            _backupUiState.value = BackupUiState.ConfirmReplace(current.payload, current.preview)
        }
    }

    fun backToPreview() {
        val current = _backupUiState.value
        if (current is BackupUiState.ConfirmReplace) {
            _backupUiState.value = BackupUiState.PreviewReady(current.payload, current.preview)
        }
    }

    fun confirmRestore(mode: BackupMode) {
        val (payload, _) = when (val current = _backupUiState.value) {
            is BackupUiState.PreviewReady -> if (mode == BackupMode.MERGE) current.payload to current.preview else return
            is BackupUiState.ConfirmReplace -> if (mode == BackupMode.REPLACE) current.payload to current.preview else return
            else -> return
        }
        viewModelScope.launch {
            _backupUiState.value = BackupUiState.Working(isExport = false)
            val next = withContext(Dispatchers.IO) {
                try {
                    val safetyDir = File(application.filesDir, SafetyBackupStore.DIR_NAME)
                    val taskIdsBefore = repository.freshSnapshot().tasks.map { it.id }.toSet()
                    val report = repository.restoreBackup(
                        payload = payload,
                        mode = mode,
                        safetyDir = safetyDir,
                        appVersion = BuildConfig.VERSION_NAME,
                        appVersionCode = BuildConfig.VERSION_CODE,
                    )
                    if (report.activeFocusRunCleared) {
                        FocusTimerAlarms.cancel(application)
                        FocusTimerService.stop(application)
                        FocusTimerRuntime.discardWithoutRecording()
                    }
                    val taskIdsAfter = repository.freshSnapshot().tasks.map { it.id }.toSet()
                    rescheduleRemindersNow(
                        removedRoutineIds = report.removedRoutineIds,
                        removedTaskIds = taskIdsBefore - taskIdsAfter,
                    )
                    BackupUiState.RestoreSuccess(report)
                } catch (e: BackupValidationException) {
                    BackupUiState.RestoreError(e.code)
                } catch (_: Throwable) {
                    BackupUiState.RestoreError(BackupErrorCode.RESTORE_FAILED)
                }
            }
            _backupUiState.value = next
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        val queried = runCatching {
            application.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return queried
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "tag-backup-${Dates.iso(Dates.today())}.json"
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
        rescheduleReminders()
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
        colorId: Int = 0,
        repeatDaily: Boolean = false,
    ) = viewModelScope.launch {
        val targetEpochDay = _dates.value.selected.toEpochDay()
        val resolvedDateEpochDay: Long
        val resolvedEndDateEpochDay: Long?
        val resolvedExcludedDates: String
        if (existing == null) {
            resolvedDateEpochDay = targetEpochDay
            resolvedEndDateEpochDay = null
            resolvedExcludedDates = ""
        } else if (existing.repeatDaily && !repeatDaily) {
            // Turning off daily repeat pins this routine solely to the currently edited day.
            resolvedDateEpochDay = targetEpochDay
            resolvedEndDateEpochDay = null
            resolvedExcludedDates = ""
        } else if (!existing.repeatDaily && repeatDaily) {
            resolvedDateEpochDay = if (existing.dateEpochDay > 0L) existing.dateEpochDay else targetEpochDay
            resolvedEndDateEpochDay = null
            resolvedExcludedDates = existing.excludedDates
        } else {
            resolvedDateEpochDay = if (!repeatDaily && existing.dateEpochDay == 0L) {
                targetEpochDay
            } else {
                existing.dateEpochDay
            }
            resolvedEndDateEpochDay = existing.endDateEpochDay
            resolvedExcludedDates = existing.excludedDates
        }

        val entity = (existing ?: RoutineEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            groupName = group,
            icon = icon,
            timeMinutes = timeMinutes,
            daysMask = daysMask,
            reminder = reminder,
            colorHex = colorHex,
            colorId = colorId,
            sortOrder = repository.newRoutineOrder(),
            archived = false,
            createdAt = System.currentTimeMillis(),
            dateEpochDay = resolvedDateEpochDay,
            repeatDaily = repeatDaily,
            endDateEpochDay = resolvedEndDateEpochDay,
            excludedDates = resolvedExcludedDates,
        )).copy(
            title = title.trim(),
            groupName = group,
            icon = icon,
            timeMinutes = timeMinutes,
            daysMask = daysMask,
            reminder = reminder,
            colorHex = colorHex,
            colorId = colorId,
            dateEpochDay = resolvedDateEpochDay,
            repeatDaily = repeatDaily,
            endDateEpochDay = resolvedEndDateEpochDay,
            excludedDates = resolvedExcludedDates,
        )
        repository.saveRoutine(entity)
        rescheduleReminders()
    }

    fun dismissTemplatesCard() = viewModelScope.launch {
        repository.setTemplatesCardDismissed(true)
    }

    fun applyTemplate(template: RoutineTemplate, resources: android.content.res.Resources) =
        viewModelScope.launch {
            var order = repository.newRoutineOrder()
            val now = System.currentTimeMillis()
            val targetEpochDay = _dates.value.selected.toEpochDay()
            template.items.forEachIndexed { idx, item ->
                val spec = com.tag.routine.data.RoutineColors.byId(item.colorId)
                repository.saveRoutine(
                    RoutineEntity(
                        id = UUID.randomUUID().toString(),
                        title = resources.getString(item.titleRes),
                        groupName = item.groupName,
                        icon = item.icon,
                        timeMinutes = item.timeMinutes,
                        daysMask = Dates.EVERY_DAY,
                        reminder = false,
                        colorHex = spec.lightHex,
                        colorId = item.colorId,
                        sortOrder = order + idx,
                        archived = false,
                        createdAt = now + idx,
                        dateEpochDay = targetEpochDay,
                        repeatDaily = false,
                    ),
                )
            }
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

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }

    fun setLanguage(tag: String) = viewModelScope.launch { repository.setLanguage(tag) }

    fun setCalendarType(type: CalendarType) = viewModelScope.launch { repository.setCalendarType(type) }

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
        repository.setDigestMinutes(AppConstants.clampDigestMinutes(minutes))
        rescheduleReminders()
    }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch { repository.setHaptics(enabled) }

    fun setAnimationSetting(setting: AnimationSetting) = viewModelScope.launch {
        repository.setAnimationSetting(setting)
    }

    /** Stores the default focus length used when the Focus screen opens. */
    fun setFocusMinutes(minutes: Int) = viewModelScope.launch {
        repository.setFocusMinutes(AppConstants.clampFocusMinutes(minutes))
    }

    fun setBreakModeEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setBreakModeEnabled(enabled)
    }

    fun setShortBreakMinutes(minutes: Int) = viewModelScope.launch {
        repository.setShortBreakMinutes(AppConstants.clampShortBreakMinutes(minutes))
    }

    fun setLongBreakMinutes(minutes: Int) = viewModelScope.launch {
        repository.setLongBreakMinutes(AppConstants.clampLongBreakMinutes(minutes))
    }

    fun setLongBreakInterval(sessions: Int) = viewModelScope.launch {
        repository.setLongBreakInterval(AppConstants.clampLongBreakInterval(sessions))
    }

    fun setAutoStartBreak(enabled: Boolean) = viewModelScope.launch {
        repository.setAutoStartBreak(enabled)
    }

    fun resetProgress() = viewModelScope.launch { repository.resetProgress() }

    fun rescheduleReminders(
        removedRoutineIds: Collection<String> = emptyList(),
        removedTaskIds: Collection<String> = emptyList(),
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rescheduleRemindersNow(removedRoutineIds, removedTaskIds)
            }
        }
    }

    private suspend fun rescheduleRemindersNow(
        removedRoutineIds: Collection<String> = emptyList(),
        removedTaskIds: Collection<String> = emptyList(),
    ) {
        val snapshot = repository.freshSnapshot()
        val settings = snapshot.settings
        ReminderScheduler.rescheduleAll(
            context = application,
            routines = snapshot.routines,
            tasks = snapshot.tasks,
            digestMinutes = settings.dailyDigestMinutes,
            digestEnabled = settings.notificationsEnabled && settings.dailyDigestEnabled,
            notificationsEnabled = settings.notificationsEnabled,
            removedRoutineIds = removedRoutineIds,
            removedTaskIds = removedTaskIds,
        )
    }

    private fun scheduleTaskReminder(task: TaskEntity) {
        val alarmManager = application.getSystemService(android.app.AlarmManager::class.java) ?: return
        ReminderScheduler.scheduleTask(application, alarmManager, task)
    }

    private fun cancelTaskReminder(taskId: String) {
        val alarmManager = application.getSystemService(android.app.AlarmManager::class.java) ?: return
        ReminderScheduler.cancelTask(application, alarmManager, taskId)
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(application)
                .cancel(com.tag.routine.notifications.TaskReminderIds.notificationId(taskId))
        }
    }

    fun defaultIconFor(group: String): String = RoutineIcons.defaultFor(group)

    fun priorityLabel(value: Int): String = Priorities.label(value)
}
