package com.tag.routine.data

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class BackupMode {
    MERGE,
    REPLACE,
}

enum class BackupErrorCode {
    FILE_TOO_LARGE,
    EMPTY_FILE,
    INVALID_JSON,
    INVALID_FORMAT,
    UNSUPPORTED_VERSION,
    MISSING_REQUIRED_FIELD,
    INVALID_FIELD_TYPE,
    INVALID_DATE,
    DUPLICATE_ID,
    SAFETY_BACKUP_FAILED,
    IO_ERROR,
    RESTORE_FAILED,
}

class BackupValidationException(
    val code: BackupErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class BackupSettings(
    val themeMode: Int = AppSettings.ThemeMode.SYSTEM,
    val themePalette: String = AppSettings.Palettes.VIOLET,
    val dynamicColor: Boolean = false,
    val language: String = "",
    val calendarType: CalendarType = CalendarType.GREGORIAN,
    val userName: String = "",
    val notificationsEnabled: Boolean = true,
    val dailyDigestEnabled: Boolean = true,
    val dailyDigestMinutes: Int = 8 * 60,
    val hapticsEnabled: Boolean = true,
    val focusMinutes: Int = AppConstants.DEFAULT_FOCUS_MINUTES,
    val templatesCardDismissed: Boolean = false,
    val animationSetting: AnimationSetting = AnimationSetting.AUTO,
) {
    fun appliedTo(base: AppSettings): AppSettings = base.copy(
        themeMode = themeMode.coerceIn(0, 2),
        themePalette = validPalette(themePalette),
        dynamicColor = dynamicColor,
        language = validLanguage(language),
        calendarType = calendarType,
        userName = userName,
        notificationsEnabled = notificationsEnabled,
        dailyDigestEnabled = dailyDigestEnabled,
        dailyDigestMinutes = dailyDigestMinutes.coerceIn(0, 1439),
        hapticsEnabled = hapticsEnabled,
        focusMinutes = AppConstants.clampFocusMinutes(focusMinutes),
        templatesCardDismissed = templatesCardDismissed,
        animationSetting = animationSetting,
        onboarded = true,
    )

    companion object {
        private val VALID_PALETTES = setOf(
            AppSettings.Palettes.VIOLET,
            AppSettings.Palettes.SKY,
            AppSettings.Palettes.PURPLE,
            AppSettings.Palettes.PINK,
            AppSettings.Palettes.TEAL,
            AppSettings.Palettes.SUNSET,
        )
        private val VALID_LANGUAGES = setOf("", "en", "fa", "fr", "de", "tr")

        fun validPalette(value: String): String =
            if (value in VALID_PALETTES) value else AppSettings.Palettes.VIOLET

        fun validLanguage(value: String): String =
            if (value in VALID_LANGUAGES) value else ""

        fun fromAppSettings(settings: AppSettings): BackupSettings = BackupSettings(
            themeMode = settings.themeMode,
            themePalette = settings.themePalette,
            dynamicColor = settings.dynamicColor,
            language = settings.language,
            calendarType = settings.calendarType,
            userName = settings.userName,
            notificationsEnabled = settings.notificationsEnabled,
            dailyDigestEnabled = settings.dailyDigestEnabled,
            dailyDigestMinutes = settings.dailyDigestMinutes,
            hapticsEnabled = settings.hapticsEnabled,
            focusMinutes = settings.focusMinutes,
            templatesCardDismissed = settings.templatesCardDismissed,
            animationSetting = settings.animationSetting,
        )
    }
}

data class BackupPayload(
    val formatVersion: Int,
    val appVersion: String? = null,
    val appVersionCode: Int? = null,
    val exportedAt: Long? = null,
    val routines: List<RoutineEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val routineLogs: List<RoutineLogEntity> = emptyList(),
    val taskLogs: List<TaskLogEntity> = emptyList(),
    val focusSessions: List<FocusSessionEntity> = emptyList(),
    val settings: BackupSettings? = null,
)

data class BackupPreview(
    val formatVersion: Int,
    val appVersion: String?,
    val exportedAt: Long?,
    val routineCount: Int,
    val taskCount: Int,
    val tagCount: Int,
    val focusSessionCount: Int,
    val routineLogCount: Int,
    val taskLogCount: Int,
    val dateRangeStart: LocalDate?,
    val dateRangeEnd: LocalDate?,
    val hasSettings: Boolean,
)

data class MergePlan(
    val tagsToInsert: List<TagEntity>,
    val routinesToInsert: List<RoutineEntity>,
    val tasksToInsert: List<TaskEntity>,
    val routineLogsToInsert: List<RoutineLogEntity>,
    val taskLogsToInsert: List<TaskLogEntity>,
    val focusSessionsToInsert: List<FocusSessionEntity>,
    val skippedDuplicates: Int,
)

data class BackupRestoreReport(
    val mode: BackupMode,
    val routinesAdded: Int,
    val tasksAdded: Int,
    val tagsAdded: Int,
    val focusSessionsAdded: Int,
    val routineLogsAdded: Int,
    val taskLogsAdded: Int,
    val skippedDuplicates: Int,
    val settingsRestored: Boolean,
    val safetyBackupPath: String,
    val removedRoutineIds: List<String>,
)

/**
 * Streaming JSON backup serializer, parser, strict validator, and preview builder.
 *
 * Format v3 writes explicit `formatVersion`, `appVersion`, `appVersionCode`, `exportedAt`,
 * all 6 Room tables (`routines`, `tasks`, `tags`, `routineLogs`, `taskLogs`, `focusSessions`),
 * and user preferences (`settings`). Legacy backups created before v1.6 (missing `formatVersion`,
 * `focusSessions`, `settings`, or v4 routine fields) are also accepted and upgraded in-memory.
 */
object BackupCodec {
    const val APP_NAME = "Tag"
    const val CURRENT_FORMAT_VERSION = 3
    val SUPPORTED_FORMAT_VERSIONS = setOf(1, 2, 3)
    val SUPPORTED_LEGACY_VERSIONS = setOf(1, 2)

    const val MAX_BACKUP_BYTES = 20L * 1024L * 1024L
    const val MAX_BACKUP_CHARS = 20_000_000L

    private const val MIN_EPOCH_DAY = -100_000L
    private const val MAX_EPOCH_DAY = 200_000L

    private val STRICT_ISO_DATE: DateTimeFormatter = DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_LOCAL_DATE)
        .toFormatter(Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    fun encodeToString(
        snapshot: Snapshot,
        appVersion: String = "1.6",
        appVersionCode: Int = 16,
        exportedAt: Long = System.currentTimeMillis(),
    ): String {
        val sw = StringWriter(4096)
        writeBackup(sw, snapshot, appVersion, appVersionCode, exportedAt)
        return sw.toString()
    }

    fun writeBackup(
        writer: Writer,
        snapshot: Snapshot,
        appVersion: String,
        appVersionCode: Int,
        exportedAt: Long = System.currentTimeMillis(),
    ) {
        val out = if (writer is BufferedWriter) writer else BufferedWriter(writer)
        val json = JsonStreamWriter(out)
        json.beginObject()
        json.name("app").value(APP_NAME)
        json.name("formatVersion").value(CURRENT_FORMAT_VERSION)
        json.name("appVersion").value(appVersion)
        json.name("appVersionCode").value(appVersionCode)
        json.name("exportedAt").value(exportedAt)

        json.name("routines").beginArray()
        snapshot.routines.forEach { r ->
            json.beginObject()
            json.name("id").value(r.id)
            json.name("title").value(r.title)
            json.name("group").value(r.groupName)
            json.name("icon").value(r.icon)
            json.name("timeMinutes").value(r.timeMinutes)
            json.name("daysMask").value(r.daysMask)
            json.name("reminder").value(r.reminder)
            json.name("colorHex").value(r.colorHex)
            json.name("colorId").value(r.colorId)
            json.name("archived").value(r.archived)
            json.name("sortOrder").value(r.sortOrder)
            json.name("createdAt").value(r.createdAt)
            json.name("dateEpochDay").value(r.dateEpochDay)
            json.name("repeatDaily").value(r.repeatDaily)
            json.name("endDateEpochDay").value(r.endDateEpochDay)
            json.name("excludedDates").value(r.excludedDates)
            json.endObject()
        }
        json.endArray()

        json.name("tasks").beginArray()
        snapshot.tasks.forEach { t ->
            json.beginObject()
            json.name("id").value(t.id)
            json.name("title").value(t.title)
            json.name("note").value(t.note)
            json.name("tagId").value(t.tagId)
            json.name("priority").value(t.priority)
            json.name("dueDate").value(t.dueDate)
            json.name("done").value(t.done)
            json.name("dueTimeMinutes").value(t.dueTimeMinutes)
            json.name("remindAt").value(t.remindAt)
            json.name("doneAt").value(t.doneAt)
            json.name("createdAt").value(t.createdAt)
            json.endObject()
        }
        json.endArray()

        json.name("tags").beginArray()
        snapshot.tags.forEach { t ->
            json.beginObject()
            json.name("id").value(t.id)
            json.name("name").value(t.name)
            json.name("colorHex").value(t.colorHex)
            json.name("sortOrder").value(t.sortOrder)
            json.endObject()
        }
        json.endArray()

        json.name("routineLogs").beginArray()
        snapshot.routineLogs.forEach { l ->
            json.beginObject()
            json.name("routineId").value(l.routineId)
            json.name("date").value(l.date)
            json.name("doneAt").value(l.doneAt)
            json.endObject()
        }
        json.endArray()

        json.name("taskLogs").beginArray()
        snapshot.taskLogs.forEach { l ->
            json.beginObject()
            json.name("taskId").value(l.taskId)
            json.name("date").value(l.date)
            json.name("doneAt").value(l.doneAt)
            json.endObject()
        }
        json.endArray()

        json.name("focusSessions").beginArray()
        snapshot.focusSessions.forEach { s ->
            json.beginObject()
            json.name("id").value(s.id)
            json.name("routineId").value(s.routineId)
            json.name("startedAt").value(s.startedAt)
            json.name("endedAt").value(s.endedAt)
            json.name("plannedMinutes").value(s.plannedMinutes)
            json.name("completed").value(s.completed)
            json.endObject()
        }
        json.endArray()

        val s = BackupSettings.fromAppSettings(snapshot.settings)
        json.name("settings").beginObject()
        json.name("themeMode").value(s.themeMode)
        json.name("themePalette").value(s.themePalette)
        json.name("dynamicColor").value(s.dynamicColor)
        json.name("language").value(s.language)
        json.name("calendarType").value(s.calendarType.key)
        json.name("userName").value(s.userName)
        json.name("notificationsEnabled").value(s.notificationsEnabled)
        json.name("dailyDigestEnabled").value(s.dailyDigestEnabled)
        json.name("dailyDigestMinutes").value(s.dailyDigestMinutes)
        json.name("hapticsEnabled").value(s.hapticsEnabled)
        json.name("focusMinutes").value(s.focusMinutes)
        json.name("templatesCardDismissed").value(s.templatesCardDismissed)
        json.name("animationSetting").value(s.animationSetting.key)
        json.endObject()

        json.endObject()
        json.flush()
    }

    fun readAndValidate(stream: InputStream, declaredLength: Long = -1L): BackupPayload {
        if (declaredLength > MAX_BACKUP_BYTES) {
            throw BackupValidationException(
                BackupErrorCode.FILE_TOO_LARGE,
                "Backup file exceeds maximum supported size.",
            )
        }
        val bounded = BoundedInputStream(BufferedInputStream(stream), MAX_BACKUP_BYTES)
        val decoder = StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            BufferedReader(InputStreamReader(bounded, decoder)).use { reader ->
                parseAndValidate(reader)
            }
        } catch (e: BackupValidationException) {
            throw e
        } catch (e: IOException) {
            throw BackupValidationException(BackupErrorCode.INVALID_JSON, "Could not read backup stream", e)
        }
    }

    fun parseAndValidate(raw: String): BackupPayload =
        parseAndValidate(StringReader(raw))

    fun parseAndValidate(reader: Reader): BackupPayload {
        val json = JsonStreamReader(reader)
        if (json.isEmptyInput()) {
            throw BackupValidationException(BackupErrorCode.EMPTY_FILE, "Backup file is empty")
        }
        if (json.peekType() != JsonValueType.OBJECT) {
            throw BackupValidationException(BackupErrorCode.INVALID_FORMAT, "Backup root must be a JSON object")
        }

        var appName: String? = null
        var hasAppField = false
        var formatVersion: Int? = null
        var hasFormatVersionField = false
        var legacyVersion: Int? = null
        var hasLegacyVersionField = false
        var appVersion: String? = null
        var appVersionCode: Int? = null
        var exportedAt: Long? = null
        var hasDataArray = false

        val parsedRoutines = mutableListOf<ParsedRoutine>()
        val routineIds = HashSet<String>()
        val parsedTasks = mutableListOf<ParsedTask>()
        val taskIds = HashSet<String>()
        val parsedTags = mutableListOf<ParsedTag>()
        val tagIds = HashSet<String>()
        val parsedRoutineLogs = mutableListOf<ParsedRoutineLog>()
        val routineLogKeys = HashSet<Pair<String, String>>()
        val parsedTaskLogs = mutableListOf<ParsedTaskLog>()
        val taskLogKeys = HashSet<Pair<String, String>>()
        val parsedFocusSessions = mutableListOf<FocusSessionEntity>()
        val focusIds = HashSet<String>()
        var parsedSettings: BackupSettings? = null

        json.readObject { key ->
            when (key) {
                "app" -> {
                    hasAppField = true
                    appName = json.readStringOrNull()
                }
                "formatVersion" -> {
                    hasFormatVersionField = true
                    if (json.peekType() != JsonValueType.NUMBER) {
                        throw BackupValidationException(
                            BackupErrorCode.UNSUPPORTED_VERSION,
                            "Invalid formatVersion type",
                        )
                    }
                    val v = try {
                        json.readInt()
                    } catch (e: BackupValidationException) {
                        throw BackupValidationException(
                            BackupErrorCode.UNSUPPORTED_VERSION,
                            "Unsupported formatVersion",
                            e,
                        )
                    }
                    if (v !in SUPPORTED_FORMAT_VERSIONS) {
                        throw BackupValidationException(
                            BackupErrorCode.UNSUPPORTED_VERSION,
                            "Unsupported backup formatVersion: $v",
                        )
                    }
                    formatVersion = v
                }
                "version" -> {
                    hasLegacyVersionField = true
                    if (json.peekType() != JsonValueType.NUMBER) {
                        throw BackupValidationException(
                            BackupErrorCode.UNSUPPORTED_VERSION,
                            "Invalid legacy version type",
                        )
                    }
                    val v = try {
                        json.readInt()
                    } catch (e: BackupValidationException) {
                        throw BackupValidationException(
                            BackupErrorCode.UNSUPPORTED_VERSION,
                            "Unsupported legacy version",
                            e,
                        )
                    }
                    legacyVersion = v
                }
                "appVersion" -> appVersion = json.readStringOrNull()
                "appVersionCode" -> appVersionCode = json.readIntOrNull()
                "exportedAt" -> {
                    val value = json.readLongOrNull()
                    if (value != null && value < 0L) {
                        throw BackupValidationException(BackupErrorCode.INVALID_DATE, "Negative exportedAt")
                    }
                    exportedAt = value
                }
                "routines" -> {
                    hasDataArray = true
                    json.readArray { idx ->
                        val parsed = parseRoutine(json, idx)
                        if (!routineIds.add(parsed.entity.id)) {
                            throw BackupValidationException(
                                BackupErrorCode.DUPLICATE_ID,
                                "Duplicate routine id: ${parsed.entity.id}",
                            )
                        }
                        parsedRoutines.add(parsed)
                    }
                }
                "tasks" -> {
                    hasDataArray = true
                    json.readArray {
                        val parsed = parseTask(json)
                        if (!taskIds.add(parsed.entity.id)) {
                            throw BackupValidationException(
                                BackupErrorCode.DUPLICATE_ID,
                                "Duplicate task id: ${parsed.entity.id}",
                            )
                        }
                        parsedTasks.add(parsed)
                    }
                }
                "tags" -> {
                    hasDataArray = true
                    json.readArray { idx ->
                        val parsed = parseTag(json, idx)
                        if (!tagIds.add(parsed.entity.id)) {
                            throw BackupValidationException(
                                BackupErrorCode.DUPLICATE_ID,
                                "Duplicate tag id: ${parsed.entity.id}",
                            )
                        }
                        parsedTags.add(parsed)
                    }
                }
                "routineLogs" -> {
                    hasDataArray = true
                    json.readArray {
                        val parsed = parseRoutineLog(json)
                        if (!routineLogKeys.add(parsed.entity.routineId to parsed.entity.date)) {
                            throw BackupValidationException(
                                BackupErrorCode.DUPLICATE_ID,
                                "Duplicate routine log: ${parsed.entity.routineId}@${parsed.entity.date}",
                            )
                        }
                        parsedRoutineLogs.add(parsed)
                    }
                }
                "taskLogs" -> {
                    hasDataArray = true
                    json.readArray {
                        val parsed = parseTaskLog(json)
                        if (!taskLogKeys.add(parsed.entity.taskId to parsed.entity.date)) {
                            throw BackupValidationException(
                                BackupErrorCode.DUPLICATE_ID,
                                "Duplicate task log: ${parsed.entity.taskId}@${parsed.entity.date}",
                            )
                        }
                        parsedTaskLogs.add(parsed)
                    }
                }
                "focusSessions" -> {
                    hasDataArray = true
                    json.readArray {
                        val parsed = parseFocusSession(json)
                        if (!focusIds.add(parsed.id)) {
                            throw BackupValidationException(
                                BackupErrorCode.DUPLICATE_ID,
                                "Duplicate focus session id: ${parsed.id}",
                            )
                        }
                        parsedFocusSessions.add(parsed)
                    }
                }
                "settings" -> {
                    parsedSettings = when (json.peekType()) {
                        JsonValueType.NULL -> {
                            json.readNull()
                            null
                        }
                        JsonValueType.OBJECT -> parseSettings(json)
                        else -> throw BackupValidationException(
                            BackupErrorCode.INVALID_FIELD_TYPE,
                            "settings must be a JSON object",
                        )
                    }
                }
                else -> json.skipValue()
            }
        }
        json.ensureEof()

        if (hasAppField && appName != APP_NAME) {
            throw BackupValidationException(BackupErrorCode.INVALID_FORMAT, "Backup app mismatch: $appName")
        }

        val resolvedFormatVersion = if (hasFormatVersionField) {
            formatVersion!!
        } else {
            if (hasLegacyVersionField && legacyVersion !in SUPPORTED_LEGACY_VERSIONS) {
                throw BackupValidationException(
                    BackupErrorCode.UNSUPPORTED_VERSION,
                    "Unsupported legacy backup version: $legacyVersion",
                )
            }
            val recognizedLegacy = (appName == APP_NAME) || (legacyVersion != null) || hasDataArray
            if (!recognizedLegacy) {
                throw BackupValidationException(BackupErrorCode.INVALID_FORMAT, "Not a Tag backup file")
            }
            legacyVersion ?: 2
        }

        val strictV3 = resolvedFormatVersion >= CURRENT_FORMAT_VERSION
        if (strictV3) {
            if (exportedAt == null) {
                throw BackupValidationException(
                    BackupErrorCode.MISSING_REQUIRED_FIELD,
                    "Missing exportedAt in format v3 backup",
                )
            }
            if (!hasDataArray) {
                throw BackupValidationException(
                    BackupErrorCode.MISSING_REQUIRED_FIELD,
                    "Backup contains no data sections",
                )
            }
            parsedRoutines.forEach { it.requireV3Fields() }
            parsedTasks.forEach { it.requireV3Fields() }
            parsedTags.forEach { it.requireV3Fields() }
            parsedRoutineLogs.forEach { it.requireV3Fields() }
            parsedTaskLogs.forEach { it.requireV3Fields() }
        }

        return BackupPayload(
            formatVersion = resolvedFormatVersion,
            appVersion = appVersion,
            appVersionCode = appVersionCode,
            exportedAt = exportedAt,
            routines = parsedRoutines.map { it.entity },
            tasks = parsedTasks.map { it.entity },
            tags = parsedTags.map { it.entity },
            routineLogs = parsedRoutineLogs.map { it.entity },
            taskLogs = parsedTaskLogs.map { it.entity },
            focusSessions = parsedFocusSessions,
            settings = parsedSettings,
        )
    }

    fun buildPreview(
        payload: BackupPayload,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BackupPreview {
        val dates = mutableListOf<LocalDate>()
        payload.routineLogs.forEach { l -> parseIsoDateStrict(l.date).let(dates::add) }
        payload.taskLogs.forEach { l -> parseIsoDateStrict(l.date).let(dates::add) }
        payload.tasks.forEach { t -> t.dueDate?.let { dates.add(parseIsoDateStrict(it)) } }
        payload.routines.forEach { r ->
            if (r.dateEpochDay > 0L) dates.add(LocalDate.ofEpochDay(r.dateEpochDay))
            r.endDateEpochDay?.takeIf { it > 0L }?.let { dates.add(LocalDate.ofEpochDay(it)) }
        }
        payload.focusSessions.forEach { s ->
            dates.add(Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate())
        }

        val minDate = dates.minOrNull()
        val maxDate = dates.maxOrNull()

        return BackupPreview(
            formatVersion = payload.formatVersion,
            appVersion = payload.appVersion,
            exportedAt = payload.exportedAt,
            routineCount = payload.routines.size,
            taskCount = payload.tasks.size,
            tagCount = payload.tags.size,
            focusSessionCount = payload.focusSessions.size,
            routineLogCount = payload.routineLogs.size,
            taskLogCount = payload.taskLogs.size,
            dateRangeStart = minDate,
            dateRangeEnd = maxDate,
            hasSettings = payload.settings != null,
        )
    }

    private data class ParsedRoutine(
        val entity: RoutineEntity,
        val hasGroup: Boolean,
        val hasDaysMask: Boolean,
        val hasReminder: Boolean,
        val hasColorHex: Boolean,
        val hasArchived: Boolean,
        val hasSortOrder: Boolean,
        val hasCreatedAt: Boolean,
        val hasDateEpochDay: Boolean,
        val hasRepeatDaily: Boolean,
    ) {
        fun requireV3Fields() {
            if (!hasGroup || !hasDaysMask || !hasReminder || !hasColorHex ||
                !hasArchived || !hasSortOrder || !hasCreatedAt || !hasDateEpochDay || !hasRepeatDaily
            ) {
                throw BackupValidationException(
                    BackupErrorCode.MISSING_REQUIRED_FIELD,
                    "Routine ${entity.id} is missing required fields",
                )
            }
        }
    }

    private data class ParsedTask(
        val entity: TaskEntity,
        val hasPriority: Boolean,
        val hasDone: Boolean,
        val hasCreatedAt: Boolean,
    ) {
        fun requireV3Fields() {
            if (!hasPriority || !hasDone || !hasCreatedAt) {
                throw BackupValidationException(
                    BackupErrorCode.MISSING_REQUIRED_FIELD,
                    "Task ${entity.id} is missing required fields",
                )
            }
        }
    }

    private data class ParsedTag(
        val entity: TagEntity,
        val hasColorHex: Boolean,
        val hasSortOrder: Boolean,
    ) {
        fun requireV3Fields() {
            if (!hasColorHex || !hasSortOrder) {
                throw BackupValidationException(
                    BackupErrorCode.MISSING_REQUIRED_FIELD,
                    "Tag ${entity.id} is missing required fields",
                )
            }
        }
    }

    private data class ParsedRoutineLog(
        val entity: RoutineLogEntity,
        val hasDoneAt: Boolean,
    ) {
        fun requireV3Fields() {
            if (!hasDoneAt) {
                throw BackupValidationException(
                    BackupErrorCode.MISSING_REQUIRED_FIELD,
                    "Routine log ${entity.routineId} is missing doneAt",
                )
            }
        }
    }

    private data class ParsedTaskLog(
        val entity: TaskLogEntity,
        val hasDoneAt: Boolean,
    ) {
        fun requireV3Fields() {
            if (!hasDoneAt) {
                throw BackupValidationException(
                    BackupErrorCode.MISSING_REQUIRED_FIELD,
                    "Task log ${entity.taskId} is missing doneAt",
                )
            }
        }
    }

    private fun parseRoutine(json: JsonStreamReader, index: Int): ParsedRoutine {
        var id: String? = null
        var title: String? = null
        var groupName: String? = null
        var icon: String = RoutineEmojis.DEFAULT
        var timeMinutes: Int? = null
        var daysMask: Int? = null
        var reminder: Boolean? = null
        var colorHex: Long? = null
        var colorId: Int = 0
        var archived: Boolean? = null
        var sortOrder: Int? = null
        var createdAt: Long? = null
        var dateEpochDay: Long? = null
        var repeatDaily: Boolean? = null
        var endDateEpochDay: Long? = null
        var excludedDates: String = ""

        json.readObject { key ->
            when (key) {
                "id" -> id = json.readString()
                "title" -> title = json.readString()
                "group" -> groupName = json.readString()
                "icon" -> icon = json.readStringOrNull()?.ifBlank { RoutineEmojis.DEFAULT } ?: RoutineEmojis.DEFAULT
                "timeMinutes" -> {
                    val v = json.readIntOrNull()
                    if (v != null && v !in 0..1439) {
                        throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Invalid timeMinutes: $v")
                    }
                    timeMinutes = v
                }
                "daysMask" -> {
                    val v = json.readInt()
                    if (v !in 0..Dates.EVERY_DAY) {
                        throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Invalid daysMask: $v")
                    }
                    daysMask = v
                }
                "reminder" -> reminder = json.readBoolean()
                "colorHex" -> colorHex = json.readLong()
                "colorId" -> colorId = json.readInt()
                "archived" -> archived = json.readBoolean()
                "sortOrder" -> sortOrder = json.readInt()
                "createdAt" -> {
                    val v = json.readLong()
                    if (v < 0L) {
                        throw BackupValidationException(BackupErrorCode.INVALID_DATE, "Negative createdAt")
                    }
                    createdAt = v
                }
                "dateEpochDay" -> {
                    val v = json.readLong()
                    validateEpochDay(v)
                    dateEpochDay = v
                }
                "repeatDaily" -> repeatDaily = json.readBoolean()
                "endDateEpochDay" -> {
                    val v = json.readLongOrNull()
                    if (v != null) validateEpochDay(v)
                    endDateEpochDay = v
                }
                "excludedDates" -> {
                    val raw = json.readStringOrNull().orEmpty()
                    if (raw.isNotBlank()) {
                        raw.split(',').forEach { part ->
                            parseIsoDateStrict(part.trim())
                        }
                    }
                    excludedDates = raw
                }
                else -> json.skipValue()
            }
        }

        val validId = requireNonBlank(id, "routine.id")
        val validTitle = requireNonBlank(title, "routine.title")

        val entity = RoutineEntity(
            id = validId,
            title = validTitle,
            groupName = groupName?.ifBlank { Groups.MORNING } ?: Groups.MORNING,
            icon = icon,
            timeMinutes = timeMinutes,
            daysMask = daysMask ?: Dates.EVERY_DAY,
            reminder = reminder ?: false,
            colorHex = colorHex ?: Palette.at(index),
            colorId = colorId,
            sortOrder = sortOrder ?: index,
            archived = archived ?: false,
            createdAt = createdAt ?: 0L,
            dateEpochDay = dateEpochDay ?: 0L,
            repeatDaily = repeatDaily ?: true,
            endDateEpochDay = endDateEpochDay,
            excludedDates = excludedDates,
        )
        return ParsedRoutine(
            entity = entity,
            hasGroup = groupName != null,
            hasDaysMask = daysMask != null,
            hasReminder = reminder != null,
            hasColorHex = colorHex != null,
            hasArchived = archived != null,
            hasSortOrder = sortOrder != null,
            hasCreatedAt = createdAt != null,
            hasDateEpochDay = dateEpochDay != null,
            hasRepeatDaily = repeatDaily != null,
        )
    }

    private fun parseTask(json: JsonStreamReader): ParsedTask {
        var id: String? = null
        var title: String? = null
        var note: String = ""
        var tagId: String? = null
        var priority: Int? = null
        var dueDate: String? = null
        var dueTimeMinutes: Int? = null
        var remindAt: Long? = null
        var done: Boolean? = null
        var doneAt: Long? = null
        var createdAt: Long? = null

        json.readObject { key ->
            when (key) {
                "id" -> id = json.readString()
                "title" -> title = json.readString()
                "note" -> note = json.readStringOrNull().orEmpty()
                "tagId" -> tagId = json.readStringOrNull()?.ifBlank { null }
                "priority" -> {
                    val v = json.readInt()
                    if (v !in Priorities.LOW..Priorities.HIGH) {
                        throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Invalid priority: $v")
                    }
                    priority = v
                }
                "dueDate" -> {
                    val raw = json.readStringOrNull()?.trim()?.ifBlank { null }
                    if (raw != null) parseIsoDateStrict(raw)
                    dueDate = raw
                }
                "dueTimeMinutes" -> {
                    val v = json.readIntOrNull()
                    if (v != null && v !in 0..1439) {
                        throw BackupValidationException(
                            BackupErrorCode.INVALID_FIELD_TYPE,
                            "Invalid dueTimeMinutes: $v",
                        )
                    }
                    dueTimeMinutes = v
                }
                "remindAt" -> {
                    val v = json.readLongOrNull()
                    if (v != null && v < 0L) {
                        throw BackupValidationException(BackupErrorCode.INVALID_DATE, "Negative remindAt")
                    }
                    remindAt = v
                }
                "done" -> done = json.readBoolean()
                "doneAt" -> {
                    val v = json.readLongOrNull()
                    if (v != null && v < 0L) {
                        throw BackupValidationException(BackupErrorCode.INVALID_DATE, "Negative doneAt")
                    }
                    doneAt = v
                }
                "createdAt" -> {
                    val v = json.readLong()
                    if (v < 0L) {
                        throw BackupValidationException(BackupErrorCode.INVALID_DATE, "Negative createdAt")
                    }
                    createdAt = v
                }
                else -> json.skipValue()
            }
        }

        val validId = requireNonBlank(id, "task.id")
        val validTitle = requireNonBlank(title, "task.title")

        val entity = TaskEntity(
            id = validId,
            title = validTitle,
            note = note,
            tagId = tagId,
            priority = priority ?: Priorities.NORMAL,
            dueDate = dueDate,
            dueTimeMinutes = dueTimeMinutes,
            remindAt = remindAt,
            done = done ?: false,
            doneAt = doneAt,
            createdAt = createdAt ?: 0L,
        )
        return ParsedTask(
            entity = entity,
            hasPriority = priority != null,
            hasDone = done != null,
            hasCreatedAt = createdAt != null,
        )
    }

    private fun parseTag(json: JsonStreamReader, index: Int): ParsedTag {
        var id: String? = null
        var name: String? = null
        var colorHex: Long? = null
        var sortOrder: Int? = null

        json.readObject { key ->
            when (key) {
                "id" -> id = json.readString()
                "name" -> name = json.readString()
                "colorHex" -> colorHex = json.readLong()
                "sortOrder" -> sortOrder = json.readInt()
                else -> json.skipValue()
            }
        }

        val validId = requireNonBlank(id, "tag.id")
        val validName = requireNonBlank(name, "tag.name")
        return ParsedTag(
            entity = TagEntity(
                id = validId,
                name = validName,
                colorHex = colorHex ?: Palette.at(index),
                sortOrder = sortOrder ?: index,
            ),
            hasColorHex = colorHex != null,
            hasSortOrder = sortOrder != null,
        )
    }

    private fun parseRoutineLog(json: JsonStreamReader): ParsedRoutineLog {
        var routineId: String? = null
        var date: String? = null
        var doneAt: Long? = null

        json.readObject { key ->
            when (key) {
                "routineId" -> routineId = json.readString()
                "date" -> date = json.readString()
                "doneAt" -> {
                    val v = json.readLong()
                    if (v < 0L) {
                        throw BackupValidationException(BackupErrorCode.INVALID_DATE, "Negative doneAt")
                    }
                    doneAt = v
                }
                else -> json.skipValue()
            }
        }

        val validRoutineId = requireNonBlank(routineId, "routineLog.routineId")
        val rawDate = requireNonBlank(date, "routineLog.date")
        parseIsoDateStrict(rawDate)

        return ParsedRoutineLog(
            entity = RoutineLogEntity(validRoutineId, rawDate, doneAt ?: 0L),
            hasDoneAt = doneAt != null,
        )
    }

    private fun parseTaskLog(json: JsonStreamReader): ParsedTaskLog {
        var taskId: String? = null
        var date: String? = null
        var doneAt: Long? = null

        json.readObject { key ->
            when (key) {
                "taskId" -> taskId = json.readString()
                "date" -> date = json.readString()
                "doneAt" -> {
                    val v = json.readLong()
                    if (v < 0L) {
                        throw BackupValidationException(BackupErrorCode.INVALID_DATE, "Negative doneAt")
                    }
                    doneAt = v
                }
                else -> json.skipValue()
            }
        }

        val validTaskId = requireNonBlank(taskId, "taskLog.taskId")
        val rawDate = requireNonBlank(date, "taskLog.date")
        parseIsoDateStrict(rawDate)

        return ParsedTaskLog(
            entity = TaskLogEntity(validTaskId, rawDate, doneAt ?: 0L),
            hasDoneAt = doneAt != null,
        )
    }

    private fun parseFocusSession(json: JsonStreamReader): FocusSessionEntity {
        var id: String? = null
        var routineId: String? = null
        var startedAt: Long? = null
        var endedAt: Long? = null
        var plannedMinutes: Int? = null
        var completed: Boolean? = null

        json.readObject { key ->
            when (key) {
                "id" -> id = json.readString()
                "routineId" -> routineId = json.readStringOrNull()?.ifBlank { null }
                "startedAt" -> {
                    val v = json.readLong()
                    if (v < 0L) {
                        throw BackupValidationException(BackupErrorCode.INVALID_DATE, "Negative startedAt")
                    }
                    startedAt = v
                }
                "endedAt" -> {
                    val v = json.readLong()
                    if (v < 0L) {
                        throw BackupValidationException(BackupErrorCode.INVALID_DATE, "Negative endedAt")
                    }
                    endedAt = v
                }
                "plannedMinutes" -> {
                    val v = json.readInt()
                    if (v !in 1..1440) {
                        throw BackupValidationException(
                            BackupErrorCode.INVALID_FIELD_TYPE,
                            "Invalid plannedMinutes: $v",
                        )
                    }
                    plannedMinutes = v
                }
                "completed" -> completed = json.readBoolean()
                else -> json.skipValue()
            }
        }

        val validId = requireNonBlank(id, "focusSession.id")
        val validStart = startedAt ?: throw BackupValidationException(
            BackupErrorCode.MISSING_REQUIRED_FIELD,
            "focusSession.startedAt missing",
        )
        val validEnd = endedAt ?: throw BackupValidationException(
            BackupErrorCode.MISSING_REQUIRED_FIELD,
            "focusSession.endedAt missing",
        )
        if (validEnd < validStart) {
            throw BackupValidationException(BackupErrorCode.INVALID_DATE, "focusSession.endedAt < startedAt")
        }
        val validPlanned = plannedMinutes ?: throw BackupValidationException(
            BackupErrorCode.MISSING_REQUIRED_FIELD,
            "focusSession.plannedMinutes missing",
        )
        val validCompleted = completed ?: throw BackupValidationException(
            BackupErrorCode.MISSING_REQUIRED_FIELD,
            "focusSession.completed missing",
        )

        return FocusSessionEntity(
            id = validId,
            routineId = routineId,
            startedAt = validStart,
            endedAt = validEnd,
            plannedMinutes = validPlanned,
            completed = validCompleted,
        )
    }

    private fun parseSettings(json: JsonStreamReader): BackupSettings {
        var themeMode = AppSettings.ThemeMode.SYSTEM
        var themePalette = AppSettings.Palettes.VIOLET
        var dynamicColor = false
        var language = ""
        var calendarType = CalendarType.GREGORIAN
        var userName = ""
        var notificationsEnabled = true
        var dailyDigestEnabled = true
        var dailyDigestMinutes = 8 * 60
        var hapticsEnabled = true
        var focusMinutes = AppConstants.DEFAULT_FOCUS_MINUTES
        var templatesCardDismissed = false
        var animationSetting = AnimationSetting.AUTO

        json.readObject { key ->
            when (key) {
                "themeMode" -> {
                    val v = json.readInt()
                    if (v !in 0..2) {
                        throw BackupValidationException(BackupErrorCode.INVALID_FIELD_TYPE, "Invalid themeMode: $v")
                    }
                    themeMode = v
                }
                "themePalette" -> themePalette = BackupSettings.validPalette(json.readString())
                "dynamicColor" -> dynamicColor = json.readBoolean()
                "language" -> language = BackupSettings.validLanguage(json.readString())
                "calendarType" -> {
                    val raw = json.readString()
                    if (raw != CalendarType.GREGORIAN.key && raw != CalendarType.JALALI.key) {
                        throw BackupValidationException(
                            BackupErrorCode.INVALID_FIELD_TYPE,
                            "Invalid calendarType: $raw",
                        )
                    }
                    calendarType = CalendarType.fromKey(raw)
                }
                "userName" -> userName = json.readStringOrNull().orEmpty()
                "notificationsEnabled" -> notificationsEnabled = json.readBoolean()
                "dailyDigestEnabled" -> dailyDigestEnabled = json.readBoolean()
                "dailyDigestMinutes" -> {
                    val v = json.readInt()
                    if (v !in 0..1439) {
                        throw BackupValidationException(
                            BackupErrorCode.INVALID_FIELD_TYPE,
                            "Invalid dailyDigestMinutes: $v",
                        )
                    }
                    dailyDigestMinutes = v
                }
                "hapticsEnabled" -> hapticsEnabled = json.readBoolean()
                "focusMinutes" -> {
                    val v = json.readInt()
                    if (v !in AppConstants.MIN_FOCUS_MINUTES..AppConstants.MAX_FOCUS_MINUTES) {
                        throw BackupValidationException(
                            BackupErrorCode.INVALID_FIELD_TYPE,
                            "Invalid focusMinutes: $v",
                        )
                    }
                    focusMinutes = v
                }
                "templatesCardDismissed" -> templatesCardDismissed = json.readBoolean()
                "animationSetting" -> animationSetting = AnimationSetting.fromKey(json.readStringOrNull())
                else -> json.skipValue()
            }
        }

        return BackupSettings(
            themeMode = themeMode,
            themePalette = themePalette,
            dynamicColor = dynamicColor,
            language = language,
            calendarType = calendarType,
            userName = userName,
            notificationsEnabled = notificationsEnabled,
            dailyDigestEnabled = dailyDigestEnabled,
            dailyDigestMinutes = dailyDigestMinutes,
            hapticsEnabled = hapticsEnabled,
            focusMinutes = focusMinutes,
            templatesCardDismissed = templatesCardDismissed,
            animationSetting = animationSetting,
        )
    }

    private fun requireNonBlank(value: String?, field: String): String {
        if (value == null || value.isBlank()) {
            throw BackupValidationException(
                BackupErrorCode.MISSING_REQUIRED_FIELD,
                "Missing or blank required field: $field",
            )
        }
        return value
    }

    fun parseIsoDateStrict(raw: String): LocalDate {
        if (raw.length != 10) {
            throw BackupValidationException(BackupErrorCode.INVALID_DATE, "Invalid ISO date: $raw")
        }
        return try {
            LocalDate.parse(raw, STRICT_ISO_DATE)
        } catch (e: Exception) {
            throw BackupValidationException(BackupErrorCode.INVALID_DATE, "Invalid ISO date: $raw", e)
        }
    }

    private fun validateEpochDay(day: Long) {
        if (day !in MIN_EPOCH_DAY..MAX_EPOCH_DAY || runCatching { LocalDate.ofEpochDay(day) }.isFailure) {
            throw BackupValidationException(BackupErrorCode.INVALID_DATE, "Invalid epochDay: $day")
        }
    }

    private class BoundedInputStream(
        input: InputStream,
        private val maxBytes: Long,
    ) : FilterInputStream(input) {
        private var bytesRead = 0L

        override fun read(): Int {
            val b = super.read()
            if (b != -1) {
                bytesRead++
                checkLimit()
            }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) {
                bytesRead += n
                checkLimit()
            }
            return n
        }

        private fun checkLimit() {
            if (bytesRead > maxBytes) {
                throw BackupValidationException(
                    BackupErrorCode.FILE_TOO_LARGE,
                    "Backup file exceeds ${maxBytes / (1024 * 1024)} MB limit",
                )
            }
        }
    }
}

