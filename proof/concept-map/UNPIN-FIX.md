# Mobile Concept Map — Phase B Pass 4 (M-P4-2): pinned node is reversible

**Surface:** Concept Map (mobile WebView asset `assets/cfa/concept_map.html`) → node tap / detail panel.

**Issue (severity: medium — user-control / dead-end):**
Tapping a node only ever ADDED the `.sel` highlight and populated the panel
(`pick()` was one-way). There was no gesture to unpin: once a user tapped a
node, they were stuck on that node's explanation with no way back to the calm
placeholder panel except reloading the screen. This is the same one-way
selection the approved spec ships with, and the exact defect fixed on desktop in
Pass 4 (D-P4-5); the two platforms share the spec but not the file.

**Fix (byte-parity with the desktop `CfaConceptMapPage` unpin fix):**
- `pick()` now TOGGLES: tapping the already-pinned node clears `.sel` and calls
  `resetPanel()`, restoring the exact initial placeholder panel (captured as
  `PANEL_INIT` at end of build, so the bold-tag copy + AI-provenance line come
  back verbatim).
- **Escape** unpins from anywhere via a `keydown` listener — the keyboard
  "emergency exit" that mirrors desktop.
- A discoverable, always-visible hint was added to the map caption:
  "tap a pinned node again (or press Esc) to unpin".

**Verification:**
- Drove the real asset in Chrome (data shim injected) and read `.node.sel`:
  - initial `pinned=false`
  - tap centre → `pinned=true`, panel title = "CFA"
  - tap same node again → `pinned=false`, panel title back to "Your CFA map"
  - tap again → `pinned=true`; press **Escape** → `pinned=false`, placeholder restored
  - hint string present in `.mapcap .meta`
- Screenshot of the restored placeholder state: `mobile-unpin-fix.png`.
- Regression guard `CfaConceptMapTest.concept_map_asset_pinned_node_is_reversible`
  (asserts `wasSel` / `resetPanel` / `"Escape"` / "to unpin" present) — 8/8 tests green.
