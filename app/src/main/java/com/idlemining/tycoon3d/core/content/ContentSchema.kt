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
 * One of: miningSpeed | moveSpeed | backpack | sellMargin | idleExtraction |
 * offlineCap | luckyStrike. `perLevel` applies for the scalar effects;
 * `rates` only for idleExtraction.
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
    val market: MarketTuning = MarketTuning(),
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

/**
 * Dynamic market — Phase 3. Resource prices drift on slow sine waves so the
 * right time to sell becomes a real decision.
 *
 * - [amplitude]: fractional swing around the base price (0.22 = ±22%).
 * - [basePeriodSec] / [periodSpreadSec]: each resource gets a period in
 *   [basePeriodSec, basePeriodSec + periodSpreadSec], derived deterministically
 *   from its index, so waves stay out of sync.
 * - [trendWindowSec]: how far back the UI compares prices to draw ▲/▼ arrows.
 */
@Serializable
data class MarketTuning(
    val amplitude: Float = 0.22f,
    val basePeriodSec: Float = 300f,
    val periodSpreadSec: Float = 180f,
    val trendWindowSec: Float = 30f,
)

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
    val visuals: VisualsDef = VisualsDef(),
    /** Mobile render tuning — Phase 3.5 performance profile. */
    val performance: PerformanceDef = PerformanceDef(),
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

/**
 * Locked 45° orthographic camera — Phase 3 presentation. The angle and the
 * projection NEVER change during play (no orbit gesture); the player pans by
 * dragging and zooms by pinching, both clamped so the mine stays reachable.
 *
 * - [yaw] / [pitch]: fixed viewing angles in degrees. yaw 0 looks toward -Z
 *   (camera on the +Z side); positive yaw swings the camera toward +X.
 * - [distance]: constant eye-to-target distance in meters. With an ortho
 *   projection this does not affect framing — it only places the eye inside
 *   the [near]/[far] depth range.
 * - [target]: framing anchor [x, y, z] — the ground point at screen center
 *   when no pan has been applied.
 * - [zoomHeight]: default visible vertical extent in meters; pinch zoom is
 *   clamped to [zoomMinHeight] / [zoomMaxHeight].
 * - [panRangeX] / [panRangeZ]: how far (meters) the target may move from the
 *   anchor while panning.
 */
@Serializable
data class CameraDef(
    val yaw: Float = 31f,
    val pitch: Float = 45f,
    val distance: Float = 60f,
    val target: List<Float> = listOf(0f, 0.6f, -5f),
    val zoomHeight: Float = 26f,
    val zoomMinHeight: Float = 13f,
    val zoomMaxHeight: Float = 52f,
    val panRangeX: Float = 15f,
    val panRangeZ: Float = 14f,
    val near: Float = 1f,
    val far: Float = 400f,
)

// ---------------------------------------------------------------------------
// visuals — lighting, sky and post-processing tuning (Phase 2)
// ---------------------------------------------------------------------------

/**
 * Scene presentation tuning. Everything the renderer needs to make the zone
 * look good lives here so each zone can carry its own mood.
 */
@Serializable
data class VisualsDef(
    val sun: SunDef = SunDef(),
    val fill: FillDef = FillDef(),
    val ambient: AmbientDef = AmbientDef(),
    val sky: SkyDef = SkyDef(),
    val fog: FogDef = FogDef(),
    val bloom: BloomDef = BloomDef(),
    val vignette: VignetteDef = VignetteDef(),
    /** One of: aces | filmic | linear. */
    val toneMapping: String = "aces",
    val exposure: Float = 1.05f,
    val contrast: Float = 1.06f,
    val saturation: Float = 1.1f,
)

/** Main directional light — the sun. [direction] points *toward* the light. */
@Serializable
data class SunDef(
    /** Hex "#RRGGBB" (sRGB). */
    val color: String = "#FFF1D6",
    /** Illuminance in lux. */
    val intensity: Float = 26_000f,
    /** Normalized direction toward the light. */
    val direction: List<Float> = listOf(-0.42f, -0.78f, 0.46f),
    /** Apparent sun disc radius in degrees (affects soft shadow penumbra). */
    val angularRadius: Float = 1.9f,
    /** Shadow map resolution (pixels, power of two). */
    val shadowMapSize: Int = 2048,
)