/**
 * Deterministic deduplication planner for [BackupMode.MERGE].
 *
 * Existing items are never deleted or overwritten; duplicates are detected by ID or by a
 * fixed logical key per entity type, and dependent records (`routineLogs`, `taskLogs`,
 * `tasks.tagId`, `focusSessions.routineId`) are remapped to matching existing IDs.
 */
object BackupRestorePlanner {

    private data class RoutineKey(
        val title: String,
        val repeatDaily: Boolean,
        val dateEpochDay: Long,
        val timeMinutes: Int?,
        val daysMask: Int,
    )

    private data class TaskKey(
        val title: String,
        val dueDate: String,
        val dueTimeMinutes: Int?,
    )

    private data class FocusKey(
        val routineId: String?,
        val startedAt: Long,
        val endedAt: Long,
        val plannedMinutes: Int,
    )

    fun planMerge(existing: Snapshot, backup: BackupPayload): MergePlan {
        var skipped = 0

        // 1. Tags: match by id or trimmed lowercase name
        val tagById = existing.tags.associateByTo(HashMap()) { it.id }
        val tagByName = existing.tags.associateByTo(HashMap()) { normalize(it.name) }
        val tagIdMap = HashMap<String, String>()
        val tagsToInsert = mutableListOf<TagEntity>()

        for (tag in backup.tags) {
            val match = tagById[tag.id] ?: tagByName[normalize(tag.name)]
            if (match != null) {
                tagIdMap[tag.id] = match.id
                skipped++
            } else {
                tagsToInsert.add(tag)
                tagById[tag.id] = tag
                tagByName[normalize(tag.name)] = tag
                tagIdMap[tag.id] = tag.id
            }
        }

        // 2. Routines: match by id or (title + dateEpochDay + timeMinutes + repeatDaily + daysMask)
        val routineById = existing.routines.associateByTo(HashMap()) { it.id }
        val routineByKey = existing.routines.associateByTo(HashMap()) { routineKey(it) }
        val routineIdMap = HashMap<String, String>()
        val routinesToInsert = mutableListOf<RoutineEntity>()

        for (routine in backup.routines) {
            val key = routineKey(routine)
            val match = routineById[routine.id] ?: routineByKey[key]
            if (match != null) {
                routineIdMap[routine.id] = match.id
                skipped++
            } else {
                routinesToInsert.add(routine)
                routineById[routine.id] = routine
                routineByKey[key] = routine
                routineIdMap[routine.id] = routine.id
            }
        }

        // 3. Tasks: match by id or (title + dueDate + dueTimeMinutes)
        val taskById = existing.tasks.associateByTo(HashMap()) { it.id }
        val taskByKey = existing.tasks.associateByTo(HashMap()) { taskKey(it) }
        val taskIdMap = HashMap<String, String>()
        val tasksToInsert = mutableListOf<TaskEntity>()

        for (task in backup.tasks) {
            val key = taskKey(task)
            val match = taskById[task.id] ?: taskByKey[key]
            if (match != null) {
                taskIdMap[task.id] = match.id
                skipped++
            } else {
                val resolvedTagId = task.tagId?.let { orig ->
                    tagIdMap[orig] ?: orig.takeIf { it in tagById }
                }
                val prepared = task.copy(tagId = resolvedTagId)
                tasksToInsert.add(prepared)
                taskById[prepared.id] = prepared
                taskByKey[key] = prepared
                taskIdMap[task.id] = prepared.id
            }
        }

        // 4. Routine logs: remap routineId and match by (routineId, date)
        val routineLogKeys = existing.routineLogs.mapTo(HashSet()) { it.routineId to it.date }
        val routineLogsToInsert = mutableListOf<RoutineLogEntity>()
        for (log in backup.routineLogs) {
            val resolvedId = routineIdMap[log.routineId] ?: log.routineId
            val key = resolvedId to log.date
            if (routineLogKeys.add(key)) {
                routineLogsToInsert.add(log.copy(routineId = resolvedId))
            } else {
                skipped++
            }
        }

        // 5. Task logs: remap taskId and match by (taskId, date)
        val taskLogKeys = existing.taskLogs.mapTo(HashSet()) { it.taskId to it.date }
        val taskLogsToInsert = mutableListOf<TaskLogEntity>()
        for (log in backup.taskLogs) {
            val resolvedId = taskIdMap[log.taskId] ?: log.taskId
            val key = resolvedId to log.date
            if (taskLogKeys.add(key)) {
                taskLogsToInsert.add(log.copy(taskId = resolvedId))
            } else {
                skipped++
            }
        }

        // 6. Focus sessions: remap routineId and match by id or (routineId, startedAt, endedAt, plannedMinutes)
        val focusIds = existing.focusSessions.mapTo(HashSet()) { it.id }
        val focusKeys = existing.focusSessions.mapTo(HashSet()) { focusKey(it, it.routineId) }
        val focusSessionsToInsert = mutableListOf<FocusSessionEntity>()
        for (session in backup.focusSessions) {
            val resolvedRoutineId = session.routineId?.let { routineIdMap[it] ?: it }
            val key = focusKey(session, resolvedRoutineId)
            if (session.id !in focusIds && key !in focusKeys) {
                val prepared = session.copy(routineId = resolvedRoutineId)
                focusSessionsToInsert.add(prepared)
                focusIds.add(prepared.id)
                focusKeys.add(key)
            } else {
                skipped++
            }
        }

        return MergePlan(
            tagsToInsert = tagsToInsert,
            routinesToInsert = routinesToInsert,
            tasksToInsert = tasksToInsert,
            routineLogsToInsert = routineLogsToInsert,
            taskLogsToInsert = taskLogsToInsert,
            focusSessionsToInsert = focusSessionsToInsert,
            skippedDuplicates = skipped,
        )
    }

