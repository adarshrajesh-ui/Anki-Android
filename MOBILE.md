# MOBILE.md — AnkiDroid debug build (reproducible steps)

De-risk build of the AnkiDroid Android app against the bundled Anki Rust backend.
See `docs/prd/mobile-build.md` for scope. Two-way sync is out of scope.

## Toolchain (pinned, this machine)

| Component | Version | Location |
|-----------|---------|----------|
| macOS | Darwin 24.6.0 (arm64) | — |
| JDK (build) | Temurin 21.0.11+10 (LTS) | `~/.local/jdks/jdk-21.0.11+10/Contents/Home` |
| Gradle daemon JVM criteria | JETBRAINS 21 (auto via foojay-resolver) | auto-downloaded by Gradle |
| Gradle wrapper | 9.5.0 | `./gradlew` |
| Android Gradle Plugin | 9.0.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.3.21 | `gradle/libs.versions.toml` |
| Android cmdline-tools | latest (Homebrew cask) | `/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest` |
| SDK Platform | android-36 (compileSdk 36) | `$ANDROID_HOME/platforms/android-36` |
| SDK Build-Tools | 36.0.0 | `$ANDROID_HOME/build-tools/36.0.0` |
| SDK Platform-Tools | r37.0.0 | `$ANDROID_HOME/platform-tools` |
| minSdk / targetSdk | 24 / 35 | `gradle/libs.versions.toml` |

App module: `:AnkiDroid`. Flavors: `play`, `amazon`, `full`.
Debug variant task: `assemblePlayDebug`; fast unit tests: `testPlayDebugUnitTest`.

## One-time setup (from a clean machine)

```bash
# 1. JDK 21 (no sudo) — Temurin tarball into a user dir
mkdir -p ~/.local/jdks && cd ~/.local/jdks
curl -fsSL -o t.tgz "https://api.adoptium.net/v3/binary/latest/21/ga/mac/aarch64/jdk/hotspot/normal/eclipse?project=jdk"
tar xzf t.tgz && rm t.tgz

# 2. Android cmdline-tools
brew install --cask android-commandlinetools     # installs to /opt/homebrew/share/android-commandlinetools

# 3. Env
export JAVA_HOME="$HOME/.local/jdks/jdk-21.0.11+10/Contents/Home"
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$PATH"

# 4. SDK packages + licenses
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-36" "build-tools;36.0.0"

# 5. Point Gradle at the SDK (repo root; git-ignored)
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

Note: the Homebrew `temurin@21` cask requires `sudo`; the no-sudo tarball above is used
here instead. Gradle's daemon JVM criteria (vendor JETBRAINS, version 21) is satisfied
automatically by the `foojay-resolver-convention` plugin, which downloads a matching JVM
on first build — the Temurin 21 above is the launcher/toolchain JDK.

## Build + test

```bash
export JAVA_HOME="$HOME/.local/jdks/jdk-21.0.11+10/Contents/Home"
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$PATH"

bash scripts/verify_mobile.sh    # assembleDebug + unit tests + APK assertion; prints PASS/FAIL
```

## Debug APK path

`assemblePlayDebug` emits per-ABI splits (no universal APK) under
`AnkiDroid/build/outputs/apk/play/debug/`:

| ABI | File | Size |
|-----|------|------|
| arm64-v8a | `AnkiDroid-play-arm64-v8a-debug.apk` | ~72 MB |
| armeabi-v7a | `AnkiDroid-play-armeabi-v7a-debug.apk` | ~68 MB |
| x86_64 | `AnkiDroid-play-x86_64-debug.apk` | ~75 MB |
| x86 | `AnkiDroid-play-x86-debug.apk` | ~74 MB |

applicationId `com.ichi2.anki.debug`, versionName `2.25.0alpha1-debug`, versionCode `22500101`.
Install the split matching the target device/emulator ABI (arm64-v8a for Apple-silicon /
modern ARM emulators; x86_64 for Intel emulator images).

## Emulator review (COMPLETED this session)

A real review session WAS run on-device this session — full detail + artifacts in
`docs/mobile-proof/PROOF.md`, recording at `docs/mobile-proof/cfa_review_session.mp4`.
Contrary to the earlier assumption, a headless emulator (`-no-window -gpu swiftshader_indirect`,
API 34 arm64) booted reliably in ~6 s.

- Booted AVD `ankidroid_cfa` (Pixel 6, Android 14, arm64-v8a); installed the arm64-v8a debug APK.
- Imported a 14-card **CFA Level II** deck (`docs/mobile-proof/cfa_level2.apkg`) via the Rust
  backend importer (`POST /_anki/importAnkiPackage` → "14 new notes imported").
- Ran a real review on the Anki Rust FSRS scheduler (Again/Hard/Good/Easy incl. relearns);
  **45 review-log entries persisted across the 14 cards** (verified in `collection.anki2`).
- Recording: `docs/mobile-proof/cfa_review_session.mp4` (H.264 920×2048, 73.6 s), captured with
  `scrcpy` (host-side muxing — on-device `screenrecord` would not finalize under `input`-driven load).
- Guardrails: cold start ~1.4–1.7 s (emulator, `am start -W`); kill-app-mid-review ×3 →
  collection intact + reopens cleanly + "Database rebuilt and optimized" (Rust `check_database`).

`scripts/run_emulator_review.sh` (manual, sample deck) plus the scripts used this session
(`review_scrcpy.sh`, `kill_test.sh`, `set_due_full.sh`, `make_cfa_deck.py`, `adb_env.sh`) are in `scripts/`.

## Status

- [x] Toolchain installed + pinned (JDK 21, Android SDK 36, licenses accepted).
- [x] `./gradlew assembleDebug` (assemblePlayDebug) produces a debug APK (4 per-ABI splits above).
- [x] JVM unit tests green (`testPlayDebugUnitTest`: 1868 tests, 0 failed, 39 skipped).
- [x] `scripts/verify_mobile.sh` exits 0 / prints PASS (full run ~5m; cached rerun instant).
- [x] Emulator review **completed**: headless AVD booted, CFA Level II deck imported, real review
  run on the Anki Rust scheduler, screen recording captured, kill-mid-review integrity verified.
  See `docs/mobile-proof/PROOF.md`.

### Local test note

`DeckPickerContextMenuTest` does not extend `RobolectricTest`, so the rsdroid backend native
library is not loaded into its Robolectric sandbox automatically. After the upstream deck-picker
"use backend string" refactors, its context-menu option labels resolve backend translation
strings during dialog creation, which requires the backend. On Linux CI the library happens to
be shared across sandboxes; on macOS/arm64 (JBR 21) it is not, so 5 tests failed with
`UnsatisfiedLinkError: NativeMethods.openBackend`. Fixed by calling `RustBackendLoader.ensureSetup()`
in the test's `@Before` (idempotent, matches how `RobolectricTest` loads it) — no product code changed.
