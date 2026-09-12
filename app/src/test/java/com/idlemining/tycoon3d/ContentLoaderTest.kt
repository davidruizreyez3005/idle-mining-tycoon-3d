package com.idlemining.tycoon3d

import com.idlemining.tycoon3d.core.content.ContentException
import com.idlemining.tycoon3d.core.content.ContentLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ContentLoaderTest {

    @Test
    fun `parses the full bundle and resolves references`() {
        val content = TestContent.build()
        assertEquals(3, content.resources.size)
        assertEquals(2, content.nodeTypes.size)
        assertEquals(5, content.upgrades.size)
        assertEquals(2, content.world.nodes.size)
        assertNotNull(content.resource("stone"))
        assertNotNull(content.nodeType("gold_vein"))
        assertNotNull(content.upgrade("extractor"))
        assertEquals("Test Quarry", content.world.name)
    }

    @Test
    fun `hex colors parse to opaque ARGB`() {
        assertEquals(0xFFB0BEC5L, ContentLoader.parseColor("#B0BEC5", "test"))
        assertEquals(0xFF000000L, ContentLoader.parseColor("#000000", "test"))
    }

    @Test
    fun `invalid hex color is rejected`() {
        try {
            ContentLoader.parseColor("#GGHHII", "test")
            fail("expected ContentException")
        } catch (e: ContentException) {
            assertTrue(e.message!!.contains("Invalid color"))
        }
    }

    @Test
    fun `node referencing an unknown type fails validation`() {
        // Full valid bundle with the node types file replaced by an unknown type.
        try {
            ContentLoader.load(TestContent.bundleOverrides(
                ContentLoader.FILE_NODES to """
                    { "version": 1, "nodeTypes": [
                        { "id": "mystery_vein", "name": "M", "hp": 10, "respawnSeconds": 5,
                          "yields": { "stone": 2 }, "primaryResource": "stone" }
                    ] }
                """.trimIndent(),
            ))
            fail("expected ContentException for unknown typeId")
        } catch (e: ContentException) {
            // world.json still references stone_vein, which no longer exists.
            assertTrue(e.message!!.contains("typeId"))
        }
    }

    @Test
    fun `duplicate resource ids are rejected`() {
        try {
            ContentLoader.load(
                mapOf(
                    ContentLoader.FILE_RESOURCES to """
                        { "version": 1, "resources": [
                            { "id": "stone", "name": "A", "baseValue": 1, "color": "#111111" },
                            { "id": "stone", "name": "B", "baseValue": 2, "color": "#222222" }
                        ] }
                    """.trimIndent(),
                )
            )
            fail("expected duplicate id failure")
        } catch (e: ContentException) {
            assertTrue(e.message!!.contains("duplicate"))
        }
    }

    @Test
    fun `cost growth must exceed one`() {
        try {
            ContentLoader.load(TestContent.bundleOverrides(
                ContentLoader.FILE_UPGRADES to """
                    { "version": 1, "upgrades": [
                        { "id": "u", "name": "U", "desc": "d",
                          "effect": { "type": "miningSpeed", "perLevel": 0.1 },
                          "baseCost": 10, "costGrowth": 1.0, "maxLevel": 5 }
                    ] }
                """.trimIndent(),
            ))
            fail("expected costGrowth failure")
        } catch (e: ContentException) {
            assertTrue(e.message!!.contains("costGrowth"))
        }
    }

    @Test
    fun `missing file is reported clearly`() {
        try {
            ContentLoader.load(emptyMap())
            fail("expected missing file failure")
        } catch (e: ContentException) {
            assertTrue(e.message!!.contains("resources.json"))
        }
    }

    // ------------------------------------------------------------- phase 2: camera + visuals

    @Test
    fun `camera and visuals parse from world data`() {
        val content = TestContent.build()
        val camera = content.world.camera
        assertEquals(31f, camera.yaw, 0.001f)
        assertEquals(33f, camera.pitch, 0.001f)
        assertEquals(21.5f, camera.distance, 0.001f)
        assertEquals(42f, camera.fov, 0.001f)
        assertEquals(3, camera.target.size)
        assertEquals(0.18f, camera.followStrength, 0.001f)
        assertEquals(3.2f, camera.followDeadzone, 0.001f)

        val visuals = content.world.visuals
        assertEquals("aces", visuals.toneMapping)
        assertEquals(26_000f, visuals.sun.intensity, 0.01f)
        assertEquals(3, visuals.sun.direction.size)
        assertEquals(2048, visuals.sun.shadowMapSize)
        assertTrue(visuals.fog.enabled)
        assertEquals(0.012f, visuals.fog.density, 0.0001f)
        assertEquals(0.2f, visuals.bloom.strength, 0.001f)
    }

    @Test
    fun `camera defaults apply when block is absent`() {
        // bundleOverrides' world file has no camera/visuals blocks at all.
        val content = ContentLoader.load(TestContent.bundleOverrides())
        val camera = content.world.camera
        assertEquals(30f, camera.yaw, 0.001f)
        assertEquals(0.16f, camera.followStrength, 0.001f)
        assertEquals("aces", content.world.visuals.toneMapping)
        assertTrue(content.world.visuals.fog.enabled)
    }

    @Test
    fun `bad sun direction is rejected`() {
        try {
            ContentLoader.load(TestContent.bundleOverrides(
                ContentLoader.FILE_WORLD to """
                    { "version": 1, "zone": "z", "name": "Z",
                      "nodes": [ { "typeId": "stone_vein", "at": [1, 6] } ],
                      "visuals": { "sun": { "direction": [0, 0, 0] } } }
                """.trimIndent(),
            ))
            fail("expected zero direction failure")
        } catch (e: ContentException) {
            assertTrue(e.message!!.contains("visuals.sun.direction"))
        }
    }

    @Test
    fun `unknown tone mapping is rejected`() {
        try {
            ContentLoader.load(TestContent.bundleOverrides(
                ContentLoader.FILE_WORLD to """
                    { "version": 1, "zone": "z", "name": "Z",
                      "nodes": [ { "typeId": "stone_vein", "at": [1, 6] } ],
                      "visuals": { "toneMapping": "technicolor" } }
                """.trimIndent(),
            ))
            fail("expected toneMapping failure")
        } catch (e: ContentException) {
            assertTrue(e.message!!.contains("toneMapping"))
        }
    }

    @Test
    fun `invalid camera fov is rejected`() {
        try {
            ContentLoader.load(TestContent.bundleOverrides(
                ContentLoader.FILE_WORLD to """
                    { "version": 1, "zone": "z", "name": "Z",
                      "nodes": [ { "typeId": "stone_vein", "at": [1, 6] } ],
                      "camera": { "fov": 0 } }
                """.trimIndent(),
            ))
            fail("expected fov failure")
        } catch (e: ContentException) {
            assertTrue(e.message!!.contains("camera.fov"))
        }
    }

    @Test
    fun `non power of two shadow map is rejected`() {
        try {
            ContentLoader.load(TestContent.bundleOverrides(
                ContentLoader.FILE_WORLD to """
                    { "version": 1, "zone": "z", "name": "Z",
                      "nodes": [ { "typeId": "stone_vein", "at": [1, 6] } ],
                      "visuals": { "sun": { "shadowMapSize": 1000 } } }
                """.trimIndent(),
            ))
            fail("expected shadowMapSize failure")
        } catch (e: ContentException) {
            assertTrue(e.message!!.contains("shadowMapSize"))
        }
    }

}
