#!/usr/bin/env bash
# run_emulator_review.sh — manual, best-effort emulator review of AnkiDroid.
#
# Boots an Android emulator, installs the debug APK built by verify_mobile.sh,
# launches AnkiDroid, and leaves it open so you can open the bundled sample deck
# and run a real review session against the Anki Rust backend.
#
# This is NOT run automatically in the de-risk build: a headless emulator does not
# boot reliably unattended, so run this by hand on a machine with a display.
# See MOBILE.md for toolchain versions. Requires the debug APK to already exist
# (run `bash scripts/verify_mobile.sh` first).
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# --- Toolchain: prefer an already-exported JAVA_HOME, else the pinned Temurin 21 ---
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME:-}/bin/java" ]; then
  for candidate in "$HOME"/.local/jdks/jdk-21*/Contents/Home "$HOME"/.local/jdks/jdk-21*; do
    if [ -x "$candidate/bin/java" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d /opt/homebrew/share/android-commandlinetools ]; then
  export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
fi
export PATH="${JAVA_HOME:-}/bin:${ANDROID_HOME:-}/platform-tools:${ANDROID_HOME:-}/emulator:${ANDROID_HOME:-}/cmdline-tools/latest/bin:$PATH"

SDKMANAGER="${ANDROID_HOME:-}/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="${ANDROID_HOME:-}/cmdline-tools/latest/bin/avdmanager"
EMULATOR="${ANDROID_HOME:-}/emulator/emulator"
ADB="${ANDROID_HOME:-}/platform-tools/adb"

# AVD/system-image config. arm64 host (Apple silicon) -> arm64-v8a image + APK.
AVD_NAME="${AVD_NAME:-ankidroid_review}"
API="${API:-34}"
HOST_ARCH="$(uname -m)"
if [ "$HOST_ARCH" = "arm64" ] || [ "$HOST_ARCH" = "aarch64" ]; then
  ABI="arm64-v8a"
else
  ABI="x86_64"
fi
SYS_IMAGE="system-images;android-${API};google_apis;${ABI}"
APK="AnkiDroid/build/outputs/apk/play/debug/AnkiDroid-play-${ABI}-debug.apk"
APP_ID="com.ichi2.anki.debug"

echo "== run_emulator_review.sh =="
echo "JAVA_HOME=${JAVA_HOME:-<unset>}"
echo "ANDROID_HOME=${ANDROID_HOME:-<unset>}"
echo "AVD=$AVD_NAME  API=$API  ABI=$ABI"
echo "APK=$APK"

fail() { echo "ERROR: $1" >&2; exit 1; }

[ -n "${ANDROID_HOME:-}" ] || fail "ANDROID_HOME not set and SDK not found."
[ -f "$APK" ] || fail "debug APK not found at $APK — run 'bash scripts/verify_mobile.sh' first."

# 1. System image + AVD (idempotent).
echo "== ensuring system image + AVD =="
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "$SYS_IMAGE" || fail "could not install $SYS_IMAGE"
if ! "$AVDMANAGER" list avd 2>/dev/null | grep -q "Name: ${AVD_NAME}$"; then
  echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYS_IMAGE" --device "pixel_6" \
    || fail "could not create AVD $AVD_NAME"
fi

# 2. Boot the emulator (background) and wait for it.
echo "== booting emulator (this needs a display; do not run headless unattended) =="
"$EMULATOR" -avd "$AVD_NAME" -no-snapshot -no-boot-anim &
EMU_PID=$!
trap 'echo "(leaving emulator $EMU_PID running for manual review)"' EXIT

echo "== waiting for device =="
"$ADB" wait-for-device
# Wait for full boot.
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  sleep 3
done
echo "== device booted =="

# 3. Install + launch.
echo "== installing $APK =="
"$ADB" install -r -g "$APK" || fail "adb install failed"
echo "== launching $APP_ID =="
"$ADB" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null

cat <<EOF

==============================================================================
AnkiDroid is installed and launched on emulator '$AVD_NAME'.

Manual review steps:
  1. On first launch, accept the intro / grant storage.
  2. AnkiDroid ships a sample "Default" deck with a few cards. If empty, use
     the deck picker overflow -> "Get shared decks" or add a note, then Study.
  3. Tap the deck -> "Study Now" and answer a card (Again/Hard/Good/Easy) to
     exercise a real review session on the Anki Rust backend.
  4. Optional recording (macOS):  xcrun simctl is iOS-only; for Android use:
       $ADB shell screenrecord /sdcard/review.mp4   # Ctrl-C to stop
       $ADB pull /sdcard/review.mp4
==============================================================================
EOF
