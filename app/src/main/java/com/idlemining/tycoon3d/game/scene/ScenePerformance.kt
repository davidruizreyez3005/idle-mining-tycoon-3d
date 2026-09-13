package com.idlemining.tycoon3d.game.scene

import com.google.android.filament.View
import com.idlemining.tycoon3d.core.content.PerformanceDef

/**
 * Phase 3.5 — the mobile performance profile.
 *
 * The game previously rendered with `RenderQuality.Cinematic` (MSAA 4x + FXAA,
 * SSAO at HIGH with bilateral upsampling, HDR HIGH, dynamic resolution OFF,
 * 2048px shadow map with PCSS soft shadows). On mid-range phones that preset
 * alone could eat the entire 16 ms frame budget, which is why the game felt
 * slow and laggy while the simulation itself only ticks at 10 Hz.
 *
 * [apply] runs *after* the SceneView preset (see the ordering note in
 * [GameScene] — later writes win, exactly like [ScenePresentation.applyPostFx])
 * and re-tunes the same [View] from the `performance` block of `world.json`:
 *
 *  - **Dynamic resolution ON** — Filament measures frame time and rescales the
 *    render target within [PerformanceDef.dynamicResolution]'s window. This is
 *    the safety net: whatever device the APK lands on, the renderer degrades
 *    resolution instead of frame rate.
 *  - **MSAA off, FXAA only** — one fullscreen 4-sample resolve pass removed.
 *  - **SSAO off** — the sun + 1024px shadow map already ground every object in
 *    this stylized kit; SSAO HIGH cost roughly a millisecond for a subtle gain.
 *  - **HDR MEDIUM / bloom LOW** — half the post-processing bandwidth, visually
 *    identical for this palette.
 *  - **PCSS penumbra off** — shadow sampling becomes hard taps.
 *
 * Everything is authored data, so a future zone (or a "Quality" settings
 * screen) can flip any tier without touching code.
 */
object ScenePerformance {

    /** Applies the tuned profile. Idempotent; safe to call repeatedly. */
    fun apply(view: View, perf: PerformanceDef) {
        // HDR color buffer tier.
        view.renderQuality = view.renderQuality.apply {
            hdrColorBuffer = tier(perf.hdrQuality)
        }

        // Dynamic resolution — the big one. Homogeneous scaling keeps the
        // aspect ratio exact (no non-uniform blur) while the scale floats.
        view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
            enabled = perf.dynamicResolution.enabled
            homogeneousScaling = true
            minScale = perf.dynamicResolution.minScale
            maxScale = perf.dynamicResolution.maxScale
            quality = tier(perf.dynamicResolution.quality)
        }

        // Anti-aliasing: FXAA alone when msaaSampleCount <= 1.
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply {
            enabled = perf.msaaSampleCount > 1
            sampleCount = if (perf.msaaSampleCount > 1) perf.msaaSampleCount else 1
        }

        // SSAO — only paid for when a zone asks for it, and then at the cheap tier.
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply {
            enabled = perf.ssao
            if (perf.ssao) {
                quality = View.QualityLevel.LOW
                upsampling = View.QualityLevel.LOW
                lowPassFilter = View.QualityLevel.LOW
            }
        }

        // Bloom pyramid tier (enabled/strength stay owned by visuals.bloom).
        view.bloomOptions = view.bloomOptions.apply {
            quality = tier(perf.bloomQuality)
        }

        // PCSS soft shadows — off means plain taps in the shadow sampler.
        view.softShadowOptions = view.softShadowOptions.apply {
            penumbraScale = if (perf.softShadows) 0.35f else 0.0f
        }
    }

    /** Authored tier name -> Filament enum (validation already ran at load). */
    private fun tier(name: String): View.QualityLevel = when (name) {
        "low" -> View.QualityLevel.LOW
        "high" -> View.QualityLevel.HIGH
        else -> View.QualityLevel.MEDIUM
    }
}
