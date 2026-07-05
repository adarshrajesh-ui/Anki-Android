// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — CFA Home / Today dashboard data builder (mobile side).
//
// The desktop app OPENS INTO a native CFA Home dashboard (ts/lib/cfa/pages/
// CfaHomePage.svelte) instead of a raw deck list: a quiet brand lockup, the
// exam COUNTDOWN hero + an honest pass/fail one-liner, the three VALUE-FIRST
// honest scores (Memory / Performance / Readiness), and a STUDY grid of CTAs.
// The phone had no equivalent — it launched into the stock DeckPicker — so this
// is the mobile CFA Home, rendered from the SAME self-contained asset shape the
// Concept Map uses (assets/cfa/home.html) so it matches the desktop design
// system faithfully.
//
// This builder is PURE (no Android / Collection deps): it turns the readiness
// [CfaScores] + the exam countdown into the compact JSON the WebView asset reads
// as `window.CFA_HOME_DATA`. The number formatting mirrors the desktop Home
// helpers (ts/lib/cfa/pages/home.ts + readiness.ts) exactly — bandValue (a
// range, or "Awaiting reviews" while abstaining), bandSub (midpoint, or the
// give-up reason), bandTone (muted while abstaining), and the exam-countdown
// tone (warn inside 14 days) — so the same honest give-up rule holds on both
// platforms.

package com.ichi2.anki.cfa

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Pure builder producing the JSON payload the CFA Home WebView asset consumes.
 * Kept free of Android/Collection deps so it is trivially unit-testable and its
 * display strings are guaranteed identical to the desktop Home page's.
 */
object CfaHome {
    /** Days at or under which the exam countdown turns warn-coloured (desktop EXAM_SOON_DAYS). */
    const val EXAM_SOON_DAYS = 14L

    /** Whole-percent, matching CfaExamReadinessActivity.pct + the desktop `pct`. */
    private fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

    /** A score range "low – high", matching the desktop `rangeText`. */
    private fun rangeText(
        low: Double,
        high: Double,
    ): String = "${pct(low)} – ${pct(high)}"

    /** StatCard big value: a range, or the honest "Awaiting reviews" while abstaining (desktop bandValue). */
    fun bandValue(s: HonestScore): String =
        if (s.abstain || s.rangeLow == null || s.rangeHigh == null) {
            "Awaiting reviews"
        } else {
            rangeText(s.rangeLow, s.rangeHigh)
        }

    /** StatCard faint sub: the give-up reason, or the midpoint (desktop bandSub). */
    fun bandSub(s: HonestScore): String =
        if (s.abstain || s.point == null) {
            s.reason
        } else {
            "midpoint ${pct(s.point)}"
        }

    /** StatCard tone: quiet "muted" while abstaining (honest absence of data), else "neutral" (desktop bandTone). */
    fun bandTone(s: HonestScore): String = if (s.abstain) "muted" else "neutral"

    /**
     * The pass/fail one-liner leading the countdown hero. Honest: while the
     * Readiness score abstains it states the give-up reason rather than faking a
     * call; once calibrated it reports the estimated pass probability + range.
     * Mirrors the desktop `heroLead`.
     */
    fun heroLead(readiness: HonestScore): String =
        if (readiness.abstain || readiness.point == null) {
            "${readiness.reason} — keep studying to unlock a pass/fail call."
        } else {
            val range =
                if (readiness.rangeLow != null && readiness.rangeHigh != null) {
                    " (range ${rangeText(readiness.rangeLow, readiness.rangeHigh)})"
                } else {
                    ""
                }
            "Estimated pass probability ${pct(readiness.point)}$range."
        }

    /**
     * The exam-countdown hero. A missing exam date is a calm neutral prompt (not
     * a warning); a genuine deadline inside [EXAM_SOON_DAYS] turns warn-orange.
     * Mirrors the desktop `examCountdown`.
     */
    private fun countdown(
        daysToExam: Long?,
        examDate: String?,
    ): JSONObject {
        if (daysToExam == null || examDate == null) {
            return JSONObject()
                .put("headline", "Set your exam date")
                .put("sub", "Schedule it from Exam Readiness.")
                .put("tone", "neutral")
                .put("unset", true)
        }
        val headline =
            when {
                daysToExam <= 0 -> "Exam day is here"
                daysToExam == 1L -> "1 day to the exam"
                else -> "$daysToExam days to the exam"
            }
        val tone = if (daysToExam <= EXAM_SOON_DAYS) "warn" else "neutral"
        return JSONObject()
            .put("headline", headline)
            .put("sub", "CFA Level II · $examDate")
            .put("tone", tone)
            .put("unset", false)
    }

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
     * Build the full Home payload JSON string.
     *
     * @param scores the honest readiness scores (from [CfaScoresProvider]).
     * @param daysToExam whole days until the exam, or null when unset/past-parse-failure.
     * @param examDate the ISO exam date to echo in the hero sub, or null.
     */
    fun buildPayload(
        scores: CfaScores,
        daysToExam: Long?,
        examDate: String?,
    ): String {
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

        val hero =
            countdown(daysToExam, examDate)
                .put("lead", heroLead(scores.readiness))

        return JSONObject()
            .put("scores", cards)
            .put("hero", hero)
            .put(
                "caption",
                "Coverage ${pct(scores.coveragePct)} (${scores.topicsCovered}/${scores.topicsTotal} topics) · " +
                    "${scores.gradedReviews} graded reviews · ${scores.firstExposures} first-seen",
            ).put("source", scores.source)
            .toString()
    }
}
