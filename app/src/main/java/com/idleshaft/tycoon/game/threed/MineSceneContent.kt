package com.idleshaft.tycoon.game.threed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.idleshaft.tycoon.domain.OreType
import io.github.sceneview.NodeScope
import io.github.sceneview.SceneScope
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.MeshNode

@Composable
private fun SceneScope.rememberToonMaterials(): ToonMaterials =
    remember(materialLoader) { ToonMaterials(materialLoader) }

@Composable
private fun SceneScope.rememberShapes(): ToonShapes =
    remember(engine) { ToonShapes(engine) }

// ---------------------------------------------------------------------------
// Static world construction. Every node is created exactly once; animated ones
// are captured into [MineWorld] through `apply` blocks. No Compose state is read
// here — the content composes once and never recomposes.
//
// Every prop is a composite rig built from primitives + custom [ToonShapes]
// meshes: miners with articulated arms/legs and pickaxes, headframes with
// spinning hoist wheels and live cables, trucks with rolling wheels — all
// driven imperatively by [MineAnimator] for a fluid game state.
//
// Layout rules (the "no clipping" contract):
//  - Pits are open-top, open-front cutaways — the underground action is always
//    visible and nothing pokes through solid geometry.
//  - Overlapping solids are always offset by >= 0.02 m so no two faces are
//    coplanar (no z-fighting shimmer).
// ---------------------------------------------------------------------------

@Composable
internal fun SceneScope.MineSceneContent(world: MineWorld) {
    val mats = rememberToonMaterials()
    val shapes = rememberShapes()

    Ground(mats)
    for (i in 0 until MineWorld.ShaftCount) {
        Shaft(i, world, mats, shapes)
    }
    CrossConveyor(world, mats, shapes)
    MainConveyor(world, mats, shapes)
    Crusher(world, mats, shapes)
    BarOutput(world, mats, shapes)
    RoadAndMarket(world, mats, shapes)
    Truck(world, mats, shapes)
    Decor(world, mats, shapes)
    Clouds(world, mats)
}

// --------------------------------------------------------------------- ground

@Composable
private fun SceneScope.Ground(mats: ToonMaterials) {
    // Grass — seven slabs leaving the pit footprints OPEN. The pits are
    // open-top cutaways dug below grade; a single solid grass cube would
    // swallow them (and everything inside), so the ground is assembled
    // around the pit row instead: front, back, left, right + three fillers
    // in the gaps between pits (where the headframe legs stand).
    val grass = mats.of(MineWorld.GRASS)
    // Front slab: z −24 .. 2.9.
    CubeNode(
        size = Size(48f, 0.5f, 26.9f),
        materialInstance = grass,
        position = Position(0f, -0.25f, -10.55f),
    )
    // Back slab: z 6.3 .. 24.
    CubeNode(
        size = Size(48f, 0.5f, 17.7f),
        materialInstance = grass,
        position = Position(0f, -0.25f, 15.15f),
    )
    // Side slabs flanking the pit row (z 2.9 .. 6.3).
    CubeNode(
        size = Size(16.35f, 0.5f, 3.4f),
        materialInstance = grass,
        position = Position(-15.825f, -0.25f, 4.6f),
    )
    CubeNode(
        size = Size(16.35f, 0.5f, 3.4f),
        materialInstance = grass,
        position = Position(15.825f, -0.25f, 4.6f),
    )
    // Gap fillers between the four pits.
    for (gx in listOf(-4.2f, 0f, 4.2f)) {
        CubeNode(
            size = Size(1.6f, 0.5f, 3.4f),
            materialInstance = grass,
            position = Position(gx, -0.25f, 4.6f),
        )
    }
    // Packed-dirt facility deck: front strip stops short of the pit footprints.
    CubeNode(
        size = Size(19f, 0.1f, 8.95f),
        materialInstance = mats.of(MineWorld.DIRT_DECK),
        position = Position(0f, 0.02f, -1.625f),
    )
    // Back strip behind the pit row.
    CubeNode(
        size = Size(19f, 0.1f, 1.0f),
        materialInstance = mats.of(MineWorld.DIRT_DECK),
        position = Position(0f, 0.02f, 6.85f),
    )
}

// --------------------------------------------------------------------- shafts

