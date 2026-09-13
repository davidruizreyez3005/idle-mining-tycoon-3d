package com.idlemining.tycoon3d.game.scene

import com.google.android.filament.Engine
import com.idlemining.tycoon3d.core.content.CameraDef
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.math.Position
import io.github.sceneview.node.CameraNode
import kotlin.math.cos
import kotlin.math.sin

/**
 * A [CameraNode] that always renders with an orthographic projection.
 *
 * SceneView calls [updateProjection] itself whenever the surface is resized
 * (it would otherwise fall back to a perspective lens), so overriding it here
 * keeps the view orthographic through every resize / rotation / foldable state
 * change. The visible vertical extent is [orthoZoomHeight] meters; the
 * horizontal extent follows the viewport aspect ratio.
 */
class OrthoCameraNode(engine: Engine) : CameraNode(engine) {

    /** Vertical extent of the orthographic box, in world meters. */
    var orthoZoomHeight: Float = 26f

    var orthoNear: Float = 1f

    var orthoFar: Float = 400f

    override fun updateProjection(
        focalLength: Double,
        near: Float,
        far: Float,
        aspect: Double,
    ) {
        // Perspective parameters are ignored — this camera is always ortho.
        applyOrthoProjection()
    }

    /** (Re)applies the ortho frustum from the current viewport + zoom. */
    fun applyOrthoProjection() {
        val view = this.view ?: return
        val aspect = view.viewport.width.toDouble() / view.viewport.height.toDouble()
        val halfH = orthoZoomHeight / 2.0
        val halfW = halfH * aspect
        camera.setProjection(
            com.google.android.filament.Camera.Projection.ORTHO,
            -halfW, halfW, -halfH, halfH,
            orthoNear.toDouble(), orthoFar.toDouble(),
        )
    }
}

/**
 * The Phase 3 camera — a locked 45° orthographic rig with player pan & zoom.
 *
 * The viewing angle (yaw / pitch) and the projection (orthographic) NEVER
 * change during play. What the player controls:
 *
 *  - **Pan** — one-finger drag slides the framing target along the ground
 *    plane, clamped to a rectangular window around the authored anchor so
 *    the mine and depot always remain reachable in one gesture.
 *  - **Zoom** — pinch scales the orthographic box height (meters visible),
 *    anchored on the world point under the pinch midpoint, clamped to
 *    [CameraDef.zoomMinHeight] / [CameraDef.zoomMaxHeight].
 *
 * The transform is written directly onto the [OrthoCameraNode] (SceneView gets
 * `cameraManipulator = null`, so nothing else ever touches it).
 *
 * **Per-frame cost (Phase 3.5):** the angle is locked, so the camera's
 * *rotation* is a constant — it is computed once (captured after a one-time
 * `lookAt`) and replayed from cache. Position, projection and rotation are
 * only written when something actually changed ([dirty] — a pan/zoom gesture
 * or a viewport resize). While the player is not touching the screen this
 * controller does zero Filament work, zero allocations and zero JNI calls;
 * the pre-tuning implementation ran a full lookAt + setProjection + Float3
 * allocations on every single frame.
 */
class OrthoCameraController(private val config: CameraDef) {

    private val anchorX = config.target[0]
    private val anchorY = config.target.getOrElse(1) { 0f }
    private val anchorZ = config.target.getOrElse(2) { 0f }

    /** Current framing target (x, z) — the ground point at screen center. */
    private var targetX = anchorX
    private var targetZ = anchorZ

    /** Current ortho box height in meters (the zoom level). */
    private var zoomHeight = config.zoomHeight

    // Eye offset from the target, derived once from the authored angle.
    private val dirX: Float
    private val dirY: Float
    private val dirZ: Float

    // Ground-plane camera axes: forward = where the camera looks (projected
    // to the ground), right = screen-right on the ground.
    private val fwdX: Float
    private val fwdZ: Float
    private val rightX: Float
    private val rightZ: Float

    /** Raised by pan/zoom/reset and by viewport-size changes; cleared once applied. */
    private var dirty = true

    /** Last observed viewport size (a change means the surface was resized). */
    private var lastViewportW = -1
    private var lastViewportH = -1

    /** Cached locked orientation — captured after the one-time lookAt. */
    private var lockedRotation: Quaternion? = null

    init {
        val yawRad = Math.toRadians(config.yaw.toDouble())
        val pitchRad = Math.toRadians(config.pitch.toDouble())
        // yaw 0 -> camera on +Z looking toward -Z; positive yaw swings toward +X.
        dirX = (sin(yawRad) * cos(pitchRad)).toFloat()
        dirY = sin(pitchRad).toFloat()
        dirZ = (cos(yawRad) * cos(pitchRad)).toFloat()
        // Camera looks toward the target: view forward = -dir, projected to XZ.
        fwdX = -sin(yawRad).toFloat()
        fwdZ = -cos(yawRad).toFloat()
        rightX = cos(yawRad).toFloat()
        rightZ = -sin(yawRad).toFloat()
    }

