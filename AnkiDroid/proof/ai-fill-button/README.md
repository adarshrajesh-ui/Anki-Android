# Mobile AI-Fill button (Feature 3 — phone parity)

**Objective:** "on phone make it a button while on desktop it is tab to fill".

## Before
`action_cfa_ai_fill` lived in the note-editor **overflow** menu
(`showAsAction="never"`) with no icon — undiscoverable; the only non-overflow
trigger was a hardware-keyboard Tab (`KEYCODE_TAB`), which phones lack.

## After
Promoted to a visible toolbar affordance: `showAsAction="ifRoom"` with a new
`ic_cfa_ai_fill` lightbulb-sparkle icon (solid white for the dark note-editor
action bar). Handler unchanged (`fillWithAi()`), so AI-off / AI-failed still
report honestly via snackbar (`cfa_ai_fill_unavailable`).

## Evidence
- `menu-diff.txt` — the source diff.
- `processPlayDebugResources` merged clean (EXIT=0); merged
  `packaged_res/playDebug/.../menu/note_editor.xml` now carries
  `android:icon="@drawable/ic_cfa_ai_fill"` and the drawable is packaged.
