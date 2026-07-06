# M-P4-4 — Mobile AI Settings (the two AI-feature toggles)

## Finding (Phase B, cross-platform parity / objective-fidelity)

The objective requires the two AI features "each with its own toggle … displayed
consistently on desktop and phone," and settings "discoverable from an obvious
in-app entry — no hunting." On desktop this is the `cfa_ai_settings.py` dialog +
a Home footer chip (`AI On/Off · settings`). On the **phone** the toggles were
read-only: `CfaAiClient` honoured the synced `col.conf` keys (iters 18–19) but
there was **no UI to read or change them** — the phone Home showed no AI state
and had no settings screen at all. So "each toggle, displayed consistently on
both platforms" was unmet on mobile.

## Fix

A native mobile AI Settings screen, the phone equivalent of the desktop dialog,
built as a self-contained CFA-styled WebView asset (matching Home / Concept Map /
Readiness — one design system):

- `assets/cfa/ai_settings.html` — three toggles (MASTER + AI ethics grading + AI
  tab-to-fill), rendered from injected `window.CFA_AI_SETTINGS`. Each switch
  persists through the `AndroidCfaAiSettings.setToggle` bridge. Turning MASTER
  off greys + disables the feature rows and shows the honest state line
  "AI is off — every feature is using its deterministic fallback." Copy states
  the phone never holds the OpenAI key (it calls the desktop proxy) and that
  every feature degrades deterministically with AI off.
- `cfa/CfaAiSettings.kt` — pure builder (AI-first default-ON, mirroring the
  desktop `get_ai_toggles`) + `col.conf` read/write keyed to the SAME shared
  keys `CfaAiClient` gates on, so a toggle here SYNCS to the desktop and back.
  `setToggle` refuses any key outside the three-key allow-list.
- `CfaAiSettingsActivity.kt` + `activity_cfa_ai_settings.xml` — WebView host +
  the `AndroidCfaAiSettings` @JavascriptInterface bridge; manifest registered.
- Home footer: a tappable `AI On/Off · settings` chip (`home.html` + the
  `CfaHomeActivity` `aiSettings` bridge target + injected `window.CFA_AI_ENABLED`),
  matching the desktop Home chip.

## Verification

- `CfaAiSettingsTest` — 7 tests green (default-ON payload, explicit values, the
  three-key shared-with-desktop allow-list, asset/bridge/Home-chip/manifest
  source guards). Full `com.ichi2.anki.cfa.*` build compiles the whole main
  sourceset (validates the Activity / manifest / layout / string wiring).
- ktlint clean on all new/edited files.
- Device-observable (chrome-devtools-axi, stubbed bridge): toggling tab-fill
  fires `setToggle("cfa_ai_tabfill_enabled", true)`; MASTER off fires
  `setToggle("cfa_ai_enabled", false)`, disables both feature toggles, and
  updates the state line.

## Evidence

- `ai-settings-master-on.png` — master + grading on (green), tab-fill off.
- `ai-settings-master-off.png` — master off greys the two feature rows.
- `home-with-ai-chip.png` — the Home footer `AI On · settings` chip (green).
