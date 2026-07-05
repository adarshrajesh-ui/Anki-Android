# Phase B — Android residual stock-blue theme leaks retoned (M-P4-3)

**Surface:** the whole Android app theme (every screen inherits it).

**Issue (severity: medium — brand consistency).** A prior shell refactor branded
the light theme's *primary* chrome (`colorPrimary = cfa_navy`, FAB = `cfa_accent`),
but a set of **secondary** theme roles were still shipping stock-AnkiDroid
light-blue and leaked through the navy shell:

| Role (attr) | Before | After |
|---|---|---|
| `colorAccent` (light) | `material_blue_grey_700` | `cfa_navy` |
| `colorSecondaryContainer` (light) | `#C1E7FF` | `cfa_accent_soft` |
| `colorSurfaceContainerHigh` (light) | `#E4F5FD` | `cfa_surface` |
| `colorSurfaceContainer` (light) | `#0F03A9F4` (6%-blue) | `cfa_surface` |
| `preferenceCategoryTitleTextColor` (light · Settings) | `material_light_blue_800` | `cfa_accent_ink` |
| `editTextHighlightColor` (light · editor) | `material_light_blue_200` | `cfa_accent_soft` |
| `customTabNavBarColor` (light) | `material_light_blue_500` | `cfa_navy` |
| `snackbarButtonTextColor` (light · on dark bar) | `material_light_blue_300` | `cfa_accent_on_navy` |
| `progressDialogButtonTextColor` (light) | `material_light_blue_500` | `cfa_navy` |
| `incrementerButtonBackground` (light) | `material_light_blue_500` | `cfa_navy` |
| `fab_normal` / `fab_pressed` (**dark** theme FAB) | `material_light_blue_700/900` | `cfa_accent` / `cfa_accent_hover` |

**Fix.** Retone each to a token from `values/cfa.xml` (the phone's mirror of the
desktop CFA design system). The learned semantic colours — the Again/Hard/Good/Easy
ease buttons, the Learn/Review counts and the browser flags — are intentionally
left untouched.

**Verification.**
- `CfaThemeBrandingTest` (2 tests, green) parses `theme_light.xml` / `theme_dark.xml`
  and asserts each retoned attr references its CFA token and the specific stock
  light-blue references are gone.
- `./gradlew :AnkiDroid:processPlayDebugResources` merges cleanly (all refs resolve).
- Before/after swatch comparison: `theme-branding.png`.

**Deferred (now CLOSED — see `DARK-BLACK-REBRAND.md`, M-P4-4).** The **dark & black**
themes' `colorPrimary` / `colorAccent` and their dependent tints (switches,
activated widgets, tab indicator, "new" count, dynamic-deck names, dialog/snackbar
action text) were retoned to the dark-safe warm `cfa_accent_on_navy` (#F0894A,
AA-safe on the dark surfaces) with navy for the light-surface roles. This pass
fixed the safe light-theme leaks + the glaring dark-mode FAB; the follow-up
increment closed the dark/black accent rebrand.
