package com.idleshaft.tycoon.game.threed

import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.idleshaft.tycoon.domain.OreType
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.material.setColor

/**
 * Toon-shaded palette.
 *
 * Every prop in the world is rendered with the matc-compiled cel shader
 * (`src/main/materials/toon.filamat` — unlit + 3-band light ramp + rim
 * darkening), so the whole scene reads as flat, clean low-poly toon:
 * no specular noise, no photometric gradients, no shadow-map acne.
 *
 * One [MaterialInstance] per palette colour, cached exactly like the old
 * lit [ColorMaterials] so [MineAnimator] can hot-swap instance colours the
 * same way (bars / truck cargo recolouring).
 */
class ToonMaterials(private val loader: MaterialLoader) {

    private val toonMaterial: Material by lazy {
        loader.createMaterial(TOON_MATERIAL_ASSET)
    }

    private val shadowMaterial: Material by lazy {
        loader.createMaterial(SHADOW_MATERIAL_ASSET)
    }

    private val cache = HashMap<Int, MaterialInstance>()

    /** Opaque toon instance for an ARGB palette colour. */
    fun of(argb: Long): MaterialInstance = cache.getOrPut(argb.toInt()) {
        toonMaterial.createInstance().also { it.setColor("baseColor", argb.toInt()) }
    }

    fun ore(type: OreType): MaterialInstance = of(type.argb)
    fun bar(type: OreType): MaterialInstance = of(type.barArgb)

    /**
     * Shared translucent blob-shadow instance (fixed dark tint, no per-colour
     * state — classic toon "contact shadow" under dynamic props).
     */
    val blobShadow: MaterialInstance by lazy {
        shadowMaterial.createInstance()
    }

    private companion object {
        const val TOON_MATERIAL_ASSET = "materials/toon.filamat"
        const val SHADOW_MATERIAL_ASSET = "materials/toon_shadow.filamat"
    }
}
