# Phase B — Mobile Pass 1 (AFTER — CFA-activity MAJOR fixes)

Device-observable `adb screencap` on `emulator-5554` (real running debug build,
`com.ichi2.anki.debug`) after fixing the Pass-1 MOBILE MAJORs that live in the
CFA activities (mirrors the desktop iter-26 StatCard fix). Pair each with the
matching `../pass-1-before/` capture.

| After | Screen | Fixes shown |
|-------|--------|-------------|
| `03-exam-readiness.png` | Exam Readiness (top) | **M3-1** abstain no longer shouts in warn-orange — now a calm muted-grey "Awaiting reviews"; **M3-2** the orange brand eyebrow no longer collides with the abstain colour (abstain is grey); **M3-3** all three score cards share one container (surface + hairline stroke + 12dp radius) |
| `04-exam-readiness-bottom.png` | Exam Readiness (bottom) | consistent cards continue; **M3-6/M4-1** the "Exam configuration" outlined button is now CFA navy (label + stroke), not stock AnkiDroid blue |
| `05-exam-config.png` | Exam configuration | **M4-1** the "Pick date" outlined button is now CFA navy, matching the navy "Save" filled button |

Verified green after these changes: `ktlintCheck`, `lintVitalFullRelease`
(the release gate M5 established), and the CFA unit tests
(`testPlayDebugUnitTest --tests "com.ichi2.anki.cfa.*"`).

Not touched this pass (Pass-2 mobile backlog): the un-branded **shell** —
DeckPicker / nav-drawer header / Reviewer are still stock AnkiDroid light-blue
(M1-1, M2-1, M5-1) — plus the junk scratch decks (M1-2) and the two-tone
status bar (M3-4, MINOR).
