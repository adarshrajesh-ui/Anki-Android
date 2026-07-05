// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Exam Readiness WebView payload builder (mobile side).
//
// The desktop Exam Readiness screen is a self-contained CFA-styled page
// (ts/lib/cfa/pages/CfaReadinessPage.svelte): a quiet brand lockup, the Bayesian
// pass/fail VERDICT hero (accuracy + 95% CI, or the honest abstain state), three
// VALUE-FIRST honest-score StatCards (Memory / Performance / Readiness), the
// per-topic recall table, a coverage caption, and an explanatory footer. The
// phone rendered the SAME data but as a hand-built native LinearLayout, so it
// diverged from the desktop design system.
//
// This builder turns the shared [CfaScores] into the compact JSON the new
// assets/cfa/readiness.html WebView asset consumes (via `window.CFA_READINESS_DATA`),
// exactly mirroring the Home / Concept Map WebView pattern so all three CFA
// surfaces read as one product. It is PURE (no Android / Collection deps): its
// number/percent/range/verdict formatting is byte-identical to the desktop
// readiness helpers (readiness.ts) and to CfaHome, so desktop == phone.

package com.ichi2.anki.cfa

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Pure builder producing the JSON payload the Exam Readiness WebView asset reads.
 * Kept free of Android/Collection deps so its display strings are guaranteed
 * identical to the desktop CfaReadinessPage and are trivially unit-testable.
 */
object CfaReadiness {
    /** Recall high/mid below this reads "at risk" and is warn-coloured (desktop LOW_RECALL). */
    const val LOW_RECALL = 0.6

    /** The standing explanatory footer, byte-mirroring qt/aqt/mediasrv.py `_CFA_READINESS_FOOTER`. */
    const val FOOTER =
        "The headline is a Bayesian call: exam-weighted accuracy as a 95% credible " +
            "band (per-topic Beta posterior on first-exposure correctness) that starts " +
            "wide and narrows as reviews accrue — no give-up wall. Recall uses FSRS R, " +
            "falling back to an SM-2 forgetting curve so a number appears from the first " +
            "review. Below it, the three give-up-gated scores: Memory (exam-weighted " +
            "per-topic FSRS retrievability), Performance (Wilson interval on " +
            "first-exposure accuracy) and Readiness (fused P(pass)). The pass/fail call " +
            "is NOT validated against real exam data. No AI — pure spaced-repetition stats."

    /** Whole-percent, matching CfaHome.pct + the desktop `pct`. */
    private fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

    private fun rangeText(
        low: Double,
        high: Double,
    ): String = "${pct(low)} – ${pct(high)}"

    /** StatCard big value: a range, or the honest "Awaiting reviews" while abstaining (desktop bandValue). */
    private fun bandValue(s: HonestScore): String =
        if (s.abstain || s.rangeLow == null || s.rangeHigh == null) {
            "Awaiting reviews"
        } else {
            rangeText(s.rangeLow, s.rangeHigh)
        }

    /** StatCard faint sub: the give-up reason, or the midpoint (desktop bandSub). */
    private fun bandSub(s: HonestScore): String =
        if (s.abstain || s.point == null) {
            s.reason
        } else {
            "midpoint ${pct(s.point)}"
        }

    /** StatCard tone: quiet "muted" while abstaining, else "neutral" (desktop bandTone). */
    private fun bandTone(s: HonestScore): String = if (s.abstain) "muted" else "neutral"

    private fun scoreCard(
        name: String,
        meaning: String,
        s: HonestScore,
    ): JSONObject =
        JSONObject()
            .put("name", name)
            .put("meaning", meaning)
            .put("value", bandValue(s))
            .put("sub", bandSub(s))
            .put("tone", bandTone(s))
            .put("abstain", s.abstain)

