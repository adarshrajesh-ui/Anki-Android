// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — screen-reader accessibility guards for the native CFA screens.
//
// The Exam Readiness screen is now the assets/cfa/readiness.html WebView
// (matching the desktop CfaReadinessPage + the CFA Home / Concept Map pattern),
// so TalkBack reads its content through native HTML semantics rather than
// hand-grouped TextView contentDescriptions. This test guards two things: (1)
// the WebView asset carries the HTML accessibility affordances a non-sighted
// user needs (the verdict hero's role="group" + a composed aria-label, a real
// <table> with <th> column headers, HTML-escaped injected text), and (2) the
// still-native Exam Config date field announces itself as a control (M-P3-3).

package com.ichi2.anki.cfa

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class CfaAccessibilityTest {
    // ---- Regression guard: the WebView readiness asset is screen-reader-ready --

    @Test
    fun `readiness webview asset carries html accessibility semantics`() {
        val html = findRepoFile("src/main/assets/cfa/readiness.html")
        // The verdict hero is a labelled group so TalkBack reads the call + lead
        // + note as one coherent utterance, not fragmented spans.
        assertThat(html, containsString("role=\"group\""))
        assertThat(html, containsString("\"aria-label\""))
        assertThat(html, containsString("heroEl.setAttribute("))
        // The per-topic recall is a real table with column headers (SC 1.3.1),
        // so a non-sighted user hears "Recall R, 62%" not a bare "62%".
        assertThat(html, containsString("<thead>"))
        assertThat(html, containsString("<th"))
        // Injected score/topic text is HTML-escaped (no markup injection).
        assertThat(html, containsString("function esc("))
    }

    // ---- M-P3-3: the exam-date field is a tappable control, not inert text ---

    @Test
    fun `unset exam-date field announces itself as a control that opens the picker`() {
        val label = examDateFieldContentDescription(null)
        assertThat(label, containsString("Exam date"))
        assertThat(label, containsString("not set"))
        // Non-sighted users must learn activating it does something.
        assertThat(label, containsString("Double-tap"))
    }

    @Test
    fun `blank exam-date is treated as unset, not spoken as an empty value`() {
        assertThat(examDateFieldContentDescription("  "), equalTo(examDateFieldContentDescription(null)))
    }

    @Test
    fun `set exam-date field announces its value and that it can be changed`() {
        val label = examDateFieldContentDescription("2026-08-22")
        assertThat(label, equalTo("Exam date, 2026-08-22. Double-tap to change."))
    }

    @Test
    fun `exam-config date box is a real touch target with a ripple and a picker click, not a false affordance`() {
        val layout = findRepoFile("src/main/res/layout/activity_cfa_exam_config.xml")
        // The box that looks like an input must behave like a control:
        // clickable + focusable + a >=48dp Material/WCAG-2.5.5 touch target +
        // a ripple foreground so a tap is perceivable.
        val box = layout.substringAfter("@+id/cfa_config_date_value").substringBefore("/>")
        assertThat(box, containsString("android:clickable=\"true\""))
        assertThat(box, containsString("android:focusable=\"true\""))
        assertThat(box, containsString("android:minHeight=\"48dp\""))
        assertThat(box, containsString("?attr/selectableItemBackground"))
        // The redundant secondary "Pick date" button must be gone — the box IS
        // the control now, so there is exactly one date affordance.
        assertThat(layout.contains("cfa_config_pick_date"), equalTo(false))

        val activity = findRepoFile("src/main/java/com/ichi2/anki/CfaExamConfigActivity.kt")
        assertThat(activity, containsString("dateView.setOnClickListener"))
        assertThat(activity, containsString("examDateFieldContentDescription(selectedDate)"))
        // No lingering wiring to the removed button.
        assertThat(activity.contains("cfa_config_pick_date"), equalTo(false))
    }

    private fun findRepoFile(rel: String): String {
        val candidates = listOf(rel, "AnkiDroid/$rel", "../AnkiDroid/$rel")
        var dir = java.io.File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            for (c in candidates) {
                val f = java.io.File(dir, c)
                if (f.exists()) return f.readText()
            }
            dir = dir.parentFile ?: return@repeat
        }
        error("could not locate $rel from ${System.getProperty("user.dir")}")
    }
}
