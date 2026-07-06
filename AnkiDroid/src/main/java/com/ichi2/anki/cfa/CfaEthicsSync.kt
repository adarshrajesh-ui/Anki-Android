// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — persist ethics attempt detail into card.custom_data (mobile side).
//
// Desktop writes a completed ethics attempt into card.custom_data under the
// "cfaEthic" namespace (see qt/aqt/cfa_ethics_sync.py + pylib/anki/cfa_sync.py's
// merge_custom_data / compact_ethics_payload). custom_data rides the normal Anki
// sync engine, so an ethics highlight / governing Standard graded on one device
// crosses to the others after a sync. This is the AnkiDroid parity: after the
// ethics answer side is shown, the completed-attempt payload the card template
// stashed in localStorage["cfaEthics:pending"] is compacted and merged into the
// SAME custom_data key/shape desktop reads, so ethics detail round-trips through
// normal collection sync (the review count already crosses via the revlog).
//
// The decode / compact / merge helpers are pure (no Collection or WebView) so
// they are unit-testable and lock the exact key/shape the desktop side expects.

package com.ichi2.anki.cfa

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

object CfaEthicsSync {
    /** Anki caps custom_data top-level keys at 8 bytes; desktop uses "cfaEthic". */
    const val NAMESPACE = "cfaEthic"

    /** The localStorage key the ethics card templates write the completed attempt to. */
    const val LOCAL_STORAGE_KEY = "cfaEthics:pending"

    /** Anki rejects a serialized custom_data blob larger than 100 bytes. */
    @VisibleForTesting
    const val MAX_CUSTOM_DATA_BYTES = 100

    /**
     * Persist a completed ethics attempt into [cardId]'s custom_data (parity with
     * desktop cfa_ethics_sync). [rawLocalStorageValue] is exactly what
     * `WebView.evaluateJavascript("localStorage.getItem(...)")` hands back — a
     * JSON-encoded string literal, or "null"/null when nothing is pending.
     *
     * Only genuinely completed attempts are written (matching the desktop hook),
     * and existing custom_data namespaces are preserved. Returns true iff a
     * payload was written.
     */
    fun persistFromLocalStorage(
        col: Collection,
        cardId: CardId,
        rawLocalStorageValue: String?,
    ): Boolean {
        val payload = decodePendingPayload(rawLocalStorageValue) ?: return false
        if (!payload.optBoolean("completed", false)) return false
        val card = col.getCard(cardId)
        val merged = mergeCustomData(card.customData, compactEthicsPayload(payload))
        // Mirror col.update_card: rebuild the backend card with the merged blob and
        // persist it (Card.customData has no public setter). Marks the card modified
        // so the change syncs like any other collection edit.
        val backendCard =
            card
                .toBackendCard()
                .toBuilder()
                .setCustomData(merged)
                .build()
        col.backend.updateCards(listOf(backendCard), false)
        return true
    }

    /**
     * Decode the raw `evaluateJavascript` result of `localStorage.getItem(...)`.
     * evaluateJavascript JSON-encodes the JS value, so a stored string arrives as
     * a quoted, escaped string literal (e.g. `"{\"pairId\":...}"`); a `null`/empty
     * result means nothing is pending. Returns the parsed attempt object, or null
     * on any malformed input (never throws).
     */
    @VisibleForTesting
    fun decodePendingPayload(rawLocalStorageValue: String?): JSONObject? {
        val raw = rawLocalStorageValue?.trim().orEmpty()
        if (raw.isEmpty() || raw == "null") return null
        return try {
            // First strip evaluateJavascript's outer JSON string encoding (a stored
            // string comes back quoted+escaped); then parse the attempt object. A
            // bare object literal (defensive) is parsed directly.
            val outer = JSONTokener(raw).nextValue()
            val json = if (outer is String) outer else raw
            JSONTokener(json).nextValue() as? JSONObject
        } catch (e: JSONException) {
            null
        }
    }

    /**
     * Shrink a W3 attempt-detail payload to the compact shape desktop stores
     * (mirror of pylib compact_ethics_payload), so it fits Anki's 100-byte cap and
     * the two platforms read the identical fields.
     */
    @VisibleForTesting
    fun compactEthicsPayload(payload: JSONObject): JSONObject {
        val id = payload.optString("pairId").ifEmpty { payload.optString("itemId") }
        return JSONObject()
            .put("id", id.take(12))
            .put("ok", payload.optBoolean("correct", false))
            .put("hl", payload.optString("highlight").take(8))
            .put("src", if (payload.optString("source") == "ai") "ai" else "fb")
            .put("std", payload.optString("standard").take(24))
    }

    /**
     * Merge [value] under [NAMESPACE] into an existing custom_data JSON string,
     * preserving every other namespace (mirror of pylib merge_custom_data). The
     * result is compact JSON (no spaces), matching desktop's serialisation.
     *
     * @throws IllegalArgumentException if the result exceeds Anki's 100-byte cap.
     */
    @VisibleForTesting
    fun mergeCustomData(
        existing: String?,
        value: JSONObject,
    ): String {
        val root =
            try {
                if (existing.isNullOrBlank()) {
                    JSONObject()
                } else {
                    (JSONTokener(existing).nextValue() as? JSONObject) ?: JSONObject()
                }
            } catch (e: JSONException) {
                JSONObject()
            }
        root.put(NAMESPACE, value)
        val serialized = root.toString()
        val bytes = serialized.toByteArray(Charsets.UTF_8).size
        require(bytes <= MAX_CUSTOM_DATA_BYTES) {
            "custom_data exceeds Anki's $MAX_CUSTOM_DATA_BYTES-byte limit ($bytes bytes)"
        }
        return serialized
    }
}
