package com.example.idlemining.domain.model

data class Upgrade(
    val id: String,
    val name: String,
    val description: String,
    val category: UpgradeCategory,
    val baseCost: Double,
    val level: Int = 1
)

enum class UpgradeCategory {
    MINER, SHAFT, CART, REFINERY, MANAGER
}
