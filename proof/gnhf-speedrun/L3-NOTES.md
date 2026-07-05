# CFA Speedrun — Mobile L3 notes (branch gnhf/speedrun-mobile)

Master: `/Users/adarshrajesh/AlphaWeek2/ankiCFA/proof/friday/SPEEDRUN-PLAN.md`.
Plan: `SPEEDRUN-MOBILE-PLAN.md`. Track M1–M5 + mobile UI passes here.

## Environment / toolchain (verified 2026-07-05)
- Worktree: `/Users/adarshrajesh/wed/AnkiDroid-gnhf-worktrees/speedrun-mobile`
  (git worktree of `/Users/adarshrajesh/wed/AnkiDroid`, branch `gnhf/speedrun-mobile`
  cut from `b4c7247824`). Other GNHF runs use the main checkout / other worktrees —
  work stays isolated here.
- Local fork backend: `local_backend=true` in `local.properties`. Resolves to
  `../Anki-Android-Backend` — symlinked to `/Users/adarshrajesh/wed/Anki-Android-Backend`.
- Build JDK 21 (`/opt/homebrew/opt/openjdk@21`); NDK 29.0.14206865; cargo-ndk 4.1.2;
  rust android targets installed. Gradle wrapper 9.5.0.
- Targeted tests: `./gradlew :AnkiDroid:testPlayDebugUnitTest --tests "com.ichi2.anki.cfa.*"`.

## Progress
- **M1 [P0] scores via shared Rust computeCfaScores RPC — DONE.**
  Rebuilt the fork AAR + host testing dylib so `compute_cfa_scores` is compiled in
  and surfaced as `col.backend.computeCfaScores(...)`; wired `CfaScoresProvider` to
  prefer it (source=rpc) with the deterministic `CfaScorer` fallback (source=fallback);
  fixed a real parity gap (fallback now dedupes graded reviews per card-day like the
  engine). Tests green (3+2+1), runtime log shows `source=rpc`. Evidence:
  `proof/gnhf-speedrun/L3/scores-rpc.txt`.
- **M2 [P0] device-observable desktop→phone reverse sync — DONE.**
  Made TWO causal desktop reviews (unique revlog ids 1783211725074, 1783211928235)
  against the local anki-sync-server, synced the emulator, cold-relaunched, and
  confirmed on-disk persistence: phone collection revlog 24→26→28, both desktop
  cards reps 2→3, BOTH desktop revlog ids present on the phone; the Exam Readiness
  screen increments 25→26 graded reviews (one per desktop review), honestly
  abstaining per the give-up rule. The HANDOFF "on-disk diverges/resets across cold
  launch" does NOT reproduce — it was a measurement artifact (reading the main
  .anki2 while un-checkpointed reviews sat in the WAL); after a clean cold-stop the
  WAL checkpoints (round 1, 0 bytes) or applies on next open (round 2). No engine
  fix needed. IntentHandler CFA-routing concern also stale (no CFA routing on this
  branch). Corroborated by desktop `pytest pylib/tests/test_cfa_sync.py` → 7 passed.
  Evidence: `proof/gnhf-speedrun/L3/reverse-sync/` (3-shot sequence + reverse-sync.txt
  + cfa_desktop_review.py).
- M3 same-card offline conflict merge — TODO
- M4 offline-then-sync + AI-off scores — TODO
- M5 packaged phone build — TODO
- Phase B mobile UI passes (≥3) — TODO

## Key learnings
- The mobile CFA backend is a LOCAL fork build (`../Anki-Android-Backend`,
  `local_backend=true`), NOT the published `anki-android-backend` AAR (which lacks all
  CFA RPCs). `cfa_build_fork_engine.sh` clones `~/AlphaWeek2/ankiCFA` (main) into
  `anki/` and cross-compiles; it REUSES an existing `anki/` clone unless it is not a
  git repo, so `rm -rf anki` first to pick up new main RPCs.
- The AAR build only produces the Android `.so` + Kotlin binding. For JVM unit tests
  the host `librsdroid.dylib` inside `rsdroid-testing.jar` must ALSO be rebuilt
  (`cargo build -p rsdroid --release`, then zip it into the jar) or the RPC throws at
  test runtime and silently falls back.
- Bumping the engine changed `buildExamQueue`'s arity (added `typeMultipliers`) —
  repointing to a newer main can break existing CFA call sites; fix them in the same pass.
