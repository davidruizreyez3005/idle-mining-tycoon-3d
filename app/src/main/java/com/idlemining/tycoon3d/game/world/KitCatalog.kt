package com.idlemining.tycoon3d.game.world

import com.google.android.filament.MaterialInstance
import com.idlemining.tycoon3d.core.content.GameContent
import io.github.sceneview.loaders.MaterialLoader

/**
 * The procedural modular kit — every environment asset is a named list of
 * primitives. This is the "geometry source" the vertical slice ships with; when
 * the AI asset pipeline lands, generated GLB assets replace kit pieces by id
 * (see core/providers/AssetProviders.kt + docs/05_ASSET_PIPELINE.md).
 */

enum class PrimKind { BOX, CYLINDER, SPHERE, CONE }

/**
 * Palette keys resolved against world data (grass/dirt/rock/cliff come from
 * world.json) and a fixed accent set.
 */
enum class KitColor {
    GRASS, DIRT, ROCK, CLIFF,
    TRUNK, FOLIAGE_A, FOLIAGE_B,
    WOOD, WOOD_DARK,
    STEEL, STEEL_DARK, MACHINE,
    RED, CANVAS, GOLD, LAMP_GLOW,
    DARK, TUNNEL,
    WORKER_BODY, WORKER_SKIN, WORKER_HELMET, PICKAXE,
}

/**
 * One primitive of a kit piece.
 * - BOX: a=width, b=height, c=depth
 * - CYLINDER / CONE: a=radius, b=height
 * - SPHERE: a=radius
 * Local offset (dx, dy, dz); optional yaw and non-uniform scale.
 */
data class Prim(
    val kind: PrimKind,
    /** BOX: width. CYLINDER/CONE: radius. SPHERE: radius. */
    val a: Float,
    /** BOX: height. CYLINDER/CONE: height. Unused for SPHERE. */
    val b: Float = 0f,
    /** BOX: depth. Unused otherwise. */
    val c: Float = 0f,
    val color: KitColor,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val dz: Float = 0f,
    val rotY: Float = 0f,
    val sx: Float = 1f,
    val sy: Float = 1f,
    val sz: Float = 1f,
)

data class KitPiece(val id: String, val prims: List<Prim>)

/** All kit pieces, addressed by id from world.json placements. */
object KitCatalog {

