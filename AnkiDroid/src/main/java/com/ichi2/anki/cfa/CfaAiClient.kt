// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — mobile AI client. The phone never holds the OpenAI key; instead it
// POSTs to the server-side AI proxy (tools/cfa/ai_proxy.py) that runs alongside
// the self-hosted sync server, which does the LLM call and returns the draft
// WITH provenance. A shared bearer token (NOT the OpenAI key) gates access.
//
// Contract mirrors the desktop: on any failure (no proxy, no key on the server,
// network error) the result is source == "fallback" so the UI degrades cleanly
// and never blocks note entry.

package com.ichi2.anki.cfa

import com.ichi2.anki.libanki.Collection
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * One AI tab-fill result. [source] is "ai" on success, "fallback" otherwise;
 * [target] is the side that was generated ("back" or "front"), or null.
 */
data class CfaAiResult(
    val ok: Boolean,
    val text: String,
    val target: String?,
    val source: String,
    val model: String?,
    val error: String?,
)

/** Per-gold-span verdict inside a semantic ethics grade. */
data class CfaGradeSpan(
    val phrase: String,
    val matched: Boolean,
    val note: String,
)

/**
 * One semantic ethics-grade result from the proxy (`POST /cfa/grade`). Mirrors
 * the desktop `cfa/ethics_pairs/ai_grading.grade_semantic` contract exactly so
 * the phone renders the SAME partial-credit tiers, cited Standard, and personal
 * coaching. [source] is "ai" on a real semantic grade, "fallback" whenever the
 * proxy degraded (AI off, no key, network/parse error) — the card template's own
 * deterministic grade stays authoritative in that case.
 *
 * [grade] is one of "correct" | "somewhat" | "partial" | "wrong". [standard] is
 * the governing CFA Standard code+name the grader cites (echoed even on
 * fallback). [confidence] is null when the model did not report one.
 */
data class CfaGradeResult(
    val ok: Boolean,
    val source: String,
    val grade: String,
    val verdictCorrect: Boolean,
    val correct: Boolean,
    val explanation: String,
    val coaching: String,
    val studyTip: String,
    val confidence: Double?,
    val standard: String,
    val itemId: String,
    val perSpan: List<CfaGradeSpan>,
    val model: String?,
    val error: String?,
)

/** Injection seam for the HTTP POST so the client is unit-testable without network. */
fun interface CfaHttpPost {
    /** POST [jsonBody] to [url] with a Bearer [token]; return (httpStatus, body). */
    fun post(
        url: String,
        token: String,
        jsonBody: String,
    ): Pair<Int, String>
}

object CfaAiClient {
    const val URL_KEY = "cfa_ai_proxy_url"
    const val TOKEN_KEY = "cfa_ai_proxy_token"

    // AI-toggle contract — SHARED with the desktop `qt/aqt/cfa_ai_settings.py`
    // and stored in col.conf, so it SYNCS: turning AI off on the desktop is
    // honoured on the phone (and vice-versa). A feature's AI path runs only when
    // the master switch AND that feature's switch are on; both DEFAULT ON
    // (AI-first) to match the desktop `get_ai_toggles`. When off, the phone must
    // NOT call the proxy — it degrades to its deterministic fallback exactly as
    // the desktop does, and honestly reports `error == "ai_off"`.
    const val MASTER_KEY = "cfa_ai_enabled"
    const val TABFILL_KEY = "cfa_ai_tabfill_enabled"
    const val GRADING_KEY = "cfa_ai_grading_enabled"

    // Emulator -> host machine. Override via the synced col.conf keys above so a
    // real device can point at the LAN address of the machine running the proxy.
    const val DEFAULT_URL = "http://10.0.2.2:27702"
    const val DEFAULT_TOKEN = "cfa-ai-proxy"

    fun proxyUrl(col: Collection): String = (col.config.get<String>(URL_KEY) ?: "").ifBlank { DEFAULT_URL }

    fun proxyToken(col: Collection): String = (col.config.get<String>(TOKEN_KEY) ?: "").ifBlank { DEFAULT_TOKEN }

