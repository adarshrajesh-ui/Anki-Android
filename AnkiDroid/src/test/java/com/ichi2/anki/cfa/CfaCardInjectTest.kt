// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — reviewer AI-toggle injection tests. Pure/deterministic (no
// collection or WebView): they lock the shape of the window global the ethics
// card reads to honour the synced AI-grading toggle, so front.html's
// `window.CFA_AI_GRADING_ENABLED === false` gate keeps matching.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.startsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CfaCardInjectTest {
    @Test
    fun script_publishes_true_when_enabled() {
        val s = CfaCardInject.aiGradingToggleScript(true)
        // exact global name + boolean literal front.html strict-equals against
        assertThat(s, equalTo("<script>window.CFA_AI_GRADING_ENABLED=true;</script>"))
    }

    @Test
    fun script_publishes_false_when_disabled() {
        val s = CfaCardInject.aiGradingToggleScript(false)
        assertThat(s, equalTo("<script>window.CFA_AI_GRADING_ENABLED=false;</script>"))
        // the exact token front.html gates the proxy fetch on
        assertThat(s, containsString("CFA_AI_GRADING_ENABLED=false"))
    }

    @Test
    fun toggle_is_prepended_ahead_of_the_card_content() {
        // Simulate withAiToggle's composition without a real Collection: the
        // global must come BEFORE the card body so it is set before the card's
        // grade request runs.
        val body = "<!doctype html><html><body>card</body></html>"
        val composed = CfaCardInject.aiGradingToggleScript(false) + body
        assertThat(composed, startsWith("<script>window.CFA_AI_GRADING_ENABLED=false;</script>"))
        assertThat(composed, containsString(body))
    }
}
