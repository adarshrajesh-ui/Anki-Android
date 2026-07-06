// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — M1: scores served by the shared Rust `computeCfaScores` RPC with a
// Kotlin fallback. Verifies that when the fork engine exposes the RPC the
// provider uses it (source = rpc), that the RPC numbers agree with the
// deterministic on-device fallback (cross-engine parity, both mirror
// pylib/anki/cfa.py), and that a fresh collection abstains identically on both
// paths. If a build ever consumes a backend without the RPC, `rpcAvailable`
// reports false and the provider transparently falls back.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CfaScoresProviderTest : RobolectricTest() {
    /** The fork engine consumed by these tests exposes the shared RPC. */
    @Test
    fun `rpc is available on the fork engine`() {
        assertThat(CfaScoresProvider.rpcAvailable(col), equalTo(true))
    }

    /**
     * On a fresh (below-threshold) collection both the RPC and the on-device
     * fallback abstain with the give-up rule — no fabricated numbers on either
     * path. The provider prefers the RPC, so its source is `rpc`.
     */
    @Test
    fun `fresh collection abstains on both engines and provider uses rpc`() {
        val provided = CfaScoresProvider.scores(col)
        assertThat(provided.source, equalTo(CfaScores.SOURCE_RPC))
        assertThat(provided.memory.abstain, equalTo(true))
        assertThat(provided.performance.abstain, equalTo(true))
        assertThat(provided.readiness.abstain, equalTo(true))

        // The deterministic fallback abstains identically.
        val fallback = CfaScorer.compute(col)
        assertThat(fallback.source, equalTo(CfaScores.SOURCE_FALLBACK))
        assertThat(fallback.memory.abstain, equalTo(true))
        assertThat(fallback.performance.abstain, equalTo(true))
        assertThat(fallback.readiness.abstain, equalTo(true))

        // Same canonical topic list on both engines.
        assertThat(provided.topicsTotal, equalTo(fallback.topicsTotal))

        // The Bayesian verdict NEVER abstains (even on a fresh collection) and
        // agrees across the RPC and the on-device port. With no evidence the band
        // is very wide, so it should read "likely fail" against the MPS proxy.
        assertBayesianClose(provided.bayesian, fallback.bayesian)
        val providedBayes = provided.bayesian!!
        assertThat(providedBayes.call, equalTo("likely fail"))
        assertThat(providedBayes.firstExposures, equalTo(0))
    }

    /**
     * With enough tagged, FSRS-stateful, graded reviews the RPC returns honest
     * ranges that AGREE with the on-device fallback — proving desktop and phone
     * compute the same numbers from one engine spec. This is the M1 parity gate.
     */
    @Test
    fun `rpc scores match the on-device fallback on a populated collection`() {
        seedReviewedCards(
            topics = listOf("ethics", "quant", "econ", "fra", "corp", "equity"),
            cardsPerTopic = 6,
            reviewsPerCard = 6,
        )

        val rpc = CfaScoresProvider.scores(col)
        val fb = CfaScorer.compute(col)

        // Provider took the shared RPC path.
        assertThat(rpc.source, equalTo(CfaScores.SOURCE_RPC))

        // Evidence crossed every threshold on the RPC path, so no abstaining.
        assertThat(rpc.gradedReviews, greaterThanOrEqualTo(MIN_GRADED_REVIEWS))
        assertThat(rpc.firstExposures, greaterThanOrEqualTo(MIN_FIRST_EXPOSURES))
        assertThat(rpc.memory.abstain, equalTo(false))
        assertThat(rpc.performance.abstain, equalTo(false))
        assertThat(rpc.readiness.abstain, equalTo(false))

        // Counts identical (same DB, same canonical topics).
        assertThat(rpc.gradedReviews, equalTo(fb.gradedReviews))
        assertThat(rpc.firstExposures, equalTo(fb.firstExposures))
        assertThat(rpc.topicsCovered, equalTo(fb.topicsCovered))
        assertThat(rpc.coveragePct, closeTo(fb.coveragePct, 1e-9))

        // Each honest score point + range agrees to floating-point tolerance.
        assertScoreClose(rpc.memory, fb.memory)
        assertScoreClose(rpc.performance, fb.performance)
        assertScoreClose(rpc.readiness, fb.readiness)

        // The Bayesian verdict hero also agrees across the shared Rust engine and
        // the on-device Kotlin port — proving desktop == phone on the pass/fail
        // call, the accuracy band and the exam-weighted recall.
        assertBayesianClose(rpc.bayesian, fb.bayesian)
        val rpcBayes = rpc.bayesian!!
        val fbBayes = fb.bayesian!!
        assertThat(rpcBayes.call, equalTo(fbBayes.call))
        assertThat(rpcBayes.passed, equalTo(fbBayes.passed))

        // Every score is still a well-formed range in [0,1].
        for (s in listOf(rpc.memory, rpc.performance, rpc.readiness)) {
            val low = s.rangeLow!!
            val point = s.point!!
            val high = s.rangeHigh!!
            assertThat(low, lessThanOrEqualTo(point))
            assertThat(point, lessThanOrEqualTo(high))
            assertThat(low, greaterThanOrEqualTo(0.0))
            assertThat(high, lessThanOrEqualTo(1.0))
        }
    }

    private fun assertScoreClose(
        a: HonestScore,
        b: HonestScore,
    ) {
        assertThat(a.abstain, equalTo(b.abstain))
        if (!a.abstain) {
            assertThat(a.point!!, closeTo(b.point!!, 1e-6))
            assertThat(a.rangeLow!!, closeTo(b.rangeLow!!, 1e-6))
            assertThat(a.rangeHigh!!, closeTo(b.rangeHigh!!, 1e-6))
        }
    }

    private fun assertBayesianClose(
        a: BayesianVerdict?,
        b: BayesianVerdict?,
    ) {
        assertThat("RPC bayesian present", a != null, equalTo(true))
        assertThat("fallback bayesian present", b != null, equalTo(true))
        // accuracy / CI are exact beta math on both engines; p_pass uses erf
        // (exact in Rust, an A&S approximation in Kotlin) so it gets a looser bound.
        assertThat(a!!.accuracy, closeTo(b!!.accuracy, 1e-6))
        assertThat(a.ciLow, closeTo(b.ciLow, 1e-6))
        assertThat(a.ciHigh, closeTo(b.ciHigh, 1e-6))
        assertThat(a.mps, closeTo(b.mps, 1e-9))
        assertThat(a.callProb, closeTo(b.callProb, 1e-4))
        assertThat(a.topicsCovered, equalTo(b.topicsCovered))
        assertThat(a.topicsTotal, equalTo(b.topicsTotal))
        assertThat(a.firstExposures, equalTo(b.firstExposures))
        val aRecall = a.recall
        val bRecall = b.recall
        if (aRecall != null && bRecall != null) {
            assertThat(aRecall, closeTo(bRecall, 1e-6))
        } else {
            assertThat(aRecall, equalTo(bRecall))
        }
    }

    /**
     * Add [cardsPerTopic] cards for each of [topics] (tagged `los::<topic>::…`),
     * each with FSRS memory state and [reviewsPerCard] graded revlog rows. ~80%
     * of first exposures are correct so Performance is a non-degenerate band.
     * (Mirror of the fixture in CfaScorerTest so both engines see identical data.)
     */
    private fun seedReviewedCards(
        topics: List<String>,
        cardsPerTopic: Int,
        reviewsPerCard: Int,
    ) {
        val nowMs = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val baseDay = (nowMs / dayMs) * dayMs
        val now = nowMs / 1000
        val lrt = now - 2 * 86_400 // reviewed 2 days ago -> high, valid retrievability
        var cardIndex = 0
        for (topic in topics) {
            repeat(cardsPerTopic) { i ->
                val note = addBasicNote("q-$topic-$i", "a-$topic-$i")
                val cid = note.firstCard().id
                col.db.execute("update notes set tags = ? where id = ?", " los::$topic::sub ", note.id)
                col.db.execute(
                    "update cards set data = ?, type = 2, queue = 2, reps = ? where id = ?",
                    """{"s":50.0,"d":5.0,"lrt":$lrt}""",
                    reviewsPerCard,
                    cid,
                )
                val firstEase = if (cardIndex % 5 == 0) 1 else 3
                repeat(reviewsPerCard) { j ->
                    // Each review lands on a DISTINCT day (both engines dedup
                    // graded reviews per card-day); a tiny per-card sub-day
                    // offset keeps ids unique without changing the day bucket.
                    // j==0 is the earliest id -> the graded first exposure.
                    val revlogId = baseDay - (reviewsPerCard - j) * dayMs + cardIndex * 1000L + j
                    val ease = if (j == 0) firstEase else 3
                    col.db.execute(
                        "insert into revlog(id,cid,usn,ease,ivl,lastIvl,factor,time,type) " +
                            "values(?,?,?,?,?,?,?,?,?)",
                        revlogId,
                        cid,
                        0,
                        ease,
                        30,
                        1,
                        2000,
                        3000,
                        1,
                    )
                }
                cardIndex += 1
            }
        }
    }
}
