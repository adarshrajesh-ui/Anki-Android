// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — the single seam through which the Exam Readiness screen obtains
// scores. Today it returns the deterministic on-device fallback ([CfaScores]).
//
// When the orchestrator ships the Rust `compute_cfa_scores` RPC and its Kotlin
// passthrough `col.backend.computeCfaScores(...)` (see
// proof/friday/mobile/HANDOFF.md for the exact proto + generated signature we
// need), swap the body of [scores] to call the RPC and map its response into
// [CfaScores] with source = SOURCE_RPC. The Activity does not change.

package com.ichi2.anki.cfa

import com.ichi2.anki.libanki.Collection

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

    /** Obtain the readiness scores, preferring the shared RPC when present. */
    fun scores(col: Collection): CfaScores {
        // NOTE: when computeCfaScores lands, call it here and map to CfaScores
        // (source = SOURCE_RPC). Until then, the deterministic fallback below is
        // the honest, no-network source of the numbers.
        return CfaScorer.compute(col)
    }
}
