# 06 — Build & Release Pipeline

## Overview

Development happens from a phone; the repository is the source of truth; GitHub
Actions is the build farm. Every push to `main` produces a tested, installable
APK attached to a GitHub release — the "always playable" rule made mechanical.

```
git push (from phone or any client)
  │
  ▼
GitHub Actions: android-release.yml
  ├── checkout
  ├── JDK 17 (temurin)
  ├── ./gradlew testDebugUnitTest assembleDebug assembleRelease
  ├── upload both APKs as workflow artifacts
  └── (main only) recreate the v0.1.0 release with the release APK
        │
        ▼
GitHub Release — install from the phone's browser, test, iterate
```

## Release strategy

- **Release tag**: `v0.1.0` for the whole vertical-slice milestone. Each push to
  `main` **replaces** the release's assets (delete + recreate) so there is always
  exactly one canonical "latest slice build" — no release spam.
- **Signing**: the release build is signed with the debug key
  (`signingConfig = debug`). That keeps the APK sideloadable on any phone with
  zero secret management. A real keystore (base64 in secrets, `key.properties`
  generated in CI) replaces this before any store submission — Phase 12.
- **Versioning**: `versionCode 1 / versionName 0.1.0` for the slice; bump both
  per milestone (the release job stamps the run id into the release notes).
- **No minification yet** (documented decision — docs/01 §6).

## Workflow details (`.github/workflows/android-release.yml`)

- **Triggers**: `push` to `main` + pull requests. The release step runs only on
  `push` to `main` — PRs build and test but never publish.
- **Memory budget**: the workflow pins
  `org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=640m` via the repo's
  `gradle.properties` (2-core runners are tight; Kotlin compiles in-process).
- **Concurrency**: a per-ref concurrency group cancels superseded runs, keeping
  the queue fast during bursty pushes.
- **Loop prevention**: the release job does **not** commit anything back to the
  repo (artifacts live in the release, not in git), so no `[skip ci]` guards or
  path filters are needed.
- **Authentication**: the release job uses the built-in `GITHUB_TOKEN` — no
  personal token ever lives in the repo or workflow.

## Local builds (fallback, not the primary path)

```bash
export JAVA_HOME=<jdk-17>
export ANDROID_HOME=<sdk with platforms;android-36 + build-tools;36.0.0>
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

Toolchain pinned by `gradle/libs.versions.toml` + wrapper:
AGP 8.13.2 · Kotlin 2.4.20 · Gradle 8.14.3 · compileSdk 36 · minSdk 24 ·
Compose BOM 2026.06.01 · SceneView 4.34.0.

## Verification loop (phone-only)

1. Push (or merge a PR) to `main`.
2. Watch the run from the GitHub mobile app (Actions tab).
3. On success: Releases → `v0.1.0` → download `MiningTycoon3D-v0.1.0.apk`.
4. Install (allow "unknown sources" for the browser once).
5. Play the loop: mine → collect → sell → upgrade → idle → close/reopen.

## Quality gates in CI

| Gate | Enforced by |
|------|-------------|
| Content schemas valid | `ContentLoaderTest` (parsing + validation gates) |
| Economy math exact | `EconomyRulesTest` (formulas, caps, formatting) |
| Save compatibility | `SaveMigrationTest` (v0→v1, roundtrip, future files) |
| Game loop correct | `SimulationTest` (mine/break/collect/sell/idle/offline, headless) |
| Compiles + packages | `assembleDebug` + `assembleRelease` |

37 tests, all JVM-pure, running in seconds — the suite is deliberately sized to
run on every push.
