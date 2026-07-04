# Fork backend repoint — AnkiDroid → ankiCFA Rust engine

Consolidation of the mobile stream ("Agent 9"). Recovered from the GNHF worktree
`AnkiDroid-gnhf-worktrees/context-discipline-r-4edec4` (branch
`gnhf/context-discipline-r-4edec4`) after the stated source dir
`~/wed/baseline-fleet/agent9/` went missing.

## Status (honest)

| Part | State |
|------|-------|
| AnkiDroid debug build + unit tests + on-device review on the **stock upstream** Anki Rust backend | **DONE & verified** by the mobile agent — see `MOBILE.md`, `docs/mobile-proof/PROOF.md`, and the recording `docs/mobile-proof/cfa_review_session.mp4`. Reproducible now via `scripts/verify_mobile.sh`. |
| Repoint AnkiDroid at the **fork's** Rust engine build (custom RPCs) | **NOT executed.** Procedure below + `scripts/build_fork_backend.sh`. Verified statically only (build hook + upstream README). |

No fabricated "done" state: `local.properties` on this branch does **not** set
`local_backend=true`, so the tree still builds against the stock published dependency and
stays green. The fork cross-compile was deliberately deferred (needs Android NDK + Rust
android targets; multi-hour, network) — exactly as documented in `docs/mobile-proof/PROOF.md`.

## Current backend wiring (verified in this repo)

- Version catalog: `ankiBackend = '0.1.64-anki25.09.2'` (`gradle/libs.versions.toml:73`) — upstream Anki 25.09.2.
- `AnkiDroid/build.gradle:507-517` switches on the `local_backend` property:
  - `local_backend=true` → `files(../Anki-Android-Backend/rsdroid/build/outputs/aar/rsdroid-release.aar)` + `.../rsdroid-testing/build/libs/rsdroid-testing.jar`
  - otherwise → published `io.github.david-allison:anki-android-backend` (**current state**).
- `Anki-Android-Backend` must be a **sibling** of this repo and keep that exact name (hard-coded in the gradle path above).

## Reproducible repoint procedure

Prereqs: the toolchain in `MOBILE.md` (JDK 21, Android SDK 36, cmdline-tools) + Rust
(`rustup`) + a C toolchain (Xcode Command Line Tools).

1. Clone the JNI backend wrapper as a **sibling** of this repo and fetch its submodule:
   ```bash
   git clone https://github.com/ankidroid/Anki-Android-Backend ../Anki-Android-Backend
   cd ../Anki-Android-Backend && git submodule update --init --recursive
   ```
2. Point the bundled `anki` submodule at the **fork** and check out the branch carrying the
   custom engine (`cfa/exam-queue-mvp` → `SchedulerService.BuildExamQueue`,
   `proto/anki/scheduler.proto` + `rslib/src/scheduler/service/mod.rs`):
   ```bash
   cd anki
   git remote add fork "$HOME/AlphaWeek2/ankiCFA"
   git fetch fork && git checkout cfa/exam-queue-mvp --recurse-submodules
   cd .. && cargo check          # refresh Cargo.lock; keep rust-toolchain.toml in sync
   ```
3. Install NDK + Rust android targets and build the library (NDK version comes from the
   backend's own `gradle/libs.versions.toml` key `versions.ndk`):
   ```bash
   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
   cargo install toml-cli
   ANDROID_NDK_VERSION=$(toml get gradle/libs.versions.toml versions.ndk --raw)
   export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
   export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$ANDROID_NDK_VERSION"
   sdkmanager --sdk_root="$ANDROID_HOME" --install "ndk;$ANDROID_NDK_VERSION"
   RELEASE=1 ./build.sh          # emits rsdroid-release.aar + rsdroid-testing.jar
   ```
4. Repoint AnkiDroid at the freshly built fork engine and re-run the build gate:
   ```bash
   cd -                          # back to the AnkiDroid repo
   echo 'local_backend=true' >> local.properties
   bash scripts/verify_mobile.sh # now builds + unit-tests against the FORK engine
   ```
5. Surface fork RPCs in Kotlin as thin pass-throughs, mirroring AnkiDroid's existing backend
   delegation (`Scheduler.kt` is a pass-through over the generated `Backend`), e.g.:
   ```kotlin
   fun buildExamQueue(/* ... */) = col.backend.buildExamQueue(/* ... */)
   ```

`scripts/build_fork_backend.sh` automates steps 1–4 (idempotent; parameterised by `FORK_REF`).

## Verified during consolidation vs. not

- **Verified:** the `local_backend` hook exists and is correct (`AnkiDroid/build.gradle:507-517`);
  the repoint flow matches the upstream Anki-Android-Backend README ("Testing with a specific
  version of anki"); the stock-backend build artifacts exist (4 debug APKs, `output-metadata.json`)
  and unit tests were green (`MOBILE.md` / `PROOF.md`); `scripts/*.sh` parse (`bash -n`).
- **Not verified here** (needs the cross-compile toolchain, ~hours, network): the actual fork
  `rsdroid-release.aar` build and a running fork-engine APK.

## Fork branch note

The fork's custom-RPC branch is being consolidated in parallel by the **desktop** stream in
`~/AlphaWeek2/ankiCFA` (this consolidation deliberately does not touch that repo). Confirm the
final consolidated branch name there and pass it as `FORK_REF` to
`scripts/build_fork_backend.sh` (documented default: `cfa/exam-queue-mvp`).
