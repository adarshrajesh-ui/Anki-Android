// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — read/write the exam configuration in col.conf (Increment 4).
//
// The exam config is stored under `cfa_exam_config` as
//   { "exam_date": "YYYY-MM-DD", "topic_weights": { "los::ethics": 0.15, ... } }
// exactly matching the desktop reference (`pylib/anki/cfa.py::set_exam_config`),
// so the SAME synced col.conf key drives desktop and phone. Writing to col.conf
// syncs natively — no new sync endpoint (this is data, not sync plumbing).

package com.ichi2.anki.cfa

import com.ichi2.anki.libanki.Collection
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The exam date + per-topic weights persisted in col.conf. */
data class CfaExamConfigData(
    val examDate: String?,
    val topicWeights: Map<String, Double>,
)

object CfaExamConfig {
    /** Read the current exam config, or null when the key is absent/empty. */
    fun read(col: Collection): CfaExamConfigData? {
        val obj = col.config.getObject(EXAM_CONFIG_KEY, JSONObject())
        if (obj.length() == 0) return null
        val date = obj.optString("exam_date", "").ifEmpty { null }
        val weights = HashMap<String, Double>()
        obj.optJSONObject("topic_weights")?.let { w ->
            for (key in w.keys()) weights[key] = w.optDouble(key, 0.0)
        }
        return CfaExamConfigData(examDate = date, topicWeights = weights)
    }

    /**
     * Persist [examDate] (ISO yyyy-MM-dd) WITHOUT disturbing any existing
     * topic_weights, then re-save the merged object under [EXAM_CONFIG_KEY].
     */
    fun writeExamDate(
        col: Collection,
        examDate: String,
    ) {
        val obj = col.config.getObject(EXAM_CONFIG_KEY, JSONObject())
        obj.put("exam_date", examDate)
        if (!obj.has("topic_weights")) {
            obj.put("topic_weights", JSONObject())
        }
        col.config.set(EXAM_CONFIG_KEY, obj)
    }

    /**
     * Whole days from [today] to the ISO `yyyy-MM-dd` exam [date] (positive =
     * future, 0 = today, negative = past), or null when [date] is null/absent or
     * cannot be parsed. Pure so the countdown preview is unit-testable without a
     * device clock or resources.
     */
    fun daysUntil(
        date: String?,
        today: LocalDate,
    ): Long? {
        val iso = date?.ifEmpty { null } ?: return null
        return kotlin
            .runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(iso)) }
            .getOrNull()
    }

    /** Persist the full config (date + weights), matching set_exam_config. */
    fun write(
        col: Collection,
        examDate: String,
        topicWeights: Map<String, Double>,
    ) {
        val obj = JSONObject()
        obj.put("exam_date", examDate)
        val weights = JSONObject()
        for ((k, v) in topicWeights) weights.put(k, v)
        obj.put("topic_weights", weights)
        col.config.set(EXAM_CONFIG_KEY, obj)
    }
}
