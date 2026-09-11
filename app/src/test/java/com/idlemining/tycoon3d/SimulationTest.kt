package com.idlemining.tycoon3d

import com.idlemining.tycoon3d.game.GameEvent
import com.idlemining.tycoon3d.game.GameIntent
import com.idlemining.tycoon3d.game.GameState
import com.idlemining.tycoon3d.game.Simulation
import com.idlemining.tycoon3d.game.WorkerAction
import com.idlemining.tycoon3d.game.WorkerTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationTest {

    private val content = TestContent.build()
    private val nowMs = 1_000_000L

    private fun newState(): GameState = Simulation.initial(content, nowMs)

    private fun tick(state: GameState, seconds: Float, events: MutableList<GameEvent> = mutableListOf()): GameState {
        var s = state
        var left = seconds
        while (left > 0f) {
            val dt = minOf(0.1f, left)
            s = Simulation.simulate(s, dt, events)
            left -= dt
        }
        return s
    }

    // ------------------------------------------------------------- core loop

    @Test
    fun `tap node makes worker walk and mine`() {
        var state = newState()
        state = Simulation.reduce(state, GameIntent.TapNode(TestContent.STONE_NODE), mutableListOf())

        assertEquals(WorkerAction.WALKING, state.worker.action)
        assertTrue(state.worker.target is WorkerTarget.Node)

        // Distance from spawn (0,8) to node (1,6) is ~2.24 m; at 2.4 m/s + reach 1.8
        // arrival happens almost immediately.
        state = tick(state, 1.0f)
        assertEquals(WorkerAction.MINING, state.worker.action)
        assertEquals(TestContent.STONE_NODE, state.worker.miningNodeIndex)

        // Mine the stone vein: 100 hp at 22 dps = ~4.55 s.
        state = tick(state, 6.0f)
        val node = state.nodes[TestContent.STONE_NODE]
        assertTrue(!node.alive)
        assertEquals(3, state.inventory["stone"])
        assertEquals(0f, node.hp, 1e-6f)
        assertTrue(node.respawnRemainingSec > 0f)
    }

    @Test
    fun `node respawns after its timer`() {
        var state = newState()
        state = Simulation.reduce(state, GameIntent.TapNode(TestContent.STONE_NODE), mutableListOf())
        state = tick(state, 6.0f) // break it
        assertTrue(!state.nodes[TestContent.STONE_NODE].alive)

        // Respawn is 15 s.
        state = tick(state, 16.0f)
        val node = state.nodes[TestContent.STONE_NODE]
        assertTrue(node.alive)
        assertEquals(node.maxHp, node.hp, 1e-6f)
    }

    @Test
    fun `stats track mining progress`() {
        var state = newState()
        val events = mutableListOf<GameEvent>()
        state = Simulation.reduce(state, GameIntent.TapNode(TestContent.STONE_NODE), events)
        state = tick(state, 6.0f, events)

        assertEquals(3, state.stats.totalMined)
        assertEquals(1, state.stats.nodesBroken)
        assertTrue(events.any { it is GameEvent.NodeBroken })
    }

    // ------------------------------------------------------------- backpack

    @Test
    fun `backpack capacity clamps drops and warns`() {
        var state = newState().copy(inventory = mapOf("stone" to 11)) // 1 slot free
        val events = mutableListOf<GameEvent>()
        state = Simulation.reduce(state, GameIntent.TapNode(TestContent.STONE_NODE), events)
        state = tick(state, 8.0f, events)

        // 12 capacity: only 1 of the 3 stone fits.
        assertEquals(12, state.inventory["stone"])
        assertTrue(events.any { it is GameEvent.BackpackFull })
    }

    // ------------------------------------------------------------- selling

    @Test
    fun `sell converts inventory to money at the depot`() {
        var state = newState().copy(inventory = mapOf("stone" to 3, "gold" to 1))
        // Put the worker right next to the depot.
        state = state.copy(worker = state.worker.copy(x = 0f, z = 11.5f, target = null, action = WorkerAction.IDLE))

        val events = mutableListOf<GameEvent>()
        state = Simulation.reduce(state, GameIntent.TapDepot, events)

        val sold = events.filterIsInstance<GameEvent.Sold>().firstOrNull()
        assertNotNull(sold)
        assertEquals(51.0, sold!!.amount, 1e-9) // 3*2 + 45
        assertEquals(25.0 + 51.0, state.money, 1e-9)
        assertTrue(state.inventory.isEmpty())
        assertEquals(51.0, state.stats.totalEarned, 1e-9)
    }

    @Test
    fun `far away depot tap just walks`() {
        var state = newState().copy(inventory = mapOf("stone" to 3))
        state = Simulation.reduce(state, GameIntent.TapDepot, mutableListOf())

        assertEquals(WorkerAction.WALKING, state.worker.action)
        assertTrue(state.worker.target is WorkerTarget.Depot)
        // Still holding resources while walking.
        assertEquals(3, state.inventory["stone"])
    }

    // ------------------------------------------------------------- movement

    @Test
    fun `ground taps clamp to the map bounds`() {
        var state = newState()
        state = Simulation.reduce(state, GameIntent.TapGround(999f, 999f), mutableListOf())
        val target = state.worker.target as WorkerTarget.Ground
        assertTrue(target.x <= 22f)
        assertTrue(target.z <= 22f)
    }

    // ------------------------------------------------------------- idle

    @Test
    fun `extractor streams resources over time`() {
        var state = newState().copy(upgrades = mapOf("extractor" to 1))
        state = tick(state, 3.0f) // 0.4/s * 3 = 1.2 -> 1 whole unit
        assertEquals(1, state.inventory["stone"])
        assertTrue(state.idleBuffer["stone"]!! > 0.0)

        state = tick(state, 3.0f) // another 1.2 -> 2 total
        assertEquals(2, state.inventory["stone"])
    }

    @Test
    fun `no extractor means no passive stream`() {
        var state = newState()
        state = tick(state, 10.0f)
        assertTrue(state.inventory.isEmpty())
    }

    // ------------------------------------------------------------- offline

    @Test
    fun `offline report computes capped earnings and merges into inventory`() {
        var state = newState().copy(upgrades = mapOf("extractor" to 1))
        val save = Simulation.toSave(state, nowMs + 1000L)

        // Away for 2 hours (7200 s < 4 h cap): 0.4 * 7200 = 2880 stone.
        val awayMs = nowMs + 1000L + 2 * 3600 * 1000L
        val report = Simulation.computeOffline(content, save, awayMs)
        assertNotNull(report)
        assertEquals(2880, report!!.resources["stone"])
        assertEquals(5760.0, report.totalValue, 1e-9) // 2880 * $2

        val loaded = Simulation.fromSave(content, save, awayMs, report)
        assertEquals(2880, loaded.inventory["stone"])
        assertEquals(report, loaded.offlineReport)
    }

    @Test
    fun `dismiss offline clears the report`() {
        var state = newState().copy(upgrades = mapOf("extractor" to 1))
        val save = Simulation.toSave(state, nowMs + 1000L)
        val report = Simulation.computeOffline(content, save, nowMs + 10_000L)!!
        var loaded = Simulation.fromSave(content, save, nowMs + 10_000L, report)
        assertNotNull(loaded.offlineReport)

        loaded = Simulation.reduce(loaded, GameIntent.DismissOffline, mutableListOf())
        assertNull(loaded.offlineReport)
    }

    // ------------------------------------------------------------- upgrades

    @Test
    fun `buying an upgrade deducts cost and levels up`() {
        var state = newState().copy(money = 100.0)
        val events = mutableListOf<GameEvent>()
        state = Simulation.reduce(state, GameIntent.BuyUpgrade("pickaxe"), events)

        assertEquals(1, state.upgradeLevel("pickaxe"))
        assertEquals(40.0, state.money, 1e-9) // 100 - 60
        assertTrue(events.any { it is GameEvent.UpgradeBought })
    }

    @Test
    fun `insufficient funds refuses the purchase`() {
        var state = newState().copy(money = 10.0)
        val events = mutableListOf<GameEvent>()
        state = Simulation.reduce(state, GameIntent.BuyUpgrade("pickaxe"), events)

        assertEquals(0, state.upgradeLevel("pickaxe"))
        assertEquals(10.0, state.money, 1e-9)
        assertTrue(events.any { it is GameEvent.Popup })
    }

    // ------------------------------------------------------------- lifecycle

    @Test
    fun `save roundtrip restores worker and nodes`() {
        var state = newState()
        state = Simulation.reduce(state, GameIntent.TapNode(TestContent.STONE_NODE), mutableListOf())
        state = tick(state, 2.0f)
        val midMining = state.copy(inventory = mapOf("stone" to 2), money = 77.0)

        val save = Simulation.toSave(midMining, nowMs + 5000L)
        val raw = com.idlemining.tycoon3d.core.save.SaveJson.encodeToString(
            com.idlemining.tycoon3d.core.save.SaveData.serializer(), save,
        )
        val decoded = com.idlemining.tycoon3d.core.save.decodeSave(raw)
        val restored = Simulation.fromSave(content, decoded, nowMs + 6000L, null)

        assertEquals(77.0, restored.money, 1e-9)
        assertEquals(2, restored.inventory["stone"])
        assertEquals(midMining.worker.x, restored.worker.x, 1e-6f)
        // Node hp persisted.
        assertEquals(
            midMining.nodes[TestContent.STONE_NODE].hp,
            restored.nodes[TestContent.STONE_NODE].hp, 1e-4f,
        )
    }

    @Test
    fun `play seconds accumulate`() {
        var state = newState()
        state = tick(state, 2.0f)
        assertEquals(2.0, state.stats.playSeconds, 0.05)
    }
}
