package com.idleshaft.tycoon.domain

import kotlin.math.min
import kotlin.math.pow

/**
 * All gameplay math lives here: production rates, upgrade scaling, manager prices,
 * income analytics and number formatting. Pure functions only — trivially testable.
 *
 * Upgrade costs follow the classic idle-game exponential curve:
 * `cost = base * growth^(level - 1)` with growth ~1.15 (see [UpgradeKind]).
 */
object Economy {

    // ------------------------------------------------------------------ Tuning

    /** Raw ore units consumed per refined bar. */
    const val ORE_PER_BAR = 10.0

    /** Seconds a miner takes to walk between the seam and the shaft stockpile (each way). */
    const val MINER_WALK_SECONDS: Float = 1.2f

    /** Cart loading / unloading duration in seconds. */
    const val CART_LOAD_SECONDS: Float = 1.2f
    const val CART_UNLOAD_SECONDS: Float = 0.8f

    /** Truck dwell times in seconds. */
    const val TRUCK_LOAD_SECONDS: Float = 0.8f
    const val TRUCK_SELL_SECONDS: Float = 0.6f

    /** Base offline earnings cap in hours (extendable via the Warehouse upgrade). */
    const val BASE_OFFLINE_CAP_HOURS = 4
    const val MAX_OFFLINE_CAP_HOURS = 12

    /** Gem boost: cost, duration and multiplier. */
    const val BOOST_GEM_COST = 5
    const val BOOST_DURATION_MS = 120_000L
    const val BOOST_MULTIPLIER = 2.0

    /** totalEarned thresholds that each award one gem. */
    val GEM_MILESTONES = doubleArrayOf(
        1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10,
    )

    // ------------------------------------------------------------- Production

    /** Seconds a miner needs to dig one trip's worth of ore. */
    fun mineTimeSeconds(minerSpeedLevel: Int): Double = 3.0 / (1.0 + 0.20 * (minerSpeedLevel - 1))

    /** Ore units dug per miner per trip (before the depth richness multiplier). */
    fun minerCapacity(minerCapacityLevel: Int): Double = 2.0 + 1.0 * (minerCapacityLevel - 1)

    /** Deeper shafts hit richer veins: yield multiplier. */
    fun richness(depthLevel: Int): Double = 1.0 + 0.25 * (depthLevel - 1)

    /** Ore units the shaft's underground stockpile can hold. */
    fun shaftStorage(cartCapacityLevel: Int): Double = cartCapacity(cartCapacityLevel) * 3.0

    /** Raw ore units a cart can carry per trip. */
    fun cartCapacity(cartCapacityLevel: Int): Double = 12.0 + 6.0 * (cartCapacityLevel - 1)

    /** One-way elevator travel time in seconds. */
    fun cartTravelSeconds(cartSpeedLevel: Int): Double = 4.0 / (1.0 + 0.12 * (cartSpeedLevel - 1))

    /** Seconds the refinery needs per bar. */
    fun refineTimeSeconds(speedLevel: Int): Double = 3.0 / (1.0 + 0.12 * (speedLevel - 1))

    /** Market value multiplier of a refined bar. */
    fun barValueMultiplier(efficiencyLevel: Int): Double = 1.0 + 0.08 * (efficiencyLevel - 1)

    /** Value in dollars of one refined bar of [ore] at the given refinery efficiency. */
    fun barValue(ore: OreType, efficiencyLevel: Int): Double =
        ore.rawValuePerUnit * ORE_PER_BAR * barValueMultiplier(efficiencyLevel)

    /** Bars a truck can carry per delivery run. */
    fun truckCapacity(truckCapacityLevel: Int): Int = 5 + 2 * (truckCapacityLevel - 1)

    /** One-way truck travel time in seconds. */
    fun truckTravelSeconds(truckSpeedLevel: Int): Double = 6.0 / (1.0 + 0.12 * (truckSpeedLevel - 1))

    // -------------------------------------------------------------- Upgrades

    /** Upgrades the player can buy. Shaft-scoped kinds apply to one shaft; the rest are global. */
    enum class UpgradeKind {
        MINER_SPEED, MINER_CAPACITY, CART_CAPACITY, CART_SPEED, SHAFT_DEPTH,
        REFINERY_SPEED, REFINERY_EFFICIENCY,
        TRUCK_CAPACITY, TRUCK_SPEED, OFFLINE_CAP;

        val isShaftScoped: Boolean
            get() = this in SHAFT_SCOPED

        companion object {
            val SHAFT_SCOPED = setOf(MINER_SPEED, MINER_CAPACITY, CART_CAPACITY, CART_SPEED, SHAFT_DEPTH)
        }
    }

    /** cost = base * growth^(level - 1), rounded up to a whole dollar. */
    data class UpgradeCurve(val base: Double, val growth: Double) {
        fun costAt(level: Int): Double = base * growth.pow(level - 1)
    }

    val UPGRADE_CURVES: Map<UpgradeKind, UpgradeCurve> = mapOf(
        UpgradeKind.MINER_SPEED to UpgradeCurve(30.0, 1.15),
        UpgradeKind.MINER_CAPACITY to UpgradeCurve(25.0, 1.15),
        UpgradeKind.CART_CAPACITY to UpgradeCurve(60.0, 1.17),
        UpgradeKind.CART_SPEED to UpgradeCurve(50.0, 1.15),
        UpgradeKind.SHAFT_DEPTH to UpgradeCurve(400.0, 1.22),
        UpgradeKind.REFINERY_SPEED to UpgradeCurve(120.0, 1.16),
        UpgradeKind.REFINERY_EFFICIENCY to UpgradeCurve(150.0, 1.18),
        UpgradeKind.TRUCK_CAPACITY to UpgradeCurve(90.0, 1.15),
        UpgradeKind.TRUCK_SPEED to UpgradeCurve(80.0, 1.15),
        UpgradeKind.OFFLINE_CAP to UpgradeCurve(500.0, 1.9),
    )

