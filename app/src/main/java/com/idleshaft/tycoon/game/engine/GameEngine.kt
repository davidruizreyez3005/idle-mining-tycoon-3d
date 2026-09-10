package com.idleshaft.tycoon.game.engine

import com.idleshaft.tycoon.data.GameRepository
import com.idleshaft.tycoon.domain.GameEvent
import com.idleshaft.tycoon.domain.GameIntent
import com.idleshaft.tycoon.domain.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * The game engine: owns [GameState] inside a [StateFlow] and advances it on a fixed
 * 100ms simulation tick (coroutine-driven, wall-clock compensated). Player intents are
 * reduced through the pure [Simulation] kernel — the engine itself does no game math.
 */
class GameEngine(
    private val repository: GameRepository,
    private val scope: CoroutineScope,
) {
    companion object {
        /** Simulation frequency in milliseconds. */
        const val TICK_INTERVAL_MS = 100L

        /** Autosave cadence. */
        const val AUTOSAVE_INTERVAL_MS = 5_000L
    }

    private val _state = MutableStateFlow(GameState.initial())
    val state: StateFlow<GameState> = _state.asStateFlow()

    /** One-shot presentation events (popups, milestones, offline report...). */
    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var lastTickNanos = System.nanoTime()
    private var isLoaded = false

    init {
        // Boot: load save, apply offline earnings, then start the tick + autosave loops.
        scope.launch {
            val saved = repository.load()
            val now = System.currentTimeMillis()
            val fresh = saved ?: GameState.initial(now)
            val result = OfflineEarningsCalculator.compute(fresh, now)
            val loaded = if (saved != null && result.amount > 0.0) {
                fresh.copy(
                    cash = fresh.cash + result.amount,
                    totalEarned = fresh.totalEarned + result.amount,
                    pendingOfflineEarnings = result.amount,
                    pendingOfflineSeconds = result.awaySeconds,
                )
            } else fresh
            _state.value = loaded
            isLoaded = true
            if (result.amount > 0.0) {
                _events.tryEmit(GameEvent.OfflineEarnings(result.amount, result.awaySeconds))
            }
        }
        scope.launch { tickLoop() }
        scope.launch { autosaveLoop() }
    }

    private suspend fun tickLoop() {
        while (scope.isActive) {
            delay(TICK_INTERVAL_MS)
            if (!isLoaded) continue
            val now = System.nanoTime()
            val dtSeconds = (now - lastTickNanos) / 1_000_000_000f
            lastTickNanos = now
            _state.value = Simulation.simulate(_state.value, dtSeconds, System.currentTimeMillis()) { event ->
                _events.tryEmit(event)
            }
        }
    }

    private suspend fun autosaveLoop() {
        while (scope.isActive) {
            delay(AUTOSAVE_INTERVAL_MS)
            if (isLoaded) repository.save(_state.value)
        }
    }

    /** Entry point for every UI action (Unidirectional Data Flow). */
    fun dispatch(intent: GameIntent) {
        if (!isLoaded && intent != GameIntent.DismissOfflineEarnings) return
        _state.value = Simulation.reduce(_state.value, intent, System.currentTimeMillis()) { event ->
            _events.tryEmit(event)
        }
    }

    /** Immediately stamps and persists the state (called from onPause / onCleared). */
    fun saveNow() {
        if (!isLoaded) return
        val snapshot = _state.value
        val scope = this.scope
        scope.launch(kotlinx.coroutines.NonCancellable) {
            repository.save(snapshot)
        }
    }

    /** Seconds remaining on the active 2x boost, or 0. */
    fun boostSecondsRemaining(nowMs: Long = System.currentTimeMillis()): Long =
        max(0L, (state.value.boostUntilMs - nowMs) / 1000L)
}
