package com.idlemining.tycoon3d.game.scene

import com.google.android.filament.Engine
import com.idlemining.tycoon3d.core.content.CameraDef
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
 * The transform is written per frame directly onto the [OrthoCameraNode]
 * (SceneView gets `cameraManipulator = null`, so nothing else ever touches
 * it), and the ortho projection is re-applied every frame so any external
 * perspective reset (surface resize) is immediately overridden.
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
        targetX = clampX(wx)
        targetZ = clampZ(wz)
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
        zoomHeight = newZoom
    }

    /** Recenter on the authored anchor at the default zoom (future HUD use). */
    fun reset() {
        targetX = anchorX
        targetZ = anchorZ
        zoomHeight = config.zoomHeight
    }

    /**
     * Advances the camera by one frame: applies the ortho projection (winning
     * over any perspective reset triggered by a surface resize) and writes the
     * fixed-angle eye position + orientation.
     */
    fun update(cameraNode: OrthoCameraNode) {
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
        cameraNode.lookAt(Position(targetX, anchorY + LOOK_AT_LIFT, targetZ))
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