    /**
     * The Bayesian verdict hero, or the honest abstain state. The abstain GATE is
     * the exact desktop one (memory.abstain || performance.abstain) so the phone
     * and desktop hide/show the pass/fail call on identical evidence.
     */
    private fun hero(scores: CfaScores): JSONObject {
        val bayes = scores.bayesian
        val heroAbstain = scores.memory.abstain || scores.performance.abstain
        if (heroAbstain || bayes == null) {
            val reason =
                when {
                    scores.memory.abstain && scores.performance.abstain -> scores.readiness.reason
                    scores.memory.abstain -> scores.memory.reason
                    else -> scores.performance.reason
                }
            return JSONObject()
                .put("mode", "abstain")
                .put("headline", "Not enough data — keep studying")
                .put("note", "$reason · not validated against real exam data")
        }
        val recall = if (bayes.recall != null) pct(bayes.recall) else null
        val note =
            "Bayesian — the band starts wide and narrows as reviews accrue " +
                "(${bayes.firstExposures} first-seen · ${bayes.topicsCovered}/" +
                "${bayes.topicsTotal} topics studied). ${bayes.label}."
        return JSONObject()
            .put("mode", "bayesian")
            .put("call", bayes.call.replaceFirstChar { it.uppercase() })
            .put("passed", bayes.passed)
            .put("callProb", "p=${"%.2f".format(bayes.callProb)}")
            .put("accuracy", pct(bayes.accuracy))
            .put("ciLow", pct(bayes.ciLow))
            .put("ciHigh", pct(bayes.ciHigh))
            .put("mps", pct(bayes.mps))
            .put("recall", recall ?: JSONObject.NULL)
            .put("note", note)
    }

    /** Per-topic recall rows, weightiest first (desktop `topicRows` order). */
    private fun topicRows(topics: List<TopicRecall>): JSONArray {
        val rows = JSONArray()
        topics
            .sortedWith(compareByDescending<TopicRecall> { it.weight }.thenBy { it.displayName })
            .forEach { t ->
                val hasRecall = t.covered && t.avgR != null
                val tone =
                    when {
                        !hasRecall -> "muted"
                        t.avgR < LOW_RECALL -> "warn"
                        else -> "neutral"
                    }
                rows.put(
                    JSONObject()
                        .put("name", t.displayName)
                        .put("weight", "%.2f".format(t.weight))
                        .put("graded", t.gradedReviews)
                        .put("recall", if (hasRecall) pct(t.avgR) else "no data")
                        .put("tone", tone),
                )
            }
        return rows
    }

    /**
     * Build the full Exam Readiness payload JSON string the WebView consumes.
     *
     * @param scores the honest readiness scores (from [CfaScoresProvider]).
     */
    fun buildPayload(scores: CfaScores): String {
        val cards =
            JSONArray()
                // Meanings byte-mirror the desktop backend payload (qt/aqt/mediasrv.py).
                .put(scoreCard("Memory", "recall probability, exam-weighted across topics", scores.memory))
                .put(
                    scoreCard(
                        "Performance",
                        "P(correct on a new question), first-exposure accuracy",
                        scores.performance,
                    ),
                ).put(scoreCard("Readiness", "P(pass); wide range, uncalibrated", scores.readiness))

        val rows = topicRows(scores.topics)
        // A fresh deck: rows exist but none has recall yet → one calm hint, not
        // ten flat "no data" lines (desktop `noRecallYet`).
        val awaitingRecall = rows.length() > 0 && (0 until rows.length()).all { rows.getJSONObject(it).getString("recall") == "no data" }

        return JSONObject()
            .put("title", "Exam Readiness")
            .put("hero", hero(scores))
            .put("scores", cards)
            .put(
                "caption",
                "Coverage ${pct(scores.coveragePct)} (${scores.topicsCovered}/${scores.topicsTotal} topics) · " +
                    "${scores.gradedReviews} graded reviews · ${scores.firstExposures} first-seen",
            ).put("topics", rows)
            .put("awaitingRecall", awaitingRecall)
            .put("footer", FOOTER)
            .put("source", scores.source)
            .toString()
    }
}
