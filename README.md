# Mining Tycoon 3D

A complete 3D mobile idle-mining tycoon for Android, built **from scratch** around a
data-driven core, a procedural 3D world (Filament via SceneView) and a mobile-only
development pipeline where GitHub is the single source of truth and GitHub Actions
is the build farm.

**Current milestone: Phase 2 v0.2.0** — cinematic presentation on top of the playable vertical slice.

## Play it

1. Download `MiningTycoon3D-v0.2.0.apk` from the [latest release](https://github.com/davidruizreyez3005/idle-mining-tycoon-3d/releases).
2. Install on any Android 7.0+ device (minSdk 24) — the release APK is debug-signed for sideloading.
3. Tap a rock to mine it, tap the ground to walk — the fixed cinematic camera frames the action
   and follows you automatically.
4. Sell at the depot (the spinning gold coin), buy upgrades, buy the Auto-Extractor,
   close the app and come back to a "While you were away..." report.

## The vertical slice

- Greenfield Quarry: a 46 x 46 m 3D zone — grass, dirt road, cliff wall with a mine
  entrance tunnel, depot, trees, fences, crates, lamps.
- 4 resources (stone, coal, iron, gold) with distinct colors, values and rarity.
- 11 mineable veins across 4 node types with HP, break FX, drops and respawns.
- One directly controllable worker: tap-to-move, tap-to-mine, procedural walk/mining
  animation, pickaxe, growing backpack.
- 5 data-driven upgrades (pickaxe, boots, backpack, market contracts, auto-extractor).
- Idle production + offline earnings capped at 4 h, with a welcome-back dialog.
- Versioned, migration-aware save system with atomic file writes.
- JVM unit tests covering content validation, economy math, save migrations and
  the full simulation loop.

## Phase 2 — cinematic presentation

- **Fixed cinematic camera**: authored yaw/pitch/distance/fov in `world.json`, zero
  gesture orbit; the framing target softly follows the worker inside a clamped
  window (deadzone + strength + range, all data-driven).
- **Depth**: warm sun + cool sky fill (2048 px soft shadow map), atmospheric distance
  fog with sun in-scattering, a tree ring and mountain silhouettes on a wide apron —
  layered haze instead of a flat backdrop.
- **Shading**: per-material PBR (brushed steel, near-mirror gold, matte rock,
  polished-gem ore crystals, unlit glowing lamps), ACES tone mapping, bloom, vignette
  and SSAO on the Cinematic quality preset.
- Every look parameter lives in the `visuals` block of `world.json` — per-zone moods
  without touching code.

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
