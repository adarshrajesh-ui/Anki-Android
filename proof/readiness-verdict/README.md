# M-P4-3 — Mobile Exam Readiness had no pass/fail VERDICT (cross-platform parity / signature-feature gap)

## Finding

The desktop Exam Readiness page (`ts/lib/cfa/pages/CfaReadinessPage.svelte`)
leads with the **Bayesian pass/fail verdict** — its signature call: a big
"Likely pass / Likely fail" headline with the supporting probability, the
estimated exam-weighted accuracy + 95% credible band, the MPS proxy, exam-
weighted recall, and the standing "not validated" caveat. This band **never
abstains** — with little evidence it is simply very wide and narrows as reviews
accrue.

The **mobile** Exam Readiness screen (`CfaExamReadinessActivity.kt`) had **no
verdict at all**. It showed the Readiness *give-up score* as one of three cards,
which **abstains** ("Awaiting reviews") below 200 graded reviews — so a phone
user studying toward the exam saw no pass/fail call, ever, until very late. The
shared Rust engine already computed the Bayesian hero (`ComputeCfaScoresResponse.
bayesian`, proto field 4), but the mobile client **dropped it on the floor** in
`CfaScoresProvider.fromRpc`, and the on-device fallback (`CfaScorer`) never
computed it. A signature desktop feature was entirely missing on phone.

## Fix (this iteration)

1. **Plumbed the RPC Bayesian through.** `CfaScoresProvider.fromRpc` now maps
   `resp.bayesian` → a new `BayesianVerdict` on `CfaScores` (shared Rust engine,
   exact numbers).
2. **Ported the Bayesian math to the on-device fallback.** `CfaScorer` now
   computes the verdict deterministically offline, mirroring
   `pylib/anki/cfa._py_bayesian_readiness` field-for-field (per-card first-
   exposure Bernoulli trials → per-topic Beta(1,1) posteriors → exam-weighted
   aggregate → `p_pass` via a standard-normal CDF; SM-2 recall fallback when
   FSRS R is null). erf uses the Abramowitz & Stegun 7.1.26 approximation
   (abs error ~1.5e-7).
3. **Surfaced the verdict hero** on `CfaExamReadinessActivity`, leading the
   screen with the pass/fail call in the CFA `cfa_pass`/`cfa_fail` palette (or an
   honest amber `cfa_warn` "keep studying" abstain state, gated exactly like the
   desktop hero on `memory.abstain || performance.abstain`), followed by the
   three honest StatCards — the same layout as the desktop page. One
   screen-reader node per card (WCAG 1.3.1/4.1.2).

## Verification

- **RPC↔fallback Bayesian parity (the key gate):** `CfaScoresProviderTest`
  now asserts `rpc.bayesian ≈ fb.bayesian` — accuracy / CI / recall to **1e-6**,
  `p_pass`/`callProb` to **1e-4** (looser only because Kotlin uses an erf
  approximation vs Rust's exact `libm::erf`), and identical `call`/`passed`.
  This proves the on-device port matches the shared engine → **desktop == phone**
  on the verdict by construction. Both suites green:
  `./gradlew :AnkiDroid:testPlayDebugUnitTest --tests '…CfaScorerTest' --tests '…CfaScoresProviderTest'`
  (CfaScorerTest 3/3, CfaScoresProviderTest 3/3).
- **Never-abstains guard:** `CfaScorerTest.bayesian verdict never abstains…`
  — a fresh (zero-evidence) collection still yields a well-formed "likely fail"
  call; a populated one yields an ordered in-[0,1] band with `callProb ≥ 0.5`.
- **Compile + lint:** main sourceset compiles under `-Werror`; `ktlintCheck`
  clean.
- **Visual:** `verdict-hero.{html,png}` reproduces the two shipped states
  (green "Likely pass" call + amber "keep studying" abstain) with the exact
  copy, CFA colour tokens and text sizes the Kotlin renders.

## Follow-up

The mobile Readiness is still a hand-built native `LinearLayout` (Home and
Concept Map are WebView assets identical to desktop). Converting it to a
`readiness.html` WebView for pixel-parity is a separate, larger increment; the
verdict-hero content gap — the more important one — is now closed.
