package com.example.idlemining.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GameStateDao {
    @Query("SELECT * FROM saved_game_state WHERE id = 1")
    suspend fun getSavedGame(): SavedGameState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGame(state: SavedGameState)

    @Query("DELETE FROM saved_game_state WHERE id = 1")
    suspend fun clearGame()
}
