package com.idlemining.tycoon3d

import com.idlemining.tycoon3d.core.content.ContentLoader
import com.idlemining.tycoon3d.core.content.EconomyFile
import com.idlemining.tycoon3d.core.content.EffectDef
import com.idlemining.tycoon3d.core.content.GameContent
import com.idlemining.tycoon3d.core.content.GroundDef
import com.idlemining.tycoon3d.core.content.IdleRateDef
import com.idlemining.tycoon3d.core.content.IdleTuning
import com.idlemining.tycoon3d.core.content.MineNodesFile
import com.idlemining.tycoon3d.core.content.NodePlacement
import com.idlemining.tycoon3d.core.content.NodeTypeDef
import com.idlemining.tycoon3d.core.content.ResourceDef
import com.idlemining.tycoon3d.core.content.ResourcesFile
import com.idlemining.tycoon3d.core.content.SellTuning
import com.idlemining.tycoon3d.core.content.StartDef
import com.idlemining.tycoon3d.core.content.UpgradeDef
import com.idlemining.tycoon3d.core.content.UpgradesFile
import com.idlemining.tycoon3d.core.content.WorkerTuning
import com.idlemining.tycoon3d.core.content.WorldFile
import com.idlemining.tycoon3d.core.content.DepotDef
import com.idlemining.tycoon3d.core.content.CliffDef
import com.idlemining.tycoon3d.core.content.CameraDef
import com.idlemining.tycoon3d.core.content.PropPlacement

/**
 * Shared test fixture: a compact but real content bundle — same shapes and
 * rules as the shipped gamedata JSON, only smaller for readability.
 */
object TestContent {

    fun build(): GameContent = ContentLoader.load(
        mapOf(
            ContentLoader.FILE_RESOURCES to """
                { "version": 1, "resources": [
                    { "id": "stone", "name": "Stone", "rarity": "common", "baseValue": 2, "color": "#B0BEC5" },
                    { "id": "coal", "name": "Coal", "rarity": "uncommon", "baseValue": 6, "color": "#37474F" },
                    { "id": "gold", "name": "Gold", "rarity": "rare", "baseValue": 45, "color": "#FFD54F" }
                ] }
            """.trimIndent(),

            ContentLoader.FILE_NODES to """
                { "version": 1, "nodeTypes": [
                    { "id": "stone_vein", "name": "Stone Vein", "hp": 100, "respawnSeconds": 15,
                      "yields": { "stone": 3 }, "primaryResource": "stone", "crystals": 3 },
                    { "id": "gold_vein", "name": "Gold Vein", "mesh": "rock_boulder", "hp": 320,
                      "respawnSeconds": 50, "yields": { "gold": 1, "stone": 2 },
                      "primaryResource": "gold", "crystals": 5 }
                ] }
            """.trimIndent(),

            ContentLoader.FILE_UPGRADES to """
                { "version": 1, "upgrades": [
                    { "id": "pickaxe", "name": "Reinforced Pickaxe", "desc": "d",
                      "effect": { "type": "miningSpeed", "perLevel": 0.35 },
                      "baseCost": 60, "costGrowth": 1.9, "maxLevel": 10 },
                    { "id": "boots", "name": "Speed Boots", "desc": "d",
                      "effect": { "type": "moveSpeed", "perLevel": 0.18 },
                      "baseCost": 45, "costGrowth": 1.75, "maxLevel": 8 },
                    { "id": "backpack", "name": "Big Backpack", "desc": "d",
                      "effect": { "type": "backpack", "perLevel": 6.0 },
                      "baseCost": 50, "costGrowth": 1.7, "maxLevel": 10 },
                    { "id": "market", "name": "Trade Contracts", "desc": "d",
                      "effect": { "type": "sellMargin", "perLevel": 0.15 },
                      "baseCost": 120, "costGrowth": 2.0, "maxLevel": 10 },
                    { "id": "extractor", "name": "Auto-Extractor", "desc": "d",
                      "effect": { "type": "idleExtraction", "rates": [
                        { "resource": "stone", "perLevel": 0.4, "unlockLevel": 1 },
                        { "resource": "coal", "perLevel": 0.15, "unlockLevel": 4 }
                      ] },
                      "baseCost": 150, "costGrowth": 2.1, "maxLevel": 12 }
                ] }
            """.trimIndent(),

            ContentLoader.FILE_ECONOMY to """
                { "version": 1,
                  "start": { "money": 25, "backpack": 12 },
                  "worker": { "moveSpeed": 2.4, "mineDps": 22, "collectRadius": 1.5, "reachRadius": 1.8 },
                  "sell": { "depotRadius": 3.0 },
                  "idle": { "offlineCapHours": 4 } }
            """.trimIndent(),

            ContentLoader.FILE_WORLD to """
                { "version": 1, "zone": "test_quarry", "name": "Test Quarry",
                  "ground": { "size": [46, 46], "grassColor": "#7CB342", "dirtColor": "#A1887F",
                              "rockColor": "#78909C", "pathWidth": 4.2 },
                  "camera": { "orbitRadius": 16.5, "target": [0, 0.8, -4] },
                  "spawn": [0, 8],
                  "cliff": { "z": -19, "halfSpan": 14, "height": 5, "depth": 4,
                             "entranceHalfWidth": 3.4, "wallColor": "#6D838F" },
                  "nodes": [
                    { "typeId": "stone_vein", "at": [1, 6] },
                    { "typeId": "gold_vein", "at": [0, -20] }
                  ],
                  "depot": { "position": [0, 12.5], "facing": 0 },
                  "props": [ { "piece": "tree_pine", "at": [-19, -3] } ] }
            """.trimIndent(),
        )
    )

