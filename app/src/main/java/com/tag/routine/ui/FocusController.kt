package com.tag.routine.ui

import com.tag.routine.data.ActiveFocusRun
import com.tag.routine.data.AppConstants
import com.tag.routine.data.FocusPhase
import com.tag.routine.data.RoutineEntity
import com.tag.routine.data.TagRepository
import com.tag.routine.notifications.FocusTimerAlarms
import com.tag.routine.notifications.FocusTimerRuntime
import com.tag.routine.notifications.FocusTimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Live state of the focus timer, as rendered on the Focus screen. */
data class FocusState(
    val active: Boolean = false,
    val routineId: String? = null,
    val routineTitle: String? = null,
    val plannedMinutes: Int = AppConstants.DEFAULT_FOCUS_MINUTES,
    /** Seconds already elapsed in the current run. */
    val elapsedSeconds: Int = 0,
    val running: Boolean = false,
    val phase: FocusPhase = FocusPhase.FOCUS,
    val cycleCount: Int = 1,
) {
    val isBreak: Boolean get() = phase.isBreak
    val totalSeconds: Int get() = plannedMinutes * 60
    val remainingSeconds: Int get() = (totalSeconds - elapsedSeconds).coerceAtLeast(0)
    val progress: Float
        get() = if (totalSeconds == 0) 0f else (elapsedSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
}

/**
 * Presents the focus timer to [AppViewModel].
 *
 * The timer is *not* owned here. It lives in a foreground service
 * ([FocusTimerService]) with its state persisted on disk, so closing the app no longer
 * stops it. This class only mirrors that run into UI state and forwards the user's
 * actions; elapsed time is recomputed from the run's wall-clock timestamps on every
 * tick, so a frozen process never falls behind.
 */
class FocusController(
    private val repository: TagRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    private var tickJob: Job? = null
    private val actionLock = Mutex()

    init {
        // The run is the source of truth: whenever the service starts, pauses, resumes
        // or settles it, the UI follows without polling.
        FocusTimerRuntime.run
            .onEach { run ->
                if (run == null) {
                    stopTicking()
                    _state.value = FocusState()
                } else {
                    _state.value = run.toUiState()
                    startTicking()
                }
            }
            .launchIn(scope)

        // Pick up a timer that was left running while the app was closed. If it is still
        // running, the service is brought back up too, so the ongoing notification and
        // the completion alarm are restored along with the timer.
        scope.launch {
            val restored = repository.currentActiveFocusRun()
            FocusTimerRuntime.restore(restored)
            if (restored != null && restored.running) {
                withAppContext { ctx -> FocusTimerAlarms.arm(ctx, restored) }
                startTimerService()
            }
        }
    }

    /**
     * Starts a focus run for [routine] lasting [minutes]. Any existing run is settled
     * cleanly before starting the replacement.
     *
     * Runs on the application scope, not the view-model scope: the user can tap Start and
     * immediately swipe the app away, and the run must still be persisted and handed to
     * the service rather than being cancelled along with the screen.
     */
    fun start(routine: RoutineEntity?, minutes: Int) = switchTo(routine, minutes)

    /**
     * Cancels any pending completion alarm, settles the active run if one exists, and starts
     * and persists the new focus session for [routine].
     */
    fun switchTo(routine: RoutineEntity?, minutes: Int) {
        val clamped = AppConstants.clampFocusMinutes(minutes)
        appScope().launch {
            actionLock.withLock {
                val next = FocusTimerRuntime.switchTo(
                    routineId = routine?.id,
                    routineTitle = routine?.title,
                    minutes = clamped,
                    onBeforeSettle = {
                        withAppContext { ctx -> FocusTimerAlarms.cancel(ctx) }
                    },
                )
                withAppContext { ctx -> FocusTimerAlarms.arm(ctx, next) }
                startTimerService()
            }
        }
    }

    fun pause() {
        appScope().launch {
            actionLock.withLock {
                FocusTimerRuntime.pause()
                withAppContext { ctx -> FocusTimerAlarms.cancel(ctx) }
            }
        }
    }

    fun resume() {
        appScope().launch {
            actionLock.withLock {
                FocusTimerRuntime.resume()
                withAppContext { ctx -> FocusTimerRuntime.run.value?.let { FocusTimerAlarms.arm(ctx, it) } }
                startTimerService()
            }
        }
    }

    /** Skips a running or paused break and leaves the next focus run paused. */
    fun skipBreak() {
        appScope().launch {
            actionLock.withLock {
                if (FocusTimerRuntime.skipBreak()) {
                    withAppContext { ctx -> FocusTimerAlarms.cancel(ctx) }
                    stopTimerService()
                }
            }
        }
    }

    /**
     * Ends the run early. The service settles it, so the session is recorded exactly
     * once whether the user stops from the app or from the notification.
     */
    fun stop() {
        appScope().launch {
            actionLock.withLock {
                withAppContext { ctx -> FocusTimerAlarms.cancel(ctx) }
                stopTimerService()
                FocusTimerRuntime.finish()
            }
        }
    }

    /**
     * Finishing is the same operation as stopping: a run that reached its planned length
     * is stored as completed and a shorter one as an abandoned attempt, which the
     * history already distinguishes.
     */
    fun finish() = stop()

    /**
     * The scope used for timer mutations.
     *
     * Prefers the application scope so a run outlives the screen that started it, and
     * falls back to the view-model scope only if the container is not ready yet.
     */
    private fun appScope(): CoroutineScope =
        com.tag.routine.TagApp.containerOrNull?.appScope ?: scope

    private fun startTimerService() {
        withAppContext { ctx -> FocusTimerService.start(ctx) }
    }

    private fun stopTimerService() {
        withAppContext { ctx -> FocusTimerService.stop(ctx) }
    }

    /**
     * Runs [block] with the application context when the container exists.
     *
     * The context is fetched through [com.tag.routine.TagApp] rather than held as a field
     * so that a controller created before the container is ready simply skips the call
     * instead of crashing.
     */
    private inline fun withAppContext(block: (android.content.Context) -> Unit) {
        val context = com.tag.routine.TagApp.containerOrNull?.appContext ?: return
        block(context)
    }

    private fun ActiveFocusRun.toUiState(): FocusState = FocusState(
        active = true,
        routineId = routineId,
        routineTitle = routineTitle,
        plannedMinutes = plannedMinutes,
        elapsedSeconds = elapsedSeconds(),
        running = running,
        phase = phase,
        cycleCount = cycleCount,
    )

    /**
     * Re-derives the displayed time once a second while a run exists.
     *
     * The service also watches the run, but the UI must keep counting on its own: the
     * service's notification updates are throttled by the platform and it may be paused
     * by the OS at any moment, whereas the screen is only visible when the app is in
     * front. Completion is funnelled through [FocusTimerRuntime.advance] so that a run
     * still finishes correctly even if the service is not running.
     */
    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (true) {
                val run = FocusTimerRuntime.run.value
                if (run == null) return@launch
                _state.value = run.toUiState()
                val transition = FocusTimerRuntime.advanceWithTransition()
                if (transition.completed) {
                    withAppContext { ctx -> FocusTimerAlarms.cancel(ctx) }
                    val next = transition.next
                    if (next != null && next.running) {
                        withAppContext { ctx -> FocusTimerAlarms.arm(ctx, next) }
                        startTimerService()
                    } else {
                        // A finished/skipped break leaves a paused focus run; nothing
                        // should keep the foreground service or its alarm alive.
                        stopTimerService()
                    }
                }
                delay(1_000)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }
}
