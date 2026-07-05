// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Concept Map data-builder tests. These are pure (no Collection /
// network): they exercise the topic-recall -> per-slug mastery payload the
// WebView asset consumes, including the honest give-up (abstain) rule (uncovered
// topics become JSON null so their node stays gray) and the weight-adjusted
// centre roll-up that matches the desktop engine.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CfaConceptMapTest {
    private fun topic(
        slug: String,
        avgR: Double?,
        weight: Double,
        covered: Boolean = avgR != null,
        reviews: Int = if (covered) 50 else 0,
    ) = TopicRecall(
        topic = slug,
        displayName = slug,
        weight = weight,
        gradedReviews = reviews,
        avgR = avgR,
        covered = covered,
    )

    @Test
    fun slug_strips_prefix_and_hierarchy() {
        assertThat(CfaConceptMap.slugOf("los::ethics"), equalTo("ethics"))
        assertThat(CfaConceptMap.slugOf("los::fra::pensions"), equalTo("fra"))
        assertThat(CfaConceptMap.slugOf("equity"), equalTo("equity"))
    }

    @Test
    fun mastery_abstains_when_uncovered() {
        assertThat(CfaConceptMap.masteryOf(topic("los::quant", null, 0.075)), equalTo(null))
        assertThat(
            CfaConceptMap.masteryOf(topic("los::quant", 0.8, 0.075, covered = false)),
            equalTo(null),
        )
    }

    @Test
    fun mastery_reads_and_clamps_avgR() {
        assertThat(CfaConceptMap.masteryOf(topic("los::ethics", 0.82, 0.125)), equalTo(0.82))
        assertThat(CfaConceptMap.masteryOf(topic("los::ethics", 1.4, 0.125)), equalTo(1.0))
    }

    @Test
    fun overall_is_weight_adjusted_and_abstains_when_empty() {
        // No covered topics -> centre abstains (null).
        assertThat(CfaConceptMap.overall(listOf(topic("los::ethics", null, 0.125))), equalTo(null))
        // Heavier section pulls the roll-up toward its mastery.
        val ov =
            CfaConceptMap.overall(
                listOf(
                    topic("los::ethics", 0.9, 0.30),
                    topic("los::quant", 0.5, 0.10),
                ),
            )!!
        // (0.9*0.30 + 0.5*0.10) / 0.40 = 0.8
        assertThat(ov, closeTo(0.8, 1e-9))
    }

    @Test
    fun payload_emits_null_for_abstaining_slugs_and_carries_source() {
        val json =
            CfaConceptMap.buildPayload(
                listOf(
                    topic("los::ethics", 0.82, 0.125),
                    topic("los::quant", null, 0.075),
                ),
                "fallback",
            )
        val o = JSONObject(json)
        assertThat(o.getString("source"), equalTo("fallback"))
        val slugs = o.getJSONObject("slugs")
        assertThat(slugs.getDouble("ethics"), closeTo(0.82, 1e-9))
        assertThat(slugs.isNull("quant"), equalTo(true))
        // overall present (ethics is the only covered topic -> equals its mastery)
        assertThat(o.getDouble("overall"), closeTo(0.82, 1e-9))
    }

    @Test
    fun payload_overall_null_when_all_abstain() {
        val json = CfaConceptMap.buildPayload(listOf(topic("los::ethics", null, 0.125)), "rpc")
        val o = JSONObject(json)
        assertThat(o.isNull("overall"), equalTo(true))
        assertThat(o.getString("source"), equalTo("rpc"))
    }

    /**
     * Regression guard for the panel-gauge abstain-honesty fix (D-P4-1), which
     * matches the desktop CfaConceptMapPage. An abstaining (no-evidence) node
     * must NOT draw a 0%-width fill bar — that reads as a measured "you scored
     * zero", conflating no-data with a genuine 0% and breaking the give-up rule.
     * The old code did `style.width=(pct===null?0:pct)+"%"`; this fails if that
     * conflating fallback returns, and confirms the hatch / no-data path stays.
     */
    @Test
    fun concept_map_asset_gauge_never_fakes_zero_for_abstain() {
        val candidates =
            listOf(
                File("src/main/assets/cfa/concept_map.html"),
                File("AnkiDroid/src/main/assets/cfa/concept_map.html"),
            )
        val asset = candidates.firstOrNull { it.exists() }
        assertThat("concept_map.html asset must exist for this guard", asset != null, equalTo(true))
        val html = asset!!.readText()
        // The abstain-conflating 0%-fill fallback must be gone.
        assertThat(html, not(containsString("(pct===null?0:pct)")))
        // The honest no-data treatment must be present.
        assertThat(html, containsString("is-nodata"))
        assertThat(html, containsString("awaiting evidence"))
    }

    /**
     * Regression guard for the pinned-node unpin fix (mirrors desktop D-P4-5). A
     * user must never be trapped on one node's explanation: tapping the pinned
     * node again toggles it off, Escape unpins from anywhere, and a discoverable
     * always-visible hint says so. The old code only ever ADDED .sel (one-way).
     */
    @Test
    fun concept_map_asset_pinned_node_is_reversible() {
        val candidates =
            listOf(
                File("src/main/assets/cfa/concept_map.html"),
                File("AnkiDroid/src/main/assets/cfa/concept_map.html"),
            )
        val asset = candidates.firstOrNull { it.exists() }
        assertThat("concept_map.html asset must exist for this guard", asset != null, equalTo(true))
        val html = asset!!.readText()
        // Tapping the already-selected node must un-toggle it (not blindly re-add).
        assertThat(html, containsString("wasSel"))
        assertThat(html, containsString("resetPanel"))
        // Escape must be a wired keyboard exit.
        assertThat(html, containsString("\"Escape\""))
        // A discoverable always-visible unpin hint must be present.
        assertThat(html, containsString("to unpin"))
    }
}
