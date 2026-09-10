package com.idleshaft.tycoon

import com.idleshaft.tycoon.domain.Economy
import com.idleshaft.tycoon.domain.GameEvent
import com.idleshaft.tycoon.domain.GameIntent
import com.idleshaft.tycoon.domain.GameState
import com.idleshaft.tycoon.domain.OreType
import com.idleshaft.tycoon.game.engine.OfflineEarningsCalculator
import com.idleshaft.tycoon.game.engine.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Economy + simulation kernel tests. These run on the JVM (no Android framework),
 * so [Economy.incomePerSecond]'s clock use is pinned by a boost far in the past.
 */
class EconomyTest {

    private fun automatedState(): GameState {
        // Copper shaft fully automated + refinery + trucks automated.
        var state = GameState.initial(nowMs = 1_000L)
        state = state.copy(
            shafts = state.shafts.map { it.copy(unlocked = true, managerHired = true) },
            refinery = state.refinery.copy(managerHired = true),
            logistics = state.logistics.copy(managerHired = true),
            boostUntilMs = 0L,
        )
        return state
    }

    @Test
    fun `upgrade cost follows the exponential curve`() {
        // 30 * 1.15^0 = 30 (level 1 -> 2), 30 * 1.15^2 = 39.67 -> 40 (level 3 -> 4)
        assertEquals(30.0, Economy.upgradeCost(Economy.UpgradeKind.MINER_SPEED, 1), 1e-9)
        assertEquals(40.0, Economy.upgradeCost(Economy.UpgradeKind.MINER_SPEED, 3), 1e-9)
    }

    @Test
    fun `money formatting uses compact suffixes`() {
        assertEquals("$950", Economy.money(950.0))
        assertEquals("$1.23k", Economy.money(1_234.0))
        assertEquals("$1.23M", Economy.money(1_234_567.0))
        assertEquals("$1.23B", Economy.money(1.234e9))
        assertEquals("$1.23T", Economy.money(1.234e12))
    }

    @Test
    fun `bar values scale with ore rarity and refinery efficiency`() {
        val copper = Economy.barValue(OreType.COPPER, 1)
        val diamond = Economy.barValue(OreType.DIAMOND, 1)
        assertEquals(64.0, diamond / copper, 1e-6)
        assertEquals(1.08, Economy.barValueMultiplier(2), 1e-9)
    }

    @Test
    fun `level one automated pipeline yields about one dollar per second`() {
        val state = automatedState()
        val rate = Economy.incomePerSecond(state)
        // 3 miners * 2 ore / 5.4s cycle = 1.11 ore/s -> 0.111 bar/s * $10 = $1.11/s.
        assertEquals(1.11, rate, 0.05)
    }

    @Test
    fun `income is zero without a refinery or logistics manager`() {
        val state = automatedState().copy(refinery = GameState.initial().refinery)
        assertEquals(0.0, Economy.incomePerSecond(state), 1e-9)
    }

    @Test
    fun `simulation converts ore into cash end to end`() {
        var state = automatedState().copy(cash = 0.0, boostUntilMs = 0L)
        val events = mutableListOf<GameEvent>()
        // 10 simulated minutes at 0.1s ticks.
        repeat(6000) {
            state = Simulation.simulate(state, 0.1f, 1_000L + it * 100L) { events.add(it) }
        }
        // Cash must have flowed from truck sales.
        assertTrue("expected positive cash, got ${state.cash}", state.cash > 1.0)
        assertTrue(events.any { it is GameEvent.CashPopup })
        // And the analytic rate should be within 15% of the simulated one.
        val simulated = state.cash / 600.0
        assertEquals(Economy.incomePerSecond(automatedState()), simulated, simulated * 0.15)
    }

    @Test
    fun `buying an upgrade deducts cash and raises the level`() {
        var state = GameState.initial()
        state = state.copy(cash = 100.0)
        val events = mutableListOf<GameEvent>()
        state = Simulation.reduce(state, GameIntent.BuyShaftUpgrade(0, Economy.UpgradeKind.MINER_SPEED), 1L) { events.add(it) }
        assertEquals(70.0, state.cash, 1e-9)
        assertEquals(2, state.shafts[0].minerSpeedLevel)
    }

    @Test
    fun `purchase fails politely when broke`() {
        var state = GameState.initial().copy(cash = 0.0)
        val events = mutableListOf<GameEvent>()
        state = Simulation.reduce(state, GameIntent.BuyShaftUpgrade(0, Economy.UpgradeKind.MINER_SPEED), 1L) { events.add(it) }
        assertEquals(0.0, state.cash, 1e-9)
        assertEquals(1, state.shafts[0].minerSpeedLevel)
        assertTrue(events.any { it is GameEvent.PurchaseFailed })
    }

    @Test
    fun `offline earnings are capped by warehouse storage`() {
        val state = automatedState().copy(
            lastSavedAtMs = 0L,
            boostUntilMs = 0L,
        )
        // Away 100h with a 4h cap -> pays 4h of base rate.
        val result = OfflineEarningsCalculator.compute(state, nowMs = 100L * 3600_000L)
        assertEquals(4 * 3600L, result.paidSeconds)
        assertEquals(100 * 3600L, result.awaySeconds)
        assertEquals(Economy.incomePerSecond(state) * 4 * 3600, result.amount, 1.0)
    }
}
