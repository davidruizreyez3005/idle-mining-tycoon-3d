package com.idleshaft.tycoon.game.engine

import com.idleshaft.tycoon.domain.Cart
import com.idleshaft.tycoon.domain.CartPhase
import com.idleshaft.tycoon.domain.Economy
import com.idleshaft.tycoon.domain.GameEvent
import com.idleshaft.tycoon.domain.GameIntent
import com.idleshaft.tycoon.domain.GameState
import com.idleshaft.tycoon.domain.Logistics
import com.idleshaft.tycoon.domain.Miner
import com.idleshaft.tycoon.domain.MinerPhase
import com.idleshaft.tycoon.domain.OreType
import com.idleshaft.tycoon.domain.Refinery
import com.idleshaft.tycoon.domain.RefineryPhase
import com.idleshaft.tycoon.domain.Shaft
import com.idleshaft.tycoon.domain.TruckPhase
import kotlin.math.min

/**
 * The pure simulation kernel: [simulate] advances every entity state machine by [dt] seconds,
 * and [reduce] applies a player [GameIntent]. Both return a new immutable [GameState] and
 * report transient effects through [emit] — no side effects, no clocks, no Android types.
 */
object Simulation {

    /** Maximum simulated delta per tick (protects against huge pauses between frames). */
    private const val MAX_DT = 0.5f

    // ------------------------------------------------------------------ tick

    fun simulate(state: GameState, dtSeconds: Float, nowMs: Long, emit: (GameEvent) -> Unit): GameState {
        val dt = dtSeconds.coerceIn(0f, MAX_DT)
        if (dt <= 0f) return state

        var rawSilo = state.rawOreSilo.toMutableMap()
        var barSilo = state.barSilo.toMutableMap()
        var cash = state.cash
        var totalEarned = state.totalEarned
        var gems = state.gems
        var milestoneIndex = state.gemMilestoneIndex

        val boostActive = nowMs < state.boostUntilMs
        val boostMultiplier = if (boostActive) Economy.BOOST_MULTIPLIER else 1.0

        // 1. Shafts: miners dig, carts haul.
        val shafts = state.shafts.map { shaft ->
            advanceShaft(shaft, dt) { oreType, amount ->
                rawSilo[oreType] = (rawSilo[oreType] ?: 0.0) + amount
            }
        }

        // 2. Refinery: raw ore -> bars.
        val refinery = advanceRefinery(state.refinery, rawSilo, dt) { ore ->
            rawSilo[ore] = (rawSilo[ore] ?: 0.0) - Economy.ORE_PER_BAR
            barSilo[ore] = (barSilo[ore] ?: 0) + 1
        }

        // 3. Trucks: bars -> cash.
        val logistics = advanceLogistics(
            logistics = state.logistics,
            barSilo = barSilo,
            dt = dt,
            boostMultiplier = boostMultiplier,
            efficiencyLevel = refinery.efficiencyLevel,
            sell = { loadValue ->
                cash += loadValue
                totalEarned += loadValue
                emit(GameEvent.CashPopup(loadValue))
            },
            updateSilo = { bars -> barSilo = bars },
        )

        // 4. Gem milestones.
        while (milestoneIndex < Economy.GEM_MILESTONES.size &&
            totalEarned >= Economy.GEM_MILESTONES[milestoneIndex]
        ) {
            gems += 1
            milestoneIndex += 1
            emit(GameEvent.GemMilestone(gems))
        }

        return state.copy(
            cash = cash,
            gems = gems,
            totalEarned = totalEarned,
            gemMilestoneIndex = milestoneIndex,
            shafts = shafts,
            rawOreSilo = rawSilo,
            barSilo = barSilo,
            refinery = refinery,
            logistics = logistics,
        )
    }

    // ---------------------------------------------------------------- shafts

