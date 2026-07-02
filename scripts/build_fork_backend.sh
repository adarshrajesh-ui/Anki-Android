#!/usr/bin/env bash
# build_fork_backend.sh — repoint AnkiDroid at the ankiCFA fork's Rust engine (rsdroid).
#
# STATUS: procedure captured from the upstream Anki-Android-Backend README plus the verified
# AnkiDroid `local_backend` hook (AnkiDroid/build.gradle:507-517). It was NOT executed during
# consolidation (needs Android NDK + Rust android targets; multi-hour cross-compile, network).
# Run it on a machine with the toolchain from MOBILE.md to produce a fork-engine build.
# See docs/prd/fork-backend-repoint.md for the full write-up.
set -euo pipefail

# ---- parameters (override via env) ----
: "${FORK_REMOTE:=$HOME/AlphaWeek2/ankiCFA}"   # ankiCFA fork with custom proto/ + rslib
: "${FORK_REF:=cfa/exam-queue-mvp}"            # fork branch carrying BuildExamQueue (confirm final branch)
: "${BACKEND_REPO:=https://github.com/ankidroid/Anki-Android-Backend}"
: "${ANDROID_HOME:=/opt/homebrew/share/android-commandlinetools}"

ANKIDROID_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PARENT_DIR="$(cd "$ANKIDROID_DIR/.." && pwd)"
BACKEND_DIR="$PARENT_DIR/Anki-Android-Backend"  # MUST be a sibling; name is hard-coded in AnkiDroid gradle
export ANDROID_HOME

echo "AnkiDroid : $ANKIDROID_DIR"
echo "Backend   : $BACKEND_DIR"
echo "Fork src  : $FORK_REMOTE @ $FORK_REF"

# 1. clone the JNI backend wrapper as a SIBLING of AnkiDroid (do not rename it) + submodule
if [ ! -d "$BACKEND_DIR/.git" ]; then
  git clone "$BACKEND_REPO" "$BACKEND_DIR"
fi
cd "$BACKEND_DIR"
git submodule update --init --recursive

# 2. point the bundled `anki` submodule at THIS fork and check out the fork branch
git -C anki remote add fork "$FORK_REMOTE" 2>/dev/null || git -C anki remote set-url fork "$FORK_REMOTE"
git -C anki fetch fork
git -C anki checkout "$FORK_REF" --recurse-submodules
cargo check --manifest-path anki/Cargo.toml || true   # refresh Cargo.lock; keep rust-toolchain.toml in sync

# 3. toolchain: rust android targets + NDK (version pinned by the backend repo)
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
cargo install toml-cli >/dev/null 2>&1 || true
ANDROID_NDK_VERSION="$(toml get gradle/libs.versions.toml versions.ndk --raw)"
sdkmanager --sdk_root="$ANDROID_HOME" --install "ndk;$ANDROID_NDK_VERSION"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$ANDROID_NDK_VERSION"

# 4. build the .aar (Android) + .jar (host, for Robolectric). RELEASE=1 => optimized.
RELEASE=1 ./build.sh
ls -la rsdroid/build/outputs/aar/rsdroid-release.aar \
       rsdroid-testing/build/libs/rsdroid-testing.jar

# 5. flip AnkiDroid to the local fork backend and re-run the build gate against the FORK engine
cd "$ANKIDROID_DIR"
grep -qx 'local_backend=true' local.properties 2>/dev/null || echo 'local_backend=true' >> local.properties
bash scripts/verify_mobile.sh

echo "Done: AnkiDroid built against the fork Rust engine (local_backend=true)."
echo "Next: add Kotlin pass-throughs for fork RPCs, e.g. col.backend.buildExamQueue(...)."
