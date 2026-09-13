package com.idlemining.tycoon3d.game

import com.idlemining.tycoon3d.core.content.GameContent
import com.idlemining.tycoon3d.core.economy.EconomyRules
import com.idlemining.tycoon3d.core.economy.capacityClamp
import com.idlemining.tycoon3d.core.save.NodeSave
import com.idlemining.tycoon3d.core.save.SaveData
import com.idlemining.tycoon3d.core.save.SaveSchema
import com.idlemining.tycoon3d.core.save.StatsSave
import com.idlemining.tycoon3d.core.save.WorkerSave
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot

/**
 * The pure game kernel. `simulate` advances the world on a fixed tick; `reduce`
 * applies player intents. Both are total functions of (state, input) — no clocks,
 * no Android, no side effects beyond appending [GameEvent]s, which makes the whole
 * game loop unit-testable.
 */
object Simulation {

    // ------------------------------------------------------------ lifecycle

    fun initial(content: GameContent, nowMs: Long): GameState = GameState(
        content = content,
        createdAtMs = nowMs,
        lastSavedAtMs = nowMs,
        money = content.economy.start.money.toDouble(),
        inventory = emptyMap(),
        idleBuffer = emptyMap(),
        upgrades = emptyMap(),
        worker = WorkerState(
            x = content.world.spawn[0],
            z = content.world.spawn[1],
            facing = 180f, // spawn faces north, toward the mine
        ),
        nodes = content.world.nodes.resolveNodes(content),
        stats = StatsState(),
        flags = emptyMap(),
        // New games start the market clock at an epoch-derived phase so the
        // first session already shows varied (not-all-neutral) prices.
        marketTimeSec = nowMs / 1000.0,
    )

    /**
     * Rebuilds state from a save. Away-time earnings are computed by the caller
     * (the engine owns the clock) and merged here; node respawn timers keep
     * counting down while the game is closed.
     */
    fun fromSave(content: GameContent, save: SaveData, nowMs: Long, offline: OfflineReport?): GameState {
        val placements = content.world.nodes.resolveNodes(content)
        val nodes = placements.mapIndexed { i, placed ->
            val saved = save.nodes.getOrNull(i)
            when {
                saved == null -> placed
                saved.respawnRemainingSec > 0f -> placed.copy(
                    hp = 0f,
                    respawnRemainingSec = saved.respawnRemainingSec,
                )
                else -> placed.copy(hp = saved.hp.coerceIn(0f, placed.maxHp))
            }
        }
        val offlineRes = offline?.resources ?: emptyMap()
        val inventory = (save.inventory + offlineRes.mapValues { (save.inventory[it.key] ?: 0) + it.value })
            .filterValues { it > 0 }
        return GameState(
            content = content,
            createdAtMs = save.createdAtMs,
            lastSavedAtMs = save.savedAtMs,
            money = save.money,
            inventory = inventory,
            idleBuffer = emptyMap(),
            upgrades = save.upgrades.filterValues { it > 0 },
            worker = WorkerState(x = save.worker.x, z = save.worker.z, facing = save.worker.facing),
            nodes = nodes,
            stats = StatsState(
                totalMined = save.stats.totalMined,
                totalSoldValue = save.stats.totalSoldValue,
                totalEarned = save.stats.totalEarned,
                playSeconds = save.stats.playSeconds,
                nodesBroken = save.stats.nodesBroken,
            ),
            flags = save.flags,
            // Resume the market on a phase derived from the save stamp.
            marketTimeSec = save.savedAtMs / 1000.0,
            offlineReport = offline,
        )
    }

    fun toSave(state: GameState, nowMs: Long): SaveData = SaveData(
        version = SaveSchema.VERSION,
        createdAtMs = state.createdAtMs,
        savedAtMs = nowMs,
        money = state.money,
        inventory = state.inventory,
        upgrades = state.upgrades,
        worker = WorkerSave(x = state.worker.x, z = state.worker.z, facing = state.worker.facing),
        nodes = state.nodes.map { NodeSave(hp = it.hp, respawnRemainingSec = it.respawnRemainingSec) },
        stats = StatsSave(
            totalMined = state.stats.totalMined,
            totalSoldValue = state.stats.totalSoldValue,
            totalEarned = state.stats.totalEarned,
            playSeconds = state.stats.playSeconds,
            nodesBroken = state.stats.nodesBroken,
        ),
        flags = state.flags,
        offlineUnseen = state.offlineReport != null,
    )

