# Source this before any emulator/adb work:  source .../adb_env.sh
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export ANDROID_SDK_ROOT="$ANDROID_HOME"
# Pinned Temurin 21 (for any gradle use)
for c in "$HOME"/.local/jdks/jdk-21*/Contents/Home "$HOME"/.local/jdks/jdk-21*; do
  [ -x "$c/bin/java" ] && { export JAVA_HOME="$c"; break; }
done
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:${JAVA_HOME:-}/bin:$PATH"
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
AVD_NAME="${AVD_NAME:-ankidroid_cfa}"
APP_ID="com.ichi2.anki.debug"
WORKTREE=/Users/adarshrajesh/wed/AnkiDroid-gnhf-worktrees/context-discipline-r-4edec4
APK="$WORKTREE/AnkiDroid/build/outputs/apk/play/debug/AnkiDroid-play-arm64-v8a-debug.apk"
SP=/private/tmp/claude-501/-Users-adarshrajesh-AlphaWeek2-ankiCFA/9c09416f-a791-48ee-b759-6f91e130ac84/scratchpad
DECK="$SP/cfa_level2.apkg"
export ADB EMULATOR AVDMANAGER SDKMANAGER AVD_NAME APP_ID WORKTREE APK SP DECK
