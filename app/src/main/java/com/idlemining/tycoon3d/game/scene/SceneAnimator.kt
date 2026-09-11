package com.idlemining.tycoon3d.game.scene

import com.google.android.filament.MaterialInstance
import com.idlemining.tycoon3d.core.economy.EconomyRules
import com.idlemining.tycoon3d.game.GameState
import com.idlemining.tycoon3d.game.WorkerAction
import com.idlemining.tycoon3d.game.world.KitMaterials
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.SphereNode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Drives all 3D animation by writing transforms onto the nodes captured in
 * [SceneRefs]. Runs inside SceneView's `onFrame` callback — never triggers
 * recomposition. The simulation advances logic at 10 Hz; this animator
 * interpolates worker movement per-frame and plays all effects.
 */
class SceneAnimator(
    private val refs: SceneRefs,
    private val mats: KitMaterials,
    private val nodeCount: Int,
) {

    private var elapsed = 0.0
    private var lastFrameNanos = 0L

    // Worker visual state (smoothed).
    private var visX = Float.NaN
    private var visZ = Float.NaN
    private var visFacing = 180f
    private var walkPhase = 0.0

    // Node FX state.
    private val prevHp = FloatArray(nodeCount) { Float.NaN }
    private val prevAlive = BooleanArray(nodeCount) { true }
    private val flash = FloatArray(nodeCount) { 0f }

    // Effect pools (in-flight instances).
    private val fragments = Array(SceneRefs.FRAGMENT_POOL) { Fragment() }
    private val drops = Array(SceneRefs.DROP_POOL) { Drop() }

    private class Fragment {
        var active = false
        var x = 0f; var y = 0f; var z = 0f
        var vx = 0f; var vy = 0f; var vz = 0f
        var ttl = 0f
    }

    private class Drop {
        var active = false
        var sx = 0f; var sy = 0f; var sz = 0f
        var t = 0f
        var delay = 0f
    }

    fun advance(state: GameState, frameTimeNanos: Long) {
        if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos
        val dt = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.0, 0.1)
        lastFrameNanos = frameTimeNanos
        elapsed += dt
        val t = elapsed.toFloat()

        updateWorker(state, dt, t)
        updateNodes(state, dt)
        updateFragments(dt)
        updateDrops(state, dt)
        updateDepot(t)
        updateExtractor(state, t)
    }

    // ---------------------------------------------------------------- worker

    private fun updateWorker(state: GameState, dt: Double, t: Float) {
        val root = refs.worker.root ?: return
        val w = state.worker

        // Snap the visual to the logical position on the first frame.
        if (visX.isNaN()) {
            visX = w.x
            visZ = w.z
            visFacing = w.facing
            root.position = Position(w.x, 0f, w.z)
        }

        // Exponential smoothing toward the 10 Hz logical position.
        val blend = (1.0 - exp(-dt * 14.0)).toFloat()
        visX += (w.x - visX) * blend
        visZ += (w.z - visZ) * blend
        visFacing = smoothAngle(visFacing, w.facing, (1.0 - exp(-dt * 10.0)).toFloat())

        val moving = w.action == WorkerAction.WALKING
        val mining = w.action == WorkerAction.MINING
        if (moving) walkPhase += dt * 11.0
        val bob = if (moving) abs(sin(walkPhase)).toFloat() * 0.05f else 0f

        root.position = Position(visX, bob, visZ)
        root.rotation = Rotation(0f, visFacing, 0f)

        // Legs & arms.
        val swing = if (moving) sin(walkPhase).toFloat() else 0f
        refs.worker.legPivots[0]?.rotation = Rotation(swing * 38f, 0f, 0f)
        refs.worker.legPivots[1]?.rotation = Rotation(-swing * 38f, 0f, 0f)
        refs.worker.armPivots[0]?.rotation = Rotation(-swing * 30f, 0f, 0f)

        if (mining) {
            // Pickaxe swing — fast periodic strike.
            val strike = sin(t * 9f)
            refs.worker.armPivots[1]?.rotation = Rotation(-35f - (strike * 55f), 0f, 0f)
            refs.worker.body?.position = Position(0f, 0.58f + abs(strike) * 0.02f, 0f)
        } else {
            refs.worker.armPivots[1]?.rotation = Rotation(swing * 30f, 0f, 0f)
            refs.worker.body?.position = Position(0f, 0.58f, 0f)
        }

        // Backpack grows with how full the pack is.
        val capacity = EconomyRules.backpackCapacity(state.content, state.upgrades).coerceAtLeast(1)
        val fill = (state.totalCarried.toFloat() / capacity).coerceIn(0.15f, 1f)
        refs.worker.backpack?.scale = Scale(1f, 0.55f + 0.6f * fill, 1f)
    }

    private fun smoothAngle(from: Float, to: Float, blend: Float): Float {
        var delta = to - from
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return from + delta * blend
    }

    // ------------------------------------------------------------------ nodes

    private fun updateNodes(state: GameState, dt: Double) {
        for (i in 0 until nodeCount) {
            val node = state.nodes[i]
            val visuals = refs.nodes[i]

            // Break detection → spawn fragments + drops.
            val wasAlive = prevAlive[i]
            if (wasAlive && !node.alive) {
                spawnBreakFx(state, i)
            }
            prevAlive[i] = node.alive

            // Hit flash while being mined.
            if (!prevHp[i].isNaN() && node.alive && node.hp < prevHp[i]) {
                flash[i] = 0.14f
            }
            prevHp[i] = node.hp
            flash[i] = (flash[i] - dt.toFloat()).coerceAtLeast(0f)

            val total = (state.content.nodeType(node.typeId).respawnSeconds).toFloat()
            if (node.alive) {
                val frac = node.hp / node.maxHp
                val s = 0.72f + 0.28f * frac + flash[i] * 1.4f
                visuals.body?.scale = Scale(s)
                visuals.crystalGroup?.isVisible = true
            } else {
                // Regrowing ghost.
                val progress = if (total > 0f) 1f - (node.respawnRemainingSec / total) else 1f
                val s = 0.15f + 0.85f * progress
                visuals.body?.scale = Scale(s)
                visuals.crystalGroup?.isVisible = false
            }
        }
    }

    private fun spawnBreakFx(state: GameState, index: Int) {
        val node = state.nodes[index]
        val def = state.content.nodeType(node.typeId)

        // Rock fragments with fake physics.
        var spawned = 0
        for (f in fragments) {
            if (f.active) continue
            f.active = true
            f.x = node.x + (Math.random().toFloat() - 0.5f) * 0.6f
            f.y = 0.5f + Math.random().toFloat() * 0.4f
            f.z = node.z + (Math.random().toFloat() - 0.5f) * 0.6f
            f.vx = (Math.random().toFloat() - 0.5f) * 3.2f
            f.vy = 2.2f + Math.random().toFloat() * 2.0f
            f.vz = (Math.random().toFloat() - 0.5f) * 3.2f
            f.ttl = 1.1f
            refs.fragments[spawned]?.apply {
                position = Position(f.x, f.y, f.z)
                isVisible = true
            }
            if (++spawned >= 6) break
        }

        // Ore drops flying to the worker — one per yielded unit (max pool).
        val yieldUnits = def.yields.values.sum().coerceAtMost(SceneRefs.DROP_POOL)
        val resMat: MaterialInstance = mats.resource(def.primaryResource)
        var dropsSpawned = 0
        for (d in drops) {
            if (d.active) continue
            if (dropsSpawned >= yieldUnits) break
            d.active = true
            d.sx = node.x + (Math.random().toFloat() - 0.5f) * 0.5f
            d.sy = 0.7f
            d.sz = node.z + (Math.random().toFloat() - 0.5f) * 0.5f
            d.t = 0f
            d.delay = dropsSpawned * 0.07f
            (refs.drops[dropsSpawned] as? SphereNode)?.apply {
                materialInstance = resMat
                position = Position(d.sx, d.sy, d.sz)
                isVisible = true
            }
            dropsSpawned++
        }
    }

    private fun updateFragments(dt: Double) {
        for (i in fragments.indices) {
            val f = fragments[i]
            if (!f.active) continue
            f.ttl -= dt.toFloat()
            if (f.ttl <= 0f) {
                f.active = false
                refs.fragments[i]?.isVisible = false
                continue
            }
            f.vy -= 9.8f * dt.toFloat()
            f.x += f.vx * dt.toFloat()
            f.y += f.vy * dt.toFloat()
            f.z += f.vz * dt.toFloat()
            if (f.y < 0.08f) {
                f.y = 0.08f
                f.vy *= -0.35f
                f.vx *= 0.6f
                f.vz *= 0.6f
            }
            val node = refs.fragments[i] ?: continue
            node.position = Position(f.x, f.y, f.z)
            node.rotation = Rotation(tumble(f), tumble(f) * 0.7f, 0f)
            val fade = (f.ttl / 1.1f).coerceIn(0f, 1f)
            node.scale = Scale(0.5f + 0.5f * fade)
        }
    }

    private fun tumble(f: Fragment): Float = (f.ttl * 420f) % 360f

    private fun updateDrops(state: GameState, dt: Double) {
        val w = state.worker
        for (i in drops.indices) {
            val d = drops[i]
            if (!d.active) continue
            if (d.delay > 0f) {
                d.delay -= dt.toFloat()
                continue
            }
            d.t += dt.toFloat() / 0.45f
            val targetX = if (visX.isNaN()) w.x else visX
            val targetZ = if (visZ.isNaN()) w.z else visZ
            if (d.t >= 1f) {
                d.active = false
                refs.drops[i]?.isVisible = false
                continue
            }
            val x = lerp(d.sx, targetX, d.t)
            val z = lerp(d.sz, targetZ, d.t)
            val arc = sin(Math.PI * d.t).toFloat() * 1.1f
            refs.drops[i]?.position = Position(x, d.sy + arc * (1f - d.t) + 0.5f * d.t, z)
        }
    }

    // -------------------------------------------------------------- ambience

    private fun updateDepot(t: Float) {
        // The coin spins around its own vertical axis — a classic "sell here" beacon.
        refs.depotSign?.let { sign ->
            sign.rotation = Rotation(0f, 90f + t * 110f, 0f)
        }
    }

    private fun updateExtractor(state: GameState, t: Float) {
        val level = state.upgradeLevel("extractor")
        refs.extractor?.isVisible = level > 0
        if (level <= 0) return
        refs.extractorDrill?.let { drill ->
            drill.position = Position(0f, 0.2f + abs(sin(t * 11f)) * 0.09f, 0.3f)
            drill.rotation = Rotation(180f, t * 240f, 0f)
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
