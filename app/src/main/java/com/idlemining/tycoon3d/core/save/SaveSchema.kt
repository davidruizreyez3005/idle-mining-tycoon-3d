package com.idlemining.tycoon3d.core.save

import kotlinx.serialization.Serializable

/**
 * Versioned save schema. The save DTO is deliberately decoupled from runtime
 * [com.idlemining.tycoon3d.game.GameState] — the game evolves, old saves are
 * migrated forward by [SaveMigrations] and then decoded into the current shape.
 *
 * Bump [VERSION] whenever the schema changes and add a migration step.
 */
@Serializable
data class SaveData(
    val version: Int = SaveSchema.VERSION,
    val createdAtMs: Long,
    val savedAtMs: Long,
    val money: Double = 0.0,
    /** resource id -> units carried. */
    val inventory: Map<String, Int> = emptyMap(),
    /** upgrade id -> level. */
    val upgrades: Map<String, Int> = emptyMap(),
    val worker: WorkerSave = WorkerSave(),
    /** One entry per world node, in world.json order. */
    val nodes: List<NodeSave> = emptyList(),
    val stats: StatsSave = StatsSave(),
    val flags: Map<String, Boolean> = emptyMap(),
    /** True when the away-time report has not been shown yet. */
    val offlineUnseen: Boolean = false,
)

@Serializable
data class WorkerSave(
    val x: Float = 0f,
    val z: Float = 0f,
    val facing: Float = 0f,
)

@Serializable
data class NodeSave(
    val hp: Float,
    /** Seconds until respawn; 0 when the node is alive. */
    val respawnRemainingSec: Float = 0f,
)

@Serializable
data class StatsSave(
    val totalMined: Int = 0,
    val totalSoldValue: Double = 0.0,
    val totalEarned: Double = 0.0,
    val playSeconds: Double = 0.0,
    val nodesBroken: Int = 0,
)

object SaveSchema {
    const val VERSION = 1
}
