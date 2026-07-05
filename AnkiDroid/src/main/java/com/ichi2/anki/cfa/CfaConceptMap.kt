// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Concept Map data builder (headline Feature 5, mobile side).
//
// The Concept Map is the radial "mastery map" tab: CFA in the centre (biggest),
// the test sections orbiting it (SIZE = exam weight), each section's subsections
// beyond, node FILL going light-gray -> turquoise by mastery. It is meant to be
// IDENTICAL on phone and desktop, so the phone renders the SAME self-contained
// SVG/JS asset (assets/cfa/concept_map.html) the approved spec
// (.lavish/concept-map-spec.html) locks in, and this builder only feeds it the
// REAL per-topic mastery. The fixed layout + node SIZES (exam weight) live in
// the asset (canonical, matching the desktop TS engine ts/lib/cfa/pages/
// conceptmap.ts); this Kotlin side is purely: topic recall -> per-slug mastery.
//
// The honest give-up (abstain) rule is preserved verbatim: a topic that is not
// covered (no graded reviews / no retrievability yet) yields a NULL mastery, so
// its node stays gray in the map rather than faking a bright, unearned fill.
// This mirrors [CfaScorer]'s abstain rule and the desktop `masteryFromTopic`.

package com.ichi2.anki.cfa

import org.json.JSONObject

/**
 * Pure builder turning the readiness [TopicRecall] rows into the compact JSON
 * payload the concept-map WebView asset consumes. No Android/Collection deps, so
 * it is trivially unit-testable and matches the desktop engine's math exactly.
 */
object CfaConceptMap {
    /** `los::` tag prefix the topics are keyed by (shared with [CfaScorer]). */
    private const val TOPIC_PREFIX = "los::"

    /**
     * The asset keys its fixed layout by short slug (ethics, quant, fra, ...).
     * Strip the `los::` prefix and keep only the first hierarchy segment, so
     * `los::fra::pensions` and `los::fra` both map to `fra`.
     */
    fun slugOf(topic: String): String {
        val stripped = if (topic.startsWith(TOPIC_PREFIX)) topic.substring(TOPIC_PREFIX.length) else topic
        return stripped.substringBefore("::")
    }

    /**
     * A topic's mastery for the map, 0..1, or null when abstaining. Honest
     * give-up rule: an uncovered topic (no data yet) returns null so its node
     * stays gray until brightness is actually earned. Mirrors the desktop
     * `masteryFromTopic` (midpoint of the recall range) — here `avgR` already IS
     * the mean retrievability the desktop range is centred on.
     */
    fun masteryOf(t: TopicRecall): Double? {
        if (!t.covered || t.avgR == null) return null
        return t.avgR.coerceIn(0.0, 1.0)
    }

    /**
     * The centre CFA node's mastery: an exam-WEIGHT-adjusted roll-up of every
     * topic that has data (heavier sections move the centre most), matching the
     * desktop `overallMastery`. Abstains (null) when NO topic has data yet, so a
     * fresh deck shows a gray centre rather than a fake overall score. A topic
     * with a zero/unset weight still counts with a small floor (0.1) so an
     * unconfigured exam still rolls up as a plain average of covered topics.
     */
    fun overall(topics: List<TopicRecall>): Double? {
        var wsum = 0.0
        var msum = 0.0
        for (t in topics) {
            val m = masteryOf(t) ?: continue
            val w = if (t.weight > 0) t.weight else 0.1
            wsum += w
            msum += m * w
        }
        return if (wsum == 0.0) null else msum / wsum
    }

    /**
     * Build the JSON payload injected into the WebView asset as
     * `window.CFA_MAP_DATA`:
     * ```
     * { "slugs": { "ethics": 0.82, "quant": null, ... },
     *   "overall": 0.55, "source": "fallback" }
     * ```
     * `overall`/mastery values are `null` (JSON null) when abstaining.
     */
    fun buildPayload(
        topics: List<TopicRecall>,
        source: String,
    ): String {
        val slugs = JSONObject()
        for (t in topics) {
            val slug = slugOf(t.topic)
            val m = masteryOf(t)
            // Deterministic best-writer-wins if two rows share a slug (e.g. a
            // sub-topic tag): keep the brightest covered value the learner earned.
            val prev = if (slugs.has(slug)) slugs.opt(slug) else null
            val prevM = (prev as? Double)
            if (prev == null || (m != null && (prevM == null || prevM < m))) {
                slugs.put(slug, m ?: JSONObject.NULL)
            }
        }
        val ov = overall(topics)
        return JSONObject()
            .put("slugs", slugs)
            .put("overall", ov ?: JSONObject.NULL)
            .put("source", source)
            .toString()
    }

    /** Convenience overload reading straight from a computed [CfaScores]. */
    fun buildPayload(scores: CfaScores): String = buildPayload(scores.topics, scores.source)
}