    private inline fun advanceShaft(
        shaft: Shaft,
        dt: Float,
        dumpToSurface: (OreType, Double) -> Unit,
    ): Shaft {
        if (!shaft.unlocked) return shaft

        var buffer = shaft.buffer
        val storage = Economy.shaftStorage(shaft.cartCapacityLevel)
        val auto = shaft.managerHired

        // --- Miners: MINING -> HAULING -> RETURNING -> repeat.
        val miners = shaft.miners.map { miner ->
            var phase = miner.phase
            var progress = miner.progress
            var carrying = miner.carrying
            var queued = miner.queuedCycles

            when (phase) {
                MinerPhase.IDLE -> {
                    if (auto || queued > 0) {
                        if (!auto) queued -= 1
                        phase = MinerPhase.MINING
                        progress = 0f
                    }
                }
                MinerPhase.MINING -> {
                    progress += dt / Economy.mineTimeSeconds(shaft.minerSpeedLevel).toFloat()
                    if (progress >= 1f) {
                        carrying = Economy.minerCapacity(shaft.minerCapacityLevel) *
                            Economy.richness(shaft.depthLevel)
                        phase = MinerPhase.HAULING
                        progress = 0f
                    }
                }
                MinerPhase.HAULING -> {
                    progress += dt / Economy.MINER_WALK_SECONDS
                    if (progress >= 1f) {
                        val space = storage - buffer
                        if (space > 0.0) {
                            val deposit = min(carrying, space)
                            buffer += deposit
                            carrying -= deposit
                            phase = MinerPhase.RETURNING
                            progress = 0f
                        } else {
                            progress = 1f // blocked: stockpile full, wait for the cart
                        }
                    }
                }
                MinerPhase.RETURNING -> {
                    progress += dt / Economy.MINER_WALK_SECONDS
                    if (progress >= 1f) {
                        phase = MinerPhase.IDLE
                        progress = 0f
                    }
                }
            }
            Miner(phase, progress, carrying, queued)
        }

        // --- Cart: DESCENDING -> LOADING -> ASCENDING -> UNLOADING.
        var cart = shaft.cart
        var load = cart.load
        var phase = cart.phase
        var progress = cart.progress
        val capacity = Economy.cartCapacity(shaft.cartCapacityLevel)

        when (phase) {
            CartPhase.IDLE -> {
                val managerWantsTrip = auto && buffer >= capacity * 0.5
                val manualWantsTrip = cart.queuedTrips > 0 && buffer > 0.01
                if (managerWantsTrip || manualWantsTrip) {
                    val remainingQueued = if (manualWantsTrip) cart.queuedTrips - 1 else cart.queuedTrips
                    phase = CartPhase.DESCENDING
                    progress = 0f
                    cart = Cart(phase, progress, load, remainingQueued)
                }
            }
            CartPhase.DESCENDING -> {
                progress += dt / Economy.cartTravelSeconds(shaft.cartSpeedLevel).toFloat()
                if (progress >= 1f) {
                    phase = CartPhase.LOADING
                    progress = 0f
                }
                cart = Cart(phase, progress, load, cart.queuedTrips)
            }
            CartPhase.LOADING -> {
                progress += dt / Economy.CART_LOAD_SECONDS
                if (progress >= 1f) {
                    load = min(capacity, buffer)
                    buffer -= load
                    phase = CartPhase.ASCENDING
                    progress = 0f
                }
                cart = Cart(phase, progress, load, cart.queuedTrips)
            }
            CartPhase.ASCENDING -> {
                progress += dt / Economy.cartTravelSeconds(shaft.cartSpeedLevel).toFloat()
                if (progress >= 1f) {
                    phase = CartPhase.UNLOADING
                    progress = 0f
                }
                cart = Cart(phase, progress, load, cart.queuedTrips)
            }
            CartPhase.UNLOADING -> {
                progress += dt / Economy.CART_UNLOAD_SECONDS
                if (progress >= 1f) {
                    if (load > 0.0) dumpToSurface(shaft.oreType, load)
                    load = 0.0
                    phase = CartPhase.IDLE
                    progress = 0f
                }
                cart = Cart(phase, progress, load, cart.queuedTrips)
            }
        }

        return shaft.copy(buffer = buffer, miners = miners, cart = cart)
    }

