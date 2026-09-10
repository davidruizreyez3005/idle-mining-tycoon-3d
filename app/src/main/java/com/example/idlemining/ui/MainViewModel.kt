package com.example.idlemining.ui

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.idlemining.data.local.AppDatabase
import com.example.idlemining.data.local.GameStateDao
import com.example.idlemining.data.repository.GameRepositoryImpl
import com.example.idlemining.domain.model.GameState
import com.example.idlemining.game.engine.GameLoopTicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repositoryImpl: GameRepositoryImpl) : ViewModel() {
    private val ticker = GameLoopTicker()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState

    init {
        viewModelScope.launch {
            val saved = repositoryImpl.loadState()
            ticker.startTicking()
            val now = System.currentTimeMillis()
            val elapsed = now - saved.lastOnlineTimestampMs
            if (elapsed > 0) {
                ticker.applyOfflineEarnings(elapsed)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ticker.stop()
        viewModelScope.launch {
            repositoryImpl.saveState(_gameState.value)
        }
    }
}