@Composable
private fun SceneScope.Shaft(i: Int, world: MineWorld, mats: ToonMaterials, shapes: ToonShapes) {
    val x = MineWorld.shaftX(i)
    val z = MineWorld.SHAFT_Z
    val ore = OreType.ordered[i]

    // --- Pit terrain (always visible) --------------------------------------
    // Floor slab.
    CubeNode(
        size = Size(2.6f, 0.2f, 3.4f),
        materialInstance = mats.of(MineWorld.PIT_FLOOR),
        position = Position(x, MineWorld.PIT_FLOOR_Y - 0.1f, z),
    )
    // Back wall (full height — the cutaway backdrop).
    CubeNode(
        size = Size(2.96f, 3.975f, MineWorld.WALL_T),
        materialInstance = mats.of(MineWorld.PIT_WALL),
        position = Position(x, -1.8625f, z + MineWorld.PIT_HALF_D - 0.09f),
    )
    // Side walls.
    CubeNode(
        size = Size(MineWorld.WALL_T, 3.975f, 3.4f),
        materialInstance = mats.of(MineWorld.PIT_WALL),
        position = Position(x - 1.21f, -1.8625f, z),
    )
    CubeNode(
        size = Size(MineWorld.WALL_T, 3.975f, 3.4f),
        materialInstance = mats.of(MineWorld.PIT_WALL),
        position = Position(x + 1.21f, -1.8625f, z),
    )
    // Short front lip (cutaway edge).
    CubeNode(
        size = Size(2.96f, 0.45f, MineWorld.WALL_T),
        materialInstance = mats.of(MineWorld.PIT_WALL),
        position = Position(x, -3.425f, z - MineWorld.PIT_HALF_D + 0.09f),
    )
    // Collar rim framing the pit mouth.
    CubeNode(
        size = Size(3.1f, 0.16f, 0.22f),
        materialInstance = mats.of(MineWorld.ROCK_LID),
        position = Position(x, 0.13f, z - 1.81f),
    )
    CubeNode(
        size = Size(3.1f, 0.16f, 0.22f),
        materialInstance = mats.of(MineWorld.ROCK_LID),
        position = Position(x, 0.13f, z + 1.81f),
    )

    // Rock lid while the shaft is still locked — seated on the collar.
    CubeNode(
        size = Size(2.7f, 0.3f, 3.5f),
        materialInstance = mats.of(MineWorld.ROCK_LID),
        position = Position(x, 0.38f, z),
        apply = { world.shafts[i].lid = this },
    )

    // --- Equipment rig (group-relative, scale-pops on unlock) --------------
    Node(
        position = Position(x, 0f, z),
        apply = { world.shafts[i].group = this },
    ) {
        // Ore seam embedded in the back wall.
        for (v in 0 until 3) {
            MeshPart(
                mesh = shapes.rock(v + 1),
                materialInstance = mats.ore(ore),
                scale = Scale(0.42f),
                position = Position(-0.5f + v * 0.5f, MineWorld.PIT_FLOOR_Y + 0.3f, MineWorld.VEIN_Z),
                apply = { world.shafts[i].vein[v] = this },
            )
        }

        // Underground heap piled against the left wall (visible count tracks the
        // shaft buffer; the walking lane at x −0.7 .. 0.7 stays clear).
        val heapSpots = listOf(
            Position(-1.0f, 0.15f, 0.5f),
            Position(-1.0f, 0.15f, 0.9f),
            Position(-1.0f, 0.38f, 0.7f),
            Position(-1.0f, 0.38f, 1.0f),
        )
        for (s in 0 until 4) {
            MeshPart(
                mesh = shapes.rock(s + 2),
                materialInstance = mats.ore(ore),
                scale = Scale(0.3f),
                position = Position(
                    heapSpots[s].x,
                    MineWorld.PIT_FLOOR_Y + heapSpots[s].y,
                    MineWorld.HEAP_Z + heapSpots[s].z,
                ),
                apply = { world.shafts[i].stockpile[s] = this },
            )
        }

        // Headframe towers over the front of the pit.
        for (lx in listOf(-1.5f, 1.5f)) {
            for (lz in listOf(-1.45f, 0.05f)) {
                CubeNode(
                    size = Size(0.18f, 3.0f, 0.18f),
                    materialInstance = mats.of(MineWorld.WOOD),
                    position = Position(lx, 1.49f, lz),
                )
            }
        }
        for (lx in listOf(-1.5f, 1.5f)) {
            CubeNode(
                size = Size(0.16f, 0.16f, 1.66f),
                materialInstance = mats.of(MineWorld.STEEL),
                position = Position(lx, 2.95f, -0.7f),
            )
        }
        CubeNode(
            size = Size(3.2f, 0.18f, 0.18f),
            materialInstance = mats.of(MineWorld.STEEL),
            position = Position(0f, 2.95f, -0.7f),
        )
        CubeNode(
            size = Size(2.0f, 0.1f, 1.4f),
            materialInstance = mats.of(MineWorld.WOOD_DARK),
            position = Position(0f, 3.12f, -0.7f),
        )
        CubeNode(
            size = Size(0.12f, 0.45f, 0.12f),
            materialInstance = mats.of(MineWorld.STEEL_DARK),
            position = Position(0f, 3.3f, -0.7f),
        )

        // Hoist wheel — spins with the cart, spokes make the spin readable.
        Node(
            position = Position(0f, MineWorld.WHEEL_Y, -0.7f),
            apply = { world.shafts[i].pulley = this },
        ) {
            CylinderNode(
                radius = 0.3f,
                height = 0.1f,
                materialInstance = mats.of(MineWorld.STEEL),
                rotation = Rotation(x = 90f),
            )
            for (s in 0 until 3) {
                CubeNode(
                    size = Size(0.56f, 0.05f, 0.04f),
                    materialInstance = mats.of(MineWorld.STEEL_MID),
                    rotation = Rotation(z = s * 60f),
                )
            }
            CylinderNode(
                radius = 0.07f,
                height = 0.18f,
                materialInstance = mats.of(MineWorld.STEEL_DARK),
                rotation = Rotation(x = 90f),
            )
        }

        // Hoist cable from the wheel down to the cart (scaled every frame).
        CubeNode(
            size = Size(0.045f, 1f, 0.045f),
            materialInstance = mats.of(MineWorld.STEEL_MID),
            position = Position(0f, 1.5f, -0.7f),
            apply = { world.shafts[i].cable = this },
        )

        // Guide rails from the pit floor to the headframe.
        for (rx in listOf(-0.55f, 0.55f)) {
            CubeNode(
                size = Size(0.08f, 6.55f, 0.08f),
                materialInstance = mats.of(MineWorld.STEEL_MID),
                position = Position(rx, -0.5f, -0.7f),
            )
        }

        // Flag on the headframe (standing on the top platform).
        CubeNode(
            size = Size(0.05f, 0.85f, 0.05f),
            materialInstance = mats.of(MineWorld.WOOD_DARK),
            position = Position(0.85f, 3.5f, -0.7f),
        )
        Node(
            position = Position(0.85f, 3.86f, -0.7f),
            apply = { world.shafts[i].flag = this },
        ) {
            CubeNode(
                size = Size(0.36f, 0.2f, 0.03f),
                materialInstance = mats.of(MineWorld.FLAG),
                position = Position(0.19f, 0f, 0f),
            )
        }

        // Elevator cart.
        CartRig(i, world, mats, shapes, ore)

        // Miner crew.
        for (m in 0 until MineWorld.ShaftMinerCount) {
            MinerRig(i, m, world, mats, shapes, ore)
        }
    }
}

