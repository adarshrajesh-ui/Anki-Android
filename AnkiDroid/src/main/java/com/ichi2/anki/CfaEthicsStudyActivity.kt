// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — "Study Ethics — Minimal Pairs" action (the flagship ethics drill).
//
// The desktop Home makes Ethics Minimal-Pairs the PRIMARY study CTA
// (aqt/cfa.py `study_ethics_pairs`): it materialises the shipped
// `CFA::Ethics Pairs` deck into a reusable filtered study deck (oldest-seen
// first, rescheduling like a normal review so FSRS stays authoritative), then
// enters the Reviewer. This is the phone's equivalent so the flagship drill is
// directly launchable from the CFA Home instead of buried under "Browse decks".
//
// Additive: read-only queue build + a reschedule=true filtered deck (identical
// mechanism to CfaExamPriorityActivity); no engine/scheduling/sync changes. If
// the ethics deck is missing or empty it reports an honest empty state rather
// than a dead-end.

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
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber

class CfaEthicsStudyActivity : AnkiActivity(R.layout.activity_cfa_exam_priority) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        findViewById<TextView>(R.id.cfa_priority_message).setText(R.string.cfa_study_ethics)
        enterEthicsSession()
    }

    private fun enterEthicsSession() {
        launchCatchingTask {
            val cardCount = withCol { buildAndSelectEthicsDeck(this) }
            if (cardCount > 0) {
                Timber.i("Entering CFA ethics session (%d cards)", cardCount)
                startActivity(Reviewer.getIntent(this@CfaEthicsStudyActivity))
                finish()
            } else {
                Timber.i("CFA ethics queue empty")
                findViewById<View>(R.id.cfa_priority_progress).visibility = View.GONE
                findViewById<TextView>(R.id.cfa_priority_message).setText(R.string.cfa_study_ethics_empty)
                showSnackbar(R.string.cfa_study_ethics_empty)
            }
        }
    }

    /**
     * Gather the shipped `CFA::Ethics Pairs` deck into the reusable
     * "CFA Study — Ethics Minimal-Pairs" filtered deck (oldest-seen first,
     * rescheduling like a normal review), selecting it. Returns the number of
     * cards actually gathered — 0 if the ethics deck is missing or empty.
     */
    private fun buildAndSelectEthicsDeck(col: Collection): Int {
        val deckName = getString(R.string.cfa_study_ethics_deck)
        val existingId = col.decks.idForName(deckName)?.takeIf { col.decks.isFiltered(it) }

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
                                search = ETHICS_SOURCE_SEARCH
                                limit = MAX_SESSION_CARDS
                                order = Deck.Filtered.SearchTerm.Order.OLDEST_REVIEWED_FIRST
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

        /**
         * The shipped ethics source deck (matches the desktop
         * `ETHICS_DECK_NAME = "CFA::Ethics Pairs"` in aqt/cfa.py).
         */
        private const val ETHICS_SOURCE_SEARCH = "deck:\"CFA::Ethics Pairs\""

        fun getIntent(context: Context): Intent = Intent(context, CfaEthicsStudyActivity::class.java)
    }
}
