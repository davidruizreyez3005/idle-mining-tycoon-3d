package com.idleshaft.tycoon.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.idleshaft.tycoon.domain.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val Context.gameDataStore by preferencesDataStore(name = "idle_shaft_tycoon_save")

/**
 * Persistence layer: the whole [GameState] is serialized to JSON and stored in a single
 * Preferences DataStore entry. DataStore writes are transactional and crash-safe, and the
 * tolerant [Json] decoder falls back to a fresh save when the format evolves.
 */
class GameRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true   // forward compatibility with future fields
        encodeDefaults = true      // stable diffs between save versions
    }

    private val key = stringPreferencesKey("game_state_json")

    /** Loads the save; returns `null` when no valid save exists. */
    suspend fun load(): GameState? = withContext(Dispatchers.IO) {
        val raw = context.gameDataStore.data.first()[key] ?: return@withContext null
        runCatching { json.decodeFromString<GameState>(raw) }.getOrNull()
    }

    /** Persists the state and stamps [GameState.lastSavedAtMs] with the save time. */
    suspend fun save(state: GameState): Unit = withContext(Dispatchers.IO) {
        val stamped = state.copy(lastSavedAtMs = System.currentTimeMillis())
        context.gameDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(GameState.serializer(), stamped)
        }
    }
}
