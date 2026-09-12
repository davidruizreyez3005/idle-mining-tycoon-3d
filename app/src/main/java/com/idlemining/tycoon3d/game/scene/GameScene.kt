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
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberView
import kotlin.math.tan

/**
 * The 3D viewport: builds the world once from content data, then drives all
 * animation and the camera in `onFrame` (the Compose tree never recomposes
 * during play).
 *
 * Camera — FIXED (Phase 2): the angle, distance and fov are authored in
 * `world.json` and never change; there are no orbit/pan/zoom gestures. The
 * framing target softly tracks the worker inside a clamped window
 * ([FixedCameraController]). Passing `cameraManipulator = null` keeps
 * SceneView's gesture layer from ever touching the camera node.
 *
 * Presentation — (Phase 2): warm sun + cool sky fill (both data-driven, the
 * sun casting soft 2048px shadows), neutral IBL under a sky-colored skybox,
 * distance fog with sun in-scattering, SSAO, bloom, vignette and an ACES color
 * grade ([ScenePresentation]).
 *
 * Input model (touch-first):
 * - Tap on a node    → walk there and mine it
 * - Tap on the depot → walk there and sell
 * - Tap on ground    → walk there
 */
@Composable
fun GameScene(
    gameState: State<GameState>,
    dispatch: (GameIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val context = LocalContext.current
    val content: GameContent = gameState.value.content
    val visuals = content.world.visuals
    val cameraConfig = content.world.camera

    val mats = remember(materialLoader, content) { KitMaterials(materialLoader, content) }
    val nodeCount = content.world.nodes.size
    val refs = remember(content) { SceneRefs(nodeCount) }
    val registry = remember(content) { NodeRegistry() }
    val animator = remember(refs, mats) { SceneAnimator(refs, mats, nodeCount) }
    val cameraController = remember(cameraConfig) { FixedCameraController(cameraConfig) }
    val dispatchLatest = rememberUpdatedState(dispatch)
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 24.dp.toPx() }

    // Plain holders — no Compose state writes inside frame or touch callbacks.
    val touch = remember { TouchTracker() }
    val frameClock = remember { longArrayOf(0L) }

    // ── Fixed camera: fov authored in data, converted to the 35mm-equivalent
    //    focal length SceneView's updateProjection() keeps using on resizes.
    val cameraNode: CameraNode = rememberCameraNode(engine) {
        isSmoothTransformEnabled = false
        focalLength = 12.0 / tan(Math.toRadians(cameraConfig.fov.toDouble()) / 2.0)
    }

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
        // Full fidelity: MSAA 4x + FXAA, SSAO high, HDR high, shadows on.
        renderQuality = RenderQuality.Cinematic,
        mainLightNode = sun,
        fillLightNode = fill,
        environment = environment,
        cameraNode = cameraNode,
        // FIXED CAMERA — no gesture control; we own the transform every frame.
        cameraManipulator = null,
        onTouchEvent = { event, hitResult ->
            handleTouch(event, hitResult, touch, touchSlopPx, registry, dispatchLatest.value)
            // Never consume — node touches must keep working (no camera gestures
            // left to protect).
            false
        },
        onFrame = { frameTimeNanos ->
            if (frameClock[0] == 0L) frameClock[0] = frameTimeNanos
            val dt = ((frameTimeNanos - frameClock[0]) / 1_000_000_000.0)
                .coerceIn(0.0, 0.1).toFloat()
            frameClock[0] = frameTimeNanos

            val state = gameState.value
            cameraController.update(cameraNode, state.worker.x, state.worker.z, dt)
            animator.advance(state, frameTimeNanos)
        },
    ) {
        WorldBuilder(content, refs, registry, mats)
    }

    // ── Post-processing — applied AFTER SceneView's Cinematic preset effect
    //    (LaunchedEffects run in composition order, and ours is registered
    //    later), so the preset cannot clobber the zone look.
    LaunchedEffect(view, content.world.zone) {
        ScenePresentation.applyPostFx(view, engine, visuals)
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

/** Tracks a potential tap between ACTION_DOWN and ACTION_UP. */
private class TouchTracker {
    var downX = 0f
    var downY = 0f
    var downTimeMs = 0L
    var tracking = false
}

private fun handleTouch(
    event: MotionEvent,
    hitResult: HitResult?,
    touch: TouchTracker,
    slopPx: Float,
    registry: NodeRegistry,
    dispatch: (GameIntent) -> Unit,
) {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            touch.downX = event.x
            touch.downY = event.y
            touch.downTimeMs = System.currentTimeMillis()
            touch.tracking = true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (!touch.tracking) return
            touch.tracking = false
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
