#!/usr/bin/env bash
set -uo pipefail
source /private/tmp/claude-501/-Users-adarshrajesh-AlphaWeek2-ankiCFA/9c09416f-a791-48ee-b759-6f91e130ac84/scratchpad/adb_env.sh
tapf(){ c=$(python3 "$SP/find_tap.py" "$1" "$2"|head -1); x=$(echo $c|awk '{print $1}'); y=$(echo $c|awk '{print $2}'); echo "tap '$2'->($x,$y)"; [ -n "$x" ] && "$ADB" shell input tap $x $y; }
dumpui(){ "$ADB" shell uiautomator dump /sdcard/ui.xml>/dev/null 2>&1; "$ADB" pull /sdcard/ui.xml "$1">/dev/null 2>&1; }
texts(){ grep -oE 'text="[^"]+"' "$1"|sed 's/text=//'|tr '\n' '|'; }

"$ADB" logcat -c 2>/dev/null
# Close any open menu, then back out to the DeckPicker.
"$ADB" shell input keyevent 4; sleep 1
for i in 1 2 3 4 5 6; do
  f=$("$ADB" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus)
  echo "$f" | grep -q "DeckPicker" && { echo "reached DeckPicker"; break; }
  "$ADB" shell input keyevent 4; sleep 1
  dumpui "$SP/cb.xml"; grep -q '"Later"' "$SP/cb.xml" && { tapf "$SP/cb.xml" Later; sleep 1; }
done
echo "focus: $("$ADB" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus)"
dumpui "$SP/c1.xml"; grep -q '"Later"' "$SP/c1.xml" && { tapf "$SP/c1.xml" Later; sleep 1; }
"$ADB" shell input tap 1022 212; sleep 1; dumpui "$SP/c2.xml"
echo "overflow: $(texts "$SP/c2.xml")"
tapf "$SP/c2.xml" "Check"; sleep 1; dumpui "$SP/c3.xml"
echo "submenu: $(texts "$SP/c3.xml")"
tapf "$SP/c3.xml" "Check database"; sleep 2; dumpui "$SP/c4.xml"
echo "after check-db tap: $(texts "$SP/c4.xml")"
if grep -qE '"Check"' "$SP/c4.xml"; then tapf "$SP/c4.xml" "Check"; fi
sleep 5
dumpui "$SP/c5.xml"
"$ADB" exec-out screencap -p > "$SP/shot_checkdb.png"
echo "RESULT: $(texts "$SP/c5.xml")"
echo "=== logcat ==="
"$ADB" logcat -d 2>/dev/null | grep -iE "check.?database|DatabaseCheck|problem|integrity|rebuild|optimiz|_anki/check|CollectionOp|no.*error" | tail -12
echo DONE
