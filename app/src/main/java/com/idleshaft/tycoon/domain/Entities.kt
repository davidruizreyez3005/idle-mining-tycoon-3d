package com.idleshaft.tycoon.domain

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Worker phase machines — the four stages of the production pipeline.
// ---------------------------------------------------------------------------

/** Per-miner state machine: MINING -> HAULING -> RETURNING -> (repeat | IDLE). */
@Serializable
enum class MinerPhase { IDLE, MINING, HAULING, RETURNING }

/** Per-shaft elevator cart: DESCENDING -> LOADING -> ASCENDING -> UNLOADING. */
@Serializable
enum class CartPhase { IDLE, DESCENDING, LOADING, ASCENDING, UNLOADING }

/** Refinery runs one batch at a time: 10 units of raw ore -> 1 bar. */
@Serializable
enum class RefineryPhase { IDLE, PROCESSING }

/** Delivery truck: drive out, sell, drive home. */
@Serializable
enum class TruckPhase { IDLE, LOADING, OUTBOUND, SELLING, INBOUND }

// ---------------------------------------------------------------------------
// Entities
// ---------------------------------------------------------------------------

@Serializable
data class Miner(
    val phase: MinerPhase = MinerPhase.IDLE,
    /** 0..1 progress through the current phase. */
    val progress: Float = 0f,
    /** Ore currently being carried to the shaft stockpile. */
    val carrying: Double = 0.0,
    /** Queued manual work cycles (consumed one per started cycle). */
    val queuedCycles: Int = 0,
)

@Serializable
data class Cart(
    val phase: CartPhase = CartPhase.IDLE,
    val progress: Float = 0f,
    /** Raw ore currently riding in the cart. */
    val load: Double = 0.0,
    /** Queued manual trips (a tap queues exactly one round trip). */
    val queuedTrips: Int = 0,
)

/**
 * One mine shaft. Shafts are laid out from cheap surface copper to deep diamond;
 * each owns its own miner crew, underground stockpile and elevator cart.
 */
@Serializable
data class Shaft(
    val oreType: OreType,
    val unlocked: Boolean = false,
    /** Shaft manager automates mining + hauling for this shaft. */
    val managerHired: Boolean = false,
    val minerSpeedLevel: Int = 1,
    val minerCapacityLevel: Int = 1,
    val cartCapacityLevel: Int = 1,
    val cartSpeedLevel: Int = 1,
    val depthLevel: Int = 1,
    /** Raw ore waiting at the shaft bottom. */
    val buffer: Double = 0.0,
    val miners: List<Miner> = List(MINERS_PER_SHAFT) { Miner() },
    val cart: Cart = Cart(),
) {
    companion object {
        const val MINERS_PER_SHAFT = 3
    }
}

/** The surface refinery: converts raw ore into sellable bars. */
@Serializable
data class Refinery(
    /** Speed upgrade level (reduces processing time per bar). */
    val speedLevel: Int = 1,
    /** Efficiency upgrade level (increases bar market value). */
    val efficiencyLevel: Int = 1,
    val managerHired: Boolean = false,
    val phase: RefineryPhase = RefineryPhase.IDLE,
    val progress: Float = 0f,
    /** Ore type of the batch currently being processed, if any. */
    val processingOre: OreType? = null,
    /** True while a manual "work" run is active (auto-clears when starved). */
    val manualRun: Boolean = false,
)

/** Logistics: trucks haul refined bars to the market and convert them to cash. */
@Serializable
data class Logistics(
    val truckCapacityLevel: Int = 1,
    val truckSpeedLevel: Int = 1,
    val managerHired: Boolean = false,
    val phase: TruckPhase = TruckPhase.IDLE,
    val progress: Float = 0f,
    /** Bars on board, by ore type. */
    val load: Map<OreType, Int> = emptyMap(),
    /** True while a manual "work" run is active (auto-clears when the silo empties). */
    val manualRun: Boolean = false,
    /** Offline earnings storage, in hours (Warehouse Logistics upgrade). */
    val offlineCapHours: Int = 4,
)

// ---------------------------------------------------------------------------
// Root game state — the single source of truth, stored via kotlinx-serialization.
// ---------------------------------------------------------------------------

@Serializable
data class GameState(
    val cash: Double = 80.0,
    val gems: Int = 0,
    val totalEarned: Double = 0.0,
    /** Index of the next gem milestone threshold (see [Economy.GEM_MILESTONES]). */
    val gemMilestoneIndex: Int = 0,
    /** Epoch millis until which the 2x income boost is active. */
    val boostUntilMs: Long = 0L,
    val shafts: List<Shaft> = ShaftList(),
    /** Raw ore delivered to the surface silo, by type. */
    val rawOreSilo: Map<OreType, Double> = OreType.ordered.associateWith { 0.0 },
    /** Refined bars waiting for delivery, by type. */
    val barSilo: Map<OreType, Int> = OreType.ordered.associateWith { 0 },
    val refinery: Refinery = Refinery(),
    val logistics: Logistics = Logistics(),
    /** Epoch millis of the last save — the anchor for offline earnings. */
    val lastSavedAtMs: Long = 0L,
    /** Amount earned while away, presented in the welcome-back dialog. */
    val pendingOfflineEarnings: Double = 0.0,
    val pendingOfflineSeconds: Long = 0L,
) {
    companion object {
        /** Fresh save: copper shaft already unlocked, a little seed cash. */
        fun initial(nowMs: Long = 0L): GameState = GameState(
            lastSavedAtMs = nowMs,
            shafts = ShaftList(unlockFirst = true),
        )

        private fun ShaftList(unlockFirst: Boolean = false): List<Shaft> =
            OreType.ordered.mapIndexed { index, ore ->
                Shaft(oreType = ore, unlocked = unlockFirst && index == 0)
            }
    }
}
