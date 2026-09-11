package com.idlemining.tycoon3d.game

import com.idlemining.tycoon3d.core.content.GameContent
import com.idlemining.tycoon3d.core.content.NodePlacement
import com.idlemining.tycoon3d.core.economy.OfflineEarnings
import kotlinx.serialization.Serializable

/**
 * Immutable runtime state — advanced only through [Simulation] (pure functions)
 * and exposed through a StateFlow at 10 Hz. The 3D scene reads it inside its
 * frame callback; the HUD recomposes on each emission.
 */
data class GameState(
    val content: GameContent,
    val createdAtMs: Long,
    val lastSavedAtMs: Long,
    val money: Double,
    /** resource id -> units carried (manual hauls + idle machine output). */
    val inventory: Map<String, Int>,
    /** Fractional idle accumulation, resource id -> units-in-progress. */
    val idleBuffer: Map<String, Double>,
    /** upgrade id -> level. */
    val upgrades: Map<String, Int>,
    val worker: WorkerState,
    val nodes: List<NodeState>,
    val stats: StatsState,
    val flags: Map<String, Boolean>,
    /** Set at load when the away-time report has not been seen yet. */
    val offlineReport: OfflineReport? = null,
) {
    val totalCarried: Int get() = inventory.values.sum()

    fun upgradeLevel(id: String): Int = upgrades[id] ?: 0
}

// --------------------------------------------------------------------------- worker

enum class WorkerAction { IDLE, WALKING, MINING }

/** Where the worker is headed. Sealed — exhaustive when() in the simulation. */
@Serializable
sealed class WorkerTarget {
    @Serializable
    data class Ground(val x: Float, val z: Float) : WorkerTarget()

    @Serializable
    data class Node(val index: Int) : WorkerTarget()

    @Serializable
    object Depot : WorkerTarget()
}

data class WorkerState(
    val x: Float,
    val z: Float,
    /** Yaw in degrees; the model is authored facing +Z. */
    val facing: Float,
    val action: WorkerAction = WorkerAction.IDLE,
    val target: WorkerTarget? = null,
    /** Index of the node being mined, valid while action == MINING. */
    val miningNodeIndex: Int = -1,
)

// --------------------------------------------------------------------------- nodes

data class NodeState(
    val index: Int,
    val typeId: String,
    val maxHp: Float,
    val hp: Float,
    /** Seconds until the vein regrows; 0 when alive. */
    val respawnRemainingSec: Float = 0f,
    val x: Float,
    val z: Float,
    val scale: Float = 1f,
) {
    val alive: Boolean get() = hp > 0f && respawnRemainingSec <= 0f
}

// --------------------------------------------------------------------------- stats

data class StatsState(
    val totalMined: Int = 0,
    val totalSoldValue: Double = 0.0,
    val totalEarned: Double = 0.0,
    val playSeconds: Double = 0.0,
    val nodesBroken: Int = 0,
)

// --------------------------------------------------------------------------- offline

data class OfflineReport(
    val awaySeconds: Long,
    /** resource id -> units earned while away. */
    val resources: Map<String, Int>,
    val totalValue: Double,
)

/** Resolved node placements for a world (index-stable with world.json order). */
fun List<NodePlacement>.resolveNodes(content: GameContent): List<NodeState> =
    mapIndexed { i, p ->
        val def = content.nodeType(p.typeId)
        NodeState(
            index = i,
            typeId = def.id,
            maxHp = def.hp.toFloat(),
            hp = def.hp.toFloat(),
            x = p.at[0],
            z = p.at[1],
            scale = p.scale,
        )
    }

fun OfflineEarnings.toReport(value: Double) = OfflineReport(
    awaySeconds = effectiveSeconds,
    resources = resources,
    totalValue = value,
)
