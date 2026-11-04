package com.tag.routine.ui

import com.tag.routine.data.AppConstants
import com.tag.routine.data.RoutineEntity
import com.tag.routine.data.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Live state of the focus timer. */
data class FocusState(
    val routineId: String? = null,
    val routineTitle: String? = null,
    val plannedMinutes: Int = AppConstants.DEFAULT_FOCUS_MINUTES,
    /** Seconds already elapsed in the current run. */
    val elapsedSeconds: Int = 0,
    val running: Boolean = false,
) {
    val totalSeconds: Int get() = plannedMinutes * 60
    val remainingSeconds: Int get() = (totalSeconds - elapsedSeconds).coerceAtLeast(0)
    val progress: Float
        get() = if (totalSeconds == 0) 0f else (elapsedSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
}

/**
 * Owns the Pomodoro-style focus timer. Kept separate from [AppViewModel] so the
 * ticking logic and its persistence can be reasoned about on their own.
 */
class FocusController(
    private val repository: TagRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    private var tickJob: Job? = null

    /** Starts (or restarts) a focus run for [routine] lasting [minutes]. */
    fun start(routine: RoutineEntity, minutes: Int) {
        tickJob?.cancel()
        _state.value = FocusState(
            routineId = routine.id,
            routineTitle = routine.title,
            plannedMinutes = minutes,
            elapsedSeconds = 0,
            running = true,
        )
        tick()
    }

    fun pause() {
        tickJob?.cancel()
        _state.value = _state.value.copy(running = false)
    }

    fun resume() {
        if (_state.value.routineId == null || _state.value.running) return
        _state.value = _state.value.copy(running = true)
        tick()
    }

    /** Abandons the run without recording a session. */
    fun stop() {
        tickJob?.cancel()
        _state.value = FocusState()
    }

    /**
     * Ends the run. A run that reached a minute is recorded; reaching the planned
     * length marks it completed, a shorter one is stored as an abandoned attempt so
     * the stats stay honest.
     */
    fun finish() {
        val current = _state.value
        tickJob?.cancel()
        val id = current.routineId
        if (id == null || current.elapsedSeconds < AppConstants.MIN_FOCUS_SECONDS) {
            _state.value = FocusState()
            return
        }
        val startedAt = System.currentTimeMillis() - current.elapsedSeconds * 1000L
        val completed = current.elapsedSeconds >= current.totalSeconds
        scope.launch {
            repository.recordFocusSession(
                routineId = id,
                startedAt = startedAt,
                endedAt = System.currentTimeMillis(),
                plannedMinutes = current.plannedMinutes,
                completed = completed,
            )
        }
        _state.value = FocusState()
    }

    private fun tick() {
        tickJob = scope.launch {
            while (true) {
                delay(1_000)
                val current = _state.value
                if (!current.running || current.routineId == null) return@launch
                val next = current.elapsedSeconds + 1
                if (next >= current.totalSeconds) {
                    _state.value = current.copy(elapsedSeconds = current.totalSeconds, running = false)
                    finish()
                    return@launch
                }
                _state.value = current.copy(elapsedSeconds = next)
            }
        }
    }
}