    val pieces: Map<String, KitPiece> = listOf(
        piece(
            "tree_pine",
            Prim(PrimKind.CYLINDER, 0.14f, 0.9f, color = KitColor.TRUNK, dy = 0.45f),
            Prim(PrimKind.CONE, 0.72f, 1.9f, color = KitColor.FOLIAGE_A, dy = 1.55f),
            Prim(PrimKind.CONE, 0.5f, 1.4f, color = KitColor.FOLIAGE_B, dy = 2.5f),
        ),
        piece(
            "tree_round",
            Prim(PrimKind.CYLINDER, 0.13f, 0.9f, color = KitColor.TRUNK, dy = 0.45f),
            Prim(PrimKind.SPHERE, 0.68f, color = KitColor.FOLIAGE_B, dy = 1.5f, sy = 0.92f),
        ),
        piece(
            "rock_scatter",
            Prim(PrimKind.BOX, 0.55f, 0.4f, 0.5f, KitColor.ROCK, dy = 0.18f, rotY = 12f),
            Prim(PrimKind.BOX, 0.32f, 0.24f, 0.3f, KitColor.ROCK, dx = 0.28f, dy = 0.1f, dz = 0.16f, rotY = 40f),
        ),
        piece(
            "fence",
            Prim(PrimKind.BOX, 0.09f, 0.78f, 0.09f, KitColor.WOOD_DARK, dx = -0.82f, dy = 0.39f),
            Prim(PrimKind.BOX, 0.09f, 0.78f, 0.09f, KitColor.WOOD_DARK, dx = 0.82f, dy = 0.39f),
            Prim(PrimKind.BOX, 1.8f, 0.1f, 0.07f, KitColor.WOOD, dy = 0.42f),
            Prim(PrimKind.BOX, 1.8f, 0.1f, 0.07f, KitColor.WOOD, dy = 0.66f),
        ),
        piece(
            "crate",
            Prim(PrimKind.BOX, 0.56f, 0.56f, 0.56f, KitColor.WOOD, dy = 0.28f),
            Prim(PrimKind.BOX, 0.6f, 0.08f, 0.6f, KitColor.WOOD_DARK, dy = 0.04f),
            Prim(PrimKind.BOX, 0.6f, 0.1f, 0.12f, KitColor.WOOD_DARK, dy = 0.28f),
        ),
        piece(
            "barrel",
            Prim(PrimKind.CYLINDER, 0.22f, 0.52f, color = KitColor.STEEL_DARK, dy = 0.26f),
            Prim(PrimKind.CYLINDER, 0.235f, 0.06f, color = KitColor.RED, dy = 0.18f),
            Prim(PrimKind.CYLINDER, 0.235f, 0.06f, color = KitColor.RED, dy = 0.36f),
        ),
        piece(
            "lamp",
            Prim(PrimKind.CYLINDER, 0.05f, 1.9f, color = KitColor.STEEL_DARK, dy = 0.95f),
            Prim(PrimKind.BOX, 0.26f, 0.18f, 0.26f, KitColor.STEEL, dy = 1.95f),
            Prim(PrimKind.BOX, 0.16f, 0.16f, 0.16f, KitColor.LAMP_GLOW, dy = 1.86f),
        ),
        piece(
            "cliff_block",
            Prim(PrimKind.BOX, 3.0f, 5.0f, 2.0f, KitColor.CLIFF, dy = 2.5f),
            Prim(PrimKind.BOX, 2.4f, 1.3f, 1.7f, KitColor.CLIFF, dx = 0.25f, dy = 5.2f, rotY = 14f),
        ),
        piece(
            "cliff_top",
            Prim(PrimKind.BOX, 2.2f, 1.2f, 1.6f, KitColor.CLIFF, dy = 0.6f, rotY = 26f),
        ),
        piece(
            "grass_tuft",
            Prim(PrimKind.CONE, 0.14f, 0.34f, color = KitColor.FOLIAGE_A, dy = 0.17f),
            Prim(PrimKind.CONE, 0.11f, 0.26f, color = KitColor.FOLIAGE_B, dx = 0.12f, dy = 0.13f, rotY = 40f),
        ),
        piece(
            "mine_post",
            Prim(PrimKind.BOX, 0.3f, 3.3f, 0.3f, KitColor.WOOD, dy = 1.65f),
        ),
        piece(
            "mine_beam",
            Prim(PrimKind.BOX, 7.6f, 0.32f, 0.32f, KitColor.WOOD, dy = 3.2f),
        ),
        piece(
            "sack",
            Prim(PrimKind.SPHERE, 0.24f, color = KitColor.CANVAS, dy = 0.2f, sy = 0.85f),
        ),
        piece(
            "extractor_machine",
            Prim(PrimKind.BOX, 1.3f, 0.8f, 1.0f, KitColor.MACHINE, dy = 0.4f),
            Prim(PrimKind.CYLINDER, 0.28f, 1.2f, color = KitColor.STEEL, dy = 1.0f),
            Prim(PrimKind.BOX, 0.2f, 0.2f, 0.2f, KitColor.LAMP_GLOW, dy = 0.85f, dx = 0.42f),
        ),
    ).associateBy { it.id }

    private fun piece(id: String, vararg prims: Prim) = KitPiece(id, prims.toList())

    fun has(id: String): Boolean = id in pieces || id in ROCK_BODIES

    /** Mine-node rock bodies (crystals are added per node with resource colors). */
    val ROCK_BODIES = setOf("rock_small", "rock_large", "rock_boulder")
}

