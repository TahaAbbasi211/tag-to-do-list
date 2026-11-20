package com.tag.routine

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.room.Room
import com.tag.routine.data.Dates
import com.tag.routine.data.SettingsStore
import com.tag.routine.data.TagDatabase
import com.tag.routine.data.TagRepository
import com.tag.routine.notifications.FocusTimerRuntime
import com.tag.routine.notifications.ReminderScheduler
import com.tag.routine.widget.WidgetRefresh
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
        .addMigrations(TagDatabase.MIGRATION_1_2, TagDatabase.MIGRATION_2_3, TagDatabase.MIGRATION_3_4)
        .build()

    val settingsStore = SettingsStore(appContext)

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository = TagRepository(database, settingsStore, appScope, appContext)

    fun bootstrap() {
        appScope.launch {
            // Upgrade path: drop the demo routines that 1.0.0 seeded on first launch.
            repository.purgeSeededRoutines()
            repository.seedIfNeeded()
            repository.ensureFirstLaunchDate(Dates.iso(Dates.today()))
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
                notificationsEnabled = snapshot.settings.notificationsEnabled,
            )
            // Populate any placed widgets once initial seeding/bootstrap finishes.
            WidgetRefresh.request(appContext)
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
        ensureContainer(this)
        registerDateChangeReceiver()
    }

    @Synchronized
    internal fun initContainerIfNeeded(): AppContainer {
        if (!this::container.isInitialized) {
            container = AppContainer(this)
            containerOrNull = container
            container.bootstrap()
        } else if (containerOrNull == null) {
            containerOrNull = container
        }
        return container
    }

    /**
     * Manifest-declared ACTION_DATE_CHANGED is blocked on Android 8+, so listen
     * dynamically while the app process is alive without alarms or extra permissions.
     */
    private fun registerDateChangeReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_DATE_CHANGED) {
                    WidgetRefresh.request(context)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_DATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    companion object {
        /** Set once [onCreate] or [ensureContainer] has run. */
        @Volatile
        var containerOrNull: AppContainer? = null
            private set

        /**
         * Returns the live [AppContainer], initializing it on demand (without an Activity)
         * if a widget or broadcast receiver runs before [TagApp.onCreate].
         */
        fun ensureContainer(context: Context): AppContainer? {
            containerOrNull?.let { return it }
            val app = context.applicationContext as? TagApp ?: return null
            return app.initContainerIfNeeded()
        }
    }
}
