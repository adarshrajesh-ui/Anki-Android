#!/usr/bin/env bash
# build_fork_engine_apk.sh
# Build the AnkiDroid debug APK against the LOCAL ankiCFA fork-engine backend
# (rsdroid-release.aar), instead of the published Maven backend. This makes the
# fork-only RPC `build_exam_queue` callable on-device.
#
# Prereq: the sibling ../Anki-Android-Backend has been built against the fork via
#         its `cfa_build_fork_engine.sh` (so rsdroid-release.aar exists).
#
# Verified 2026-07-02 on Apple Silicon macOS.
set -euo pipefail

AD="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$AD"

export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

AAR="../Anki-Android-Backend/rsdroid/build/outputs/aar/rsdroid-release.aar"
[ -f "$AAR" ] || { echo "ERROR: $AAR not found — build the backend first (cfa_build_fork_engine.sh)"; exit 1; }

# local-backend wiring: build.gradle switches to the sibling AAR when local_backend=true.
# local.properties is .gitignored, so it must be (re)created here.
touch local.properties
grep -q '^sdk.dir='       local.properties || echo "sdk.dir=$ANDROID_HOME" >> local.properties
grep -qx 'local_backend=true' local.properties || echo 'local_backend=true' >> local.properties
echo "== local.properties =="; cat local.properties

./gradlew assembleFullDebug -Dorg.gradle.java.home="$JAVA_HOME"

echo
echo "== APKs =="
ls -la AnkiDroid/build/outputs/apk/full/debug/*.apk
echo "== confirm fork engine is inside the arm64 APK =="
APK="$(ls AnkiDroid/build/outputs/apk/full/debug/*arm64-v8a*.apk | head -1)"
unzip -l "$APK" | grep -E 'librsdroid\.so' || echo "WARN: librsdroid.so not found in APK"
