package com.idlemining.tycoon3d.game.scene

import android.content.Context
import com.google.android.filament.ColorGrading
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Skybox
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.utils.KTX1Loader
import com.idlemining.tycoon3d.core.content.ContentLoader
import com.idlemining.tycoon3d.core.content.SunDef
import com.idlemining.tycoon3d.core.content.FillDef
import com.idlemining.tycoon3d.core.content.VisualsDef
import io.github.sceneview.environment.Environment
import io.github.sceneview.node.LightNode
import io.github.sceneview.utils.readBuffer
import kotlin.math.sqrt

/**
 * Phase 2 presentation pipeline — turns the `visuals` block of `world.json`
 * into Filament lights, sky, fog and post-processing.
 *
 * Everything here is authored data, no magic constants at call sites: the sun's
 * warm afternoon tint, the cool sky fill, the horizon haze, the vignette and the
 * ACES grade all come from [VisualsDef] so each zone can carry its own mood.
 *
 * Order of application matters:
 *  1. `SceneView` creates the [View] and applies the `RenderQuality.Default`
 *     preset via its own `LaunchedEffect`.
 *  2. [applyPostFx] then runs *after* that effect (its own LaunchedEffect is
 *     registered later in composition) and layers on the zone-specific fog,
 *     vignette, bloom strength and color grading — the preset contract
 *     explicitly preserves tweaks applied after it.
 *  3. [ScenePerformance] runs last of all and re-tunes the render pipeline for
 *     the mobile performance profile (dynamic resolution, MSAA/SSAO tiers).
 */
object ScenePresentation {

    // ------------------------------------------------------------------ lights

    /**
     * Builds the main sun: a shadow-casting directional light with the zone's
     * warm tint, intensity, direction and shadow-map resolution.
     */
    fun buildSun(engine: Engine, sun: SunDef): LightNode {
        val (r, g, b) = hexToLinear(sun.color, "visuals.sun.color")
        val (dx, dy, dz) = normalized(sun.direction)
        return LightNode(
            engine = engine,
            type = LightManager.Type.DIRECTIONAL,
            apply = {
                color(r, g, b)
                intensity(sun.intensity)
                direction(dx, dy, dz)
                castShadows(true)
                sunAngularRadius(sun.angularRadius)
                sunHaloSize(10f)
                sunHaloFalloff(80f)
                shadowOptions(LightManager.ShadowOptions().apply {
                    mapSize = sun.shadowMapSize
                    // Keep the shadow frustum tight around the play area for
                    // crisp contact shadows.
                    shadowFar = 80f
                    normalBias = 0.02f
                })
            },
        )
    }

    /**
     * Builds the sky fill: a cool, shadow-less directional light from the
     * opposite side that lifts the shadowed faces.
     */
    fun buildFill(engine: Engine, fill: FillDef): LightNode {
        val (r, g, b) = hexToLinear(fill.color, "visuals.fill.color")
        val (dx, dy, dz) = normalized(fill.direction)
        return LightNode(
            engine = engine,
            type = LightManager.Type.DIRECTIONAL,
            apply = {
                color(r, g, b)
                intensity(fill.intensity)
                direction(dx, dy, dz)
                castShadows(false)
            },
        )
    }

    // ------------------------------------------------------- sky and ambient

    /**
     * Builds the zone environment: the neutral KTX IBL (physically sensible
     * ambient, intensity from data) under a solid sky-colored skybox. Fog
     * handles the horizon gradient.
     */
    fun buildEnvironment(
        engine: Engine,
        context: Context,
        visuals: VisualsDef,
    ): Environment {
        val (skyR, skyG, skyB) = hexToLinear(visuals.sky.color, "visuals.sky.color")
        val skybox = Skybox.Builder()
            .color(floatArrayOf(skyR, skyG, skyB, 1f))
            .build(engine)

        val indirectLight: IndirectLight? = try {
            KTX1Loader.createIndirectLight(
                engine,
                context.assets.readBuffer("environments/neutral/neutral_ibl.ktx"),
            ).indirectLight?.also { it.intensity = visuals.ambient.intensity }
        } catch (e: Exception) {
            // The neutral probe ships inside the SceneView AAR; if unavailable
            // (exotic build variants), the sun + fill still light the scene.
            null
        }

        return Environment(indirectLight = indirectLight, skybox = skybox)
    }

    // ------------------------------------------------------------- post fx

    /**
     * Applies the zone look on top of the preset: distance fog, bloom
     * strength, vignette and the color grade.
     * Call after `View.applyRenderQuality` — later writes win. Shadow-map
     * resolution and the whole mobile performance profile (MSAA/SSAO/dynamic
     * resolution/PCSS) are owned by [ScenePerformance], which runs after this.
     */
    fun applyPostFx(view: View, engine: Engine, visuals: VisualsDef) {

        // Distance haze — extinction fog with sun in-scattering. Applied only
        // beyond `distance` from the camera and capped at maximumOpacity so
        // the quarry walls never dissolve completely.
        view.fogOptions = view.fogOptions.apply {
            enabled = visuals.fog.enabled
            density = visuals.fog.density
            distance = visuals.fog.distance
            cutOffDistance = visuals.fog.cutOffDistance
            maximumOpacity = visuals.fog.maximumOpacity
            height = 0f
            heightFalloff = visuals.fog.heightFalloff
            inScatteringStart = visuals.fog.inScatteringStart
            inScatteringSize = visuals.fog.inScatteringSize
            val (r, g, b) = hexToLinear(visuals.fog.color, "visuals.fog.color")
            color[0] = r; color[1] = g; color[2] = b
            fogColorFromIbl = false
        }

        // Bloom — lifts the lamp glows, gold and crystal speculars. The
        // Cinematic preset already enabled it with the library's tuned
        // threshold/highlight defaults (whole-image threshold-free bloom);
        // only the strength is zone data.
        view.bloomOptions = view.bloomOptions.apply {
            enabled = visuals.bloom.enabled
            strength = visuals.bloom.strength
        }

        // Vignette — subtle darkening in the corners to focus the composition.
        view.vignetteOptions = view.vignetteOptions.apply {
            enabled = visuals.vignette.enabled
            midPoint = visuals.vignette.midPoint
            roundness = visuals.vignette.roundness
            feather = visuals.vignette.feather
            // Fixed near-black-blue corner tint — authored look, not zone data.
            color[0] = 0.004f; color[1] = 0.006f; color[2] = 0.012f; color[3] = 1f
        }

        // Color grade — ACES filmic with a touch of exposure, contrast and
        // saturation for a sunnier, punchier quarry.
        val toneMapper = when (visuals.toneMapping) {
            "aces" -> ToneMapper.ACES()
            "filmic" -> ToneMapper.Filmic()
            else -> ToneMapper.Linear()
        }
        view.colorGrading = ColorGrading.Builder()
            .toneMapper(toneMapper)
            .exposure(visuals.exposure)
            .contrast(visuals.contrast)
            .saturation(visuals.saturation)
            .build(engine)
    }

    // -------------------------------------------------------------- helpers

    /** "#RRGGBB" (sRGB) -> linear RGB triple, using Filament's converter. */
    fun hexToLinear(hex: String, what: String): Triple<Float, Float, Float> {
        val argb = ContentLoader.parseColor(hex, what).toInt()
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val linear = Colors.toLinear(Colors.RgbType.SRGB, r, g, b)
        return Triple(linear[0], linear[1], linear[2])
    }

    private fun normalized(dir: List<Float>): Triple<Float, Float, Float> {
        val len = sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2])
        return Triple(dir[0] / len, dir[1] / len, dir[2] / len)
    }
}
