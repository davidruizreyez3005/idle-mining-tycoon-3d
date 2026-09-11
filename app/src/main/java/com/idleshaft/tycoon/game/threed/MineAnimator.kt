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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Drives all 3D animation by writing transforms onto the nodes captured in [MineWorld].
 * Runs inside SceneView's `onFrame` callback — never triggers recomposition.
 *
 * **Fluid game state.** The simulation publishes quantized steps at 100 ms
 * ticks; raw rendering of those steps would look steppy at 60 fps. Everything
 * here is therefore continuous:
 *  - Sim-driven positions pass through frame-rate-independent exponential
 *    smoothing ([smoothTowards]).
 *  - Discrete counts (cart loads, heaps, bars, truck cargo) become animated
 *    *presence* — damped springs with a slight overshoot, so items pop in and
 *    sink out instead of snapping.
 *  - Workers have articulated rigs: walk cycles, pickaxe swings, carry poses
 *    and smooth facing turns.
 *  - Machinery rotates with motion: hoist wheels spin with the cart, rollers
 *    spin with the belts, truck wheels spin with the road speed.
 *  - Phase transitions get one-shot flourishes: the unlock lid flips off, the
 *    shaft rig scale-pops in, the market bursts a coin fountain on sale.
 *  - Ambience never stops: drifting clouds, swaying canopies, waving flags,
 *    swinging signage, breathing ore veins.
 */