/** Resolves palette keys to cached Filament material instances. */
class KitMaterials(
    private val loader: MaterialLoader,
    private val content: GameContent,
) {
    private val cache = HashMap<KitColor, MaterialInstance>()

    /**
     * Resource materials are requested on every vein break (the ore drops):
     * caching them keeps a long session from allocating — and leaking, since
     * nothing ever destroyed them — one GPU-backed MaterialInstance per break.
     */
    private val resourceCache = HashMap<String, MaterialInstance>()

    /**
     * PBR surface finish per palette key — Phase 2. The base color comes from
     * the palette; these parameters give every material a distinct physical
     * personality under the sun + sky fill: brushed metal for steel, near-mirror
     * for gold, chalky matte for rock, low-luster for wood.
     */
    private data class Surface(
        val metallic: Float = 0f,
        val roughness: Float = 0.85f,
        val reflectance: Float = 0.04f,
        /** Unlit materials ignore lighting (used for glowing lamp filaments). */
        val unlit: Boolean = false,
    )

    private val surfaces = mapOf(
        KitColor.GRASS to Surface(roughness = 1f, reflectance = 0f),
        KitColor.DIRT to Surface(roughness = 1f, reflectance = 0f),
        KitColor.ROCK to Surface(roughness = 0.92f, reflectance = 0.02f),
        KitColor.CLIFF to Surface(roughness = 0.88f, reflectance = 0.03f),
        KitColor.TRUNK to Surface(roughness = 0.9f, reflectance = 0.02f),
        KitColor.FOLIAGE_A to Surface(roughness = 0.95f, reflectance = 0.01f),
        KitColor.FOLIAGE_B to Surface(roughness = 0.95f, reflectance = 0.01f),
        KitColor.WOOD to Surface(roughness = 0.8f, reflectance = 0.05f),
        KitColor.WOOD_DARK to Surface(roughness = 0.75f, reflectance = 0.05f),
        KitColor.STEEL to Surface(metallic = 1f, roughness = 0.34f, reflectance = 0.5f),
        KitColor.STEEL_DARK to Surface(metallic = 1f, roughness = 0.46f, reflectance = 0.5f),
        KitColor.MACHINE to Surface(metallic = 0.85f, roughness = 0.55f, reflectance = 0.4f),
        KitColor.RED to Surface(roughness = 0.6f, reflectance = 0.06f),
        KitColor.CANVAS to Surface(roughness = 0.9f, reflectance = 0.03f),
        KitColor.GOLD to Surface(metallic = 1f, roughness = 0.22f, reflectance = 0.6f),
        KitColor.LAMP_GLOW to Surface(unlit = true),
        KitColor.DARK to Surface(roughness = 0.95f, reflectance = 0f),
        KitColor.TUNNEL to Surface(roughness = 1f, reflectance = 0f),
        KitColor.WORKER_BODY to Surface(roughness = 0.7f, reflectance = 0.05f),
        KitColor.WORKER_SKIN to Surface(roughness = 0.55f, reflectance = 0.04f),
        KitColor.WORKER_HELMET to Surface(roughness = 0.45f, reflectance = 0.08f),
        KitColor.PICKAXE to Surface(metallic = 1f, roughness = 0.3f, reflectance = 0.55f),
    )

    /** Ore crystals: polished-gem sparkle (low roughness, high reflectance). */
    private val crystal = Surface(metallic = 0.05f, roughness = 0.16f, reflectance = 0.35f)

    private val fixed = mapOf(
        KitColor.TRUNK to 0xFF795548L,
        KitColor.FOLIAGE_A to 0xFF388E3CL,
        KitColor.FOLIAGE_B to 0xFF43A047L,
        KitColor.WOOD to 0xFF8D6E63L,
        KitColor.WOOD_DARK to 0xFF6D4C41L,
        KitColor.STEEL to 0xFF90A4AEL,
        KitColor.STEEL_DARK to 0xFF37474FL,
        KitColor.MACHINE to 0xFF455A64L,
        KitColor.RED to 0xFFE53935L,
        KitColor.CANVAS to 0xFFD7CCC8L,
        KitColor.GOLD to 0xFFFFC93CL,
        KitColor.LAMP_GLOW to 0xFFFFE082L,
        KitColor.DARK to 0xFF2B3640L,
        KitColor.TUNNEL to 0xFF37474FL,
        KitColor.WORKER_BODY to 0xFF546E7AL,
        KitColor.WORKER_SKIN to 0xFFFFCCBCL,
        KitColor.WORKER_HELMET to 0xFFFFC107L,
        KitColor.PICKAXE to 0xFFFFB300L,
    )

    fun of(color: KitColor): MaterialInstance = cache.getOrPut(color) {
        val argb = when (color) {
            KitColor.GRASS -> content.grassArgb
            KitColor.DIRT -> content.dirtArgb
            KitColor.ROCK -> content.rockArgb
            KitColor.CLIFF -> content.cliffArgb
            else -> fixed[color] ?: 0xFF90A4AEL
        }
        val surface = surfaces[color] ?: Surface()
        if (surface.unlit) {
            // Unlit + bloom threshold = a lamp that genuinely glows.
            loader.createUnlitColorInstance(argb.toInt())
        } else {
            loader.createColorInstance(
                color = argb.toInt(),
                metallic = surface.metallic,
                roughness = surface.roughness,
                reflectance = surface.reflectance,
            )
        }
    }

    fun of(argb: Long): MaterialInstance = loader.createColorInstance(argb.toInt())

    fun resource(resourceId: String): MaterialInstance = resourceCache.getOrPut(resourceId) {
        loader.createColorInstance(
            color = content.resourceArgb(resourceId).toInt(),
            metallic = crystal.metallic,
            roughness = crystal.roughness,
            reflectance = crystal.reflectance,
        )
    }
}