    /**
     * Computes the away-time report. Pure — takes explicit timestamps so tests
     * can dial any elapsed time. Values use the market phase the save resumes
     * on, so the number matches what selling right after collecting yields.
     */
    fun computeOffline(content: GameContent, save: SaveData, nowMs: Long): OfflineReport? {
        // Away-time earnings accrue whenever the game was closed with an extractor
        // running — the report is shown once at load, then cleared from state.
        val elapsed = ((nowMs - save.savedAtMs) / 1000L).coerceAtLeast(0L)
        val earnings = EconomyRules.offlineEarnings(
            content, save.upgrades, elapsed,
        )
        if (earnings.resources.isEmpty()) return null
        val marketTimeSec = save.savedAtMs / 1000.0
        val value = earnings.resources.entries.sumOf { (id, count) ->
            EconomyRules.sellPricePerUnit(content, content.resource(id), save.upgrades, marketTimeSec) * count
        }
        return OfflineReport(
            awaySeconds = earnings.effectiveSeconds,
            resources = earnings.resources,
            totalValue = value,
        )
    }

    // ----------------------------------------------------------------- tick

    fun simulate(
        state: GameState,
        dtSec: Float,
        events: MutableList<GameEvent>,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): GameState {
        if (dtSec <= 0f) return state

        var worker = state.worker
        var nodes = state.nodes
        var inventory = state.inventory
        var money = state.money
        var stats = state.stats
        var idleBuffer = state.idleBuffer

        val speed = EconomyRules.moveSpeedPerSecond(state.content, state.upgrades)
        val dps = EconomyRules.mineDamagePerSecond(state.content, state.upgrades)
        val reach = state.content.economy.worker.reachRadius

        // 1. Worker brain: walk, arrive, mine.
        when (worker.action) {
            WorkerAction.IDLE -> {
                val target = worker.target
                if (target != null) {
                    val (tx, tz, radius) = targetPoint(target, nodes, state.content)
                    val dist = hypot(tx - worker.x, tz - worker.z)
                    worker = if (dist <= radius + 0.05f) {
                        handleArrival(state, worker, nodes, events)
                    } else {
                        worker.copy(action = WorkerAction.WALKING)
                    }
                }
            }

            WorkerAction.WALKING -> {
                val target = worker.target
                if (target == null) {
                    worker = worker.copy(action = WorkerAction.IDLE)
                } else {
                    val (tx, tz, radius) = targetPoint(target, nodes, state.content)
                    val dx = tx - worker.x
                    val dz = tz - worker.z
                    val dist = hypot(dx, dz)
                    val step = speed * dtSec
                    if (dist <= step + radius) {
                        // Stop just short of the target (never inside the rock).
                        val stop = if (target is WorkerTarget.Node) (radius * 0.75f).coerceAtMost(dist) else 0f
                        val arrivedX = if (dist > 0.001f) tx - dx / dist * stop else tx
                        val arrivedZ = if (dist > 0.001f) tz - dz / dist * stop else tz
                        val arrived = worker.copy(x = arrivedX, z = arrivedZ, action = WorkerAction.IDLE)
                        val outcome = arrive(state, arrived, nodes, inventory, money, stats, events)
                        worker = outcome.worker
                        inventory = outcome.inventory
                        money = outcome.money
                        stats = outcome.stats
                    } else {
                        worker = worker.copy(
                            x = worker.x + dx / dist * step,
                            z = worker.z + dz / dist * step,
                            facing = facingOf(dx, dz),
                        )
                    }
                }
            }

            WorkerAction.MINING -> {
                val node = nodes.getOrNull(worker.miningNodeIndex)
                if (node == null || !node.alive) {
                    worker = worker.copy(action = WorkerAction.IDLE, miningNodeIndex = -1)
                } else {
                    val dist = hypot(node.x - worker.x, node.z - worker.z)
                    if (dist > reach * 1.35f) {
                        worker = worker.copy(action = WorkerAction.IDLE, miningNodeIndex = -1)
                    } else {
                        val hpLeft = (node.hp - dps * dtSec).coerceAtLeast(0f)
                        nodes = nodes.toMutableList().also { it[node.index] = node.copy(hp = hpLeft) }
                        if (hpLeft <= 0f) {
                            val luckyRoll = random.nextDouble()
                            val result = breakNode(state, nodes, node.index, inventory, luckyRoll, events)
                            nodes = result.nodes
                            inventory = result.inventory
                            stats = stats.copy(
                                totalMined = stats.totalMined + result.collected,
                                nodesBroken = stats.nodesBroken + 1,
                            )
                            worker = worker.copy(action = WorkerAction.IDLE, miningNodeIndex = -1)
                        }
                    }
                }
            }
        }

        // 2. Node respawn countdown. Skipped — and nothing allocated — when no
        //    vein is regrowing, which is the overwhelmingly common case at 10 Hz.
        var anyRespawn = false
        for (i in nodes.indices) {
            if (nodes[i].respawnRemainingSec > 0f) { anyRespawn = true; break }
        }
        if (anyRespawn) {
            val respawned = nodes.map { n ->
                if (n.respawnRemainingSec > 0f) {
                    val left = (n.respawnRemainingSec - dtSec).coerceAtLeast(0f)
                    if (left <= 0f) n.copy(respawnRemainingSec = 0f, hp = n.maxHp) else n.copy(respawnRemainingSec = left)
                } else n
            }
            if (respawned != nodes) nodes = respawned
        }

        // 3. Idle extraction (the Auto-Extractor machine).
        val extractorLevel = state.upgradeLevel("extractor")
        if (extractorLevel > 0) {
            val rates = EconomyRules.idleRatesPerSecond(state.content, extractorLevel)
            if (rates.isNotEmpty()) {
                val nextBuffer = idleBuffer.toMutableMap()
                val nextInventory = inventory.toMutableMap()
                var invChanged = false
                for ((res, perSecond) in rates) {
                    val acc = (idleBuffer[res] ?: 0.0) + perSecond * dtSec
                    val whole = floor(acc).toInt()
                    if (whole >= 1) {
                        nextInventory[res] = (nextInventory[res] ?: 0) + whole
                        nextBuffer[res] = acc - whole
                        invChanged = true
                    } else {
                        nextBuffer[res] = acc
                    }
                }
                idleBuffer = nextBuffer.toMap()
                if (invChanged) inventory = nextInventory.toMap()
            }
        }

        // 4. Time played + the market clock.
        stats = stats.copy(playSeconds = stats.playSeconds + dtSec)
        val marketTimeSec = state.marketTimeSec + dtSec

        return state.copy(
            money = money,
            inventory = inventory,
            idleBuffer = idleBuffer,
            worker = worker,
            nodes = nodes,
            stats = stats,
            marketTimeSec = marketTimeSec,
        )
    }