    /**
     * Pans the framing target by a screen-space drag delta (pixels).
     * "Grab the ground": dragging right moves the world right, i.e. the camera
     * target moves left; dragging down reveals what was further away.
     */
    fun pan(cameraNode: OrthoCameraNode, dxPx: Float, dyPx: Float) {
        val (vw, vh) = viewportSize(cameraNode) ?: return
        if (vw <= 0 || vh <= 0) return
        val worldPerPx = zoomHeight / vh

        // Screen right -> ground right; screen down -> ground forward.
        val wx = targetX - rightX * dxPx * worldPerPx
        val wz = targetZ + fwdZ * dyPx * worldPerPx
        val nx = clampX(wx)
        val nz = clampZ(wz)
        if (nx != targetX || nz != targetZ) {
            targetX = nx
            targetZ = nz
            dirty = true
        }
    }

    /**
     * Zooms by [factor] (>1 = zoom in, <1 = zoom out), keeping the world point
     * under the pinch midpoint ([focusXpx], [focusYpx]) anchored in place.
     */
    fun zoom(cameraNode: OrthoCameraNode, factor: Float, focusXpx: Float, focusYpx: Float) {
        val (vw, vh) = viewportSize(cameraNode) ?: return
        if (vw <= 0 || vh <= 0 || !factor.isFinite() || factor <= 0f) return

        val newZoom = (zoomHeight / factor).coerceIn(config.zoomMinHeight, config.zoomMaxHeight)
        val wppOld = zoomHeight / vh
        val wppNew = newZoom / vh

        // World point under the pinch midpoint, before the zoom.
        val cx = vw / 2f
        val cy = vh / 2f
        val px = targetX + rightX * (focusXpx - cx) * wppOld
        val pz = targetZ + fwdZ * (cy - focusYpx) * wppOld

        // Re-anchor the target so P stays under the midpoint after the zoom.
        targetX = clampX(px - rightX * (focusXpx - cx) * wppNew)
        targetZ = clampZ(pz + fwdZ * (cy - focusYpx) * wppNew)
        if (newZoom != zoomHeight) {
            zoomHeight = newZoom
            dirty = true
        }
    }

    /** Recenter on the authored anchor at the default zoom (future HUD use). */
    fun reset() {
        targetX = anchorX
        targetZ = anchorZ
        zoomHeight = config.zoomHeight
        dirty = true
    }

    /**
     * Advances the camera by one frame. Cheap by design: nothing happens
     * unless [dirty] was raised (gesture or resize). The rotation is computed
     * once and replayed from [lockedRotation]; the projection is re-applied on
     * zoom/resize so any external perspective reset is overridden immediately.
     */
    fun update(cameraNode: OrthoCameraNode) {
        val vp = cameraNode.view?.viewport
        val vw = vp?.width ?: 0
        val vh = vp?.height ?: 0
        if (vw != lastViewportW || vh != lastViewportH) {
            lastViewportW = vw
            lastViewportH = vh
            dirty = true
        }
        // The first frames may arrive before the surface exists (0x0) — stay
        // dirty until the viewport is real, then apply once and go quiet.
        if (!dirty && vw > 0) return
        if (vw <= 0 || vh <= 0) return

        cameraNode.orthoZoomHeight = zoomHeight
        cameraNode.orthoNear = config.near
        cameraNode.orthoFar = config.far
        cameraNode.applyOrthoProjection()

        val eye = Position(
            targetX + dirX * config.distance,
            anchorY + dirY * config.distance,
            targetZ + dirZ * config.distance,
        )
        cameraNode.position = eye

        val locked = lockedRotation
        if (locked == null) {
            // One-time: solve the orientation for the authored angle, then
            // cache it — the angle never changes, so neither does this value.
            cameraNode.lookAt(Position(targetX, anchorY + LOOK_AT_LIFT, targetZ))
            lockedRotation = cameraNode.quaternion
        } else {
            cameraNode.quaternion = locked
        }

        dirty = false
    }

    private fun viewportSize(cameraNode: OrthoCameraNode): Pair<Int, Int>? {
        val vp = cameraNode.view?.viewport ?: return null
        return vp.width to vp.height
    }

    private fun clampX(v: Float): Float =
        (v - anchorX).coerceIn(-config.panRangeX, config.panRangeX) + anchorX

    private fun clampZ(v: Float): Float =
        (v - anchorZ).coerceIn(-config.panRangeZ, config.panRangeZ) + anchorZ

    companion object {
        /** Look slightly above the ground plane so props read against the horizon. */
        private const val LOOK_AT_LIFT = 0.9f
    }
}
