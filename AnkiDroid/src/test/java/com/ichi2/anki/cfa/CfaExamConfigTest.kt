// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Increment 4 (exam-config editor) persistence tests.
//
// The exam config is stored under the shared, synced col.conf key
// `cfa_exam_config` as { exam_date, topic_weights } — identical to the desktop
// reference (pylib/anki/cfa.py) so the same synced key drives desktop and phone.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CfaExamConfigTest : RobolectricTest() {
    @Test
    fun `read is null when unset`() {
        assertThat(CfaExamConfig.read(col), nullValue())
    }

    @Test
    fun `writeExamDate round-trips through col conf under the shared key`() {
        CfaExamConfig.writeExamDate(col, "2026-07-24")

        val cfg = CfaExamConfig.read(col)!!
        assertThat(cfg.examDate, equalTo("2026-07-24"))

        // Persisted under the shared synced key with the { exam_date, topic_weights } shape.
        val raw = col.config.getObject(EXAM_CONFIG_KEY, JSONObject())
        assertThat(raw.getString("exam_date"), equalTo("2026-07-24"))
        assertThat("topic_weights object is always present", raw.has("topic_weights"), equalTo(true))
    }

    @Test
    fun `writeExamDate preserves existing topic weights`() {
        CfaExamConfig.write(col, "2026-01-01", mapOf("los::ethics" to 0.15, "los::quant" to 0.10))

        // Changing only the date must not clobber the weights.
        CfaExamConfig.writeExamDate(col, "2026-07-24")

        val cfg = CfaExamConfig.read(col)!!
        assertThat(cfg.examDate, equalTo("2026-07-24"))
        assertThat(cfg.topicWeights["los::ethics"]!!, closeTo(0.15, 1e-9))
        assertThat(cfg.topicWeights["los::quant"]!!, closeTo(0.10, 1e-9))
    }
}