    // -------------------------------------------------------------- intents

    fun reduce(state: GameState, intent: GameIntent, events: MutableList<GameEvent>): GameState =
        when (intent) {
            is GameIntent.TapGround -> onTapGround(state, intent)
            is GameIntent.TapNode -> onTapNode(state, intent)
            GameIntent.TapDepot -> onTapDepot(state, events)
            is GameIntent.BuyUpgrade -> buyUpgrade(state, intent.id, events)
            GameIntent.DismissOffline -> state.copy(offlineReport = null)
            GameIntent.MarkHintShown -> state.copy(flags = state.flags + ("hint_shown" to true))
        }

    private fun onTapGround(state: GameState, intent: GameIntent.TapGround): GameState {
        val half = state.content.world.ground.size[0] / 2f - 1.0f
        val x = intent.x.coerceIn(-half, half)
        val z = intent.z.coerceIn(-half, half)
        return state.copy(
            worker = state.worker.copy(
                target = WorkerTarget.Ground(x, z),
                action = WorkerAction.WALKING,
                miningNodeIndex = -1,
            ),
        )
    }

    private fun onTapNode(state: GameState, intent: GameIntent.TapNode): GameState {
        val node = state.nodes.getOrNull(intent.index) ?: return state
        if (!node.alive) return state
        return state.copy(
            worker = state.worker.copy(
                target = WorkerTarget.Node(intent.index),
                action = WorkerAction.WALKING,
                miningNodeIndex = -1,
            ),
        )
    }

