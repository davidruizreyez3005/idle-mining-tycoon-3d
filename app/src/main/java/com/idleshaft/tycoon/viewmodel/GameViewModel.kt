package com.idleshaft.tycoon.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.idleshaft.tycoon.data.GameRepository
import com.idleshaft.tycoon.domain.GameEvent
import com.idleshaft.tycoon.domain.GameIntent
import com.idleshaft.tycoon.domain.GameState
import com.idleshaft.tycoon.game.engine.GameEngine
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin ViewModel: owns the [GameEngine] (scoped to [viewModelScope]) and forwards
 * UI intents to it — classic MVVM + Unidirectional Data Flow. All state flows out
 * through [state]; all transient effects flow out through [events].
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = GameEngine(
        repository = GameRepository(application),
        scope = viewModelScope,
    )

    val state: StateFlow<GameState> = engine.state
    val events: SharedFlow<GameEvent> = engine.events

    fun dispatch(intent: GameIntent) = engine.dispatch(intent)

    /** Called from the Activity's onPause — stamps and persists the save immediately. */
    fun onAppPause() = engine.saveNow()

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GameViewModel(app) as T
    }
}
