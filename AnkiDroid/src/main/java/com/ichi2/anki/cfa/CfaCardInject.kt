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
import org.json.JSONObject

object CfaCardInject {
    /**
     * A tiny inline script that publishes the resolved AI-grading toggle and
     * proxy config as window globals. Pure/deterministic so it is unit-testable
     * without a collection or a WebView.
     */
    fun aiConfigScript(
        enabled: Boolean,
        proxyUrl: String = CfaAiClient.DEFAULT_URL,
        proxyToken: String = CfaAiClient.DEFAULT_TOKEN,
    ): String =
        "<script>" +
            "window.CFA_AI_GRADING_ENABLED=$enabled;" +
            "window.CFA_AI_PROXY_URL=${JSONObject.quote(proxyUrl)};" +
            "window.CFA_AI_PROXY_TOKEN=${JSONObject.quote(proxyToken)};" +
            "</script>"

    /**
     * Prepend the AI globals to rendered card [html] so the ethics card template
     * can honour the synced toggle and configured proxy URL/token. Reading
     * col.conf is cheap; on any error the html is returned unchanged (fail-open —
     * the card then behaves as an older build, i.e. AI on with default proxy).
     */
    fun withAiToggle(
        col: Collection,
        html: String,
    ): String =
        try {
            aiConfigScript(
                CfaAiClient.aiEnabled(col, CfaAiClient.GRADING_KEY),
                CfaAiClient.proxyUrl(col),
                CfaAiClient.proxyToken(col),
            ) + html
        } catch (_: Throwable) {
            html
        }
}
