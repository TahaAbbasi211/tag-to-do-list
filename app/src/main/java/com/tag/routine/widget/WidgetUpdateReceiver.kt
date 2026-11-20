package com.tag.routine.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the widget correct when the day or the clock changes underneath it.
 *
 * The widget shows "today", so it goes stale at midnight, when the user travels into a
 * different time zone, and after a reboot or an app update (both of which can leave the
 * launcher holding an old RemoteViews). None of these need a permission: they are system
 * broadcasts the widget receiver already listens for.
 *
 * Work runs behind [goAsync] on [Dispatchers.IO] because it reads the database, and every
 * failure is swallowed so a broadcast can never crash the app.
 */
class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            -> Unit
            else -> return
        }

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Only redraw instances that actually exist; a device with no widget
                // placed should not pay for a database read on every boot.
                val ids = AppWidgetManager.getInstance(appContext)
                    ?.getAppWidgetIds(ComponentName(appContext, TodayWidgetProvider::class.java))
                    ?: IntArray(0)
                if (ids.isNotEmpty()) {
                    WidgetUpdater.updateAll(appContext, ids)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "widget refresh on ${intent.action} failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "WidgetUpdateReceiver"
    }
}
