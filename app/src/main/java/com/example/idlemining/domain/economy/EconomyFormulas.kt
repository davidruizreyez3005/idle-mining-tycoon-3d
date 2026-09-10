package com.example.idlemining.domain.economy

object EconomyFormulas {
    fun upgradeCost(baseCost: Double, level: Int): Double {
        return baseCost * Math.pow(1.15, level.toDouble())
    }

    fun oreValue(oreType: String): Double {
        return when (oreType) {
            "COPPER" -> 1.0
            "IRON" -> 5.0
            "GOLD" -> 25.0
            "DIAMOND" -> 150.0
            else -> 1.0
        }
    }
}
