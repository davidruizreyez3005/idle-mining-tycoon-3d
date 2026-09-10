# Idle Mining Tycoon 3D — Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Each task uses independent subagent dispatch with review gate.

**Goal:** Build working Android 3D idle mining tycoon app (SceneView + Compose MVVM) with offline earnings, pushed to `idle-mining-tycoon-3d`.

**Architecture:** Kotlin, Compose Material3 overlay, SceneView (Filament), MVVM with StateFlow, Room + DataStore, Coroutine tick loop (100ms).

**Tech Stack:** Kotlin DSL, Compose BOM, SceneView 2.x, Room 2.6, DataStore, Coroutines Android.

**Spec:** docs/superpowers/specs/2026-09-10-idle-mining-tycoon-3d-design.md

## Global Constraints
- Kotlin 100%, `.kts` DSL only.
- SceneView dependency: `io.github.sceneview:sceneview`
- No `npm`; build only via `gradlew`.
- All 3D assets procedural/stylized primitives (no external glTF downloads).
- Public GitHub repo `idle-mining-tycoon-3d`; `main` branch.
- `assembleDebug` must pass with zero compilation errors.
- Offline calculator uses `System.currentTimeMillis()` delta.
- Upgrade formula: `cost = base * 1.15^level`.

---

### Task 1: Gradle Setup & Project Skeleton

**Files:**
- Create: `build.gradle.kts` (app)
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Modify: `AndroidManifest.xml`
- Create: `gradlew` wrapper (if missing)

**Produces:** Working Gradle build that syncs.

**Constraints:** Compose BOM, Material3, SceneView, Room, DataStore, Coroutines included. Kotlin 2.0 plugin.

- [ ] Step 1: Write `build.gradle.kts` with dependencies
- [ ] Step 2: Write `settings.gradle.kts`
- [ ] Step 3: Write `gradle.properties` (compileSdk 35, minSdk 26)
- [ ] Step 4: Create basic `AndroidManifest.xml` (theme, MAIN/LAUNCHER)
- [ ] Step 5: Verify `./gradlew sync` (via `assembleDebug` dry-run)
- [ ] Step 6: Commit

---

### Task 2: Domain Layer (Models + Economy)

**Files:**
- Create: `src/main/java/com/example/idlemining/domain/model/GameState.kt`
- Create: `src/main/java/com/example/idlemining/domain/model/Upgrade.kt`
- Create: `src/main/java/com/example/idlemining/domain/model/OreType.kt`
- Create: `src/main/java/com/example/idlemining/domain/economy/EconomyFormulas.kt`

**Produces:** Pure Kotlin data classes; no Android framework dependency.

- [ ] Step 1: Define `OreType` enum (COPPER, IRON, GOLD, DIAMOND)
- [ ] Step 2: Define `Upgrade` data class (id, level, baseCost, multiplier)
- [ ] Step 3: Define `GameState` data class (cash, gems, oreInventory, upgradesMap, timestamp)
- [ ] Step 4: Implement `EconomyFormulas.costFor(level: Int, base: Double)` = `base * Math.pow(1.15, level)`
- [ ] Step 5: Unit-style verification (compile check)
- [ ] Step 6: Commit

---

### Task 3: Data Layer — Room + Repository + Offline Calculator

**Files:**
- Create: `src/main/java/com/example/idlemining/data/local/AppDatabase.kt`
- Create: `src/main/java/com/example/idlemining/data/local/GameStateDao.kt`
- Create: `src/main/java/com/example/idlemining/data/repository/GameRepository.kt`
- Create: `src/main/java/com/example/idlemining/data/repository/OfflineCalculator.kt`

**Produces:** Database schema, repository interface, offline calculator logic.

- [ ] Step 1: Create Room `@Entity` `SavedGameState`
- [ ] Step 2: Create `AppDatabase` abstract class
- [ ] Step 3: Create `GameStateDao` with `@Query` insert/select
- [ ] Step 4: Create `OfflineCalculator.computeEarnings(savedState, currentTimeMs)`
- [ ] Step 5: Create `GameRepository` interface + basic implementation
- [ ] Step 6: Commit

