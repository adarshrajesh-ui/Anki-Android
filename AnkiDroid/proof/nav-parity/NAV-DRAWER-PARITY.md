# Mobile CFA nav-drawer parity (Study + Ethics)

## Finding (Phase B — native-shell parity)
The desktop top bar exposes five CFA sections natively: **Home / Study / Ethics /
Concept Map / Readiness** (`qt/aqt/toolbar.py::_centerLinks`). The phone's
navigation drawer only carried **three** of them — Home, Readiness, Concept Map.
**Study** (by exam priority) and **Ethics** (Minimal-Pairs drill) were reachable
ONLY as CTAs on the Home dashboard, so the objective's requirement — "CFA
navigation (Home/Today, Study, Concept Map, Readiness, Ethics) is present
natively across the whole app" — was unmet on mobile: from any non-Home screen
the drawer offered no way to start either drill.

## Fix
- Added two nav-drawer items to `res/menu/navigation_drawer.xml`, reordered so
  the drawer matches the desktop top-bar order: Home → **Study** → **Ethics** →
  Concept Map → Readiness.
- New strings `cfa_nav_study` ("Study") and `cfa_nav_ethics` ("Ethics") in
  `values/cfa.xml`.
- New CFA-tinted vector icons `ic_cfa_study.xml` (Material "school") and
  `ic_cfa_ethics.xml` (Material "balance" scales), following the drawer's
  `?colorControlNormal` tint like the other CFA nav icons.
- Wired the two new handler branches in `NavigationDrawerActivity.onNavigationItemSelected`:
  `nav_cfa_study → CfaExamPriorityActivity.getIntent(...)`,
  `nav_cfa_ethics → CfaEthicsStudyActivity.getIntent(...)` — the exact native
  launch paths the Home dashboard's `priority` / `ethics` CTA bridge already uses,
  so no new engine work.

## Verification
- `./gradlew :AnkiDroid:testPlayDebugUnitTest --tests com.ichi2.anki.cfa.CfaHomeTest --rerun-tasks`
  → BUILD SUCCESSFUL (full main-sourceset recompile + resource merge validating
  the new icons/menu/strings), **11 tests, 0 failures**.
- Two new source-parsing regression guards in `CfaHomeTest`:
  - `nav_drawer_exposes_all_five_cfa_sections` — the menu XML carries all five
    `nav_cfa_*` ids plus the two new CFA icons.
  - `nav_drawer_handler_launches_study_and_ethics_natively` — the handler routes
    Study→CfaExamPriorityActivity and Ethics→CfaEthicsStudyActivity.

Presentation/navigation only — no scheduler, score, or engine change.
