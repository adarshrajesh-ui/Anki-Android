#!/system/bin/sh
# Runs ENTIRELY on-device in a single adb shell (one adb client) so screenrecord
# finalizes cleanly. Opens the CFA deck, reviews 14 cards, stops recording via SIGINT.
input tap 267 359          # open CFA deck -> reviewer
sleep 3
screenrecord --bit-rate 8000000 --time-limit 70 /sdcard/rev4.mp4 &
SRPID=$!
sleep 1.5
for k in G E G H G E A G G E H G E G; do
  input tap 540 2273       # Show answer
  sleep 1.6
  case $k in
    G) input tap 675 2295;;   # Good
    E) input tap 945 2295;;   # Easy
    H) input tap 405 2295;;   # Hard
    A) input tap 135 2295;;   # Again
  esac
  sleep 1.7
done
sleep 1.5
kill -INT $SRPID
wait $SRPID
echo REC_DONE
