#!/usr/bin/env bash
# verify_mobile.sh — AnkiDroid debug-build verification.
# Runs the debug assemble + JVM unit tests, asserts a debug APK exists.
# Exits 0 and prints PASS only if the build succeeds, tests pass, and an APK is found.
# See MOBILE.md for toolchain versions and reproducible setup.
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
export PATH="${JAVA_HOME:-}/bin:$PATH"

echo "== verify_mobile.sh =="
echo "JAVA_HOME=${JAVA_HOME:-<unset>}"
echo "ANDROID_HOME=${ANDROID_HOME:-<unset>}"

# App module :AnkiDroid uses product flavors (play/amazon/full).
# Debug build task and fast JVM unit-test task for the play flavor:
ASSEMBLE_TASK="assemblePlayDebug"
TEST_TASK="testPlayDebugUnitTest"
APK_GLOB="AnkiDroid/build/outputs/apk/play/debug/*-debug.apk"

fail() { echo "RESULT: FAIL — $1"; exit 1; }

# The build hard-codes maxParallelForks = physical CPU count for unit tests AND turns
# on intra-fork JUnit concurrent execution (junit.jupiter.execution.parallel.enabled).
# On a many-core dev box that is forks × cores threads (e.g. 14×14), which oversubscribes
# the CPU so heavily that the coroutine `runTest` bodies exceed their 1-minute dispatch
# timeout and fail with UncompletedCoroutinesError. Capping Gradle's worker pool bounds
# the number of concurrent test forks (maxParallelForks is limited by --max-workers), so a
# single fork gets the whole machine and the tests complete well within the timeout.
# Override with GRADLE_MAX_WORKERS if needed.
GRADLE_MAX_WORKERS="${GRADLE_MAX_WORKERS:-1}"
echo "== ./gradlew --max-workers=$GRADLE_MAX_WORKERS $ASSEMBLE_TASK $TEST_TASK =="
./gradlew --no-daemon --max-workers="$GRADLE_MAX_WORKERS" "$ASSEMBLE_TASK" "$TEST_TASK" \
  || fail "gradle build/test task failed"

# Assert the debug APK exists.
shopt -s nullglob
apks=( $APK_GLOB )
if [ ${#apks[@]} -eq 0 ]; then
  fail "no debug APK found matching $APK_GLOB"
fi

echo "APK(s) produced:"
for a in "${apks[@]}"; do
  echo "  $a"
done

echo "RESULT: PASS"
exit 0
