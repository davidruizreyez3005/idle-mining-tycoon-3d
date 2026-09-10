package com.idleshaft.tycoon.domain

/**
 * The four ore tiers of the mine, unlocked as the player digs deeper.
 *
 * [rawValuePerUnit] is the base market value of one unit of raw ore once it has been
 * refined into a bar (bar value scales with [rawValuePerUnit], see `Economy.barValue`).
 */
enum class OreType(
    val displayName: String,
    val rawValuePerUnit: Double,
    /** ARGB color used for 3D ore vein cubes and UI chips. */
    val argb: Long,
    /** ARGB color of the refined bar. */
    val barArgb: Long,
) {
    COPPER("Copper", 1.0, 0xFFC57A46, 0xFFE0915B),
    IRON("Iron", 4.0, 0xFF9EA7AE, 0xFFC4CBD2),
    GOLD("Gold", 16.0, 0xFFE2B33C, 0xFFF6C945),
    DIAMOND("Diamond", 64.0, 0xFF6FDCFF, 0xFFA9EDFF);

    companion object {
        /** Ordered list, shallowest/cheapest first. */
        val ordered: List<OreType> = entries.toList()
    }
}