    private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)

    private fun routineKey(r: RoutineEntity): RoutineKey = RoutineKey(
        title = normalize(r.title),
        repeatDaily = r.repeatDaily,
        dateEpochDay = r.dateEpochDay,
        timeMinutes = r.timeMinutes,
        daysMask = if (r.repeatDaily) r.daysMask else 0,
    )

    private fun taskKey(t: TaskEntity): TaskKey = TaskKey(
        title = normalize(t.title),
        dueDate = t.dueDate?.trim().orEmpty(),
        dueTimeMinutes = t.dueTimeMinutes,
    )

    private fun focusKey(s: FocusSessionEntity, resolvedRoutineId: String?): FocusKey = FocusKey(
        routineId = resolvedRoutineId,
        startedAt = s.startedAt,
        endedAt = s.endedAt,
        plannedMinutes = s.plannedMinutes,
    )
}

/**
 * Stores automatic safety backups inside internal app storage (`filesDir/safety-backups`)
 * before any destructive or merge restore, keeping only the 3 most recent backups.
 */
object SafetyBackupStore {
    const val DIR_NAME = "safety-backups"
    const val MAX_BACKUPS = 3
    private const val PREFIX = "tag-safety-"
    private const val SUFFIX = ".json"

    private val sequence = AtomicLong(0L)
    private val fileStampFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd_HH-mm-ss-SSS", Locale.ROOT)

