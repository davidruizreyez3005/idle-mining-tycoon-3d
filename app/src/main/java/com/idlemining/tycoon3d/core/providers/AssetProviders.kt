package com.idlemining.tycoon3d.core.providers

/**
 * Provider abstractions for the AI asset pipeline.
 *
 * The game must be able to swap HOW assets are produced without touching gameplay
 * code. In the shipped vertical slice every 3D asset is procedural (composed from
 * Filament primitives by `game/world/KitCatalog`). As the AI pipeline comes online,
 * generated `.glb` files replace kit pieces one by one — this seam is where they
 * plug in.
 *
 * The full pipeline design (prompt → generate → validate → optimize → LOD →
 * import → manifest) is documented in docs/05_ASSET_PIPELINE.md.
 */

/**
 * A single environment/world asset exposed to the game. Assets are addressed by
 * stable string ids (e.g. "tree_pine", "rock_large") so content JSON never breaks
 * when the underlying geometry source changes.
 */
interface WorldKitProvider {
    val id: String

    /** Kit piece ids this provider can build. */
    val pieces: Set<String>

    fun hasPiece(pieceId: String): Boolean = pieceId in pieces
}

/**
 * The procedural provider — the only implementation in the vertical slice.
 * Piece geometry lives in `game/world/KitCatalog` and renders through the
 * SceneView node DSL.
 */
object ProceduralKitProvider : WorldKitProvider {
    override val id: String = "procedural-v1"

    override val pieces: Set<String> = setOf(
        // rocks / mine nodes
        "rock_small", "rock_large", "rock_boulder",
        // terrain
        "cliff_block", "cliff_top", "tunnel_floor", "path",
        // vegetation & scatter
        "tree_pine", "tree_round", "rock_scatter", "grass_tuft",
        // structures
        "mine_post", "mine_beam", "mine_sign",
        "fence", "crate", "barrel", "lamp",
        // depot
        "depot_platform", "depot_booth", "depot_conveyor", "depot_sign", "sack",
        // automation
        "extractor_machine",
    )
}

/**
 * Placeholder seams for the AI generation providers. Each becomes a real
 * implementation when its pipeline stage lands (docs/05_ASSET_PIPELINE.md):
 * development-time only — the shipped game never needs API credentials.
 */
interface TextModelProvider {
    suspend fun complete(prompt: String): String
}

interface ImageProvider {
    suspend fun generateImage(prompt: String, size: String): ByteArray
}

interface ThreeDModelProvider {
    /** Returns raw glTF/GLB bytes for the prompt. */
    suspend fun generateModel(prompt: String): ByteArray
}

interface AssetProcessingProvider {
    /** Validates + optimizes a GLB (draco/meshopt, texture compression, LODs). */
    suspend fun process(glbBytes: ByteArray): ByteArray
}

interface AnimationProvider {
    /** Generates an animation clip for a model. */
    suspend fun animate(modelId: String, prompt: String): ByteArray
}

/**
 * The manifest of assets currently live in the build. The AI pipeline writes
 * here after a generated asset passes validation; the game resolves kit pieces
 * through this manifest first, procedural fallback second.
 */
data class AssetManifest(
    val version: Int = 1,
    val assets: List<ManifestEntry> = emptyList(),
) {
    data class ManifestEntry(
        val id: String,
        val file: String,
        val source: String,
        val lodCount: Int = 1,
        val vertexBudget: Int = 0,
        val validated: Boolean = false,
    )
}