@Composable
private fun NodeScope.CartRig(
    i: Int,
    world: MineWorld,
    mats: ToonMaterials,
    shapes: ToonShapes,
    ore: OreType,
) {
    Node(
        position = Position(0f, MineWorld.CART_TOP_Y, MineWorld.CART_Z - MineWorld.SHAFT_Z),
        apply = { world.shafts[i].cart = this },
    ) {
        CubeNode(
            size = Size(0.9f, 0.08f, 0.8f),
            materialInstance = mats.of(MineWorld.CART_RED),
            position = Position(0f, 0.04f, 0f),
        )
        for (px in listOf(-0.4f, 0.4f)) {
            for (pz in listOf(-0.34f, 0.34f)) {
                CubeNode(
                    size = Size(0.07f, 0.4f, 0.07f),
                    materialInstance = mats.of(MineWorld.CART_TRIM),
                    position = Position(px, 0.28f, pz),
                )
            }
        }
        for (rz in listOf(-0.37f, 0.37f)) {
            CubeNode(
                size = Size(0.92f, 0.2f, 0.05f),
                materialInstance = mats.of(MineWorld.CART_RED),
                position = Position(0f, 0.38f, rz),
            )
        }
        CubeNode(
            size = Size(0.05f, 0.2f, 0.66f),
            materialInstance = mats.of(MineWorld.CART_RED),
            position = Position(-0.44f, 0.38f, 0f),
        )
        // Cable hook.
        CubeNode(
            size = Size(0.1f, 0.09f, 0.1f),
            materialInstance = mats.of(MineWorld.STEEL_DARK),
            position = Position(0f, 0.55f, 0f),
        )
        // Ore load slots (presence-driven rock chunks).
        for (c in 0 until 4) {
            MeshPart(
                mesh = shapes.rock(c),
                materialInstance = mats.ore(ore),
                scale = Scale(0.15f),
                position = Position(-0.27f + c * 0.18f, 0.17f, 0f),
                apply = { world.shafts[i].cartLoad[c] = this },
            )
        }
    }
}

@Composable
private fun NodeScope.MinerRig(
    i: Int,
    m: Int,
    world: MineWorld,
    mats: ToonMaterials,
    shapes: ToonShapes,
    ore: OreType,
) {
    val rig = world.shafts[i].miners[m]
    Node(
        position = Position((m - 1) * 0.7f, MineWorld.MINER_Y, MineWorld.MINER_MINE_Z),
        apply = { rig.root = this },
    ) {
        // Legs — hip pivots the animator swings for the walk cycle.
        Node(position = Position(x = -0.065f, y = 0.2f, z = 0f), apply = { rig.legL = this }) {
            CubeNode(
                size = Size(0.09f, 0.22f, 0.11f),
                materialInstance = mats.of(MineWorld.MINER_PANTS),
                position = Position(y = -0.11f),
            )
        }
        Node(position = Position(x = 0.065f, y = 0.2f, z = 0f), apply = { rig.legR = this }) {
            CubeNode(
                size = Size(0.09f, 0.22f, 0.11f),
                materialInstance = mats.of(MineWorld.MINER_PANTS),
                position = Position(y = -0.11f),
            )
        }
        // Torso.
        CubeNode(
            size = Size(0.27f, 0.3f, 0.17f),
            materialInstance = mats.of(MineWorld.MINER_SUIT),
            position = Position(0f, 0.36f, 0f),
        )
        // Head + helmet (chunky low-poly spheres).
        SphereNode(
            radius = 0.082f,
            stacks = 6,
            slices = 8,
            materialInstance = mats.of(MineWorld.MINER_SKIN),
            position = Position(0f, 0.585f, 0f),
        )
        SphereNode(
            radius = 0.096f,
            stacks = 5,
            slices = 8,
            materialInstance = mats.of(MineWorld.MINER_HELMET),
            position = Position(0f, 0.6f, 0.01f),
        )
        // Left arm — shoulder pivot.
        Node(position = Position(x = -0.165f, y = 0.47f, z = 0f), apply = { rig.armL = this }) {
            CubeNode(
                size = Size(0.07f, 0.24f, 0.07f),
                materialInstance = mats.of(MineWorld.MINER_SUIT),
                position = Position(y = -0.12f),
            )
        }
        // Right arm — carries the pickaxe.
        Node(position = Position(x = 0.165f, y = 0.47f, z = 0f), apply = { rig.armR = this }) {
            CubeNode(
                size = Size(0.07f, 0.24f, 0.07f),
                materialInstance = mats.of(MineWorld.MINER_SUIT),
                position = Position(y = -0.12f),
            )
            Node(position = Position(0f, -0.2f, -0.02f), apply = { rig.pick = this }) {
                // Handle sticking forward (−Z) from the hand.
                CubeNode(
                    size = Size(0.045f, 0.5f, 0.045f),
                    materialInstance = mats.of(MineWorld.PICK_HANDLE),
                    position = Position(0f, 0f, -0.22f),
                    rotation = Rotation(x = -90f),
                )
                // Pick head at the far end.
                CubeNode(
                    size = Size(0.21f, 0.055f, 0.06f),
                    materialInstance = mats.of(MineWorld.PICK_HEAD),
                    position = Position(0f, 0f, -0.46f),
                )
            }
        }
        // Carried ore chunk (visible while hauling).
        Node(apply = { rig.ore = this }) {
            MeshPart(
                mesh = shapes.rock(2),
                materialInstance = mats.ore(ore),
                scale = Scale(0.14f),
                position = Position(0f, 0.34f, -0.13f),
            )
        }
    }
}

