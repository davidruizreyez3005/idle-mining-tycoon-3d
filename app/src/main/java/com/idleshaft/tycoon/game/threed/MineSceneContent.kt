package com.idleshaft.tycoon.game.threed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.idleshaft.tycoon.domain.OreType
import io.github.sceneview.SceneScope
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size

@Composable
private fun SceneScope.rememberToonMaterials(): ToonMaterials =
    remember(materialLoader) { ToonMaterials(materialLoader) }

// ---------------------------------------------------------------------------
// Static world construction. Every node is created exactly once; animated ones
// are captured into [MineWorld] through `apply` blocks. No Compose state is read
// here — the content composes once and never recomposes.
//
// Layout rules (the "no clipping" contract):
//  - The facility deck is split into front/back strips so the trench
//    footprints stay OPEN — the underground floor, veins, stockpiles, miners
//    and carts are always visible and never poke through the deck.
//  - The cross belt is an elevated bridge; its legs land between the shafts.
//  - The truck road runs east of the bar platform with a clear gap; the truck
//    parks beside (never inside) the platform and the market stall.
//  - Overlapping solids are always offset by >= 0.02 m so no two faces are
//    coplanar (no z-fighting shimmer).
// ---------------------------------------------------------------------------

@Composable
internal fun SceneScope.MineSceneContent(world: MineWorld) {
    val mats = rememberToonMaterials()

    Ground(mats)
    Shafts(world, mats)
    CrossConveyor(world, mats)
    MainConveyor(world, mats)
    Crusher(world, mats)
    BarOutput(world, mats)
    RoadAndMarket(mats)
    Truck(world, mats)
    Decor(mats)
}

@Composable
private fun SceneScope.Ground(mats: ToonMaterials) {
    // Grass base — oversized so the horizon never clips into the void.
    CubeNode(
        size = Size(48f, 0.5f, 48f),
        materialInstance = mats.of(MineWorld.GRASS),
        position = Position(0f, -0.25f, 0f),
    )
    // Packed-dirt facility deck: front strip stops short of the trenches.
    CubeNode(
        size = Size(19f, 0.1f, 9.05f),
        materialInstance = mats.of(MineWorld.DIRT_DECK),
        position = Position(0f, 0.02f, -1.625f),
    )
    // Back strip behind the trench row.
    CubeNode(
        size = Size(19f, 0.1f, 1.05f),
        materialInstance = mats.of(MineWorld.DIRT_DECK),
        position = Position(0f, 0.02f, 6.825f),
    )
}

