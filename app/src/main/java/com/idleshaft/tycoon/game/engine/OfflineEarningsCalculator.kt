package com.idleshaft.tycoon.game.engine

import com.idleshaft.tycoon.domain.Economy
import com.idleshaft.tycoon.domain.GameState

/**
 * Computes what the fully-automated part of the operation earned while the app
 * was closed, based on the wall-clock gap between [GameState.lastSavedAtMs] and
 * "now". The effective window is capped by the player's offline storage level.
 */
object OfflineEarningsCalculator {

    data class Result(
        /** Cash earned while away (already capped). */
        val amount: Double,
        /** Real seconds the player was away (uncapped, for display). */
        val awaySeconds: Long,
        /** Seconds of earnings that were actually paid out (capped). */
        val paidSeconds: Long,
        /** Applied income rate, for the dialog breakdown. */
        val ratePerSecond: Double,
    )

    fun compute(state: GameState, nowMs: Long): Result {
        val awayMs = (nowMs - state.lastSavedAtMs).coerceAtLeast(0L)
        val awaySeconds = awayMs / 1000
        val capSeconds = state.logistics.offlineCapHours * 3600L
        val paidSeconds = awaySeconds.coerceAtMost(capSeconds)
        val rate = Economy.incomePerSecond(state)
        // The boost may have covered part of the offline window, but for fairness the
        // offline engine pays the *base* rate only — the dialog says so explicitly.
        val baseRate = rate / (if (System.currentTimeMillis() < state.boostUntilMs) Economy.BOOST_MULTIPLIER else 1.0)
        return Result(
            amount = baseRate * paidSeconds,
            awaySeconds = awaySeconds,
            paidSeconds = paidSeconds,
            ratePerSecond = baseRate,
        )
    }
}
