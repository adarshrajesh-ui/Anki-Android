# Mobile CFA Home / Today dashboard — the native CFA landing screen

## Objective gap this closes

> "App opens into a CFA experience (home/today), not a raw deck list. CFA
> navigation (Home/Today, Study, Concept Map, Readiness, Ethics) is present
> natively across the whole app."

The desktop app opens into a native CFA Home dashboard (`ts/lib/cfa/pages/
CfaHomePage.svelte`). The phone had **no equivalent** — it launched straight
into the stock `DeckPicker` deck list, and the nav drawer had no Home entry.
This adds the mobile CFA Home and routes the launcher into it.

## What was built

| Piece | File |
|-------|------|
| Pure payload builder (scores → range/midpoint/abstain, exam countdown, hero one-liner) mirroring the desktop Home helpers | `AnkiDroid/src/main/java/com/ichi2/anki/cfa/CfaHome.kt` |
| Self-contained dashboard asset (CFA design tokens, same as Concept Map) | `AnkiDroid/src/main/assets/cfa/home.html` |
| WebView host + `AndroidCfaHome` JS bridge routing CTAs to native CFA screens | `AnkiDroid/src/main/java/com/ichi2/anki/CfaHomeActivity.kt` |
| Layout, manifest entry, nav-drawer "Home" entry (top of CFA group) + icon + strings + dispatch | `activity_cfa_home.xml`, `AndroidManifest.xml`, `navigation_drawer.xml`, `ic_cfa_home.xml`, `cfa.xml`, `NavigationDrawerActivity.kt` |
| Tightly-guarded launcher hand-off (cold ACTION_MAIN+LAUNCHER only, once/process, never on in-app nav) | `CfaBootstrap.kt` (`shouldOpenCfaHomeOnLaunch` + `maybeOpenCfaHome`), called from `DeckPicker.onCreate` |

## The three honest scores, on the phone Home

Memory / Performance / Readiness are rendered identically to the desktop Home:
big range on top, muted name + meaning, midpoint sub — and the **give-up rule**
is honored: an abstaining score reads a quiet muted **"Awaiting reviews"** with
the reason, never a fake 0%. Meanings byte-mirror the desktop backend payload
(`qt/aqt/mediasrv.py`). A provenance chip states the source (shared engine vs
on-device deterministic).

## Why the launcher hand-off can't loop

`shouldOpenCfaHomeOnLaunch` only fires on a genuine cold launch
(`ACTION_MAIN` + `CATEGORY_LAUNCHER`), on a fresh start (savedInstanceState ==
null), at most once per process. In-app navigation to the deck list — Home's
"Browse decks" CTA and the nav-drawer "Decks" entry — uses a plain component
Intent with no action/category, so it never re-triggers the hand-off. DeckPicker
stays on the back stack so Back from Home returns to the deck list.

## Verification (light)

- **Compile-gate + unit tests:** `./gradlew :AnkiDroid:testPlayDebugUnitTest
  --tests 'com.ichi2.anki.cfa.CfaHomeTest'` → **BUILD SUCCESSFUL, 9 tests, 0
  failures** (compiles the entire main sourceset, so the new Activity, nav,
  manifest, layout, drawable and string wiring are all validated). Tests cover
  the score-card range/midpoint formatting, the abstain "Awaiting reviews" quiet
  state, the hero one-liner (abstain reason vs pass-probability), the exam
  countdown tone rule (warn ≤14d, neutral prompt when unset), the launcher-guard
  truth table, and an asset source guard.
- **Device-observable render:** `home-preview.html` injects a realistic
  `window.CFA_HOME_DATA` into the shipped asset and is driven in a real browser
  (`chrome-devtools-axi`): hero "42 days to the exam", Memory "62% – 78%",
  abstaining Readiness "Awaiting reviews" (muted), 4 CTAs (primary "Study by
  Exam Priority"), source chip "Source: shared engine", and a CTA click no-ops
  safely when the native bridge is absent. Screenshot: `mobile-cfa-home.png`.

## Known follow-up

The launcher hand-off is guarded by a pure, unit-tested decision but was not
exercised on a real emulator this iteration (no device run). The remaining
polish is a Phase-B pass over the Home on-device (TalkBack grouping, dark mode).
