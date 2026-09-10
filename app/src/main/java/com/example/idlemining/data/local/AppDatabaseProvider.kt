package com.example.idlemining.data.local

import android.content.Context
import androidx.room.Room

object AppDatabase {
    @Volatile
    private var instance: com.example.idlemining.data.local.AppDatabase? = null

    fun getDatabase(context: Context): com.example.idlemining.data.local.AppDatabase {
        return instance ?: synchronized(this) {
            val db = Room.databaseBuilder(
                context.applicationContext,
                com.example.idlemining.data.local.AppDatabase::class.java,
                "idle_mining_db"
            ).build()
            instance = db
            db
        }
    }
}
