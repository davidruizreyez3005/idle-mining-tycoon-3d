package com.idlemining.tycoon3d

import com.idlemining.tycoon3d.core.save.SaveData
import com.idlemining.tycoon3d.core.save.SaveSchema
import com.idlemining.tycoon3d.core.save.SaveJson
import com.idlemining.tycoon3d.core.save.decodeSave
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveMigrationTest {

    @Test
    fun `v0 save with string money migrates to v1`() {
        val v0 = buildJsonObject {
            put("createdAtMs", 1000L)
            put("savedAtMs", 2000L)
            put("money", "$125")
            put("inventory", buildJsonObject { put("stone", 3) })
        }
        val raw = SaveJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), v0)
        val migrated = decodeSave(raw)

        assertEquals(SaveSchema.VERSION, migrated.version)
        assertEquals(125.0, migrated.money, 1e-9)
        assertEquals(3, migrated.inventory["stone"])
    }

    @Test
    fun `roundtrip preserves state`() {
        val save = SaveData(
            version = 1,
            createdAtMs = 1000L,
            savedAtMs = 2000L,
            money = 321.5,
            inventory = mapOf("stone" to 4, "gold" to 1),
            upgrades = mapOf("pickaxe" to 2),
            nodes = listOf(
                com.idlemining.tycoon3d.core.save.NodeSave(hp = 50f),
                com.idlemining.tycoon3d.core.save.NodeSave(hp = 0f, respawnRemainingSec = 12f),
            ),
            offlineUnseen = true,
        )
        val raw = SaveJson.encodeToString(SaveData.serializer(), save)
        val back = decodeSave(raw)

        assertEquals(save, back)
    }

    @Test
    fun `unknown fields are tolerated for forward compatibility`() {
        val raw = """
            { "version": 1, "createdAtMs": 1, "savedAtMs": 2, "money": 10,
              "futureField": { "nested": true } }
        """.trimIndent()
        val decoded = decodeSave(raw)
        assertEquals(10.0, decoded.money, 1e-9)
    }

    @Test
    fun `newer save version is rejected`() {
        val raw = """
            { "version": 99, "createdAtMs": 1, "savedAtMs": 2, "money": 10 }
        """.trimIndent()
        try {
            decodeSave(raw)
            org.junit.Assert.fail("expected SaveFormatException")
        } catch (e: com.idlemining.tycoon3d.core.save.SaveFormatException) {
            assertTrue(e.message!!.contains("newer"))
        }
    }
}
