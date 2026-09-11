package com.idlemining.tycoon3d.game.world

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.idlemining.tycoon3d.core.content.GameContent
import com.idlemining.tycoon3d.game.scene.NodeRegistry
import com.idlemining.tycoon3d.game.scene.SceneRefs
import io.github.sceneview.SceneScope
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.ConeNode
import io.github.sceneview.node.CapsuleNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds the entire 3D zone from `world.json` data — ground, dirt path, cliff
 * wall with the mine entrance, every prop placement, the mine nodes with their
 * resource-colored crystals, the depot, the Auto-Extractor and the controllable
 * worker. Composes exactly once; no Compose state is read here (animator owns
 * all per-frame transforms through [SceneRefs]).
 */
@Composable
fun SceneScope.WorldBuilder(
    content: GameContent,
    refs: SceneRefs,
    registry: NodeRegistry,
    mats: KitMaterials,
) {
    val world = content.world

    Ground(content, mats, registry)
    CliffWall(content, mats)
    MineEntrance(content, mats, refs, registry)
    Props(content, mats)
    MineNodes(content, refs, registry, mats)
    Depot(content, mats, refs, registry)
    Extractor(content, refs, mats)
    Worker(refs, mats, registry)
    EffectPools(refs, mats)
}

// --------------------------------------------------------------------- ground

@Composable
private fun SceneScope.Ground(
    content: GameContent,
    mats: KitMaterials,
    registry: NodeRegistry,
) {
    val w = content.world
    val sx = w.ground.size[0]
    val sz = w.ground.size[1]

    // Grass base — the tap-to-move surface.
    CubeNode(
        size = Size(sx, 0.5f, sz),
        materialInstance = mats.of(KitColor.GRASS),
        position = Position(0f, -0.25f, 0f),
        apply = { registry.registerGround(this) },
    )

    // Dirt path from the depot down to the mine.
    val pathLen = (w.depot.position[1] - 2.5f) - (w.cliff.z + w.cliff.depth / 2 + 1.5f)
    val pathCenterZ = (w.depot.position[1] - 2.5f + w.cliff.z + w.cliff.depth / 2 + 1.5f) / 2f
    CubeNode(
        size = Size(w.ground.pathWidth, 0.04f, pathLen),
        materialInstance = mats.of(KitColor.DIRT),
        position = Position(0f, 0.02f, pathCenterZ),
    )

    // Mining apron in front of the cliff.
    CubeNode(
        size = Size(w.cliff.halfSpan * 2.2f, 0.03f, 7.5f),
        materialInstance = mats.of(KitColor.DIRT),
        position = Position(0f, 0.015f, w.cliff.z + w.cliff.depth / 2 + 3.2f),
    )
}

// ---------------------------------------------------------------------- cliff

@Composable
private fun SceneScope.CliffWall(content: GameContent, mats: KitMaterials) {
    val cliff = content.world.cliff
    val blockWidth = 3.0f
    val innerEdge = cliff.entranceHalfWidth
    val outerEdge = cliff.halfSpan

    // Wall segments flanking the entrance gap, both sides.
    val segmentsPerSide = ceil((outerEdge - innerEdge) / blockWidth).toInt()
    for (side in intArrayOf(-1, 1)) {
        for (i in 0 until segmentsPerSide) {
            val x = side * (innerEdge + blockWidth / 2 + i * blockWidth)
            PlacePiece("cliff_block", x, cliff.z, 1f, 0f, mats)
        }
    }

    // Back wall closing the tunnel.
    val backSegments = ceil(innerEdge * 2f / blockWidth).toInt()
    val backZ = cliff.z - cliff.depth / 2 + 0.8f
    for (i in 0 until backSegments) {
        val x = -innerEdge + blockWidth / 2 + i * blockWidth
        PlacePiece("cliff_block", x, backZ, 1f, 0f, mats)
    }

    // Dark tunnel floor.
    CubeNode(
        size = Size(innerEdge * 2f, 0.08f, cliff.depth),
        materialInstance = mats.of(KitColor.TUNNEL),
        position = Position(0f, 0.04f, cliff.z),
    )
    // Gravel strip inside the tunnel.
    CubeNode(
        size = Size(innerEdge * 1.5f, 0.05f, cliff.depth * 0.8f),
        materialInstance = mats.of(KitColor.DARK),
        position = Position(0f, 0.07f, cliff.z),
    )

    // Scattered top caps for silhouette variety.
    PlacePiece("cliff_top", -outerEdge + 0.6f, cliff.z - cliff.depth / 2 - 0.6f, 1.2f, 30f, mats)
    PlacePiece("cliff_top", outerEdge - 0.8f, cliff.z - cliff.depth / 2 - 0.4f, 1.1f, 70f, mats)
    PlacePiece("cliff_top", -outerEdge + 1.5f, cliff.z + cliff.depth / 2 + 0.5f, 0.9f, 10f, mats)
    PlacePiece("cliff_top", outerEdge - 1.8f, cliff.z + cliff.depth / 2 + 0.3f, 1.0f, 55f, mats)
}

