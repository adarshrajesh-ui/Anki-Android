// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Exam Readiness scores adapter (mobile side of Increment 2 / D6).
//
// The desktop fork's Rust RPC `compute_cfa_scores` (Memory / Performance /
// Readiness + Bayesian hero, each a range with a give-up/abstain rule) is the
// canonical source of these numbers. That RPC + its Kotlin passthrough
// `col.backend.computeCfaScores(...)` are the orchestrator's Phase-0
// deliverable and are NOT yet in the fork engine AAR consumed here (only
// `buildExamQueue` is). See proof/friday/mobile/HANDOFF.md.
//
// Until the RPC lands, [CfaScorer.compute] provides a DETERMINISTIC, NO-NETWORK
// on-device fallback that mirrors the Python reference (`pylib/anki/cfa.py`):
// the same thresholds (MIN_GRADED_REVIEWS=200, MIN_TOPIC_COVERAGE=0.50,
// MIN_FIRST_EXPOSURES=30), the same give-up reason text, the same canonical
// topic list, and the same range shapes (Wilson / mean±stdev). This is what
// drives the abstain/empty state today; when the RPC is added, only
// [CfaScoresProvider] needs to be repointed — the UI is unchanged.

package com.ichi2.anki.cfa

import com.ichi2.anki.libanki.Collection
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.sqrt

/** col.conf key holding the exam config (date + per-topic weights). Syncs natively. */
const val EXAM_CONFIG_KEY = "cfa_exam_config"

/** Hierarchical topic tag prefix (join key shared with the Rust BuildExamQueue engine). */
private const val TOPIC_PREFIX = "los::"

// --- Give-up rule thresholds (must match pylib/anki/cfa.py) -------------------
const val MIN_GRADED_REVIEWS = 200
const val MIN_TOPIC_COVERAGE = 0.50
const val MIN_FIRST_EXPOSURES = 30

// Anki ease scale: 1=Again (incorrect), 2=Hard, 3=Good, 4=Easy. >=2 is correct.
private const val CORRECT_EASE = 2

/** Canonical CFA Level II topic areas, keyed by their `los::<slug>` tag prefix. */
val CANONICAL_TOPICS: List<String> =
    listOf(
        "los::altinv",
        "los::corp",
        "los::econ",
        "los::equity",
        "los::ethics",
        "los::fra",
        "los::portmgmt",
        "los::quant",
    )

private val TOPIC_DISPLAY_NAMES: Map<String, String> =
    mapOf(
        "ethics" to "Ethics & Professional Standards",
        "quant" to "Quantitative Methods",
        "econ" to "Economics",
        "fra" to "Financial Reporting & Analysis",
        "corp" to "Corporate Issuers",
        "equity" to "Equity Investments",
        "fixed_income" to "Fixed Income",
        "fi" to "Fixed Income",
        "derivatives" to "Derivatives",
        "altinv" to "Alternative Investments",
        "portmgmt" to "Portfolio Management",
    )

/** Human-readable CFA topic-area name for a `los::<slug>` tag prefix (presentation only). */
fun topicDisplayName(topic: String): String {
    val slug = (if (topic.startsWith(TOPIC_PREFIX)) topic.substring(TOPIC_PREFIX.length) else topic).substringBefore("::")
    TOPIC_DISPLAY_NAMES[slug]?.let { return it }
    val words =
        slug
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotEmpty() }
    return if (words.isEmpty()) topic else words.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}

/** A single honest score reported as a range, or an abstain reason (give-up rule). */
data class HonestScore(
    val abstain: Boolean,
    val reason: String,
    val point: Double?,
    val rangeLow: Double?,
    val rangeHigh: Double?,
)

/** Per-topic recall row for the readiness table. */
data class TopicRecall(
    val topic: String,
    val displayName: String,
    val weight: Double,
    val gradedReviews: Int,
    val avgR: Double?,
    val covered: Boolean,
)

/**
 * The full Exam Readiness payload: three honest scores + per-topic recall +
 * evidence, plus the provenance of where the numbers came from.
 */
