package com.tag.routine

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.tag.routine.data.SettingsStore
import com.tag.routine.data.TagDatabase
import com.tag.routine.data.TagRepository
import com.tag.routine.notifications.FocusTimerRuntime
import com.tag.routine.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-rolled dependency container: one database, one repository, one app scope. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val database: TagDatabase = Room.databaseBuilder(appContext, TagDatabase::class.java, TagDatabase.NAME)
        // Real migrations instead of a destructive fallback: an upgrade must never drop
        // the user's routines, tasks or history.
        .addMigrations(TagDatabase.MIGRATION_1_2)
        .build()

    val settingsStore = SettingsStore(appContext)

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository = TagRepository(database, settingsStore, appScope)

    fun bootstrap() {
        appScope.launch {
            // Upgrade path: drop the demo routines that 1.0.0 seeded on first launch.
            repository.purgeSeededRoutines()
            repository.seedIfNeeded()
            // A focus run that reached its planned length while the app (and possibly its
            // service) was gone still deserves to be recorded. The write is keyed by the
            // run's own id, so this can never double-count a session the service already
            // settled.
            settleFinishedFocusRun()
            val snapshot = repository.snapshot.value
            ReminderScheduler.rescheduleAll(
                context = appContext,
                routines = snapshot.routines,
                digestMinutes = snapshot.settings.dailyDigestMinutes,
                digestEnabled = snapshot.settings.notificationsEnabled && snapshot.settings.dailyDigestEnabled,
            )
        }
    }

    /**
     * Closes a persisted focus run that has already run out, recording it as a finished
     * session. Does nothing when no run is in progress, or when the run still has time
     * left — in that case the timer is left alone and merely resumes in the background.
     *
     * The work is delegated to [FocusTimerRuntime] so that the settle path is identical
     * to the one the service uses.
     */
    private suspend fun settleFinishedFocusRun() {
        val run = repository.currentActiveFocusRun() ?: return
        FocusTimerRuntime.adopt(run)
        FocusTimerRuntime.advance()
    }
}

class TagApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Published before bootstrap: the bootstrap work runs on another thread and reads
        // this back through [containerOrNull], so it must never observe a half-built state.
        containerOrNull = container
        container.bootstrap()
    }

    companion object {
        /** Set once [onCreate] has run; receivers can be invoked before that on some devices. */
        @Volatile
        var containerOrNull: AppContainer? = null
            private set
    }
}
