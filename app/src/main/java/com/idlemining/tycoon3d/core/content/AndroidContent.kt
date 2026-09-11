package com.idlemining.tycoon3d.core.content

import android.content.Context

/**
 * Android bridge: reads the gamedata JSON bundle from `assets/gamedata/` and hands
 * it to the pure [ContentLoader]. All validation happens in the loader so the same
 * code path runs in JVM unit tests.
 */
object AndroidContent {

    fun load(context: Context): GameContent {
        val names = listOf(
            ContentLoader.FILE_RESOURCES,
            ContentLoader.FILE_NODES,
            ContentLoader.FILE_UPGRADES,
            ContentLoader.FILE_ECONOMY,
            ContentLoader.FILE_WORLD,
        )
        val files = names.associateWith { name ->
            context.assets.open("gamedata/$name").bufferedReader().use { it.readText() }
        }
        return ContentLoader.load(files)
    }
}
