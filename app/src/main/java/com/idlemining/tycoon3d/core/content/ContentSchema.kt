package com.idlemining.tycoon3d.core.content

import kotlinx.serialization.Serializable

/**
 * Data-driven content schemas. Every gameplay number, name and color in the vertical
 * slice comes from JSON files under `assets/gamedata/` — nothing is hardcoded in the
 * game logic. These DTOs are the wire format; [GameContent] is the resolved,
 * validated runtime form.
 */

// ---------------------------------------------------------------------------
// resources.json
// ---------------------------------------------------------------------------

@Serializable
data class ResourcesFile(val version: Int = 1, val resources: List<ResourceDef> = emptyList())

@Serializable
data class ResourceDef(
    val id: String,
    val name: String,
    val rarity: String = "common",
    val baseValue: Int,
    /** Hex color "#RRGGBB" used for ore crystals, drop particles and UI chips. */
    val color: String,
    val icon: String? = null,
)

// ---------------------------------------------------------------------------
// mine_nodes.json
// ---------------------------------------------------------------------------

@Serializable
data class MineNodesFile(val version: Int = 1, val nodeTypes: List<NodeTypeDef> = emptyList())

@Serializable
data class NodeTypeDef(
    val id: String,
    val name: String,
    /** Kit piece used for the rock body: rock_small | rock_large | rock_boulder. */
    val mesh: String = "rock_small",
    val hp: Int,
    val respawnSeconds: Int,
    /** resource id -> units dropped when the node is fully mined. */
    val yields: Map<String, Int>,
    /** Resource whose color the ore crystals on the rock surface use. */
    val primaryResource: String,
    val scale: Float = 1.0f,
    /** How many ore crystals stud the rock surface. */
    val crystals: Int = 4,
)

// ---------------------------------------------------------------------------
// upgrades.json
// ---------------------------------------------------------------------------

@Serializable
data class UpgradesFile(val version: Int = 1, val upgrades: List<UpgradeDef> = emptyList())

@Serializable
data class UpgradeDef(
    val id: String,
    val name: String,
    val desc: String,
    val effect: EffectDef,
    val baseCost: Int,
    val costGrowth: Float,
    val maxLevel: Int,
    val category: String = "general",
)

@Serializable
data class EffectDef(
    /**
     * One of: miningSpeed | moveSpeed | backpack | sellMargin | idleExtraction.
     * `perLevel` applies for the scalar effects; `rates` only for idleExtraction.
     */
    val type: String,
    /** Double — this feeds money math where Float precision visibly drifts. */
    val perLevel: Double = 0.0,
    val rates: List<IdleRateDef> = emptyList(),
)

@Serializable
data class IdleRateDef(
    val resource: String,
    val perLevel: Double,
    /** Extractor level at which this resource starts flowing. */
    val unlockLevel: Int = 1,
)

// ---------------------------------------------------------------------------
// economy.json
// ---------------------------------------------------------------------------

@Serializable
data class EconomyFile(
    val version: Int = 1,
    val start: StartDef = StartDef(),
    val worker: WorkerTuning = WorkerTuning(),
    val sell: SellTuning = SellTuning(),
    val idle: IdleTuning = IdleTuning(),
)

@Serializable
data class StartDef(val money: Int = 0, val backpack: Int = 10)

@Serializable
data class WorkerTuning(
    val moveSpeed: Float = 2.4f,
    val mineDps: Float = 20f,
    val collectRadius: Float = 1.5f,
    val reachRadius: Float = 1.8f,
)

@Serializable
data class SellTuning(val depotRadius: Float = 3.0f)

@Serializable
data class IdleTuning(val offlineCapHours: Int = 4)

// ---------------------------------------------------------------------------
// world.json — the modular zone layout
// ---------------------------------------------------------------------------

@Serializable
data class WorldFile(
    val version: Int = 1,
    val zone: String = "unknown",
    val name: String = "Zone",
    val ground: GroundDef = GroundDef(),
    val camera: CameraDef = CameraDef(),
    /** Worker spawn point as [x, z]. */
    val spawn: List<Float> = listOf(0f, 0f),
    val cliff: CliffDef = CliffDef(),
    val nodes: List<NodePlacement> = emptyList(),
    val depot: DepotDef = DepotDef(),
    val props: List<PropPlacement> = emptyList(),
)

@Serializable
data class GroundDef(
    val size: List<Float> = listOf(40f, 40f),
    val grassColor: String = "#7CB342",
    val dirtColor: String = "#A1887F",
    val rockColor: String = "#78909C",
    val pathWidth: Float = 4.0f,
)

@Serializable
data class CameraDef(
    val orbitRadius: Float = 16.5f,
    val target: List<Float> = listOf(0f, 0.8f, -4f),
)

@Serializable
data class CliffDef(
    /** Center Z of the rock wall mass. */
    val z: Float = -19f,
    /** Half-extent along X. */
    val halfSpan: Float = 14f,
    val height: Float = 5f,
    val depth: Float = 4f,
    /** Half-width of the mine entrance gap cut through the wall. */
    val entranceHalfWidth: Float = 3.4f,
    val wallColor: String = "#6D838F",
)

@Serializable
data class NodePlacement(
    val typeId: String,
    /** Position as [x, z]. */
    val at: List<Float>,
    val scale: Float = 1.0f,
)

@Serializable
data class DepotDef(
    val position: List<Float> = listOf(0f, 12.5f),
    val facing: Float = 0f,
)

@Serializable
data class PropPlacement(
    val piece: String,
    val at: List<Float>,
    val scale: Float = 1.0f,
    /** Yaw in degrees. */
    val rotation: Float = 0f,
)
