#!/usr/bin/env bash
set -uo pipefail
source /private/tmp/claude-501/-Users-adarshrajesh-AlphaWeek2-ankiCFA/9c09416f-a791-48ee-b759-6f91e130ac84/scratchpad/adb_env.sh
OUT="$SP/review_session.mp4"; rm -f "$OUT"

echo "== start scrcpy (host-side record) =="
scrcpy --no-window --no-audio -N --record="$OUT" --time-limit=100 > "$SP/scrcpy.log" 2>&1 &
SC=$!
sleep 4   # allow scrcpy to connect + begin recording

echo "== open CFA deck -> reviewer =="
"$ADB" shell input tap 267 359
sleep 3
echo "focus: $("$ADB" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus)"

PLAN=(GOOD EASY GOOD HARD GOOD EASY AGAIN GOOD GOOD EASY HARD GOOD EASY GOOD)
i=0
for k in "${PLAN[@]}"; do
  i=$((i+1)); echo "card $i rate $k"
  "$ADB" shell input tap 540 2273; sleep 1.5     # Show answer
  case $k in GOOD)"$ADB" shell input tap 675 2295;;EASY)"$ADB" shell input tap 945 2295;;HARD)"$ADB" shell input tap 405 2295;;AGAIN)"$ADB" shell input tap 135 2295;;esac
  sleep 1.6
done
sleep 2

echo "== stop scrcpy =="
kill -INT "$SC" 2>/dev/null
wait "$SC" 2>/dev/null
tail -4 "$SP/scrcpy.log"
python3 - "$OUT" <<'PY'
import sys,struct,os
p=sys.argv[1]
if not os.path.exists(p): print("NO FILE"); sys.exit()
d=open(p,'rb').read();i=0;b=[]
while i+8<=len(d) and len(b)<20:
    sz=struct.unpack('>I',d[i:i+4])[0];b.append(d[i+4:i+8].decode('latin1','replace'))
    if sz<8:break
    i+=sz
print("review_session.mp4 bytes",len(d),"boxes",b,"has_moov",'moov' in b)
PY
"$ADB" exec-out screencap -p > "$SP/shot_review_done.png"
echo DONE