    // -------------------------------------------------------------- refinery

    private inline fun advanceRefinery(
        refinery: Refinery,
        rawSilo: Map<OreType, Double>,
        dt: Float,
        consume: (OreType) -> Unit,
    ): Refinery {
        var phase = refinery.phase
        var progress = refinery.progress
        var processing = refinery.processingOre
        var manualRun = refinery.manualRun

        when (phase) {
            RefineryPhase.IDLE -> {
                val running = refinery.managerHired || manualRun
                if (running) {
                    val pick = rawSilo.entries
                        .filter { it.value >= Economy.ORE_PER_BAR }
                        .maxByOrNull { it.value }?.key
                    if (pick != null) {
                        processing = pick
                        phase = RefineryPhase.PROCESSING
                        progress = 0f
                    } else {
                        manualRun = false // starved: end the manual run
                    }
                }
            }
            RefineryPhase.PROCESSING -> {
                val ore = processing
                if (ore != null && (rawSilo[ore] ?: 0.0) >= Economy.ORE_PER_BAR) {
                    progress += dt / Economy.refineTimeSeconds(refinery.speedLevel).toFloat()
                    if (progress >= 1f) {
                        consume(ore)
                        processing = null
                        phase = RefineryPhase.IDLE
                        progress = 0f
                    }
                } else {
                    // Ore vanished mid-batch (save/load edge case): bail out cleanly.
                    processing = null
                    phase = RefineryPhase.IDLE
                    progress = 0f
                }
            }
        }

        return refinery.copy(phase = phase, progress = progress, processingOre = processing, manualRun = manualRun)
    }

    // ----------------------------------------------------------------- trucks

    private inline fun advanceLogistics(
        logistics: Logistics,
        barSilo: Map<OreType, Int>,
        dt: Float,
        boostMultiplier: Double,
        efficiencyLevel: Int,
        sell: (Double) -> Unit,
        updateSilo: (MutableMap<OreType, Int>) -> Unit,
    ): Logistics {
        var phase = logistics.phase
        var progress = logistics.progress
        var load = logistics.load
        var manualRun = logistics.manualRun
        val capacity = Economy.truckCapacity(logistics.truckCapacityLevel)

        when (phase) {
            TruckPhase.IDLE -> {
                val barsAvailable = barSilo.values.sum()
                if (logistics.managerHired || (manualRun && barsAvailable > 0)) {
                    if (barsAvailable > 0) {
                        var remaining = capacity
                        val taken = mutableMapOf<OreType, Int>()
                        for (ore in OreType.ordered) {
                            val available = barSilo[ore] ?: 0
                            if (available > 0 && remaining > 0) {
                                val take = min(available, remaining)
                                taken[ore] = take
                                remaining -= take
                            }
                        }
                        val updatedSilo = barSilo.toMutableMap()
                        for ((ore, take) in taken) {
                            updatedSilo[ore] = (updatedSilo[ore] ?: 0) - take
                        }
                        updateSilo(updatedSilo)
                        load = taken
                        phase = TruckPhase.LOADING
                        progress = 0f
                    } else {
                        manualRun = false // starved: end the manual run
                    }
                }
            }
            TruckPhase.LOADING -> {
                progress += dt / Economy.TRUCK_LOAD_SECONDS
                if (progress >= 1f) {
                    phase = TruckPhase.OUTBOUND
                    progress = 0f
                }
            }
            TruckPhase.OUTBOUND -> {
                progress += dt / Economy.truckTravelSeconds(logistics.truckSpeedLevel).toFloat()
                if (progress >= 1f) {
                    phase = TruckPhase.SELLING
                    progress = 0f
                }
            }
            TruckPhase.SELLING -> {
                progress += dt / Economy.TRUCK_SELL_SECONDS
                if (progress >= 1f) {
                    val value = load.entries.sumOf { (ore, count) ->
                        Economy.barValue(ore, efficiencyLevel) * count
                    } * boostMultiplier
                    if (value > 0.0) sell(value)
                    load = emptyMap()
                    phase = TruckPhase.INBOUND
                    progress = 0f
                }
            }
            TruckPhase.INBOUND -> {
                progress += dt / Economy.truckTravelSeconds(logistics.truckSpeedLevel).toFloat()
                if (progress >= 1f) {
                    phase = TruckPhase.IDLE
                    progress = 0f
                }
            }
        }

        return logistics.copy(phase = phase, progress = progress, load = load, manualRun = manualRun)
    }