// ------------------------------------------------------------------ conveyors

@Composable
private fun SceneScope.CrossConveyor(world: MineWorld, mats: ToonMaterials, shapes: ToonShapes) {
    val z = MineWorld.CROSS_BELT_Y
    val bz = MineWorld.CROSS_BELT_Z
    // Elevated belt bridge across the shaft row.
    CubeNode(
        size = Size(MineWorld.CROSS_BELT_HALF_LENGTH * 2f, 0.1f, 0.62f),
        materialInstance = mats.of(MineWorld.CONVEYOR_BELT),
        position = Position(0f, z, bz),
    )
    for (rz in listOf(-0.34f, 0.34f)) {
        CubeNode(
            size = Size(MineWorld.CROSS_BELT_HALF_LENGTH * 2f, 0.14f, 0.07f),
            materialInstance = mats.of(MineWorld.CONVEYOR_RAIL),
            position = Position(0f, z + 0.08f, bz + rz),
        )
    }
    // Spinning rollers (axis along X — rotation.z lays the cylinder down).
    for (r in 0 until world.crossRollers.size) {
        val rx = -6f + r * 4f
        Node(
            position = Position(rx, z + 0.03f, bz),
            apply = { world.crossRollers[r] = this },
        ) {
            CylinderNode(
                radius = 0.07f,
                height = 0.56f,
                materialInstance = mats.of(MineWorld.ROLLER),
                rotation = Rotation(z = 90f),
            )
        }
    }
    // Support legs landing in the gaps between the pits.
    for (lx in listOf(-4.2f, 0f, 4.2f)) {
        CubeNode(
            size = Size(0.12f, 0.32f, 0.12f),
            materialInstance = mats.of(MineWorld.STEEL_MID),
            position = Position(lx, z - 0.26f, bz),
        )
    }
    // Ore chunks riding the belt.
    for (i in 0 until world.crossBeltOre.size) {
        MeshPart(
            mesh = shapes.rock(i % 3),
            materialInstance = mats.of(MineWorld.ORE_GENERIC),
            scale = Scale(0.13f),
            position = Position(0f, z + 0.16f, bz),
            apply = { world.crossBeltOre[i] = this },
        )
    }
}

@Composable
private fun SceneScope.MainConveyor(world: MineWorld, mats: ToonMaterials, shapes: ToonShapes) {
    val y = MineWorld.MAIN_BELT_Y
    // Belt toward the crusher.
    CubeNode(
        size = Size(0.62f, 0.1f, 2.7f),
        materialInstance = mats.of(MineWorld.CONVEYOR_BELT),
        position = Position(0f, y, 1.9f),
    )
    for (rx in listOf(-0.34f, 0.34f)) {
        CubeNode(
            size = Size(0.07f, 0.14f, 2.7f),
            materialInstance = mats.of(MineWorld.CONVEYOR_RAIL),
            position = Position(rx, y + 0.08f, 1.9f),
        )
    }
    for (r in 0 until world.mainRollers.size) {
        val rz = 1.35f + r * 1.1f
        Node(
            position = Position(0f, y + 0.03f, rz),
            apply = { world.mainRollers[r] = this },
        ) {
            CylinderNode(
                radius = 0.07f,
                height = 0.5f,
                materialInstance = mats.of(MineWorld.ROLLER),
                rotation = Rotation(z = 90f),
            )
        }
    }
    for (lz in listOf(1.0f, 2.8f)) {
        CubeNode(
            size = Size(0.12f, 0.32f, 0.12f),
            materialInstance = mats.of(MineWorld.STEEL_MID),
            position = Position(0f, y - 0.26f, lz),
        )
    }
    // Chute bridging the cross belt down to the main belt.
    MeshPart(
        mesh = shapes.wedge,
        materialInstance = mats.of(MineWorld.STEEL_MID),
        scale = Scale(0.5f, 0.32f, 0.55f),
        position = Position(0f, y + 0.1f, 3.1f),
    )
    // Ore chunks riding toward the crusher.
    for (i in 0 until world.mainBeltOre.size) {
        MeshPart(
            mesh = shapes.rock(i % 3),
            materialInstance = mats.of(MineWorld.ORE_GENERIC),
            scale = Scale(0.13f),
            position = Position(0f, y + 0.16f, 1.9f),
            apply = { world.mainBeltOre[i] = this },
        )
    }
}

// -------------------------------------------------------------------- crusher

