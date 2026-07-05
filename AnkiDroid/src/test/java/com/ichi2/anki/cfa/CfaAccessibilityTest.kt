// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Phase B Pass-3 (ruthless) screen-reader accessibility guard.
//
// The ruthless pass audited the Exam Readiness screen for a NON-sighted user
// and found a genuine WCAG 2.1 defect (SC 1.3.1 Info and Relationships / 4.1.2
// Name, Role, Value): the score cards and per-topic recall rows are built as
// separate sibling TextViews with no accessibility grouping, so TalkBack
// announces each fragment as its own swipe — "Ethics & Professional Standards"
// then, disconnected, "62% recall · 3 reviews" — and the name↔number
// relationship is lost.
//
// The fix (CfaAccessibility.kt + CfaExamReadinessActivity) makes each row / card
// a single screen-reader focus node with one coherent contentDescription. These
// tests lock the pure label builders (device-free, like the desktop
// deadline.ts / contrast.test.ts helpers) and add a source-parsing regression
// guard that fails if the grouping is ever removed from the activity.

package com.ichi2.anki.cfa

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class CfaAccessibilityTest {
    private fun covered(
        name: String,
        recall: Double,
        reviews: Int,
    ) = TopicRecall(
        topic = "los::x",
        displayName = name,
        weight = 0.1,
        gradedReviews = reviews,
        avgR = recall,
        covered = true,
    )

    private fun uncovered(name: String) =
        TopicRecall(
            topic = "los::x",
            displayName = name,
            weight = 0.1,
            gradedReviews = 0,
            avgR = null,
            covered = false,
        )

    private fun scored(
        point: Double,
        low: Double,
        high: Double,
    ) = HonestScore(abstain = false, reason = "", point = point, rangeLow = low, rangeHigh = high)

    private fun abstaining(reason: String) = HonestScore(abstain = true, reason = reason, point = null, rangeLow = null, rangeHigh = null)

    // ---- The fix: pure, coherent single-string labels ------------------------

    @Test
    fun `covered topic row announces name together with its recall as one phrase`() {
        val label = topicRowContentDescription(covered("Ethics & Professional Standards", 0.624, 3))
        // A single coherent utterance, not two disconnected TalkBack swipes.
        assertThat(label, equalTo("Ethics & Professional Standards: 62% recall from 3 reviews"))
    }

    @Test
    fun `single-review topic uses the singular 'review'`() {
        val label = topicRowContentDescription(covered("Economics", 0.90, 1))
        assertThat(label, equalTo("Economics: 90% recall from 1 review"))
    }

    @Test
    fun `uncovered topic announces name with a spoken 'no data yet' not a bare fragment`() {
        val label = topicRowContentDescription(uncovered("Derivatives"))
        assertThat(label, equalTo("Derivatives: no recall data yet"))
    }

    @Test
    fun `scored card announces label, value and range as one phrase`() {
        val label = scoreCardContentDescription("Readiness", scored(0.92, 0.88, 0.95))
        assertThat(label, equalTo("Readiness: 92%, range 88% to 95%"))
    }

    @Test
    fun `abstaining card announces the label with a spoken awaiting-reviews reason`() {
        val label = scoreCardContentDescription("Memory", abstaining("Needs 200 graded reviews (have 12)."))
        assertThat(label, equalTo("Memory: awaiting reviews. Needs 200 graded reviews (have 12)."))
    }

    @Test
    fun `abstaining card honours the hero composite override when supplied`() {
        val label =
            scoreCardContentDescription(
                "Readiness",
                abstaining("verbatim mem+perf reason"),
                abstainOverride = "Keep studying — the Memory and Performance scores need more data first.",
            )
        assertThat(label, containsString("Keep studying"))
        // The verbatim concatenated reason must NOT leak into the hero label.
        assertThat(label.contains("verbatim mem+perf reason"), equalTo(false))
    }

    // ---- Regression guard: the activity groups rows/cards for screen readers --

    @Test
    fun `readiness activity groups score cards and topic rows into single screen-reader nodes`() {
        val src = findActivitySource()
        // Both the card and the row must get a coherent contentDescription…
        assertThat(src, containsString("card.contentDescription = scoreCardContentDescription("))
        assertThat(src, containsString("row.contentDescription = topicRowContentDescription("))
        // …and be exposed to TalkBack as ONE focusable node each.
        assertThat(src, containsString("ViewCompat.setScreenReaderFocusable(card, true)"))
        assertThat(src, containsString("ViewCompat.setScreenReaderFocusable(row, true)"))
        // The inner fragments must be hidden from the accessibility tree so they
        // are not read a second time as loose nodes.
        val hidden = "View.IMPORTANT_FOR_ACCESSIBILITY_NO"
        assertThat(
            "expected >=5 child TextViews marked $hidden",
            src.split(hidden).size - 1,
            org.hamcrest.Matchers.greaterThanOrEqualTo(5),
        )
    }

    private fun findActivitySource(): String {
        val rel = "src/main/java/com/ichi2/anki/CfaExamReadinessActivity.kt"
        val candidates = listOf(rel, "AnkiDroid/$rel", "../AnkiDroid/$rel")
        var dir = java.io.File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            for (c in candidates) {
                val f = java.io.File(dir, c)
                if (f.exists()) return f.readText()
            }
            dir = dir.parentFile ?: return@repeat
        }
        error("could not locate CfaExamReadinessActivity.kt from ${System.getProperty("user.dir")}")
    }
}
