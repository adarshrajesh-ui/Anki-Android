# Mobile Concept Map — Phase B Pass 4 (M-P4-1): panel-gauge abstain honesty

**Surface:** Concept Map (mobile WebView asset `assets/cfa/concept_map.html`) → side/bottom detail panel gauge.

**Issue (severity: high — honesty/give-up-rule violation):**
When a node was abstaining (no evidence yet, `pct === null`), the panel gauge
was drawn with `style.width = (pct===null?0:pct)+"%"` — i.e. a genuine 0%-width
fill. A measured "you scored 0%" and "no data yet" were visually identical, so
the give-up/abstain rule was silently broken in the panel even though the node
FILL on the map honoured it. This was the exact same defect fixed on desktop in
Pass 4 (D-P4-1); the two platforms share the spec but not the file.

**Fix:**
- Abstaining nodes now render a neutral diagonal **hatch** track (`.gauge.is-nodata`)
  with **no fill bar** (`#p-gauge` hidden), honestly distinct from both empty-0%
  and a partial fill — byte-parity with the desktop `CfaConceptMapPage` treatment.
- Added `role="progressbar"` + `aria-valuetext` to the gauge wrapper: abstaining
  reads "No data yet — awaiting evidence" (no `aria-valuenow`), scored reads
  "N% mastered" — screen readers get the same abstain-vs-zero distinction.

**Proof:** `mobile-panel-gauge-abstain-fix.png` — Fixed Income (abstaining) selected,
panel shows "No data yet — abstaining" with the hatch track; scored nodes (e.g.
Equity 71%) still show a real turquoise fill. Verified live via chrome-devtools-axi:
abstaining → `{isNodata:true, barDisplay:"none", ariaValuenow:null,
ariaValuetext:"No data yet — awaiting evidence"}`; scored → `{isNodata:false,
barWidth:"71%", ariaValuenow:71}`.

**Regression guard:** `CfaConceptMapTest.concept_map_asset_gauge_never_fakes_zero_for_abstain`
fails if the `(pct===null?0:pct)` conflation returns or the hatch path is removed.
