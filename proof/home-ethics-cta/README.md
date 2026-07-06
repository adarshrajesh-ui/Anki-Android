# Mobile Home — flagship Ethics drill surfaced as the primary CTA

## Finding (desktop↔phone parity gap)
On the desktop CFA Home, **Study Ethics — Minimal Pairs** is the *primary* study
CTA (`cfa:ethics`, warm accent-soft featured tile — see
`ts/lib/cfa/pages/home.ts` `HOME_CTAS[0]`). On the phone Home (added iter 16)
the flagship ethics drill had **no direct launcher at all** — it was only
reachable by tapping "Browse all decks (Ethics lives here)". The phone's primary
tile was instead "Study by Exam Priority", rendered as a solid-navy button that
diverged from the desktop's warm-accent featured-tile design language.

## Fix
- New `CfaEthicsStudyActivity` mirrors the desktop `aqt/cfa.py study_ethics_pairs`:
  materialises the shipped `CFA::Ethics Pairs` deck into a reusable
  "CFA Study — Ethics Minimal-Pairs" filtered deck (oldest-seen first,
  `reschedule=true` so FSRS stays authoritative), then enters the Reviewer.
  Honest empty state (no dead-end) when no ethics cards are present.
- New `"ethics"` target in `CfaHomeActivity.CfaHomeBridge` + manifest entry.
- `home.html`: Ethics is now the primary CTA (Priority demoted to secondary);
  the `.cta.is-primary` tile now uses the warm `--accent-soft` (#FCEBDA) fill +
  accent border matching the desktop `.cfa-home__cta.is-primary`, so the two
  Homes read as one product.

## Verification
- `CfaHomeTest` (9 tests, incl. the asset guard inverted to require the ethics
  CTA) green; whole main sourceset compiles (new activity/bridge/manifest).
- chrome-devtools-axi on the real asset with injected data: primary tile label
  "Study Ethics — Minimal Pairs", computed bg rgb(252,235,218)=#FCEBDA, border
  rgb(218,92,1)=#DA5C01, click → bridge target "ethics", 5 CTAs total.
- Screenshot: mobile-home-ethics-primary.png