@Composable
private fun SceneScope.Crusher(world: MineWorld, mats: ToonMaterials, shapes: ToonShapes) {
    val z = MineWorld.CRUSHER_Z
    // Base + body.
    CubeNode(
        size = Size(2.6f, 0.24f, 2.2f),
        materialInstance = mats.of(MineWorld.STEEL_DARK),
        position = Position(0f, 0.19f, z),
    )
    CubeNode(
        size = Size(2.4f, 1.5f, 2.0f),
        materialInstance = mats.of(MineWorld.MACHINE_BODY),
        position = Position(0f, 1.06f, z),
    )
    // Front service panel (proud of the body face).
    CubeNode(
        size = Size(1.6f, 0.72f, 0.06f),
        materialInstance = mats.of(MineWorld.MACHINE_PANEL),
        position = Position(0f, 1.0f, z - 1.03f),
    )
    // Status lights (unlit material = they glow).
    for (l in 0 until 2) {
        SphereNode(
            radius = 0.07f,
            stacks = 5,
            slices = 7,
            materialInstance = mats.of(MineWorld.LIGHT_GLOW),
            position = Position(-0.55f + l * 1.1f, 1.5f, z - 1.05f),
            apply = { world.crusherLights[l] = this },
        )
    }
    // Anvil plate the stamp head hits.
    CubeNode(
        size = Size(0.6f, 0.1f, 0.3f),
        materialInstance = mats.of(MineWorld.STEEL_MID),
        position = Position(0f, 1.0f, z - 1.0f),
    )
    // Stamp guide posts.
    for (gx in listOf(-0.26f, 0.26f)) {
        CubeNode(
            size = Size(0.1f, 1.3f, 0.1f),
            materialInstance = mats.of(MineWorld.STEEL_DARK),
            position = Position(gx, 2.0f, z - 0.95f),
        )
    }
    // Stamping piston (animator bobs it onto the anvil).
    Node(
        position = Position(0f, 2.06f, z - 0.95f),
        apply = { world.crusherPiston = this },
    ) {
        CubeNode(
            size = Size(0.12f, 1.1f, 0.12f),
            materialInstance = mats.of(MineWorld.PISTON),
            position = Position(0f, -0.15f, 0f),
        )
        CubeNode(
            size = Size(0.3f, 0.18f, 0.18f),
            materialInstance = mats.of(MineWorld.PISTON),
            position = Position(0f, -0.72f, 0f),
        )
    }
    // Feed hopper (inverted pyramid funnel).
    MeshPart(
        mesh = shapes.pyramid,
        materialInstance = mats.of(MineWorld.STEEL_MID),
        scale = Scale(1.35f, 0.8f, 1.15f),
        rotation = Rotation(x = 180f),
        position = Position(0f, 2.25f, z + 0.15f),
        apply = { world.crusherHopper = this },
    )
    // Chimney + smoke.
    CylinderNode(
        radius = 0.08f,
        height = 0.9f,
        materialInstance = mats.of(MineWorld.STEEL_DARK),
        position = Position(-0.95f, 2.2f, z - 0.6f),
    )
    CubeNode(
        size = Size(0.22f, 0.06f, 0.22f),
        materialInstance = mats.of(MineWorld.STEEL_DARK),
        position = Position(-0.95f, 2.68f, z - 0.6f),
    )
    for (i in 0 until world.smoke.size) {
        SphereNode(
            radius = 0.13f,
            stacks = 5,
            slices = 7,
            materialInstance = mats.of(MineWorld.SMOKE),
            position = Position(-0.95f, 2.75f, z - 0.6f),
            apply = { world.smoke[i] = this },
        )
    }
}

// ----------------------------------------------------------------- bar output

@Composable
private fun SceneScope.BarOutput(world: MineWorld, mats: ToonMaterials, shapes: ToonShapes) {
    // Output belt from the crusher to the bar platform.
    CubeNode(
        size = Size(0.5f, 0.1f, 1.6f),
        materialInstance = mats.of(MineWorld.CONVEYOR_BELT),
        position = Position(0f, 0.5f, -2.8f),
    )
    for (rx in listOf(-0.26f, 0.26f)) {
        CubeNode(
            size = Size(0.07f, 0.13f, 1.6f),
            materialInstance = mats.of(MineWorld.CONVEYOR_RAIL),
            position = Position(rx, 0.57f, -2.8f),
        )
    }
    for (rz in listOf(-2.35f, -3.25f)) {
        Node(position = Position(0f, 0.52f, rz)) {
            CylinderNode(
                radius = 0.05f,
                height = 0.42f,
                materialInstance = mats.of(MineWorld.ROLLER),
                rotation = Rotation(z = 90f),
            )
        }
    }
    for (lz in listOf(-2.2f, -3.4f)) {
        CubeNode(
            size = Size(0.1f, 0.34f, 0.1f),
            materialInstance = mats.of(MineWorld.STEEL_MID),
            position = Position(0f, 0.28f, lz),
        )
    }
    // Bar platform (pallet) with slats.
    CubeNode(
        size = Size(1.9f, 0.3f, 1.1f),
        materialInstance = mats.of(MineWorld.WOOD),
        position = Position(0f, 0.22f, -3.7f),
    )
    for (sz in listOf(-3.35f, -4.05f)) {
        CubeNode(
            size = Size(1.9f, 0.05f, 0.2f),
            materialInstance = mats.of(MineWorld.WOOD_DARK),
            position = Position(0f, 0.395f, sz),
        )
    }
    // Stack of up to 8 visible ingots (presence pops when refined).
    for (b in 0 until world.bars.size) {
        MeshPart(
            mesh = shapes.ingot,
            materialInstance = mats.bar(OreType.COPPER),
            scale = Scale(0.42f, 0.85f, 0.45f),
            position = Position(-0.63f + (b % 4) * 0.42f, 0.5f + (b / 4) * 0.3f, -3.7f),
            apply = { world.bars[b] = this },
        )
    }
}

