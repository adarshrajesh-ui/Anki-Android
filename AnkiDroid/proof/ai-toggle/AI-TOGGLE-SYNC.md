# Mobile honours the SYNCED AI toggle (cfa_ai_enabled)

## Defect
The desktop stores its AI toggles in `col.conf` (`qt/aqt/cfa_ai_settings.py`):
`cfa_ai_enabled` (master), `cfa_ai_tabfill_enabled`, `cfa_ai_grading_enabled`
— all default ON. These keys SYNC with the collection. But the mobile
`CfaAiClient` only read the proxy URL/token from config and **never checked the
master/feature AI toggle**: turning AI off on the desktop synced `cfa_ai_enabled
= false` to the phone, yet the phone still POSTed to the LLM proxy on tab-fill
and grading. That violates the objective's "each feature has its own toggle" and
"with AI off, both features degrade gracefully / say deterministic".

## Fix
`CfaAiClient` now reads the SAME synced keys and gates the collection-based
overloads on the desktop rule (master AND feature; both default ON):
- `aiEnabled(master, feature)` — pure truth-table (desktop `ai_active`).
- `aiEnabled(col, featureKey)` — reads `cfa_ai_enabled` + the feature key.
- `fill(col, …)` returns `aiOffFill()` (source=fallback, error="ai_off", no
  network) when the tab-fill toggle is off.
- `grade(col, …)` returns `aiOffGrade()` (error="ai_off", cited Standard
  preserved) when the grading toggle is off.
- `NoteEditorFragment.fillWithAi` short-circuits with a friendly "AI is turned
  off" snackbar and no "drafting" flash when off.

So a desktop toggle change now reaches the phone and is honoured — no proxy
call, deterministic fallback, honest `ai_off` provenance.

## Proof
`./gradlew :AnkiDroid:testPlayDebugUnitTest --tests com.ichi2.anki.cfa.CfaAiClientTest`
→ 16 tests, 0 failures (compiles the whole main sourceset, gating the
NoteEditorFragment edit too). New tests: toggle truth-table, default-ON when
unset, and the honest ai_off fill/grade shapes.
