// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — mobile AI client tests. The HTTP POST is injected, so these are
// deterministic (no network/proxy/key): they exercise the AI path and the
// fallback contract (source flips to "fallback" on any non-200 / exception /
// server-reported failure), mirroring the desktop AI-off behaviour.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CfaAiClientTest {
    private val aiPoster =
        CfaHttpPost { _, _, _ ->
            200 to
                """{"ok":true,"text":"Front-running is trading ahead of client orders.","source":"ai","model":"gpt-4o-mini","error":null}"""
        }

    @Test
    fun tabfill_ai_success() {
        val r = CfaAiClient.tabfill("http://x", "tok", "Define front-running.", "Basic", aiPoster)
        assertThat(r.ok, equalTo(true))
        assertThat(r.source, equalTo("ai"))
        assertThat(r.model, equalTo("gpt-4o-mini"))
        assertThat(r.text.isNotBlank(), equalTo(true))
    }

    @Test
    fun tabfill_sends_bearer_token_and_route() {
        var seenUrl = ""
        var seenAuth = ""
        val spy =
            CfaHttpPost { url, token, _ ->
                seenUrl = url
                seenAuth = token
                200 to """{"ok":true,"text":"x","source":"ai","model":"m","error":null}"""
            }
        CfaAiClient.tabfill("http://host:27702/", "secret", "q", "Basic", spy)
        assertThat(seenUrl, equalTo("http://host:27702/cfa/tabfill"))
        assertThat(seenAuth, equalTo("secret"))
    }

    @Test
    fun tabfill_falls_back_on_non_200() {
        val r = CfaAiClient.tabfill("http://x", "t", "q", "Basic", CfaHttpPost { _, _, _ -> 401 to "" })
        assertThat(r.ok, equalTo(false))
        assertThat(r.source, equalTo("fallback"))
        assertThat(r.error, equalTo("http_401"))
    }

    @Test
    fun tabfill_falls_back_on_exception() {
        val r =
            CfaAiClient.tabfill("http://x", "t", "q", "Basic", CfaHttpPost { _, _, _ -> throw RuntimeException("net") })
        assertThat(r.source, equalTo("fallback"))
        assertThat(r.error?.startsWith("client_error:"), equalTo(true))
    }

    @Test
    fun tabfill_empty_front_is_fallback() {
        val r = CfaAiClient.tabfill("http://x", "t", "   ", "Basic", aiPoster)
        assertThat(r.source, equalTo("fallback"))
        assertThat(r.error, equalTo("empty_front"))
    }

    @Test
    fun tabfill_propagates_server_fallback() {
        val r =
            CfaAiClient.tabfill(
                "http://x",
                "t",
                "q",
                "Basic",
                CfaHttpPost { _, _, _ ->
                    200 to """{"ok":false,"text":"","source":"fallback","model":null,"error":"no_api_key"}"""
                },
            )
        assertThat(r.ok, equalTo(false))
        assertThat(r.source, equalTo("fallback"))
        assertThat(r.error, equalTo("no_api_key"))
    }
}
