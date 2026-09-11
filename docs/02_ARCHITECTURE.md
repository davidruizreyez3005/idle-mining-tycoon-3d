# 02 — Technical Architecture

## Layer map

```
┌──────────────────────────────────────────────────────────────┐
│ ui/          Compose HUD, dialogs, panels                     │  Android UI
│ viewmodel/   GameViewModel (MVVM glue)                        │
├──────────────────────────────────────────────────────────────┤
│ game/scene/  SceneView viewport, touch picking, animator      │  3D presentation
│ game/world/  Kit catalog + WorldBuilder (procedural kit)      │
├──────────────────────────────────────────────────────────────┤
│ game/        GameEngine (tick loop), Simulation (pure kernel) │  Game logic
│              GameState, intents, events                       │
├──────────────────────────────────────────────────────────────┤
│ core/content ContentLoader + GameContent (validated JSON)     │  Data-driven core
│ core/economy EconomyRules (pure formulas)                     │
│ core/save    SaveData + migrations + storage seam             │
│ core/providers Asset provider interfaces (AI pipeline seams)  │
└──────────────────────────────────────────────────────────────┘
```

Dependencies point **down only**. `core/` has zero Android imports except the
deliberately thin `AndroidContent` bridge (assets reader) — everything below
`viewmodel/` is pure Kotlin and runs in JVM unit tests.

## The four rules

### Rule 1 — Data drives content, code drives behavior

All gameplay numbers, names, colors, node placements, prop placements and camera
setup come from `assets/gamedata/*.json`. Kotlin never hardcodes a resource value.
Adding a resource = one JSON entry. Rebalancing = editing JSON, no rebuild of
logic. The `ContentLoader` validates everything at load time so a bad file fails
loudly at boot — never during play.

### Rule 2 — Unidirectional data flow, pure kernel

```
UI intents ──dispatch──▶ GameEngine ──reduce/simulate──▶ GameState (StateFlow, 10 Hz)
     ▲                                                      │
     └────── HUD recomposes ────────────────────────────────┘
                    GameEvents (SharedFlow, one-shot popups)
```

`Simulation` is a pure object: `simulate(state, dt) → state` and
`reduce(state, intent) → state`. No clocks, no Android, no I/O. The engine owns
the wall clock, the tick cadence (100 ms) and persistence; the kernel stays
trivially testable (see `SimulationTest` — the entire game loop runs headless).

### Rule 3 — 10 Hz logic, 60 Hz rendering, zero recomposition during play

The 3D scene composes **once** from content data; no Compose state is read inside
the scene tree. `GameScene` hands `State<GameState>` to `SceneAnimator`, which
runs inside SceneView's `onFrame` and writes node transforms imperatively:

- worker position/facing are **exponentially smoothed** toward the 10 Hz logical
  position (frame-rate-independent),
- node damage flash, respawns, break fragments, ore drops and the depot/extractor
  ambience are all diff-driven from state,
- effects live in pooled nodes (10 drops, 18 fragments) — no allocations in the
  frame loop.

### Rule 4 — Seams where the future plugs in

| Seam | Interface | Today | Tomorrow |
|------|-----------|-------|----------|
| Geometry source | `WorldKitProvider` | `ProceduralKitProvider` (KitCatalog primitives) | `GlbKitProvider` (validated AI-generated GLBs) |
| Asset generation | `TextModelProvider` / `ImageProvider` / `ThreeDModelProvider` / `AnimationProvider` | placeholders | cloud APIs, dev-time only |
| Asset processing | `AssetProcessingProvider` | placeholder | validate → optimize → LOD pipeline |
| Persistence | `SaveStorage` | `FileSaveStorage` (atomic tmp+rename) | any store; tests use `InMemorySaveStorage` |
| Clock | `nowMs: () -> Long` on `GameEngine` | system clock | deterministic tests |

## Input architecture (touch-first)

SceneView's touch dispatcher raycasts against node colliders (auto-derived box
colliders on renderable nodes) and hands every `MotionEvent` to our
`onTouchEvent` callback **without consuming it** — the camera gesture detector
keeps working. Tap detection (down→up within 24 dp slop and 400 ms) resolves the
hit through `NodeRegistry` (parent-chain walk) into a `GameIntent`:

- node root → `TapNode(index)` — walk there and mine
- depot root → `TapDepot` — walk there and sell
- ground/props → `TapGround(x, z)` — walk there

## Rendering stack

- **Engine**: Filament through SceneView 4.34 (Compose-first API).
- **Geometry**: `CubeNode` / `SphereNode` / `CylinderNode` / `ConeNode` /
  `CapsuleNode` primitives composed by the kit catalog — one material instance
  per color (cached), shadows on, default sun + IBL.
- **Camera**: `CameraManipulator` in orbit mode around the quarry center;
  author-specified radius/target from `world.json`.
- **Picking**: Filament-agnostic — SceneView's built-in collision system with
  auto AABB colliders.

## Save/load flow

```
GameEngine.saveNow() ─▶ Simulation.toSave(state, now) ─▶ SaveData (v1 JSON)
                        ─▶ SaveStorage.writeRaw (tmp + atomic rename)

boot ─▶ SaveStorage.readRaw ─▶ decodeSave (migrate v0→…→v1, tolerant decode)
      ─▶ Simulation.computeOffline (extractor × elapsed, 4 h cap)
      ─▶ Simulation.fromSave (merge offline earnings) ─▶ StateFlow + report dialog
```

## Performance posture (vertical slice budget)

- ~120 renderable nodes total, all primitives; one Filament material per color.
- Logic tick: 10 Hz fixed, wall-clock compensated, dt clamped to 0.25 s.
- Frame loop: O(nodes) diff writes + pooled effects; no allocations after warmup.
- APK 43 MB (dominated by Filament natives across 4 ABIs) — acceptable for the
  slice; App Bundle + ABI splits are Phase 12 work.
