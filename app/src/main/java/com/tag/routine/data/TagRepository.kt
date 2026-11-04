package com.tag.routine.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject
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
    val loaded: Boolean = false,
)

/** How many rows a backup restore wrote. */
data class RestoreResult(
    val routines: Int,
    val tasks: Int,
    val tags: Int,
)

/** Outcome of a backup import, surfaced to the user in a dialog. */
data class ImportOutcome(
    val ok: Boolean,
    val routines: Int = 0,
    val tasks: Int = 0,
    val tags: Int = 0,
    val error: String? = null,
)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key)

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key)) null else optLong(key)

private fun JSONObject.optStringOrNull(key: String): String? {
    if (isNull(key)) return null
    val value = optString(key)
    return value.ifBlank { null }
}

/** Non-blank string value, or null when the key is missing/blank. */
private fun JSONObject.stringOrNull(key: String): String? = optStringOrNull(key)

/** The named array as a list of objects, or an empty list when absent. */
private fun JSONObject.array(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

class TagRepository(
    private val db: TagDatabase,
    private val settingsStore: SettingsStore,
    scope: CoroutineScope,
) {
    private val routineDao = db.routines()
    private val taskDao = db.tasks()
    private val tagDao = db.tags()
    private val logDao = db.logs()
    private val focusDao = db.focus()

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
            snap.copy(focusMinutes = (millis / 60_000L).toInt())
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
    }

    suspend fun saveRoutine(item: RoutineEntity) = routineDao.upsert(item)

    suspend fun deleteRoutine(routineId: String) {
        logDao.deleteRoutineLogsFor(routineId)
        routineDao.delete(routineId)
    }

    suspend fun newRoutineOrder(): Int = routineDao.maxOrder() + 1

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
    }

    suspend fun routine(id: String): RoutineEntity? = routineDao.byId(id)

    // ------------------------------------------------------------ tasks

    suspend fun saveTask(item: TaskEntity) = taskDao.upsert(item)

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
    }

    suspend fun deleteTask(taskId: String) {
        logDao.deleteTaskLogsFor(taskId)
        taskDao.delete(taskId)
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

    suspend fun setLanguage(tag: String) = settingsStore.setLanguage(tag)

    suspend fun setUserName(name: String) = settingsStore.setUserName(name)

    suspend fun setNotificationsEnabled(enabled: Boolean) = settingsStore.setNotifications(enabled)

    suspend fun setDigestEnabled(enabled: Boolean) = settingsStore.setDigestEnabled(enabled)

    suspend fun setDigestMinutes(minutes: Int) = settingsStore.setDigestMinutes(minutes)

    suspend fun setHaptics(enabled: Boolean) = settingsStore.setHaptics(enabled)

    suspend fun markSeeded() = settingsStore.setSeeded(true)

    // ------------------------------------------------------------ focus timer

    /** Records one focus run so it can show up in the insights screen. */
    suspend fun recordFocusSession(
        routineId: String?,
        startedAt: Long,
        endedAt: Long,
        plannedMinutes: Int,
        completed: Boolean,
    ) {
        focusDao.upsert(
            FocusSessionEntity(
                id = java.util.UUID.randomUUID().toString(),
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
    }

    /** Serialises the current snapshot to JSON for backup/export. */
    fun exportJson(snapshot: Snapshot): String {
        val root = JSONObject()
        root.put("app", "Tag")
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())

        val routines = JSONArray()
        snapshot.routines.forEach { r ->
            routines.put(
                JSONObject()
                    .put("id", r.id).put("title", r.title).put("group", r.groupName)
                    .put("icon", r.icon).put("timeMinutes", r.timeMinutes ?: JSONObject.NULL)
                    .put("daysMask", r.daysMask).put("reminder", r.reminder)
                    .put("colorHex", r.colorHex).put("archived", r.archived)
                    .put("sortOrder", r.sortOrder).put("createdAt", r.createdAt),
            )
        }
        root.put("routines", routines)

        val tasks = JSONArray()
        snapshot.tasks.forEach { t ->
            tasks.put(
                JSONObject()
                    .put("id", t.id).put("title", t.title).put("note", t.note)
                    .put("tagId", t.tagId ?: JSONObject.NULL).put("priority", t.priority)
                    .put("dueDate", t.dueDate ?: JSONObject.NULL).put("done", t.done)
                    .put("dueTimeMinutes", t.dueTimeMinutes ?: JSONObject.NULL)
                    .put("remindAt", t.remindAt ?: JSONObject.NULL)
                    .put("doneAt", t.doneAt ?: JSONObject.NULL)
                    .put("createdAt", t.createdAt),
            )
        }
        root.put("tasks", tasks)

        val tags = JSONArray()
        snapshot.tags.forEach { t ->
            tags.put(
                JSONObject()
                    .put("id", t.id).put("name", t.name)
                    .put("colorHex", t.colorHex).put("sortOrder", t.sortOrder),
            )
        }
        root.put("tags", tags)

        val logs = JSONArray()
        snapshot.routineLogs.forEach { l ->
            logs.put(
                JSONObject()
                    .put("routineId", l.routineId).put("date", l.date).put("doneAt", l.doneAt),
            )
        }
        root.put("routineLogs", logs)

        val taskLogs = JSONArray()
        snapshot.taskLogs.forEach { l ->
            taskLogs.put(
                JSONObject()
                    .put("taskId", l.taskId).put("date", l.date).put("doneAt", l.doneAt),
            )
        }
        root.put("taskLogs", taskLogs)

        return root.toString(2)
    }

    /**
     * Restores a backup produced by [exportJson]. Existing rows with the same id are
     * overwritten, so importing the same file twice is safe.
     *
     * @return how many routines, tasks and tags were written.
     */
    suspend fun importJson(raw: String): RestoreResult {
        val root = JSONObject(raw)

        var routineCount = 0
        root.array("routines").forEachIndexed { i, o ->
            val id = o.stringOrNull("id") ?: return@forEachIndexed
            routineDao.upsert(
                RoutineEntity(
                    id = id,
                    title = o.optString("title"),
                    groupName = o.optString("group", Groups.MORNING),
                    icon = o.optString("icon", "spark"),
                    timeMinutes = o.optIntOrNull("timeMinutes"),
                    daysMask = o.optInt("daysMask", Dates.EVERY_DAY),
                    reminder = o.optBoolean("reminder", false),
                    colorHex = o.optLong("colorHex", Palette.at(0)),
                    sortOrder = o.optInt("sortOrder", i),
                    archived = o.optBoolean("archived", false),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                ),
            )
            routineCount++
        }

        var tagCount = 0
        root.array("tags").forEachIndexed { i, o ->
            val id = o.stringOrNull("id") ?: return@forEachIndexed
            tagDao.upsert(
                TagEntity(
                    id = id,
                    name = o.optString("name"),
                    colorHex = o.optLong("colorHex", Palette.at(i)),
                    sortOrder = o.optInt("sortOrder", i),
                ),
            )
            tagCount++
        }

        var taskCount = 0
        root.array("tasks").forEachIndexed { i, o ->
            val id = o.stringOrNull("id") ?: return@forEachIndexed
            taskDao.upsert(
                TaskEntity(
                    id = id,
                    title = o.optString("title"),
                    note = o.optString("note"),
                    tagId = o.optStringOrNull("tagId"),
                    priority = o.optInt("priority", Priorities.NORMAL),
                    dueDate = o.optStringOrNull("dueDate"),
                    dueTimeMinutes = o.optIntOrNull("dueTimeMinutes"),
                    remindAt = o.optLongOrNull("remindAt"),
                    done = o.optBoolean("done", false),
                    doneAt = o.optLongOrNull("doneAt"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                ),
            )
            taskCount++
        }

        root.array("routineLogs").forEach { o ->
            val routineId = o.stringOrNull("routineId") ?: return@forEach
            val date = o.stringOrNull("date") ?: return@forEach
            logDao.insertRoutineLog(
                RoutineLogEntity(routineId, date, o.optLong("doneAt", System.currentTimeMillis())),
            )
        }

        root.array("taskLogs").forEach { o ->
            val taskId = o.stringOrNull("taskId") ?: return@forEach
            val date = o.stringOrNull("date") ?: return@forEach
            logDao.insertTaskLog(
                TaskLogEntity(taskId, date, o.optLong("doneAt", System.currentTimeMillis())),
            )
        }

        return RestoreResult(routineCount, taskCount, tagCount)
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
