# CFA Speedrun — Mobile (part of the single GNHF run)

> Master plan lives in the desktop repo:
> `/Users/adarshrajesh/AlphaWeek2/ankiCFA/proof/friday/SPEEDRUN-PLAN.md`.
> Work here on branch **`gnhf/speedrun-mobile`**; commit frequently. Track progress
> in `proof/gnhf-speedrun/L3-NOTES.md`. This is the AnkiDroid-based **phone
> companion** of a CFA Level II exam-prep app: share ONE engine (the fork Rust
> backend), review the same deck, sync both ways, show the same three scores
> (Memory/Performance/Readiness) with ranges + give-up. **Honesty rule:** never show
> a score without evidence; label simulated data `SIMULATED`; AI-off must still
> score; never fabricate proof. **NO VIDEOS** — capture ordered screenshot sequences
> + logs.

## Roles (same as master)
THE PERFECTIONIST orchestrates; spawn IMPLEMENTER, CRITIC/EVALUATOR (ruthless
UWorld-grade product designer + QA, harsher each pass), and VERIFIER sub-agents.

## Already DONE (do not redo)
Three scores + ranges + give-up UI; exam-priority via real `col.backend.buildExamQueue`;
exam-config editor; ethics-pairs deck bundled + AI grading via proxy; Tab-key AI fill;
nav "Log out / Sync account".

## PHASE A — mobile features (commit + verify each)

- **M1 [P0] Scores from the shared Rust `computeCfaScores` RPC.** Wire
  `CfaScoresProvider.scores()` to `col.backend.computeCfaScores(...)` (mirror how
  `CfaExamQueue` calls `col.backend.buildExamQueue`); keep `CfaScorer.compute` as a
  **fallback only** when the RPC is unavailable; expose a `source` flag
  (`rpc`/`fallback`). *Verify:* `CfaScoresProviderTest` (RPC path used when backend
  exposes it) + parity vs Kotlin fallback + logcat `source=rpc`. *Evidence:*
  `proof/gnhf-speedrun/L3/scores-rpc.txt`. If the fork AAR cannot expose the RPC,
  keep the fallback, add a parity test vs desktop fixture numbers, mark BLOCKED.
- **M2 [P0] Device-observable desktop→phone reverse sync.** Root-cause + fix the
  on-device collection divergence/reset described in
  `/Users/adarshrajesh/AlphaWeek2/ankiCFA/proof/friday/sync/HANDOFF.md` so a card
  reviewed on desktop shows on the emulator after sync + cold launch. *Verify:*
  adb-driven screenshot sequence (review on desktop → sync → phone shows it; revlog
  counts match) at `proof/gnhf-speedrun/L3/reverse-sync/`. If device-observable
  capture is impossible, fix what you can, cite the engine-level pytest
  `/Users/adarshrajesh/AlphaWeek2/ankiCFA/pylib/tests/test_cfa_sync.py`, mark BLOCKED.
- **M3 [P1] Same-card offline conflict merge (Sunday 7b).** Review the same card on
  both devices offline, sync, show the conflict rule picks a clear winner; document
  the rule. *Evidence:* `proof/gnhf-speedrun/L3/conflict-merge.txt` + screenshots.
- **M4 [P1] Offline-then-sync + AI-off-still-scores.** Verify offline review syncs on
  reconnect and scores render with AI off. *Evidence:* screenshot sequence + log.
- **M5 [P2] Packaged phone build (Sunday).** Signed release APK if a keystore is
  available; else `assembleRelease` unsigned + a BLOCKED note in L3-NOTES.md.

Targeted tests: `./gradlew :AnkiDroid:testFullDebugUnitTest --tests "*Cfa*"`,
`assembleFullDebug`.

## PHASE B — mobile UI/UX full refactor (final, biggest mobile lift)

The AnkiDroid CFA UI is the largest UX lift. After Phase A, run the **same
multi-pass critique loop as the master plan §4** on every mobile screen, to premium
**UWorld production grade**: ≥3 passes, each more critical, screenshot every screen
+ state via `adb exec-out screencap -p` (navigate with `adb shell am start` /
`input tap`), critique via GPT-4o vision against the master rubric, log every issue
in `proof/gnhf-speedrun/mobile-ui/UI-CRITIQUE-LOG.md`, and fix ALL blocker+major
issues. Build a real theme / styles / Material components + a consistent CFA design
language (typography, color, spacing, states, motion). Add functional checks that
each CFA screen renders with real data. Save before/after pairs under
`proof/gnhf-speedrun/mobile-ui/pass-N/`.

## Tooling
- adb: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`; device
  `emulator-5554` (AVD `ankidroid_cfa`). Start if down:
  `/opt/homebrew/share/android-commandlinetools/emulator/emulator @ankidroid_cfa &`.
- Screenshot: `adb exec-out screencap -p > f.png` (fallback `adb shell screencap -p
  /sdcard/s.png && adb pull /sdcard/s.png f.png`).
- Package: `com.ichi2.anki.debug`. Sync server already configured on device
  (`http://10.0.2.2:27701/`, user `cfa`).

## Stop condition (mobile portion)
M1–M4 committed with a passing test or captured evidence (M5 best-effort/BLOCKED),
and ≥3 increasingly-critical mobile UI passes with before/after screenshots and all
blocker+major issues resolved or documented. Focused `*Cfa*` tests green.