    // ---------------------------------------------------------------- intents

    fun reduce(state: GameState, intent: GameIntent, nowMs: Long, emit: (GameEvent) -> Unit): GameState {
        val denied = { emit(GameEvent.PurchaseFailed); state }

        return when (intent) {
            GameIntent.TapMine -> state.copy(
                shafts = state.shafts.map { shaft ->
                    if (!shaft.unlocked || shaft.managerHired) shaft else shaft.copy(
                        miners = shaft.miners.map { it.copy(queuedCycles = it.queuedCycles + 1) },
                        cart = shaft.cart.copy(queuedTrips = shaft.cart.queuedTrips + 1),
                    )
                }
            )

            GameIntent.TapRefine -> state.copy(refinery = state.refinery.copy(manualRun = true))

            GameIntent.TapDeliver -> state.copy(logistics = state.logistics.copy(manualRun = true))

            GameIntent.UnlockShaft -> {
                val index = state.shafts.indexOfFirst { !it.unlocked }
                if (index < 0) return denied()
                val cost = Economy.shaftUnlockCost(index)
                if (state.cash < cost) return denied()
                state.copy(
                    cash = state.cash - cost,
                    shafts = state.shafts.mapIndexed { i, shaft ->
                        if (i == index) shaft.copy(unlocked = true) else shaft
                    },
                )
            }

            is GameIntent.BuyShaftUpgrade -> {
                val shaft = state.shafts.getOrNull(intent.shaftIndex) ?: return denied()
                if (!shaft.unlocked || !intent.kind.isShaftScoped) return denied()
                val level = currentShaftLevel(shaft, intent.kind)
                if (level >= Economy.MAX_LEVEL) return denied()
                val cost = Economy.upgradeCost(intent.kind, level)
                if (state.cash < cost) return denied()
                state.copy(
                    cash = state.cash - cost,
                    shafts = state.shafts.mapIndexed { i, s ->
                        if (i == intent.shaftIndex) upgradedShaft(s, intent.kind) else s
                    },
                )
            }

            is GameIntent.BuyGlobalUpgrade -> {
                val level = currentGlobalLevel(state, intent.kind)
                val maxLevel = if (intent.kind == Economy.UpgradeKind.OFFLINE_CAP) {
                    Economy.MAX_OFFLINE_LEVEL
                } else Economy.MAX_LEVEL
                if (level >= maxLevel) return denied()
                val cost = Economy.upgradeCost(intent.kind, level)
                if (state.cash < cost) return denied()
                state.copy(
                    cash = state.cash - cost,
                    refinery = if (intent.kind == Economy.UpgradeKind.REFINERY_SPEED) {
                        state.refinery.copy(speedLevel = level + 1)
                    } else if (intent.kind == Economy.UpgradeKind.REFINERY_EFFICIENCY) {
                        state.refinery.copy(efficiencyLevel = level + 1)
                    } else state.refinery,
                    logistics = when (intent.kind) {
                        Economy.UpgradeKind.TRUCK_CAPACITY ->
                            state.logistics.copy(truckCapacityLevel = level + 1)
                        Economy.UpgradeKind.TRUCK_SPEED ->
                            state.logistics.copy(truckSpeedLevel = level + 1)
                        Economy.UpgradeKind.OFFLINE_CAP ->
                            state.logistics.copy(offlineCapHours = Economy.BASE_OFFLINE_CAP_HOURS + level + 1)
                        else -> state.logistics
                    },
                )
            }

            is GameIntent.HireShaftManager -> {
                val shaft = state.shafts.getOrNull(intent.shaftIndex) ?: return denied()
                if (!shaft.unlocked || shaft.managerHired) return denied()
                val cost = Economy.shaftManagerCost(intent.shaftIndex)
                if (state.cash < cost) return denied()
                state.copy(
                    cash = state.cash - cost,
                    shafts = state.shafts.mapIndexed { i, s ->
                        if (i == intent.shaftIndex) s.copy(managerHired = true) else s
                    },
                )
            }

            GameIntent.HireRefineryManager -> {
                if (state.refinery.managerHired || state.cash < Economy.REFINERY_MANAGER_COST) return denied()
                state.copy(cash = state.cash - Economy.REFINERY_MANAGER_COST, refinery = state.refinery.copy(managerHired = true))
            }

            GameIntent.HireLogisticsManager -> {
                if (state.logistics.managerHired || state.cash < Economy.LOGISTICS_MANAGER_COST) return denied()
                state.copy(cash = state.cash - Economy.LOGISTICS_MANAGER_COST, logistics = state.logistics.copy(managerHired = true))
            }

            GameIntent.ActivateBoost -> {
                if (state.gems < Economy.BOOST_GEM_COST) return denied()
                val newExpiry = maxOf(state.boostUntilMs, nowMs) + Economy.BOOST_DURATION_MS
                state.copy(gems = state.gems - Economy.BOOST_GEM_COST, boostUntilMs = newExpiry)
            }

            GameIntent.DismissOfflineEarnings ->
                state.copy(pendingOfflineEarnings = 0.0, pendingOfflineSeconds = 0L)
        }
    }

