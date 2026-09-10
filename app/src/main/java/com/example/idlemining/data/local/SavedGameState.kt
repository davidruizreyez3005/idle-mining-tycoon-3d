package com.example.idlemining.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_game_state")
data class SavedGameState(
    @PrimaryKey val id: Int = 1,
    val cash: Double,
    val gems: Int,
    val lastOnlineTimestampMs: Long,
    val oreCopper: Int = 0,
    val oreIron: Int = 0,
    val oreGold: Int = 0,
    val oreDiamond: Int = 0,
    val upgradeLevelsJson: String = "{}"
)
