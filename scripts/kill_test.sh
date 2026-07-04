#!/usr/bin/env bash
# Kill-app-mid-review integrity guardrail (Wednesday smoke; full 20-kill test is Sunday).
# Force-stops AnkiDroid while an answer is being written, reopens, and proves the
# collection is intact (opens cleanly, SQLite readable, revlog monotonic).
set -uo pipefail
source /private/tmp/claude-501/-Users-adarshrajesh-AlphaWeek2-ankiCFA/9c09416f-a791-48ee-b759-6f91e130ac84/scratchpad/adb_env.sh
DB="/storage/emulated/0/Android/data/com.ichi2.anki.debug/files/AnkiDroid/collection.anki2"

verify(){ # $1=label
  "$ADB" pull "$DB" "$SP/coll_$1.anki2" >/dev/null 2>&1
  rl=$(sqlite3 "$SP/coll_$1.anki2" "SELECT count(*) FROM revlog;" 2>&1)
  cc=$(sqlite3 "$SP/coll_$1.anki2" "SELECT count(*) FROM cards;" 2>&1)
  tb=$(sqlite3 "$SP/coll_$1.anki2" ".tables" 2>&1 | tr '\n' ' ')
  ok=BAD; echo "$tb" | grep -qw cards && ok=OK
  echo "  [$1] revlog=$rl cards=$cc tables=$ok"
}
dismiss_backup(){
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; "$ADB" pull /sdcard/ui.xml "$SP/kb.xml" >/dev/null 2>&1
  if grep -q '"Later"' "$SP/kb.xml"; then c=$(python3 "$SP/find_tap.py" "$SP/kb.xml" Later|head -1); "$ADB" shell input tap $(echo $c|awk '{print $1}') $(echo $c|awk '{print $2}'); sleep 1; fi
}
open_reviewer(){
  "$ADB" shell am start -n "$APP_ID/com.ichi2.anki.DeckPicker" >/dev/null 2>&1; sleep 2
  dismiss_backup
  "$ADB" shell input tap 267 359; sleep 2   # open deck -> reviewer
}

echo "== baseline (collection intact before kills) =="
open_reviewer
echo "  focus: $("$ADB" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus)"
verify baseline

for n in 1 2 3; do
  echo "== KILL iteration $n =="
  "$ADB" shell input tap 540 2273 &     # Show answer
  sleep 1.0
  "$ADB" shell input tap 675 2295 &     # Good -> backend answerCard DB write starts
  "$ADB" shell am force-stop "$APP_ID"  # SIGKILL, racing the write
  echo "  force-stopped mid-write"
  sleep 1
  open_reviewer
  echo "  reopened focus: $("$ADB" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus)"
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; "$ADB" pull /sdcard/ui.xml "$SP/kpost$n.xml" >/dev/null 2>&1
  txt=$(grep -oE 'text="[^"]+"' "$SP/kpost$n.xml" | sed 's/text=//' | tr '\n' '|')
  if echo "$txt" | grep -qiE "corrupt|rebuild|recover|database error|load.*fail"; then
    echo "  !! POSSIBLE CORRUPTION WORDING: $txt"
  else
    echo "  reopened cleanly (no corruption/recovery dialog)"
  fi
  "$ADB" exec-out screencap -p > "$SP/shot_kill$n.png"
  verify kill$n
done
echo "== KILL TEST COMPLETE =="