// ------------------------------------------------------------ road and market

@Composable
private fun SceneScope.RoadAndMarket(world: MineWorld, mats: ToonMaterials, shapes: ToonShapes) {
    val mx = MineWorld.TRUCK_LOAD_X
    val mz = MineWorld.MARKET_Z
    // Road strip to the market.
    CubeNode(
        size = Size(1.9f, 0.08f, MineWorld.ROAD_LENGTH),
        materialInstance = mats.of(MineWorld.ROAD),
        position = Position(mx, 0.05f, MineWorld.ROAD_Z_CENTER),
    )
    // Market platform + counter + back wall + shelf.
    CubeNode(
        size = Size(2.7f, 0.14f, 1.9f),
        materialInstance = mats.of(MineWorld.WOOD_DARK),
        position = Position(mx, 0.07f, mz),
    )
    CubeNode(
        size = Size(1.6f, 0.52f, 0.55f),
        materialInstance = mats.of(MineWorld.WOOD),
        position = Position(mx, 0.4f, mz + 0.65f),
    )
    CubeNode(
        size = Size(2.5f, 1.15f, 0.12f),
        materialInstance = mats.of(MineWorld.WOOD_DARK),
        position = Position(mx, 0.76f, mz - 0.8f),
    )
    CubeNode(
        size = Size(1.9f, 0.06f, 0.3f),
        materialInstance = mats.of(MineWorld.WOOD),
        position = Position(mx, 1.1f, mz - 0.65f),
    )
    // Display ingots on the shelf.
    for (d in 0 until 2) {
        MeshPart(
            mesh = shapes.ingot,
            materialInstance = mats.of(MineWorld.GOLD),
            scale = Scale(0.22f, 0.6f, 0.26f),
            position = Position(mx - 0.25f + d * 0.5f, 1.19f, mz - 0.65f),
        )
    }
    // Roof posts.
    for (px in listOf(-1.28f, 1.28f)) {
        for (pz in listOf(0.85f, -0.85f)) {
            CubeNode(
                size = Size(0.09f, 2.15f, 0.09f),
                materialInstance = mats.of(MineWorld.WOOD),
                position = Position(mx + px, 1.2f, mz + pz),
            )
        }
    }
    // Pyramid roof.
    MeshPart(
        mesh = shapes.pyramid,
        materialInstance = mats.of(MineWorld.ROOF_RED),
        scale = Scale(3.3f, 1.1f, 2.9f),
        position = Position(mx, 2.75f, mz),
    )
    // Hanging coin sign under the roof edge (swings gently).
    CubeNode(
        size = Size(0.04f, 0.3f, 0.04f),
        materialInstance = mats.of(MineWorld.STEEL_DARK),
        position = Position(mx, 2.45f, mz + 0.85f),
    )
    Node(
        position = Position(mx, 2.6f, mz + 0.85f),
        apply = { world.coinSign = this },
    ) {
        CylinderNode(
            radius = 0.17f,
            height = 0.05f,
            materialInstance = mats.of(MineWorld.GOLD),
            rotation = Rotation(x = 90f),
            position = Position(0f, -0.25f, 0f),
        )
    }
    // Crates + barrel beside the stall.
    CubeNode(
        size = Size(0.55f, 0.45f, 0.55f),
        materialInstance = mats.of(MineWorld.CRATE),
        position = Position(mx - 1.05f, 0.32f, mz + 0.65f),
    )
    CubeNode(
        size = Size(0.4f, 0.32f, 0.4f),
        materialInstance = mats.of(MineWorld.CRATE),
        position = Position(mx + 1.1f, 0.26f, mz - 0.5f),
    )
    CylinderNode(
        radius = 0.2f,
        height = 0.52f,
        materialInstance = mats.of(MineWorld.BARREL),
        position = Position(mx + 1.25f, 0.4f, mz + 0.55f),
    ) {
        CubeNode(
            size = Size(0.43f, 0.05f, 0.43f),
            materialInstance = mats.of(MineWorld.BARREL_BAND),
            position = Position(0f, 0.1f, 0f),
        )
        CubeNode(
            size = Size(0.43f, 0.05f, 0.43f),
            materialInstance = mats.of(MineWorld.BARREL_BAND),
            position = Position(0f, -0.1f, 0f),
        )
    }
    // Coin fountain burst (animated when the truck sells).
    for (c in 0 until world.coins.size) {
        CylinderNode(
            radius = 0.09f,
            height = 0.045f,
            materialInstance = mats.of(MineWorld.GOLD),
            rotation = Rotation(x = 90f),
            position = Position(mx, 1.2f, mz + 1.1f),
            apply = {
                isVisible = false
                world.coins[c] = this
            },
        )
    }
}

// --------------------------------------------------------------------- truck