@Composable
private fun SceneScope.MineEntrance(
    content: GameContent,
    mats: KitMaterials,
    refs: SceneRefs,
    registry: NodeRegistry,
) {
    val cliff = content.world.cliff
    val halfW = cliff.entranceHalfWidth

    // Timber posts + lintel framing the entrance.
    PlacePiece("mine_post", -halfW + 0.45f, cliff.z + cliff.depth / 2, 1f, 0f, mats)
    PlacePiece("mine_post", halfW - 0.45f, cliff.z + cliff.depth / 2, 1f, 0f, mats)

    // Entrance frame: lintel + warning sign above the posts (world-space wrapper).
    Node(position = Position(0f, 0f, cliff.z + cliff.depth / 2)) {
        CubeNode(
            size = Size(halfW * 2f, 0.3f, 0.3f),
            materialInstance = mats.of(KitColor.WOOD),
            position = Position(0f, 3.35f, 0f),
        )
        // Warning sign board above the lintel.
        CubeNode(
            size = Size(1.1f, 0.55f, 0.08f),
            materialInstance = mats.of(KitColor.WOOD_DARK),
            position = Position(0f, 3.85f, 0f),
        )
        // Gold coin marker on the sign.
        CylinderNode(
            radius = 0.18f,
            height = 0.06f,
            materialInstance = mats.of(KitColor.GOLD),
            position = Position(0f, 3.85f, 0.06f),
            rotation = Rotation(0f, 90f, 0f),
        )
    }

    // Interior support beams down the tunnel.
    PlacePiece("mine_post", -halfW + 0.45f, cliff.z - 1.2f, 0.85f, 0f, mats)
    PlacePiece("mine_post", halfW - 0.45f, cliff.z - 1.2f, 0.85f, 0f, mats)
    Node(position = Position(0f, 0f, cliff.z - 1.2f)) {
        CubeNode(
            size = Size(halfW * 1.8f, 0.24f, 0.24f),
            materialInstance = mats.of(KitColor.WOOD),
            position = Position(0f, 2.9f, 0f),
        )
    }
}

// ---------------------------------------------------------------------- props

@Composable
private fun SceneScope.Props(content: GameContent, mats: KitMaterials) {
    for (p in content.world.props) {
        PlacePiece(p.piece, p.at[0], p.at[1], p.scale, p.rotation, mats)
    }
}

/** Places a kit piece (wrapper node with yaw + scale, prims inside). */
@Composable
fun SceneScope.PlacePiece(
    pieceId: String,
    x: Float,
    z: Float,
    scale: Float,
    yawDeg: Float,
    mats: KitMaterials,
) {
    val piece = KitCatalog.pieces[pieceId] ?: return
    Node(
        position = Position(x, 0f, z),
        rotation = Rotation(0f, yawDeg, 0f),
        scale = Scale(scale),
    ) {
        for (prim in piece.prims) {
            RenderPrim(prim, mats)
        }
    }
}