/** Sky/ambient fill — cool bounce from the opposite side, never casts shadows. */
@Serializable
data class FillDef(
    val color: String = "#AECBEB",
    val intensity: Float = 5_000f,
    val direction: List<Float> = listOf(0.5f, -0.32f, -0.8f),
)

/** Image-based ambient: neutral IBL tint intensity. */
@Serializable
data class AmbientDef(
    val intensity: Float = 10_000f,
)

/** Solid skybox color (hex, sRGB). Distance haze comes from [FogDef]. */
@Serializable
data class SkyDef(
    val color: String = "#9EC9EC",
)

/**
 * Distance fog — *density is an extinction factor in 1/m* (each unit of density
 * reduces light to 37% per meter). Subtle quarry haze: ~0.012.
 */
@Serializable
data class FogDef(
    val enabled: Boolean = true,
    val density: Float = 0.012f,
    /** Distance in meters from the camera where fog starts. */
    val distance: Float = 0.5f,
    /** Everything beyond this stays unfogged (keeps the sky clean). */
    val cutOffDistance: Float = 85f,
    val maximumOpacity: Float = 0.7f,
    val heightFalloff: Float = 0.04f,
    /** Hex color, sRGB. */
    val color: String = "#CFE0F2",
    /** Sun in-scattering distance/size; size <= 0 disables the sun glow. */
    val inScatteringStart: Float = 6f,
    val inScatteringSize: Float = 32f,
)

@Serializable
data class BloomDef(
    val enabled: Boolean = true,
    val strength: Float = 0.2f,
)

@Serializable
data class VignetteDef(
    val enabled: Boolean = true,
    val midPoint: Float = 0.45f,
    val roundness: Float = 0.5f,
    val feather: Float = 0.55f,
)

// ---------------------------------------------------------------------------
// performance — mobile render tuning (Phase 3.5)
// ---------------------------------------------------------------------------

/**
 * Render performance profile. The pre-tuning build shipped the `Cinematic`
 * preset (MSAA 4x + SSAO high + 2048px PCSS shadows) and ran at an unstable
 * frame rate on mid-range phones. This block re-tunes the same Filament view
 * for a locked 60: the defaults below are what a zone gets when it does not
 * author a `performance` block at all, and they are all mobile-first.
 *
 * - [dynamicResolution]: Filament rescales the render target every few frames
 *   to hold the device's frame rate — the single most effective GPU relief.
 * - [ssao]: screen-space ambient occlusion. Off by default: the sun + shadow
 *   map already ground every object; SSAO high cost ~1-2 ms/frame on mid-GPUs.
 * - [msaaSampleCount]: 0/1 = FXAA only (cheap); 4 was the old default and was
 *   the most expensive single toggle in the pipeline.
 * - [hdrQuality]: HDR color buffer tier — medium is visually identical for this
 *   stylized palette at half the bandwidth.
 * - [bloomQuality]: bloom pyramid tier.
 * - [softShadows]: PCSS penumbra sampling. Off = hard sample taps only.
 */
@Serializable
data class PerformanceDef(
    val dynamicResolution: DynamicResolutionDef = DynamicResolutionDef(),
    val ssao: Boolean = false,
    val msaaSampleCount: Int = 0,
    /** One of: low | medium | high. */
    val hdrQuality: String = "medium",
    /** One of: low | medium | high. */
    val bloomQuality: String = "low",
    val softShadows: Boolean = false,
)

@Serializable
data class DynamicResolutionDef(
    val enabled: Boolean = true,
    /** Render-target scale floor — 0.5 = half resolution worst case. */
    val minScale: Float = 0.5f,
    val maxScale: Float = 1.0f,
    /** History quality used to pick the scale: low | medium | high. */
    val quality: String = "medium",
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