@Composable
private fun SceneScope.Truck(world: MineWorld, mats: ToonMaterials, shapes: ToonShapes) {
    // Flat toon blob shadow under the truck (glued to the road).
    CubeNode(
        size = Size(1.7f, 0.02f, 3.0f),
        materialInstance = mats.blobShadow,
        position = Position(MineWorld.TRUCK_LOAD_X, MineWorld.TRUCK_SHADOW_Y, MineWorld.TRUCK_LOAD_Z),
        apply = { world.truckShadow = this },
    )
    Node(
        position = Position(MineWorld.TRUCK_LOAD_X, 0f, MineWorld.TRUCK_LOAD_Z),
        apply = { world.truck = this },
    ) {
        // Chassis.
        CubeNode(
            size = Size(1.15f, 0.18f, 2.9f),
            materialInstance = mats.of(MineWorld.STEEL_DARK),
            position = Position(0f, 0.32f, 0f),
        )
        // Cab.
        CubeNode(
            size = Size(0.95f, 0.72f, 0.85f),
            materialInstance = mats.of(MineWorld.TRUCK_CAB),
            position = Position(0f, 0.82f, -0.95f),
        )
        CubeNode(
            size = Size(0.72f, 0.3f, 0.06f),
            materialInstance = mats.of(MineWorld.SMOKE),
            position = Position(0f, 0.98f, -1.41f),
        )
        CubeNode(
            size = Size(0.18f, 0.09f, 0.18f),
            materialInstance = mats.of(MineWorld.LIGHT_GLOW),
            position = Position(0f, 1.22f, -0.98f),
        )
        CubeNode(
            size = Size(0.62f, 0.22f, 0.08f),
            materialInstance = mats.of(MineWorld.STEEL_DARK),
            position = Position(0f, 0.62f, -1.42f),
        )
        CubeNode(
            size = Size(0.95f, 0.15f, 0.14f),
            materialInstance = mats.of(MineWorld.STEEL),
            position = Position(0f, 0.44f, -1.47f),
        )
        for (hx in listOf(-0.32f, 0.32f)) {
            CubeNode(
                size = Size(0.14f, 0.11f, 0.07f),
                materialInstance = mats.of(MineWorld.LIGHT_GLOW),
                position = Position(hx, 0.57f, -1.44f),
            )
        }
        // Exhaust stack + puffs.
        CylinderNode(
            radius = 0.05f,
            height = 0.8f,
            materialInstance = mats.of(MineWorld.STEEL_DARK),
            position = Position(0.44f, 1.35f, -0.7f),
        )
        CubeNode(
            size = Size(0.13f, 0.04f, 0.13f),
            materialInstance = mats.of(MineWorld.STEEL_DARK),
            position = Position(0.44f, 1.77f, -0.7f),
        )
        for (e in 0 until world.truckExhaust.size) {
            SphereNode(
                radius = 0.09f,
                stacks = 5,
                slices = 7,
                materialInstance = mats.of(MineWorld.SMOKE),
                position = Position(0.44f, 1.85f, -0.7f),
                apply = { world.truckExhaust[e] = this },
            )
        }
        // Fuel tank (axis along X).
        CylinderNode(
            radius = 0.13f,
            height = 0.6f,
            materialInstance = mats.of(MineWorld.STEEL),
            rotation = Rotation(z = 90f),
            position = Position(-0.42f, 0.36f, 0.35f),
        )
        // Cargo container + ribs.
        CubeNode(
            size = Size(1.05f, 0.75f, 1.6f),
            materialInstance = mats.of(MineWorld.TRUCK_CARGO),
            position = Position(0f, 0.99f, 0.42f),
        )
        for (rz in listOf(0.05f, 0.75f)) {
            CubeNode(
                size = Size(1.12f, 0.62f, 0.09f),
                materialInstance = mats.of(MineWorld.STEEL_MID),
                position = Position(0f, 0.99f, rz),
            )
        }
        // Ingot load slots.
        for (b in 0 until world.truckBars.size) {
            MeshPart(
                mesh = shapes.ingot,
                materialInstance = mats.bar(OreType.COPPER),
                scale = Scale(0.24f, 0.7f, 0.4f),
                position = Position(-0.3f + (b % 3) * 0.3f, 1.5f, 0.32f - (b / 3) * 0.5f),
                apply = { world.truckBars[b] = this },
            )
        }
        // Wheels (front pair + wide rear duals) — spinning.
        for (w in 0 until world.truckWheels.size) {
            val wx = if (w % 2 == 0) -0.62f else 0.62f
            val wz = if (w / 2 == 0) -0.95f else 0.82f
            val dual = w / 2 == 1
            Node(
                position = Position(wx, 0.33f, wz),
                apply = { world.truckWheels[w] = this },
            ) {
                CylinderNode(
                    radius = 0.24f,
                    height = if (dual) 0.3f else 0.17f,
                    materialInstance = mats.of(MineWorld.TIRE),
                    rotation = Rotation(z = 90f),
                )
                CylinderNode(
                    radius = 0.1f,
                    height = if (dual) 0.32f else 0.19f,
                    materialInstance = mats.of(MineWorld.HUB),
                    rotation = Rotation(z = 90f),
                )
            }
        }
    }
}

// --------------------------------------------------------------------- decor