    /**
     * Pure toggle rule (desktop `ai_active`): AI runs for a feature only when the
     * [master] switch AND the [feature] switch are on. Both DEFAULT ON when unset
     * (null), matching the desktop AI-first defaults.
     */
    fun aiEnabled(
        master: Boolean?,
        feature: Boolean?,
    ): Boolean = (master ?: true) && (feature ?: true)

    /** Whether AI for [featureKey] (TABFILL_KEY / GRADING_KEY) is enabled by the SYNCED toggles. */
    fun aiEnabled(
        col: Collection,
        featureKey: String,
    ): Boolean = aiEnabled(col.config.get<Boolean>(MASTER_KEY), col.config.get<Boolean>(featureKey))

    /** The honest AI-off tab-fill result — no network call, deterministic fallback (desktop error "ai_off"). */
    fun aiOffFill(): CfaAiResult = CfaAiResult(false, "", null, "fallback", null, "ai_off")

    /**
     * Bidirectional tab-fill via the proxy configured in [col]: send both sides,
     * the server generates whichever is empty (front->back or back->front).
     * Skips the network entirely when the synced AI toggle is off (returns the
     * honest "ai_off" fallback). Never throws.
     */
    fun fill(
        col: Collection,
        front: String,
        back: String,
        poster: CfaHttpPost = DEFAULT_POST,
    ): CfaAiResult =
        if (!aiEnabled(col, TABFILL_KEY)) {
            aiOffFill()
        } else {
            fill(proxyUrl(col), proxyToken(col), front, back, poster)
        }

    /** Pure overload: POST {front, back} to the proxy at [baseUrl]; parse the result. */
    fun fill(
        baseUrl: String,
        token: String,
        front: String,
        back: String,
        poster: CfaHttpPost,
    ): CfaAiResult {
        if (front.isBlank() == back.isBlank()) {
            // both empty or both filled -> nothing to generate
            return CfaAiResult(false, "", null, "fallback", null, "nothing_to_fill")
        }
        val body =
            JSONObject()
                .put("front", front)
                .put("back", back)
                .toString()
        return try {
            val (status, resp) = poster.post("${baseUrl.trimEnd('/')}/cfa/tabfill", token, body)
            if (status != 200) {
                return CfaAiResult(false, "", null, "fallback", null, "http_$status")
            }
            val o = JSONObject(resp)
            CfaAiResult(
                ok = o.optBoolean("ok", false),
                text = o.optString("text", ""),
                target = o.optString("target", "").ifBlank { null },
                source = o.optString("source", "fallback"),
                model = o.optString("model", "").ifBlank { null },
                error = o.optString("error", "").ifBlank { null },
            )
        } catch (e: Exception) {
            CfaAiResult(false, "", null, "fallback", null, "client_error:${e.javaClass.simpleName}")
        }
    }

    /**
     * Semantic ethics grade via the proxy configured in [col]. Sends the passage,
     * the correct + learner verdicts, the authored gold evidence spans, and the
     * learner's highlighted phrases; the server grades SEMANTICALLY (meaning, not
     * exact wording) and returns partial-credit tiers + cited Standard + personal
     * coaching. Never throws — see [grade] below.
     */
    fun grade(
        col: Collection,
        passage: String,
        answerVerdict: String,
        judgedVerdict: String,
        goldSpans: List<Pair<String, String>>,
        learnerSpans: List<String>,
        itemId: String = "",
        standard: String = "",
        poster: CfaHttpPost = DEFAULT_POST,
    ): CfaGradeResult =
        if (!aiEnabled(col, GRADING_KEY)) {
            // AI grading toggled off (synced) — skip the proxy; the card's own
            // deterministic grade stays authoritative. Honest error "ai_off".
            fallbackGrade(standard, itemId, "ai_off")
        } else {
            grade(
                proxyUrl(col),
                proxyToken(col),
                passage,
                answerVerdict,
                judgedVerdict,
                goldSpans,
                learnerSpans,
                itemId,
                standard,
                poster,
            )
        }

    /** The honest AI-off grade result (no network) — the card's deterministic grade stays authoritative. */
    fun aiOffGrade(
        standard: String = "",
        itemId: String = "",
    ): CfaGradeResult = fallbackGrade(standard, itemId, "ai_off")

