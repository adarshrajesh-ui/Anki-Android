// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Phase B Pass-3 (ruthless) screen-reader accessibility helpers.
//
// The Exam Readiness screen builds its score cards and per-topic recall rows
// programmatically as SEPARATE sibling TextViews inside a plain LinearLayout.
// With no accessibility grouping, TalkBack treats every TextView as its own
// focusable node, so a topic row is announced as two disconnected swipes —
// "Ethics & Professional Standards" … then, separately, "62% recall · 3
// reviews" — and a score card fragments into label / value / range. The
// relationship between a name and its number is lost to a non-sighted user
// (WCAG 2.1 SC 1.3.1 Info and Relationships; 4.1.2 Name, Role, Value).
//
// The fix (mirroring the desktop Pass-3 pure-helper pattern) makes each row /
// card a single screen-reader focus node with one coherent contentDescription
// built by the pure functions below. They are pure (no Android Context) so they
// are unit-testable without a device, exactly like the desktop deadline.ts /
// contrast.test.ts helpers.

package com.ichi2.anki.cfa

import kotlin.math.roundToInt

/** Format a 0..1 fraction as a whole-number percent, matching the visible text. */
internal fun cfaPct(v: Double): String = "${(v * 100).roundToInt()}%"

/**
 * One coherent spoken label for a per-topic recall row, so TalkBack announces
 * the topic name together with its recall instead of as two separate nodes.
 *
 * e.g. "Ethics & Professional Standards: 62% recall from 3 reviews" or
 *      "Economics: no recall data yet".
 */
fun topicRowContentDescription(topic: TopicRecall): String =
    if (topic.covered && topic.avgR != null) {
        "${topic.displayName}: ${cfaPct(topic.avgR)} recall from ${topic.gradedReviews} " +
            if (topic.gradedReviews == 1) "review" else "reviews"
    } else {
        "${topic.displayName}: no recall data yet"
    }

/**
 * One coherent spoken label for a score card, so TalkBack announces the score
 * name together with its value/range (or its abstain reason) instead of as
 * fragmented label / value / range nodes.
 *
 * e.g. "Readiness: 92%, range 88% to 95%" or
 *      "Readiness: awaiting reviews. Keep studying — …".
 */
fun scoreCardContentDescription(
    label: String,
    score: HonestScore,
    abstainOverride: String? = null,
): String =
    if (score.abstain) {
        "$label: awaiting reviews. ${abstainOverride ?: score.reason}"
    } else {
        "$label: ${cfaPct(score.point!!)}, range ${cfaPct(score.rangeLow!!)} to ${cfaPct(score.rangeHigh!!)}"
    }

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
