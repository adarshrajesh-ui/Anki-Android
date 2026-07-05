# Ethics card honours the synced AI-grading toggle on the phone

**Finding (honesty / provenance / objective fidelity).** The mobile ethics card
(`cfa/ethics_pairs/templates/front.html`, shared with desktop) fetched the AI
grading proxy (`http://10.0.2.2:27702/cfa/grade`) **unconditionally on Android**,
ignoring the synced AI toggle. The desktop pycmd bridge refuses the LLM call when
grading is off (returns `error="ai_off"` → the "Deterministic" provenance box,
per iter 15/D-P4-7). So with AI switched off, the desktop said *Deterministic* but
the phone still called the AI and rendered *AI feedback* — the objective's
"say deterministic when off" honesty rule was violated on the phone.

**Fix.**
- `CfaCardInject.kt` (new, pure/testable) publishes the resolved toggle as
  `window.CFA_AI_GRADING_ENABLED` from `col.conf` (`cfa_ai_enabled &&
  cfa_ai_grading_enabled` — the same synced keys the desktop writes), prepended
  to the rendered card in `AbstractFlashcardViewer.updateCard`. Fail-open on any
  error (older-build behaviour = AI on).
- `front.html` Android branch now checks `window.CFA_AI_GRADING_ENABLED === false`
  BEFORE the proxy fetch; when off it calls `renderAiGrade({source:"fallback",
  error:"ai_off"})` — the honest "Deterministic" state, no network. (undefined =>
  on, so cards rendered without the injection keep working.)

**Verification.**
- Mobile: `CfaCardInjectTest` (3) + `CfaAiClientTest` (16) green; whole main
  sourceset compiles (`./gradlew :AnkiDroid:testPlayDebugUnitTest`).
- Desktop: `cfa/ethics_pairs/tests/test_ai_provenance.py`
  `test_mobile_grade_honours_the_synced_ai_toggle` green; full ethics suite
  **130 passed** (`just cfa-test`).
- Device-observable (`gate-proof.{html,png}`, driven in a real browser over the
  SHIPPED gate logic):
  - `toggle=OFF   -> fetchCalls=0  box='Deterministic AI grading is off …'`
  - `toggle=ON    -> fetchCalls=1  (proxy called)`
  - `toggle=unset -> fetchCalls=1  (proxy called, back-compat)`
