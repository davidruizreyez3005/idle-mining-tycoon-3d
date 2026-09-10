package com.example.idlemining.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SavedGameState::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameStateDao(): GameStateDao
}