@Composable
private fun SceneScope.Shafts(world: MineWorld, mats: ToonMaterials) {
    for (i in 0 until MineWorld.ShaftCount) {
        val x = MineWorld.shaftX(i)

        // Open trench with a dark collar slightly proud of the deck rim.
        CubeNode(
            size = Size(2.6f, MineWorld.SHAFT_TRENCH_DEPTH, 3.4f),
            materialInstance = mats.of(MineWorld.TRENCH_DARK),
            position = Position(x, MineWorld.TRENCH_TOP_Y - MineWorld.SHAFT_TRENCH_DEPTH / 2f, MineWorld.SHAFT_Z),
        )

        // Rock lid while the shaft is still locked — seated flush on the collar.
        CubeNode(
            size = Size(2.7f, 0.35f, 3.5f),
            materialInstance = mats.of(MineWorld.ROCK_LID),
            position = Position(x, MineWorld.TRENCH_TOP_Y + 0.175f, MineWorld.SHAFT_Z),
            apply = { world.shafts[i].lid = this },
        )

        // Everything else hides behind this group while locked.
        Node(apply = { world.shafts[i].group = this }) {

            // Ore seam cubes at the back of the trench floor.
            for (v in 0 until 3) {
                CubeNode(
                    size = Size(0.45f, 0.45f, 0.45f),
                    materialInstance = mats.ore(OreType.ordered[i]),
                    position = Position(x + (v - 1) * 0.6f, MineWorld.SHAFT_FLOOR_Y - 0.15f, MineWorld.SHAFT_Z - 1.05f),
                    apply = { world.shafts[i].vein[v] = this },
                )
            }

            // Underground stockpile (visible count tracks the shaft buffer).
            for (s in 0 until 4) {
                CubeNode(
                    size = Size(0.34f, 0.3f, 0.34f),
                    materialInstance = mats.ore(OreType.ordered[i]),
                    position = Position(x + (s % 2) * 0.35f - 0.17f, -3.35f + (s / 2) * 0.26f, MineWorld.SHAFT_Z + 0.85f),
                    apply = { world.shafts[i].stockpile[s] = this },
                )
            }

            // Headframe: legs stand OUTSIDE the trench footprint on the grass,
            // the crossbar spans between them, the pulley rides on top.
            CubeNode(
                size = Size(0.24f, 3.0f, 0.24f),
                materialInstance = mats.of(MineWorld.WOOD),
                position = Position(x - 1.55f, 1.49f, MineWorld.SHAFT_Z),
            )
            CubeNode(
                size = Size(0.24f, 3.0f, 0.24f),
                materialInstance = mats.of(MineWorld.WOOD),
                position = Position(x + 1.55f, 1.49f, MineWorld.SHAFT_Z),
            )
            CubeNode(
                size = Size(3.6f, 0.24f, 0.24f),
                materialInstance = mats.of(MineWorld.STEEL),
                position = Position(x, 3.05f, MineWorld.SHAFT_Z),
            )
            // Pulley wheel (disc facing along X).
            CylinderNode(
                radius = 0.32f,
                height = 0.14f,
                materialInstance = mats.of(MineWorld.STEEL),
                position = Position(x, 3.35f, MineWorld.SHAFT_Z),
            )
            // Guide rails clear of the 0.9 m-wide cart (rails at +/-0.55).
            CubeNode(
                size = Size(0.09f, 6.6f, 0.09f),
                materialInstance = mats.of(MineWorld.STEEL_MID),
                position = Position(x - 0.55f, -0.25f, MineWorld.SHAFT_Z),
            )
            CubeNode(
                size = Size(0.09f, 6.6f, 0.09f),
                materialInstance = mats.of(MineWorld.STEEL_MID),
                position = Position(x + 0.55f, -0.25f, MineWorld.SHAFT_Z),
            )

            // Elevator cart with ore load slots.
            Node(apply = { world.shafts[i].cart = this }) {
                CubeNode(
                    size = Size(0.9f, 0.5f, 0.8f),
                    materialInstance = mats.of(MineWorld.CART_RED),
                    position = Position(0f, 0.25f, 0f),
                )
                for (c in 0 until 4) {
                    CubeNode(
                        size = Size(0.18f, 0.18f, 0.18f),
                        materialInstance = mats.ore(OreType.ordered[i]),
                        position = Position(-0.27f + c * 0.18f, 0.59f, 0f),
                        apply = { world.shafts[i].cartLoad[c] = this },
                    )
                }
            }

            // Miner crew: body + helmet, parented to a moving anchor node.
            for (m in 0 until MineWorld.ShaftMinerCount) {
                Node(apply = { world.shafts[i].miners[m] = this }) {
                    CubeNode(
                        size = Size(0.22f, 0.38f, 0.22f),
                        materialInstance = mats.of(MineWorld.MINER_BODY),
                        position = Position(0f, 0.19f, 0f),
                    )
                    SphereNode(
                        radius = 0.09f,
                        materialInstance = mats.of(MineWorld.MINER_HELMET),
                        position = Position(0f, 0.46f, 0f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SceneScope.CrossConveyor(world: MineWorld, mats: ToonMaterials) {
    // Elevated belt bridge across the shaft row.
    CubeNode(
        size = Size(MineWorld.CROSS_BELT_HALF_LENGTH * 2f, 0.14f, 0.7f),
        materialInstance = mats.of(MineWorld.CONVEYOR_BELT),
        position = Position(0f, MineWorld.CROSS_BELT_Y, MineWorld.CROSS_BELT_Z),
    )
    // Support legs landing in the gaps between the trench footprints.
    for (legX in listOf(-8.2f, -4.2f, 0f, 4.2f, 8.2f)) {
        CubeNode(
            size = Size(0.12f, 0.35f, 0.12f),
            materialInstance = mats.of(MineWorld.STEEL_MID),
            position = Position(legX, 0.175f, MineWorld.CROSS_BELT_Z),
        )
    }
    for (i in 0 until world.crossBeltOre.size) {
        CubeNode(
            size = Size(0.2f, 0.2f, 0.2f),
            materialInstance = mats.of(MineWorld.ORE_GENERIC),
            position = Position(0f, MineWorld.CROSS_BELT_Y + 0.17f, MineWorld.CROSS_BELT_Z),
            apply = { world.crossBeltOre[i] = this },
        )
    }
}

@Composable
private fun SceneScope.MainConveyor(world: MineWorld, mats: ToonMaterials) {
    CubeNode(
        size = Size(0.7f, 0.14f, 2.7f),
        materialInstance = mats.of(MineWorld.CONVEYOR_BELT),
        position = Position(MineWorld.MAIN_BELT_X, MineWorld.MAIN_BELT_Y, 1.65f),
    )
    // Feeder chute bridging the cross belt down to the main belt.
    CubeNode(
        size = Size(0.5f, 0.4f, 0.5f),
        materialInstance = mats.of(MineWorld.STEEL_MID),
        position = Position(MineWorld.MAIN_BELT_X, MineWorld.MAIN_BELT_Y + 0.28f, 2.75f),
    )
    for (i in 0 until world.mainBeltOre.size) {
        CubeNode(
            size = Size(0.2f, 0.2f, 0.2f),
            materialInstance = mats.of(MineWorld.ORE_GENERIC),
            position = Position(MineWorld.MAIN_BELT_X, MineWorld.MAIN_BELT_Y + 0.17f, 1.65f),
            apply = { world.mainBeltOre[i] = this },
        )
    }
}

@Composable
private fun SceneScope.Crusher(world: MineWorld, mats: ToonMaterials) {
    // Machine body + hopper + piston + smoke puffs.
    CubeNode(
        size = Size(2.4f, 1.6f, 2.0f),
        materialInstance = mats.of(MineWorld.MACHINE_BODY),
        position = Position(0f, 0.85f, MineWorld.CRUSHER_Z),
    )
    CubeNode(
        size = Size(1.5f, 0.5f, 1.3f),
        materialInstance = mats.of(MineWorld.STEEL_MID),
        position = Position(0f, 1.9f, MineWorld.CRUSHER_Z + 0.25f),
    )
    CubeNode(
        size = Size(0.6f, 0.55f, 0.55f),
        materialInstance = mats.of(MineWorld.PISTON),
        position = Position(0f, 2.35f, MineWorld.CRUSHER_Z - 0.15f),
        apply = { world.crusherPiston = this },
    )
    for (i in 0 until world.smoke.size) {
        SphereNode(
            radius = 0.16f,
            materialInstance = mats.of(MineWorld.SMOKE),
            position = Position(-0.3f + i * 0.3f, 2.7f, MineWorld.CRUSHER_Z - 0.15f),
            apply = { world.smoke[i] = this },
        )
    }
}

@Composable
private fun SceneScope.BarOutput(world: MineWorld, mats: ToonMaterials) {
    // Short output belt from the crusher to the bar platform.
    CubeNode(
        size = Size(0.6f, 0.12f, 1.5f),
        materialInstance = mats.of(MineWorld.CONVEYOR_BELT),
        position = Position(0f, 0.5f, -2.6f),
    )
    // Bar platform (west of the truck road, with a clear gap).
    CubeNode(
        size = Size(1.9f, 0.35f, 1.1f),
        materialInstance = mats.of(MineWorld.WOOD),
        position = Position(0f, 0.18f, -3.7f),
    )
    // Stack of up to 8 visible bars (color follows the dominant ore in the silo).
    for (b in 0 until world.bars.size) {
        CubeNode(
            size = Size(0.5f, 0.2f, 0.32f),
            materialInstance = mats.bar(OreType.COPPER),
            position = Position(-0.6f + (b % 4) * 0.4f, 0.45f + (b / 4) * 0.24f, -3.7f),
            apply = { world.bars[b] = this },
        )
    }
}

@Composable
private fun SceneScope.RoadAndMarket(mats: ToonMaterials) {
    // Road strip to the market (long enough for the sell parking spot).
    CubeNode(
        size = Size(1.9f, 0.08f, MineWorld.ROAD_LENGTH),
        materialInstance = mats.of(MineWorld.ROAD),
        position = Position(MineWorld.TRUCK_LOAD_X, 0.05f, MineWorld.ROAD_Z_CENTER),
    )
    val mx = MineWorld.TRUCK_LOAD_X
    val mz = MineWorld.MARKET_Z
    // Market stall: counter, posts, striped awning.
    CubeNode(
        size = Size(1.7f, 0.95f, 0.85f),
        materialInstance = mats.of(MineWorld.WOOD),
        position = Position(mx, 0.48f, mz),
    )
    CubeNode(
        size = Size(0.1f, 2.3f, 0.1f),
        materialInstance = mats.of(MineWorld.STEEL),
        position = Position(mx - 0.95f, 1.15f, mz + 0.35f),
    )
    CubeNode(
        size = Size(0.1f, 2.3f, 0.1f),
        materialInstance = mats.of(MineWorld.STEEL),
        position = Position(mx + 0.95f, 1.15f, mz + 0.35f),
    )
    CubeNode(
        size = Size(2.1f, 0.16f, 1.05f),
        materialInstance = mats.of(MineWorld.AWNING_RED),
        position = Position(mx, 2.4f, mz + 0.35f),
    )
    for (s in 0 until 3) {
        CubeNode(
            size = Size(0.34f, 0.18f, 1.07f),
            materialInstance = mats.of(MineWorld.AWNING_WHITE),
            position = Position(mx - 0.7f + s * 0.7f, 2.42f, mz + 0.35f),
        )
    }
    // Gold coin sign above the stall.
    CylinderNode(
        radius = 0.3f,
        height = 0.07f,
        materialInstance = mats.of(OreType.GOLD.argb),
        position = Position(mx, 2.9f, mz + 0.6f),
    )
}

@Composable
private fun SceneScope.Truck(world: MineWorld, mats: ToonMaterials) {
    // Flat toon blob shadow under the truck (drives the "grounded" look —
    // the truck hops above it, the shadow stays glued to the road).
    CubeNode(
        size = Size(1.5f, 0.02f, 2.2f),
        materialInstance = mats.blobShadow,
        position = Position(MineWorld.TRUCK_LOAD_X, MineWorld.TRUCK_SHADOW_Y, MineWorld.TRUCK_LOAD_Z),
        apply = { world.truckShadow = this },
    )
    Node(apply = { world.truck = this }) {
        // Chassis.
        CubeNode(
            size = Size(1.15f, 0.18f, 1.9f),
            materialInstance = mats.of(MineWorld.STEEL_DARK),
            position = Position(0f, 0.24f, 0f),
        )
        // Cab (toward the outbound direction, -Z).
        CubeNode(
            size = Size(0.8f, 0.55f, 0.65f),
            materialInstance = mats.of(MineWorld.TRUCK_CAB),
            position = Position(0f, 0.6f, -0.62f),
        )
        // Cargo bed + windshield.
        CubeNode(
            size = Size(1.05f, 0.5f, 1.0f),
            materialInstance = mats.of(MineWorld.TRUCK_CARGO),
            position = Position(0f, 0.58f, 0.35f),
        )
        CubeNode(
            size = Size(0.7f, 0.25f, 0.06f),
            materialInstance = mats.of(MineWorld.SMOKE),
            position = Position(0f, 0.72f, -0.96f),
        )
        // Bar load slots on the cargo bed.
        for (b in 0 until world.truckBars.size) {
            CubeNode(
                size = Size(0.19f, 0.12f, 0.15f),
                materialInstance = mats.bar(OreType.COPPER),
                position = Position(-0.3f + (b % 3) * 0.3f, 0.88f, 0.35f - (b / 3) * 0.4f),
                apply = { world.truckBars[b] = this },
            )
        }
        // Wheels: four short cylinders lying along X.
        for (w in 0 until 4) {
            val wx = if (w % 2 == 0) -0.62f else 0.62f
            val wz = if (w / 2 == 0) -0.55f else 0.65f
            CylinderNode(
                radius = 0.2f,
                height = 0.12f,
                materialInstance = mats.of(MineWorld.STEEL_DARK),
                position = Position(wx, 0.2f, wz),
            )
        }
    }
}

@Composable
private fun SceneScope.Decor(mats: ToonMaterials) {
    val trees = listOf(
        -11.5f to -8.5f, -8.5f to -10.5f, 11.5f to -8.5f, 8.5f to -10.5f,
        -11.5f to 5.5f, 11.5f to 6.5f, -12.5f to -1.5f, 12.5f to 1.5f,
        -11.5f to 11.0f, 11.5f to 11.5f, 5.0f to 12.0f, -5.0f to 12.5f,
    )
    trees.forEachIndexed { i, (x, z) ->
        val scale = 0.85f + (i % 3) * 0.14f
        CylinderNode(
            radius = 0.13f * scale,
            height = 0.9f * scale,
            materialInstance = mats.of(MineWorld.TRUNK),
            position = Position(x, 0.45f * scale + 0.01f, z),
        )
        if (i % 2 == 0) {
            ConeNode(
                radius = 0.68f * scale,
                height = 1.7f * scale,
                materialInstance = mats.of(MineWorld.FOLIAGE_A),
                position = Position(x, 1.75f * scale, z),
            )
        } else {
            SphereNode(
                radius = 0.62f * scale,
                materialInstance = mats.of(MineWorld.FOLIAGE_B),
                position = Position(x, 1.35f * scale, z),
            )
        }
    }
    val rocks = listOf(
        -9.5f to -5.5f, 9.8f to -4.2f, -10.5f to 7.5f, 10.5f to 8.5f,
        -4.5f to 8.8f, 4.8f to 9.0f, 8.0f to 5.5f, -8.2f to 6.2f,
    )
    rocks.forEachIndexed { i, (x, z) ->
        CubeNode(
            size = Size(0.35f + (i % 3) * 0.1f, 0.3f + (i % 2) * 0.12f, 0.35f + (i % 4) * 0.07f),
            materialInstance = mats.of(MineWorld.ROCKS),
            position = Position(x, 0.16f, z),
        )
    }
}
