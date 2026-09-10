package com.example.idlemining

import android.app.Application
import com.example.idlemining.data.local.AppDatabase

class IdleMiningApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Database initialization for Room
        AppDatabase.getDatabase(this)
    }
}