class MineAnimator(
    private val world: MineWorld,
    private val materials: ToonMaterials,
) {
    private var elapsed = 0.0
    private var lastFrameNanos = 0L

    private var dominantSiloOre: OreType? = null
    private var dominantTruckOre: OreType? = null

    // ---------------------------------------------------------------- state

    /** Damped spring with a touch of overshoot — discrete counts made fluid. */
    private class Presence(
        var value: Float = 0f,
        var target: Float = 0f,
        private var velocity: Float = 0f,
    ) {
        fun advance(dt: Float, freq: Float, damping: Float): Float {
            val w = 6.28318f * freq
            val k = w * w
            val c = 2f * damping * w
            velocity += (k * (target - value) - c * velocity) * dt
            value += velocity * dt
            return value
        }
    }

    private class MinerAnim {
        var x = 0f; var y = 0f; var z = 0f
        var yaw = 180f
        var walk = 0f
        var lastX = 0f; var lastZ = 0f
        var init = false
    }

    private class CartAnim {
        var y = MineWorld.CART_TOP_Y
        var prevY = MineWorld.CART_TOP_Y
        var wheel = 0f
        var init = false
    }

    private class LidAnim {
        var opening = false
        var progress = 0f
    }

    private val minerAnim = Array(MineWorld.ShaftCount) { Array(MineWorld.ShaftMinerCount) { MinerAnim() } }
    private val cartAnim = Array(MineWorld.ShaftCount) { CartAnim() }
    private val reveal = Array(MineWorld.ShaftCount) { Presence() }
    private val lidAnim = Array(MineWorld.ShaftCount) { LidAnim() }
    private val loadPresence = Array(MineWorld.ShaftCount) { Array(4) { Presence() } }
    private val heapPresence = Array(MineWorld.ShaftCount) { Array(4) { Presence() } }
    private val barPresence = Array(8) { Presence() }
    private val truckBarPresence = Array(6) { Presence() }

    private var rollerAngle = 0f
    private var truckZ = MineWorld.TRUCK_LOAD_Z
    private var truckPrevZ = MineWorld.TRUCK_LOAD_Z
    private var truckYaw = 0f
    private var truckY = 0f
    private var truckWheel = 0f
    private var truckInit = false
    private var lastTruckPhase: TruckPhase? = null
    private var coinStart = -1.0
    private val cloudBaseX = FloatArray(3) { Float.NaN }
    private val cloudBaseZ = FloatArray(3) { Float.NaN }

    // ----------------------------------------------------------------- frame

    fun advance(state: GameState, frameTimeNanos: Long) {
        if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos
        val dt = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.0, 0.1)
        lastFrameNanos = frameTimeNanos
        elapsed += dt
        val d = dt.toFloat()
        val t = elapsed.toFloat()

        updateShafts(state, d, t)
        updateConveyors(state, d, t)
        updateCrusher(state, t)
        updateBarSilo(state, d)
        updateTruck(state, d, t)
        updateAmbience(t)
    }

    /**
     * Frame-rate independent exponential smoothing toward a target.
     * `rate` ~ 14 gives a ~70 ms time constant — hides the 100 ms sim
     * quantization without visible lag.
     */
    private fun smoothTowards(current: Float, target: Float, dt: Float, rate: Float = 14f): Float =
        current + (target - current) * (1f - exp(-rate * dt)).coerceIn(0f, 1f)

    /** Shortest-path angular smoothing (degrees). */
    private fun smoothAngle(current: Float, target: Float, dt: Float, rate: Float = 10f): Float {
        val diff = ((target - current + 540f) % 360f) - 180f
        return current + diff * (1f - exp(-rate * dt)).coerceIn(0f, 1f)
    }

    private fun smoothstep(x: Float): Float = x * x * (3f - 2f * x)

    // ---------------------------------------------------------------- shafts

    private fun updateShafts(state: GameState, dt: Float, t: Float) {
        for (i in 0 until MineWorld.ShaftCount) {
            val shaft = state.shafts[i]
            val nodes = world.shafts[i]
            val unlocked = shaft.unlocked

            // ---- unlock lid flip-off ------------------------------------
            val lid = nodes.lid
            val lidA = lidAnim[i]
            if (lid != null) {
                if (!unlocked) {
                    lid.isVisible = true
                } else if (!lidA.opening) {
                    lidA.opening = true
                    lidA.progress = 0f
                }
                if (lidA.opening && lidA.progress < 1f) {
                    lidA.progress = (lidA.progress + dt * 1.5f).coerceAtMost(1f)
                    val e = 1f - (1f - lidA.progress) * (1f - lidA.progress)
                    lid.position = Position(
                        MineWorld.shaftX(i),
                        0.38f + e * 2.6f,
                        MineWorld.SHAFT_Z + e * 1.1f,
                    )
                    lid.rotation = Rotation(x = e * 30f, z = e * 45f)
                    val s = (1f - e * 0.85f).coerceAtLeast(0.05f)
                    lid.scale = Scale(s)
                    lid.isVisible = lidA.progress < 1f
                }
            }

            // ---- equipment rig reveal ------------------------------------
            val group = nodes.group
            reveal[i].target = if (unlocked) 1f else 0f
            if (group != null) {
                group.isVisible = unlocked
                if (unlocked) {
                    val rv = reveal[i].advance(dt, freq = 2.2f, damping = 0.58f)
                    val rs = rv.coerceIn(0.02f, 1.08f)
                    group.scale = Scale(rs)
                }
            }
            if (!unlocked) continue

            // ---- miners ---------------------------------------------------
            for (j in 0 until MineWorld.ShaftMinerCount) {
                val miner = shaft.miners[j]
                val rig = nodes.miners[j] ?: continue
                val root = rig.root ?: continue
                val anim = minerAnim[i][j]

                val relX = (j - 1) * 0.7f
                val mineX = relX
                val dumpX = relX * 0.8f
                val mineZ = MineWorld.MINER_MINE_Z
                val dumpZ = MineWorld.MINER_DUMP_Z

                val (tx, ty, tz, yawTarget) = when (miner.phase) {
                    MinerPhase.MINING -> {
                        val sway = cos(t * 4.5f + j) * 0.03f
                        Quad(mineX + sway, MineWorld.MINER_Y, mineZ, 180f)
                    }
                    MinerPhase.HAULING -> {
                        val p = miner.progress
                        Quad(
                            lerp(mineX, dumpX, p),
                            MineWorld.MINER_Y,
                            lerp(mineZ, dumpZ, p),
                            0f,
                        )
                    }
                    MinerPhase.RETURNING -> {
                        val p = miner.progress
                        Quad(
                            lerp(dumpX, mineX, p),
                            MineWorld.MINER_Y,
                            lerp(dumpZ, mineZ, p),
                            180f,
                        )
                    }
                    MinerPhase.IDLE -> Quad(mineX, MineWorld.MINER_Y, mineZ, 180f)
                }

                if (!anim.init) {
                    anim.x = tx; anim.y = ty; anim.z = tz
                    anim.lastX = tx; anim.lastZ = tz
                    anim.yaw = yawTarget
                    anim.init = true
                }
                anim.x = smoothTowards(anim.x, tx, dt)
                anim.z = smoothTowards(anim.z, tz, dt)
                anim.y = ty
                anim.yaw = smoothAngle(anim.yaw, yawTarget, dt)

                val vx = (anim.x - anim.lastX) / dt
                val vz = (anim.z - anim.lastZ) / dt
                anim.lastX = anim.x
                anim.lastZ = anim.z
                val speed = if (dt > 0f) kotlin.math.hypot(vx, vz) else 0f

                // Walk cycle drives leg/arm swing; amplitude follows speed.
                if (speed > 0.04f) anim.walk += speed * 6.5f * dt
                val walkAmp = (speed * 2.4f).coerceIn(0f, 1f)
                val swing = sin(anim.walk) * 38f * walkAmp

                var bob = 0f
                when (miner.phase) {
                    MinerPhase.MINING -> {
                        // Pickaxe chops into the seam; body bobs with effort.
                        val chop = sin(t * 7f + j * 2.3f)
                        rig.armR?.rotation = Rotation(x = -14f + chop * 36f)
                        rig.armL?.rotation = Rotation(x = -10f + sin(t * 7f + j * 2.3f + 1.2f) * 10f)
                        rig.legL?.rotation = Rotation(x = 8f)
                        rig.legR?.rotation = Rotation(x = -8f)
                        rig.pick?.isVisible = true
                        rig.ore?.isVisible = false
                        bob = abs(sin(t * 8f + j * 2.1f)) * 0.03f
                    }
                    MinerPhase.HAULING -> {
                        // Both arms forward, carrying the ore chunk.
                        rig.armR?.rotation = Rotation(x = -52f)
                        rig.armL?.rotation = Rotation(x = -52f)
                        rig.legL?.rotation = Rotation(x = swing)
                        rig.legR?.rotation = Rotation(x = -swing)
                        rig.pick?.isVisible = false
                        rig.ore?.isVisible = true
                        bob = abs(sin(anim.walk)) * 0.032f
                    }
                    MinerPhase.RETURNING -> {
                        // Pick stowed over the shoulder.
                        rig.armR?.rotation = Rotation(x = -105f)
                        rig.armL?.rotation = Rotation(x = -swing * 0.6f)
                        rig.legL?.rotation = Rotation(x = swing)
                        rig.legR?.rotation = Rotation(x = -swing)
                        rig.pick?.isVisible = true
                        rig.ore?.isVisible = false
                        bob = abs(sin(anim.walk)) * 0.032f
                    }
                    MinerPhase.IDLE -> {
                        val breathe = sin(t * 2.2f + j) * 4f
                        rig.armR?.rotation = Rotation(x = breathe)
                        rig.armL?.rotation = Rotation(x = -breathe)
                        rig.legL?.rotation = Rotation(x = 0f)
                        rig.legR?.rotation = Rotation(x = 0f)
                        rig.pick?.isVisible = true
                        rig.ore?.isVisible = false
                        bob = sin(t * 2.2f + j) * 0.01f
                    }
                }

                root.position = Position(anim.x, anim.y + bob, anim.z)
                root.rotation = Rotation(y = anim.yaw)
            }

            // ---- elevator cart --------------------------------------------
            val cartA = cartAnim[i]
            val cartNode = nodes.cart
            if (cartNode != null) {
                val targetY = when (shaft.cart.phase) {
                    CartPhase.IDLE, CartPhase.UNLOADING -> MineWorld.CART_TOP_Y
                    CartPhase.DESCENDING -> lerp(MineWorld.CART_TOP_Y, MineWorld.CART_BOTTOM_Y, shaft.cart.progress)
                    CartPhase.LOADING -> MineWorld.CART_BOTTOM_Y
                    CartPhase.ASCENDING -> lerp(MineWorld.CART_BOTTOM_Y, MineWorld.CART_TOP_Y, shaft.cart.progress)
                }
                if (!cartA.init) {
                    cartA.y = targetY
                    cartA.prevY = targetY
                    cartA.init = true
                }
                cartA.y = smoothTowards(cartA.y, targetY, dt, rate = 12f)
                val velY = if (dt > 0f) (cartA.y - cartA.prevY) / dt else 0f
                cartA.prevY = cartA.y

                cartNode.position = Position(0f, cartA.y, MineWorld.CART_Z - MineWorld.SHAFT_Z)
                val moving = (abs(velY) * 2.5f).coerceIn(0f, 1f)
                cartNode.rotation = Rotation(z = sin(t * 2.4f + i) * 1.4f * moving)

                // Hoist wheel spins with the cable; live cable follows the cart.
                cartA.wheel += velY * 195f * dt
                nodes.pulley?.rotation = Rotation(z = cartA.wheel)
                nodes.cable?.let { cable ->
                    val top = MineWorld.WHEEL_Y - 0.32f
                    val bottom = cartA.y + 0.58f
                    val len = (top - bottom).coerceAtLeast(0.05f)
                    cable.position = Position(0f, (top + bottom) / 2f, MineWorld.CART_Z - MineWorld.SHAFT_Z)
                    cable.scale = Scale(1f, len, 1f)
                }
            }

            // ---- cart load presence ---------------------------------------
            val cartCapacity = Economy.cartCapacity(shaft.cartCapacityLevel)
            val loadCount = ceil(shaft.cart.load / cartCapacity * 4).toInt().coerceIn(0, 4)
            for (c in 0 until 4) {
                val node = nodes.cartLoad[c] ?: continue
                loadPresence[i][c].target = if (c < loadCount) 1f else 0f
                val p = loadPresence[i][c].advance(dt, freq = 6f, damping = 0.55f)
                node.isVisible = p > 0.04f
                if (node.isVisible) {
                    val pc = p.coerceIn(0f, 1.15f)
                    node.scale = Scale(0.15f * pc)
                    node.position = Position(-0.27f + c * 0.18f, 0.17f + (1f - p) * 0.3f, 0f)
                }
            }

            // ---- heap presence --------------------------------------------
            val storage = Economy.shaftStorage(shaft.cartCapacityLevel)
            val stockCount = ceil(shaft.buffer / storage * 4).toInt().coerceIn(0, 4)
            val heapSpots = listOf(
                Position(-1.0f, 0.15f, 0.5f),
                Position(-1.0f, 0.15f, 0.9f),
                Position(-1.0f, 0.38f, 0.7f),
                Position(-1.0f, 0.38f, 1.0f),
            )
            for (s in 0 until 4) {
                val node = nodes.stockpile[s] ?: continue
                heapPresence[i][s].target = if (s < stockCount) 1f else 0f
                val p = heapPresence[i][s].advance(dt, freq = 5f, damping = 0.5f)
                node.isVisible = p > 0.04f
                if (node.isVisible) {
                    val pc = p.coerceIn(0f, 1.12f)
                    node.scale = Scale(0.3f * pc)
                    node.position = Position(
                        heapSpots[s].x,
                        MineWorld.PIT_FLOOR_Y + heapSpots[s].y + (1f - p) * 0.28f,
                        MineWorld.HEAP_Z + heapSpots[s].z,
                    )
                }
            }

            // ---- ore seam breathing ---------------------------------------
            for (v in 0 until 3) {
                nodes.vein[v]?.scale = Scale(0.42f * (1f + 0.08f * sin(t * 3f + i * 1.3f + v)))
            }

            // ---- flag waving ----------------------------------------------
            nodes.flag?.rotation = Rotation(y = sin(t * 2.6f + i * 1.7f) * 18f)
        }
    }

    // -------------------------------------------------------------- conveyors

    private fun updateConveyors(state: GameState, dt: Float, t: Float) {
        val flowing = Economy.totalRawOre(state) > 0.0 ||
            state.refinery.phase == RefineryPhase.PROCESSING

        if (flowing) rollerAngle += 320f * dt

        // Rollers spin while the belts flow.
        for (roller in world.crossRollers) {
            roller?.rotation = Rotation(x = rollerAngle)
        }
        for (roller in world.mainRollers) {
            roller?.rotation = Rotation(x = -rollerAngle)
        }

        // Cross belt: chunks tumble from both ends toward the central feeder.
        for (i in world.crossBeltOre.indices) {
            val node = world.crossBeltOre[i] ?: continue
            node.isVisible = flowing
            if (!flowing) continue
            val side = if (i % 2 == 0) -1f else 1f
            val lane = (i / 2).toFloat()
            val phase = ((t * 1.35f + lane * 2.4f + (i % 2) * 1.2f) % MineWorld.CROSS_BELT_TRAVEL)
            val edge = (phase / MineWorld.CROSS_BELT_TRAVEL).coerceIn(0f, 1f)
            val fade = minOf(1f, edge / 0.06f, (1f - edge) / 0.06f).coerceAtLeast(0.25f)
            node.position = Position(
                side * (MineWorld.CROSS_BELT_TRAVEL - phase),
                MineWorld.CROSS_BELT_Y + 0.16f + abs(sin(t * 6f + i * 1.9f)) * 0.02f,
                MineWorld.CROSS_BELT_Z + (lane - 1f) * 0.18f,
            )
            node.rotation = Rotation(y = t * 120f + i * 47f)
            node.scale = Scale(0.13f * fade)
        }

        // Main belt: chunks flow toward the crusher hopper.
        for (i in world.mainBeltOre.indices) {
            val node = world.mainBeltOre[i] ?: continue
            node.isVisible = flowing
            if (!flowing) continue
            val phase = (t * 1.5f + i * 0.85f) % 2.5f
            val edge = phase / 2.5f
            val fade = minOf(1f, edge / 0.08f, (1f - edge) / 0.08f).coerceAtLeast(0.25f)
            node.position = Position(
                MineWorld.MAIN_BELT_X,
                MineWorld.MAIN_BELT_Y + 0.16f + abs(sin(t * 6.5f + i * 1.6f)) * 0.02f,
                3.2f - phase,
            )
            node.rotation = Rotation(y = t * 120f + i * 31f)
            node.scale = Scale(0.13f * fade)
        }
    }

    // ---------------------------------------------------------------- crusher

    private fun updateCrusher(state: GameState, t: Float) {
        val processing = state.refinery.phase == RefineryPhase.PROCESSING

        // Stamp piston: smooth ease onto the anvil plate.
        val chop = if (processing) sin(t * 4.5f) * sin(t * 4.5f) * 0.2f else 0f
        world.crusherPiston?.position = Position(0f, 2.06f - chop, MineWorld.CRUSHER_Z - 0.95f)

        // Hopper wobble while crunching.
        world.crusherHopper?.rotation = Rotation(
            x = 180f,
            z = if (processing) sin(t * 22f) * 0.7f else 0f,
        )

        // Alternating status lights.
        for (l in 0 until world.crusherLights.size) {
            world.crusherLights[l]?.let { light ->
                val pulse = if (processing) {
                    0.85f + 0.3f * abs(sin(t * 3f + l * 1.5708f))
                } else {
                    0.9f
                }
                light.scale = Scale(pulse)
            }
        }

        // Chimney smoke puffs.
        for (i in world.smoke.indices) {
            val puff = world.smoke[i] ?: continue
            puff.isVisible = processing
            if (!processing) continue
            val cycle = (t * 0.45f + i * 0.33f) % 1f
            puff.position = Position(-0.95f, 2.75f + cycle * 1.0f, MineWorld.CRUSHER_Z - 0.6f)
            val s = (1f - cycle * 0.75f).coerceAtLeast(0.1f)
            puff.scale = Scale(s)
        }
    }

    // --------------------------------------------------------------- bar silo

    private fun updateBarSilo(state: GameState, dt: Float) {
        val total = Economy.totalBars(state)
        for (b in world.bars.indices) {
            val node = world.bars[b] ?: continue
            barPresence[b].target = if (b < total.coerceAtMost(8)) 1f else 0f
            val p = barPresence[b].advance(dt, freq = 4.5f, damping = 0.5f)
            node.isVisible = p > 0.04f
            if (node.isVisible) {
                val pc = p.coerceIn(0f, 1.12f)
                node.scale = Scale(0.42f * pc, 0.85f * pc, 0.45f * pc)
                node.position = Position(-0.63f + (b % 4) * 0.42f, 0.5f + (b / 4) * 0.3f + (1f - p) * 0.32f, -3.7f)
            }
        }
        val dominant = state.barSilo.entries
            .filter { it.value > 0 }
            .maxByOrNull { it.value }?.key
        if (dominant != null && dominant != dominantSiloOre) {
            dominantSiloOre = dominant
            val instance = materials.bar(dominant)
            for (b in 0 until 8) {
                world.bars[b]?.setMaterialInstanceAt(0, instance)
            }
        }
    }

    // ------------------------------------------------------------------ truck

    private fun updateTruck(state: GameState, dt: Float, t: Float) {
        val truckNode = world.truck ?: return
        val logistics = state.logistics

        val targetZ: Float
        val yawTarget: Float
        var hop = 0f
        when (logistics.phase) {
            TruckPhase.IDLE, TruckPhase.LOADING -> {
                targetZ = MineWorld.TRUCK_LOAD_Z
                yawTarget = 0f
                hop = abs(sin(t * 10f)) * 0.012f
            }
            TruckPhase.OUTBOUND -> {
                // Ease the acceleration at the start and the brake at the end.
                targetZ = lerp(MineWorld.TRUCK_LOAD_Z, MineWorld.TRUCK_SELL_Z, smoothstep(logistics.progress))
                yawTarget = 0f
            }
            TruckPhase.SELLING -> {
                targetZ = MineWorld.TRUCK_SELL_Z
                yawTarget = 0f
                hop = abs(sin(t * 8f)) * 0.05f
            }
            TruckPhase.INBOUND -> {
                // Ease the U-turn during the first 30% of the return trip.
                val turn = (logistics.progress / 0.3f).coerceIn(0f, 1f)
                yawTarget = 180f * smoothstep(turn)
                targetZ = lerp(
                    MineWorld.TRUCK_SELL_Z,
                    MineWorld.TRUCK_LOAD_Z,
                    smoothstep(logistics.progress),
                )
            }
        }

        if (!truckInit) {
            truckZ = targetZ
            truckPrevZ = targetZ
            truckYaw = yawTarget
            truckY = hop
            truckInit = true
        }
        truckZ = smoothTowards(truckZ, targetZ, dt, rate = 9f)
        truckYaw = smoothAngle(truckYaw, yawTarget, dt, rate = 6f)
        truckY = smoothTowards(truckY, hop, dt, rate = 20f)

        val velZ = if (dt > 0f) (truckZ - truckPrevZ) / dt else 0f
        truckPrevZ = truckZ

        // Wheels roll with the road speed; body pitches under acceleration.
        truckWheel += velZ * 240f * dt
        val pitch = (velZ * -0.8f).coerceIn(-2.5f, 2.5f)

        truckNode.position = Position(MineWorld.TRUCK_LOAD_X, truckY, truckZ)
        truckNode.rotation = Rotation(x = pitch, y = truckYaw)

        for (w in world.truckWheels.indices) {
            world.truckWheels[w]?.rotation = Rotation(x = truckWheel)
        }

        // Blob shadow glides along under the truck, glued to the road.
        world.truckShadow?.let { shadow ->
            val sc = shadow.position
            shadow.position = Position(
                MineWorld.TRUCK_LOAD_X,
                MineWorld.TRUCK_SHADOW_Y,
                smoothTowards(sc.z, targetZ, dt, rate = 10f),
            )
        }

        // Exhaust puffs — faster while driving, lazy idle otherwise.
        val driving = abs(velZ) > 0.35f
        val puffSpeed = if (driving) 1.3f else 0.35f
        for (e in world.truckExhaust.indices) {
            val puff = world.truckExhaust[e] ?: continue
            val cycle = (t * puffSpeed + e * 0.5f) % 1f
            puff.position = Position(0.44f, 1.85f + cycle * 0.55f, -0.7f)
            val s = (1f - cycle * 0.7f).coerceAtLeast(0.08f)
            puff.scale = Scale(s)
        }

        // Cargo ingots pop with presence.
        val loadTotal = logistics.load.values.sum()
        for (b in world.truckBars.indices) {
            val node = world.truckBars[b] ?: continue
            truckBarPresence[b].target = if (b < loadTotal.coerceAtMost(6)) 1f else 0f
            val p = truckBarPresence[b].advance(dt, freq = 5f, damping = 0.55f)
            node.isVisible = p > 0.04f
            if (node.isVisible) {
                val pc = p.coerceIn(0f, 1.12f)
                node.scale = Scale(0.24f * pc, 0.7f * pc, 0.4f * pc)
                node.position = Position(
                    -0.3f + (b % 3) * 0.3f,
                    1.5f + (1f - p) * 0.25f,
                    0.32f - (b / 3) * 0.5f,
                )
            }
        }
        val loadOre = logistics.load.entries
            .filter { it.value > 0 }
            .maxByOrNull { it.value }?.key
        if (loadOre != null && loadOre != dominantTruckOre) {
            dominantTruckOre = loadOre
            val instance = materials.bar(loadOre)
            for (b in 0 until 6) {
                world.truckBars[b]?.setMaterialInstanceAt(0, instance)
            }
        }

        // ---- coin fountain on every sale --------------------------------
        if (logistics.phase != lastTruckPhase) {
            if (logistics.phase == TruckPhase.SELLING) coinStart = elapsed
            lastTruckPhase = logistics.phase
        }
        val selling = logistics.phase == TruckPhase.SELLING
        for (c in world.coins.indices) {
            val coin = world.coins[c] ?: continue
            val local = elapsed - coinStart - c * 0.1
            val active = selling && coinStart >= 0 && local in 0.0..1.1
            coin.isVisible = active
            if (!active) continue
            val l = local.toFloat()
            val y = 1.1f + 3.4f * l - 3.1f * l * l
            val fade = if (l > 0.75f) (1.1f - l) / 0.35f else 1f
            coin.position = Position(
                MineWorld.TRUCK_LOAD_X + (c - 2) * 0.17f,
                y,
                MineWorld.MARKET_Z + 1.1f + l * 0.45f,
            )
            coin.rotation = Rotation(x = l * 720f)
            coin.scale = Scale(fade.coerceAtLeast(0.1f))
        }
    }

    // --------------------------------------------------------------- ambience

    private fun updateAmbience(t: Float) {
        // Clouds drift east and wrap around.
        for (i in world.clouds.indices) {
            val cloud = world.clouds[i] ?: continue
            if (cloudBaseX[i].isNaN()) {
                cloudBaseX[i] = cloud.position.x
                cloudBaseZ[i] = cloud.position.z
            }
            val speed = 0.22f + i * 0.09f
            var x = cloudBaseX[i] + t * speed
            x = ((x + 24f) % 48f) - 24f
            cloud.position = Position(x, cloud.position.y, cloudBaseZ[i])
        }

        // Tree canopies sway.
        for (i in world.canopies.indices) {
            world.canopies[i].rotation = Rotation(z = sin(t * 0.7f + i * 1.3f) * 1.6f)
        }

        // Market coin sign swings.
        world.coinSign?.rotation = Rotation(z = sin(t * 1.7f) * 6f)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Lightweight 4-tuple for miner targets. */
    private data class Quad(val x: Float, val y: Float, val z: Float, val yaw: Float)
}
