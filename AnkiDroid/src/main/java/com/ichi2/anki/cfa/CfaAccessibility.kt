// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — screen-reader accessibility helpers for the native CFA screens.
//
// Note: the Exam Readiness score cards / per-topic recall rows USED to be built
// programmatically as separate sibling TextViews, which needed pure
// contentDescription builders (topicRowContentDescription /
// scoreCardContentDescription) to group each row/card into one TalkBack node.
// That screen is now the assets/cfa/readiness.html WebView (matching the desktop
// CfaReadinessPage + the CFA Home / Concept Map pattern), so TalkBack reads its
// content through native HTML semantics (headings, a real <table> with <th>
// headers, the verdict hero's role="group" + aria-label) — no manual grouping
// needed. The remaining helper below serves the still-native Exam Config screen.
//
// It is pure (no Android Context) so it is unit-testable without a device,
// exactly like the desktop deadline.ts / contrast.test.ts helpers.

package com.ichi2.anki.cfa

/**
 * One coherent spoken label for the exam-date field on the Exam Config screen.
 *
 * Phase B Pass-3 (M-P3-3): the date box is styled as a filled input (surface
 * background, padding, 18sp navy) but is a TAPPABLE control that opens the date
 * picker — not inert text. TalkBack must therefore announce it as a control with
 * its current value AND the action that activating it performs; otherwise a
 * non-sighted user hears only static text and never discovers it opens a picker
 * (WCAG 2.1 SC 4.1.2 Name, Role, Value; 1.3.1 Info and Relationships).
 *
 * [dateValue] is the selected ISO date (yyyy-MM-dd) or null/blank when unset.
 *
 * e.g. "Exam date, 2026-08-22. Double-tap to change." or
 *      "Exam date, not set. Double-tap to choose your exam date."
 */
fun examDateFieldContentDescription(dateValue: String?): String =
    if (dateValue.isNullOrBlank()) {
        "Exam date, not set. Double-tap to choose your exam date."
    } else {
        "Exam date, $dateValue. Double-tap to change."
    }