---

### Task 4: 3D Scene — SceneView Setup + Nodes

**Files:**
- Create: `src/main/java/com/example/idlemining/game/3d/MiningScene.kt`
- Create: `src/main/java/com/example/idlemining/game/3d/nodes/ShaftNode.kt`
- Create: `src/main/java/com/example/idlemining/game/3d/nodes/ConveyorNode.kt`
- Create: `src/main/java/com/example/idlemining/game/3d/nodes/CartNode.kt`

**Produces:** SceneView scene with directional light + ambient, procedural nodes.

- [ ] Step 1: Setup `SceneView` in Compose (`AndroidView`)
- [ ] Step 2: Add directional sun + ambient light with soft shadow
- [ ] Step 3: Create procedural `ShaftNode` (`Box` primitive, stylized color)
- [ ] Step 4: Create `ConveyorNode` (`Cylinder` + `Material`)
- [ ] Step 5: Create `CartNode` (`Box` with animation-capable path)
- [ ] Step 6: Commit

---

### Task 5: Game Engine — Tick Loop + StateFlow

**Files:**
- Create: `src/main/java/com/example/idlemining/game/engine/GameLoopTicker.kt`
- Create: `src/main/java/com/example/idlemining/game/engine/WorkerState.kt`

**Produces:** Coroutine tick loop (100ms) emitting `StateFlow` updates.

- [ ] Step 1: Define `WorkerState` enum (MINING, LOADING, MOVING, UNLOADING)
- [ ] Step 2: Implement `GameLoopTicker` with `viewModelScope`
- [ ] Step 3: Tick updates: move cart, generate ore, update cash
- [ ] Step 4: Emit to `StateFlow<GameState>`
- [ ] Step 5: Commit

---

### Task 6: Compose UI — HUD + Bottom Sheet Upgrade Panel

**Files:**
- Create: `src/main/java/com/example/idlemining/ui/HudOverlay.kt`
- Create: `src/main/java/com/example/idlemining/ui/UpgradeBottomSheet.kt`
- Create: `src/main/res/layout/activity_main.xml` (or Compose-only)
- Modify: `MainActivity.kt`

**Produces:** Overlaid HUD with balance/income; slide-up upgrade sheet.

- [ ] Step 1: Create `MainActivity` hosting `SceneView` + Compose overlay (`Box`)
- [ ] Step 2: Build `HudOverlay` (cash, gems, $/sec) with `Surface` cards
- [ ] Step 3: Build `UpgradeBottomSheet` for Shaft/Refinery/Managers
- [ ] Step 4: Wire `StateFlow` from `ViewModel` into Compose `collectAsState()`
- [ ] Step 5: Commit

---

### Task 7: Integration — ViewModel + Data Flow + Offline

**Files:**
- Create: `src/main/java/com/example/idlemining/ui/MainViewModel.kt`
- Modify: `MainActivity.kt` (inject ViewModel)

**Produces:** End-to-end MVVM: UI updates from tick loop; offline earnings applied on launch.

- [ ] Step 1: Create `MainViewModel` extending `ViewModel`
- [ ] Step 2: Inject `GameRepository`, expose `StateFlow<GameState>`
- [ ] Step 3: On init, load saved state; if `currentTimeMs - last > 0`, apply `OfflineCalculator`
- [ ] Step 4: Connect tick updates to repository saves
- [ ] Step 5: Commit

---

### Task 8: Build Verification + Push

**Produces:** `./gradlew assembleDebug` passes; repo pushed.

- [ ] Step 1: Run `./gradlew assembleDebug`
- [ ] Step 2: Fix any compilation errors
- [ ] Step 3: Commit final build
- [ ] Step 4: Create GitHub repo `idle-mining-tycoon-3d` via `gh` API
- [ ] Step 5: `git remote add origin ...` + `git push -u origin main`
- [ ] Step 6: Verify remote exists and `main` pushed
