# Mining Tycoon 3D

A complete 3D mobile idle-mining tycoon for Android, built **from scratch** around a
data-driven core, a procedural 3D world (Filament via SceneView) and a mobile-only
development pipeline where GitHub is the single source of truth and GitHub Actions
is the build farm.

**Current milestone: Phase 3 v0.3.0** — pan/zoom locked-ortho camera, dynamic market,
new resources and upgrade abilities on top of the cinematic vertical slice.

## Play it

1. Download `MiningTycoon3D-v0.3.0.apk` from the [latest release](https://github.com/davidruizreyez3005/idle-mining-tycoon-3d/releases).
2. Install on any Android 7.0+ device (minSdk 24) — the release APK is debug-signed for sideloading.
3. Tap a rock to mine it, tap the ground to walk. Drag with one finger to pan the
   camera, pinch with two to zoom — the 45° orthographic view angle never changes.
4. Sell at the depot (the spinning gold coin) when the MARKET panel shows your resources
   trending up, buy upgrades — including Lucky Strikes and the Warehouse — buy the
   Auto-Extractor, close the app and come back to a "While you were away..." report.

## The vertical slice

- Greenfield Quarry: a 46 x 46 m 3D zone — grass, dirt road, cliff wall with a mine
  entrance tunnel, depot, trees, fences, crates, lamps.
- 6 resources (stone, coal, iron, silver, gold, crystal) with distinct colors, values
  and rarity.
- 15 mineable veins across 6 node types with HP, break FX, drops and respawns.
- One directly controllable worker: tap-to-move, tap-to-mine, procedural walk/mining
  animation, pickaxe, growing backpack.
- 7 data-driven upgrades (pickaxe, boots, backpack, lucky strikes, market contracts,
  warehouse, auto-extractor).
- Idle production + offline earnings capped at 4 h (+2 h per Warehouse level),
  with a welcome-back dialog.
- Versioned, migration-aware save system with atomic file writes.
- JVM unit tests covering content validation, economy math, save migrations and
  the full simulation loop.

## Phase 2 — cinematic presentation

- **Depth**: warm sun + cool sky fill (2048 px soft shadow map), atmospheric distance
  fog with sun in-scattering, a tree ring and mountain silhouettes on a wide apron —
  layered haze instead of a flat backdrop.
- **Shading**: per-material PBR (brushed steel, near-mirror gold, matte rock,
  polished-gem ore crystals, unlit glowing lamps), ACES tone mapping, bloom and
  SSAO on the Cinematic quality preset.
- Every look parameter lives in the `visuals` block of `world.json` — per-zone moods
  without touching code.

## Phase 3 — economy depth + upgrade abilities

- **Locked 45° ortho camera**: the viewing angle and orthographic projection are
  authored in `world.json` and never change; the player pans by dragging and zooms
  by pinching, both clamped so the mine and depot stay reachable. The blur/vignette
  frame is gone — clean viewport edges.
- **Dynamic market**: every resource price rides a slow sine wave (amplitude,
  periods and trend window data-driven in `economy.json`). The new MARKET panel
  shows live prices with trend arrows, so *when* you sell becomes a real decision.
- **New content**: Silver and Crystal resources, two new vein types placed in the
  quarry, and Auto-Extractor rates for silver (level 7) and crystal (level 10).
- **New upgrades**: Lucky Strikes (chance of double loot on vein break) and
  Warehouse (+2 h offline cap per level).

## Build locally

```bash
./gradlew testDebugUnitTest assembleDebug
# or the release APK (debug-signed, sideloadable):
./gradlew assembleRelease
```

Requires JDK 17 and Android SDK 36 (see `docs/06_BUILD_PIPELINE.md`).

## Documentation

| Doc | Contents |
|-----|----------|
| [01 Requirements analysis](docs/01_REQUIREMENTS_ANALYSIS.md) | The 32-section game design brief, distilled into engineering decisions |
| [02 Architecture](docs/02_ARCHITECTURE.md) | System layers, data flow, and the rules that keep them decoupled |
| [03 Directory structure](docs/03_DIRECTORY_STRUCTURE.md) | Every package and its job |
| [04 Data schemas](docs/04_DATA_SCHEMAS.md) | The five gamedata JSON files + the save schema |
| [05 Asset pipeline](docs/05_ASSET_PIPELINE.md) | AI asset generation, validation, and the provider seams |
| [06 Build pipeline](docs/06_BUILD_PIPELINE.md) | GitHub Actions → APK → GitHub Release |

## Development model

Developed entirely from a phone: GitHub is the source of truth, GitHub Actions builds
and releases the APK, and AI asset generation (future phases) runs as cloud APIs
wired through swappable providers. No secrets ever ship inside the APK.

## Roadmap

The 12-phase plan (core loop → economy → upgrades → machines → workers →
underground → 3D quality → animation/VFX → idle offline → prestige → content →
optimization) lives in `docs/01_REQUIREMENTS_ANALYSIS.md`. Phase 1's playable
vertical slice is what you are looking at; every later phase keeps the build
playable at all times.
