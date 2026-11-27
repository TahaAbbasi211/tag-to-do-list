package com.tag.routine.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room migration verification against exported schemas `1.json` through `4.json`.
 */
@RunWith(AndroidJUnit4::class)
class TagDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TagDatabase::class.java,
    )

    @Test
    fun migrate1To2_createsFocusSessionsAndPreservesRows() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            insertV1Routine(db, id = "r1", title = "Morning walk", timeMinutes = 420)
            db.execSQL(
                """
                INSERT INTO tasks (id, title, note, tagId, priority, dueDate, dueTimeMinutes, remindAt, done, doneAt, createdAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("t1", "Buy water", "", null, 1, "2026-03-21", null, null, 0, null, 1700000000000L),
            )
            db.execSQL(
                "INSERT INTO routine_logs (routineId, date, doneAt) VALUES (?, ?, ?)",
                arrayOf<Any?>("r1", "2026-03-21", 1700000100000L),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, TagDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT id, title, timeMinutes FROM routines WHERE id = ?", arrayOf("r1")).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Morning walk", c.getString(1))
                assertEquals(420, c.getInt(2))
            }
            db.query("SELECT title FROM tasks WHERE id = ?", arrayOf("t1")).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Buy water", c.getString(0))
            }
            db.query("SELECT date FROM routine_logs WHERE routineId = ?", arrayOf("r1")).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("2026-03-21", c.getString(0))
            }
            db.execSQL(
                "INSERT INTO focus_sessions (id, routineId, startedAt, endedAt, plannedMinutes, completed) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("f1", "r1", 1700000200000L, 1700001700000L, 25, 1),
            )
            db.query("SELECT plannedMinutes, completed FROM focus_sessions WHERE id = ?", arrayOf("f1")).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(25, c.getInt(0))
                assertEquals(1, c.getInt(1))
            }
        }
    }

    @Test
    fun migrate2To3_addsDefaultColorIdAndPreservesFocusSessions() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            insertV1Routine(db, id = "r1", title = "Morning walk", timeMinutes = 420)
            db.execSQL(
                "INSERT INTO focus_sessions (id, routineId, startedAt, endedAt, plannedMinutes, completed) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("f1", "r1", 1700000200000L, 1700001700000L, 25, 1),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, TagDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT id, title, colorId FROM routines WHERE id = ?", arrayOf("r1")).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Morning walk", c.getString(1))
                assertEquals(0, c.getInt(2))
            }
            db.query("SELECT COUNT(*) FROM focus_sessions WHERE id = ?", arrayOf("f1")).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        }
    }

    @Test
    fun migrate3To4_addsDayCentricColumnsWithDefaults() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO routines (id, title, groupName, icon, timeMinutes, daysMask, reminder, colorHex, colorId, sortOrder, archived, createdAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("r1", "Read 20 pages", "Evening", "book", 1260, 127, 1, 0xFF42A5F5L, 4, 1, 0, 1700000001000L),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, TagDatabase.MIGRATION_3_4).use { db ->
            db.query(
                "SELECT title, colorId, dateEpochDay, repeatDaily, endDateEpochDay, excludedDates FROM routines WHERE id = ?",
                arrayOf("r1"),
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Read 20 pages", c.getString(0))
                assertEquals(4, c.getInt(1))
                assertEquals(0L, c.getLong(2))
                assertEquals(1, c.getInt(3))
                assertTrue(c.isNull(4))
                assertEquals("", c.getString(5))
            }
        }
    }

    @Test
    fun migrate1To4_chainPreservesDataAndOpensWithRoom() = runBlocking {
        helper.createDatabase(TEST_DB, 1).use { db ->
            insertV1Routine(db, id = "r1", title = "Hydrate", timeMinutes = null)
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            TagDatabase.MIGRATION_1_2,
            TagDatabase.MIGRATION_2_3,
            TagDatabase.MIGRATION_3_4,
        ).close()

        val roomDb = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TagDatabase::class.java,
            TEST_DB,
        ).addMigrations(
            TagDatabase.MIGRATION_1_2,
            TagDatabase.MIGRATION_2_3,
            TagDatabase.MIGRATION_3_4,
        ).build()

        try {
            val routines = roomDb.routines().getAll()
            assertEquals(1, routines.size)
            val r = routines.first()
            assertEquals("r1", r.id)
            assertEquals("Hydrate", r.title)
            assertEquals(0, r.colorId)
            assertEquals(0L, r.dateEpochDay)
            assertTrue(r.repeatDaily)
            assertEquals("", r.excludedDates)
        } finally {
            roomDb.close()
        }
    }

    private fun insertV1Routine(db: SupportSQLiteDatabase, id: String, title: String, timeMinutes: Int?) {
        db.execSQL(
            """
            INSERT INTO routines (id, title, groupName, icon, timeMinutes, daysMask, reminder, colorHex, sortOrder, archived, createdAt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(id, title, "Morning", "sunrise", timeMinutes, 127, 0, 0xFF7C6CFFL, 1, 0, 1700000000000L),
        )
    }

    companion object {
        private const val TEST_DB = "tag-migration-test.db"
    }
}
