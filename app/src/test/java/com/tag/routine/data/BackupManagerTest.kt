package com.tag.routine.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId

class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ---------------------------------------------------------------- fixtures

    private fun sampleSnapshot(): Snapshot {
        val d1 = LocalDate.of(2026, 11, 18)
        val d2 = LocalDate.of(2026, 11, 20)
        val tags = listOf(
            TagEntity(id = "tag-1", name = "Work", colorHex = Palette.INDIGO, sortOrder = 0),
            TagEntity(id = "tag-2", name = "Health", colorHex = Palette.TEAL, sortOrder = 1),
        )
        val routines = listOf(
            RoutineEntity(
                id = "r-1",
                title = "Morning stretch",
                groupName = Groups.MORNING,
                icon = "🧘",
                timeMinutes = 7 * 60 + 15,
                daysMask = Dates.EVERY_DAY,
                reminder = true,
                colorHex = RoutineColors.hexFor(2, false),
                colorId = 2,
                sortOrder = 0,
                archived = false,
                createdAt = 1_760_000_000_000L,
                dateEpochDay = d1.toEpochDay(),
                repeatDaily = true,
                endDateEpochDay = d2.plusDays(10).toEpochDay(),
                excludedDates = "2026-11-19",
            ),
            RoutineEntity(
                id = "r-2",
                title = "Read paper",
                groupName = Groups.EVENING,
                icon = "📖",
                timeMinutes = null,
                daysMask = Dates.EVERY_DAY,
                reminder = false,
                colorHex = RoutineColors.hexFor(0, false),
                colorId = 0,
                sortOrder = 1,
                archived = false,
                createdAt = 1_760_000_010_000L,
                dateEpochDay = d2.toEpochDay(),
                repeatDaily = false,
                endDateEpochDay = null,
                excludedDates = "",
            ),
        )
        val tasks = listOf(
            TaskEntity(
                id = "t-1",
                title = "Prepare slides",
                note = "Include \"quotes\" and\nnewlines",
                tagId = "tag-1",
                priority = Priorities.HIGH,
                dueDate = "2026-11-20",
                dueTimeMinutes = 14 * 60,
                remindAt = 1_763_630_000_000L,
                done = true,
                doneAt = 1_763_635_000_000L,
                createdAt = 1_763_600_000_000L,
            ),
            TaskEntity(
                id = "t-2",
                title = "Buy water",
                note = "",
                tagId = null,
                priority = Priorities.LOW,
                dueDate = null,
                dueTimeMinutes = null,
                remindAt = null,
                done = false,
                doneAt = null,
                createdAt = 1_763_600_100_000L,
            ),
        )
        val routineLogs = listOf(
            RoutineLogEntity(routineId = "r-1", date = "2026-11-18", doneAt = 1_763_440_000_000L),
            RoutineLogEntity(routineId = "r-2", date = "2026-11-20", doneAt = 1_763_636_000_000L),
        )
        val taskLogs = listOf(
            TaskLogEntity(taskId = "t-1", date = "2026-11-20", doneAt = 1_763_635_000_000L),
        )
        val utc = ZoneId.of("UTC")
        val focus1Start = d2.atTime(9, 0).atZone(utc).toInstant().toEpochMilli()
        val focus2Start = d2.atTime(15, 0).atZone(utc).toInstant().toEpochMilli()
        val focusSessions = listOf(
            FocusSessionEntity(
                id = "fs-1",
                routineId = "r-1",
                startedAt = focus1Start,
                endedAt = focus1Start + 25 * 60_000L,
                plannedMinutes = 25,
                completed = true,
            ),
            FocusSessionEntity(
                id = "fs-2",
                routineId = null,
                startedAt = focus2Start,
                endedAt = focus2Start + 10 * 60_000L,
                plannedMinutes = 45,
                completed = false,
            ),
        )
        val settings = AppSettings(
            themeMode = AppSettings.ThemeMode.DARK,
            themePalette = AppSettings.Palettes.TEAL,
            dynamicColor = true,
            language = "fa",
            calendarType = CalendarType.JALALI,
            userName = "Taha",
            notificationsEnabled = false,
            dailyDigestEnabled = true,
            dailyDigestMinutes = 9 * 60 + 30,
            hapticsEnabled = false,
            onboarded = true,
            focusMinutes = 45,
            breakModeEnabled = true,
            shortBreakMinutes = 7,
            longBreakMinutes = 20,
            longBreakInterval = 3,
            autoStartBreak = false,
            templatesCardDismissed = true,
            animationSetting = AnimationSetting.REDUCED,
        )
        return Snapshot(
            routines = routines,
            tasks = tasks,
            tags = tags,
            routineLogs = routineLogs,
            taskLogs = taskLogs,
            settings = settings,
            focusMinutes = 25,
            focusSessions = focusSessions,
            loaded = true,
        )
    }

    private class FakeBackupStorage(
        initial: Snapshot,
        var activeFocusRun: ActiveFocusRun? = null,
    ) : BackupStorage {
        var current: Snapshot = initial
        var failInsideTransactionOnCall: Int? = null
        var failApplySettings: Boolean = false
        var failClearActiveFocusRun: Boolean = false
        private var txCalls = 0

        private var txSnapshot: Snapshot? = null

        override suspend fun readSnapshotFresh(): Snapshot = current

        override suspend fun runInTransaction(block: suspend () -> Unit) {
            txCalls++
            val before = current
            txSnapshot = before
            try {
                if (failInsideTransactionOnCall == txCalls) {
                    // Simulate partial write before crash to verify rollback.
                    clearAllTables()
                    throw IllegalStateException("Simulated disk I/O error mid-transaction")
                }
                block()
                current = txSnapshot!!
            } catch (t: Throwable) {
                current = before
                throw t
            } finally {
                txSnapshot = null
            }
        }

        private fun active(): Snapshot = txSnapshot ?: current
        private fun update(transform: (Snapshot) -> Snapshot) {
            if (txSnapshot != null) {
                txSnapshot = transform(txSnapshot!!)
            } else {
                current = transform(current)
            }
        }

        override suspend fun clearAllTables() {
            update {
                it.copy(
                    routines = emptyList(),
                    tasks = emptyList(),
                    tags = emptyList(),
                    routineLogs = emptyList(),
                    taskLogs = emptyList(),
                    focusSessions = emptyList(),
                    focusMinutes = 0,
                )
            }
        }

        override suspend fun insertTags(items: List<TagEntity>) {
            update { snap ->
                val byId = snap.tags.associateByTo(LinkedHashMap()) { it.id }
                items.forEach { byId[it.id] = it }
                snap.copy(tags = byId.values.toList())
            }
        }

        override suspend fun insertRoutines(items: List<RoutineEntity>) {
            update { snap ->
                val byId = snap.routines.associateByTo(LinkedHashMap()) { it.id }
                items.forEach { byId[it.id] = it }
                snap.copy(routines = byId.values.toList())
            }
        }

        override suspend fun insertTasks(items: List<TaskEntity>) {
            update { snap ->
                val byId = snap.tasks.associateByTo(LinkedHashMap()) { it.id }
                items.forEach { byId[it.id] = it }
                snap.copy(tasks = byId.values.toList())
            }
        }

        override suspend fun insertRoutineLogs(items: List<RoutineLogEntity>) {
            update { snap ->
                val byKey = snap.routineLogs.associateByTo(LinkedHashMap()) { it.routineId to it.date }
                items.forEach { byKey[it.routineId to it.date] = it }
                snap.copy(routineLogs = byKey.values.toList())
            }
        }

        override suspend fun insertTaskLogs(items: List<TaskLogEntity>) {
            update { snap ->
                val byKey = snap.taskLogs.associateByTo(LinkedHashMap()) { it.taskId to it.date }
                items.forEach { byKey[it.taskId to it.date] = it }
                snap.copy(taskLogs = byKey.values.toList())
            }
        }

        override suspend fun insertFocusSessions(items: List<FocusSessionEntity>) {
            update { snap ->
                val byId = snap.focusSessions.associateByTo(LinkedHashMap()) { it.id }
                items.forEach { byId[it.id] = it }
                val merged = byId.values.toList()
                val mins = merged.filter { it.completed }.sumOf { it.endedAt - it.startedAt } / 60_000L
                snap.copy(focusSessions = merged, focusMinutes = mins.toInt())
            }
        }

        override suspend fun applySettings(settings: BackupSettings) {
            if (failApplySettings) {
                throw IllegalStateException("Simulated DataStore write failure")
            }
            update { snap -> snap.copy(settings = settings.appliedTo(snap.settings)) }
        }

        override suspend fun readActiveFocusRun(): ActiveFocusRun? = activeFocusRun

        override suspend fun clearActiveFocusRun() {
            if (failClearActiveFocusRun) {
                throw IllegalStateException("Simulated focus runtime clear failure")
            }
            activeFocusRun = null
        }

        override suspend fun restoreActiveFocusRun(run: ActiveFocusRun) {
            activeFocusRun = run
        }
    }

    private fun assertValidationFails(expected: BackupErrorCode, block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected BackupValidationException($expected) but succeeded")
        } catch (e: BackupValidationException) {
            assertEquals("Unexpected error code for: ${e.message}", expected, e.code)
        }
    }

    // ---------------------------------------------------------------- validation tests

    @Test
    fun `rejects empty, corrupt, non-object, and non-tag JSON`() {
        assertValidationFails(BackupErrorCode.EMPTY_FILE) {
            BackupCodec.parseAndValidate("   \n\t ")
        }
        assertValidationFails(BackupErrorCode.INVALID_JSON) {
            BackupCodec.parseAndValidate("{bad json")
        }
        assertValidationFails(BackupErrorCode.INVALID_JSON) {
            BackupCodec.parseAndValidate("""{"app":"Tag","formatVersion":3,"exportedAt":10,"routines":[]} trailing""")
        }
        assertValidationFails(BackupErrorCode.INVALID_FORMAT) {
            BackupCodec.parseAndValidate("[1, 2, 3]")
        }
        assertValidationFails(BackupErrorCode.INVALID_FORMAT) {
            BackupCodec.parseAndValidate("""{"hello": "world"}""")
        }
        assertValidationFails(BackupErrorCode.INVALID_FORMAT) {
            BackupCodec.parseAndValidate("""{"app":"OtherApp","formatVersion":3,"exportedAt":10,"routines":[]}""")
        }
        // Invalid UTF-8 binary stream
        assertValidationFails(BackupErrorCode.INVALID_JSON) {
            val badBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x80.toByte(), 0x00)
            BackupCodec.readAndValidate(ByteArrayInputStream(badBytes))
        }
    }

    @Test
    fun `rejects unknown formatVersion and unknown legacy version`() {
        assertValidationFails(BackupErrorCode.UNSUPPORTED_VERSION) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":99,"exportedAt":1000,"routines":[]}""",
            )
        }
        assertValidationFails(BackupErrorCode.UNSUPPORTED_VERSION) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":0,"exportedAt":1000,"routines":[]}""",
            )
        }
        assertValidationFails(BackupErrorCode.UNSUPPORTED_VERSION) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":"3","exportedAt":1000,"routines":[]}""",
            )
        }
        assertValidationFails(BackupErrorCode.UNSUPPORTED_VERSION) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","version":7,"exportedAt":1000,"routines":[]}""",
            )
        }
    }

    @Test
    fun `rejects missing required fields and invalid field types`() {
        // Missing exportedAt in formatVersion 3
        assertValidationFails(BackupErrorCode.MISSING_REQUIRED_FIELD) {
            BackupCodec.parseAndValidate("""{"app":"Tag","formatVersion":3,"routines":[]}""")
        }
        // Routine missing title
        assertValidationFails(BackupErrorCode.MISSING_REQUIRED_FIELD) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":3,"exportedAt":100,"routines":[{"id":"r1","group":"Morning","daysMask":127,"reminder":false,"colorHex":1,"archived":false,"sortOrder":0,"createdAt":0,"dateEpochDay":0,"repeatDaily":true}]}""",
            )
        }
        // Routine missing daysMask in v3
        assertValidationFails(BackupErrorCode.MISSING_REQUIRED_FIELD) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":3,"exportedAt":100,"routines":[{"id":"r1","title":"Walk","group":"Morning","reminder":false,"colorHex":1,"archived":false,"sortOrder":0,"createdAt":0,"dateEpochDay":0,"repeatDaily":true}]}""",
            )
        }
        // Wrong field type (timeMinutes as string, out-of-range priority, non-array routines)
        assertValidationFails(BackupErrorCode.INVALID_FIELD_TYPE) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":3,"exportedAt":100,"routines":[{"id":"r1","title":"Walk","group":"Morning","timeMinutes":"07:00","daysMask":127,"reminder":false,"colorHex":1,"archived":false,"sortOrder":0,"createdAt":0,"dateEpochDay":0,"repeatDaily":true}]}""",
            )
        }
        assertValidationFails(BackupErrorCode.INVALID_FIELD_TYPE) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":3,"exportedAt":100,"tasks":[{"id":"t1","title":"Pay","priority":5,"done":false,"createdAt":0}]}""",
            )
        }
        assertValidationFails(BackupErrorCode.INVALID_FIELD_TYPE) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":3,"exportedAt":100,"routines":{}}""",
            )
        }
    }

    @Test
    fun `rejects invalid dates and duplicate IDs`() {
        // Non-existent calendar date Feb 30
        assertValidationFails(BackupErrorCode.INVALID_DATE) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":3,"exportedAt":100,"routines":[],"routineLogs":[{"routineId":"r1","date":"2026-02-30","doneAt":100}]}""",
            )
        }
        // Invalid excludedDates entry
        assertValidationFails(BackupErrorCode.INVALID_DATE) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":3,"exportedAt":100,"routines":[{"id":"r1","title":"Walk","group":"Morning","daysMask":127,"reminder":false,"colorHex":1,"archived":false,"sortOrder":0,"createdAt":0,"dateEpochDay":0,"repeatDaily":true,"excludedDates":"2026-02-29"}]}""",
            )
        }
        // Focus session ended before started
        assertValidationFails(BackupErrorCode.INVALID_DATE) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":3,"exportedAt":100,"focusSessions":[{"id":"f1","routineId":null,"startedAt":500,"endedAt":100,"plannedMinutes":25,"completed":true}]}""",
            )
        }
        // Duplicate routine id
        assertValidationFails(BackupErrorCode.DUPLICATE_ID) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":3,"exportedAt":100,"routines":[
                    {"id":"dup","title":"A","group":"Morning","daysMask":127,"reminder":false,"colorHex":1,"archived":false,"sortOrder":0,"createdAt":0,"dateEpochDay":0,"repeatDaily":true},
                    {"id":"dup","title":"B","group":"Morning","daysMask":127,"reminder":false,"colorHex":1,"archived":false,"sortOrder":1,"createdAt":0,"dateEpochDay":0,"repeatDaily":true}
                ]}""",
            )
        }
        // Duplicate routineLog (routineId, date)
        assertValidationFails(BackupErrorCode.DUPLICATE_ID) {
            BackupCodec.parseAndValidate(
                """{"app":"Tag","formatVersion":3,"exportedAt":100,"routines":[],"routineLogs":[
                    {"routineId":"r1","date":"2026-11-20","doneAt":10},
                    {"routineId":"r1","date":"2026-11-20","doneAt":20}
                ]}""",
            )
        }
    }

    @Test
    fun `rejects oversized declared or streamed files without crashing`() {
        assertValidationFails(BackupErrorCode.FILE_TOO_LARGE) {
            BackupCodec.readAndValidate(
                ByteArrayInputStream("{}".toByteArray()),
                declaredLength = BackupCodec.MAX_BACKUP_BYTES + 1,
            )
        }
        // Stream larger than bounded limit without declaredLength
        val hugeStream = object : InputStream() {
            private var emitted = 0L
            private val header = """{"app":"Tag","formatVersion":3,"exportedAt":100,"routines":[""".toByteArray()
            override fun read(): Int {
                val b = if (emitted < header.size) header[emitted.toInt()].toInt() and 0xFF else ' '.code
                emitted++
                return b
            }
        }
        assertValidationFails(BackupErrorCode.FILE_TOO_LARGE) {
            BackupCodec.readAndValidate(hugeStream, declaredLength = -1L)
        }
        // Deeply nested JSON bomb
        val deep = "[".repeat(64) + "]".repeat(64)
        assertValidationFails(BackupErrorCode.INVALID_FORMAT) {
            BackupCodec.parseAndValidate("""{"app":"Tag","formatVersion":3,"exportedAt":100,"extra":$deep,"routines":[]}""")
        }
    }

    // ---------------------------------------------------------------- backward compatibility

    @Test
    fun `reads legacy v2 backup without formatVersion, focusSessions, or settings`() {
        val legacyJson = """
            {
              "app": "Tag",
              "version": 2,
              "exportedAt": 1763500000000,
              "routines": [
                {
                  "id": "old-r1",
                  "title": "Drink water",
                  "group": "Morning",
                  "icon": "water",
                  "timeMinutes": 420,
                  "daysMask": 127,
                  "reminder": true,
                  "colorHex": 4286344447,
                  "archived": false,
                  "sortOrder": 0,
                  "createdAt": 1760000000000
                }
              ],
              "tasks": [
                {
                  "id": "old-t1",
                  "title": "Call mom",
                  "note": "",
                  "tagId": null,
                  "priority": 1,
                  "dueDate": "2026-11-10",
                  "done": false,
                  "dueTimeMinutes": null,
                  "remindAt": null,
                  "doneAt": null,
                  "createdAt": 1760000000000
                }
              ],
              "tags": [
                { "id": "old-tag", "name": "Personal", "colorHex": 4294929309, "sortOrder": 0 }
              ],
              "routineLogs": [
                { "routineId": "old-r1", "date": "2026-11-09", "doneAt": 1762670000000 }
              ],
              "taskLogs": []
            }
        """.trimIndent()

        val payload = BackupCodec.parseAndValidate(legacyJson)
        assertEquals(2, payload.formatVersion)
        assertEquals(1, payload.routines.size)
        assertTrue(payload.routines[0].repeatDaily)
        assertEquals(0L, payload.routines[0].dateEpochDay)
        assertEquals(0, payload.routines[0].colorId)
        assertEquals(1, payload.tasks.size)
        assertEquals(1, payload.tags.size)
        assertEquals(1, payload.routineLogs.size)
        assertTrue(payload.focusSessions.isEmpty())
        assertNull(payload.settings)

        val preview = BackupCodec.buildPreview(payload, ZoneId.of("UTC"))
        assertEquals(1, preview.routineCount)
        assertEquals(1, preview.taskCount)
        assertEquals(0, preview.focusSessionCount)
        assertEquals(LocalDate.of(2026, 11, 9), preview.dateRangeStart)
        assertEquals(LocalDate.of(2026, 11, 10), preview.dateRangeEnd)
        assertFalse(preview.hasSettings)
    }

    @Test
    fun `reads legacy v1 backup without version, exportedAt, or v4 fields`() {
        val legacyV1 = """
            {
              "app": "Tag",
              "routines": [
                {
                  "id": "v1-r1",
                  "title": "Meditate",
                  "group": "Morning",
                  "daysMask": 127
                }
              ],
              "tasks": [],
              "tags": []
            }
        """.trimIndent()

        val payload = BackupCodec.parseAndValidate(legacyV1)
        assertEquals(2, payload.formatVersion)
        assertNull(payload.exportedAt)
        assertEquals(1, payload.routines.size)
        assertTrue(payload.routines[0].repeatDaily)
        assertEquals(0L, payload.routines[0].dateEpochDay)

        val preview = BackupCodec.buildPreview(payload, ZoneId.of("UTC"))
        assertNull(preview.exportedAt)
        assertNull(preview.dateRangeStart)
        assertNull(preview.dateRangeEnd)
    }

    @Test
    fun `reads v1_9 formatVersion 3 settings missing animationSetting and defaults to AUTO`() {
        val v19Json = """
            {
              "app": "Tag",
              "formatVersion": 3,
              "appVersion": "1.9",
              "appVersionCode": 19,
              "exportedAt": 1763640000000,
              "routines": [],
              "tasks": [],
              "tags": [],
              "routineLogs": [],
              "taskLogs": [],
              "focusSessions": [],
              "settings": {
                "themeMode": 2,
                "themePalette": "teal",
                "dynamicColor": true,
                "language": "fa",
                "calendarType": "jalali",
                "userName": "Taha",
                "notificationsEnabled": true,
                "dailyDigestEnabled": false,
                "dailyDigestMinutes": 540,
                "hapticsEnabled": false,
                "focusMinutes": 30,
                "templatesCardDismissed": true
              }
            }
        """.trimIndent()

        val payload = BackupCodec.parseAndValidate(v19Json)
        val settings = payload.settings
        assertNotNull(settings)
        assertEquals(AnimationSetting.AUTO, settings!!.animationSetting)
        assertEquals("teal", settings.themePalette)
        assertEquals(CalendarType.JALALI, settings.calendarType)
        assertFalse(settings.hapticsEnabled)
        // Break fields did not exist in v1.9; they must fall back to safe defaults.
        assertFalse(settings.breakModeEnabled)
        assertEquals(AppConstants.DEFAULT_SHORT_BREAK_MINUTES, settings.shortBreakMinutes)
        assertEquals(AppConstants.DEFAULT_LONG_BREAK_MINUTES, settings.longBreakMinutes)
        assertEquals(AppConstants.DEFAULT_LONG_BREAK_INTERVAL, settings.longBreakInterval)
        assertTrue(settings.autoStartBreak)
    }

    // ---------------------------------------------------------------- round-trip

    @Test
    fun `export then validate then replace restore round-trips every table and setting`() = runBlocking {
        val original = sampleSnapshot()
        val json = BackupCodec.encodeToString(original, appVersion = "1.6", appVersionCode = 16, exportedAt = 1_763_640_000_000L)

        val payload = BackupCodec.parseAndValidate(json)
        assertEquals(BackupCodec.CURRENT_FORMAT_VERSION, payload.formatVersion)
        assertEquals("1.6", payload.appVersion)
        assertEquals(16, payload.appVersionCode)
        assertEquals(1_763_640_000_000L, payload.exportedAt)

        val preview = BackupCodec.buildPreview(payload, ZoneId.of("UTC"))
        assertEquals(2, preview.routineCount)
        assertEquals(2, preview.taskCount)
        assertEquals(2, preview.tagCount)
        assertEquals(2, preview.focusSessionCount)
        assertEquals(LocalDate.of(2026, 11, 18), preview.dateRangeStart)
        assertEquals(LocalDate.of(2026, 11, 30), preview.dateRangeEnd)
        assertTrue(preview.hasSettings)

        val storage = FakeBackupStorage(Snapshot(loaded = true))
        val coordinator = BackupCoordinator(storage, tempFolder.newFolder("safety"))
        val report = coordinator.restore(payload, BackupMode.REPLACE, nowMillis = 1_763_650_000_000L)

        assertEquals(BackupMode.REPLACE, report.mode)
        assertTrue(report.settingsRestored)
        assertEquals(original.routines, storage.current.routines)
        assertEquals(original.tasks, storage.current.tasks)
        assertEquals(original.tags, storage.current.tags)
        assertEquals(original.routineLogs, storage.current.routineLogs)
        assertEquals(original.taskLogs, storage.current.taskLogs)
        assertEquals(original.focusSessions, storage.current.focusSessions)
        assertEquals(original.settings, storage.current.settings)
    }

    // ---------------------------------------------------------------- merge & replace behavior

    @Test
    fun `merge keeps existing data, skips duplicates by ID and logical key, and remaps dependents`() = runBlocking {
        val day = LocalDate.of(2026, 11, 20).toEpochDay()
        val existingSettings = AppSettings(
            themeMode = AppSettings.ThemeMode.LIGHT,
            language = "en",
            userName = "ExistingUser",
        )
        val existing = Snapshot(
            tags = listOf(TagEntity("tag-local", "Work", Palette.INDIGO, 0)),
            routines = listOf(
                RoutineEntity(
                    id = "r-local",
                    title = "Stretch",
                    groupName = Groups.MORNING,
                    icon = "🧘",
                    timeMinutes = 450,
                    daysMask = Dates.EVERY_DAY,
                    reminder = true,
                    colorHex = Palette.VIOLET,
                    colorId = 0,
                    sortOrder = 0,
                    archived = false,
                    createdAt = 100L,
                    dateEpochDay = day,
                    repeatDaily = false,
                ),
            ),
            tasks = listOf(
                TaskEntity(
                    id = "t-local",
                    title = "Submit report",
                    note = "Local note",
                    tagId = "tag-local",
                    priority = Priorities.HIGH,
                    dueDate = "2026-11-20",
                    dueTimeMinutes = 600,
                    remindAt = null,
                    done = false,
                    doneAt = null,
                    createdAt = 100L,
                ),
            ),
            routineLogs = listOf(RoutineLogEntity("r-local", "2026-11-20", 1000L)),
            focusSessions = listOf(FocusSessionEntity("f-local", "r-local", 5000L, 6000L, 25, true)),
            settings = existingSettings,
            loaded = true,
        )

        val backup = BackupPayload(
            formatVersion = 3,
            exportedAt = 9999L,
            tags = listOf(
                // Duplicate by normalized name with different id
                TagEntity("tag-backup", "  work ", Palette.TEAL, 3),
                TagEntity("tag-new", "Study", Palette.AMBER, 1),
            ),
            routines = listOf(
                // Duplicate by logical key (title + dateEpochDay + timeMinutes + repeatDaily)
                RoutineEntity(
                    id = "r-backup-dup",
                    title = " stretch ",
                    groupName = Groups.MORNING,
                    icon = "✨",
                    timeMinutes = 450,
                    daysMask = Dates.EVERY_DAY,
                    reminder = false,
                    colorHex = Palette.ROSE,
                    colorId = 3,
                    sortOrder = 5,
                    archived = false,
                    createdAt = 200L,
                    dateEpochDay = day,
                    repeatDaily = false,
                ),
                // New routine on a different day
                RoutineEntity(
                    id = "r-new",
                    title = "Stretch",
                    groupName = Groups.MORNING,
                    icon = "🧘",
                    timeMinutes = 450,
                    daysMask = Dates.EVERY_DAY,
                    reminder = true,
                    colorHex = Palette.VIOLET,
                    colorId = 0,
                    sortOrder = 1,
                    archived = false,
                    createdAt = 210L,
                    dateEpochDay = day + 1,
                    repeatDaily = false,
                ),
            ),
            tasks = listOf(
                // Duplicate by logical key (title + dueDate + dueTimeMinutes)
                TaskEntity("t-backup-dup", "Submit report", "Remote", "tag-backup", Priorities.LOW, "2026-11-20", 600, null, true, 900L, 200L),
                // New task referencing deduplicated tag "tag-backup" -> should remap to "tag-local"
                TaskEntity("t-new", "Review PR", "", "tag-backup", Priorities.NORMAL, "2026-11-21", null, null, false, null, 201L),
            ),
            routineLogs = listOf(
                // Duplicate after remapping r-backup-dup -> r-local
                RoutineLogEntity("r-backup-dup", "2026-11-20", 2000L),
                // New log remapped to r-local
                RoutineLogEntity("r-backup-dup", "2026-11-19", 2001L),
            ),
            focusSessions = listOf(
                // Duplicate by (resolvedRoutineId, startedAt, endedAt, plannedMinutes)
                FocusSessionEntity("f-backup-dup", "r-backup-dup", 5000L, 6000L, 25, true),
                // New focus session linked to deduplicated routine -> should remap to "r-local"
                FocusSessionEntity("f-new", "r-backup-dup", 7000L, 8500L, 25, true),
            ),
            settings = BackupSettings(userName = "BackupUser", themeMode = AppSettings.ThemeMode.DARK),
        )

        val storage = FakeBackupStorage(existing)
        val safetyDir = tempFolder.newFolder("safety-merge")
        val coordinator = BackupCoordinator(storage, safetyDir)

        val first = coordinator.restore(backup, BackupMode.MERGE, nowMillis = 10_000L)
        assertEquals(1, first.tagsAdded)
        assertEquals(1, first.routinesAdded)
        assertEquals(1, first.tasksAdded)
        assertEquals(1, first.routineLogsAdded)
        assertEquals(1, first.focusSessionsAdded)
        assertEquals(5, first.skippedDuplicates)
        assertFalse(first.settingsRestored)

        // Existing data & settings preserved
        assertEquals(existingSettings, storage.current.settings)
        assertEquals(2, storage.current.tags.size)
        assertEquals(2, storage.current.routines.size)
        assertEquals(2, storage.current.tasks.size)
        assertEquals("Local note", storage.current.tasks.first { it.id == "t-local" }.note)
        assertEquals("tag-local", storage.current.tasks.first { it.id == "t-new" }.tagId)
        assertEquals(2, storage.current.routineLogs.size)
        assertTrue(storage.current.routineLogs.any { it.routineId == "r-local" && it.date == "2026-11-19" })
        assertEquals(2, storage.current.focusSessions.size)
        assertEquals("r-local", storage.current.focusSessions.first { it.id == "f-new" }.routineId)
        assertEquals(1, SafetyBackupStore.list(safetyDir).size)

        // Running merge a second time is strictly idempotent
        val second = coordinator.restore(backup, BackupMode.MERGE, nowMillis = 11_000L)
        assertEquals(0, second.routinesAdded)
        assertEquals(0, second.tasksAdded)
        assertEquals(0, second.tagsAdded)
        assertEquals(0, second.routineLogsAdded)
        assertEquals(0, second.focusSessionsAdded)
        assertEquals(2, storage.current.routines.size)
        assertEquals(2, SafetyBackupStore.list(safetyDir).size)
    }

    @Test
    fun `replace wipes old data, applies backup, and reports removed routine IDs for reminder cleanup`() = runBlocking {
        val existing = sampleSnapshot()
        val replacement = BackupPayload(
            formatVersion = 3,
            exportedAt = 5000L,
            routines = listOf(
                existing.routines[0], // kept
                RoutineEntity("r-brand-new", "Walk", Groups.AFTERNOON, "🚶", 1000, Dates.EVERY_DAY, true, Palette.SKY, 4, 0, false, 100L),
            ),
            tasks = emptyList(),
            tags = emptyList(),
            routineLogs = emptyList(),
            taskLogs = emptyList(),
            focusSessions = emptyList(),
            settings = BackupSettings(userName = "RestoredUser", calendarType = CalendarType.GREGORIAN),
        )

        val storage = FakeBackupStorage(existing)
        val safetyDir = tempFolder.newFolder("safety-replace")
        val coordinator = BackupCoordinator(storage, safetyDir)

        val report = coordinator.restore(replacement, BackupMode.REPLACE, nowMillis = 20_000L)
        assertEquals(listOf("r-2"), report.removedRoutineIds)
        assertEquals(2, storage.current.routines.size)
        assertTrue(storage.current.tasks.isEmpty())
        assertTrue(storage.current.tags.isEmpty())
        assertTrue(storage.current.focusSessions.isEmpty())
        assertEquals("RestoredUser", storage.current.settings.userName)
        assertEquals(CalendarType.GREGORIAN, storage.current.settings.calendarType)
        assertTrue(File(report.safetyBackupPath).isFile)
    }

    // ---------------------------------------------------------------- safety retention & rollback

    @Test
    fun `safety backup retains only the 3 most recent files`() = runBlocking {
        val storage = FakeBackupStorage(sampleSnapshot())
        val safetyDir = tempFolder.newFolder("safety-prune")
        val coordinator = BackupCoordinator(storage, safetyDir)
        val payload = BackupPayload(formatVersion = 3, exportedAt = 100L)

        repeat(5) { idx ->
            coordinator.restore(payload, BackupMode.MERGE, nowMillis = 1_763_640_000_000L + idx * 1_000L)
        }

        val kept = SafetyBackupStore.list(safetyDir)
        assertEquals(3, kept.size)
        // Newest first and valid backup JSON
        assertTrue(kept[0].name > kept[1].name && kept[1].name > kept[2].name)
        val latest = BackupCodec.parseAndValidate(kept[0].readText(StandardCharsets.UTF_8))
        assertEquals(2, latest.routines.size)
    }

    @Test
    fun `mid-restore failure leaves existing database and settings completely unchanged`() = runBlocking {
        val original = sampleSnapshot()
        val replacement = BackupPayload(
            formatVersion = 3,
            exportedAt = 999L,
            routines = listOf(
                RoutineEntity("r-only", "Only", Groups.MORNING, "☀️", null, Dates.EVERY_DAY, false, Palette.VIOLET, 0, 0, false, 1L),
            ),
            settings = BackupSettings(userName = "ShouldNeverPersist"),
        )

        // 1. Failure inside DB transaction
        val storageTxFail = FakeBackupStorage(original).apply { failInsideTransactionOnCall = 1 }
        val safetyDir1 = tempFolder.newFolder("safety-tx-fail")
        val coord1 = BackupCoordinator(storageTxFail, safetyDir1)

        assertValidationFails(BackupErrorCode.RESTORE_FAILED) {
            runBlocking { coord1.restore(replacement, BackupMode.REPLACE, nowMillis = 30_000L) }
        }
        assertEquals(original, storageTxFail.current)
        assertEquals(1, SafetyBackupStore.list(safetyDir1).size)

        // 2. Failure during settings apply after DB transaction -> DB rolled back too
        val storageSettingsFail = FakeBackupStorage(original).apply { failApplySettings = true }
        val safetyDir2 = tempFolder.newFolder("safety-settings-fail")
        val coord2 = BackupCoordinator(storageSettingsFail, safetyDir2)

        assertValidationFails(BackupErrorCode.RESTORE_FAILED) {
            runBlocking { coord2.restore(replacement, BackupMode.REPLACE, nowMillis = 31_000L) }
        }
        assertEquals(original, storageSettingsFail.current)
        assertEquals(1, SafetyBackupStore.list(safetyDir2).size)

        // 3. Failure creating safety backup -> aborts before touching DB
        val storageNoSafety = FakeBackupStorage(original)
        val blockedPath = tempFolder.newFile("not-a-directory")
        val coord3 = BackupCoordinator(storageNoSafety, blockedPath)

        assertValidationFails(BackupErrorCode.SAFETY_BACKUP_FAILED) {
            runBlocking { coord3.restore(replacement, BackupMode.REPLACE, nowMillis = 32_000L) }
        }
        assertEquals(original, storageNoSafety.current)
    }

    @Test
    fun `excludedDates trims whitespace during parse, export, and scheduling lookup`() {
        val backupJson = """
            {
              "app": "Tag",
              "formatVersion": 3,
              "exportedAt": 1763640000000,
              "routines": [
                {
                  "id": "r-space",
                  "title": "Walk",
                  "group": "Morning",
                  "daysMask": 127,
                  "reminder": false,
                  "colorHex": 1,
                  "archived": false,
                  "sortOrder": 0,
                  "createdAt": 1000,
                  "dateEpochDay": 0,
                  "repeatDaily": true,
                  "excludedDates": " 2026-11-19 , 2026-11-20 , 2026-11-19 "
                }
              ],
              "tasks": [],
              "tags": []
            }
        """.trimIndent()

        val payload = BackupCodec.parseAndValidate(backupJson)
        val routine = payload.routines.single()
        assertEquals("2026-11-19,2026-11-20", routine.excludedDates)

        val unparsedDirty = routine.copy(excludedDates = " 2026-11-19 , 2026-11-20 ")
        assertTrue(ExcludedDates.isExcluded(unparsedDirty.excludedDates, "2026-11-19"))
        assertTrue(ExcludedDates.isExcluded(unparsedDirty.excludedDates, " 2026-11-20 "))
        assertFalse(StatsEngine.isScheduledOn(unparsedDirty, LocalDate.of(2026, 11, 19)))
        assertFalse(StatsEngine.isScheduledOn(unparsedDirty, LocalDate.of(2026, 11, 20)))
        assertTrue(StatsEngine.isScheduledOn(unparsedDirty, LocalDate.of(2026, 11, 21)))

        val exported = BackupCodec.encodeToString(
            Snapshot(routines = listOf(unparsedDirty), loaded = true),
            exportedAt = 100L,
        )
        assertTrue(exported.contains("\"2026-11-19,2026-11-20\""))
        assertEquals("2026-11-19,2026-11-20", BackupCodec.parseAndValidate(exported).routines.single().excludedDates)
    }

    @Test
    fun `replace clears activeFocusRun only when its linked routine is absent after restore`() = runBlocking {
        val existing = sampleSnapshot()
        val orphanRun = ActiveFocusRun(
            id = "run-orphan",
            routineId = "r-2",
            routineTitle = "Read paper",
            plannedMinutes = 25,
            segmentStartedAt = 10_000L,
            accumulatedSeconds = 0,
            startedAt = 10_000L,
        )
        val keptRun = orphanRun.copy(id = "run-kept", routineId = "r-1", routineTitle = "Morning stretch")
        val generalRun = orphanRun.copy(id = "run-general", routineId = null, routineTitle = null)

        val replacementWithR1Only = BackupPayload(
            formatVersion = 2, // legacy backup without settings still cleans orphan timer
            routines = listOf(existing.routines[0]),
        )

        // 1. Linked routine removed -> activeFocusRun cleared
        val storageOrphan = FakeBackupStorage(existing, activeFocusRun = orphanRun)
        val reportOrphan = BackupCoordinator(storageOrphan, tempFolder.newFolder("safety-orphan"))
            .restore(replacementWithR1Only, BackupMode.REPLACE, nowMillis = 40_000L)
        assertTrue(reportOrphan.activeFocusRunCleared)
        assertNull(storageOrphan.activeFocusRun)

        // 2. Linked routine preserved -> activeFocusRun preserved
        val storageKept = FakeBackupStorage(existing, activeFocusRun = keptRun)
        val reportKept = BackupCoordinator(storageKept, tempFolder.newFolder("safety-kept"))
            .restore(replacementWithR1Only, BackupMode.REPLACE, nowMillis = 41_000L)
        assertFalse(reportKept.activeFocusRunCleared)
        assertEquals(keptRun, storageKept.activeFocusRun)

        // 3. General session (routineId == null) -> preserved
        val storageGeneral = FakeBackupStorage(existing, activeFocusRun = generalRun)
        val reportGeneral = BackupCoordinator(storageGeneral, tempFolder.newFolder("safety-general"))
            .restore(replacementWithR1Only, BackupMode.REPLACE, nowMillis = 42_000L)
        assertFalse(reportGeneral.activeFocusRunCleared)
        assertEquals(generalRun, storageGeneral.activeFocusRun)

        // 4. Failure while clearing activeFocusRun rolls back DB and settings atomically
        val storageFailFocus = FakeBackupStorage(existing, activeFocusRun = orphanRun).apply {
            failClearActiveFocusRun = true
        }
        val withSettings = replacementWithR1Only.copy(
            formatVersion = 3,
            exportedAt = 5000L,
            settings = BackupSettings(userName = "UpdatedName"),
        )
        assertValidationFails(BackupErrorCode.RESTORE_FAILED) {
            runBlocking {
                BackupCoordinator(storageFailFocus, tempFolder.newFolder("safety-fail-focus"))
                    .restore(withSettings, BackupMode.REPLACE, nowMillis = 43_000L)
            }
        }
        assertEquals(existing, storageFailFocus.current)
        assertEquals(orphanRun, storageFailFocus.activeFocusRun)
    }

    @Test
    fun `android auto backup keeps database enabled and excludes only active focus runtime store`() {
        val appDir = listOf(File("src/main"), File("app/src/main"), File("../app/src/main"))
            .firstOrNull { it.isDirectory }
            ?.absoluteFile?.parentFile?.parentFile
            ?: error("could not locate app module from ${File(".").absolutePath}")

        val manifest = File(appDir, "src/main/AndroidManifest.xml").readText(StandardCharsets.UTF_8)
        assertTrue(manifest.contains("""android:allowBackup="true""""))
        assertTrue(manifest.contains("""android:fullBackupContent="@xml/backup_rules""""))
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))

        val backupRules = File(appDir, "src/main/res/xml/backup_rules.xml").readText(StandardCharsets.UTF_8)
        assertTrue(backupRules.contains("""datastore/tag_focus_runtime.preferences_pb"""))
        assertFalse(backupRules.contains("""domain="database""""))

        val extractionRules = File(appDir, "src/main/res/xml/data_extraction_rules.xml").readText(StandardCharsets.UTF_8)
        assertTrue(extractionRules.contains("""datastore/tag_focus_runtime.preferences_pb"""))
        assertFalse(extractionRules.contains("""domain="database""""))
    }
}
