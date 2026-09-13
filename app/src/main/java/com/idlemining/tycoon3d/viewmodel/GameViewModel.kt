package com.idlemining.tycoon3d.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.idlemining.tycoon3d.core.content.AndroidContent
import com.idlemining.tycoon3d.core.content.GameContent
import com.idlemining.tycoon3d.core.save.FileSaveStorage
import com.idlemining.tycoon3d.game.GameEngine
import com.idlemining.tycoon3d.game.GameEvent
import com.idlemining.tycoon3d.game.GameIntent
import com.idlemining.tycoon3d.game.GameState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Thin ViewModel: owns the [GameEngine] (scoped to [viewModelScope]) and forwards
 * UI intents to it — classic MVVM + Unidirectional Data Flow. State flows out
 * through [state]; transient effects flow out through [events].
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = GameEngine(
        content = AndroidContent.load(application),
        storage = FileSaveStorage(File(application.filesDir, "saves")),
        scope = viewModelScope,
        loadOnIO = true,
    )

    /** Immutable, load-time-constant content — safe to read during composition. */
    val content: GameContent = engine.content

    val state: StateFlow<GameState> = engine.state
    val events: SharedFlow<GameEvent> = engine.events

    fun dispatch(intent: GameIntent) = engine.dispatch(intent)

    /** Called from the Activity's onPause — stamps and persists the save immediately. */
    fun onAppPause() = engine.saveNow()

    override fun onCleared() {
        engine.saveNow()
        super.onCleared()
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GameViewModel(app) as T
    }
}
