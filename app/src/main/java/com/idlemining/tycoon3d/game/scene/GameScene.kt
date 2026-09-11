package com.idlemining.tycoon3d.game.scene

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.idlemining.tycoon3d.core.content.GameContent
import com.idlemining.tycoon3d.game.GameIntent
import com.idlemining.tycoon3d.game.GameState
import com.idlemining.tycoon3d.game.scene.NodeRegistry.Kind
import com.idlemining.tycoon3d.game.world.KitMaterials
import com.idlemining.tycoon3d.game.world.WorldBuilder
import io.github.sceneview.SceneView
import io.github.sceneview.collision.HitResult
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader

/**
 * The 3D viewport: builds the world once from content data, then drives all
 * animation in `onFrame` (the Compose tree never recomposes during play).
 *
 * Input model (touch-first):
 * - One-finger drag  → orbit camera (SceneView CameraManipulator)
 * - Pinch            → zoom
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
    val content: GameContent = gameState.value.content
    val mats = remember(materialLoader, content) { KitMaterials(materialLoader, content) }
    val nodeCount = content.world.nodes.size
    val refs = remember(content) { SceneRefs(nodeCount) }
    val registry = remember(content) { NodeRegistry() }
    val animator = remember(refs, mats) { SceneAnimator(refs, mats, nodeCount) }
    val camera = content.world.camera
    val dispatchLatest = rememberUpdatedState(dispatch)
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 24.dp.toPx() }

    // Plain holder — no Compose state writes inside the touch callback.
    val touch = remember { TouchTracker() }

    SceneView(
        modifier = modifier,
        engine = engine,
        materialLoader = materialLoader,
        // Authored world coordinates are final — do not re-center the content.
        autoCenterContent = false,
        cameraManipulator = rememberCameraManipulator(
            orbitRadius = camera.orbitRadius,
            targetPosition = Position(camera.target[0], camera.target[1], camera.target[2]),
        ),
        onTouchEvent = { event, hitResult ->
            handleTouch(event, hitResult, touch, touchSlopPx, registry, dispatchLatest.value)
            // Never consume — camera gestures and node touches must keep working.
            false
        },
        onFrame = { frameTimeNanos ->
            animator.advance(gameState.value, frameTimeNanos)
        },
    ) {
        WorldBuilder(content, refs, registry, mats)
    }
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
