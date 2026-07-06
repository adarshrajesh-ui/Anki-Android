# Phase B — Mobile Reviewer ease-button retone (M-P4-3)

**Surface:** the four answer/ease buttons (Again / Hard / Good / Easy) in the
default (classic) AnkiDroid reviewer — `include_reviewer_answer_buttons.xml`,
backed by `?attr/againButtonRef`…`?attr/easyButtonRef` → the
`footer_button_{again,hard,good,easy}[_dark].xml` drawables.

## Finding — severity MAJOR (design-system consistency / "coloring is ugly")

The most-used surface in a spaced-repetition app shipped its four answer buttons
in **raw stock Material colours**:

| Tier | Before (light) | Before (dark) |
|------|----------------|---------------|
| Again | `material_red_700` | `material_red_800` |
| Hard  | `material_blue_grey_700` | `material_blue_grey_800` |
| Good  | `material_green_500` | `material_green_800` |
| Easy  | **`material_light_blue_500`** | `material_light_blue_800` |

The **Easy** button is the exact stock-AnkiDroid light-blue (`#03A9F4`) the
objective explicitly flags as "ugly … copy the coloring scheme on desktop." The
desktop reviewer answer bar (`qt/aqt/cfa_chrome.py` `_reviewer_bottom_css`, iter
21) already reads as CFA, so this was a clear mobile parity gap.

## Fix (iter 28)

Retoned all eight drawables (light + dark) to one on-brand **CFA four-tier
scheme** — rising confidence: **fail-red → neutral-slate → pass-green → navy** —
via new shared `cfa_ease_*` tokens in `values/cfa.xml`:

| Tier | Token | Value | White-text contrast |
|------|-------|-------|---------------------|
| Again | `cfa_ease_again` | `#B91C1C` | 5.8:1 ✓ |
| Hard  | `cfa_ease_hard`  | `#4D5C6D` | 6.9:1 ✓ |
| Good  | `cfa_ease_good`  | `#0F6E33` (cfa_pass darkened for AA) | 6.4:1 ✓ |
| Easy  | `cfa_ease_easy`  | `#122B46` (brand navy — "mastered") | 15:1 ✓ |

Every base fill clears **WCAG AA (≥4.5:1)** against the white
`answerButtonTextColor`, so the retone introduces no contrast regression. The
`_hover` shades back the pressed/focused states. Light and dark drawables point
at the **same** tokens so the ease bar reads identically in both themes.

The `footer_button_all_plain` / `footer_button_all_black` monochrome drawables
(the minimalist Plain/Black themes) are already neutral (no stock blue) and were
left untouched.

## Verification

- **`CfaReviewerEaseButtonsTest`** (3 tests, green): each light + dark drawable
  now references its `cfa_ease_*` token and no longer references the stock
  `@color/material_*` fill it used to; the four base tokens are pinned to their
  exact AA-safe values and the hover shades exist.
- `CfaThemeBrandingTest` still green (its stale "ease buttons keep learned Anki
  affordance" note updated to point here).
- Ran with `--rerun-tasks --no-configuration-cache` to force a real
  recompile + resource merge (config-cache reports UP-TO-DATE otherwise).
- **Before/after evidence:** `ease-buttons.{html,png}` — a faithful reproduction
  of the four equal-weight bars showing the stock light-blue Easy replaced by
  brand navy in a cohesive CFA scheme.

## Not in scope (follow-up)

The opt-in experimental "new reviewer" (`ui/windows/reviewer`, default **off**)
colours its `AnswerButton`s separately (`view_answer_area.xml`) and was not
touched here; retoning it is a candidate future increment.
