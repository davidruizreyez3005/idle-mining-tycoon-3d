package com.example.idlemining.data.repository

import com.example.idlemining.data.local.GameStateDao
import com.example.idlemining.data.local.SavedGameState
import com.example.idlemining.domain.model.GameState

interface GameRepository {
    suspend fun loadState(): GameState
    suspend fun saveState(state: GameState)
}

class GameRepositoryImpl(private val dao: GameStateDao) : GameRepository {
    override suspend fun loadState(): GameState {
        val saved = dao.getSavedGame()
        return if (saved != null) {
            GameState(
                cash = saved.cash,
                gems = saved.gems,
                oreInventory = mapOf(),
                upgrades = emptyMap(),
                lastOnlineTimestampMs = saved.lastOnlineTimestampMs,
                netIncomePerSec = saved.cash * 0.1
            )
        } else {
            GameState(lastOnlineTimestampMs = System.currentTimeMillis())
        }
    }

    override suspend fun saveState(state: GameState) {
        dao.saveGame(
            SavedGameState(
                id = 1,
                cash = state.cash,
                gems = state.gems,
                lastOnlineTimestampMs = System.currentTimeMillis()
            )
        )
    }
}
