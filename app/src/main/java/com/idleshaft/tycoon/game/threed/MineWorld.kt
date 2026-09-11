package com.idleshaft.tycoon.game.threed

import io.github.sceneview.node.MeshNode
import io.github.sceneview.node.Node

/**
 * Mutable node references captured from the declarative scene at build time.
 * [MineAnimator] writes transforms onto these nodes every frame — the Compose
 * tree itself stays static (no recomposition while the game renders).
 *
 * Shafts are authored **group-relative**: each shaft's equipment lives under a
 * group node at (shaftX, 0, SHAFT_Z) so the whole rig can scale-pop when the
 * shaft is unlocked. Trench walls / lid are terrain (world space, always drawn).
 */
class MineWorld {

    /** Fully-rigged miner: pivots the animator swings for walk/mine cycles. */
    class MinerRig {
        var root: Node? = null
        var armL: Node? = null
        var armR: Node? = null
        var legL: Node? = null
        var legR: Node? = null
        var pick: Node? = null
        var ore: Node? = null
    }

    class ShaftNodes {
        /** Root of headframe + rails + cart + miners + heap; hidden while locked. */
        var group: Node? = null
        /** Rock lid covering the pit while locked (animated off on unlock). */
        var lid: Node? = null
        val miners: Array<MinerRig> = Array(ShaftMinerCount) { MinerRig() }
        var cart: Node? = null
        val cartLoad: Array<MeshNode?> = arrayOfNulls(4)
        val stockpile: Array<MeshNode?> = arrayOfNulls(4)
        val vein: Array<MeshNode?> = arrayOfNulls(3)
        /** Hoist wheel (spins with the cart). */
        var pulley: Node? = null
        /** Cable from wheel to cart (scaled every frame). */
        var cable: Node? = null
        /** Waving flag pivot. */
        var flag: Node? = null
    }

    val shafts: Array<ShaftNodes> = Array(ShaftCount) { ShaftNodes() }

    /** Ore chunks riding the cross belt (surface, along X). */
    val crossBeltOre: Array<Node?> = arrayOfNulls(6)

    /** Ore chunks riding the main belt (toward the crusher). */
    val mainBeltOre: Array<Node?> = arrayOfNulls(4)

    /** Spinning conveyor rollers. */
    val crossRollers: Array<Node?> = arrayOfNulls(4)
    val mainRollers: Array<Node?> = arrayOfNulls(2)

    /** Crusher stamping piston + wobbling hopper + status lights. */
    var crusherPiston: Node? = null
    var crusherHopper: Node? = null
    val crusherLights: Array<Node?> = arrayOfNulls(2)

    /** Crusher smoke puffs. */
    val smoke: Array<Node?> = arrayOfNulls(3)

    /** Ingot stack on the output platform (visible count follows the silo). */
    val bars: Array<MeshNode?> = arrayOfNulls(8)

    /** The delivery truck (root moves along the road). */
    var truck: Node? = null

    /** Flat toon blob shadow under the truck. */
    var truckShadow: Node? = null

    /** Spinning wheels. */
    val truckWheels: Array<Node?> = arrayOfNulls(4)

    /** Exhaust puffs from the stack. */
    val truckExhaust: Array<Node?> = arrayOfNulls(2)

    /** Ingot load on the truck's cargo bed. */
    val truckBars: Array<MeshNode?> = arrayOfNulls(6)

    /** Hanging gold-coin sign at the market (gentle swing). */
    var coinSign: Node? = null

    /** Coin fountain burst when the truck sells. */
    val coins: Array<Node?> = arrayOfNulls(5)

    /** Drifting clouds. */
    val clouds: Array<Node?> = arrayOfNulls(3)

    /** Tree canopy pivots (gentle sway). */
    val canopies: MutableList<Node> = mutableListOf()

