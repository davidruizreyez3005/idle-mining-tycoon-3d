# 01 — Requirements Analysis

The game design brief is a 32-section production spec for a **commercial-grade 3D
mobile idle mining tycoon**. This document distills it into the engineering
decisions that shaped the repository, and defines what "done" means for each
pillar. It is the contract the other docs implement.

## 1. What the game is

A mining tycoon where the player visibly grows a hand-worked quarry into an
automated industrial operation: manual pick-and-haul first, machines and
workers second, a fully self-running multi-level industrial complex eventually.
The fantasy is **watching your operation grow in 3D**, not reading numbers.

## 2. Non-negotiable pillars (from the brief)

| # | Pillar | Engineering consequence |
|---|--------|------------------------|
| 1 | **Data-driven content** — nothing hardcoded | All content lives in `assets/gamedata/*.json`; logic reads it through `GameContent` |
| 2 | **Provider abstraction for AI assets** | `core/providers/AssetProviders.kt` defines the seams (text/image/3D/animation/processing) |
| 3 | **Modular world generation** | Kit pieces with stable ids + placements in `world.json`; `WorldBuilder` assembles the zone |
| 4 | **Versioned saves** | `SaveData` with `version`, migration chain in `SaveMigrations`, atomic writes |
| 5 | **Mobile-only development** | GitHub = source of truth; Actions = build farm; releases from CI, never local |
| 6 | **No secrets in the APK** | Dev-time credentials stay in CI secrets; the shipped game needs none |
| 7 | **Performance budget** | Procedural low-poly geometry, small node counts, 10 Hz logic / 60 Hz render split |
| 8 | **Touch-first UX** | Tap-to-move/mine, drag-orbit, pinch-zoom; large HUD targets; no desktop assumptions |

## 3. The vertical slice contract (Phase 1 exit criteria)

The brief's section 31 demands a **playable vertical slice** before anything else:

- [x] A detailed 3D mining area (Greenfield Quarry: cliff, tunnel, depot, props)
- [x] One controllable player/worker (tap-to-move + tap-to-mine)
- [x] One mine (the tunnel into the cliff with interior node cluster)
- [x] At least 3 resources — shipped 4 (stone, coal, iron, gold)
- [x] One mining mechanic (HP-based tap mining with FX and drops)
- [x] Resource collection (auto-pickup with backpack capacity)
- [x] Selling (depot arrival auto-sell + SELL button pathing)
- [x] One upgrade — shipped 5 (all data-driven)
- [x] Basic idle production (Auto-Extractor streams resources; offline report)
- [x] Basic UI (HUD, upgrade shop, popups, onboarding hint)
- [x] Save/load (versioned, autosave 5 s, save on pause)
- [x] Android touch controls (tap/drag/pinch via SceneView)
- [x] Optimized 3D assets (instanced-style primitives, low poly, one material per color)
- [x] A working APK build from CI with a GitHub release

## 4. The 12-phase development order

| Phase | Theme | Status |
|-------|-------|--------|
| 1 | Core loop vertical slice | **done (this repo)** |
| 2 | Economy depth (market fluctuation, contracts) | next |
| 3 | Upgrade trees + special abilities | planned |
| 4 | Machines (crusher, smelter, conveyor networks) | planned |
| 5 | Worker automation (hire, task, pathing) | planned |
| 6 | Underground expansion (7+ visually distinct levels) | planned |
| 7 | 3D quality (lighting, toon shading, post-FX) | planned |
| 8 | Animation + VFX polish | planned |
| 9 | Idle/offline depth (managers, boosts, caps) | planned |
| 10 | Prestige (rebirth) system | planned |
| 11 | Content breadth (zones, achievements, skins) | planned |
| 12 | Optimization + store readiness | planned |

Rule enforced throughout: **every phase ends with a playable, releasable build.**
CI releases on every push to `main` to make that verifiable.

## 5. Technical constraints accepted

- **Kotlin + Jetpack Compose + SceneView/Filament** — same verified stack as the
  predecessor build (AGP 8.13.2 / Kotlin 2.4.20 / compileSdk 36 / minSdk 24).
- **Single module now, seams for later modules** — the package layout anticipates
  `:core`, `:game`, `:ui` extraction without moving files.
- **Memory ceiling** — builds run under a 2 GB Gradle daemon (CI runners and the
  dev sandbox are both memory-constrained).
- **No emulator in the dev loop** — verification is: unit tests + APK build + the
  user installing the release on a real phone.

## 6. Deferred (explicitly, with reasons)

- **R8/minification** — the slice ships unminified; shrinking is Phase 12 work
  and needs a careful keep-rule matrix for Filament + kotlinx-serialization.
- **Toon shading / post-processing** — Phase 7; the slice's stylized flat-color
  look reads well without custom materials.
- **Sound** — Phase 8.
- **Real GLB assets** — arrive with the AI pipeline (docs/05); the procedural kit
  keeps geometry data-driven in the meantime.
- **Multi-zone save data** — the save schema is versioned exactly so zone data
  can migrate in later without breaking players.
