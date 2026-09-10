package com.idleshaft.tycoon.game.threed

import io.github.sceneview.node.Node

/**
 * Mutable node references captured from the declarative scene at build time.
 * [MineAnimator] writes transforms onto these nodes every frame — the Compose tree
 * itself stays static (no recomposition while the game renders).
 */
class MineWorld {

    /** Per-shaft animated node bundle. */
    class ShaftNodes {
        /** Root of headframe + miners + cart + rails; hidden while the shaft is locked. */
        var group: Node? = null
        /** Rock lid covering the trench while locked. */
        var lid: Node? = null
        val miners: Array<Node?> = arrayOfNulls(ShaftMinerCount)
        var cart: Node? = null
        val cartLoad: Array<Node?> = arrayOfNulls(4)
        val stockpile: Array<Node?> = arrayOfNulls(4)
        val vein: Array<Node?> = arrayOfNulls(3)
    }

    val shafts: Array<ShaftNodes> = Array(ShaftCount) { ShaftNodes() }

    /** Ore cubes riding the cross belt (surface, along X). */
    val crossBeltOre: Array<Node?> = arrayOfNulls(6)

    /** Ore cubes riding the main belt (toward the crusher). */
    val mainBeltOre: Array<Node?> = arrayOfNulls(4)

    /** Crusher piston (bobs while processing). */
    var crusherPiston: Node? = null

    /** Crusher smoke puffs. */
    val smoke: Array<Node?> = arrayOfNulls(3)

    /** Bar stack on the output platform (visible count follows the silo). */
    val bars: Array<Node?> = arrayOfNulls(8)

    /** The delivery truck (root moves along the road). */
    var truck: Node? = null

    /** Bar cubes riding on the truck's cargo bed. */
    val truckBars: Array<Node?> = arrayOfNulls(6)

    companion object {
        const val ShaftCount = 4
        const val ShaftMinerCount = 3

        // ------------------------------------------------------------------
        // World layout — authored coordinates, meters.
        // ------------------------------------------------------------------

        /** X position of shaft [i] (left to right). */
        fun shaftX(i: Int): Float = -6.3f + 4.2f * i

        const val SHAFT_Z = 4.6f
        const val SHAFT_TRENCH_TOP = 0.05f
        const val SHAFT_FLOOR_Y = -3.2f

        /** Cart rest height at the surface (just under the headframe crossbar). */
        const val CART_TOP_Y = 0.6f
        const val CART_BOTTOM_Y = -3.2f

        const val CROSS_BELT_Y = 0.42f
        const val CROSS_BELT_Z = 3.4f

        const val MAIN_BELT_X = 0f
        const val MAIN_BELT_Y = 0.42f

        const val CRUSHER_Z = -0.9f

        const val TRUCK_LOAD_X = 1.2f
        const val TRUCK_LOAD_Z = -3.6f
        const val MARKET_Z = -10.4f

        // ------------------------------------------------------------------
        // Low-poly palette (ARGB).
        // ------------------------------------------------------------------

        const val GRASS = 0xFF7CB342
        const val DIRT_DECK = 0xFFA1887F
        const val ROCK_LID = 0xFF607D8B
        const val TRENCH_DARK = 0xFF2B3640
        const val WOOD = 0xFF8D6E63
        const val STEEL = 0xFF90A4AE
        const val STEEL_DARK = 0xFF37474F
        const val STEEL_MID = 0xFF546E7A
        const val MACHINE_BODY = 0xFF455A64
        const val PISTON = 0xFFFF7043
        const val SMOKE = 0xFFB0BEC5
        const val CONVEYOR_BELT = 0xFF3B4750
        const val ORE_GENERIC = 0xFF9C7A5B
        const val CART_RED = 0xFFE57373
        const val TRUCK_CAB = 0xFF3949AB
        const val TRUCK_CARGO = 0xFFE8EAF6
        const val ROAD = 0xFF5A6B78
        const val AWNING_RED = 0xFFE53935
        const val AWNING_WHITE = 0xFFFAFAFA
        const val MINER_BODY = 0xFF546E7A
        const val MINER_HELMET = 0xFFFFC93C
        const val TRUNK = 0xFF795548
        const val FOLIAGE_A = 0xFF388E3C
        const val FOLIAGE_B = 0xFF43A047
        const val ROCKS = 0xFF78909C
    }
}
