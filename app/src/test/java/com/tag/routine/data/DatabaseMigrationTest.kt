package com.tag.routine.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DatabaseMigrationTest {

    private data class FakeColumn(
        val name: String,
        val defaultValue: Any?,
    )

    private class FakeV2RoutinesDb(
        includeColorId: Boolean = false,
        includeFocusTable: Boolean = true,
    ) {
        val executedSql = mutableListOf<String>()
        val createdTables = linkedSetOf("routines", "tasks", "tags", "routine_logs", "task_logs").apply {
            if (includeFocusTable) add("focus_sessions")
        }
        val columns = linkedMapOf(
            "id" to FakeColumn("id", null),
            "title" to FakeColumn("title", null),
            "groupName" to FakeColumn("groupName", null),
            "icon" to FakeColumn("icon", "check"),
            "colorHex" to FakeColumn("colorHex", 0L),
            "timeMinutes" to FakeColumn("timeMinutes", null),
            "daysMask" to FakeColumn("daysMask", 127),
            "archived" to FakeColumn("archived", 0),
            "sortOrder" to FakeColumn("sortOrder", 0),
            "createdAt" to FakeColumn("createdAt", 0L),
        ).apply {
            if (includeColorId) {
                put("colorId", FakeColumn("colorId", 0))
            }
        }
        val rows = mutableListOf<MutableMap<String, Any?>>(
            mutableMapOf<String, Any?>(
                "id" to "r1",
                "title" to "Morning walk",
                "groupName" to "MORNING",
                "icon" to "sunrise",
                "colorHex" to 0xFF009688L,
                "timeMinutes" to 420,
                "daysMask" to 127,
                "archived" to 0,
                "sortOrder" to 1,
                "createdAt" to 1700000000000L,
            ).apply { if (includeColorId) put("colorId", 3) },
            mutableMapOf<String, Any?>(
                "id" to "r2",
                "title" to "Read 20 pages",
                "groupName" to "EVENING",
                "icon" to "book",
                "colorHex" to 0xFF42A5F5L,
                "timeMinutes" to 1260,
                "daysMask" to 127,
                "archived" to 0,
                "sortOrder" to 2,
                "createdAt" to 1700000001000L,
            ).apply { if (includeColorId) put("colorId", 5) },
        )

        fun execSql(sql: String) {
            executedSql.add(sql)
            val createMatch = Regex(
                """CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?""",
                RegexOption.IGNORE_CASE,
            ).find(sql.trim())
            if (createMatch != null) {
                createdTables.add(createMatch.groupValues[1])
                return
            }

            val alterMatch = Regex(
                """ALTER\s+TABLE\s+`?(\w+)`?\s+ADD\s+COLUMN\s+`?(\w+)`?\s+\w+(?:\s+NOT\s+NULL)?\s+DEFAULT\s+(.+)""",
                RegexOption.IGNORE_CASE,
            ).find(sql.trim()) ?: return
            val table = alterMatch.groupValues[1]
            val colName = alterMatch.groupValues[2]
            val rawDefault = alterMatch.groupValues[3].trim()
            val defaultVal: Any? = when {
                rawDefault.equals("NULL", ignoreCase = true) -> null
                rawDefault.startsWith("'") && rawDefault.endsWith("'") ->
                    rawDefault.substring(1, rawDefault.length - 1)
                rawDefault.startsWith("\"") && rawDefault.endsWith("\"") ->
                    rawDefault.substring(1, rawDefault.length - 1)
                else -> rawDefault.toIntOrNull() ?: rawDefault
            }
            if (table == "routines") {
                columns[colName] = FakeColumn(colName, defaultVal)
                rows.forEach { row -> row[colName] = defaultVal }
            }
        }

        fun asSupportSqliteDb(): SupportSQLiteDatabase {
            return Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "execSQL" -> {
                        val sql = args?.firstOrNull() as? String
                        if (sql != null) execSql(sql)
                        null
                    }
                    "isOpen" -> true
                    "getVersion" -> 2
                    else -> null
                }
            } as SupportSQLiteDatabase
        }
    }

    @Test
    fun migrate2To3_preservesExistingRoutinesAndAddsDefaultColorId() {
        val fakeDb = FakeV2RoutinesDb()
        val sqliteDb = fakeDb.asSupportSqliteDb()

        assertEquals(2, TagDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, TagDatabase.MIGRATION_2_3.endVersion)

        TagDatabase.MIGRATION_2_3.migrate(sqliteDb)

        assertTrue(
            "Expected ALTER TABLE routines ADD COLUMN colorId",
            fakeDb.executedSql.any {
                it.contains("routines", ignoreCase = true) &&
                    it.contains("ADD COLUMN", ignoreCase = true) &&
                    it.contains("colorId", ignoreCase = true)
            },
        )
        assertTrue(fakeDb.columns.containsKey("colorId"))
        assertEquals(2, fakeDb.rows.size)

        val first = fakeDb.rows[0]
        assertEquals("r1", first["id"])
        assertEquals("Morning walk", first["title"])
        assertEquals("sunrise", first["icon"])
        assertEquals(0, first["colorId"])

        val second = fakeDb.rows[1]
        assertEquals("r2", second["id"])
        assertEquals("Read 20 pages", second["title"])
        assertEquals("book", second["icon"])
        assertEquals(0, second["colorId"])
    }

    @Test
    fun migrate3To4_preservesExistingRoutinesAndMarksRepeatDailyTrue() {
        val fakeDb = FakeV2RoutinesDb(includeColorId = true, includeFocusTable = true)
        val sqliteDb = fakeDb.asSupportSqliteDb()

        assertEquals(3, TagDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, TagDatabase.MIGRATION_3_4.endVersion)

        TagDatabase.MIGRATION_3_4.migrate(sqliteDb)

        assertTrue(fakeDb.columns.containsKey("dateEpochDay"))
        assertTrue(fakeDb.columns.containsKey("repeatDaily"))
        assertTrue(fakeDb.columns.containsKey("endDateEpochDay"))
        assertTrue(fakeDb.columns.containsKey("excludedDates"))
        assertEquals(2, fakeDb.rows.size)

        val first = fakeDb.rows[0]
        assertEquals("r1", first["id"])
        assertEquals("Morning walk", first["title"])
        assertEquals(3, first["colorId"])
        assertEquals(0, first["dateEpochDay"])
        assertEquals(1, first["repeatDaily"])
        assertNull(first["endDateEpochDay"])
        assertEquals("", first["excludedDates"])

        val second = fakeDb.rows[1]
        assertEquals("r2", second["id"])
        assertEquals(5, second["colorId"])
        assertEquals(1, second["repeatDaily"])
    }

    @Test
    fun migrate1To4_chainPreservesDataSafely() {
        val fakeDb = FakeV2RoutinesDb(includeColorId = false, includeFocusTable = false)
        val sqliteDb = fakeDb.asSupportSqliteDb()

        TagDatabase.MIGRATION_1_2.migrate(sqliteDb)
        TagDatabase.MIGRATION_2_3.migrate(sqliteDb)
        TagDatabase.MIGRATION_3_4.migrate(sqliteDb)

        assertTrue(fakeDb.createdTables.contains("focus_sessions"))
        assertEquals(2, fakeDb.rows.size)
        val first = fakeDb.rows[0]
        assertEquals("r1", first["id"])
        assertEquals(0, first["colorId"])
        assertEquals(0, first["dateEpochDay"])
        assertEquals(1, first["repeatDaily"])
        assertNull(first["endDateEpochDay"])
        assertEquals("", first["excludedDates"])
    }

    @Test
    fun routinePaletteAndEmojis_haveExpectedCountsAndLegacyMapping() {
        assertEquals(8, RoutineColors.all.size)
        RoutineColors.all.forEachIndexed { index, spec ->
            assertEquals(index, spec.id)
            assertTrue(RoutineColors.hexFor(spec.id, isDark = false) == spec.lightHex)
            assertTrue(RoutineColors.hexFor(spec.id, isDark = true) == spec.darkHex)
        }
        assertEquals("☀️", RoutineEmojis.resolve("sunrise"))
        assertEquals("📖", RoutineEmojis.resolve("book"))
        assertEquals("🎯", RoutineEmojis.resolve("🎯"))
        assertEquals("✨", RoutineEmojis.resolve(""))
    }

    @Test
    fun routineTemplates_hasFourTemplatesWithThreeToFourItems() {
        assertEquals(4, RoutineTemplates.all.size)
        assertEquals(listOf("morning", "study", "workout", "sleep"), RoutineTemplates.all.map { it.id })
        RoutineTemplates.all.forEach { template ->
            assertTrue(
                "Template ${template.id} should have 3..4 items, was ${template.items.size}",
                template.items.size in 3..4,
            )
        }
    }
}
