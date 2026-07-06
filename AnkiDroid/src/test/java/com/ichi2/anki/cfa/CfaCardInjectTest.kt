// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — reviewer AI-toggle injection tests. Pure/deterministic (no
// collection or WebView): they lock the shape of the window global the ethics
// card reads to honour the synced AI-grading toggle and configured proxy, so
// front.html's Android grade path keeps matching.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CfaCardInjectTest {
    @Test
    fun script_publishes_true_and_proxy_config_when_enabled() {
        val s = CfaCardInject.aiConfigScript(true, "http://host:27702", "secret")
        // exact global name + boolean literal front.html strict-equals against
        assertThat(s, containsString("window.CFA_AI_GRADING_ENABLED=true;"))
        assertThat(s, containsString("window.CFA_AI_PROXY_URL="))
        assertThat(s, containsString("host:27702"))
        assertThat(s, containsString("window.CFA_AI_PROXY_TOKEN=\"secret\";"))
    }

    @Test
    fun script_publishes_false_when_disabled() {
        val s = CfaCardInject.aiConfigScript(false)
        // the exact token front.html gates the proxy fetch on
        assertThat(s, containsString("CFA_AI_GRADING_ENABLED=false"))
        assertThat(s, containsString("CFA_AI_PROXY_URL"))
        assertThat(s, containsString("CFA_AI_PROXY_TOKEN"))
    }

    @Test
    fun toggle_is_prepended_ahead_of_the_card_content() {
        // Simulate withAiToggle's composition without a real Collection: the
        // global must come BEFORE the card body so it is set before the card's
        // grade request runs.
        val body = "<!doctype html><html><body>card</body></html>"
        val composed = CfaCardInject.aiConfigScript(false) + body
        assertThat(composed, startsWith("<script>window.CFA_AI_GRADING_ENABLED=false;"))
        assertThat(composed, containsString(body))
    }
}
