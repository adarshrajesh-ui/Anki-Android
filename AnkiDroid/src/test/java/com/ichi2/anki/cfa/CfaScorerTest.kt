// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Increment 2 (Exam Readiness / D6) scorer tests.
//
// Verifies the deterministic, no-network on-device scorer that backs the Exam
// Readiness screen: the give-up/abstain rule below thresholds, and the honest
// ranged scores + per-topic recall once enough graded, FSRS-stateful reviews
// exist. Mirrors pylib/anki/cfa.py thresholds (200 / 50% / 30).

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CfaScorerTest : RobolectricTest() {
    /** A fresh collection is below every threshold, so all three scores abstain. */
    @Test
    fun `scores abstain with give-up reasons when below thresholds`() {
        val scores = CfaScorer.compute(col)

        assertThat(scores.source, equalTo(CfaScores.SOURCE_FALLBACK))
        assertThat("memory abstains", scores.memory.abstain, equalTo(true))
        assertThat("performance abstains", scores.performance.abstain, equalTo(true))
        assertThat("readiness abstains", scores.readiness.abstain, equalTo(true))
        // Give-up text quotes the thresholds (honest, not a fabricated number).
        assertThat(scores.performance.reason, containsString("need $MIN_FIRST_EXPOSURES"))
        assertThat(scores.memory.reason, containsString("need $MIN_GRADED_REVIEWS"))
        assertThat(scores.memory.point, equalTo(null))
        // The per-topic table still lists all canonical topics, all uncovered.
        assertThat(scores.topics.size, equalTo(CANONICAL_TOPICS.size))
        assertThat("nothing covered", scores.topics.none { it.covered }, equalTo(true))
    }

    /** Enough tagged, FSRS-stateful, graded cards -> honest ranged scores + recall. */
    @Test
    fun `populated collection yields honest ranges, coverage and per-topic recall`() {
        seedReviewedCards(
            topics = listOf("ethics", "quant", "econ", "fra", "corp", "equity"),
            cardsPerTopic = 6,
            reviewsPerCard = 6,
        )

        val scores = CfaScorer.compute(col)

        // Evidence crossed every threshold.
        assertThat(scores.gradedReviews, greaterThanOrEqualTo(MIN_GRADED_REVIEWS))
        assertThat(scores.firstExposures, greaterThanOrEqualTo(MIN_FIRST_EXPOSURES))
        assertThat(scores.coveragePct, greaterThanOrEqualTo(MIN_TOPIC_COVERAGE))

        // No abstaining now — each score is a proper range containing its point.
        for (score in listOf(scores.memory, scores.performance, scores.readiness)) {
            assertThat(score.abstain, equalTo(false))
            val point = score.point!!
            val low = score.rangeLow!!
            val high = score.rangeHigh!!
            assertThat(low, lessThanOrEqualTo(point))
            assertThat(point, lessThanOrEqualTo(high))
            assertThat(low, greaterThanOrEqualTo(0.0))
            assertThat(high, lessThanOrEqualTo(1.0))
        }

        // Six covered topics, each with a real recall figure; two remain uncovered.
        val covered = scores.topics.filter { it.covered }
        assertThat(covered.size, equalTo(6))
        assertThat(scores.topicsCovered, equalTo(6))
        for (topic in covered) {
            val recall = topic.avgR!!
            assertThat(recall, greaterThan(0.0))
            assertThat(recall, lessThanOrEqualTo(1.0))
            assertThat(topic.gradedReviews, greaterThan(0))
        }
    }

    /**
     * Add [cardsPerTopic] cards for each of [topics] (tagged `los::<topic>::…`),
     * each with FSRS memory state and [reviewsPerCard] graded revlog rows. ~80%
     * of first exposures are correct so Performance is a non-degenerate Wilson band.
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
                // First exposure (smallest revlog id for the card) drives Performance.
                val firstEase = if (cardIndex % 5 == 0) 1 else 3
                repeat(reviewsPerCard) { j ->
                    // Each review on a DISTINCT day so the day-deduped graded-review
                    // count (matching the shared Rust engine) still crosses the
                    // threshold; a per-card sub-day offset keeps revlog ids unique.
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
