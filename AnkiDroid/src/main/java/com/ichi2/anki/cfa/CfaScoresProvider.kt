// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — the single seam through which the Exam Readiness screen obtains
// scores. It prefers the shared Rust `compute_cfa_scores` RPC (one engine for
// desktop + phone) and falls back to the deterministic on-device [CfaScorer]
// whenever the RPC is unavailable or fails.
//
// The RPC + its Kotlin passthrough `col.backend.computeCfaScores(...)` are
// produced by the fork engine build (Anki-Android-Backend →
// CFA_FORK_ENGINE.md). When present, the numbers come from the SAME Rust code
// the desktop app runs (`rslib/src/scheduler/cfa_scores.rs`); the Kotlin
// fallback mirrors the same Python reference (`pylib/anki/cfa.py`), so the two
// paths agree. The Activity does not change — it just reads [CfaScores.source]
// to show provenance.

package com.ichi2.anki.cfa

import anki.scheduler.ComputeCfaScoresResponse
import com.ichi2.anki.libanki.Collection
import timber.log.Timber

object CfaScoresProvider {
    /**
     * Whether the fork engine exposes the shared `computeCfaScores` RPC.
     *
     * Detected reflectively so this compiles both before and after the RPC is
     * added to the generated backend. While false, the deterministic on-device
     * fallback is authoritative (and is what the abstain/empty state shows).
     */
    fun rpcAvailable(col: Collection): Boolean =
        runCatching {
            col.backend.javaClass.methods
                .any { it.name == "computeCfaScores" }
        }.getOrDefault(false)

    /**
     * Obtain the readiness scores, preferring the shared RPC when present.
     *
     * If the backend exposes `computeCfaScores` AND the native engine actually
     * implements it, the returned [CfaScores] carries `source = SOURCE_RPC`.
     * Any failure (RPC absent, older native lib, backend error) transparently
     * degrades to the deterministic on-device [CfaScorer] (`source =
     * SOURCE_FALLBACK`) — the honest, no-network source of the numbers.
     */
    fun scores(col: Collection): CfaScores {
        if (rpcAvailable(col)) {
            runCatching { fromRpc(col) }
                .onSuccess {
                    Timber.i("CFA scores source=rpc (shared Rust engine)")
                    return it
                }.onFailure { e ->
                    Timber.w(e, "CFA computeCfaScores RPC failed; using on-device fallback")
                }
        }
        val fb = CfaScorer.compute(col)
        Timber.i("CFA scores source=fallback (on-device deterministic)")
        return fb
    }

    /** Call the shared RPC over the whole collection and map it into [CfaScores]. */
    private fun fromRpc(col: Collection): CfaScores {
        // whole_collection=true so we score every deck (matches the fallback);
        // now=0 lets the backend use its own wall clock for retrievability.
        val resp: ComputeCfaScoresResponse =
            col.backend.computeCfaScores(
                deckId = 0L,
                wholeCollection = true,
                now = 0L,
            )
        val mem = resp.memory
        val perf = resp.performance
        val rdy = resp.readiness

        val topics =
            mem.topicsList.map { t ->
                TopicRecall(
                    topic = t.topic,
                    displayName = topicDisplayName(t.topic),
                    weight = t.weight,
                    gradedReviews = t.gradedReviews,
                    avgR = if (t.hasAvgR()) t.avgR else null,
                    covered = t.covered,
                )
            }

        return CfaScores(
            memory = mem.toHonest(),
            performance = perf.toHonest(),
            readiness = rdy.toHonest(),
            topics = topics,
            topicsTotal = mem.topicsTotal,
            topicsCovered = mem.topicsCovered,
            coveragePct = mem.coveragePct,
            gradedReviews = mem.gradedReviews,
            firstExposures = perf.firstExposures,
            source = CfaScores.SOURCE_RPC,
        )
    }

    private fun anki.scheduler.CfaMemoryScore.toHonest(): HonestScore =
        HonestScore(
            abstain = abstain,
            reason = reason,
            point = if (hasPoint()) point else null,
            rangeLow = if (hasRangeLow()) rangeLow else null,
            rangeHigh = if (hasRangeHigh()) rangeHigh else null,
        )

    private fun anki.scheduler.CfaPerformanceScore.toHonest(): HonestScore =
        HonestScore(
            abstain = abstain,
            reason = reason,
            point = if (hasPoint()) point else null,
            rangeLow = if (hasRangeLow()) rangeLow else null,
            rangeHigh = if (hasRangeHigh()) rangeHigh else null,
        )

    private fun anki.scheduler.CfaReadinessScore.toHonest(): HonestScore =
        HonestScore(
            abstain = abstain,
            reason = reason,
            point = if (hasPoint()) point else null,
            rangeLow = if (hasRangeLow()) rangeLow else null,
            rangeHigh = if (hasRangeHigh()) rangeHigh else null,
        )
}
