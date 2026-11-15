package com.tag.routine.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DatabaseMigrationTest {

    private data class FakeColumn(
        val name: String,
        val defaultValue: Any?,
    )

    private class FakeV2RoutinesDb {
        val executedSql = mutableListOf<String>()
        val columns = linkedMapOf(
            "id" to FakeColumn("id", null),
            "title" to FakeColumn("title", null),
            "group" to FakeColumn("group", null),
            "icon" to FakeColumn("icon", "check"),
            "colorHex" to FakeColumn("colorHex", 0L),
            "timeMinutes" to FakeColumn("timeMinutes", null),
            "daysMask" to FakeColumn("daysMask", 127),
            "isArchived" to FakeColumn("isArchived", 0),
            "sortOrder" to FakeColumn("sortOrder", 0),
            "createdAt" to FakeColumn("createdAt", 0L),
        )
        val rows = mutableListOf<MutableMap<String, Any?>>(
            mutableMapOf(
                "id" to "r1",
                "title" to "Morning walk",
                "group" to "MORNING",
                "icon" to "sunrise",
                "colorHex" to 0xFF009688L,
                "timeMinutes" to 420,
                "daysMask" to 127,
                "isArchived" to 0,
                "sortOrder" to 1,
                "createdAt" to 1700000000000L,
            ),
            mutableMapOf(
                "id" to "r2",
                "title" to "Read 20 pages",
                "group" to "EVENING",
                "icon" to "book",
                "colorHex" to 0xFF42A5F5L,
                "timeMinutes" to 1260,
                "daysMask" to 127,
                "isArchived" to 0,
                "sortOrder" to 2,
                "createdAt" to 1700000001000L,
            ),
        )

        fun execSql(sql: String) {
            executedSql.add(sql)
            val regex = Regex(
                """ALTER\s+TABLE\s+`?(\w+)`?\s+ADD\s+COLUMN\s+`?(\w+)`?\s+\w+\s+NOT\s+NULL\s+DEFAULT\s+(.+)""",
                RegexOption.IGNORE_CASE,
            )
            val match = regex.find(sql.trim()) ?: return
            val table = match.groupValues[1]
            val colName = match.groupValues[2]
            val rawDefault = match.groupValues[3].trim().trim('\'', '"')
            val defaultVal: Any = rawDefault.toIntOrNull() ?: rawDefault
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
