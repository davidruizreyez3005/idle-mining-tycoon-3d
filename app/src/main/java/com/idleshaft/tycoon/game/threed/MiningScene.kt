package com.idleshaft.tycoon.game.threed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.idleshaft.tycoon.domain.GameState
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader

/**
 * The 3D viewport of the mining operation.
 *
 * - Camera: a gesture-driven orbit/pan/zoom manipulator (SceneView built-in) framed on
 *   the facility — one finger orbits/pans, pinch zooms.
 * - Lighting: SceneView defaults — a directional sun with shadows, a soft fill light
 *   and a neutral IBL environment + skybox.
 * - World: fully procedural low-poly geometry (no glTF assets) declared once; per-frame
 *   animation happens in [MineAnimator] via `onFrame`, so the Compose tree never
 *   recomposes while the game renders.
 *
 * [gameState] is read inside the frame callback only — not during composition.
 */
@Composable
fun MiningScene(
    gameState: State<GameState>,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val world = remember(engine) { MineWorld() }
    val materials = remember(materialLoader) { ColorMaterials(materialLoader) }
    val animator = remember(world, materials) { MineAnimator(world, materials) }

    SceneView(
        modifier = modifier,
        engine = engine,
        materialLoader = materialLoader,
        // Authored world coordinates are final — do not re-center the content.
        autoCenterContent = false,
        // Isometric-style 3/4 view centered on the facility, with gesture controls.
        cameraManipulator = rememberCameraManipulator(
            orbitRadius = 17f,
            targetPosition = Position(0f, 1.0f, -1.5f),
        ),
        onFrame = { frameTimeNanos ->
            animator.advance(gameState.value, frameTimeNanos)
        },
    ) {
        MineSceneContent(world)
    }
}
