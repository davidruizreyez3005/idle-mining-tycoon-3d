# 03 — Directory Structure

```
mining-tycoon-3d/
├── .github/workflows/
│   └── android-release.yml          CI: build + test + release APK (docs/06)
├── docs/                            Design docs (this set)
├── gradle/
│   ├── wrapper/                     Gradle 8.14.3 wrapper
│   └── libs.versions.toml           Version catalog (single source of dependency truth)
├── app/
│   ├── proguard-rules.pro           Empty for now — R8 lands in Phase 12
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml  Single-activity, fullscreen, no permissions
│       │   ├── assets/gamedata/     THE data-driven content (docs/04)
│       │   │   ├── resources.json   Resources: ids, values, colors, rarity
│       │   │   ├── mine_nodes.json  Node types: hp, yields, respawn, mesh
│       │   │   ├── upgrades.json    Upgrades: effects, costs, growth, caps
│       │   │   ├── economy.json     Tuning: start money, speeds, radii, offline cap
│       │   │   └── world.json       Zone layout: ground, cliff, nodes, depot, props
│       │   ├── java/com/idlemining/tycoon3d/
│       │   │   ├── MainActivity.kt          Immersive fullscreen host
│       │   │   ├── core/                    PURE KOTLIN (JVM-testable, no Android)
│       │   │   │   ├── content/
│       │   │   │   │   ├── ContentSchema.kt   @Serializable DTOs for all 5 files
│       │   │   │   │   ├── ContentLoader.kt   Parse + validate + color parsing
│       │   │   │   │   ├── GameContent        (in ContentLoader.kt) resolved repo
│       │   │   │   │   └── AndroidContent.kt  Thin assets→loader bridge
│       │   │   │   ├── economy/
│       │   │   │   │   └── EconomyRules.kt    Every formula + formatters
│       │   │   │   ├── save/
│       │   │   │   │   ├── SaveSchema.kt      SaveData DTO (versioned)
│       │   │   │   │   ├── SaveMigrations.kt  v0→v1 chain + decodeSave()
│       │   │   │   │   └── SaveStorage.kt     Interface + File/InMemory impls
│       │   │   │   └── providers/
│       │   │   │       └── AssetProviders.kt  AI pipeline seams + manifest schema
│       │   │   ├── game/
│       │   │   │   ├── GameState.kt     Immutable runtime state + node/worker types
│       │   │   │   ├── GameEvents.kt    Intents (in) + events (out)
│       │   │   │   ├── Simulation.kt    Pure kernel: tick + reduce + save mapping
│       │   │   │   └── GameEngine.kt    Clock, tick loop, autosave, offline boot
│       │   │   ├── world/
│       │   │   │   ├── KitCatalog.kt    Procedural kit pieces (data) + palette
│       │   │   │   └── WorldBuilder.kt  Composes the zone from world.json
│       │   │   ├── scene/
│       │   │   │   ├── GameScene.kt     SceneView composable + tap handling
│       │   │   │   ├── SceneRefs.kt     Captured node references (animator targets)
│       │   │   │   ├── SceneAnimator.kt Per-frame animation + FX (pooled)
│       │   │   │   └── NodeRegistry.kt  Node → tappable entity resolution
│       │   │   ├── viewmodel/
│       │   │   │   └── GameViewModel.kt MVVM glue (engine ↔ Compose)
│       │   │   └── ui/
│       │   │       ├── GameScreen.kt    Scene + HUD + dialogs assembly
│       │   │       ├── theme/           Color + Material3 theme
│       │   │       └── components/      TopHud, BottomBar, MiningProgress,
│       │   │                            UpgradePanel, OfflineDialog, HintOverlay
│       │   └── res/                    Launcher icon, theme, strings
│       └── test/java/com/idlemining/tycoon3d/
│           ├── TestContent.kt          Real JSON bundle fixture
│           ├── ContentLoaderTest.kt    Parsing + validation gates
│           ├── EconomyRulesTest.kt     Every formula
│           ├── SaveMigrationTest.kt    v0→v1 + roundtrip + future files
│           └── SimulationTest.kt       The whole game loop, headless
```

## Conventions

- **Package = layer, not feature-slice** — keeps the dependency rule visible:
  `core ← game ← scene/world ← viewmodel ← ui`, never reversed.
- **One file = one responsibility** — no god files; `Simulation.kt` is the
  largest (~470 lines) and still only game math.
- **No magic numbers in logic** — tuning lives in `economy.json` (speeds, radii,
  caps) or named constants at the top of the file (pool sizes, tick rates).
- **Tests mirror the source tree** and use the same content loader the game uses
  (via JSON literals), so schema drift breaks CI, not players.

## Extraction path (future modules)

When the codebase grows, the same packages become Gradle modules without moving
files: `core/` → `:core` (pure Kotlin), `game/` → `:game` (depends on `:core`),
`scene|world` → `:engine3d`, `ui|viewmodel` → `:app`. The dependency rules above
are exactly the module boundaries.
