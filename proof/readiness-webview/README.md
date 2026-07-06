# Mobile Exam Readiness → WebView (desktop parity)

**Iteration 26.** Converted the phone's Exam Readiness screen from a hand-built
native `LinearLayout` (assembled in Kotlin, TextView-by-TextView) into the same
**self-contained WebView-asset** pattern that CFA Home and the Concept Map
already use — so all three CFA surfaces now render from one shared design system
and read as one product, matching the desktop `CfaReadinessPage.svelte`.

## What changed

| Before (native) | After (WebView) |
| --- | --- |
| `CfaExamReadinessActivity` built score cards + topic rows programmatically as sibling TextViews inside a `ScrollView`/`LinearLayout` | Activity hosts a full-bleed `WebView` loading `assets/cfa/readiness.html`, fed `window.CFA_READINESS_DATA` by the pure `CfaReadiness.buildPayload` |
| Verdict hero + StatCards styled ad-hoc via `getColor()` / `textSize` | One CFA design system (same tokens/hex as `home.html` + `concept_map.html`) |
| Two `MaterialButton`s for priority-study / exam-config | Two Study CTAs routed through the `AndroidCfaReadiness` @JavascriptInterface bridge |
| Accessibility via hand-grouped `contentDescription`s (`CfaAccessibility.kt`) | Native HTML semantics: verdict hero `role="group"` + composed `aria-label`, a real `<table>` with `<th>` headers, HTML-escaped injected text |

The numbers are unchanged — still `CfaScoresProvider.scores()` (shared Rust
`computeCfaScores` RPC, deterministic on-device fallback otherwise). Only the
presentation moved into the shared asset. The honest **give-up / abstain rule**
is preserved: abstaining scores read a quiet muted "Awaiting reviews", and the
pass/fail verdict is hidden (amber "keep studying" hero) on the exact desktop
gate (`memory.abstain || performance.abstain`).

## Evidence (rendered from the shipped asset)

- `readiness-pass.png` — Bayesian **Likely pass** verdict (green spine), the
  accuracy + 95% CI + est. recall lead, three measured StatCards, the per-topic
  recall table (Equity 45% warn-coloured, Derivatives "no data" muted), the
  Study CTAs, the "Source: shared engine" chip, and the explanatory footer.
- `readiness-abstain.png` — the honest **Not enough data — keep studying** hero
  (amber), three quiet muted "Awaiting reviews" cards, the fresh-deck table hint,
  and the "Source: on-device (deterministic)" chip.
- `readiness-pass.html` / `readiness-abstain.html` — the shipped asset with a
  realistic payload injected (drive with chrome-devtools-axi).

Functionally verified in a real browser (chrome-devtools-axi): the pass payload
yields `hero is-pass` + the full lead + 3 stat cards + 4 topic rows (1 warn, 1
muted recall cell) + the composed hero `aria-label`; the abstain payload yields
`hero is-warn`, 3 muted stat cards, and the table hint shown.

## Tests

- `CfaReadinessTest` (8 tests) — the pure `CfaReadiness.buildPayload` builder:
  the Bayesian hero (call / accuracy / CI / MPS / recall), the desktop abstain
  gate, recall omitted when the engine has none, the three value-first cards
  (range vs quiet abstain), topic sort-by-weight + honest recall tones, the
  awaiting-recall flag, caption + footer, and a source guard on the asset
  (bridge + CTAs + hero + table wired).
- `CfaAccessibilityTest` — rewritten regression guard now asserts the WebView
  asset carries HTML a11y semantics (`role="group"` + `aria-label`, `<table>`
  with `<th>`, `esc()` escaping); the now-unused native-only label builders
  were removed. The still-native Exam Config date-field a11y guards stay.
- `CfaContrastTest` — the readiness-eyebrow AA guard moved from the native
  layout onto the asset (eyebrow uses the AA-safe `--green`; the raw warm
  `--accent` never backs a text `color:`, only borders/spines).

All `com.ichi2.anki.cfa.*` unit tests pass (`testPlayDebugUnitTest`, which also
recompiles the whole main sourceset — so the new Activity/asset/bridge wiring is
gated offline).