/** Renders one kit primitive inside the current scope. */
@Composable
private fun SceneScope.RenderPrim(prim: com.idlemining.tycoon3d.game.world.Prim, mats: KitMaterials) {
    when (prim.kind) {
        PrimKind.BOX -> CubeNode(
            size = Size(prim.a, prim.b, prim.c),
            materialInstance = mats.of(prim.color),
            position = Position(prim.dx, prim.dy, prim.dz),
            rotation = Rotation(0f, prim.rotY, 0f),
        )

        PrimKind.CYLINDER -> CylinderNode(
            radius = prim.a,
            height = prim.b,
            materialInstance = mats.of(prim.color),
            position = Position(prim.dx, prim.dy, prim.dz),
            rotation = Rotation(0f, prim.rotY, 0f),
        )

        PrimKind.SPHERE -> SphereNode(
            radius = prim.a,
            materialInstance = mats.of(prim.color),
            position = Position(prim.dx, prim.dy, prim.dz),
            scale = Scale(prim.sx, prim.sy, prim.sz),
        )

        PrimKind.CONE -> ConeNode(
            radius = prim.a,
            height = prim.b,
            materialInstance = mats.of(prim.color),
            position = Position(prim.dx, prim.dy, prim.dz),
            rotation = Rotation(0f, prim.rotY, 0f),
        )
    }
}

// ----------------------------------------------------------------- mine nodes

@Composable
private fun SceneScope.MineNodes(
    content: GameContent,
    refs: SceneRefs,
    registry: NodeRegistry,
    mats: KitMaterials,
) {
    content.world.nodes.forEachIndexed { index, placement ->
        val def = content.nodeType(placement.typeId)
        val rockColor = KitColor.ROCK
        val resMat = mats.resource(def.primaryResource)

        Node(
            position = Position(placement.at[0], 0f, placement.at[1]),
            scale = Scale(placement.scale * def.scale),
            apply = {
                refs.nodes[index].root = this
                registry.registerNode(this, index)
            },
        ) {
            // Everything breakable lives under one wrapper the animator scales
            // with damage + regrowth (the spheres keep their own deformation).
            Node(apply = { refs.nodes[index].body = this }) {
            // Rock body (1-3 deformed spheres depending on the mesh).
            when (def.mesh) {
                "rock_boulder" -> {
                    SphereNode(
                        radius = 0.85f,
                        materialInstance = mats.of(rockColor),
                        position = Position(0f, 0.55f, 0f),
                        scale = Scale(1f, 0.8f, 1.05f),
                    )
                    SphereNode(
                        radius = 0.5f,
                        materialInstance = mats.of(rockColor),
                        position = Position(0.62f, 0.4f, 0.3f),
                        scale = Scale(1f, 0.75f, 1f),
                    )
                    SphereNode(
                        radius = 0.42f,
                        materialInstance = mats.of(rockColor),
                        position = Position(-0.58f, 0.35f, -0.25f),
                        scale = Scale(1f, 0.7f, 1.1f),
                    )
                }

                "rock_large" -> {
                    SphereNode(
                        radius = 0.7f,
                        materialInstance = mats.of(rockColor),
                        position = Position(0f, 0.45f, 0f),
                        scale = Scale(1f, 0.85f, 1f),
                    )
                    SphereNode(
                        radius = 0.45f,
                        materialInstance = mats.of(rockColor),
                        position = Position(0.38f, 0.75f, -0.2f),
                        scale = Scale(1f, 0.8f, 1f),
                    )
                }

                else -> {
                    SphereNode(
                        radius = 0.55f,
                        materialInstance = mats.of(rockColor),
                        position = Position(0f, 0.38f, 0f),
                        scale = Scale(1f, 0.8f, 1f),
                    )
                }
            }

            // Ore crystals studding the surface — the visual resource tell.
            Node(apply = { refs.nodes[index].crystalGroup = this }) {
                for (c in 0 until def.crystals) {
                    val angle = (index * 137.5f + c * 90f) * (Math.PI / 180f).toFloat()
                    val radius = 0.34f + 0.14f * ((c + index) % 3)
                    val height = 0.5f + 0.2f * ((c * 2 + index) % 3)
                    CubeNode(
                        size = Size(0.2f, 0.26f, 0.2f),
                        materialInstance = resMat,
                        position = Position(
                            cos(angle) * radius,
                            height,
                            sin(angle) * radius,
                        ),
                        rotation = Rotation(45f, angle * 57.3f, 0f),
                    )
                }
            }
            }
        }
    }
}

// ---------------------------------------------------------------------- depot