@Composable
private fun SceneScope.Decor(world: MineWorld, mats: ToonMaterials, shapes: ToonShapes) {
    val trees = listOf(
        -11.5f to -8.5f, -8.5f to -10.5f, 11.5f to -8.5f, 8.5f to -10.5f,
        -11.5f to 5.5f, 11.5f to 6.5f, -12.5f to -1.5f, 12.5f to 1.5f,
        -11.5f to 11.0f, 11.5f to 11.5f, 5.0f to 12.0f, -5.0f to 12.5f,
    )
    trees.forEachIndexed { i, (x, z) ->
        val scale = 0.85f + (i % 3) * 0.12f
        if (i % 3 == 2) {
            // Round tree: trunk + double blob canopy.
            CylinderNode(
                radius = 0.15f * scale,
                height = 0.9f * scale,
                materialInstance = mats.of(MineWorld.TRUNK),
                position = Position(x, 0.45f * scale, z),
            )
            Node(
                position = Position(x, 1.6f * scale, z),
                apply = { world.canopies.add(this) },
            ) {
                SphereNode(
                    radius = 0.62f * scale,
                    stacks = 5,
                    slices = 7,
                    materialInstance = mats.of(MineWorld.FOLIAGE_A),
                )
            }
            Node(position = Position(x + 0.15f, 2.25f * scale, z)) {
                SphereNode(
                    radius = 0.4f * scale,
                    stacks = 5,
                    slices = 7,
                    materialInstance = mats.of(MineWorld.FOLIAGE_B),
                )
            }
        } else {
            // Conifer: trunk + two stacked cone tiers.
            CylinderNode(
                radius = 0.14f * scale,
                height = 1.1f * scale,
                materialInstance = mats.of(MineWorld.TRUNK),
                position = Position(x, 0.55f * scale, z),
            )
            Node(
                position = Position(x, 1.65f * scale, z),
                apply = { world.canopies.add(this) },
            ) {
                ConeNode(
                    radius = 0.72f * scale,
                    height = 1.25f * scale,
                    sideCount = 7,
                    materialInstance = mats.of(MineWorld.FOLIAGE_A),
                )
            }
            Node(position = Position(x, 2.5f * scale, z)) {
                ConeNode(
                    radius = 0.5f * scale,
                    height = 1.0f * scale,
                    sideCount = 7,
                    materialInstance = mats.of(MineWorld.FOLIAGE_B),
                )
            }
        }
    }
    // Boulders.
    val rocks = listOf(
        -9.5f to -5.5f, 9.8f to -4.2f, -10.5f to 7.5f, 10.5f to 8.5f,
        -4.5f to 8.8f, 4.8f to 9.0f,
    )
    rocks.forEachIndexed { i, (x, z) ->
        MeshPart(
            mesh = shapes.rock(i % 4),
            materialInstance = mats.of(MineWorld.ROCKS),
            scale = Scale(0.5f + (i % 3) * 0.08f),
            position = Position(x, 0.2f, z),
        )
        MeshPart(
            mesh = shapes.rock((i + 1) % 4),
            materialInstance = mats.of(MineWorld.ROCKS),
            scale = Scale(0.34f),
            position = Position(x + 0.42f, 0.14f, z - 0.18f),
        )
    }
    // Fence along the front deck edge (gap for the truck road).
    for (fx in listOf(-7.4f, -5.8f, -4.2f, 3.6f, 5.2f, 6.8f)) {
        CubeNode(
            size = Size(0.09f, 0.55f, 0.09f),
            materialInstance = mats.of(MineWorld.FENCE),
            position = Position(fx, 0.275f, -6.3f),
        )
    }
    for (rx in listOf(-6.6f, -5.0f, 4.4f, 6.0f)) {
        CubeNode(
            size = Size(1.6f, 0.07f, 0.07f),
            materialInstance = mats.of(MineWorld.FENCE),
            position = Position(rx, 0.42f, -6.3f),
        )
    }
    // Lamps (unlit glow heads).
    for (lx in listOf(-4.5f, 4.5f)) {
        CylinderNode(
            radius = 0.055f,
            height = 1.85f,
            materialInstance = mats.of(MineWorld.STEEL_DARK),
            position = Position(lx, 0.97f, -5.6f),
        )
        SphereNode(
            radius = 0.14f,
            stacks = 5,
            slices = 7,
            materialInstance = mats.of(MineWorld.LIGHT_GLOW),
            position = Position(lx, 2.0f, -5.6f),
        )
        ConeNode(
            radius = 0.2f,
            height = 0.18f,
            sideCount = 7,
            materialInstance = mats.of(MineWorld.STEEL_DARK),
            position = Position(lx, 2.12f, -5.6f),
        )
    }
    // Props by the bar platform.
    CylinderNode(
        radius = 0.2f,
        height = 0.52f,
        materialInstance = mats.of(MineWorld.BARREL),
        position = Position(-1.55f, 0.33f, -3.4f),
    )
    CubeNode(
        size = Size(0.48f, 0.42f, 0.48f),
        materialInstance = mats.of(MineWorld.CRATE),
        position = Position(-1.1f, 0.28f, -3.05f),
    )
}

// --------------------------------------------------------------------- clouds

@Composable
private fun SceneScope.Clouds(world: MineWorld, mats: ToonMaterials) {
    val spots = listOf(
        Triple(-14f, 7.6f, -4f),
        Triple(8f, 8.4f, 2f),
        Triple(-2f, 9.2f, -12f),
    )
    spots.forEachIndexed { i, (x, y, z) ->
        Node(
            position = Position(x, y, z),
            apply = { world.clouds[i] = this },
        ) {
            SphereNode(
                radius = 0.85f,
                stacks = 5,
                slices = 7,
                materialInstance = mats.of(MineWorld.CLOUD),
                scale = Scale(1f, 0.55f, 1f),
            )
            SphereNode(
                radius = 0.6f,
                stacks = 5,
                slices = 7,
                materialInstance = mats.of(MineWorld.CLOUD),
                scale = Scale(1f, 0.55f, 1f),
                position = Position(-1.1f, 0.1f, 0.2f),
            )
            SphereNode(
                radius = 0.65f,
                stacks = 5,
                slices = 7,
                materialInstance = mats.of(MineWorld.CLOUD),
                scale = Scale(1f, 0.55f, 1f),
                position = Position(1.0f, 0.05f, -0.1f),
            )
        }
    }
}
