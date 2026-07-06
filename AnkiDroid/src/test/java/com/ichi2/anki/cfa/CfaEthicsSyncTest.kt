// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — ethics custom_data persistence tests (mobile parity with desktop
// qt/aqt/cfa_ethics_sync.py + pylib/anki/cfa_sync.py). They lock:
//   * the decode of WebView.evaluateJavascript's JSON-encoded localStorage read,
//   * the compact `cfaEthic` payload shape desktop reads (id/ok/hl/src/std),
//   * a non-clobbering merge into card.custom_data under the shared 8-byte key,
//   * and the end-to-end write onto a real card (via the collection) so ethics
//     detail round-trips through normal Anki sync.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CfaEthicsSyncTest : RobolectricTest() {
    /** Exactly what `WebView.evaluateJavascript("localStorage.getItem(...)")` hands
     *  back for a stored string: the JSON-encoded (quoted + escaped) literal. */
    private fun evalResultFor(storedJson: String): String = JSONObject.quote(storedJson)

    private val completedAttempt =
        """{"pairId":"eth-3.1","itemId":"eth-3.1","cluster":"loyalty","completed":true,""" +
            """"correct":true,"standard":"Standard III(A)","source":"fallback","highlight":"clientfirst"}"""

    // --- decodePendingPayload -------------------------------------------------

    @Test
    fun `decode returns null for absent or empty localStorage`() {
        assertThat(CfaEthicsSync.decodePendingPayload(null), nullValue())
        assertThat(CfaEthicsSync.decodePendingPayload(""), nullValue())
        // getItem() of a missing key evaluates to JS null -> the string "null".
        assertThat(CfaEthicsSync.decodePendingPayload("null"), nullValue())
    }

    @Test
    fun `decode unwraps evaluateJavascript's JSON-encoded string literal`() {
        val payload = CfaEthicsSync.decodePendingPayload(evalResultFor(completedAttempt))
        assertThat(payload, not(nullValue()))
        assertThat(payload!!.getString("pairId"), equalTo("eth-3.1"))
        assertThat(payload.getBoolean("completed"), equalTo(true))
        assertThat(payload.getString("standard"), equalTo("Standard III(A)"))
    }

    // --- compactEthicsPayload (mirror of pylib compact_ethics_payload) --------

    @Test
    fun `compact keeps the exact desktop key set and coerces types`() {
        val compact = CfaEthicsSync.compactEthicsPayload(JSONObject(completedAttempt))
        assertThat(compact.keys().asSequence().toSet(), equalTo(setOf("id", "ok", "hl", "src", "std")))
        assertThat(compact.getString("id"), equalTo("eth-3.1"))
        assertThat(compact.getBoolean("ok"), equalTo(true))
        // highlight is truncated to 8 chars ("clientfirst" -> "clientfi").
        assertThat(compact.getString("hl"), equalTo("clientfi"))
        // a fallback (deterministic) grade records src="fb"; only source=="ai" is "ai".
        assertThat(compact.getString("src"), equalTo("fb"))
        assertThat(compact.getString("std"), equalTo("Standard III(A)"))
    }

    @Test
    fun `compact records the AI source and truncates long fields`() {
        val payload =
            JSONObject()
                .put("itemId", "passage-000000000-tail") // no pairId -> itemId, capped at 12
                .put("correct", false)
                .put("highlight", "verylonghighlightspan")
                .put("source", "ai")
                .put("standard", "Standard VII(B) Reference to CFA Institute Program")
        val compact = CfaEthicsSync.compactEthicsPayload(payload)
        assertThat(compact.getString("id"), equalTo("passage-0000")) // 12 chars
        assertThat(compact.getBoolean("ok"), equalTo(false))
        assertThat(compact.getString("hl"), equalTo("verylong")) // 8 chars
        assertThat(compact.getString("src"), equalTo("ai"))
        assertThat(compact.getString("std").length, equalTo(24)) // 24 chars
    }

    // --- mergeCustomData (mirror of pylib merge_custom_data) -------------------

    @Test
    fun `merge writes the cfaEthic namespace as compact JSON`() {
        val value = CfaEthicsSync.compactEthicsPayload(JSONObject(completedAttempt))
        val merged = CfaEthicsSync.mergeCustomData("", value)
        // compact (no spaces), same serialisation desktop uses.
        assertThat(merged, not(org.hamcrest.Matchers.containsString(": ")))
        val root = JSONObject(merged)
        assertThat(root.keys().asSequence().toSet(), equalTo(setOf("cfaEthic")))
        assertThat(root.getJSONObject("cfaEthic").getString("std"), equalTo("Standard III(A)"))
    }

    @Test
    fun `merge preserves other custom_data namespaces`() {
        // e.g. the custom-scheduler's own namespace must not be clobbered.
        val existing = """{"c":7}"""
        val value = CfaEthicsSync.compactEthicsPayload(JSONObject(completedAttempt))
        val root = JSONObject(CfaEthicsSync.mergeCustomData(existing, value))
        assertThat(root.getInt("c"), equalTo(7))
        assertThat(root.getJSONObject("cfaEthic").getString("id"), equalTo("eth-3.1"))
    }

    @Test
    fun `merge rejects a blob over Anki's 100-byte cap`() {
        val oversized = JSONObject().put("x", "a".repeat(200))
        try {
            CfaEthicsSync.mergeCustomData("", oversized)
            throw AssertionError("expected an IllegalArgumentException for an oversized blob")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message!!, org.hamcrest.Matchers.containsString("100-byte limit"))
        }
    }

    // --- persistFromLocalStorage (end-to-end onto a real card) ----------------

    @Test
    fun `persist writes the cfaEthic namespace onto the card and round-trips`() {
        val cardId = addBasicNote().firstCard().id

        val wrote = CfaEthicsSync.persistFromLocalStorage(col, cardId, evalResultFor(completedAttempt))
        assertThat(wrote, equalTo(true))

        val stored = JSONObject(col.getCard(cardId).customData).getJSONObject("cfaEthic")
        assertThat(stored.getString("id"), equalTo("eth-3.1"))
        assertThat(stored.getBoolean("ok"), equalTo(true))
        assertThat(stored.getString("hl"), equalTo("clientfi"))
        assertThat(stored.getString("src"), equalTo("fb"))
        assertThat(stored.getString("std"), equalTo("Standard III(A)"))
    }

    @Test
    fun `persist does not clobber an existing custom_data namespace on the card`() {
        val cardId = addBasicNote().firstCard().id
        // Seed a foreign namespace (as the scheduler would) before the ethics write.
        val seeded =
            col
                .getCard(cardId)
                .toBackendCard()
                .toBuilder()
                .setCustomData("""{"c":3}""")
                .build()
        col.backend.updateCards(listOf(seeded), false)

        CfaEthicsSync.persistFromLocalStorage(col, cardId, evalResultFor(completedAttempt))

        val root = JSONObject(col.getCard(cardId).customData)
        assertThat(root.getInt("c"), equalTo(3))
        assertThat(root.getJSONObject("cfaEthic").getString("id"), equalTo("eth-3.1"))
    }

    @Test
    fun `persist is a no-op for an incomplete attempt or absent payload`() {
        val cardId = addBasicNote().firstCard().id

        // completed flag missing/false -> nothing is written (matches desktop).
        val incomplete = """{"pairId":"eth-3.1","completed":false,"correct":true}"""
        assertThat(CfaEthicsSync.persistFromLocalStorage(col, cardId, evalResultFor(incomplete)), equalTo(false))
        assertThat(CfaEthicsSync.persistFromLocalStorage(col, cardId, null), equalTo(false))
        assertThat(CfaEthicsSync.persistFromLocalStorage(col, cardId, "null"), equalTo(false))

        assertThat(col.getCard(cardId).customData, equalTo(""))
    }
}
