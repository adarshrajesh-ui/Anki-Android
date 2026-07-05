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
- **M3 [P1] same-card offline conflict merge — DONE.**
  Device-observable on emulator-5554. Card X=1783137755188 ("What defines a hedge
  fund...") reviewed on BOTH devices while diverged from a shared base: the PHONE
  answered AGAIN (ease 1) OFFLINE (airplane mode) -> phone revlog 1783212583931,
  card into learning; the DESKTOP then answered EASY (ease 4) at a LATER time
  (revlog 1783212648010) from the same base and uploaded. After the phone came
  online and synced, the converged phone collection (pulled from device, WAL
  applied) shows the MORE-RECENT desktop review WON: card X queue=2 (review),
  ivl=4d, mod=1783212648 — the phone's Again learning state was overridden — yet
  the phone's own Again revlog row is still present (no lost review) and reps=1
  (no double-count). DeckPicker visibly went "2 0 0"->"1 1 0" (phone Again put
  card X in learning) ->"2 0 0" (desktop Easy graduated it back to review).
  Honest: Readiness's displayed graded count is RAW (26->28); the engine
  anti-cram DEDUPED count is 21 (same-card same-day => one (card,day)).
  Corroborated by desktop pytest test_cfa_sync.py::test_conflict_more_recent_wins*
  (12/12) and the full file 20/20 after de-flaking a pre-existing same-ms
  collection-mod sync race. Evidence: proof/gnhf-speedrun/L3/conflict-merge.txt
  + conflict-merge/ (5-shot sequence + cfa_desktop_conflict_review.py).
- **M4 [P1] offline-then-sync + AI-off scores — DONE.**
  Device-observable on emulator-5554. Base: phone 31 / server 31 revlog. Airplane
  mode ON (device `ping 10.0.2.2` unreachable, status-bar airplane icon) → answered
  card 1783137755189 GOOD offline → phone revlog 31→32, offline id 1783213842513;
  server still 31, id ABSENT (`found_on_server:false`) = genuine offline-only. STILL
  OFFLINE, Exam Readiness RENDERED (source=on-device deterministic, no AI/network):
  honest N/A-abstaining with give-up reasons, 28→29 graded reviews reflecting the
  offline review — proves AI-off-still-scores. Airplane OFF → Sync icon showed an
  orange pending-upload dot → tapped Sync → logcat `sending done=true cards=1 notes=0
  revlog=1` + finalize; icon went clean. Ground truth: server on-disk collection
  revlog 31→32 and the phone's exact offline id 1783213842513 now present
  (`found_on_server:true`, re-verified by a fresh full-download). No fabrication.
  Evidence: `proof/gnhf-speedrun/L3/offline-then-sync.txt` + `offline-then-sync/`
  (6-shot sequence + `cfa_server_inspect.py`).
- **M5 [P2] packaged phone build — DONE.**
  SIGNED release APKs built: `./gradlew :AnkiDroid:assembleFullRelease` (full
  flavor, R8/minify ON, per-ABI splits) → 4 APKs under
  `AnkiDroid/build/outputs/apk/full/release/`, versionName 2.25.0alpha1,
  applicationId com.ichi2.anki. Signed with the repo fallback release keystore
  (`tools/fallback-release-keystore.jks`, my-key): `apksigner verify -v`
  reports "Verifies / v2 scheme: true", signer SHA-256 0a8ebeea… EXACTLY
  matching the keystore entry. The packaged librsdroid.so is the CFA fork
  engine (48 MB, RPC dispatch by proto index; symbols stripped by R8).
  Device-observable on emulator-5554: `adb install -r` the arm64 APK → Success
  (distinct package from com.ichi2.anki.debug); launched → first-run intro →
  permissions → native ankiCFA DeckPicker (CFA / Ethics Pairs / CFA Level II
  decks, "23 cards due") → nav-drawer Exam Readiness RENDERS (source=on-device,
  eyebrow "ANKICFA · CFA LEVEL II", honest N/A-abstaining, 8 canonical topics).
  Release build also FORCED 15 genuine lint-vital fixes in the CFA UI
  (HardcodedText→@string/cfa_brand_eyebrow, DuplicateCrowdInStrings,
  MenuTitleMaxLengthAttr/FixedMenuTitleLength, UnusedResources,
  DirectSystemCurrentTimeMillisUsage@Suppress w/ parity rationale) — all fixed
  properly, NOT baselined; the 19 `*Cfa*` unit tests + lintVitalFullRelease
  stay green. Evidence: `proof/gnhf-speedrun/L3/packaged-build.txt` +
  `packaged-build/` (01 first-run, 02 permissions, 03 deckpicker,
  04 exam-readiness). **Mobile Phase A (M1–M5) COMPLETE.**
- Phase B mobile UI passes (≥3) — TODO

## Key learnings
- M4 airplane-mode toggle on the emulator: `settings put global airplane_mode_on
  1` + `su root svc wifi disable`/`svc data disable` + `cmd connectivity
  airplane-mode enable` + broadcast `AIRPLANE_MODE` reliably makes `10.0.2.2`
  unreachable (device `ping 10.0.2.2` => "Network is unreachable") and shows the
  status-bar airplane icon. Reverse all of them to reconnect. Airplane mode kills
  BOTH the sync server and the AI proxy, so it is a genuine offline+AI-off state.
- The self-hosted anki-sync-server persists its collection at
  `/tmp/cfa-syncserver/cfa/collection.anki2` (base `/tmp/cfa-syncserver`). If the
  server process dies mid-verification, you can STILL prove an upload landed by
  reading that on-disk file directly with sqlite3 (no re-sync needed); a restart
  reuses the same on-disk collection, so the uploaded state survives the restart.
  A download-only inspector (`force_full_download` into a throwaway temp profile)
  is the clean way to confirm "is revlog id X on the server?" without mutating it.
- AI-off-still-scores is architecturally free on mobile: CfaScoresProvider (RPC +
  Kotlin fallback) is 100% local, so airplane mode changes nothing about scoring —
  the Readiness screen renders (honest abstain) with source=on-device. The only
  mobile AI is the ethics/Tab-fill proxy, which is irrelevant to the score path.
- M3 conflict method: put the PHONE in airplane mode, review card X in the
  AnkiDroid reviewer offline, identify card X from the newest phone revlog row,
  then have the desktop review that SAME card id from the server base (later
  timestamp = winner), airplane-mode OFF, sync. Anki sync is last-writer-wins on
  card STATE (both revlog rows always persist). Answering Again on the phone vs
  Easy on the desktop gives a visibly different winning state (learning vs
  review) so the "more-recent wins" outcome is observable in the DeckPicker.
- The desktop's set_due_date([cid],"0") (used to force the card due before
  answering) writes an extra revlog row (type=4, ease=0) that is NOT a graded
  review — expect 3 rows for card X after the merge (phone Again + this marker +
  desktop Easy), only 2 of which are graded.
