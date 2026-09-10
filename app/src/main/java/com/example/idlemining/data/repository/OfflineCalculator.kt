package com.example.idlemining.data.repository

object OfflineCalculator {
    fun computeEarnings(savedState: com.example.idlemining.data.local.SavedGameState?, currentMs: Long): Double {
        val saved = savedState ?: return 0.0
        val elapsedMs = currentMs - saved.lastOnlineTimestampMs
        if (elapsedMs <= 0) return 0.0
        val ratePerMs = saved.cash.coerceAtLeast(1.0) * 0.00005
        return (elapsedMs * ratePerMs).coerceAtLeast(0.0)
    }
}
