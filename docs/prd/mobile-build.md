# PRD: AnkiDroid Mobile Build De-risk

Status: DONE — build gate PASS (`verify_mobile.sh`); emulator review **captured** (real CFA
Level II review on the Anki Rust backend + recording + kill-mid-review integrity). See
`MOBILE.md` and `docs/mobile-proof/PROOF.md`. Owner: mobile. Branch: `gnhf/context-discipline-r-4edec4`.

## Goal

De-risk (for Wednesday, ungraded): get the AnkiDroid Android app (this AGPL repo)
building a **debug APK** and passing **JVM unit tests** against the bundled Anki Rust
backend, then — best-effort only — run one real review session on a sample deck in an
emulator. Two-way sync is explicitly **not** required.

## Scope (in)

- Install and pin a reproducible toolchain: JDK 21, Android SDK (cmdline-tools,
  platform-tools, `platforms;android-36`, matching build-tools), licenses accepted.
- `local.properties` (or `ANDROID_HOME`) pointing Gradle at the SDK.
- `./gradlew assembleDebug` produces a debug APK; record its exact path.
- Run the fast JVM unit test suite (Robolectric-based) and get it green.
- `scripts/verify_mobile.sh`: runs the debug assemble + unit tests, asserts the debug
  APK exists, prints `PASS`/`FAIL`, exits non-zero on any failure. This is the DONE gate.
- `MOBILE.md`: exact reproducible steps, pinned toolchain versions, APK path.
- `scripts/run_emulator_review.sh`: manual steps to boot an emulator, install the APK,
  open the sample deck, and run a review (for a human to run by hand).

## Scope (out)

- Two-way / AnkiWeb sync, account login, cloud collections.
- Release / signed / Play Store builds; fastlane; ABI-split or universal APK tuning.
- NDK / Rust-from-source compilation — the Anki backend ships as a prebuilt dependency;
  no `externalNativeBuild` exists in the app module, so NDK is not required for debug.
- Instrumented (`connectedAndroidTest`) suites; full-matrix CI.
- Modifying app features, themes, settings, or unrelated modules ("while I'm in there").
- Automated/headless emulator review in CI — if it will not boot unattended, we fall
  back to the debug build + unit tests and leave emulator steps as a manual script.

## Interfaces / key facts

- Build system: Gradle 9.5.0 wrapper (`./gradlew`), AGP 9.0.1, Kotlin 2.3.21.
- Daemon JVM criteria (`gradle/gradle-daemon-jvm.properties`): vendor JETBRAINS,
  version 21. `foojay-resolver-convention` is enabled, so Gradle auto-downloads the
  daemon JVM if a matching one is not installed locally.
- SDK levels (`gradle/libs.versions.toml`): compileSdk 36, targetSdk 35, minSdk 24.
- App module: `:AnkiDroid`; product flavors exist (e.g. `play`), so the debug variant
  task is likely `assemblePlayDebug`; the fast unit-test task is
  `testPlayDebugUnitTest`. Confirm exact task/APK names during the build slice.
- Backend: Anki Rust backend consumed as a prebuilt artifact (no local Rust build).
- Reference docs: `CONTRIBUTING.md`, `docs/development/gradle-daemon-jvm.md`.

## Done-check

1. `bash scripts/verify_mobile.sh` exits `0` and prints `PASS`.
2. A debug APK exists at the recorded path (`AnkiDroid/build/outputs/apk/.../*-debug.apk`).
3. `MOBILE.md` lists exact steps + pinned toolchain versions + the APK path, and is
   reproducible from a clean checkout with the toolchain installed.
4. Emulator review is either captured (recording) or documented as skipped with the
   reason, plus `scripts/run_emulator_review.sh` for manual execution.

## Approach (sliced)

1. Toolchain: install JDK 21 + Android SDK + licenses; record versions. ← current
2. First build: run `./gradlew assembleDebug` (resolve flavor/task names), fix blockers.
3. Unit tests: run the fast JVM suite green.
4. `scripts/verify_mobile.sh` + `MOBILE.md`; run the script until it exits 0.
5. Emulator (best-effort) or `scripts/run_emulator_review.sh` + documented skip.
