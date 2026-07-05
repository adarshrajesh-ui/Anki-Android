// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — mobile AI client tests. The HTTP POST is injected, so these are
// deterministic (no network/proxy/key): they exercise bidirectional tab-fill
// (front->back and back->front) and the fallback contract (source flips to
// "fallback" on any non-200 / exception / server-reported failure / nothing-to-
// fill), mirroring the desktop AI-off behaviour.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CfaAiClientTest {
    private val backPoster =
        CfaHttpPost { _, _, _ ->
            200 to
                """{"ok":true,"text":"Trading ahead of client orders.","target":"back","source":"ai","model":"gpt-4o-mini","error":null}"""
        }

    @Test
    fun fill_front_to_back() {
        val r = CfaAiClient.fill("http://x", "tok", "Define front-running.", "", backPoster)
        assertThat(r.ok, equalTo(true))
        assertThat(r.target, equalTo("back"))
        assertThat(r.source, equalTo("ai"))
        assertThat(r.model, equalTo("gpt-4o-mini"))
    }

    @Test
    fun fill_back_to_front() {
        val frontPoster =
            CfaHttpPost { _, _, _ ->
                200 to """{"ok":true,"text":"What is front-running?","target":"front","source":"ai","model":"m","error":null}"""
            }
        val r = CfaAiClient.fill("http://x", "tok", "", "Trading ahead of client orders.", frontPoster)
        assertThat(r.ok, equalTo(true))
        assertThat(r.target, equalTo("front"))
    }

    @Test
    fun fill_sends_bearer_token_and_route() {
        var seenUrl = ""
        var seenAuth = ""
        val spy =
            CfaHttpPost { url, token, _ ->
                seenUrl = url
                seenAuth = token
                200 to """{"ok":true,"text":"x","target":"back","source":"ai","model":"m","error":null}"""
            }
        CfaAiClient.fill("http://host:27702/", "secret", "q", "", spy)
        assertThat(seenUrl, equalTo("http://host:27702/cfa/tabfill"))
        assertThat(seenAuth, equalTo("secret"))
    }

    @Test
    fun fill_nothing_when_both_empty_or_both_filled() {
        val bothEmpty = CfaAiClient.fill("http://x", "t", "  ", "", backPoster)
        assertThat(bothEmpty.source, equalTo("fallback"))
        assertThat(bothEmpty.error, equalTo("nothing_to_fill"))
        val bothFilled = CfaAiClient.fill("http://x", "t", "Q", "A", backPoster)
        assertThat(bothFilled.error, equalTo("nothing_to_fill"))
    }

    @Test
    fun fill_falls_back_on_non_200() {
        val r = CfaAiClient.fill("http://x", "t", "q", "", CfaHttpPost { _, _, _ -> 401 to "" })
        assertThat(r.ok, equalTo(false))
        assertThat(r.source, equalTo("fallback"))
        assertThat(r.error, equalTo("http_401"))
    }

    @Test
    fun fill_falls_back_on_exception() {
        val r =
            CfaAiClient.fill("http://x", "t", "q", "", CfaHttpPost { _, _, _ -> throw RuntimeException("net") })
        assertThat(r.source, equalTo("fallback"))
        assertThat(r.error?.startsWith("client_error:"), equalTo(true))
    }

    @Test
    fun fill_propagates_server_fallback() {
        val r =
            CfaAiClient.fill(
                "http://x",
                "t",
                "q",
                "",
                CfaHttpPost { _, _, _ ->
                    200 to """{"ok":false,"text":"","target":"back","source":"fallback","model":null,"error":"no_api_key"}"""
                },
            )
        assertThat(r.ok, equalTo(false))
        assertThat(r.source, equalTo("fallback"))
        assertThat(r.error, equalTo("no_api_key"))
    }

    // ----- semantic ethics grading (POST /cfa/grade) -----

    private val gold = listOf("sells the client's holdings" to "front-running the client order")

    private val gradePoster =
        CfaHttpPost { _, _, _ ->
            200 to
                """{"ok":true,"source":"ai","grade":"partial","verdict_correct":true,"correct":false,
                    "explanation":"You caught the sale but missed the timing evidence.",
                    "coaching":"You highlighted 'sold the holdings' — right idea, but the decisive fact is she traded AHEAD of the client. Standard II(B) Market Manipulation.",
                    "study_tip":"Re-read Standard III(B) on fair dealing.",
                    "confidence":0.82,"standard":"III(B) Fair Dealing","item_id":"pair-3","model":"gpt-4o-mini",
                    "per_span":[{"phrase":"sells the client's holdings","matched":true,"note":"the decisive evidence"}],
                    "error":null}"""
        }

    @Test
    fun grade_parses_ai_result() {
        val r =
            CfaAiClient.grade(
                "http://x",
                "t",
                "passage",
                "unethical",
                "unethical",
                gold,
                listOf("sold the holdings"),
                "pair-3",
                "III(B)",
                gradePoster,
            )
        assertThat(r.ok, equalTo(true))
        assertThat(r.source, equalTo("ai"))
        assertThat(r.grade, equalTo("partial"))
        assertThat(r.verdictCorrect, equalTo(true))
        assertThat(r.confidence, equalTo(0.82))
        assertThat(r.standard, equalTo("III(B) Fair Dealing"))
        assertThat(r.itemId, equalTo("pair-3"))
        assertThat(r.model, equalTo("gpt-4o-mini"))
        assertThat(r.perSpan.size, equalTo(1))
        assertThat(r.perSpan[0].matched, equalTo(true))
        assertThat(r.perSpan[0].phrase, equalTo("sells the client's holdings"))
    }

    @Test
    fun grade_sends_bearer_token_and_route() {
        var seenUrl = ""
        var seenAuth = ""
        var seenBody = ""
        val spy =
            CfaHttpPost { url, token, body ->
                seenUrl = url
                seenAuth = token
                seenBody = body
                200 to """{"ok":true,"source":"ai","grade":"correct","standard":"II(A)"}"""
            }
        CfaAiClient.grade(
            "http://host:27702/",
            "secret",
            "p",
            "ethical",
            "ethical",
            gold,
            listOf("phrase"),
            "id1",
            "II(A)",
            spy,
        )
        assertThat(seenUrl, equalTo("http://host:27702/cfa/grade"))
        assertThat(seenAuth, equalTo("secret"))
        // gold spans + learner spans + verdicts are all sent to the server
        assertThat(seenBody.contains("\"goldSpans\""), equalTo(true))
        assertThat(seenBody.contains("\"learnerSpans\""), equalTo(true))
        assertThat(seenBody.contains("\"answerVerdict\":\"ethical\""), equalTo(true))
    }

    @Test
    fun grade_falls_back_on_non_200() {
        val r =
            CfaAiClient.grade(
                "http://x",
                "t",
                "p",
                "ethical",
                "ethical",
                gold,
                listOf("x"),
                "id",
                "II(A)",
                CfaHttpPost { _, _, _ -> 500 to "" },
            )
        assertThat(r.ok, equalTo(false))
        assertThat(r.source, equalTo("fallback"))
        assertThat(r.error, equalTo("http_500"))
        // the cited Standard is preserved even on the fallback path
        assertThat(r.standard, equalTo("II(A)"))
    }

    @Test
    fun grade_falls_back_on_exception() {
        val r =
            CfaAiClient.grade(
                "http://x",
                "t",
                "p",
                "ethical",
                "ethical",
                gold,
                listOf("x"),
                "id",
                "II(A)",
                CfaHttpPost { _, _, _ -> throw RuntimeException("net") },
            )
        assertThat(r.source, equalTo("fallback"))
        assertThat(r.error?.startsWith("client_error:"), equalTo(true))
    }

    @Test
    fun grade_propagates_server_fallback_when_ai_off() {
        val r =
            CfaAiClient.grade(
                "http://x",
                "t",
                "p",
                "ethical",
                "ethical",
                gold,
                listOf("x"),
                "id",
                "II(A)",
                CfaHttpPost { _, _, _ ->
                    200 to
                        """{"ok":false,"source":"fallback","grade":"partial","explanation":"deterministic",
                            "confidence":null,"error":"ai_off","standard":"II(A)","per_span":[]}"""
                },
            )
        assertThat(r.ok, equalTo(false))
        assertThat(r.source, equalTo("fallback"))
        assertThat(r.grade, equalTo("partial"))
        assertThat(r.confidence, equalTo(null))
        assertThat(r.error, equalTo("ai_off"))
    }
}
