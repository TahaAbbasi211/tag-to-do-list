package com.tag.routine.widget

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fire-and-forget widget refresh, for call sites that just changed app data.
 *
 * The refresh runs on [Dispatchers.IO] and swallows failures: a widget that cannot be
 * redrawn must never break the action the user actually asked for (saving a routine,
 * restoring a backup, ticking an item).
 */
object WidgetRefresh {

    private const val TAG = "WidgetRefresh"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Redraws every placed widget instance. Does nothing when none are placed. */
    fun request(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                WidgetUpdater.updateAll(appContext)
            } catch (t: Throwable) {
                Log.w(TAG, "widget refresh failed", t)
            }
        }
    }
}
