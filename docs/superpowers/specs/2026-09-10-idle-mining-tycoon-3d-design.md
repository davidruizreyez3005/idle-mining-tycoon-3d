# Idle Mining Tycoon 3D — Design Spec

Date: 2026-09-10
Author: Hermes (architectural task)
Path: architectural (new project, multi-subsystem)

## Goal
Build a mobile-first 3D idle mining tycoon Android game using Kotlin, Jetpack Compose, SceneView (Filament 3D), MVVM/UDF, Room + DataStore, with an offline earnings calculator.

## Architecture
- Language: Kotlin 100%, Kotlin DSL (`.kts`)
- UI: Jetpack Compose (Material3) overlay on 3D scene
- 3D: `io.github.sceneview:sceneview`
- Pattern: MVVM / Unidirectional Data Flow (StateFlow → Compose UI)
- Async: Kotlin Coroutines (`kotlinx.coroutines`)
- Persistence: Room (game entities), DataStore (player prefs, settings)
- Offline Engine: `OfflineCalculator` (elapsed time * rate)

## Module Layout
```
app/
  src/main/
    java/com/example/idlemining/
      data/        (Room, DataStore, Repository, OfflineCalculator)
      domain/      (GameState, Upgrade, OreType, Economy formulas)
      game/3d/     (SceneView setup, nodes, meshes)
      game/engine/ (GameLoopTicker, WorkerState)
      ui/          (HUD, BottomSheets, Dialogs)
```

## Key Design Decisions
1. **SceneView**: `io.github.sceneview:sceneview` wraps Filament; provides `ModelInstance`, `Material`, `Node`, `Camera`, `Light`. No raw OpenGL.
2. **Compose Overlay**: `AndroidView({ SceneView(...) })` or `SceneView` embedded with Compose drawn over via `Box` with transparent background + `Surface` HUD elements.
3. **Tick Loop**: `CoroutineScope(Dispatchers.Default)` launches a `while(true)` tick every 100ms (`delay(100)`). Emits to `StateFlow<GameState>`.
4. **Offline Calculation**: `lastOnlineTimestamp` (Room) saved every tick; on reopen, compute `deltaMs / 1000 * rate` and inject earnings into `GameState`.
5. **Upgrade Formula**: `cost = base * pow(1.15, level)`.
6. **3D Nodes**: Procedural primitives (`Sphere`, `Box`, `Cylinder`) with stylized colors (low-poly aesthetic). Conveyor = animated `Cylinder`; Shaft = `Box`; Cart = `Box` moving on path; Crusher = `Cylinder` + `Box` hierarchy.

## Dependencies (Gradle)
- Compose BOM (Material3)
- SceneView (`io.github.sceneview:sceneview:2.1.0` approximate)
- Room (`androidx.room`)
- DataStore (`androidx.datastore`)
- Coroutines (`kotlinx-coroutines-android`)
- Lifecycle runtime (`androidx.lifecycle`)

## Testing / Verification
- Build: `./gradlew assembleDebug` (no compilation errors)
- Push: `git push origin main`
- GitHub repo: `idle-mining-tycoon-3d` (public)

## Constraints
- Solo developer; direct-action preferred (`retry` style commands acceptable).
- Node available; `npm` absent — use `gradlew` only.
- No external 3D asset downloads; procedural/stylized primitives only.
- Must work as standalone scaffold; no CDN dependency required.
