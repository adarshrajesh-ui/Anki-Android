#!/usr/bin/env bash
set -uo pipefail
source /private/tmp/claude-501/-Users-adarshrajesh-AlphaWeek2-ankiCFA/9c09416f-a791-48ee-b759-6f91e130ac84/scratchpad/adb_env.sh
tapf(){ c=$(python3 "$SP/find_tap.py" "$1" "$2"|head -1); x=$(echo $c|awk '{print $1}'); y=$(echo $c|awk '{print $2}'); echo "tap '$2'->($x,$y)"; [ -n "$x" ] && "$ADB" shell input tap $x $y; }
dumpui(){ "$ADB" shell uiautomator dump /sdcard/ui.xml>/dev/null 2>&1; "$ADB" pull /sdcard/ui.xml "$1">/dev/null 2>&1; }
"$ADB" shell am start -n "$APP_ID/com.ichi2.anki.DeckPicker">/dev/null 2>&1; sleep 2
dumpui "$SP/s1.xml"; grep -q '"Later"' "$SP/s1.xml" && { tapf "$SP/s1.xml" Later; sleep 1; }
"$ADB" shell input swipe 267 359 267 359 700; sleep 1; dumpui "$SP/s2.xml"
tapf "$SP/s2.xml" "Browse cards"; sleep 2; dumpui "$SP/s3.xml"
tapf "$SP/s3.xml" "More options"; sleep 1; dumpui "$SP/s4.xml"
tapf "$SP/s4.xml" "Select all"; sleep 1
"$ADB" shell input tap 1027 211; sleep 1; dumpui "$SP/s5.xml"
tapf "$SP/s5.xml" "Set due date"; sleep 1; dumpui "$SP/s6.xml"
"$ADB" shell input tap 539 917; "$ADB" shell input text "0"; sleep 1
OK=$(python3 - "$SP/s6.xml" <<'PY'
import re,sys
xml=open(sys.argv[1]).read()
for tag in re.findall(r'<node[^>]*?>',xml):
    t=re.search(r'text="([^"]*)"',tag)
    if t and t.group(1)=="OK":
        b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',tag)
        if b: x1,y1,x2,y2=map(int,b.groups());print((x1+x2)//2,(y1+y2)//2);break
PY
)
echo "OK->$OK"; [ -n "$OK" ] && "$ADB" shell input tap $(echo $OK|awk '{print $1}') $(echo $OK|awk '{print $2}'); sleep 2
for i in 1 2 3 4; do f=$("$ADB" shell dumpsys window 2>/dev/null|grep -m1 mCurrentFocus); echo "$f"|grep -q DeckPicker && break; "$ADB" shell input keyevent 4; sleep 1; done
dumpui "$SP/s7.xml"; grep -q '"Later"' "$SP/s7.xml" && { tapf "$SP/s7.xml" Later; sleep 1; dumpui "$SP/s7.xml"; }
echo "deck due: $(grep -oE 'text="[^"]+"' "$SP/s7.xml"|sed 's/text=//'|grep -iE 'due' | head -2)"
echo SETDUE_DONE
