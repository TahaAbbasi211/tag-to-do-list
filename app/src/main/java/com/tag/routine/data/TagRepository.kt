package com.tag.routine.data

import androidx.room.withTransaction
import com.tag.routine.widget.WidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.Writer
import java.time.LocalDate

/** Immutable view of everything the UI needs, emitted as one consistent snapshot. */
data class Snapshot(
    val routines: List<RoutineEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val routineLogs: List<RoutineLogEntity> = emptyList(),
    val taskLogs: List<TaskLogEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    /** Total completed focus time, in minutes. */
    val focusMinutes: Int = 0,
    /** All recorded focus runs, newest first. */
    val focusSessions: List<FocusSessionEntity> = emptyList(),
    val loaded: Boolean = false,
)

class TagRepository(
    private val db: TagDatabase,
    private val settingsStore: SettingsStore,
    scope: CoroutineScope,
    /** Application context, used only to refresh the home-screen widget after writes. */
    private val appContext: android.content.Context? = null,
) : FocusRunStorage {
    private val routineDao = db.routines()
    private val taskDao = db.tasks()
    private val tagDao = db.tags()
    private val logDao = db.logs()
    private val focusDao = db.focus()

    /** Redraws the widget after a data change; a no-op when no context was supplied. */
    private fun notifyWidget() {
        appContext?.let { WidgetRefresh.request(it) }
    }

    /**
     * A single hot snapshot of all app data. Keeping it eager means broadcasts
     * (reminders, boot) can read the latest data synchronously.
     */
    val snapshot: StateFlow<Snapshot> = combine(
        combine(
            combine(
                routineDao.observeAll(),
                taskDao.observeAll(),
                tagDao.observeAll(),
                logDao.observeAllRoutineLogs(),
                logDao.observeAllTaskLogs(),
            ) { routines, tasks, tags, routineLogs, taskLogs ->
                Snapshot(
                    routines = routines,
                    tasks = tasks,
                    tags = tags,
                    routineLogs = routineLogs,
                    taskLogs = taskLogs,
                    loaded = true,
                )
            },
            focusDao.observeAll(),
        ) { snap, sessions ->
            val millis = sessions.filter { it.completed }.sumOf { it.endedAt - it.startedAt }
            snap.copy(
                focusMinutes = (millis / 60_000L).toInt(),
                focusSessions = sessions,
            )
        },
        settingsStore.settings,
    ) { snap, settings -> snap.copy(settings = settings) }
        .stateIn(scope, SharingStarted.Eagerly, Snapshot())

    fun current(): Snapshot = snapshot.value

    val seeded: Flow<Boolean> = settingsStore.seeded

    // ------------------------------------------------------------ routines

    suspend fun toggleRoutine(routineId: String, date: LocalDate, done: Boolean) {
        val iso = Dates.iso(date)
        if (done) {
            logDao.insertRoutineLog(RoutineLogEntity(routineId, iso, System.currentTimeMillis()))
        } else {
            logDao.deleteRoutineLog(routineId, iso)
        }
        notifyWidget()
    }

    suspend fun saveRoutine(item: RoutineEntity) {
        routineDao.upsert(item)
        notifyWidget()
    }

    suspend fun deleteRoutine(routineId: String) {
        logDao.deleteRoutineLogsFor(routineId)
        routineDao.delete(routineId)
        notifyWidget()
    }

    /** Hides a repeating routine on [date] only, preserving the routine row and all logs. */
    suspend fun excludeRoutineForDate(routineId: String, date: LocalDate) {
        val item = routineDao.byId(routineId) ?: return
        routineDao.upsert(item.copy(excludedDates = ExcludedDates.withExcluded(item.excludedDates, Dates.iso(date))))
        notifyWidget()
    }

    /** Ends a repeating routine from [date] onwards without removing past history or logs. */
    suspend fun endRoutineFromDate(routineId: String, date: LocalDate) {
        val item = routineDao.byId(routineId) ?: return
        val lastActive = date.minusDays(1).toEpochDay()
        val bounded = item.endDateEpochDay?.let { minOf(it, lastActive) } ?: lastActive
        routineDao.upsert(item.copy(endDateEpochDay = bounded))
        notifyWidget()
    }

    suspend fun newRoutineOrder(): Int = routineDao.maxOrder() + 1

    // ------------------------------------------------------------ copy items

    private val copyMutex = Mutex()

    /**
     * Copies everything [sourceDay] shows onto [targetDay] and returns how many items were
     * written.
     *
     * Only items are created: a copy never carries a completion log, so the tick state is
     * not duplicated. Repeating items that already show up on [targetDay] are skipped, and
     * a copy left over from an earlier press is recognised by its derived id (see
     * [CopyEngine.copyId]), which makes the action safe to press twice.
     *
     * Runs under [copyMutex] against live DAO reads rather than the cached snapshot so
     * rapid double taps serialize cleanly and never plan the same copy twice.
     */
    suspend fun copyRoutines(sourceDay: LocalDate, targetDay: LocalDate): Int = copyMutex.withLock {
        val routines = routineDao.observeAll().first()
        val plan = CopyEngine.plan(
            sourceDay = sourceDay,
            targetDay = targetDay,
            routines = routines,
            orderStart = routineDao.maxOrder() + 1,
            now = System.currentTimeMillis(),
            existingIds = routineDao.allIds().toHashSet(),
        )
        plan.forEach { routineDao.upsert(it) }
        if (plan.isNotEmpty()) notifyWidget()
        plan.size
    }

    /**
     * Copies one routine onto each day in [targetDays] and returns how many items were
     * written. Days the routine already appears on are skipped, as are duplicates.
     */
    suspend fun copyRoutineToDays(routineId: String, targetDays: List<LocalDate>): Int = copyMutex.withLock {
        val routines = routineDao.observeAll().first()
        val item = routines.firstOrNull { it.id == routineId } ?: return@withLock 0
        val plan = CopyEngine.planToDays(
            item = item,
            targets = targetDays,
            routines = routines,
            orderStart = routineDao.maxOrder() + 1,
            now = System.currentTimeMillis(),
            existingIds = routineDao.allIds().toHashSet(),
        )
        plan.forEach { routineDao.upsert(it) }
        if (plan.isNotEmpty()) notifyWidget()
        plan.size
    }

    /**
     * Moves [routineId] one slot earlier or later within its own group, then rewrites
     * the sort order of the affected routines so the new sequence sticks.
     */
    suspend fun moveRoutine(routineId: String, up: Boolean) {
        val all = current().routines.filter { !it.archived }
        val target = all.firstOrNull { it.id == routineId } ?: return
        val siblings = all
            .filter { it.groupName == target.groupName }
            .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
        val index = siblings.indexOfFirst { it.id == routineId }
        if (index < 0) return
        val swapWith = if (up) index - 1 else index + 1
        if (swapWith !in siblings.indices) return

        val reordered = siblings.toMutableList().apply {
            val moved = removeAt(index)
            add(swapWith, moved)
        }
        // Re-number from the smallest existing order so relative order in other
        // groups is untouched.
        val base = siblings.minOf { it.sortOrder }
        reordered.forEachIndexed { position, item ->
            if (item.sortOrder != base + position) {
                routineDao.upsert(item.copy(sortOrder = base + position))
            }
        }
        notifyWidget()
    }

    suspend fun routine(id: String): RoutineEntity? = routineDao.byId(id)

    // ------------------------------------------------------------ tasks

    suspend fun saveTask(item: TaskEntity) {
        taskDao.upsert(item)
        notifyWidget()
    }

    suspend fun task(id: String): TaskEntity? = taskDao.byId(id)

    suspend fun setTaskDone(taskId: String, done: Boolean) {
        val task = taskDao.byId(taskId) ?: return
        val now = System.currentTimeMillis()
        val iso = Dates.iso(LocalDate.now())
        taskDao.upsert(task.copy(done = done, doneAt = if (done) now else null))
        if (done) {
            logDao.insertTaskLog(TaskLogEntity(taskId, iso, now))
        } else {
            logDao.deleteTaskLog(taskId, iso)
        }
        notifyWidget()
    }

    suspend fun deleteTask(taskId: String) {
        logDao.deleteTaskLogsFor(taskId)
        taskDao.delete(taskId)
        notifyWidget()
    }

    // ------------------------------------------------------------ tags

    suspend fun saveTag(item: TagEntity) = tagDao.upsert(item)

    suspend fun deleteTag(tagId: String) {
        taskDao.clearTag(tagId)
        tagDao.delete(tagId)
    }

    suspend fun newTagOrder(): Int = tagDao.maxOrder() + 1

    // ------------------------------------------------------------ settings

    suspend fun setThemeMode(mode: Int) = settingsStore.setThemeMode(mode)

    suspend fun setTheme(palette: String) = settingsStore.setThemePalette(palette)

    suspend fun setDynamicColor(enabled: Boolean) = settingsStore.setDynamicColor(enabled)

    suspend fun setLanguage(tag: String) = settingsStore.setLanguage(tag)

    suspend fun setCalendarType(type: CalendarType) = settingsStore.setCalendarType(type)

    suspend fun setUserName(name: String) = settingsStore.setUserName(name)

    /** Saves the first-launch name and marks onboarding complete in one write. */
    suspend fun completeOnboarding(name: String) {
        settingsStore.setUserName(name.trim())
        settingsStore.setOnboarded(true)
    }

    /** Marks onboarding complete without setting a name (the user skipped it). */
    suspend fun skipOnboarding() = settingsStore.setOnboarded(true)

    suspend fun ensureFirstLaunchDate(todayIso: String) =
        settingsStore.ensureFirstLaunchDate(todayIso)

    suspend fun setTemplatesCardDismissed(dismissed: Boolean) =
        settingsStore.setTemplatesCardDismissed(dismissed)

    suspend fun setAnimationSetting(setting: AnimationSetting) =
        settingsStore.setAnimationSetting(setting)

    suspend fun setNotificationsEnabled(enabled: Boolean) = settingsStore.setNotifications(enabled)

    suspend fun setDigestEnabled(enabled: Boolean) = settingsStore.setDigestEnabled(enabled)

    suspend fun setDigestMinutes(minutes: Int) = settingsStore.setDigestMinutes(minutes)

    suspend fun setHaptics(enabled: Boolean) = settingsStore.setHaptics(enabled)

    /** Remembers the focus length the user last chose. */
    suspend fun setFocusMinutes(minutes: Int) = settingsStore.setFocusMinutes(minutes)

    suspend fun setBreakModeEnabled(enabled: Boolean) = settingsStore.setBreakModeEnabled(enabled)

    suspend fun setShortBreakMinutes(minutes: Int) = settingsStore.setShortBreakMinutes(minutes)

    suspend fun setLongBreakMinutes(minutes: Int) = settingsStore.setLongBreakMinutes(minutes)

    suspend fun setLongBreakInterval(sessions: Int) = settingsStore.setLongBreakInterval(sessions)

    suspend fun setAutoStartBreak(enabled: Boolean) = settingsStore.setAutoStartBreak(enabled)

    suspend fun markSeeded() = settingsStore.setSeeded(true)

    // ------------------------------------------------------------ focus timer

    /**
     * The focus run currently in progress, restored from disk. This is what makes the
     * timer survive the user closing the app: the foreground service and the UI both
     * read the same durable record.
     */
    val activeFocusRun: Flow<ActiveFocusRun?> = settingsStore.activeFocusRun

    override suspend fun currentActiveFocusRun(): ActiveFocusRun? = activeFocusRun.first()

    override suspend fun setActiveFocusRun(run: ActiveFocusRun) = settingsStore.setActiveFocusRun(run)

    override suspend fun clearActiveFocusRun() = settingsStore.clearActiveFocusRun()

    override suspend fun focusBreakSettings(): FocusBreakSettings =
        FocusBreakSettings.from(settingsStore.currentSettings())

    /**
     * Records one focus run so it can show up in the insights screen.
     *
     * [id] is normally the id of the run that produced this session, which makes the
     * write idempotent: the foreground service finishing a run and a later alarm firing
     * for the same run both upsert the same row instead of recording it twice.
     */
    override suspend fun recordFocusSession(
        routineId: String?,
        startedAt: Long,
        endedAt: Long,
        plannedMinutes: Int,
        completed: Boolean,
        id: String,
    ) {
        focusDao.upsert(
            FocusSessionEntity(
                id = id,
                routineId = routineId,
                startedAt = startedAt,
                endedAt = endedAt,
                plannedMinutes = plannedMinutes,
                completed = completed,
            ),
        )
    }

    /** Total completed focus time, in minutes. */
    suspend fun totalFocusMinutes(): Int =
        (focusDao.totalCompletedMillis() / 60_000L).toInt()

    // ------------------------------------------------------------ maintenance

    suspend fun resetProgress() {
        logDao.clearRoutineLogs()
        logDao.clearTaskLogs()
        taskDao.clearAllDone()
        notifyWidget()
    }

    private val backupMutex = Mutex()

    private val backupStorage = object : BackupStorage {
        override suspend fun readSnapshotFresh(): Snapshot = freshSnapshot()

        override suspend fun runInTransaction(block: suspend () -> Unit) {
            db.withTransaction { block() }
        }

        override suspend fun clearAllTables() {
            logDao.clearRoutineLogs()
            logDao.clearTaskLogs()
            focusDao.clear()
            taskDao.clear()
            routineDao.clear()
            tagDao.clear()
        }

        override suspend fun insertTags(items: List<TagEntity>) = tagDao.upsertAll(items)

        override suspend fun insertRoutines(items: List<RoutineEntity>) = routineDao.upsertAll(items)

        override suspend fun insertTasks(items: List<TaskEntity>) = taskDao.upsertAll(items)

        override suspend fun insertRoutineLogs(items: List<RoutineLogEntity>) = logDao.insertRoutineLogs(items)

        override suspend fun insertTaskLogs(items: List<TaskLogEntity>) = logDao.insertTaskLogs(items)

        override suspend fun insertFocusSessions(items: List<FocusSessionEntity>) = focusDao.upsertAll(items)

        override suspend fun applySettings(settings: BackupSettings) {
            settingsStore.applyBackupSettings(settings)
        }

        override suspend fun readActiveFocusRun(): ActiveFocusRun? =
            settingsStore.activeFocusRun.first()

        override suspend fun clearActiveFocusRun() {
            settingsStore.clearActiveFocusRun()
        }

        override suspend fun restoreActiveFocusRun(run: ActiveFocusRun) {
            settingsStore.setActiveFocusRun(run)
        }
    }

    /** Reads a live, consistent snapshot directly from Room and DataStore. */
    suspend fun freshSnapshot(): Snapshot = db.withTransaction {
        val routines = routineDao.getAll()
        val tasks = taskDao.getAll()
        val tags = tagDao.getAll()
        val routineLogs = logDao.getAllRoutineLogs()
        val taskLogs = logDao.getAllTaskLogs()
        val focusSessions = focusDao.getAll()
        val settings = settingsStore.currentSettings()
        val millis = focusSessions.filter { it.completed }.sumOf { it.endedAt - it.startedAt }
        Snapshot(
            routines = routines,
            tasks = tasks,
            tags = tags,
            routineLogs = routineLogs,
            taskLogs = taskLogs,
            settings = settings,
            focusMinutes = (millis / 60_000L).toInt(),
            focusSessions = focusSessions,
            loaded = true,
        )
    }

    /** Serialises the given snapshot to JSON for backup/export. */
    fun exportJson(
        snapshot: Snapshot,
        appVersion: String = "1.6",
        appVersionCode: Int = 16,
    ): String = BackupCodec.encodeToString(
        snapshot = snapshot,
        appVersion = appVersion,
        appVersionCode = appVersionCode,
    )

    /** Streams a fresh backup directly into [writer]. */
    suspend fun exportBackupTo(
        writer: Writer,
        appVersion: String,
        appVersionCode: Int,
    ) = backupMutex.withLock {
        val fresh = freshSnapshot()
        BackupCodec.writeBackup(
            writer = writer,
            snapshot = fresh,
            appVersion = appVersion,
            appVersionCode = appVersionCode,
        )
    }

    /**
     * Restores a pre-validated [payload] using [mode], saving an automatic safety backup in
     * [safetyDir] beforehand and executing all database changes in one atomic transaction.
     */
    suspend fun restoreBackup(
        payload: BackupPayload,
        mode: BackupMode,
        safetyDir: File,
        appVersion: String,
        appVersionCode: Int,
    ): BackupRestoreReport = backupMutex.withLock {
        BackupCoordinator(
            storage = backupStorage,
            safetyDir = safetyDir,
            appVersion = appVersion,
            appVersionCode = appVersionCode,
        ).restore(payload, mode).also { notifyWidget() }
    }

    // ------------------------------------------------------------ seeding

    /**
     * Removes the demo routines that version 1.0.0 wrote on first launch, so upgraded
     * installs start with an empty routine list just like fresh installs. Routines the
     * user created use UUID ids and are never matched by this query.
     */
    suspend fun purgeSeededRoutines() {
        val removed = routineDao.deleteSeeded()
        if (removed > 0) {
            logDao.deleteRoutineLogsForSeeded()
            notifyWidget()
        }
    }

    suspend fun seedIfNeeded() {
        if (routineDao.count() > 0 || tagDao.count() > 0) {
            settingsStore.setSeeded(true)
            return
        }
        val now = System.currentTimeMillis()

        val tags = listOf(
            "Health" to Palette.TEAL,
            "Work" to Palette.INDIGO,
            "Learning" to Palette.AMBER,
            "Personal" to Palette.ROSE,
        )
        tags.forEachIndexed { index, (name, color) ->
            tagDao.upsert(TagEntity("tag-$index", name, color, index))
        }

        settingsStore.setSeeded(true)
    }
}
