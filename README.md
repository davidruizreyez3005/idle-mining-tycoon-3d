# ⛏️ Idle Shaft Tycoon 3D

**Dig deep. Refine. Get rich.** — A polished 3D idle mining tycoon game for Android,
built from scratch with **100% Kotlin**, **Jetpack Compose** and **SceneView** (the
Filament-based 3D engine for Compose).

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![3D](https://img.shields.io/badge/3D-SceneView%20%2F%20Filament-FF6D00)
![License](https://img.shields.io/badge/code-open--source-green)

---

## 🎮 The Game

You run a surface mining company. The production pipeline is the core loop:

```
   Shafts            Elevator carts        Refinery               Logistics
┌─────────────┐   ┌────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│ Miners dig  │ → │ carts haul raw │ → │ crusher melts 10 │ → │ trucks sell bars │  💵 CASH
│ ore seams   │   │ ore to surface │   │ ore into 1 bar   │   │ at the market    │
└─────────────┘   └────────────────┘   └──────────────────┘   └──────────────────┘
```

- **Tap to work**: each stage starts manual — tap **DIG / REFINE / SELL**.
- **Hire managers** to automate stages forever (the *idle* part).
- **Upgrade**: miner speed/capacity, cart capacity/speed, shaft depth, refinery
  speed/efficiency, truck capacity/speed, offline warehouse storage.
- **Unlock deeper shafts** with rarer ore: Copper → Iron → Gold → Diamond.
- **Offline earnings**: close the app, come back later, collect what your automated
  crews produced (capped by your warehouse level, 4h → 12h).
- **Gems & boosts**: cross earnings milestones to earn gems, spend 5 gems for a 2×
  income boost.

## 🕹️ Controls

| Gesture | Action |
|---|---|
| One-finger drag | Orbit / pan the camera around the facility |
| Pinch | Zoom in / out |
| DIG / REFINE / SELL buttons | Manual work cycles (until managers take over) |
| Bottom bar | Shafts · Refinery · Upgrades · Managers panels |

## 🧱 Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.4 (Kotlin DSL everywhere) |
| UI | Jetpack Compose + Material 3 |
| 3D | [SceneView](https://github.com/SceneView/SceneView) 4.34 on Filament |
| Architecture | MVVM + Unidirectional Data Flow (`GameIntent` → reducer → `StateFlow<GameState>`) |
| Concurrency | Coroutines + StateFlow / SharedFlow |
| Persistence | Preferences DataStore + kotlinx-serialization JSON |
| Game loop | 100 ms fixed-tick simulation (wall-clock compensated) |

## 🏗️ Architecture

```
app/src/main/java/com/idleshaft/tycoon/
├── domain/          # Pure game math — entities, economy curves, intents, events
│   ├── Entities.kt          GameState, Shaft, Miner, Cart, Refinery, Logistics
│   ├── Economy.kt           all formulas: rates, costs, income analytics, formatting
│   └── GameIntent.kt        UDF contract (sealed intents + one-shot events)
├── game/
│   ├── engine/      # Simulation kernel + tick controller + offline engine
│   │   ├── Simulation.kt          pure state-machine reducer (miner/cart/refinery/truck)
│   │   ├── GameEngine.kt          100ms tick loop, autosave, event bus
│   │   └── OfflineEarningsCalculator.kt
│   └── threed/      # SceneView/Filament scene
│       ├── MiningScene.kt         SceneView wiring: lights, camera, onFrame loop
│       ├── MineSceneContent.kt    procedural low-poly world (no glTF assets!)
│       ├── MineWorld.kt           animated-node registry + layout constants
│       └── MineAnimator.kt        per-frame transform animation
├── data/
│   └── GameRepository.kt         DataStore + JSON save/load
├── ui/              # Compose HUD
│   ├── GameScreen.kt             3D scene + HUD overlay + bottom sheets
│   ├── components/               top bar, work buttons, popups, dialogs...
│   └── panels/                   Shafts / Refinery / Upgrades / Managers sheets
└── viewmodel/
    └── GameViewModel.kt          MVVM glue
```

**Design decisions worth knowing:**

- **Declarative scene, imperative animation.** The 3D world is declared once as a
  Compose tree of `CubeNode`/`SphereNode`/… primitives. Animated nodes are captured by
  reference and driven from SceneView's `onFrame` callback — the Compose tree never
  recomposes during gameplay, so the render loop stays at full frame rate.
- **Pure simulation kernel.** `Simulation.simulate()` / `reduce()` are pure functions
  of `(GameState, dt) → GameState` — unit-testable on the JVM (see `EconomyTest`).
- **Analytic income model.** The HUD `$/s` and the offline engine share one bottleneck
  analysis function, so displayed rates and offline payouts always agree.
- **Zero binary assets.** Every prop (headframes, carts, miners, crusher, trucks,
  market stall, trees) is procedural low-poly geometry with lit color materials.

## 🧮 Economy cheat sheet

- Upgrade cost: `cost = base × growth^(level−1)` (growth ≈ 1.15)
- Bar value: `oreValue × 10 × (1 + 0.08 × (efficiency − 1))`
- Deeper shafts: vein richness `×(1 + 0.25 × (depth − 1))`
- Offline cap: `4h + warehouse level` (max 12h), paid at the base automated rate

## 🔨 Building

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew assembleRelease        # release build (add your signing config)
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.
Requirements: JDK 17+, Android SDK 36. GitHub Actions CI builds the APK on every push
(see `.github/workflows/android-ci.yml`).

## 📜 License

Open source, provided as-is for learning and enjoyment. Happy digging! ⛏️💎
