package com.tag.routine.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.tag.routine.TagApp
import com.tag.routine.data.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Handles one-tap item completion broadcasts from the wide (4x2) home-screen widget.
 *
 * Uses [goAsync] on [Dispatchers.IO] and serializes taps under [actionMutex] so rapid double
 * taps see the updated database state, call [com.tag.routine.data.TagRepository.toggleRoutine]
 * at most once, dismiss any shown reminder notification for that routine, and refresh the widget.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_DONE) return

        val routineId = intent.getStringExtra(EXTRA_ROUTINE_ID)
        val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, WidgetActionDecider.MISSING_EPOCH_DAY)
        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                actionMutex.withLock {
                    val container = TagApp.containerOrNull ?: TagApp.ensureContainer(appContext)
                        ?: return@withLock
                    val snapshot = container.repository.freshSnapshot()
                    val today = Dates.today()
                    val decision = WidgetActionDecider.decide(
                        routineId = routineId,
                        actionEpochDay = epochDay,
                        today = today,
                        routines = snapshot.routines,
                        routineLogs = snapshot.routineLogs,
                    )
                    if (decision is WidgetActionDecision.MarkDone) {
                        container.repository.toggleRoutine(decision.routineId, decision.date, true)
                        runCatching {
                            NotificationManagerCompat.from(appContext).cancel(decision.routineId.hashCode())
                        }
                        if (snapshot.settings.hapticsEnabled) {
                            performClickHaptic(appContext)
                        }
                    }
                    WidgetUpdater.updateAll(appContext)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "widget mark-done failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Triggers a short click vibration using the app's pre-existing `VIBRATE` manifest
     * permission (no new permission added). Any device/OS denial is silently ignored.
     */
    private fun performClickHaptic(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return
            if (!vibrator.hasVibrator()) return

            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            } else {
                VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
                vibrator.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect)
            }
        }
    }

    companion object {
        const val ACTION_MARK_DONE = "com.tag.routine.widget.ACTION_MARK_DONE"
        const val EXTRA_ROUTINE_ID = "com.tag.routine.widget.EXTRA_ROUTINE_ID"
        const val EXTRA_EPOCH_DAY = "com.tag.routine.widget.EXTRA_EPOCH_DAY"
        private const val TAG = "WidgetActionReceiver"
        private val actionMutex = Mutex()
    }
}