    private fun onTapDepot(state: GameState, events: MutableList<GameEvent>): GameState {
        val depot = state.content.world.depot.position
        val dist = hypot(depot[0] - state.worker.x, depot[1] - state.worker.z)
        val radius = state.content.economy.sell.depotRadius
        return if (dist <= radius + 0.4f) {
            // Already standing at the depot — sell right away.
            sellAll(state, events)
        } else {
            state.copy(
                worker = state.worker.copy(
                    target = WorkerTarget.Depot,
                    action = WorkerAction.WALKING,
                    miningNodeIndex = -1,
                ),
            )
        }
    }

    private fun buyUpgrade(state: GameState, id: String, events: MutableList<GameEvent>): GameState {
        val def = state.content.upgrades[id] ?: return state
        val level = state.upgradeLevel(id)
        if (EconomyRules.isMaxed(def, level)) return state
        val cost = EconomyRules.upgradeCost(def, level)
        if (state.money < cost) {
            events.add(GameEvent.Popup("Need $${formatShort(cost - state.money.toLong())} more", PopupKind.WARN))
            return state
        }
        val newLevel = level + 1
        events.add(GameEvent.UpgradeBought(id, newLevel))
        return state.copy(
            money = state.money - cost,
            upgrades = state.upgrades + (id to newLevel),
        )
    }

    // -------------------------------------------------------------- helpers

    /** Resolves where the worker walks to for a target, plus its arrival radius. */
    private fun targetPoint(
        target: WorkerTarget,
        nodes: List<NodeState>,
        content: GameContent,
    ): Triple<Float, Float, Float> = when (target) {
        is WorkerTarget.Ground -> Triple(target.x, target.z, 0.15f)
        is WorkerTarget.Node -> {
            val n = nodes.getOrNull(target.index)
            if (n != null) Triple(n.x, n.z, content.economy.worker.reachRadius) else Triple(0f, 0f, 0.15f)
        }
        WorkerTarget.Depot -> {
            val d = content.world.depot.position
            Triple(d[0], d[1] - 1.6f, content.economy.sell.depotRadius)
        }
    }

    private data class ArrivalOutcome(
        val worker: WorkerState,
        val inventory: Map<String, Int>,
        val money: Double,
        val stats: StatsState,
    )

    /** Full outcome of the worker physically reaching its target point. */
    private fun arrive(
        state: GameState,
        worker: WorkerState,
        nodes: List<NodeState>,
        inventory: Map<String, Int>,
        money: Double,
        stats: StatsState,
        events: MutableList<GameEvent>,
    ): ArrivalOutcome {
        when (val target = worker.target) {
            is WorkerTarget.Node -> {
                val node = nodes.getOrNull(target.index)
                if (node != null && node.alive) {
                    val w = worker.copy(
                        action = WorkerAction.MINING,
                        miningNodeIndex = target.index,
                        facing = facingOf(node.x - worker.x, node.z - worker.z),
                        target = null,
                    )
                    return ArrivalOutcome(w, inventory, money, stats)
                }
                return ArrivalOutcome(worker.copy(target = null), inventory, money, stats)
            }

            WorkerTarget.Depot -> {
                if (inventory.isNotEmpty()) {
                    val sold = sellAll(
                        state.copy(worker = worker, inventory = inventory, money = money, stats = stats),
                        events,
                    )
                    return ArrivalOutcome(worker.copy(target = null), sold.inventory, sold.money, sold.stats)
                }
                return ArrivalOutcome(worker.copy(target = null), inventory, money, stats)
            }

            is WorkerTarget.Ground, null ->
                return ArrivalOutcome(worker.copy(target = null), inventory, money, stats)
        }
    }

