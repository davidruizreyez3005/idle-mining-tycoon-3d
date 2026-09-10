package com.idleshaft.tycoon.game.threed

import com.idleshaft.tycoon.domain.CartPhase
import com.idleshaft.tycoon.domain.Economy
import com.idleshaft.tycoon.domain.GameState
import com.idleshaft.tycoon.domain.MinerPhase
import com.idleshaft.tycoon.domain.OreType
import com.idleshaft.tycoon.domain.RefineryPhase
import com.idleshaft.tycoon.domain.TruckPhase
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drives all 3D animation by writing transforms onto the nodes captured in [MineWorld].
 * Runs inside SceneView's `onFrame` callback — never triggers recomposition.
 */
class MineAnimator(
    private val world: MineWorld,
    private val materials: ColorMaterials,
) {
    private var elapsed = 0.0
    private var lastFrameNanos = 0L

    /** Dominant bar silo ore — recolors the bar stack only when it changes. */
    private var dominantSiloOre: OreType? = null

    fun advance(state: GameState, frameTimeNanos: Long) {
        if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos
        val dt = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.0, 0.1)
        lastFrameNanos = frameTimeNanos
        elapsed += dt

        updateShafts(state)
        updateConveyors(state)
        updateCrusher(state)
        updateBarSilo(state)
        updateTruck(state)
    }

    // ----------------------------------------------------------------- shafts

    private fun updateShafts(state: GameState) {
        for (i in 0 until MineWorld.ShaftCount) {
            val shaft = state.shafts[i]
            val nodes = world.shafts[i]

            val unlocked = shaft.unlocked
            nodes.group?.isVisible = unlocked
            nodes.lid?.isVisible = !unlocked
            if (!unlocked) continue

            val x = MineWorld.shaftX(i)
            val t = elapsed.toFloat()

            // Miners: walk between the ore seam (back of trench) and the cart stop.
            val storage = Economy.shaftStorage(shaft.cartCapacityLevel)
            for (j in 0 until MineWorld.ShaftMinerCount) {
                val miner = shaft.miners[j]
                val minerNode = nodes.miners[j] ?: continue
                val offsetX = (j - 1) * 0.7f
                val mineX = x + offsetX
                val mineZ = MineWorld.SHAFT_Z - 0.8f
                val mineY = -3.62f
                val dumpX = x + (j - 1) * 0.35f
                val dumpZ = MineWorld.SHAFT_Z + 0.1f
                val dumpY = -3.5f

                when (miner.phase) {
                    MinerPhase.MINING -> {
                        val bob = sin(t * 9f + j * 2.1f) * 0.04f
                        val sway = cos(t * 4.5f + j) * 0.03f
                        minerNode.position = Position(mineX + sway, mineY + bob, mineZ)
                    }
                    MinerPhase.HAULING -> {
                        val p = miner.progress
                        minerNode.position = Position(
                            lerp(mineX, dumpX, p),
                            lerp(mineY, dumpY, p) + sin(t * 12f + j) * 0.02f,
                            lerp(mineZ, dumpZ, p),
                        )
                    }
                    MinerPhase.RETURNING -> {
                        val p = miner.progress
                        minerNode.position = Position(
                            lerp(dumpX, mineX, p),
                            lerp(dumpY, mineY, p),
                            lerp(dumpZ, mineZ, p),
                        )
                    }
                    MinerPhase.IDLE -> {
                        minerNode.position = Position(mineX, mineY, mineZ)
                    }
                }
            }

            // Cart: vertical travel in the shaft.
            val cart = shaft.cart
            nodes.cart?.let { cartNode ->
                val y = when (cart.phase) {
                    CartPhase.IDLE, CartPhase.UNLOADING -> MineWorld.CART_TOP_Y
                    CartPhase.DESCENDING -> lerp(MineWorld.CART_TOP_Y, MineWorld.CART_BOTTOM_Y, cart.progress)
                    CartPhase.LOADING -> MineWorld.CART_BOTTOM_Y
                    CartPhase.ASCENDING -> lerp(MineWorld.CART_BOTTOM_Y, MineWorld.CART_TOP_Y, cart.progress)
                }
                cartNode.position = Position(x, y, MineWorld.SHAFT_Z)
            }

            // Ore load stacked on the cart.
            val cartCapacity = Economy.cartCapacity(shaft.cartCapacityLevel)
            val loadCount = ceil(cart.load / cartCapacity * 4).toInt().coerceIn(0, 4)
            for (c in 0 until 4) {
                nodes.cartLoad[c]?.isVisible = c < loadCount
            }

            // Underground stockpile.
            val stockCount = ceil(shaft.buffer / storage * 4).toInt().coerceIn(0, 4)
            for (s in 0 until 4) {
                nodes.stockpile[s]?.isVisible = s < stockCount
            }

            // Ore seam breathing.
            for (v in 0 until 3) {
                nodes.vein[v]?.scale = Scale(1f + 0.07f * sin(t * 3f + i * 1.3f + v))
            }
        }
    }

    // -------------------------------------------------------------- conveyors

    private fun updateConveyors(state: GameState) {
        val flowing = Economy.totalRawOre(state) > 0.0 ||
            state.refinery.phase == RefineryPhase.PROCESSING
        val t = elapsed.toFloat()

        // Cross belt: cubes flow from both ends toward the central feeder.
        for (i in world.crossBeltOre.indices) {
            val node = world.crossBeltOre[i] ?: continue
            node.isVisible = flowing
            if (!flowing) continue
            val side = if (i % 2 == 0) -1f else 1f
            val lane = (i / 2).toFloat()
            val phase = ((t * 1.35f + lane * 2.4f + (i % 2) * 1.2f) % 7.2f)
            node.position = Position(
                side * (7.2f - phase),
                MineWorld.CROSS_BELT_Y + 0.17f,
                MineWorld.CROSS_BELT_Z + (lane - 1f) * 0.18f,
            )
        }

        // Main belt: cubes flow toward the crusher hopper.
        for (i in world.mainBeltOre.indices) {
            val node = world.mainBeltOre[i] ?: continue
            node.isVisible = flowing
            if (!flowing) continue
            val phase = (t * 1.5f + i * 0.85f) % 2.8f
            node.position = Position(
                MineWorld.MAIN_BELT_X,
                MineWorld.MAIN_BELT_Y + 0.17f,
                3.2f - phase,
            )
        }
    }

    // ---------------------------------------------------------------- crusher

    private fun updateCrusher(state: GameState) {
        val processing = state.refinery.phase == RefineryPhase.PROCESSING
        val t = elapsed.toFloat()

        world.crusherPiston?.let { piston ->
            piston.position = Position(
                0f,
                if (processing) 2.15f + abs(sin(t * 9f)) * 0.42f else 2.35f,
                MineWorld.CRUSHER_Z - 0.15f,
            )
        }
        for (i in world.smoke.indices) {
            val puff = world.smoke[i] ?: continue
            puff.isVisible = processing
            if (!processing) continue
            val cycle = (t * 0.45f + i * 0.33f) % 1f
            puff.position = Position(-0.3f + i * 0.3f, 2.65f + cycle * 0.9f, MineWorld.CRUSHER_Z - 0.15f)
            val s = (1f - cycle * 0.75f).coerceAtLeast(0.1f)
            puff.scale = Scale(s)
        }
    }

    // --------------------------------------------------------------- bar silo

    private fun updateBarSilo(state: GameState) {
        val total = Economy.totalBars(state)
        for (b in world.bars.indices) {
            world.bars[b]?.isVisible = b < total.coerceAtMost(8)
        }
        val dominant = state.barSilo.entries
            .filter { it.value > 0 }
            .maxByOrNull { it.value }?.key
        if (dominant != null && dominant != dominantSiloOre) {
            dominantSiloOre = dominant
            val instance = materials.bar(dominant)
            for (b in 0 until 8) {
                (world.bars[b] as? CubeNode)?.materialInstance = instance
            }
        }
    }

    // ------------------------------------------------------------------ truck

    private fun updateTruck(state: GameState) {
        val truck = world.truck ?: return
        val logistics = state.logistics
        val t = elapsed.toFloat()

        val (z, yaw, hop) = when (logistics.phase) {
            TruckPhase.IDLE, TruckPhase.LOADING ->
                Triple(MineWorld.TRUCK_LOAD_Z, 0f, abs(sin(t * 18f)) * 0.02f)
            TruckPhase.OUTBOUND ->
                Triple(lerp(MineWorld.TRUCK_LOAD_Z, MineWorld.MARKET_Z, logistics.progress), 0f, 0f)
            TruckPhase.SELLING ->
                Triple(MineWorld.MARKET_Z, 0f, abs(sin(t * 12f)) * 0.09f)
            TruckPhase.INBOUND ->
                Triple(lerp(MineWorld.MARKET_Z, MineWorld.TRUCK_LOAD_Z, logistics.progress), 180f, 0f)
        }
        truck.position = Position(MineWorld.TRUCK_LOAD_X, hop, z)
        truck.rotation = Rotation(0f, yaw, 0f)

        // Cargo bars + their color.
        val loadTotal = logistics.load.values.sum()
        for (b in world.truckBars.indices) {
            world.truckBars[b]?.isVisible = b < loadTotal.coerceAtMost(6)
        }
        val loadOre = logistics.load.entries
            .filter { it.value > 0 }
            .maxByOrNull { it.value }?.key
        if (loadOre != null) {
            val instance = materials.bar(loadOre)
            for (b in 0 until 6) {
                (world.truckBars[b] as? CubeNode)?.materialInstance = instance
            }
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
