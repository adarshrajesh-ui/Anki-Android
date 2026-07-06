# Pass-1 mobile — SHELL REFACTOR after set (device-observable)

Device `emulator-5554`, `adb screencap`, real running debug build
(`com.ichi2.anki.debug`, `installFullDebug` with the Phase B shell-refactor
theme). These resolve the remaining Pass-1 mobile MAJORs logged in
`proof/friday/UI-CRITIQUE-LOG.md` §Pass 1 MOBILE — the "non-native-CFA feel"
shell (the objective's flagged biggest lift):

- `01-deckpicker.png` — DeckPicker: navy toolbar + navy status bar (M1-1),
  orange accent FAB (M1-3), and the junk scratch decks "h"/"h gg" PURGED
  (M1-2). List is now CFA-only (CFA / Ethics Pairs / Study — Ethics
  Minimal-Pairs / CFA Exam Priority / CFA Level II).
- `02-nav-drawer.png` — nav drawer: navy CFA brand lockup header
  ("ankiCFA" + "CFA LEVEL II · EXAM PREP") replacing the stock blue-mountain
  image, and the selected item ("Decks") now CFA-navy not AnkiDroid blue (M2-1).
- `03-exam-readiness.png` — Exam Readiness CFA activity now under a consistent
  navy status bar (M3-4, two-tone band gone); prior iter-28 calm abstain +
  unified cards intact; Source: shared engine.
- `05-reviewer-question.png` / `06-reviewer-answer.png` — Reviewer (highest
  time-on-screen): navy toolbar, count bar on the calm CFA surface (M5-1); the
  four-grade ease semantics (red/grey/green/blue) are intentionally preserved
  (M5-2 MINOR).

Before set: `mobile-ui/pass-1-before/` (stock AnkiDroid light-blue shell).
