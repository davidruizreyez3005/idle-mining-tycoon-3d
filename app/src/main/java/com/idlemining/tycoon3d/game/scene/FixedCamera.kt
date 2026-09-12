package com.idlemining.tycoon3d.game.scene

import com.idlemining.tycoon3d.core.content.CameraDef
import io.github.sceneview.node.CameraNode
import io.github.sceneview.math.Position
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The fixed cinematic camera — Phase 2.
 *
 * The viewing angle (yaw / pitch / distance / fov) is authored in `world.json`
 * and NEVER changes during play: there are no orbit, pan or zoom gestures. What
 * does move is the framing target, which softly tracks the worker inside a
 * clamped window around the authored anchor:
 *
 *  1. Deadzone — the camera ignores the worker entirely while they stay within
 *     `followDeadzone` meters of the current target (no nervous drift).
 *  2. Proportional follow — beyond the deadzone the target is pulled toward the
 *     worker with strength `followStrength` per second, smoothed exponentially.
 *  3. Window clamp — the target can never leave `±followRange` around the
 *     authored anchor, so the mine and depot always stay in frame.
 *
 * The camera transform is written per frame directly onto the [CameraNode]
 * (SceneView gets `cameraManipulator = null`, so nothing else ever touches it).
 */
class FixedCameraController(private val config: CameraDef) {

    private val anchorX = config.target[0]
    private val anchorY = config.target.getOrElse(1) { 0f }
    private val anchorZ = config.target.getOrElse(2) { 0f }

    /** Current smoothed framing target (x, z). */
    private var targetX = anchorX
    private var targetZ = anchorZ

    /** Eye offset from the target, derived once from the authored angle. */
    private val dirX: Float
    private val dirY: Float
    private val dirZ: Float

    init {
        val yawRad = Math.toRadians(config.yaw.toDouble())
        val pitchRad = Math.toRadians(config.pitch.toDouble())
        // yaw 0 -> camera on +Z looking toward -Z; positive yaw swings toward +X.
        dirX = (sin(yawRad) * cos(pitchRad)).toFloat()
        dirY = sin(pitchRad).toFloat()
        dirZ = (cos(yawRad) * cos(pitchRad)).toFloat()
    }

    /** True once the first update has placed the camera (used to snap, not ease, at start). */
    private var placed = false

    /**
     * Advances the camera by one frame.
     *
     * @param workerX worker logical x position (meters)
     * @param workerZ worker logical z position (meters)
     * @param dt frame delta seconds (clamped upstream to [0, 0.1])
     */
    fun update(cameraNode: CameraNode, workerX: Float, workerZ: Float, dt: Float) {
        // ---- 1-3: soft follow of the framing target --------------------------------
        if (!placed) {
            // Snap the framing to the worker on the very first frame so the intro
            // composition is centered on the action, not the raw anchor.
            targetX = clampToWindow(workerX, true)
            targetZ = clampToWindow(workerZ, false)
            placed = true
        } else {
            val pull = 1f - exp(-dt * config.followStrength * FOLLOW_RATE).toFloat()
            val desiredX = desiredTarget(workerX, targetX, true)
            val desiredZ = desiredTarget(workerZ, targetZ, false)
            targetX += (desiredX - targetX) * pull
            targetZ += (desiredZ - targetZ) * pull
        }

        // ---- Fixed eye position + orientation ---------------------------------------
        val eye = Position(
            targetX + dirX * config.distance,
            anchorY + dirY * config.distance,
            targetZ + dirZ * config.distance,
        )
        cameraNode.position = eye
        cameraNode.lookAt(Position(targetX, anchorY + LOOK_AT_LIFT, targetZ))
    }

    /**
     * Desired target along one axis: no movement inside the deadzone, a
     * deadzone-overflow pull outside it, clamped to the follow window.
     */
    private fun desiredTarget(worker: Float, current: Float, xAxis: Boolean): Float {
        val delta = worker - current
        val deadzone = config.followDeadzone
        if (delta * delta < deadzone * deadzone) return current
        val direction = if (delta >= 0f) 1f else -1f
        val overflow = delta - direction * deadzone
        return clampToWindow(current + overflow, xAxis)
    }

    private fun clampToWindow(value: Float, xAxis: Boolean): Float {
        val range = if (xAxis) config.followRangeX else config.followRangeZ
        val anchor = if (xAxis) anchorX else anchorZ
        return (value - anchor).coerceIn(-range, range) + anchor
    }

    companion object {
        /** Follow responsiveness — followStrength 1.0 reaches ~63% of the pull in ~1 s. */
        private const val FOLLOW_RATE = 5.5f

        /** Look slightly above the ground plane so props read against the horizon. */
        private const val LOOK_AT_LIFT = 0.9f
    }
}
