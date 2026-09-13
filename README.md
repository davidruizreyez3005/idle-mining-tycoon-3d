# Mining Tycoon 3D

A complete 3D mobile idle-mining tycoon for Android, built **from scratch** around a
data-driven core, a procedural 3D world (Filament via SceneView) and a mobile-only
development pipeline where GitHub is the single source of truth and GitHub Actions
is the build farm.

**Current milestone: Phase 3.5 v0.3.1** — a full speed & performance pass over the
Phase 3 build: mobile-tuned rendering, a recomposition-quiet UI and an
allocation-free frame loop, on top of the dynamic-market vertical slice.

## Play it

1. Download `MiningTycoon3D-v0.3.1.apk` from the [latest release](https://github.com/davidruizreyez3005/idle-mining-tycoon-3d/releases).
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

- **Depth**: warm sun + cool sky fill (1024 px shadow map), atmospheric distance
  fog with sun in-scattering, a tree ring and mountain silhouettes on a wide apron —
  layered haze instead of a flat backdrop.
- **Shading**: per-material PBR (brushed steel, near-mirror gold, matte rock,
  polished-gem ore crystals, unlit glowing lamps), ACES tone mapping and bloom.
- Every look parameter lives in the `visuals` block of `world.json` — per-zone moods
  without touching code.

## Phase 3.5 — speed & performance

The v0.3.0 build felt slow and laggy on mid-range phones. The bottleneck audit
found six root causes; all are fixed and none of them were the simulation (which
only ticks at 10 Hz and costs microseconds):

- **GPU**: the `Cinematic` render preset shipped MSAA 4x + FXAA, SSAO HIGH with
  bilateral upsampling, HDR HIGH, dynamic resolution OFF and a 2048px PCSS shadow
  map. The new data-driven `performance` block in `world.json` re-tunes the same
  view: dynamic resolution **on** (Filament rescales the render target to hold
  frame rate — the safety net on any device), FXAA only, SSAO off, 1024px shadows
  without PCSS, MEDIUM HDR / LOW bloom tiers.
- **Compose**: `GameScene` read the live `GameState` during composition, so the
  entire 3D world composition (~1000 node composables) re-executed on every one of
  the 10 simulation ticks per second. The scene now takes the immutable
  `GameContent` and reads live state only inside frame/touch callbacks — it
  composes exactly once.
- **HUD**: chips and panels recomposed at 10 Hz with market sine math and string
  building in composition. They now derive 1 Hz-quantized snapshots
  (`ui/components/HudSnapshots.kt`) and only recompose when displayed values
  actually change.
- **Frame loop**: the locked-ortho camera ran full `lookAt` + `setProjection` +
  allocations every frame — the angle never changes, so the rotation is cached and
  everything is dirty-gated (zero work while idle). The animator now writes
  transforms only when values change (an idle worker or untouched vein costs
  nothing) and caches content lookups.
- **Engine**: autosave JSON encoding moved off the main thread (it caused a hitch
  every 5 s); the simulation no longer allocates a fresh node list per tick when
  nothing is respawning.
- **Leak**: every vein break allocated a GPU-backed `MaterialInstance` that was
  never destroyed — resource materials are cached now.

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
