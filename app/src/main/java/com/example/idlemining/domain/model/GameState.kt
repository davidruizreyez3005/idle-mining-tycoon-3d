package com.example.idlemining.domain.model

data class GameState(
    val cash: Double = 0.0,
    val gems: Int = 0,
    val oreInventory: Map<OreType, Int> = emptyMap(),
    val upgrades: Map<String, Upgrade> = emptyMap(),
    val lastOnlineTimestampMs: Long = System.currentTimeMillis(),
    val netIncomePerSec: Double = 0.0
)
