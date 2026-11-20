package com.tag.routine.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.tag.routine.TagApp
import com.tag.routine.data.Dates
import com.tag.routine.data.Snapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

/**
 * Home-screen widget: today's progress and the next items.
 *
 * All the arithmetic lives in [WidgetStateBuilder] (pure, unit-tested); this class only
 * moves data in and out of it. Database reads happen off the main thread behind
 * [goAsync]. If the data cannot be read the widget is left untouched (no crash, no
 * misleading "nothing scheduled"); that empty state is shown only for real empty data.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        refresh(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // A resize can cross the compact/wide threshold, so redraw this instance.
        refresh(context, intArrayOf(appWidgetId))
    }

    private fun refresh(context: Context, ids: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetUpdater.updateAll(appContext, ids)
            } catch (t: Throwable) {
                Log.w(TAG, "widget update failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "TodayWidgetProvider"
    }
}

/**
 * Reads the app's data and pushes a fresh RemoteViews to every widget instance.
 *
 * Kept separate from the provider so the same refresh can be triggered from inside the
 * app (after a toggle, an edit, a delete or a restore) without going through a broadcast.
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private const val RETRY_DELAY_MS = 1_500L

    /**
     * Refreshes the given instances, or every placed instance when [ids] is null.
     *
     * Safe to call when no widget is placed: it returns immediately. When the data cannot
     * be read (container not ready yet, snapshot null or the read throws) nothing is
     * pushed, so every instance keeps what it showed before, and one short retry runs.
     */
    suspend fun updateAll(context: Context, ids: IntArray? = null) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val targets = ids ?: WidgetRenderer.widgetIds(context)
        if (targets.isEmpty()) return

        updateWithRetry(
            targets = targets,
            load = {
                val container = TagApp.containerOrNull ?: TagApp.ensureContainer(context)
                WidgetSnapshotLoader.load(
                    containerReady = container != null,
                    read = { container?.repository?.freshSnapshot() },
                )
            },
            push = { id, snapshot -> pushSnapshot(context, manager, id, snapshot) },
        )
    }

    /**
     * Attempts to load and push once, and on [WidgetSnapshotLoader.Result.Skipped] waits
     * briefly before retrying a single time. Never pushes on Skipped.
     */
    internal suspend fun updateWithRetry(
        targets: IntArray,
        load: suspend () -> WidgetSnapshotLoader.Result,
        push: suspend (id: Int, snapshot: Snapshot) -> Unit,
        pause: suspend () -> Unit = { delay(RETRY_DELAY_MS) },
    ): Boolean {
        if (updateWith(targets, load(), push)) return true
        pause()
        return updateWith(targets, load(), push)
    }

    /**
     * Applies [result] to [targets]. On [WidgetSnapshotLoader.Result.Skipped] nothing is
     * pushed (the previous widget content stays) and false is returned. The "nothing
     * scheduled" state is therefore only drawn for a snapshot that was really read and
     * is empty.
     */
    internal suspend fun updateWith(
        targets: IntArray,
        result: WidgetSnapshotLoader.Result,
        push: suspend (id: Int, snapshot: Snapshot) -> Unit,
    ): Boolean = when (result) {
        is WidgetSnapshotLoader.Result.Skipped -> {
            runCatching {
                Log.w(TAG, "widget update skipped (${result.reason}); keeping previous content", result.error)
            }
            false
        }
        is WidgetSnapshotLoader.Result.Loaded -> {
            for (id in targets) push(id, result.snapshot)
            true
        }
    }

    private suspend fun pushSnapshot(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        snapshot: Snapshot,
    ) {
        val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
        val state = WidgetStateBuilder.build(
            routines = snapshot.routines,
            routineLogs = snapshot.routineLogs,
            date = Dates.today(),
            nowMinutes = nowMinutes,
        )
        val widthDp = withContext(Dispatchers.Default) {
            runCatching {
                manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            }.getOrDefault(0)
        }
        val views = runCatching {
            WidgetRenderer.render(context, state, snapshot.settings, widthDp)
        }.onFailure { Log.w(TAG, "widget render failed for $id", it) }.getOrNull() ?: return
        runCatching { manager.updateAppWidget(id, views) }
            .onFailure { Log.w(TAG, "updateAppWidget failed for $id", it) }
    }
}

/** Reads the data snapshot for the widget, classifying every failure as "skip". */
internal object WidgetSnapshotLoader {

    sealed interface Result {
        data class Loaded(val snapshot: Snapshot) : Result
        data class Skipped(val reason: String, val error: Throwable? = null) : Result
    }

    suspend fun load(containerReady: Boolean, read: suspend () -> Snapshot?): Result {
        if (!containerReady) return Result.Skipped("container not ready")
        return try {
            val snapshot = read()
            if (snapshot == null) Result.Skipped("snapshot null") else Result.Loaded(snapshot)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.Skipped("snapshot read failed", t)
        }
    }
}