    /** The gold vein (node index 1) sits deep in the tunnel; node 0 is near spawn. */
    const val STONE_NODE = 0
    const val GOLD_NODE = 1

    /** The full bundle with selected files replaced — for negative tests. */
    fun bundleOverrides(vararg overrides: Pair<String, String>): Map<String, String> {
        val full = mapOf(
            ContentLoader.FILE_RESOURCES to """
                { "version": 1, "resources": [
                    { "id": "stone", "name": "Stone", "baseValue": 2, "color": "#B0BEC5" },
                    { "id": "coal", "name": "Coal", "baseValue": 6, "color": "#37474F" },
                    { "id": "gold", "name": "Gold", "baseValue": 45, "color": "#FFD54F" }
                ] }
            """.trimIndent(),
            ContentLoader.FILE_NODES to """
                { "version": 1, "nodeTypes": [
                    { "id": "stone_vein", "name": "Stone Vein", "hp": 100, "respawnSeconds": 15,
                      "yields": { "stone": 3 }, "primaryResource": "stone", "crystals": 3 }
                ] }
            """.trimIndent(),
            ContentLoader.FILE_UPGRADES to """
                { "version": 1, "upgrades": [
                    { "id": "pickaxe", "name": "Pickaxe", "desc": "d",
                      "effect": { "type": "miningSpeed", "perLevel": 0.35 },
                      "baseCost": 60, "costGrowth": 1.9, "maxLevel": 10 }
                ] }
            """.trimIndent(),
            ContentLoader.FILE_ECONOMY to """
                { "version": 1, "start": { "money": 25, "backpack": 12 },
                  "worker": { "moveSpeed": 2.4, "mineDps": 22 },
                  "sell": { "depotRadius": 3.0 }, "idle": { "offlineCapHours": 4 } }
            """.trimIndent(),
            ContentLoader.FILE_WORLD to """
                { "version": 1, "zone": "z", "name": "Z",
                  "nodes": [ { "typeId": "stone_vein", "at": [1, 6] } ] }
            """.trimIndent(),
        )
        return full + overrides.toMap()
    }
}