    /** Price of upgrading [kind] from [currentLevel] to the next level. */
    fun upgradeCost(kind: UpgradeKind, currentLevel: Int): Double =
        kotlin.math.ceil(UPGRADE_CURVES.getValue(kind).costAt(currentLevel))

    /** Unlock prices for shafts 2, 3 and 4 (shaft 1 is free at game start). */
    val SHAFT_UNLOCK_COSTS = doubleArrayOf(0.0, 500.0, 5_000.0, 50_000.0)

    fun shaftUnlockCost(index: Int): Double = SHAFT_UNLOCK_COSTS[index]

    /** Manager hiring prices. Shaft managers get pricier with depth. */
    fun shaftManagerCost(shaftIndex: Int): Double = 250.0 * 6.0.pow(shaftIndex)

    const val REFINERY_MANAGER_COST = 600.0
    const val LOGISTICS_MANAGER_COST = 900.0

    /** Maximum reachable level per upgrade kind. */
    const val MAX_LEVEL = 50
    val MAX_OFFLINE_LEVEL = MAX_OFFLINE_CAP_HOURS - BASE_OFFLINE_CAP_HOURS

    // ----------------------------------------------------------- Income rate

    /**
     * Analytic income per second of the fully-automated pipeline — the number shown in the
     * HUD and the rate used for offline earnings. Each stage is limited by its bottleneck:
     * mining vs. hauling per shaft, refining throughput, truck delivery throughput.
     *
     * A stage only counts when its manager is hired (manual stages produce nothing while
     * the player is away / by themselves).
     */
    fun incomePerSecond(state: GameState): Double {
        val refinery = state.refinery
        val logistics = state.logistics
        if (!refinery.managerHired || !logistics.managerHired) return 0.0

        // 1. Ore flow per shaft (limited by the slower of miners and cart).
        val orePerSecond = mutableMapOf<OreType, Double>()
        for (shaft in state.shafts) {
            if (!shaft.unlocked || !shaft.managerHired) continue
            val minerCycle = mineTimeSeconds(shaft.minerSpeedLevel) + 2 * MINER_WALK_SECONDS
            val minerRate = Shaft.MINERS_PER_SHAFT *
                minerCapacity(shaft.minerCapacityLevel) * richness(shaft.depthLevel) / minerCycle
            val cartCycle = 2 * cartTravelSeconds(shaft.cartSpeedLevel) + CART_LOAD_SECONDS + CART_UNLOAD_SECONDS
            val cartRate = cartCapacity(shaft.cartCapacityLevel) / cartCycle
            val effective = min(minerRate, cartRate)
            orePerSecond[shaft.oreType] = (orePerSecond[shaft.oreType] ?: 0.0) + effective
        }
        val totalOre = orePerSecond.values.sum()
        if (totalOre <= 0.0) return 0.0

        // 2. Bar production limited by the refinery.
        val barRateLimitedByOre = totalOre / ORE_PER_BAR
        val refineryBarRate = 1.0 / refineTimeSeconds(refinery.speedLevel)
        val barRate = min(barRateLimitedByOre, refineryBarRate)

        // 3. Delivery limited by the trucks.
        val truckCycle = 2 * truckTravelSeconds(logistics.truckSpeedLevel) +
            TRUCK_LOAD_SECONDS + TRUCK_SELL_SECONDS
        val truckBarRate = truckCapacity(logistics.truckCapacityLevel) / truckCycle
        val soldRate = min(barRate, truckBarRate)
        if (soldRate <= 0.0) return 0.0

        // 4. Value of a weighted-average bar.
        val weightedBarValue = orePerSecond.entries.sumOf { (ore, rate) ->
            (rate / totalOre) * barValue(ore, refinery.efficiencyLevel)
        }
        val boost = if (System.currentTimeMillis() < state.boostUntilMs) BOOST_MULTIPLIER else 1.0
        return soldRate * weightedBarValue * boost
    }

    /** Total bars currently sitting in the bar silo. */
    fun totalBars(state: GameState): Int = state.barSilo.values.sum()

    /** Total raw ore in the surface silo. */
    fun totalRawOre(state: GameState): Double = state.rawOreSilo.values.sum()

    // ------------------------------------------------------------- Formatting

    /**
     * Compact money formatting: `$1.23k`, `$4.56M`, `$7.89B`, `$1.23T`.
     * Values below 1000 render without suffix.
     */
    fun money(amount: Double): String {
        val abs = kotlin.math.abs(amount)
        val prefix = if (amount < 0) "-$" else "$"
        return prefix + when {
            abs >= 1e12 -> String.format(java.util.Locale.US, "%.2fT", abs / 1e12)
            abs >= 1e9 -> String.format(java.util.Locale.US, "%.2fB", abs / 1e9)
            abs >= 1e6 -> String.format(java.util.Locale.US, "%.2fM", abs / 1e6)
            abs >= 1e3 -> String.format(java.util.Locale.US, "%.2fk", abs / 1e3)
            else -> {
                val whole = if (abs == abs.toLong().toDouble()) abs.toLong().toString()
                else String.format(java.util.Locale.US, "%.0f", abs)
                whole
            }
        }
    }

    /** Compact per-second rate, e.g. `$12.34/s`. */
    fun rate(amountPerSecond: Double): String = money(amountPerSecond) + "/s"

    /** Human-readable duration for the offline dialog. */
    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}