@Composable
private fun SceneScope.Depot(
    content: GameContent,
    mats: KitMaterials,
    refs: SceneRefs,
    registry: NodeRegistry,
) {
    val d = content.world.depot
    val x = d.position[0]
    val z = d.position[1]

    Node(
        position = Position(x, 0f, z),
        rotation = Rotation(0f, d.facing, 0f),
        apply = { registry.registerDepot(this) },
    ) {
        // Platform.
        CubeNode(
            size = Size(6f, 0.3f, 4.2f),
            materialInstance = mats.of(KitColor.WOOD),
            position = Position(0f, 0.15f, 0f),
        )
        // Booth: counter, posts, striped roof.
        CubeNode(
            size = Size(2.4f, 1.0f, 1.1f),
            materialInstance = mats.of(KitColor.WOOD_DARK),
            position = Position(-1.4f, 0.8f, 0.4f),
        )
        CubeNode(
            size = Size(0.09f, 2.2f, 0.09f),
            materialInstance = mats.of(KitColor.STEEL),
            position = Position(-2.5f, 1.1f, 0.4f),
        )
        CubeNode(
            size = Size(0.09f, 2.2f, 0.09f),
            materialInstance = mats.of(KitColor.STEEL),
            position = Position(-0.3f, 1.1f, 0.4f),
        )
        CubeNode(
            size = Size(2.9f, 0.16f, 1.6f),
            materialInstance = mats.of(KitColor.RED),
            position = Position(-1.4f, 2.3f, 0.4f),
        )
        for (s in 0 until 3) {
            CubeNode(
                size = Size(0.42f, 0.17f, 1.62f),
                materialInstance = mats.of(KitColor.CANVAS),
                position = Position(-2.4f + s * 1.0f, 2.32f, 0.4f),
            )
        }

        // Mini conveyor on the right side.
        CubeNode(
            size = Size(2.6f, 0.14f, 0.8f),
            materialInstance = mats.of(KitColor.STEEL_DARK),
            position = Position(1.6f, 0.55f, 0.2f),
        )
        CubeNode(
            size = Size(0.12f, 0.4f, 0.12f),
            materialInstance = mats.of(KitColor.STEEL),
            position = Position(0.6f, 0.35f, 0.0f),
        )
        CubeNode(
            size = Size(0.12f, 0.4f, 0.12f),
            materialInstance = mats.of(KitColor.STEEL),
            position = Position(0.6f, 0.35f, 0.4f),
        )
        CubeNode(
            size = Size(0.12f, 0.4f, 0.12f),
            materialInstance = mats.of(KitColor.STEEL),
            position = Position(2.6f, 0.35f, 0.0f),
        )
        CubeNode(
            size = Size(0.12f, 0.4f, 0.12f),
            materialInstance = mats.of(KitColor.STEEL),
            position = Position(2.6f, 0.35f, 0.4f),
        )

        // Trade sacks.
        PlacePiece("sack", 2.2f, -1.4f, 1f, 0f, mats)
        PlacePiece("sack", 1.7f, -1.6f, 0.85f, 20f, mats)

        // Spinning coin sign — the "sell here" beacon.
        Node {
            CylinderNode(
                radius = 0.06f,
                height = 2.6f,
                materialInstance = mats.of(KitColor.STEEL),
                position = Position(0f, 1.3f, 1.8f),
            )
            CylinderNode(
                radius = 0.36f,
                height = 0.08f,
                materialInstance = mats.of(KitColor.GOLD),
                position = Position(0f, 2.75f, 1.8f),
                rotation = Rotation(0f, 90f, 0f),
                apply = { refs.depotSign = this },
            )
        }
    }
}

// ------------------------------------------------------------------ extractor

@Composable
private fun SceneScope.Extractor(content: GameContent, refs: SceneRefs, mats: KitMaterials) {
    val cliff = content.world.cliff
    val x = cliff.entranceHalfWidth + 1.6f
    val z = cliff.z + cliff.depth / 2 + 2.2f

    Node(
        position = Position(x, 0f, z),
        apply = { refs.extractor = this },
    ) {
        for (prim in KitCatalog.pieces.getValue("extractor_machine").prims) {
            RenderPrim(prim, mats)
        }
        // Animated drill bit.
        ConeNode(
            radius = 0.16f,
            height = 0.5f,
            materialInstance = mats.of(KitColor.STEEL_DARK),
            position = Position(0f, 0.22f, 0.3f),
            rotation = Rotation(180f, 0f, 0f),
            apply = { refs.extractorDrill = this },
        )
    }
}

