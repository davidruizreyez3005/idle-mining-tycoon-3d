package com.idlemining.tycoon3d.game.scene

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.idlemining.tycoon3d.core.content.GameContent
import com.idlemining.tycoon3d.game.GameIntent
import com.idlemining.tycoon3d.game.GameState
import com.idlemining.tycoon3d.game.scene.NodeRegistry.Kind
import com.idlemining.tycoon3d.game.world.KitMaterials
import com.idlemining.tycoon3d.game.world.WorldBuilder
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.collision.HitResult
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberView

/**
 * The 3D viewport: builds the world once from content data, then drives all
 * animation and the camera in `onFrame` (the Compose tree never recomposes
 * during play).
 *
 * **Recomposition contract (performance-critical).** This composable takes
 * [content] (an immutable, `@Stable` [GameContent]) and the live state only as
 * a [State] *holder* — `gameState.value` is read exclusively inside the frame
 * and touch callbacks, never during composition. Reading `.value` during
 * composition would subscribe the entire world sub-tree (~1000 node
 * composables) to the 10 Hz simulation emissions and re-execute it ten times a
 * second on the main thread — that was the single biggest frame-rate killer in
 * the pre-tuning build.
 *
 * Camera — LOCKED 45° ORTHO (Phase 3): the angle and the orthographic
 * projection are authored in `world.json` and never change. The player pans
 * by dragging (one finger) and zooms by pinching, both clamped so the mine
 * and depot stay reachable ([OrthoCameraController] on an [OrthoCameraNode]).
 * Passing `cameraManipulator = null` keeps SceneView's gesture layer from
 * ever touching the camera node.
 *
 * Presentation — Phase 2 + the Phase 3.5 mobile performance profile: the
 * `RenderQuality.Default` preset is applied by SceneView, then the zone look
 * ([ScenePresentation]) and finally the render-pipeline tuning
 * ([ScenePerformance]: dynamic resolution, FXAA-only AA, SSAO off, 1024px
 * non-PCSS shadows) layer on top. See those files for the budget math.
 *
 * Input model (touch-first):
 * - Tap on a node    → walk there and mine it
 * - Tap on the depot → walk there and sell
 * - Tap on ground    → walk there
 * - Drag (1 finger)  → pan the camera
 * - Pinch (2 fingers)→ zoom the camera
 */
@Composable
fun GameScene(
    content: GameContent,
    gameState: State<GameState>,
    dispatch: (GameIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val context = LocalContext.current
    val visuals = content.world.visuals
    val cameraConfig = content.world.camera

    val mats = remember(materialLoader, content) { KitMaterials(materialLoader, content) }
    val nodeCount = content.world.nodes.size
    val refs = remember(content) { SceneRefs(nodeCount) }
    val registry = remember(content) { NodeRegistry() }
    val animator = remember(refs, mats, content) {
        SceneAnimator(refs, mats, nodeCount, content)
    }
    val cameraController = remember(cameraConfig) { OrthoCameraController(cameraConfig) }
    val dispatchLatest = rememberUpdatedState(dispatch)
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 24.dp.toPx() }

    // Plain holders — no Compose state writes inside frame or touch callbacks.
    val touch = remember { TouchTracker() }
    val frameClock = remember { longArrayOf(0L) }

    // ── Locked ortho camera: the node overrides updateProjection so even
    //    SceneView's own resize callback keeps the orthographic frustum.
    val cameraNode: OrthoCameraNode = rememberSceneNode { OrthoCameraNode(engine) }

    // ── Presentation: sun, fill, environment (lights built once per content).
    //    Local non-inline remember: SceneView's `rememberNode` is an inline
    //    function compiled at JVM target 21, which cannot be inlined into this
    //    JVM-17 module — so we wrap the same create/destroy lifecycle ourselves.
    val sun = rememberSceneNode { ScenePresentation.buildSun(engine, visuals.sun) }
    val fill = rememberSceneNode { ScenePresentation.buildFill(engine, visuals.fill) }
    val environment = rememberEnvironment(engine, key = content.world.zone) {
        ScenePresentation.buildEnvironment(engine, context, visuals)
    }
    val view = rememberView(engine)

    SceneView(
        modifier = modifier,
        engine = engine,
        materialLoader = materialLoader,
        view = view,
        // Authored world coordinates are final — do not re-center the content.
        autoCenterContent = false,
        // Mobile-tuned base preset: FXAA, SSAO default-on, HDR MEDIUM, no MSAA.
        // ScenePerformance (below) then applies the authored performance block
        // on top — dynamic resolution, SSAO off, cheap post-processing.
        renderQuality = RenderQuality.Default,
        mainLightNode = sun,
        fillLightNode = fill,
        environment = environment,
        cameraNode = cameraNode,
        // LOCKED ORTHO CAMERA — no gesture manipulator; pan/zoom are handled
        // in our own touch handler and applied through the controller.
        cameraManipulator = null,
        onTouchEvent = { event, hitResult ->
            handleTouch(
                event, hitResult, touch, touchSlopPx,
                cameraNode, cameraController, registry, dispatchLatest.value,
            )
            // Never consume — node touches must keep working (the camera is
            // driven by our own controller, not by SceneView gestures).
            false
        },
        onFrame = { frameTimeNanos ->
            if (frameClock[0] == 0L) frameClock[0] = frameTimeNanos
            val dt = ((frameTimeNanos - frameClock[0]) / 1_000_000_000.0)
                .coerceIn(0.0, 0.1).toFloat()
            frameClock[0] = frameTimeNanos

            cameraController.update(cameraNode)
            animator.advance(gameState.value, frameTimeNanos)
        },
    ) {
        WorldBuilder(content, refs, registry, mats)
    }

    // ── Post-processing + performance profile — applied AFTER SceneView's
    //    preset effect (LaunchedEffects run in composition order, and ours is
    //    registered later), so neither the preset nor the zone look can
    //    clobber the final tuning. Runs once per content — never per frame.
    LaunchedEffect(view, content.world.zone) {
        ScenePresentation.applyPostFx(view, engine, visuals)
        ScenePerformance.apply(view, content.world.performance)
    }
}

