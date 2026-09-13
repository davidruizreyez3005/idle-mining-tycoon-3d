package com.idlemining.tycoon3d.game

import com.idlemining.tycoon3d.core.content.GameContent
import com.idlemining.tycoon3d.core.save.SaveStorage
import com.idlemining.tycoon3d.core.save.decodeSave
import com.idlemining.tycoon3d.core.save.SaveJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Owns [GameState] inside a StateFlow and advances it on a fixed 100 ms tick
 * (wall-clock compensated). Intents are reduced through the pure [Simulation]
 * kernel — the engine itself does no game math. Autosaves every 5 seconds and
 * on pause; away-time earnings are computed once at boot.
 *
 * `content` is exposed as a plain immutable val so the 3D scene can take it
 * as a Compose parameter without subscribing to the 10 Hz state flow.
 */
class GameEngine(
    val content: GameContent,
    private val storage: SaveStorage,
    private val scope: CoroutineScope,
    private val loadOnIO: Boolean = false,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val TICK_INTERVAL_MS = 100L
        const val AUTOSAVE_INTERVAL_MS = 5_000L
    }

    private val _state = MutableStateFlow(Simulation.initial(content, nowMs()))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var lastTickNanos = System.nanoTime()
    private var isLoaded = false

    init {
        scope.launch {
            val savedRaw = if (loadOnIO) {
                withContext(kotlinx.coroutines.Dispatchers.IO) { storage.readRaw() }
            } else {
                storage.readRaw()
            }
            val now = nowMs()
            val loaded: GameState = if (savedRaw != null) {
                runCatching { decodeSave(savedRaw) }.getOrNull()?.let { save ->
                    val offline = Simulation.computeOffline(content, save, now)
                    Simulation.fromSave(content, save, now, offline)
                } ?: Simulation.initial(content, now)
            } else {
                Simulation.initial(content, now)
            }
            _state.value = loaded
            isLoaded = true
        }
        scope.launch { tickLoop() }
        scope.launch { autosaveLoop() }
    }

    private suspend fun tickLoop() {
        while (scope.isActive) {
            delay(TICK_INTERVAL_MS)
            if (!isLoaded) continue
            val now = System.nanoTime()
            val dtSec = ((now - lastTickNanos) / 1_000_000_000f).coerceIn(0f, 0.25f)
            lastTickNanos = now
            val events = mutableListOf<GameEvent>()
            _state.value = Simulation.simulate(_state.value, dtSec, events)
            events.forEach { _events.tryEmit(it) }
        }
    }

    private suspend fun autosaveLoop() {
        while (scope.isActive) {
            delay(AUTOSAVE_INTERVAL_MS)
            if (isLoaded) saveNow()
        }
    }

    /** Entry point for every UI action (Unidirectional Data Flow). */
    fun dispatch(intent: GameIntent) {
        if (!isLoaded) return
        val events = mutableListOf<GameEvent>()
        _state.value = Simulation.reduce(_state.value, intent, events)
        events.forEach { _events.tryEmit(it) }
    }

    /**
     * Immediately stamps and persists the state (called from onPause / onCleared).
     *
     * The JSON encode + file write run on [Dispatchers.Default] — the
     * pre-tuning build encoded the save on the *main* thread every 5 seconds,
     * which surfaced as a periodic frame hitch during play.
     */
    fun saveNow() {
        if (!isLoaded) return
        val snapshot = _state.value
        val now = nowMs()
        val scope = this.scope
        scope.launch(NonCancellable + Dispatchers.Default) {
            val raw = SaveJson.encodeToString(
                com.idlemining.tycoon3d.core.save.SaveData.serializer(),
                Simulation.toSave(snapshot, now),
            )
            if (loadOnIO) {
                withContext(kotlinx.coroutines.Dispatchers.IO) { storage.writeRaw(raw) }
            } else {
                storage.writeRaw(raw)
            }
        }
    }

    /** Seconds remaining on the active 2x boost, or 0 (reserved for a later phase). */
    fun boostSecondsRemaining(now: Long = nowMs()): Long = max(0L, 0L)
}