    /** Worker-state-only arrival (used from the IDLE engage path). */
    private fun handleArrival(
        state: GameState,
        worker: WorkerState,
        nodes: List<NodeState>,
        events: MutableList<GameEvent>,
    ): WorkerState {
        val outcome = arrive(state, worker, nodes, state.inventory, state.money, state.stats, events)
        return outcome.worker
    }

    private fun facingOf(dx: Float, dz: Float): Float {
        if (dx == 0f && dz == 0f) return 0f
        return Math.toDegrees(atan2(dx, dz).toDouble()).toFloat()
    }

    /** Breaks a node: capacity-clamped drops (doubled on a lucky strike), sets
     * respawn, emits events. [luckyRoll] is a uniform sample in [0, 1) drawn by
     * the caller — keeps this function pure and unit-testable. */
    private data class BreakResult(
        val nodes: List<NodeState>,
        val inventory: Map<String, Int>,
        val collected: Int,
    )

    private fun breakNode(
        state: GameState,
        nodes: List<NodeState>,
        index: Int,
        inventory: Map<String, Int>,
        luckyRoll: Double,
        events: MutableList<GameEvent>,
    ): BreakResult {
        val node = nodes[index]
        val def = state.content.nodeType(node.typeId)
        val capacity = EconomyRules.backpackCapacity(state.content, state.upgrades)
        val carried = inventory.values.sum()

        // Lucky Strikes — Phase 3 ability: a chance for the vein to drop double.
        val lucky = luckyRoll < EconomyRules.luckyStrikeChance(state.content, state.upgrades)
        val yields = if (lucky) def.yields.mapValues { it.value * 2 } else def.yields
        val totalYield = yields.values.sum()

        var free = (capacity - carried).coerceAtLeast(0)
        val collected = mutableMapOf<String, Int>()
        val nextInventory = inventory.toMutableMap()
        var totalCollected = 0
        for ((res, count) in yields) {
            val take = capacityClamp(0, count, free)
            if (take > 0) {
                collected[res] = (collected[res] ?: 0) + take
                nextInventory[res] = (nextInventory[res] ?: 0) + take
                free -= take
                totalCollected += take
            }
        }
        if (totalCollected < totalYield) {
            events.add(GameEvent.BackpackFull)
            events.add(GameEvent.Popup("Backpack full — sell at the depot!", PopupKind.WARN))
        }
        if (lucky && totalCollected > 0) {
            events.add(GameEvent.Popup("Lucky strike! Double loot", PopupKind.INFO))
        }

        val respawned = node.copy(hp = 0f, respawnRemainingSec = def.respawnSeconds.toFloat())
        val nextNodes = nodes.toMutableList().also { it[index] = respawned }
        events.add(GameEvent.NodeBroken(index, collected))

        return BreakResult(nextNodes, nextInventory.toMap(), totalCollected)
    }

    /**
     * Sells the whole inventory — invoked on depot arrival and on the SELL button
     * when the worker already stands at the depot.
     */
    fun sellAll(state: GameState, events: MutableList<GameEvent>): GameState {
        if (state.inventory.isEmpty()) return state
        val value = EconomyRules.inventoryValue(
            state.content, state.inventory, state.upgrades, state.marketTimeSec,
        )
        val units = state.totalCarried
        events.add(GameEvent.Sold(value, units))
        return state.copy(
            money = state.money + value,
            inventory = emptyMap(),
            idleBuffer = emptyMap(),
            stats = state.stats.copy(
                totalSoldValue = state.stats.totalSoldValue + value,
                totalEarned = state.stats.totalEarned + value,
            ),
        )
    }

    private fun formatShort(v: Long): String = if (v >= 1000) "${v / 1000}k" else v.toString()
}
