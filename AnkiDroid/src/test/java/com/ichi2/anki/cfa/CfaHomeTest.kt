// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — CFA Home / Today dashboard tests. Pure (no Collection / network):
// they exercise the honest-score -> Home payload builder (range / midpoint /
// abstain formatting mirroring the desktop Home helpers), the exam-countdown
// tone rule (warn inside 14 days, neutral prompt when unset), and the tightly-
// guarded launcher hand-off decision. A source guard on the asset keeps the
// WebView bridge + CTAs wired.

package com.ichi2.anki.cfa

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.shouldOpenCfaHomeOnLaunch
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CfaHomeTest {
    private fun score(
        abstain: Boolean,
        reason: String = "",
        point: Double? = null,
        low: Double? = null,
        high: Double? = null,
    ) = HonestScore(abstain = abstain, reason = reason, point = point, rangeLow = low, rangeHigh = high)

    private fun scores(
        memory: HonestScore,
        performance: HonestScore,
        readiness: HonestScore,
        source: String = CfaScores.SOURCE_FALLBACK,
        coveragePct: Double = 0.6,
        topicsCovered: Int = 6,
        topicsTotal: Int = 8,
        graded: Int = 220,
        first: Int = 40,
    ) = CfaScores(
        memory = memory,
        performance = performance,
        readiness = readiness,
        topics = emptyList(),
        topicsTotal = topicsTotal,
        topicsCovered = topicsCovered,
        coveragePct = coveragePct,
        gradedReviews = graded,
        firstExposures = first,
        source = source,
    )

    // --- Score-card formatting (mirrors the desktop Home helpers) -------------

    @Test
    fun measured_score_shows_range_and_midpoint() {
        val s = score(abstain = false, point = 0.70, low = 0.62, high = 0.78)
        assertThat(CfaHome.bandValue(s), equalTo("62% – 78%"))
        assertThat(CfaHome.bandSub(s), equalTo("midpoint 70%"))
        assertThat(CfaHome.bandTone(s), equalTo("neutral"))
    }

    @Test
    fun abstaining_score_is_quiet_and_honest() {
        val s = score(abstain = true, reason = "not enough data: 12 graded reviews (need 200)")
        assertThat(CfaHome.bandValue(s), equalTo("Awaiting reviews"))
        assertThat(CfaHome.bandSub(s), equalTo("not enough data: 12 graded reviews (need 200)"))
        // Abstain reads as an honest ABSENCE of data (muted), never a warning.
        assertThat(CfaHome.bandTone(s), equalTo("muted"))
    }

    @Test
    fun hero_lead_states_reason_while_readiness_abstains() {
        val lead = CfaHome.heroLead(score(abstain = true, reason = "not enough data"))
        assertThat(lead, containsString("not enough data"))
        assertThat(lead, containsString("keep studying"))
    }

    @Test
    fun hero_lead_reports_pass_probability_when_calibrated() {
        val lead = CfaHome.heroLead(score(abstain = false, point = 0.55, low = 0.4, high = 0.7))
        assertThat(lead, containsString("Estimated pass probability 55%"))
        assertThat(lead, containsString("40% – 70%"))
    }

    // --- Full payload + countdown --------------------------------------------

    @Test
    fun payload_carries_three_scores_and_source() {
        val json =
            JSONObject(
                CfaHome.buildPayload(
                    scores(
                        memory = score(false, point = 0.7, low = 0.6, high = 0.8),
                        performance = score(false, point = 0.6, low = 0.5, high = 0.7),
                        readiness = score(false, point = 0.5, low = 0.35, high = 0.65),
                        source = CfaScores.SOURCE_RPC,
                    ),
                    daysToExam = 42,
                    examDate = "2026-08-16",
                ),
            )
        val cards = json.getJSONArray("scores")
        assertThat(cards.length(), equalTo(3))
        assertThat(cards.getJSONObject(0).getString("name"), equalTo("Memory"))
        assertThat(cards.getJSONObject(2).getString("name"), equalTo("Readiness"))
        assertThat(json.getString("source"), equalTo("rpc"))
        // A far-off exam is a calm neutral countdown, not a warning.
        val hero = json.getJSONObject("hero")
        assertThat(hero.getString("headline"), equalTo("42 days to the exam"))
        assertThat(hero.getString("tone"), equalTo("neutral"))
        assertThat(hero.getString("sub"), containsString("2026-08-16"))
    }

    @Test
    fun countdown_turns_warn_inside_two_weeks() {
        val json =
            JSONObject(
                CfaHome.buildPayload(
                    scores(score(true), score(true), score(true)),
                    daysToExam = 5,
                    examDate = "2026-07-10",
                ),
            )
        val hero = json.getJSONObject("hero")
        assertThat(hero.getString("headline"), equalTo("5 days to the exam"))
        assertThat(hero.getString("tone"), equalTo("warn"))
    }

    @Test
    fun countdown_is_a_calm_prompt_when_unset() {
        val json =
            JSONObject(
                CfaHome.buildPayload(
                    scores(score(true), score(true), score(true)),
                    daysToExam = null,
                    examDate = null,
                ),
            )
        val hero = json.getJSONObject("hero")
        assertThat(hero.getString("headline"), equalTo("Set your exam date"))
        assertThat(hero.getString("tone"), equalTo("neutral"))
        assertThat(hero.getBoolean("unset"), equalTo(true))
    }

    // --- Launcher hand-off guard (never loops on in-app navigation) ----------

    @Test
    fun opens_home_only_on_a_fresh_cold_launcher_start() {
        // Genuine cold launch: ACTION_MAIN + LAUNCHER, fresh start, not yet opened.
        assertThat(
            shouldOpenCfaHomeOnLaunch(Intent.ACTION_MAIN, true, isFreshStart = true, alreadyOpenedThisProcess = false),
            equalTo(true),
        )
        // In-app navigation to the decks (plain component intent) must NOT loop back.
        assertThat(
            shouldOpenCfaHomeOnLaunch(null, false, isFreshStart = true, alreadyOpenedThisProcess = false),
            equalTo(false),
        )
        // A config-change / recreation (savedInstanceState != null) must not re-fire.
        assertThat(
            shouldOpenCfaHomeOnLaunch(Intent.ACTION_MAIN, true, isFreshStart = false, alreadyOpenedThisProcess = false),
            equalTo(false),
        )
        // At most once per process.
        assertThat(
            shouldOpenCfaHomeOnLaunch(Intent.ACTION_MAIN, true, isFreshStart = true, alreadyOpenedThisProcess = true),
            equalTo(false),
        )
        // A launcher intent missing the LAUNCHER category is not a cold launch.
        assertThat(
            shouldOpenCfaHomeOnLaunch(Intent.ACTION_MAIN, false, isFreshStart = true, alreadyOpenedThisProcess = false),
            equalTo(false),
        )
    }

    // --- Asset source guard ---------------------------------------------------

    @Test
    fun home_asset_wires_the_bridge_and_ctas() {
        val asset =
            listOf(
                File("src/main/assets/cfa/home.html"),
                File("AnkiDroid/src/main/assets/cfa/home.html"),
            ).firstOrNull { it.exists() }
        assertThat("home.html asset must exist for this guard", asset != null, equalTo(true))
        val html = asset!!.readText()
        // Reads the injected data, routes CTAs through the native bridge.
        assertThat(html, containsString("window.CFA_HOME_DATA"))
        assertThat(html, containsString("AndroidCfaHome"))
        // The CFA nav CTAs are present (Ethics / Priority / Readiness / Concept
        // Map / Decks) — the flagship Ethics drill leads as the primary CTA,
        // matching the desktop Home's primary "cfa:ethics" tile.
        assertThat(html, containsString("readiness"))
        assertThat(html, containsString("conceptmap"))
        assertThat(html, containsString("priority"))
        assertThat(html, containsString('"' + "ethics" + '"'))
        assertThat(html, containsString("Study Ethics — Minimal Pairs"))
        // Honest source provenance chip (shared engine vs on-device).
        assertThat(html, containsString("shared engine"))
    }

    private fun repoFile(vararg candidates: String): String {
        val f = candidates.map { File(it) }.firstOrNull { it.exists() }
        assertThat("one of $candidates must exist for this guard", f != null, equalTo(true))
        return f!!.readText()
    }

    // --- Native nav-drawer parity guard --------------------------------------
    // The phone's CFA nav must match the desktop top bar: Home / Study / Ethics
    // / Concept Map / Readiness are all reachable natively from the drawer, not
    // only from the Home dashboard's CTAs.
    @Test
    fun nav_drawer_exposes_all_five_cfa_sections() {
        val menu =
            repoFile(
                "src/main/res/menu/navigation_drawer.xml",
                "AnkiDroid/src/main/res/menu/navigation_drawer.xml",
            )
        for (id in listOf("nav_cfa_home", "nav_cfa_study", "nav_cfa_ethics", "nav_cfa_concept_map", "nav_cfa_readiness")) {
            assertThat("drawer must contain $id", menu, containsString(id))
        }
        // New study drills carry their own CFA icons, not a stock reuse.
        assertThat(menu, containsString("ic_cfa_study"))
        assertThat(menu, containsString("ic_cfa_ethics"))
    }

    @Test
    fun nav_drawer_handler_launches_study_and_ethics_natively() {
        val handler =
            repoFile(
                "src/main/java/com/ichi2/anki/NavigationDrawerActivity.kt",
                "AnkiDroid/src/main/java/com/ichi2/anki/NavigationDrawerActivity.kt",
            )
        // Study -> exam-priority drill; Ethics -> Minimal-Pairs drill.
        assertThat(handler, containsString("R.id.nav_cfa_study"))
        assertThat(handler, containsString("CfaExamPriorityActivity.getIntent"))
        assertThat(handler, containsString("R.id.nav_cfa_ethics"))
        assertThat(handler, containsString("CfaEthicsStudyActivity.getIntent"))
    }
}