    /**
     * Pure overload: POST the attempt to `/cfa/grade` at [baseUrl] and parse the
     * result. On any failure (non-200, exception) this returns a `source ==
     * "fallback"` result with [error] set and no AI text, so the card keeps its
     * own deterministic grade and the UI honestly says AI was unavailable. Never
     * throws.
     */
    @Suppress("LongParameterList")
    fun grade(
        baseUrl: String,
        token: String,
        passage: String,
        answerVerdict: String,
        judgedVerdict: String,
        goldSpans: List<Pair<String, String>>,
        learnerSpans: List<String>,
        itemId: String,
        standard: String,
        poster: CfaHttpPost,
    ): CfaGradeResult {
        val gold = JSONArray()
        for ((phrase, rationale) in goldSpans) {
            gold.put(JSONObject().put("phrase", phrase).put("rationale", rationale))
        }
        val learner = JSONArray()
        for (p in learnerSpans) learner.put(p)
        val body =
            JSONObject()
                .put("passage", passage)
                .put("answerVerdict", answerVerdict)
                .put("judgedVerdict", judgedVerdict)
                .put("goldSpans", gold)
                .put("learnerSpans", learner)
                .put("itemId", itemId)
                .put("standard", standard)
                .toString()
        return try {
            val (status, resp) = poster.post("${baseUrl.trimEnd('/')}/cfa/grade", token, body)
            if (status != 200) {
                return fallbackGrade(standard, itemId, "http_$status")
            }
            parseGrade(JSONObject(resp), standard, itemId)
        } catch (e: Exception) {
            fallbackGrade(standard, itemId, "client_error:${e.javaClass.simpleName}")
        }
    }

    /** A client-side fallback grade (network/parse failure) — no AI text, honest error. */
    private fun fallbackGrade(
        standard: String,
        itemId: String,
        error: String,
    ) = CfaGradeResult(
        ok = false,
        source = "fallback",
        grade = "",
        verdictCorrect = false,
        correct = false,
        explanation = "",
        coaching = "",
        studyTip = "",
        confidence = null,
        standard = standard,
        itemId = itemId,
        perSpan = emptyList(),
        model = null,
        error = error,
    )

    /** Parse a `/cfa/grade` JSON body (snake_case result keys) into [CfaGradeResult]. */
    private fun parseGrade(
        o: JSONObject,
        fallbackStandard: String,
        fallbackItemId: String,
    ): CfaGradeResult {
        val spans = mutableListOf<CfaGradeSpan>()
        val arr = o.optJSONArray("per_span")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                spans.add(
                    CfaGradeSpan(
                        phrase = s.optString("phrase", ""),
                        matched = s.optBoolean("matched", false),
                        note = s.optString("note", ""),
                    ),
                )
            }
        }
        val confidence = if (o.isNull("confidence")) null else o.optDouble("confidence").takeIf { !it.isNaN() }
        return CfaGradeResult(
            ok = o.optBoolean("ok", false),
            source = o.optString("source", "fallback"),
            grade = o.optString("grade", ""),
            verdictCorrect = o.optBoolean("verdict_correct", false),
            correct = o.optBoolean("correct", false),
            explanation = o.optString("explanation", ""),
            coaching = o.optString("coaching", ""),
            studyTip = o.optString("study_tip", ""),
            confidence = confidence,
            standard = o.optString("standard", "").ifBlank { fallbackStandard },
            itemId = o.optString("item_id", "").ifBlank { fallbackItemId },
            perSpan = spans,
            model = o.optString("model", "").ifBlank { null },
            error = o.optString("error", "").ifBlank { null },
        )
    }

    /** Default HTTP POST via HttpURLConnection (no extra dependency). */
    val DEFAULT_POST =
        CfaHttpPost { url, token, jsonBody ->
            val conn = (URL(url).openConnection() as HttpURLConnection)
            conn.requestMethod = "POST"
            conn.connectTimeout = 5_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            try {
                conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                Pair(code, text)
            } finally {
                conn.disconnect()
            }
        }
}
