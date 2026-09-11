package com.idleshaft.tycoon.game.threed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.filament.ColorGrading
import com.google.android.filament.Skybox
import com.idleshaft.tycoon.domain.GameState
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.createEnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberView

/**
 * The 3D viewport of the mining operation — rendered in a clean low-poly
 * **toon** style.
 *
 * - **Toon shading**: every prop uses [ToonMaterials] (matc-compiled cel
 *   shader: unlit base colour × 3-band light ramp + rim darkening), so the
 *   scene has crisp flat bands and an inked-silhouette feel instead of PBR
 *   gradients.
 * - **Sky**: a single flat pastel skybox colour — no IBL, no HDR gradients
 *   (nothing in the scene is lit).
 * - **Anti-aliasing**: `RenderQuality.Cinematic` gives MSAA 4× + FXAA with
 *   dynamic resolution off (fixed resolution = no shimmer). Bloom, SSAO and
 *   the filmic tone curve are then disabled so colours stay exactly as
 *   authored (LINEAR tone mapping) — see the `LaunchedEffect` below.
 * - **Camera**: gesture orbit/pan/zoom (SceneView built-in) with a depth
 *   range tightened to the world size (near 0.1 / far 160) — maximum depth
 *   precision, no far-plane clipping, no z-fighting shimmer.
 * - **World**: fully procedural low-poly geometry (no glTF assets) declared
 *   once; per-frame animation happens in [MineAnimator] via `onFrame`, so the
 *   Compose tree never recomposes while the game renders.
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
    val view = rememberView(engine)
    val world = remember(engine) { MineWorld() }
    val materials = remember(materialLoader) { ToonMaterials(materialLoader) }
    val animator = remember(world, materials) { MineAnimator(world, materials) }

    val cameraNode = rememberCameraNode(engine) {
        // Depth range matched to the world (~40 m across): maximum precision.
        near = 0.1f
        far = 160f
    }

    // Flat pastel toon sky (linear-space colour). No indirect light needed —
    // every material in the world is unlit.
    val environment = rememberEnvironment(engine, isOpaque = true) {
        createEnvironment(
            engine = engine,
            isOpaque = true,
            indirectLight = null,
            skybox = Skybox.Builder()
                .color(floatArrayOf(0.40f, 0.66f, 0.85f, 1.0f))
                .build(engine),
        )
    }

    SceneView(
        modifier = modifier,
        engine = engine,
        materialLoader = materialLoader,
        view = view,
        // MSAA 4× + FXAA + fixed resolution (the "smooth" half of the look).
        renderQuality = RenderQuality.Cinematic,
        // Authored world coordinates are final — do not re-center the content.
        autoCenterContent = false,
        cameraNode = cameraNode,
        // 3/4 overview (~39° elevation) from the market side: the 28 mm default
        // lens keeps a 65° horizontal FOV, so all four shafts fit even in
        // portrait; every open-top, open-front pit is fully readable.
        cameraManipulator = rememberCameraManipulator(
            orbitHomePosition = Position(0f, 15.5f, 18.5f),
            targetPosition = Position(0f, -0.8f, -2f),
        ),
        // The whole world is unlit toon — no scene lights, no shadow passes.
        mainLightNode = null,
        fillLightNode = null,
        environment = environment,
        onFrame = { frameTimeNanos ->
            animator.advance(gameState.value, frameTimeNanos)
        },
    ) {
        MineSceneContent(world)
    }

    // Toon post-processing: keep MSAA/FXAA from the Cinematic preset, but drop
    // bloom, SSAO and the filmic curve so flat colours stay flat and exact
    // (LINEAR tone mapping maps the unlit palette 1:1 to the display).
    // Declared after the SceneView call so it runs after the preset is applied.
    LaunchedEffect(view) {
        view.bloomOptions = view.bloomOptions.apply { enabled = false }
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply { enabled = false }
        view.colorGrading = ColorGrading.Builder()
            .toneMapping(ColorGrading.ToneMapping.LINEAR)
            .build(engine)
    }
}