    fun save(
        dir: File,
        content: String,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): File {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Could not create safety backup directory: ${dir.absolutePath}")
        }
        val stamp = Instant.ofEpochMilli(nowMillis).atZone(zone).format(fileStampFormatter)
        val seq = sequence.incrementAndGet() % 10_000L
        val seqLabel = "%04d".format(Locale.ROOT, seq)
        val target = File(dir, "$PREFIX$stamp-$seqLabel$SUFFIX")
        val temp = File(dir, "$PREFIX$stamp-$seqLabel.tmp")

        temp.writeText(content, StandardCharsets.UTF_8)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        prune(dir, MAX_BACKUPS)
        return target
    }

    fun list(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file ->
            file.isFile && file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX)
        }?.sortedByDescending { it.name } ?: emptyList()
    }

    fun prune(dir: File, keep: Int = MAX_BACKUPS) {
        val files = list(dir)
        files.drop(keep).forEach { old -> old.delete() }
    }
}

/** Storage abstraction so restore transactions and rollback are testable on the pure JVM. */
interface BackupStorage {
    suspend fun readSnapshotFresh(): Snapshot
    suspend fun runInTransaction(block: suspend () -> Unit)
    suspend fun clearAllTables()
    suspend fun insertTags(items: List<TagEntity>)
    suspend fun insertRoutines(items: List<RoutineEntity>)
    suspend fun insertTasks(items: List<TaskEntity>)
    suspend fun insertRoutineLogs(items: List<RoutineLogEntity>)
    suspend fun insertTaskLogs(items: List<TaskLogEntity>)
    suspend fun insertFocusSessions(items: List<FocusSessionEntity>)
    suspend fun applySettings(settings: BackupSettings)
}