- test_cfa_sync.py had a pre-existing timing flake: fresh getEmptyCol()
  collections that sync within one millisecond tick share a collection-mod
  anchor, so the receiver's incremental sync reports NO_CHANGES and never pulls
  a one-way change. Extra sync rounds do NOT fix it; a ~50ms settle before each
  review (so its mod is strictly later than the last sync) does. Fixed in the
  desktop repo's _review() helper.
- M5: the release build gates on `lintVitalFullRelease` (lint runs ONLY on
  release, not debug), so `assembleFullRelease` surfaced 15 CFA-code lint
  errors that debug builds/unit tests never see — always run the release build
  before claiming the mobile app "ships". Fix them properly, don't baseline.
- M5 parity gotcha: CfaScores.kt MUST use `System.currentTimeMillis()` (not
  TimeManager) for the FSRS `nowSecs` — the RPC is called with now=0 so the
  native engine uses its own wall clock, and the Kotlin fallback has to match
  or the 1e-6 CfaScoresProviderTest parity assert fails (Robolectric's mock
  clock diverges). Suppress DirectSystemCurrentTimeMillisUsage with a comment.
- M5 install: the release build's applicationId is `com.ichi2.anki` while debug
  is `com.ichi2.anki.debug`, so `adb install -r` the SIGNED release APK
  installs ALONGSIDE the dev build (no signature-mismatch/uninstall needed) and
  reaches the same shared external AnkiDroid collection once MANAGE_EXTERNAL_
  STORAGE is granted (`adb shell appops set com.ichi2.anki
  MANAGE_EXTERNAL_STORAGE allow`, then relaunch to clear the permissions gate).
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
