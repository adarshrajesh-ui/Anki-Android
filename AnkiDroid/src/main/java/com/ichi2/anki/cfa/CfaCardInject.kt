// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — reviewer WebView injection.
//
// The ethics card templates (cfa/ethics_pairs/templates/front.html) grade the
// learner's answer with AI. On the phone that card fetches the server-side AI
// proxy directly from the Reviewer WebView (there is no desktop-style pycmd
// bridge for grading). To honour the SYNCED AI-grading toggle — the same
// cfa_ai_enabled / cfa_ai_grading_enabled col.conf keys the desktop writes and
// sync carries — the card needs to know the toggle state at render time.
//
// The desktop pycmd bridge simply refuses to call the LLM when the toggle is
// off (returning error="ai_off"). The phone card can't read col.conf from JS,
// so we inject the resolved toggle as `window.CFA_AI_GRADING_ENABLED` ahead of
// the card content. When it is explicitly `false` the card shows the honest
// "Deterministic" state instead of hitting the proxy. (undefined => on, so a
// card rendered without this injection keeps working.)

package com.ichi2.anki.cfa

import com.ichi2.anki.libanki.Collection

object CfaCardInject {
    /**
     * A tiny inline script that publishes the resolved AI-grading toggle as a
     * boolean window global. Pure/deterministic so it is unit-testable without a
     * collection or a WebView.
     */
    fun aiGradingToggleScript(enabled: Boolean): String = "<script>window.CFA_AI_GRADING_ENABLED=$enabled;</script>"

    /**
     * Prepend the AI-grading toggle global to rendered card [html] so the ethics
     * card template can honour the synced toggle. Reading col.conf is cheap; on
     * any error the html is returned unchanged (fail-open — the card then behaves
     * as an older build, i.e. AI on).
     */
    fun withAiToggle(
        col: Collection,
        html: String,
    ): String =
        try {
            aiGradingToggleScript(CfaAiClient.aiEnabled(col, CfaAiClient.GRADING_KEY)) + html
        } catch (_: Throwable) {
            html
        }
}
