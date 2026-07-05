// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Exam Readiness WebView payload tests. Pure (no Collection /
// network): they exercise the honest-score -> readiness payload builder (range /
// midpoint / abstain formatting, the Bayesian verdict hero, and per-topic recall
// tone), verifying it mirrors the desktop CfaReadinessPage helpers, plus a source
// guard on the new WebView asset that keeps the bridge + CTAs wired.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CfaReadinessTest {
    private fun score(
        abstain: Boolean,
        reason: String = "",
        point: Double? = null,
        low: Double? = null,
        high: Double? = null,
    ) = HonestScore(abstain = abstain, reason = reason, point = point, rangeLow = low, rangeHigh = high)

    private fun verdict(
        passed: Boolean = true,
        recall: Double? = 0.78,
    ) = BayesianVerdict(
        call = if (passed) "likely pass" else "likely fail",
        callProb = 0.87,
        passed = passed,
        accuracy = 0.72,
        ciLow = 0.60,
        ciHigh = 0.84,
        mps = 0.65,
        recall = recall,
        firstExposures = 40,
        topicsCovered = 6,
        topicsTotal = 8,
        label = "not validated against real exam data",
    )

    private fun scores(
        memory: HonestScore,
        performance: HonestScore,
        readiness: HonestScore,
        bayesian: BayesianVerdict? = verdict(),
        topics: List<TopicRecall> = emptyList(),
        source: String = CfaScores.SOURCE_FALLBACK,
    ) = CfaScores(
        memory = memory,
        performance = performance,
        readiness = readiness,
        topics = topics,
        topicsTotal = 8,
        topicsCovered = 6,
        coveragePct = 0.75,
        gradedReviews = 220,
        firstExposures = 40,
        source = source,
        bayesian = bayesian,
    )

    private fun topic(
        slug: String,
        weight: Double,
        graded: Int,
        avgR: Double?,
    ) = TopicRecall(
        topic = "los::$slug",
        displayName = topicDisplayName("los::$slug"),
        weight = weight,
        gradedReviews = graded,
        avgR = avgR,
        covered = graded > 0 && avgR != null,
    )

    // --- Verdict hero ---------------------------------------------------------

    @Test
    fun bayesian_hero_reports_the_pass_call_with_accuracy_and_ci() {
        val json =
            org.json.JSONObject(
                CfaReadiness.buildPayload(
                    scores(
                        score(false, point = 0.7, low = 0.6, high = 0.8),
                        score(false, point = 0.6, low = 0.5, high = 0.7),
                        score(false, point = 0.5, low = 0.4, high = 0.6),
                    ),
                ),
            )
        val hero = json.getJSONObject("hero")
        assertThat(hero.getString("mode"), equalTo("bayesian"))
        // Sentence-cased call (desktop capitalises the headline).
        assertThat(hero.getString("call"), equalTo("Likely pass"))
        assertThat(hero.getBoolean("passed"), equalTo(true))
        assertThat(hero.getString("callProb"), equalTo("p=0.87"))
        assertThat(hero.getString("accuracy"), equalTo("72%"))
        assertThat(hero.getString("ciLow"), equalTo("60%"))
        assertThat(hero.getString("ciHigh"), equalTo("84%"))
        assertThat(hero.getString("mps"), equalTo("65%"))
        assertThat(hero.getString("recall"), equalTo("78%"))
    }

    @Test
    fun hero_abstains_on_the_same_gate_as_desktop_when_memory_gives_up() {
        // memory.abstain || performance.abstain -> the pass/fail call is hidden.
        val json =
            org.json.JSONObject(
                CfaReadiness.buildPayload(
                    scores(
                        memory = score(true, reason = "not enough data: 12 graded reviews (need 200)"),
                        performance = score(false, point = 0.6, low = 0.5, high = 0.7),
                        readiness = score(true, reason = "not enough data to estimate readiness"),
                        bayesian = verdict(),
                    ),
                ),
            )
        val hero = json.getJSONObject("hero")
        assertThat(hero.getString("mode"), equalTo("abstain"))
        assertThat(hero.getString("headline"), containsString("keep studying"))
        // The honest give-up reason + the standing caveat are surfaced.
        assertThat(hero.getString("note"), containsString("12 graded reviews"))
        assertThat(hero.getString("note"), containsString("not validated against real exam data"))
    }

    @Test
    fun hero_omits_recall_when_the_engine_has_none() {
        val json =
            org.json.JSONObject(
                CfaReadiness.buildPayload(
                    scores(
                        score(false, point = 0.7, low = 0.6, high = 0.8),
                        score(false, point = 0.6, low = 0.5, high = 0.7),
                        score(false, point = 0.5, low = 0.4, high = 0.6),
                        bayesian = verdict(recall = null),
                    ),
                ),
            )
        // JSON null (not the string "null") so the asset drops the recall clause.
        assertThat(json.getJSONObject("hero").isNull("recall"), equalTo(true))
    }

    // --- Honest score cards ---------------------------------------------------

    @Test
    fun three_value_first_cards_carry_range_or_quiet_abstain() {
        val json =
            org.json.JSONObject(
                CfaReadiness.buildPayload(
                    scores(
                        memory = score(false, point = 0.70, low = 0.62, high = 0.78),
                        performance = score(true, reason = "not enough data: 12 first-seen (need 30)"),
                        readiness = score(false, point = 0.5, low = 0.35, high = 0.65),
                        source = CfaScores.SOURCE_RPC,
                    ),
                ),
            )
        val cards = json.getJSONArray("scores")
        assertThat(cards.length(), equalTo(3))
        val mem = cards.getJSONObject(0)
        assertThat(mem.getString("name"), equalTo("Memory"))
        assertThat(mem.getString("value"), equalTo("62% – 78%"))
        assertThat(mem.getString("sub"), equalTo("midpoint 70%"))
        assertThat(mem.getString("tone"), equalTo("neutral"))
        // Abstaining Performance stays quiet (muted), never a warning.
        val perf = cards.getJSONObject(1)
        assertThat(perf.getString("value"), equalTo("Awaiting reviews"))
        assertThat(perf.getString("tone"), equalTo("muted"))
        assertThat(perf.getString("sub"), containsString("need 30"))
        assertThat(json.getString("source"), equalTo("rpc"))
    }

    // --- Per-topic recall table ----------------------------------------------

    @Test
    fun topics_sort_by_weight_and_carry_honest_recall_tones() {
        val json =
            org.json.JSONObject(
                CfaReadiness.buildPayload(
                    scores(
                        score(false, point = 0.7, low = 0.6, high = 0.8),
                        score(false, point = 0.6, low = 0.5, high = 0.7),
                        score(false, point = 0.5, low = 0.4, high = 0.6),
                        topics =
                            listOf(
                                topic("ethics", weight = 0.15, graded = 40, avgR = 0.82), // strong
                                topic("equity", weight = 0.20, graded = 30, avgR = 0.45), // low -> warn
                                topic("quant", weight = 0.05, graded = 0, avgR = null), // no data -> muted
                            ),
                    ),
                ),
            )
        val rows = json.getJSONArray("topics")
        assertThat(rows.length(), equalTo(3))
        // Weightiest first: Equity (0.20) then Ethics (0.15) then Quant (0.05).
        assertThat(rows.getJSONObject(0).getString("name"), containsString("Equity"))
        assertThat(rows.getJSONObject(0).getString("recall"), equalTo("45%"))
        assertThat(rows.getJSONObject(0).getString("tone"), equalTo("warn"))
        assertThat(rows.getJSONObject(1).getString("recall"), equalTo("82%"))
        assertThat(rows.getJSONObject(1).getString("tone"), equalTo("neutral"))
        // Uncovered topic reads "no data" quietly (muted), not a fake 0%.
        assertThat(rows.getJSONObject(2).getString("recall"), equalTo("no data"))
        assertThat(rows.getJSONObject(2).getString("tone"), equalTo("muted"))
        assertThat(json.getBoolean("awaitingRecall"), equalTo(false))
    }

    @Test
    fun awaiting_recall_flag_set_when_no_topic_has_data_yet() {
        val json =
            org.json.JSONObject(
                CfaReadiness.buildPayload(
                    scores(
                        score(true),
                        score(true),
                        score(true),
                        bayesian = verdict(),
                        topics =
                            listOf(
                                topic("ethics", weight = 0.15, graded = 0, avgR = null),
                                topic("equity", weight = 0.20, graded = 0, avgR = null),
                            ),
                    ),
                ),
            )
        assertThat(json.getBoolean("awaitingRecall"), equalTo(true))
    }

    // --- Caption + footer -----------------------------------------------------

    @Test
    fun payload_carries_caption_and_the_explanatory_footer() {
        val json =
            org.json.JSONObject(
                CfaReadiness.buildPayload(
                    scores(
                        score(false, point = 0.7, low = 0.6, high = 0.8),
                        score(false, point = 0.6, low = 0.5, high = 0.7),
                        score(false, point = 0.5, low = 0.4, high = 0.6),
                    ),
                ),
            )
        assertThat(json.getString("caption"), containsString("Coverage 75% (6/8 topics)"))
        assertThat(json.getString("caption"), containsString("220 graded reviews"))
        // Footer byte-mirrors the desktop `_CFA_READINESS_FOOTER`.
        assertThat(json.getString("footer"), containsString("Bayesian call"))
        assertThat(json.getString("footer"), containsString("NOT validated against real exam data"))
    }

    // --- Asset source guard ---------------------------------------------------

    @Test
    fun readiness_asset_wires_the_bridge_and_ctas() {
        val asset =
            listOf(
                File("src/main/assets/cfa/readiness.html"),
                File("AnkiDroid/src/main/assets/cfa/readiness.html"),
            ).firstOrNull { it.exists() }
        assertThat("readiness.html asset must exist for this guard", asset != null, equalTo(true))
        val html = asset!!.readText()
        // Reads the injected data, routes CTAs through the native bridge.
        assertThat(html, containsString("window.CFA_READINESS_DATA"))
        assertThat(html, containsString("AndroidCfaReadiness"))
        // The study CTAs launch the native priority-study / exam-config screens.
        assertThat(html, containsString("priority"))
        assertThat(html, containsString("config"))
        // The three honest scores + verdict hero + recall table are rendered.
        assertThat(html, containsString("Honest scores"))
        assertThat(html, containsString("Per-topic recall"))
        assertThat(html, containsString("hero-call"))
    }
}
