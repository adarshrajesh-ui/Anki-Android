# CFA Speedrun — Mobile (L3) Overnight GNHF Plan

> Durable plan for the mobile GNHF worker. Record progress in
> `proof/gnhf-speedrun/L3-NOTES.md` (create it). Companion doc:
> `/Users/adarshrajesh/AlphaWeek2/ankiCFA/proof/friday/SPEEDRUN-PLAN.md` in the desktop repo.

## Framing & honesty

This is the **phone companion of a CFA Level II exam-prep app built on AnkiDroid**.
It must share ONE engine with desktop (the fork Rust backend), review the same
deck, sync both ways, and show the same three scores (Memory/Performance/
Readiness) with ranges + the give-up rule. **Honesty rule:** never show a score
without its evidence; label any simulated data `SIMULATED`; the app must still
score with AI OFF. Do not fabricate results.

## Global rules

1. **Do NOT run `no-mistakes`.** Add focused unit tests + on-emulator evidence.
2. **Do NOT commit to `main`/`mobile-logout` directly** — work on your gnhf branch.
3. Prefer targeted Gradle tasks (`./gradlew :AnkiDroid:testFullDebugUnitTest
   --tests "*Cfa*"`, `assembleFullDebug`) over full CI.
4. Every item = committed code + a passing test + evidence under
   `proof/gnhf-speedrun/L3/`. If blocked, write `BLOCKED: <root cause>` in
   `proof/gnhf-speedrun/L3-NOTES.md`, cite the closest proof, move on.
5. Before coding: `git log --oneline -15`; preserve existing user changes.

## Already DONE — do not redo

- Three scores + ranges + give-up UI (`CfaExamReadinessActivity`, `CfaScores.kt`,
  `CfaScorer.kt`).
- Exam-priority study via real `col.backend.buildExamQueue` RPC
  (`CfaExamQueue.kt`) — this proves the fork-AAR RPC path works on device.
- Exam-config editor, ethics-pairs deck bundled + AI grading via proxy, Tab-key
  AI fill in the note editor, nav "Log out / Sync account".

## Backlog

### L3-1 [P0] Serve the three scores from the shared Rust `computeCfaScores` RPC
- **Why:** Spec: "share the engine, do not rewrite it." Scores currently come from
  a Kotlin reimplementation (`CfaScorer.compute`) in `CfaScoresProvider.kt`, not
  the Rust RPC. Exam-priority already calls `col.backend.buildExamQueue` — mirror
  that exact path for scores.
- **Do:** Rebuild/point the fork engine AAR so `col.backend.computeCfaScores(...)`
  is available on device; wire `CfaScoresProvider.scores()` to call it and map the
  proto response into `CfaScores`. Keep `CfaScorer.compute` as a **fallback only**
  when the RPC is unavailable; expose a `source` flag (`"rpc"` / `"fallback"`).
- **Verify:** `CfaScoresProviderTest` asserts the RPC path is used when the backend
  exposes it; a parity test comparing RPC vs Kotlin-fallback on a fixture; emulator
  logcat line showing `source=rpc`.
- **Evidence:** `proof/gnhf-speedrun/L3/scores-rpc.txt` (logcat + parity numbers).
- **If the AAR rebuild is blocked:** keep the fallback, add a parity unit test vs
  desktop-produced fixture numbers, and mark `BLOCKED` with the toolchain root cause.

### L3-2 [P0] Device-observable desktop→phone reverse sync
- **Why:** Friday proof requires "review on the phone, see it on the desktop, **and
  the reverse**." Per `/Users/adarshrajesh/AlphaWeek2/ankiCFA/proof/friday/sync/HANDOFF.md`, the on-device
  collection diverges/resets across cold launches, so a desktop review isn't
  observable on the phone after sync.
- **Do:** Root-cause the divergence (bootstrap/collection handling in
  `CfaBootstrap.kt` / `DeckPicker.kt` and any Cfa*Activity collection handling) so
  synced state survives a normal sync + cold launch. Fix it.
- **Verify:** adb/emulator recording — review a card on desktop → sync → the phone
  shows that review (revlog count matches). Steps: connect emulator to the local
  sync server (`http://10.0.2.2:27701/`, already configured), sync, cold-launch,
  confirm.
- **Evidence:** `proof/gnhf-speedrun/L3/reverse-sync.mp4` (or screen frames) +
  `reverse-sync.txt`. **If device-observable proof is blocked:** fix what you can,
  document root cause, and cite the desktop engine-level pytest
  (`/Users/adarshrajesh/AlphaWeek2/ankiCFA/pylib/tests/test_cfa_sync.py`) that proves reverse sync; mark BLOCKED.

## Stop condition

L3-1 and L3-2 each committed with a passing test + an evidence file under
`proof/gnhf-speedrun/L3/`, OR marked BLOCKED with a root-cause note in
`proof/gnhf-speedrun/L3-NOTES.md`. Focused `*Cfa*` unit tests green. No faked proof.
