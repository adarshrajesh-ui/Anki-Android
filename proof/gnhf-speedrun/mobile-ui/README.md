# Mobile UI/UX — Phase B captures (ankiCFA)

Device-observable UI captures for the Phase B multi-pass critique of the mobile
app. Captured on `emulator-5554` via `adb screencap` from the real running debug
build on branch `gnhf/speedrun-mobile`.

The critique + severities live in the **desktop** master log:
`ankiCFA: proof/friday/UI-CRITIQUE-LOG.md` (§Pass 1 — MOBILE). Inventory:
`ankiCFA: proof/friday/UI-INVENTORY.md`.

## pass-1-before/ (critical pass, "before" set)
| File | Screen | Key Pass-1 findings |
|------|--------|---------------------|
| `01-deckpicker.png` | DeckPicker (landing) | M1-1 stock light-blue shell (not CFA navy); M1-2 junk decks "h"/"h gg" |
| `02-nav-drawer.png` | Nav drawer | M2-1 stock AnkiDroid header, blue accent (no CFA lockup) |
| `03-exam-readiness.png` | Exam Readiness (top) | M3-1 abstain shouts in warn-orange ×3; M3-2 accent==warn collision; M3-3 inconsistent cards |
| `04-exam-readiness-bottom.png` | Readiness (per-topic + actions) | M3-5 flat "no data" rows (8 topics); M3-6 off-brand blue outlined button |
| `05-exam-config.png` | Exam configuration | M4-1 off-brand blue "Pick date"; M4-2 sparse layout |
| `06-reviewer-question.png` | Reviewer (question) | M5-1 100% stock AnkiDroid chrome, zero CFA identity |
| `07-reviewer-answer.png` | Reviewer (answer + ease) | M5-2 stock ease palette / light-blue count bar |

**Honesty:** no `OPENAI_API_KEY` in this environment, so the GPT-4o vision critic
is unavailable; the critique is a labelled structured senior-designer heuristic
pass on these real device captures (never a fabricated model transcript).

Pass-2 "after" captures will land under `pass-2/` once the fixes land.
