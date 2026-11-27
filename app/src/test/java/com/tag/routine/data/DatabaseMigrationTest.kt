package com.tag.routine.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.DriverManager

/**
 * Real SQLite-backed JVM migration tests (via `org.xerial:sqlite-jdbc`) verified against
 * the exported Room schemas in `app/schemas/com.tag.routine.data.TagDatabase/{1,2,3,4}.json`.
 */
class DatabaseMigrationTest {

    private data class SchemaField(
        val columnName: String,
        val affinity: String,
        val notNull: Boolean,
    )

    private data class SchemaEntity(
        val tableName: String,
        val createSql: String,
        val fields: List<SchemaField>,
        val primaryKeyColumns: List<String>,
    )

    private data class SchemaSpec(
        val version: Int,
        val entities: List<SchemaEntity>,
    )

    private data class SqliteColumnInfo(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val pkIndex: Int,
    )

    private val schemaDir: File = run {
        val candidates = listOf(
            File("schemas/com.tag.routine.data.TagDatabase"),
            File("app/schemas/com.tag.routine.data.TagDatabase"),
            File("../app/schemas/com.tag.routine.data.TagDatabase"),
        )
        candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate Room schemas from ${File(".").absolutePath}")
    }

    private fun loadSchema(version: Int): SchemaSpec {
        val file = File(schemaDir, "$version.json")
        assertTrue("Missing exported Room schema ${file.absolutePath}", file.isFile)
        var parsedVersion = -1
        val entities = mutableListOf<SchemaEntity>()

        file.bufferedReader(StandardCharsets.UTF_8).use { buffered ->
            val reader = JsonStreamReader(buffered)
            reader.readObject { rootField ->
                when (rootField) {
                    "database" -> reader.readObject { dbField ->
                        when (dbField) {
                            "version" -> parsedVersion = reader.readInt()
                            "entities" -> reader.readArray {
                                var tableName = ""
                                var createSql = ""
                                val fields = mutableListOf<SchemaField>()
                                val pkColumns = mutableListOf<String>()
                                reader.readObject { entityField ->
                                    when (entityField) {
                                        "tableName" -> tableName = reader.readString()
                                        "createSql" -> createSql = reader.readString()
                                        "fields" -> reader.readArray {
                                            var columnName = ""
                                            var affinity = ""
                                            var notNull = false
                                            reader.readObject { f ->
                                                when (f) {
                                                    "columnName" -> columnName = reader.readString()
                                                    "affinity" -> affinity = reader.readString()
                                                    "notNull" -> notNull = reader.readBoolean()
                                                    else -> reader.skipValue()
                                                }
                                            }
                                            fields += SchemaField(columnName, affinity, notNull)
                                        }
                                        "primaryKey" -> reader.readObject { pkField ->
                                            when (pkField) {
                                                "columnNames" -> reader.readArray {
                                                    pkColumns += reader.readString()
                                                }
                                                else -> reader.skipValue()
                                            }
                                        }
                                        else -> reader.skipValue()
                                    }
                                }
                                entities += SchemaEntity(
                                    tableName = tableName,
                                    createSql = createSql.replace("\${TABLE_NAME}", tableName),
                                    fields = fields,
                                    primaryKeyColumns = pkColumns,
                                )
                            }
                            else -> reader.skipValue()
                        }
                    }
                    else -> reader.skipValue()
                }
            }
        }
        assertEquals(version, parsedVersion)
        return SchemaSpec(parsedVersion, entities)
    }

    private class RealSqliteSupportDb(private val conn: Connection) {
        fun execSql(sql: String, bindArgs: Array<out Any?>? = null) {
            if (bindArgs == null || bindArgs.isEmpty()) {
                conn.createStatement().use { stmt -> stmt.execute(sql) }
            } else {
                conn.prepareStatement(sql).use { ps ->
                    bindArgs.forEachIndexed { index, value ->
                        when (value) {
                            null -> ps.setObject(index + 1, null)
                            is Int -> ps.setInt(index + 1, value)
                            is Long -> ps.setLong(index + 1, value)
                            is Double -> ps.setDouble(index + 1, value)
                            is Float -> ps.setFloat(index + 1, value)
                            is Boolean -> ps.setInt(index + 1, if (value) 1 else 0)
                            else -> ps.setString(index + 1, value.toString())
                        }
                    }
                    ps.execute()
                }
            }
        }

