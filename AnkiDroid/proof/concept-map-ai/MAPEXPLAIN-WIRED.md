# Mobile Concept Map — batched AI explanation now wired end-to-end

**Feature 5 requirement:** *Click a node → a casual, plain-English AI explanation
of how that score came to be, served from a SINGLE batched AI call on load
(templated fallback when AI is off).* Identical on phone and desktop.

## The gap this closed

The mobile side was fully built (uncommitted, from a crashed iteration):

- `CfaAiClient.explainMap(...)` + `CfaMapExplainResult` + `aiOffMapExplain()`
  (`cfa/CfaAiClient.kt`) — POSTs the node list to `/cfa/mapexplain`, parses the
  `{id -> text}` map, honest `source=="fallback"` on any failure.
- `CfaConceptMapActivity.CfaMapBridge` (`AndroidCfaMap.explainMap`) — reads the
  **synced master AI toggle** off `col.conf`, skips the network entirely when AI
  is off (`aiOffMapExplain`), otherwise does the ONE batched POST off the IO
  dispatcher and returns `window.cfaApplyMapExplain(...)` to the map JS.
- `concept_map.html` — fires `AndroidCfaMap.explainMap(NODEDEFS)` on load, merges
  the per-id wording over its deterministic templates, and shows honest
  provenance (off / loading / AI-generated / AI-failed).

**But the proxy had no `/cfa/mapexplain` route** — so every phone call 404'd and
silently fell back to templated wording. The batched AI explanation never
actually ran on the phone.

## The fix (desktop repo, `tools/cfa/ai_proxy.py`)

Added the `POST /cfa/mapexplain` endpoint. It calls the SHARED pure engine
`cfa.ai.mapexplain.explain_map` (the same function the desktop pycmd bridge
uses), so desktop and phone produce byte-identical batched explanations from one
call. Returns `{ok, source, explanations, model, error}` — exactly the shape the
mobile `CfaAiClient.explainMap` parser expects. AI-off / no-key / any failure →
`source=="fallback"`, empty map, phone keeps templated wording.

## Live end-to-end proof

`POST /cfa/mapexplain` against the running proxy (real GPT-4o-mini via the
desktop `.env` key), three nodes incl. an abstaining one:

```json
{"ok": true, "source": "ai", "model": "gpt-4o-mini", "error": null,
 "explanations": {
   "cfa": "You're at about 58% overall readiness for the CFA exam. ...",
   "topic:equity": "You're at about 62% mastery in Equity Investments, which is a solid start given its 10-15% weight ...",
   "topic:fi": "You're currently abstaining in Fixed Income, meaning there isn't enough graded review data to assess your mastery. ..."
 }}
```

Note the **give-up rule is preserved**: the null-mastery Fixed Income node is
described as *abstaining* with NO invented number — brightness is earned, never
faked.

## Verification

- Desktop: `just cfa-ai-proxy-test` → **11 passed** (3 new: AI path, fallback,
  no-nodes) + the live call above.
- Mobile: `./gradlew :AnkiDroid:testPlayDebugUnitTest --tests
  '...CfaAiClientTest' --tests '...CfaConceptMapTest'` → **BUILD SUCCESSFUL**
  (CfaAiClientTest 22, incl. 6 mapexplain tests; CfaConceptMapTest 9); ktlint
  clean; main sourceset compiles.