/**
 * Non-inline twin of SceneView's `rememberNode`: creates the node once,
 * destroys it when the composition leaves. Inline library helpers built at
 * JVM target 21 cannot be inlined into this JVM-17 module, so the lifecycle
 * is wrapped locally instead.
 */
@Composable
private fun <T : Node> rememberSceneNode(create: () -> T): T {
    val node = remember { create() }
    DisposableEffect(node) {
        onDispose { node.destroy() }
    }
    return node
}

/** Tracks the gesture state machine across touch events. */
private class TouchTracker {
    // Tap candidate.
    var downX = 0f
    var downY = 0f
    var downTimeMs = 0L
    var tracking = false

    // Pan (single finger).
    var panning = false
    var lastX = 0f
    var lastY = 0f

    // Pinch (two fingers).
    var pinching = false
    var pinchLastDist = 0f

    fun reset() {
        tracking = false
        panning = false
        pinching = false
    }
}

private fun handleTouch(
    event: MotionEvent,
    hitResult: HitResult?,
    touch: TouchTracker,
    slopPx: Float,
    cameraNode: OrthoCameraNode,
    camera: OrthoCameraController,
    registry: NodeRegistry,
    dispatch: (GameIntent) -> Unit,
) {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            touch.downX = event.x
            touch.downY = event.y
            touch.downTimeMs = System.currentTimeMillis()
            touch.tracking = true
            touch.panning = false
            touch.pinching = false
            touch.lastX = event.x
            touch.lastY = event.y
        }

        MotionEvent.ACTION_POINTER_DOWN -> {
            if (event.pointerCount == 2) {
                // A second finger ends any tap/pan candidate and starts a pinch.
                touch.reset()
                touch.pinching = true
                touch.pinchLastDist = pinchDistance(event)
            }
        }

        MotionEvent.ACTION_MOVE -> {
            if (touch.pinching && event.pointerCount >= 2) {
                val dist = pinchDistance(event)
                if (dist > 1f && touch.pinchLastDist > 1f) {
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    camera.zoom(cameraNode, dist / touch.pinchLastDist, midX, midY)
                }
                touch.pinchLastDist = dist
            } else if (!touch.pinching) {
                val dx = event.x - touch.lastX
                val dy = event.y - touch.lastY
                touch.lastX = event.x
                touch.lastY = event.y
                if (touch.tracking) {
                    val totalDx = event.x - touch.downX
                    val totalDy = event.y - touch.downY
                    if (totalDx * totalDx + totalDy * totalDy > slopPx * slopPx) {
                        // The finger drifted — this is a pan, not a tap.
                        touch.tracking = false
                        touch.panning = true
                    }
                }
                if (touch.panning) camera.pan(cameraNode, dx, dy)
            }
        }

        MotionEvent.ACTION_POINTER_UP -> {
            // A finger lifted from a pinch: end the gesture entirely (a fresh
            // DOWN starts a new pan or tap).
            touch.reset()
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            val wasTracking = touch.tracking
            touch.reset()
            if (!wasTracking) return
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) return
            val dx = event.x - touch.downX
            val dy = event.y - touch.downY
            val held = System.currentTimeMillis() - touch.downTimeMs
            val moved = dx * dx + dy * dy > slopPx * slopPx
            if (moved || held > 400L) return

            // It's a tap — resolve what was hit.
            val hit = registry.resolve(hitResult?.node)
            val world = hitResult?.getWorldPosition()
            when (hit.kind) {
                Kind.NODE -> dispatch(GameIntent.TapNode(hit.nodeIndex))
                Kind.DEPOT -> dispatch(GameIntent.TapDepot)
                Kind.GROUND, Kind.PROP -> if (world != null) {
                    dispatch(GameIntent.TapGround(world.x, world.z))
                }
                Kind.WORKER, Kind.NONE -> Unit
            }
        }
    }
}

private fun pinchDistance(event: MotionEvent): Float {
    val dx = event.getX(0) - event.getX(1)
    val dy = event.getY(0) - event.getY(1)
    return kotlin.math.hypot(dx, dy)
}