// --------------------------------------------------------------------- worker

@Composable
private fun SceneScope.Worker(refs: SceneRefs, mats: KitMaterials, registry: NodeRegistry) {
    Node(
        position = Position(0f, 0f, 0f),
        apply = {
            refs.worker.root = this
            registry.registerWorker(this)
        },
    ) {
        // Torso.
        CapsuleNode(
            radius = 0.22f,
            height = 0.5f,
            materialInstance = mats.of(KitColor.WORKER_BODY),
            position = Position(0f, 0.58f, 0f),
            apply = { refs.worker.body = this },
        )
        // Head + helmet with brim.
        SphereNode(
            radius = 0.16f,
            materialInstance = mats.of(KitColor.WORKER_SKIN),
            position = Position(0f, 1.02f, 0f),
            apply = { refs.worker.head = this },
        )
        CylinderNode(
            radius = 0.18f,
            height = 0.13f,
            materialInstance = mats.of(KitColor.WORKER_HELMET),
            position = Position(0f, 1.16f, 0f),
        )
        CubeNode(
            size = Size(0.3f, 0.05f, 0.34f),
            materialInstance = mats.of(KitColor.WORKER_HELMET),
            position = Position(0f, 1.1f, 0.06f),
        )
        // Backpack (scales with how full the pack is — animated).
        CubeNode(
            size = Size(0.38f, 0.42f, 0.22f),
            materialInstance = mats.of(KitColor.WOOD_DARK),
            position = Position(0f, 0.68f, -0.26f),
            apply = { refs.worker.backpack = this },
        )

        // Legs — pivot nodes so swings rotate at the hip.
        for (i in 0 until 2) {
            val side = if (i == 0) -0.09f else 0.09f
            Node(
                position = Position(side, 0.36f, 0f),
                apply = { refs.worker.legPivots[i] = this },
            ) {
                CubeNode(
                    size = Size(0.11f, 0.36f, 0.13f),
                    materialInstance = mats.of(KitColor.WORKER_BODY),
                    position = Position(0f, -0.18f, 0f),
                )
            }
        }

        // Arms — pivots at the shoulders. The right arm carries the pickaxe.
        for (i in 0 until 2) {
            val side = if (i == 0) -0.28f else 0.28f
            Node(
                position = Position(side, 0.82f, 0f),
                apply = { refs.worker.armPivots[i] = this },
            ) {
                CubeNode(
                    size = Size(0.09f, 0.32f, 0.11f),
                    materialInstance = mats.of(KitColor.WORKER_BODY),
                    position = Position(0f, -0.16f, 0f),
                )
                if (i == 1) {
                    Node(
                        position = Position(0f, -0.3f, 0.06f),
                        apply = { refs.worker.pickaxe = this },
                    ) {
                        CylinderNode(
                            radius = 0.028f,
                            height = 0.6f,
                            materialInstance = mats.of(KitColor.WOOD),
                            position = Position(0f, 0f, 0.14f),
                            rotation = Rotation(70f, 0f, 0f),
                        )
                        CubeNode(
                            size = Size(0.34f, 0.07f, 0.09f),
                            materialInstance = mats.of(KitColor.PICKAXE),
                            position = Position(0f, 0.06f, 0.42f),
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- fx pools

@Composable
private fun SceneScope.EffectPools(refs: SceneRefs, mats: KitMaterials) {
    // Ore drops that fly to the worker after a node breaks.
    for (i in 0 until SceneRefs.DROP_POOL) {
        SphereNode(
            radius = 0.1f,
            materialInstance = mats.of(KitColor.GOLD),
            position = Position(0f, -50f, 0f),
            apply = {
                refs.drops[i] = this
                isVisible = false
            },
        )
    }
    // Rock fragments with fake physics.
    for (i in 0 until SceneRefs.FRAGMENT_POOL) {
        CubeNode(
            size = Size(0.16f, 0.14f, 0.16f),
            materialInstance = mats.of(KitColor.ROCK),
            position = Position(0f, -50f, 0f),
            apply = {
                refs.fragments[i] = this
                isVisible = false
            },
        )
    }
}