        fun asSupportSqliteDb(): SupportSQLiteDatabase {
            return Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "execSQL" -> {
                        val sql = args?.getOrNull(0) as? String
                        val bindArgs = args?.getOrNull(1) as? Array<*>
                        if (sql != null) execSql(sql, bindArgs)
                        null
                    }
                    "beginTransaction", "beginTransactionNonExclusive" -> {
                        conn.autoCommit = false
                        null
                    }
                    "setTransactionSuccessful" -> null
                    "endTransaction" -> {
                        if (!conn.autoCommit) {
                            conn.commit()
                            conn.autoCommit = true
                        }
                        null
                    }
                    "inTransaction" -> !conn.autoCommit
                    "isOpen" -> !conn.isClosed
                    "close" -> {
                        conn.close()
                        null
                    }
                    else -> error("Unexpected SupportSQLiteDatabase call in migration test: ${method.name}")
                }
            } as SupportSQLiteDatabase
        }
    }

    private fun withSqliteAtVersion(version: Int, block: (Connection, SupportSQLiteDatabase) -> Unit) {
        val spec = loadSchema(version)
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { stmt ->
                spec.entities.forEach { entity ->
                    stmt.execute(entity.createSql)
                }
            }
            val supportDb = RealSqliteSupportDb(conn).asSupportSqliteDb()
            block(conn, supportDb)
        }
    }

    private fun readSqliteColumns(conn: Connection, table: String): Map<String, SqliteColumnInfo> {
        val columns = linkedMapOf<String, SqliteColumnInfo>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                while (rs.next()) {
                    val name = rs.getString("name")
                    columns[name] = SqliteColumnInfo(
                        name = name,
                        type = rs.getString("type").uppercase(),
                        notNull = rs.getInt("notnull") == 1,
                        pkIndex = rs.getInt("pk"),
                    )
                }
            }
        }
        return columns
    }

    private fun assertMatchesRoomSchema(conn: Connection, expectedVersion: Int) {
        val expected = loadSchema(expectedVersion)
        val actualTables = mutableSetOf<String>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { rs ->
                while (rs.next()) {
                    actualTables += rs.getString("name")
                }
            }
        }

        for (entity in expected.entities) {
            assertTrue(
                "Missing table '${entity.tableName}' after migrating to v$expectedVersion",
                entity.tableName in actualTables,
            )
            val cols = readSqliteColumns(conn, entity.tableName)
            assertEquals(
                "Column set mismatch for '${entity.tableName}' at v$expectedVersion",
                entity.fields.map { it.columnName }.toSet(),
                cols.keys,
            )
            for (field in entity.fields) {
                val actualCol = cols[field.columnName]!!
                assertEquals(
                    "Affinity mismatch for ${entity.tableName}.${field.columnName}",
                    field.affinity.uppercase(),
                    actualCol.type,
                )
                assertEquals(
                    "notNull mismatch for ${entity.tableName}.${field.columnName}",
                    field.notNull,
                    actualCol.notNull,
                )
            }
            val actualPk = cols.values.filter { it.pkIndex > 0 }.sortedBy { it.pkIndex }.map { it.name }
            assertEquals(
                "Primary key mismatch for '${entity.tableName}' at v$expectedVersion",
                entity.primaryKeyColumns,
                actualPk,
            )
        }
    }

    private fun insertV1SampleData(conn: Connection) {
        conn.prepareStatement(
            """
            INSERT INTO routines (id, title, groupName, icon, timeMinutes, daysMask, reminder, colorHex, sortOrder, archived, createdAt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, "r1")
            ps.setString(2, "Morning walk's test")
            ps.setString(3, "Morning")
            ps.setString(4, "sunrise")
            ps.setInt(5, 420)
            ps.setInt(6, 127)
            ps.setInt(7, 1)
            ps.setLong(8, 0xFF009688L)
            ps.setInt(9, 1)
            ps.setInt(10, 0)
            ps.setLong(11, 1700000000000L)
            ps.executeUpdate()

            ps.setString(1, "r2")
            ps.setString(2, "مطالعه شبانه")
            ps.setString(3, "Evening")
            ps.setString(4, "book")
            ps.setObject(5, null)
            ps.setInt(6, 127)
            ps.setInt(7, 0)
            ps.setLong(8, 0xFF42A5F5L)
            ps.setInt(9, 2)
            ps.setInt(10, 0)
            ps.setLong(11, 1700000001000L)
            ps.executeUpdate()
        }
        conn.createStatement().use { stmt ->
            stmt.execute(
                "INSERT INTO tasks (id, title, note, tagId, priority, dueDate, dueTimeMinutes, remindAt, done, doneAt, createdAt) " +
                    "VALUES ('t1', 'Pay bills', 'Online', NULL, 2, '2026-03-21', NULL, NULL, 0, NULL, 1700000000000)",
            )
            stmt.execute("INSERT INTO routine_logs (routineId, date, doneAt) VALUES ('r1', '2026-03-21', 1700000500000)")
        }
    }

    @Test
    fun migrate1To2_realSqliteCreatesFocusSessionsAndMatchesV2Schema() {
        withSqliteAtVersion(1) { conn, db ->
            insertV1SampleData(conn)

            assertEquals(1, TagDatabase.MIGRATION_1_2.startVersion)
            assertEquals(2, TagDatabase.MIGRATION_1_2.endVersion)
            TagDatabase.MIGRATION_1_2.migrate(db)

            assertMatchesRoomSchema(conn, expectedVersion = 2)

            conn.createStatement().use { stmt ->
                stmt.execute(
                    "INSERT INTO focus_sessions (id, routineId, startedAt, endedAt, plannedMinutes, completed) " +
                        "VALUES ('fs1', 'r1', 1700001000000, 1700002500000, 25, 1)",
                )
                stmt.executeQuery("SELECT COUNT(*) FROM routines").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(2, rs.getInt(1))
                }
                stmt.executeQuery("SELECT plannedMinutes, completed FROM focus_sessions WHERE id = 'fs1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(25, rs.getInt(1))
                    assertEquals(1, rs.getInt(2))
                }
            }
        }
    }

    @Test
    fun migrate2To3_realSqlitePreservesExistingRoutinesAndAddsDefaultColorId() {
        withSqliteAtVersion(2) { conn, db ->
            insertV1SampleData(conn)
            conn.createStatement().use { stmt ->
                stmt.execute(
                    "INSERT INTO focus_sessions (id, routineId, startedAt, endedAt, plannedMinutes, completed) " +
                        "VALUES ('fs1', 'r1', 1700001000000, 1700002500000, 25, 1)",
                )
            }

            assertEquals(2, TagDatabase.MIGRATION_2_3.startVersion)
            assertEquals(3, TagDatabase.MIGRATION_2_3.endVersion)
            TagDatabase.MIGRATION_2_3.migrate(db)

            assertMatchesRoomSchema(conn, expectedVersion = 3)

            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT id, title, timeMinutes, colorId FROM routines ORDER BY sortOrder").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("r1", rs.getString("id"))
                    assertEquals("Morning walk's test", rs.getString("title"))
                    assertEquals(420, rs.getInt("timeMinutes"))
                    assertEquals(0, rs.getInt("colorId"))

                    assertTrue(rs.next())
                    assertEquals("r2", rs.getString("id"))
                    assertEquals("مطالعه شبانه", rs.getString("title"))
                    assertNull(rs.getObject("timeMinutes"))
                    assertEquals(0, rs.getInt("colorId"))
                }
            }
        }
    }

    @Test
    fun migrate3To4_realSqlitePreservesExistingRoutinesAndMarksRepeatDailyTrue() {
        withSqliteAtVersion(3) { conn, db ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    "INSERT INTO routines (id, title, groupName, icon, timeMinutes, daysMask, reminder, colorHex, colorId, sortOrder, archived, createdAt) " +
                        "VALUES ('r1', 'Morning walk', 'Morning', 'sunrise', 420, 127, 0, 4278228616, 3, 1, 0, 1700000000000)",
                )
                stmt.execute(
                    "INSERT INTO routines (id, title, groupName, icon, timeMinutes, daysMask, reminder, colorHex, colorId, sortOrder, archived, createdAt) " +
                        "VALUES ('r2', 'Read 20 pages', 'Evening', 'book', 1260, 127, 1, 4282557941, 5, 2, 0, 1700000001000)",
                )
            }

            assertEquals(3, TagDatabase.MIGRATION_3_4.startVersion)
            assertEquals(4, TagDatabase.MIGRATION_3_4.endVersion)
            TagDatabase.MIGRATION_3_4.migrate(db)

            assertMatchesRoomSchema(conn, expectedVersion = 4)

            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                    "SELECT id, title, colorId, dateEpochDay, repeatDaily, endDateEpochDay, excludedDates FROM routines ORDER BY sortOrder",
                ).use { rs ->
                    assertTrue(rs.next())
                    assertEquals("r1", rs.getString("id"))
                    assertEquals("Morning walk", rs.getString("title"))
                    assertEquals(3, rs.getInt("colorId"))
                    assertEquals(0L, rs.getLong("dateEpochDay"))
                    assertEquals(1, rs.getInt("repeatDaily"))
                    assertNull(rs.getObject("endDateEpochDay"))
                    assertEquals("", rs.getString("excludedDates"))

                    assertTrue(rs.next())
                    assertEquals("r2", rs.getString("id"))
                    assertEquals(5, rs.getInt("colorId"))
                    assertEquals(1, rs.getInt("repeatDaily"))
                }
            }
        }
    }

    @Test
    fun migrate1To4_realSqliteChainPreservesDataAndMatchesV4Schema() {
        withSqliteAtVersion(1) { conn, db ->
            insertV1SampleData(conn)

            TagDatabase.MIGRATION_1_2.migrate(db)
            TagDatabase.MIGRATION_2_3.migrate(db)
            TagDatabase.MIGRATION_3_4.migrate(db)

            assertMatchesRoomSchema(conn, expectedVersion = 4)

            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                    "SELECT id, title, colorId, dateEpochDay, repeatDaily, endDateEpochDay, excludedDates FROM routines WHERE id = 'r1'",
                ).use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Morning walk's test", rs.getString("title"))
                    assertEquals(0, rs.getInt("colorId"))
                    assertEquals(0L, rs.getLong("dateEpochDay"))
                    assertEquals(1, rs.getInt("repeatDaily"))
                    assertNull(rs.getObject("endDateEpochDay"))
                    assertEquals("", rs.getString("excludedDates"))
                }
                stmt.executeQuery("SELECT title FROM tasks WHERE id = 't1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Pay bills", rs.getString("title"))
                }
                stmt.executeQuery("SELECT date FROM routine_logs WHERE routineId = 'r1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("2026-03-21", rs.getString("date"))
                }
                // Verify inserting a new v4 row and focus session succeeds under real SQLite constraints.
                stmt.execute(
                    "INSERT INTO routines (id, title, groupName, icon, timeMinutes, daysMask, reminder, colorHex, colorId, sortOrder, archived, createdAt, dateEpochDay, repeatDaily, endDateEpochDay, excludedDates) " +
                        "VALUES ('r3', 'One-off task', 'Afternoon', 'target', NULL, 127, 0, 4286344447, 2, 3, 0, 1700000002000, 20533, 0, NULL, '')",
                )
                stmt.execute(
                    "INSERT INTO focus_sessions (id, routineId, startedAt, endedAt, plannedMinutes, completed) " +
                        "VALUES ('fs-new', NULL, 1700003000000, 1700004500000, 25, 1)",
                )
                stmt.executeQuery("SELECT repeatDaily, dateEpochDay FROM routines WHERE id = 'r3'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(0, rs.getInt("repeatDaily"))
                    assertEquals(20533L, rs.getLong("dateEpochDay"))
                }
                stmt.executeQuery("SELECT routineId FROM focus_sessions WHERE id = 'fs-new'").use { rs ->
                    assertTrue(rs.next())
                    assertNull(rs.getObject("routineId"))
                    assertNotNull(readSqliteColumns(conn, "focus_sessions")["id"])
                }
            }
        }
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
