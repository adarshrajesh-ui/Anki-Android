// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — "Study by Exam Priority" action (Increment 3).
//
// A thin trampoline: it calls the read-only `buildExamQueue` RPC (via
// [CfaExamQueue]) to score the whole collection weakest/most-urgent first,
// materialises the top cards into a reusable "CFA Exam Priority" filtered deck,
// then enters the Reviewer on it. Building the queue is read-only; studying the
// resulting filtered deck reschedules cards exactly like any normal review, so
// FSRS scheduling and undo remain valid (additive, no engine changes).

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import anki.decks.Deck
import anki.decks.DeckKt.FilteredKt.searchTerm
import anki.decks.DeckKt.filtered
import anki.decks.filteredDeckForUpdate
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.cfa.CfaExamQueue
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber

class CfaExamPriorityActivity : AnkiActivity(R.layout.activity_cfa_exam_priority) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        enterExamPrioritySession()
    }

    private fun enterExamPrioritySession() {
        launchCatchingTask {
            val cardCount = withCol { buildAndSelectPriorityDeck(this) }
            if (cardCount > 0) {
                Timber.i("Entering CFA exam-priority session (%d cards)", cardCount)
                startActivity(Reviewer.getIntent(this@CfaExamPriorityActivity))
                finish()
            } else {
                Timber.i("CFA exam-priority queue empty")
                findViewById<View>(R.id.cfa_priority_progress).visibility = View.GONE
                findViewById<TextView>(R.id.cfa_priority_message).setText(R.string.cfa_study_priority_empty)
                showSnackbar(R.string.cfa_study_priority_empty)
            }
        }
    }

    /**
     * Build the exam-priority queue and materialise it into the reusable
     * "CFA Exam Priority" filtered deck, selecting it. Returns the number of
     * cards actually gathered into the session.
     */
    private fun buildAndSelectPriorityDeck(col: Collection): Int {
        val cardIds = CfaExamQueue.build(col, fetchLimit = MAX_SESSION_CARDS)
        if (cardIds.isEmpty()) return 0

        val deckName = getString(R.string.cfa_study_priority_deck)
        val existingId = col.decks.idForName(deckName)?.takeIf { col.decks.isFiltered(it) }
        // `cid:` selects exactly the RPC-scored cards; the filtered deck then
        // orders them weakest-first (retrievability ascending) for the session.
        val cidSearch = "cid:" + cardIds.joinToString(",")

        val deckData =
            filteredDeckForUpdate {
                id = existingId ?: 0
                name = deckName
                allowEmpty = true
                config =
                    filtered {
                        // Reschedule like a normal review so FSRS stays authoritative.
                        reschedule = true
                        previewAgainSecs = 60
                        previewHardSecs = 600
                        previewGoodSecs = 0
                        searchTerms.add(
                            searchTerm {
                                search = cidSearch
                                limit = cardIds.size
                                order = Deck.Filtered.SearchTerm.Order.RETRIEVABILITY_ASCENDING
                            },
                        )
                    }
            }
        val did = col.sched.addOrUpdateFilteredDeck(deckData).id
        val gathered = col.sched.rebuildFilteredDeck(did).count
        col.decks.select(did)
        return gathered
    }

    companion object {
        /** Cap the session so a large backlog does not build an unwieldy deck. */
        private const val MAX_SESSION_CARDS = 100

        fun getIntent(context: Context): Intent = Intent(context, CfaExamPriorityActivity::class.java)
    }
}
