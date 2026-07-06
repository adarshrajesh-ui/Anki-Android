// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Concept Map per-concept due/new count tests.
//
// Exercises [CfaConceptMap.conceptCounts], the whole-collection card counter
// that feeds the Concept Map's detail chip ("<n> cards due"). Verifies the
// shared count spec the desktop worker also implements: for each canonical
// `los::<slug>` concept the due count is `("tag:K" OR "tag:K::*") is:due` and the
// new count the same with `is:new` — i.e. the exact tag OR any descendant, split
// by Anki's due-now (review+learning, excludes new) vs. new card states.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CfaConceptCountsTest : RobolectricTest() {
    /** A fresh collection has no cards, so every canonical concept counts 0/0. */
    @Test
    fun `empty collection yields zero counts for every canonical concept`() {
        val counts = CfaConceptMap.conceptCounts(col)

        // All ten canonical concepts are present (incl. the two the narrower
        // scorer list omits), each with a zero due/new count.
        assertThat(counts.size, equalTo(CfaConceptMap.CONCEPT_TAGS.size))
        for (tag in CfaConceptMap.CONCEPT_TAGS) {
            val c = counts[CfaConceptMap.slugOf(tag)]!!
            assertThat("$tag due", c.due, equalTo(0))
            assertThat("$tag new", c.new, equalTo(0))
        }
        // Concepts the abstain-scorer omits still get a real (zero) count here.
        assertThat(counts.containsKey("derivatives"), equalTo(true))
        assertThat(counts.containsKey("fixed-income"), equalTo(true))
    }

    /** Due (review-now) and new cards are counted separately, per concept. */
    @Test
    fun `counts split due versus new and are scoped per concept`() {
        addDueCard("los::ethics::standards")
        addDueCard("los::ethics::gips")
        addNewCard("los::ethics::standards")
        addNewCard("los::quant::regression")

        val counts = CfaConceptMap.conceptCounts(col)

        assertThat(counts["ethics"]!!.due, equalTo(2))
        assertThat(counts["ethics"]!!.new, equalTo(1))
        // Quant has only a new card — a "0 due · 1 new" concept, not a bare 0.
        assertThat(counts["quant"]!!.due, equalTo(0))
        assertThat(counts["quant"]!!.new, equalTo(1))
        // An untouched sibling concept stays at zero (no cross-concept leakage).
        assertThat(counts["fra"]!!.due, equalTo(0))
        assertThat(counts["fra"]!!.new, equalTo(0))
    }

    /** The exact concept tag AND any descendant tag both count toward it. */
    @Test
    fun `exact and child tags both match the concept`() {
        addDueCard("los::ethics") // exact concept tag
        addDueCard("los::ethics::standards") // direct child
        addNewCard("los::ethics::gips::deep") // deep descendant

        val counts = CfaConceptMap.conceptCounts(col)

        assertThat(counts["ethics"]!!.due, equalTo(2))
        assertThat(counts["ethics"]!!.new, equalTo(1))
    }

    /** A prefix that only shares a stem must NOT be miscounted as the concept. */
    @Test
    fun `a look-alike tag is not counted as the concept`() {
        // `los::ethicsx` shares the "ethics" stem but is a different tag: it must
        // match neither `tag:los::ethics` (exact) nor `tag:los::ethics::*` (child).
        addDueCard("los::ethicsx")

        val counts = CfaConceptMap.conceptCounts(col)

        assertThat(counts["ethics"]!!.due, equalTo(0))
        assertThat(counts["ethics"]!!.new, equalTo(0))
    }

    /** The hyphenated `los::fixed-income` concept is counted like any other. */
    @Test
    fun `hyphenated fixed-income concept is counted`() {
        addDueCard("los::fixed-income::term-structure")
        addNewCard("los::fixed-income")

        val counts = CfaConceptMap.conceptCounts(col)

        assertThat(counts["fixed-income"]!!.due, equalTo(1))
        assertThat(counts["fixed-income"]!!.new, equalTo(1))
    }

    // --- seed helpers --------------------------------------------------------

    /** Add a review card that is due now (type/queue = review, due day 0), tagged [tag]. */
    private fun addDueCard(tag: String): Long {
        val cid = addTaggedCard(tag)
        col.db.execute("update cards set type = 2, queue = 2, due = 0 where id = ?", cid)
        return cid
    }

    /** Add a brand-new card (default new type/queue), tagged [tag]. */
    private fun addNewCard(tag: String): Long = addTaggedCard(tag)

    private fun addTaggedCard(tag: String): Long {
        val note = addBasicNote("q-$counter", "a-$counter")
        counter += 1
        val cid = note.firstCard().id
        // Space-pad exactly as Anki stores tags so the tag search matches.
        col.db.execute("update notes set tags = ? where id = ?", " $tag ", note.id)
        return cid
    }

    private var counter = 0
}
