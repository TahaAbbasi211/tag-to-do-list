package com.tag.routine.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------- entities

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val title: String,
    val groupName: String,
    val icon: String,
    val timeMinutes: Int?,
    val daysMask: Int,
    val reminder: Boolean,
    val colorHex: Long,
    val sortOrder: Int,
    val archived: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val note: String,
    val tagId: String?,
    val priority: Int,
    val dueDate: String?,
    val dueTimeMinutes: Int?,
    val remindAt: Long?,
    val done: Boolean,
    val doneAt: Long?,
    val createdAt: Long,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: Long,
    val sortOrder: Int,
)

@Entity(tableName = "routine_logs", primaryKeys = ["routineId", "date"])
data class RoutineLogEntity(
    val routineId: String,
    val date: String,
    val doneAt: Long,
)

@Entity(tableName = "task_logs", primaryKeys = ["taskId", "date"])
data class TaskLogEntity(
    val taskId: String,
    val date: String,
    val doneAt: Long,
)

/** A completed (or abandoned) focus-timer run, optionally attached to a routine. */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val routineId: String?,
    val startedAt: Long,
    val endedAt: Long,
    val plannedMinutes: Int,
    val completed: Boolean,
)

// ---------------------------------------------------------------- daos

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE archived = 0 ORDER BY sortOrder, createdAt")
    fun observeActive(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun byId(id: String): RoutineEntity?

    @Upsert
    suspend fun upsert(item: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM routines")
    suspend fun count(): Int

    /** One-off migration: drops the routines that 1.0.0 seeded on first launch. */
    @Query("DELETE FROM routines WHERE id LIKE 'routine-%'")
    suspend fun deleteSeeded(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM routines")
    suspend fun maxOrder(): Int
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: String): TaskEntity?

    @Upsert
    suspend fun upsert(item: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun count(): Int

    @Query("UPDATE tasks SET done = 0, doneAt = NULL")
    suspend fun clearAllDone()

    @Query("UPDATE tasks SET tagId = NULL WHERE tagId = :tagId")
    suspend fun clearTag(tagId: String)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun byId(id: String): TagEntity?

    @Upsert
    suspend fun upsert(item: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM tags")
    suspend fun maxOrder(): Int
}

@Dao
interface LogDao {
    @Query("SELECT * FROM routine_logs WHERE date BETWEEN :from AND :to")
    fun routineLogsBetween(from: String, to: String): Flow<List<RoutineLogEntity>>

    @Query("SELECT * FROM task_logs WHERE date BETWEEN :from AND :to")
    fun taskLogsBetween(from: String, to: String): Flow<List<TaskLogEntity>>

    @Query("SELECT * FROM routine_logs")
    fun observeAllRoutineLogs(): Flow<List<RoutineLogEntity>>

    @Query("SELECT * FROM task_logs")
    fun observeAllTaskLogs(): Flow<List<TaskLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineLog(log: RoutineLogEntity)

    @Query("DELETE FROM routine_logs WHERE routineId = :routineId AND date = :date")
    suspend fun deleteRoutineLog(routineId: String, date: String)

    @Query("DELETE FROM routine_logs WHERE routineId = :routineId")
    suspend fun deleteRoutineLogsFor(routineId: String)

    @Query("DELETE FROM routine_logs WHERE routineId LIKE 'routine-%'")
    suspend fun deleteRoutineLogsForSeeded()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskLog(log: TaskLogEntity)

    @Query("DELETE FROM task_logs WHERE taskId = :taskId AND date = :date")
    suspend fun deleteTaskLog(taskId: String, date: String)

    @Query("DELETE FROM task_logs WHERE taskId = :taskId")
    suspend fun deleteTaskLogsFor(taskId: String)

    @Query("DELETE FROM routine_logs")
    suspend fun clearRoutineLogs()

    @Query("DELETE FROM task_logs")
    suspend fun clearTaskLogs()
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :from ORDER BY startedAt DESC")
    fun observeSince(from: Long): Flow<List<FocusSessionEntity>>

    @Upsert
    suspend fun upsert(item: FocusSessionEntity)

    @Query("SELECT COUNT(*) FROM focus_sessions")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(endedAt - startedAt), 0) FROM focus_sessions WHERE completed = 1")
    suspend fun totalCompletedMillis(): Long

    @Query("DELETE FROM focus_sessions")
    suspend fun clear()
}

// ---------------------------------------------------------------- database

@Database(
    entities = [
        RoutineEntity::class,
        TaskEntity::class,
        TagEntity::class,
        RoutineLogEntity::class,
        TaskLogEntity::class,
        FocusSessionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class TagDatabase : RoomDatabase() {
    abstract fun routines(): RoutineDao
    abstract fun tasks(): TaskDao
    abstract fun tags(): TagDao
    abstract fun logs(): LogDao
    abstract fun focus(): FocusDao

    companion object {
        const val NAME = "tag.db"

        /**
         * v1 -> v2: adds the focus_sessions table used by the focus timer. Written as a real
         * migration (rather than a destructive fallback) so an upgrade never drops the user's
         * routines, tasks and history.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `focus_sessions` (
                        `id` TEXT NOT NULL,
                        `routineId` TEXT,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER NOT NULL,
                        `plannedMinutes` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
