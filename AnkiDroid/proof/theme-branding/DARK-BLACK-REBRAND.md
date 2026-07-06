# Phase B — Dark & Black theme interactive tints rebranded (M-P4-4)

**Surface:** the whole Android app in **Dark** and **Black** themes (every screen
inherits the theme).

**Issue (severity: medium — brand consistency).** The prior pass (M-P4-3) branded
the *light* theme and fixed the dark-mode FAB, but explicitly **deferred** the
dark/black themes' `colorPrimary` / `colorAccent`, which still shipped
stock-AnkiDroid light-blue (`material_blue_400/500`). Because those drive every
interactive tint — switches, checkboxes, sliders, activated widgets, text-button
labels, links, the Settings category headers (`?attr/colorAccent`), the active
tab indicator, the "new" count and the dynamic-deck names — a user in dark mode
saw stock light-blue everywhere the light user saw CFA navy/accent.

**Why not just reuse `cfa_navy`?** Navy (#122B46) is nearly invisible on the dark
`#303030` / black surface. Dark mode needs a *bright* brand tint, so it uses the
design system's warm **`cfa_accent_on_navy` (#F0894A)** — the same warm identity
the dark FAB already uses. Contrast is WCAG-AA safe: **5.25:1 on #303030**, **8.4:1
on black**. Surface-keyed action text stays legible: navy on the *light* dark-theme
snackbar (9.3:1), warm accent on the *dark* dialogs.

| Role (attr) | Before (dark / black) | After |
|---|---|---|
| `colorPrimary` | `material_blue_400` | `cfa_accent_on_navy` |
| `colorAccent` | `material_blue_400` / `material_blue_500` | `cfa_accent_on_navy` |
| `colorSecondaryContainer` (dark) | `#3C5A6E` (blue-grey) | `cfa_navy` |
| `colorSurfaceContainer` (dark) | `#0F42A5F5` (6%-blue) | `#0F122B46` (6%-navy) |
| `tabActiveIconColor` | `material_blue_500` | `cfa_accent_on_navy` |
| `dynDeckColor` | `material_light_blue_500` | `cfa_accent_on_navy` |
| `newCountColor` | `material_indigo_200` / `material_blue_600` | `cfa_accent_on_navy` |
| `editTextHighlightColor` | `material_light_blue_900` | `cfa_navy` |
| `snackbarButtonTextColor` (dark, on light bar) | `material_light_blue_900` | `cfa_navy` |
| `snackbarButtonTextColor` (black, on dark bar) | `material_light_blue_300` | `cfa_accent_on_navy` |
| `progressDialogButtonTextColor` | `material_light_blue_400` | `cfa_accent_on_navy` |

**Fix.** Retone each to a token from `values/cfa.xml`. `preferenceCategoryTitleTextColor`
and `widgetColorActivated` are `?attr/colorAccent`, so they follow the retone
automatically. The learned semantic colours — the Again/Hard/Good/**Easy** ease
buttons (Easy keeps its blue affordance), the Learn/Review counts, the flags — are
intentionally left untouched.

**Verification.**
- `CfaThemeBrandingTest` (now **4** tests, green): the two new tests parse
  `theme_dark.xml` / `theme_black.xml`, assert each retoned attr references its
  CFA token, and assert the specific stock blues (`material_blue_400/500/600`,
  `material_indigo_200`, `material_light_blue_500`) are gone.
- `./gradlew :AnkiDroid:processPlayDebugResources --rerun-tasks` merges cleanly
  (every `@color` ref resolves; the 6%-navy hex is valid ARGB).
- Before/after swatch comparison: `dark-black-rebrand.png`.

This closes the "Deferred (honest)" item recorded in `THEME-BRANDING.md`.
