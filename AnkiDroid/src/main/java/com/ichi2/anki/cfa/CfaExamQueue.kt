// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Kotlin passthrough to the read-only `buildExamQueue` RPC
// (Increment 3). Produces a weakest-first, exam-priority ordering of card ids
// used to seed the "Study by Exam Priority" filtered session.
//
// `col.backend.buildExamQueue(...)` is the shared Rust engine's read-only
// scoring RPC (score = topic_weight * (1 - retrievability) * deadline_urgency);
// it never mutates card/queue/scheduling state, so FSRS scheduling and undo
// stay valid. This mirrors the desktop reference `build_exam_queue_all_decks`
// (pylib/anki/cfa.py): score every top-level regular deck (children rolled up)
// once, then merge weakest-first (score desc, card id asc).

package com.ichi2.anki.cfa

import com.ichi2.anki.libanki.Collection
import timber.log.Timber
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object CfaExamQueue {
    /** Whole days from today to the configured exam date (>= 0), 0 when unset. */
    private fun daysToExam(col: Collection): Int {
        val date = CfaExamConfig.read(col)?.examDate ?: return 0
        return runCatching {
            ChronoUnit.DAYS
                .between(LocalDate.now(), LocalDate.parse(date))
                .coerceAtLeast(0)
                .toInt()
        }.getOrDefault(0)
    }

    /**
     * Collection-wide exam-priority queue (weakest/most-urgent first), merged
     * across every top-level regular deck. Read-only. Returns ordered card ids;
     * capped to [fetchLimit] when > 0.
     */
    fun build(
        col: Collection,
        fetchLimit: Int = 0,
    ): List<Long> {
        val cfg = CfaExamConfig.read(col)
        val weights: Map<String, Float> = cfg?.topicWeights?.mapValues { it.value.toFloat() } ?: emptyMap()
        val dte = daysToExam(col)

        val merged = ArrayList<Pair<Long, Float>>()
        for (entry in col.decks.allNamesAndIds(skipEmptyDefault = false, includeFiltered = false)) {
            // Top-level decks only; the RPC gathers each deck's children, so this
            // scores every card exactly once without double-counting.
            if (entry.name.contains("::")) continue
            val resp =
                col.backend.buildExamQueue(
                    deckId = entry.id,
                    daysToExam = dte,
                    topicWeights = weights,
                    fetchLimit = 0,
                    // Per-content-type multipliers (desktop A8) — unused on mobile
                    // today, so pass empty to keep the queue purely weight×weakness.
                    typeMultipliers = emptyMap(),
                )
            for (i in 0 until resp.cardIdsCount) {
                merged.add(resp.getCardIds(i) to resp.getScores(i))
            }
        }
        merged.sortWith(compareByDescending<Pair<Long, Float>> { it.second }.thenBy { it.first })
        Timber.i("CFA exam queue built: %d cards (daysToExam=%d)", merged.size, dte)
        val ordered = if (fetchLimit > 0) merged.take(fetchLimit) else merged
        return ordered.map { it.first }
    }
}