data class CfaScores(
    val memory: HonestScore,
    val performance: HonestScore,
    val readiness: HonestScore,
    val topics: List<TopicRecall>,
    val topicsTotal: Int,
    val topicsCovered: Int,
    val coveragePct: Double,
    val gradedReviews: Int,
    val firstExposures: Int,
    /** "fallback" (deterministic on-device) or "rpc" (shared engine), for the UI. */
    val source: String,
) {
    companion object {
        const val SOURCE_FALLBACK = "fallback"
        const val SOURCE_RPC = "rpc"
    }
}

/**
 * Deterministic, no-network on-device computation of the CFA readiness scores,
 * mirroring `pylib/anki/cfa.py`. Reads only from the collection DB + col.conf.
 * Produces a [CfaScores] payload.
 */
object CfaScorer {
    /** Longest-prefix match of a card's `los::` tags against configured topics. */
    private fun topicOf(
        tags: String,
        topicPrefixes: List<String>,
    ): String? {
        var best: String? = null
        for (tag in tags.trim().split(Regex("\\s+"))) {
            if (!tag.startsWith(TOPIC_PREFIX)) continue
            for (prefix in topicPrefixes) {
                if (tag == prefix || tag.startsWith("$prefix::")) {
                    if (best == null || prefix.length > best.length) best = prefix
                }
            }
        }
        return best
    }

    /** (point, low, high) = mean ± population stdev, clamped to [0, 1]. */
    private fun meanRange(values: List<Double>): Triple<Double, Double, Double> {
        val point = values.average()
        val spread =
            if (values.size > 1) {
                sqrt(values.sumOf { (it - point) * (it - point) } / values.size)
            } else {
                0.0
            }
        return Triple(point, (point - spread).coerceAtLeast(0.0), (point + spread).coerceAtMost(1.0))
    }

    /** (point, low, high) Wilson score interval for a binomial proportion. */
    private fun wilson(
        successes: Int,
        n: Int,
        z: Double = 1.96,
    ): Triple<Double, Double, Double> {
        val phat = successes.toDouble() / n
        val denom = 1.0 + z * z / n
        val center = (phat + z * z / (2 * n)) / denom
        val margin = z * sqrt(phat * (1.0 - phat) / n + z * z / (4.0 * n * n)) / denom
        return Triple(phat, (center - margin).coerceAtLeast(0.0), (center + margin).coerceAtMost(1.0))
    }

    private fun readinessTopicPrefixes(weights: Map<String, Double>): List<String> =
        if (weights.isNotEmpty()) weights.keys.sorted() else CANONICAL_TOPICS

    /** Exam weights configured in col.conf, keyed by `los::` prefix (empty when unset). */
    private fun examWeights(col: Collection): Map<String, Double> {
        val cfg = col.config.getObject(EXAM_CONFIG_KEY, JSONObject())
        val weightsJson = cfg.optJSONObject("topic_weights") ?: return emptyMap()
        val out = HashMap<String, Double>()
        for (key in weightsJson.keys()) {
            out[key] = weightsJson.optDouble(key, 0.0)
        }
        return out
    }

    /** Compute all readiness scores deterministically on-device (no network). */
    fun compute(col: Collection): CfaScores {
        val weights = examWeights(col)
        val topicPrefixes = readinessTopicPrefixes(weights)

        // (tags, retrievability) per card. R is NULL for never-reviewed cards.
        // extract_fsrs_retrievability is a fork-engine SQL function (same one the
        // desktop reference uses), so this stays consistent with the RPC math.
        val today = col.sched.today
        val nextDayAt = col.sched.dayCutoff
        val nowSecs = System.currentTimeMillis() / 1000

        data class CardRow(
            val cid: Long,
            val tags: String,
            val r: Double?,
        )
        val cardRows = ArrayList<CardRow>()
        col.db
            .query(
                """
                select c.id, n.tags,
                  extract_fsrs_retrievability(
                    c.data,
                    case when c.odue != 0 then c.odue else c.due end,
                    c.ivl, ?, ?, ?)
                from cards c join notes n on c.nid = n.id
                """,
                today,
                nextDayAt,
                nowSecs,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val r = if (cursor.isNull(2)) null else cursor.getDouble(2)
                    cardRows.add(CardRow(cursor.getLong(0), cursor.getString(1) ?: "", r))
                }
            }

