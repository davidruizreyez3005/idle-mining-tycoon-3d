package com.example.idlemining.game.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.idlemining.domain.model.GameState

class GameLoopTicker(private val initialState: GameState = GameState()) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<GameState> = _state

    fun startTicking() {
        scope.launch {
            while (true) {
                delay(100L)
                tick()
            }
        }
    }

    private fun tick() {
        val current = _state.value
        val newCash = current.cash + current.netIncomePerSec * 0.1
        val newOre = current.oreInventory.toMutableMap()
        newOre.forEach { (ore, count) -> newOre[ore] = count + 1 }
        _state.value = current.copy(
            cash = newCash,
            oreInventory = newOre,
            netIncomePerSec = current.netIncomePerSec + 0.05
        )
    }

    fun applyOfflineEarnings(elapsedMs: Long) {
        val earnings = (elapsedMs / 1000.0) * _state.value.netIncomePerSec
        val current = _state.value
        _state.value = current.copy(cash = current.cash + earnings)
    }

    fun stop() {
        scope.launch { } // coroutine scope cancellation handled by lifecycle
    }
}