    companion object {
        const val ShaftCount = 4
        const val ShaftMinerCount = 3

        // ------------------------------------------------------------------
        // World layout — authored coordinates, meters.
        //
        // Pits are open-top, open-front cutaways (back + side walls, short
        // front lip) so the underground action — miners, heap, cart on rails —
        // is fully visible from the default 45° camera. The dirt deck leaves
        // the pit footprints open; overlapping solids are always offset by
        // >= 0.02 m so no two faces are coplanar (no z-fighting).
        // ------------------------------------------------------------------

        /** X position of shaft [i] (left to right). */
        fun shaftX(i: Int): Float = -6.3f + 4.2f * i

        /** Pit centre Z. */
        const val SHAFT_Z = 4.6f

        /** Pit interior: 2.6 × 3.4, open top; walls 0.18 thick. */
        const val PIT_HALF_W = 1.3f
        const val PIT_HALF_D = 1.7f
        const val PIT_TOP_Y = 0.125f
        const val PIT_FLOOR_Y = -3.65f
        const val WALL_T = 0.18f

        /** Cart / rails / headframe / cable Z (front area of the pit). */
        const val CART_Z = SHAFT_Z - 0.7f

        /** Cart floor rest heights (root sits at the cart floor plane). */
        const val CART_TOP_Y = 0.62f
        const val CART_BOTTOM_Y = PIT_FLOOR_Y

        /** Hoist wheel. */
        const val WHEEL_Y = 3.45f

        /** Miner walk targets (group-relative z). */
        const val MINER_MINE_Z = 0.95f    // at the back-wall ore seam
        const val MINER_DUMP_Z = 0.25f    // at the heap
        const val MINER_Y = PIT_FLOOR_Y

        /** Heap (underground stockpile) cluster centre (group-relative z). */
        const val HEAP_Z = 0.05f

        /** Ore seam embedded in the back wall (group-relative z). */
        const val VEIN_Z = 1.42f

        /** Elevated cross-belt bridge over the shaft row. */
        const val CROSS_BELT_Y = 0.42f
        const val CROSS_BELT_Z = 3.4f
        const val CROSS_BELT_HALF_LENGTH = 8.5f
        const val CROSS_BELT_TRAVEL = 8.4f

        const val MAIN_BELT_X = 0f
        const val MAIN_BELT_Y = 0.42f

        const val CRUSHER_Z = -0.9f

        /** Truck road — stops short of the market platform (truck noses up to the stall). */
        const val TRUCK_LOAD_X = 2.0f
        const val TRUCK_LOAD_Z = -3.6f
        const val TRUCK_SELL_Z = -7.9f
        const val MARKET_Z = -10.4f
        const val ROAD_Z_CENTER = -6.0f
        const val ROAD_LENGTH = 6.6f

        /** Blob shadow rests a hair above the road surface. */
        const val TRUCK_SHADOW_Y = 0.105f

        // ------------------------------------------------------------------
        // Low-poly toon palette (ARGB) — saturated, flat, high-contrast.
        // ------------------------------------------------------------------

        const val GRASS = 0xFF8BC34A
        const val DIRT_DECK = 0xFFA1887F
        const val ROCK_LID = 0xFF78909C
        const val PIT_WALL = 0xFF3B4A56
        const val PIT_FLOOR = 0xFF2B3640
        const val WOOD = 0xFF8D6E63
        const val WOOD_DARK = 0xFF5D4037
        const val STEEL = 0xFFB0BEC5
        const val STEEL_DARK = 0xFF37474F
        const val STEEL_MID = 0xFF607D8B
        const val MACHINE_BODY = 0xFF546E7A
        const val MACHINE_PANEL = 0xFF37474F
        const val PISTON = 0xFFFF7043
        const val SMOKE = 0xFFCFD8DC
        const val CONVEYOR_BELT = 0xFF455A64
        const val CONVEYOR_RAIL = 0xFF2F3E48
        const val ROLLER = 0xFF90A4AE
        const val ORE_GENERIC = 0xFF9C7A5B
        const val CART_RED = 0xFFEF5350
        const val CART_TRIM = 0xFFFFC93C
        const val TRUCK_CAB = 0xFF42A5F5
        const val TRUCK_CARGO = 0xFFECEFF1
        const val TIRE = 0xFF263238
        const val HUB = 0xFFCFD8DC
        const val ROAD = 0xFF6D7B87
        const val ROOF_RED = 0xFFE53935
        const val AWNING_RED = 0xFFEF5350
        const val AWNING_WHITE = 0xFFFFFFFF
        const val MINER_SUIT = 0xFF5C6BC0
        const val MINER_PANTS = 0xFF3949AB
        const val MINER_SKIN = 0xFFD7A98C
        const val MINER_HELMET = 0xFFFFC107
        const val PICK_HANDLE = 0xFF8D6E63
        const val PICK_HEAD = 0xFF90A4AE
        const val LIGHT_GLOW = 0xFFFFF176
        const val GOLD = 0xFFFFD54F
        const val CLOUD = 0xFFFFFFFF
        const val FLAG = 0xFFFF7043
        const val FENCE = 0xFFA1887F
        const val BARREL = 0xFF8D6E63
        const val BARREL_BAND = 0xFF5D4037
        const val CRATE = 0xFFD7CCC8
        const val TRUNK = 0xFF795548
        const val FOLIAGE_A = 0xFF43A047
        const val FOLIAGE_B = 0xFF66BB6A
        const val ROCKS = 0xFF90A4AE
    }
}