        // Graded reviews per card (ease > 0 excludes manual reschedules),
        // DE-DUPLICATED per card-day so a same-day cram of one card counts once
        // toward the give-up threshold. This mirrors the shared Rust engine's
        // `graded_reviews_by_card` (rslib/src/scheduler/cfa_scores.rs) exactly so
        // desktop and phone agree on the MIN_GRADED_REVIEWS give-up decision.
        // DAY_OFFSET (~1e7 days) keeps the day-bucket dividend positive.
        val dayOffset = 86_400.0 * 10_000_000.0
        val reviewCounts = HashMap<Long, Int>()
        col.db
            .query(
                """
                select cid, count(*) from (
                  select c.id as cid,
                    cast((cast(r.id as real)/1000.0 - ? + ?) / 86400.0 as integer) as day
                  from revlog r join cards c on r.cid = c.id
                  where r.ease > 0
                  group by c.id, day
                ) group by cid
                """,
                nextDayAt,
                dayOffset,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    reviewCounts[cursor.getLong(0)] = cursor.getInt(1)
                }
            }

        // --- Per-topic memory (average retrievability) --------------------
        val perR = HashMap<String, MutableList<Double>>().apply { topicPrefixes.forEach { put(it, mutableListOf()) } }
        val perReviews = HashMap<String, Int>().apply { topicPrefixes.forEach { put(it, 0) } }
        for (row in cardRows) {
            val topic = topicOf(row.tags, topicPrefixes) ?: continue
            perReviews[topic] = (perReviews[topic] ?: 0) + (reviewCounts[row.cid] ?: 0)
            if (row.r != null) perR[topic]!!.add(row.r)
        }

        val topics =
            topicPrefixes.map { topic ->
                val rValues = perR[topic]!!
                val avg = if (rValues.isNotEmpty()) meanRange(rValues).first else null
                TopicRecall(
                    topic = topic,
                    displayName = topicDisplayName(topic),
                    weight = weights[topic] ?: 0.0,
                    gradedReviews = perReviews[topic] ?: 0,
                    avgR = avg,
                    covered = (perReviews[topic] ?: 0) > 0 && rValues.isNotEmpty(),
                )
            }

        val totalReviews = reviewCounts.values.sum()
        val topicsTotal = topicPrefixes.size
        val topicsCovered = topics.count { it.covered }
        val coveragePct = if (topicsTotal > 0) topicsCovered.toDouble() / topicsTotal else 0.0

        val memory = memoryScore(topics, totalReviews, coveragePct, weights, perR)

        // --- Performance: P(correct on a first exposure) ------------------
        var firstExposures = 0
        var correct = 0
        col.db
            .query(
                """
                select r.ease
                from revlog r
                join (select cid, min(id) as mid from revlog where ease > 0 group by cid) first
                  on first.cid = r.cid and first.mid = r.id
                """,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    firstExposures++
                    if (cursor.getInt(0) >= CORRECT_EASE) correct++
                }
            }
        val performance = performanceScore(firstExposures, correct)

        val readiness = readinessScore(memory, performance, coveragePct)

        return CfaScores(
            memory = memory,
            performance = performance,
            readiness = readiness,
            topics = topics,
            topicsTotal = topicsTotal,
            topicsCovered = topicsCovered,
            coveragePct = coveragePct,
            gradedReviews = totalReviews,
            firstExposures = firstExposures,
            source = CfaScores.SOURCE_FALLBACK,
        )
    }

    private fun giveupReason(
        topics: List<TopicRecall>,
        totalReviews: Int,
        coveragePct: Double,
        weights: Map<String, Double>,
    ): String? {
        if (topics.isEmpty()) return "not enough data: no topics found (tag notes with los::…)"
        if (totalReviews < MIN_GRADED_REVIEWS || coveragePct < MIN_TOPIC_COVERAGE) {
            return "not enough data: " +
                "$totalReviews graded reviews (need $MIN_GRADED_REVIEWS), " +
                "${(coveragePct * 100).toInt()}% topic coverage (need ${(MIN_TOPIC_COVERAGE * 100).toInt()}%)"
        }
        if (weights.isNotEmpty()) {
            val positive = weights.values.filter { it > 0 }
            val threshold = if (positive.isNotEmpty()) positive.average() else 0.0
            val skipped =
                topics
                    .filter { it.weight >= threshold && it.weight > 0 && !it.covered }
                    .map { it.topic }
                    .sorted()
            if (skipped.isNotEmpty()) return "high-weight topic(s) skipped, no score: ${skipped.joinToString(", ")}"
        }
        return null
    }

    private fun memoryScore(
        topics: List<TopicRecall>,
        totalReviews: Int,
        coveragePct: Double,
        weights: Map<String, Double>,
        perR: Map<String, MutableList<Double>>,
    ): HonestScore {
        val reason = giveupReason(topics, totalReviews, coveragePct, weights)
        val covered = topics.filter { it.covered && it.avgR != null }
        if (reason != null || covered.isEmpty()) {
            return HonestScore(true, reason ?: "not enough data", null, null, null)
        }
        // Exam-weighted mean ± weighted stdev (falls back to equal weight when all zero).
        val pairs = covered.map { Pair(it.avgR!!, it.weight) }
        val totalW = pairs.sumOf { it.second }
        val (point, low, high) =
            if (totalW <= 0) {
                meanRange(pairs.map { it.first })
            } else {
                val p = pairs.sumOf { it.first * it.second } / totalW
                val spread =
                    if (pairs.size > 1) {
                        sqrt(pairs.sumOf { it.second * (it.first - p) * (it.first - p) } / totalW)
                    } else {
                        0.0
                    }
                Triple(p, (p - spread).coerceAtLeast(0.0), (p + spread).coerceAtMost(1.0))
            }
        return HonestScore(false, "", point, low, high)
    }

    private fun performanceScore(
        firstExposures: Int,
        correct: Int,
    ): HonestScore {
        if (firstExposures < MIN_FIRST_EXPOSURES) {
            return HonestScore(
                true,
                "not enough data: $firstExposures first-seen questions (need $MIN_FIRST_EXPOSURES)",
                null,
                null,
                null,
            )
        }
        val (point, low, high) = wilson(correct, firstExposures)
        return HonestScore(false, "", point, low, high)
    }

    // Readiness fusion constants (mirror pylib/anki/cfa.py).
    private const val MPS = 0.65
    private const val READINESS_K = 8.0
    private const val GUESS_RATE = 1.0 / 3.0
    private const val READINESS_MARGIN = 0.15

    private fun passProb(accuracy: Double): Double = 1.0 / (1.0 + exp(-READINESS_K * (accuracy - MPS)))

    private fun readinessScore(
        mem: HonestScore,
        perf: HonestScore,
        coveragePct: Double,
    ): HonestScore {
        if (mem.abstain || perf.abstain) {
            val why = mutableListOf<String>()
            if (mem.abstain) why.add("memory (${mem.reason})")
            if (perf.abstain) why.add("performance (${perf.reason})")
            return HonestScore(true, "not enough data to estimate readiness: " + why.joinToString("; "), null, null, null)
        }
        val cov = coveragePct

        fun acc(
            m: Double,
            p: Double,
        ): Double = cov * (0.5 * m + 0.5 * p) + (1.0 - cov) * GUESS_RATE
        val accPoint = acc(mem.point!!, perf.point!!)
        val accLow = acc(mem.rangeLow!!, perf.rangeLow!!)
        val accHigh = acc(mem.rangeHigh!!, perf.rangeHigh!!)
        val point = passProb(accPoint)
        val low = (passProb(accLow) - READINESS_MARGIN).coerceAtLeast(0.0)
        val high = (passProb(accHigh) + READINESS_MARGIN).coerceAtMost(1.0)
        return HonestScore(false, "", point, low, high)
    }
}
