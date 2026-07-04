// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Increment 3 ("Study by Exam Priority") queue tests.
//
// Exercises the read-only `buildExamQueue` passthrough that seeds the
// exam-priority session: cards are scored weight * (1 - retrievability) *
// urgency, merged across decks, weakest-first, and capped to a fetch limit.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.lessThan
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CfaExamQueueTest : RobolectricTest() {
    /**
     * With a weighted topic, a weak (low-retrievability) due card must rank
     * ahead of a strong one, weighted topics ahead of unweighted ones, and the
     * fetch limit must cap the result — no card scored twice.
     */
    @Test
    fun `build ranks weakest first, respects weights and fetch limit`() {
        // Weight only ethics, so its cards score above unweighted topics.
        CfaExamConfig.write(col, examDate = "2099-01-01", topicWeights = mapOf("los::ethics" to 1.0))

        val strong = addDueFsrsCard("ethics", stability = 500.0) // high R -> low weakness
        val weak = addDueFsrsCard("ethics", stability = 1.0) //     low R  -> high weakness
        val newUnweighted = List(3) { addNewCard("quant") } //       weight 0 -> ranked last

        val ordered = CfaExamQueue.build(col, fetchLimit = 0)

        assertThat(ordered, hasItem(weak))
        assertThat(ordered, hasItem(strong))
        assertThat("weakest ethics card ranks first", ordered.indexOf(weak), lessThan(ordered.indexOf(strong)))
        assertThat(
            "weighted ethics ranks above unweighted quant",
            ordered.indexOf(strong),
            lessThan(ordered.indexOf(newUnweighted.first())),
        )
        assertThat("each card scored once", ordered.toSet().size, equalTo(ordered.size))

        val capped = CfaExamQueue.build(col, fetchLimit = 2)
        assertThat(capped.size, lessThanOrEqualTo(2))
        assertThat("the cap keeps the two weakest weighted cards first", capped[0], equalTo(weak))
    }

    /** A due review card with FSRS memory state (stability drives retrievability). */
    private fun addDueFsrsCard(
        topic: String,
        stability: Double,
    ): Long {
        val note = addBasicNote("q-$topic-$stability", "a")
        val cid = note.firstCard().id
        val lrt = System.currentTimeMillis() / 1000 - 2 * 86_400
        col.db.execute("update notes set tags = ? where id = ?", " los::$topic::x ", note.id)
        col.db.execute(
            "update cards set data = ?, type = 2, queue = 2, due = 0, ivl = ?, reps = 1 where id = ?",
            """{"s":$stability,"d":5.0,"lrt":$lrt}""",
            maxOf(1, stability.toInt()),
            cid,
        )
        return cid
    }

    /** A never-reviewed (new) card in [topic] (treated as maximally weak). */
    private fun addNewCard(topic: String): Long {
        val note = addBasicNote("new-$topic-${counter++}", "a")
        val cid = note.firstCard().id
        col.db.execute("update notes set tags = ? where id = ?", " los::$topic::x ", note.id)
        return cid
    }

    private var counter = 0
}
