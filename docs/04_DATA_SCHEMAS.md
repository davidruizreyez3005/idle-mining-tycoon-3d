# 04 — Data Schemas

All gameplay content is JSON under `app/src/main/assets/gamedata/`. Schemas are
defined once in `core/content/ContentSchema.kt` (`@Serializable` DTOs) and
validated at load time by `ContentLoader`. This document is the human-readable
contract.

Conventions:
- Colors are `#RRGGBB` strings (parsed to opaque ARGB).
- Positions are `[x, z]` meters, world origin at the zone center.
- Unknown fields are ignored on read (forward compatibility).
- Every cross-reference (node type → resource, world node → type id) is
  validated; a dangling reference fails at boot.

## resources.json

```json
{
  "version": 1,
  "resources": [
    {
      "id": "stone",              // unique key used by everything else
      "name": "Stone",            // display name
      "rarity": "common",         // common | uncommon | rare | epic (UI tinting)
      "baseValue": 2,             // sell price per unit before market margin
      "color": "#B0BEC5",         // ore crystal + drop + UI dot color
      "icon": "stone"             // reserved for the future icon pipeline
    }
  ]
}
```

Shipped: `stone` (2), `coal` (6), `iron` (16), `gold` (45).

## mine_nodes.json

```json
{
  "version": 1,
  "nodeTypes": [
    {
      "id": "coal_vein",            // referenced by world.json placements
      "name": "Coal Seam",          // shown in the mining progress card
      "mesh": "rock_large",         // kit piece: rock_small | rock_large | rock_boulder
      "hp": 140,                    // mining damage pool
      "respawnSeconds": 24,         // regrow time after breaking
      "yields": { "coal": 2, "stone": 1 },   // resource id -> units dropped
      "primaryResource": "coal",    // crystal color + drop color
      "scale": 1.1,                 // visual scale multiplier
      "crystals": 4                 // ore crystals on the rock surface
    }
  ]
}
```

## upgrades.json

```json
{
  "version": 1,
  "upgrades": [
    {
      "id": "pickaxe",              // save key + lookup key
      "name": "Reinforced Pickaxe",
      "desc": "Swing harder — mining speed +35% per level.",
      "effect": {
        "type": "miningSpeed",      // miningSpeed | moveSpeed | backpack |
                                    // sellMargin | idleExtraction
        "perLevel": 0.35            // scalar effect strength per level (Double —
                                    // feeds money math)
      },
      "baseCost": 60,               // cost of level 1
      "costGrowth": 1.9,            // cost(n) = baseCost * growth^(n-1), must be > 1
      "maxLevel": 10,
      "category": "tools"           // future shop tab grouping
    },
    {
      "id": "extractor",
      "name": "Auto-Extractor",
      "desc": "A little machine keeps digging while you are away...",
      "effect": {
        "type": "idleExtraction",
        "rates": [
          { "resource": "stone", "perLevel": 0.4, "unlockLevel": 1 },
          { "resource": "coal",  "perLevel": 0.15, "unlockLevel": 4 }
        ]
      },
      "baseCost": 150, "costGrowth": 2.1, "maxLevel": 12, "category": "automation"
    }
  ]
}
```

`perLevel` is intentionally `Double`: the sell margin and idle rates feed money
calculations where Float precision visibly drifts (a lesson captured in
`EconomyRulesTest`).

## economy.json

```json
{
  "version": 1,
  "start":   { "money": 25, "backpack": 12 },      // fresh-save values
  "worker":  { "moveSpeed": 2.4,                   // m/s before boots
               "mineDps": 22,                      // damage/s before pickaxe
               "collectRadius": 1.5,               // (reserved for ground drops)
               "reachRadius": 1.8 },               // mining engagement distance
  "sell":    { "depotRadius": 3.0 },               // auto-sell zone
  "idle":    { "offlineCapHours": 4 }              // away-time earnings cap
}
```

## world.json — the modular zone layout

```json
{
  "version": 1,
  "zone": "greenfield_quarry",
  "name": "Greenfield Quarry",
  "ground": { "size": [46, 46], "grassColor": "#7CB342", "dirtColor": "#A1887F",
              "rockColor": "#78909C", "pathWidth": 4.2 },
  "camera": { "orbitRadius": 16.5, "target": [0, 0.8, -4] },
  "spawn":  [0, 8],                                  // worker start [x, z]
  "cliff":  { "z": -19, "halfSpan": 14, "height": 5, "depth": 4,
              "entranceHalfWidth": 3.4, "wallColor": "#6D838F" },
  "nodes":  [ { "typeId": "gold_vein", "at": [0, -20.3], "scale": 1.15 }, ... ],
  "depot":  { "position": [0, 12.5], "facing": 0 },
  "props":  [ { "piece": "tree_pine", "at": [-19, -3], "scale": 1.1,
                "rotation": 90 }, ... ]
}
```

`nodes` are **index-stable** — the save file stores node state by list position,
so inserting nodes appends (never reorders). `props` reference kit piece ids
(`KitCatalog`); unknown ids are skipped, which is what lets the AI asset pipeline
introduce richer pieces later without touching this file.

### performance block (Phase 3.5)

Mobile render tuning, applied on top of the `RenderQuality.Default` preset by
`ScenePerformance` (after the zone `visuals`). All fields have mobile-first
defaults, so a zone without a `performance` block still runs fast:

```json
"performance": {
  "dynamicResolution": { "enabled": true, "minScale": 0.5, "maxScale": 1.0, "quality": "medium" },
  "ssao": false,          // screen-space ambient occlusion
  "msaaSampleCount": 0,   // 0/1 = FXAA only; 4 was the pre-tuning default
  "hdrQuality": "medium", // low | medium | high
  "bloomQuality": "low",  // low | medium | high
  "softShadows": false    // PCSS penumbra sampling
}
```

Dynamic resolution is the frame-rate safety net: Filament measures frame time
and rescales the render target within `minScale..maxScale` (homogeneous, so no
aspect distortion). Validation rejects inverted/out-of-range scale windows,
unknown tier names and unsupported MSAA sample counts.

## Save schema (v1)

Written as JSON via `SaveStorage` (atomic tmp+rename), migrated forward by
`SaveMigrations` before decoding:

```json
{
  "version": 1,
  "createdAtMs": 1697000000000,
  "savedAtMs": 1697000123456,
  "money": 125.0,
  "inventory": { "stone": 3, "gold": 1 },
  "upgrades": { "pickaxe": 2, "extractor": 1 },
  "worker": { "x": 1.2, "z": 6.4, "facing": 181.3 },
  "nodes": [ { "hp": 42.5, "respawnRemainingSec": 0.0 }, ... ],
  "stats": { "totalMined": 34, "totalSoldValue": 120.0, "totalEarned": 145.0,
             "playSeconds": 320.5, "nodesBroken": 11 },
  "flags": { "hint_shown": true },
  "offlineUnseen": false
}
```

Migration rules:
- `version` missing → treated as v0.
- Unknown fields on any future version are preserved-and-ignored, never fatal.
- A save **newer** than the build is rejected with a clear error (update the app).
- The migration chain is unit-tested with a synthetic v0 (money as a string) to
  prove the machinery end-to-end; every future schema change appends one step.