/**
 * Coordinates safe backup restoration:
 *  1. Snapshots live data and writes an automatic safety backup (keeping the last 3).
 *  2. Executes all database mutations inside a single atomic transaction.
 *  3. Rolls back database state if a post-DB settings write fails.
 */
class BackupCoordinator(
    private val storage: BackupStorage,
    private val safetyDir: File,
    private val appVersion: String = "1.6",
    private val appVersionCode: Int = 16,
) {
    suspend fun restore(
        payload: BackupPayload,
        mode: BackupMode,
        nowMillis: Long = System.currentTimeMillis(),
    ): BackupRestoreReport {
        val before = storage.readSnapshotFresh()
        val safetyFile = try {
            val json = BackupCodec.encodeToString(
                snapshot = before,
                appVersion = appVersion,
                appVersionCode = appVersionCode,
                exportedAt = nowMillis,
            )
            SafetyBackupStore.save(safetyDir, json, nowMillis)
        } catch (t: Throwable) {
            throw BackupValidationException(
                BackupErrorCode.SAFETY_BACKUP_FAILED,
                "Could not create safety backup",
                t,
            )
        }

        val previousRoutineIds = before.routines.map { it.id }

        return when (mode) {
            BackupMode.MERGE -> {
                val plan = BackupRestorePlanner.planMerge(before, payload)
                try {
                    storage.runInTransaction {
                        if (plan.tagsToInsert.isNotEmpty()) storage.insertTags(plan.tagsToInsert)
                        if (plan.routinesToInsert.isNotEmpty()) storage.insertRoutines(plan.routinesToInsert)
                        if (plan.tasksToInsert.isNotEmpty()) storage.insertTasks(plan.tasksToInsert)
                        if (plan.routineLogsToInsert.isNotEmpty()) storage.insertRoutineLogs(plan.routineLogsToInsert)
                        if (plan.taskLogsToInsert.isNotEmpty()) storage.insertTaskLogs(plan.taskLogsToInsert)
                        if (plan.focusSessionsToInsert.isNotEmpty()) storage.insertFocusSessions(plan.focusSessionsToInsert)
                    }
                } catch (e: BackupValidationException) {
                    throw e
                } catch (t: Throwable) {
                    throw BackupValidationException(BackupErrorCode.RESTORE_FAILED, "Merge restore failed", t)
                }

                BackupRestoreReport(
                    mode = BackupMode.MERGE,
                    routinesAdded = plan.routinesToInsert.size,
                    tasksAdded = plan.tasksToInsert.size,
                    tagsAdded = plan.tagsToInsert.size,
                    focusSessionsAdded = plan.focusSessionsToInsert.size,
                    routineLogsAdded = plan.routineLogsToInsert.size,
                    taskLogsAdded = plan.taskLogsToInsert.size,
                    skippedDuplicates = plan.skippedDuplicates,
                    settingsRestored = false,
                    safetyBackupPath = safetyFile.absolutePath,
                    removedRoutineIds = emptyList(),
                )
            }

            BackupMode.REPLACE -> {
                try {
                    storage.runInTransaction {
                        writeFullSnapshot(
                            tags = payload.tags,
                            routines = payload.routines,
                            tasks = payload.tasks,
                            routineLogs = payload.routineLogs,
                            taskLogs = payload.taskLogs,
                            focusSessions = payload.focusSessions,
                        )
                    }
                } catch (e: BackupValidationException) {
                    throw e
                } catch (t: Throwable) {
                    throw BackupValidationException(BackupErrorCode.RESTORE_FAILED, "Replace restore failed", t)
                }

                var settingsApplied = false
                if (payload.settings != null) {
                    try {
                        storage.applySettings(payload.settings)
                        settingsApplied = true
                    } catch (t: Throwable) {
                        // Roll back the committed DB state so DB and settings stay consistent.
                        runCatching {
                            storage.runInTransaction {
                                writeFullSnapshot(
                                    tags = before.tags,
                                    routines = before.routines,
                                    tasks = before.tasks,
                                    routineLogs = before.routineLogs,
                                    taskLogs = before.taskLogs,
                                    focusSessions = before.focusSessions,
                                )
                            }
                        }
                        throw BackupValidationException(
                            BackupErrorCode.RESTORE_FAILED,
                            "Settings restore failed; database rolled back",
                            t,
                        )
                    }
                }

                val newRoutineIds = payload.routines.mapTo(HashSet()) { it.id }
                val removedRoutineIds = previousRoutineIds.filter { it !in newRoutineIds }

                BackupRestoreReport(
                    mode = BackupMode.REPLACE,
                    routinesAdded = payload.routines.size,
                    tasksAdded = payload.tasks.size,
                    tagsAdded = payload.tags.size,
                    focusSessionsAdded = payload.focusSessions.size,
                    routineLogsAdded = payload.routineLogs.size,
                    taskLogsAdded = payload.taskLogs.size,
                    skippedDuplicates = 0,
                    settingsRestored = settingsApplied,
                    safetyBackupPath = safetyFile.absolutePath,
                    removedRoutineIds = removedRoutineIds,
                )
            }
        }
    }

    private suspend fun writeFullSnapshot(
        tags: List<TagEntity>,
        routines: List<RoutineEntity>,
        tasks: List<TaskEntity>,
        routineLogs: List<RoutineLogEntity>,
        taskLogs: List<TaskLogEntity>,
        focusSessions: List<FocusSessionEntity>,
    ) {
        storage.clearAllTables()
        if (tags.isNotEmpty()) storage.insertTags(tags)
        if (routines.isNotEmpty()) storage.insertRoutines(routines)
        if (tasks.isNotEmpty()) storage.insertTasks(tasks)
        if (routineLogs.isNotEmpty()) storage.insertRoutineLogs(routineLogs)
        if (taskLogs.isNotEmpty()) storage.insertTaskLogs(taskLogs)
        if (focusSessions.isNotEmpty()) storage.insertFocusSessions(focusSessions)
    }
}