    /** Level of the shaft-scoped upgrade [kind] on [shaft]. */
    fun currentShaftLevel(shaft: Shaft, kind: Economy.UpgradeKind): Int = when (kind) {
        Economy.UpgradeKind.MINER_SPEED -> shaft.minerSpeedLevel
        Economy.UpgradeKind.MINER_CAPACITY -> shaft.minerCapacityLevel
        Economy.UpgradeKind.CART_CAPACITY -> shaft.cartCapacityLevel
        Economy.UpgradeKind.CART_SPEED -> shaft.cartSpeedLevel
        Economy.UpgradeKind.SHAFT_DEPTH -> shaft.depthLevel
        else -> 0
    }

    private fun upgradedShaft(shaft: Shaft, kind: Economy.UpgradeKind): Shaft = when (kind) {
        Economy.UpgradeKind.MINER_SPEED -> shaft.copy(minerSpeedLevel = shaft.minerSpeedLevel + 1)
        Economy.UpgradeKind.MINER_CAPACITY -> shaft.copy(minerCapacityLevel = shaft.minerCapacityLevel + 1)
        Economy.UpgradeKind.CART_CAPACITY -> shaft.copy(cartCapacityLevel = shaft.cartCapacityLevel + 1)
        Economy.UpgradeKind.CART_SPEED -> shaft.copy(cartSpeedLevel = shaft.cartSpeedLevel + 1)
        Economy.UpgradeKind.SHAFT_DEPTH -> shaft.copy(depthLevel = shaft.depthLevel + 1)
        else -> shaft
    }

    /** Level of a global upgrade [kind] in [state]. */
    fun currentGlobalLevel(state: GameState, kind: Economy.UpgradeKind): Int = when (kind) {
        Economy.UpgradeKind.REFINERY_SPEED -> state.refinery.speedLevel
        Economy.UpgradeKind.REFINERY_EFFICIENCY -> state.refinery.efficiencyLevel
        Economy.UpgradeKind.TRUCK_CAPACITY -> state.logistics.truckCapacityLevel
        Economy.UpgradeKind.TRUCK_SPEED -> state.logistics.truckSpeedLevel
        Economy.UpgradeKind.OFFLINE_CAP -> state.logistics.offlineCapHours - Economy.BASE_OFFLINE_CAP_HOURS
        else -> 0
    }
}
