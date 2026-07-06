// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — the mobile AI-settings model (data + col.conf read/write).
//
// The desktop app exposes the two optional AI features (semantic ethics grading
// + tab-to-fill) behind three toggles it stores in `col.conf`
// (`qt/aqt/cfa_ai_settings.py`): a MASTER switch plus one per feature. Those keys
// SYNC, so the phone already HONOURS a desktop toggle (see `CfaAiClient`) — but
// the phone had no UI to READ or CHANGE them, so "each AI feature with its own
// toggle, displayed consistently on desktop and phone" was unmet on mobile.
//
// This is the phone's equivalent control. It reuses the exact shared keys +
// AI-first (default-ON) semantics `CfaAiClient` gates on, so a change here is
// honoured on the desktop after a sync and vice-versa. The screen itself is a
// self-contained CFA-styled WebView asset (`assets/cfa/ai_settings.html`) hosted
// by [com.ichi2.anki.CfaAiSettingsActivity]; this object is the PURE data layer
// (no Android deps beyond the Collection) so its payload + write rule are
// trivially unit-testable.

package com.ichi2.anki.cfa

import com.ichi2.anki.libanki.Collection
import org.json.JSONObject

object CfaAiSettings {
    // The three user-flippable toggles, keyed IDENTICALLY to the desktop
    // `cfa_ai_settings.py` + the reader in [CfaAiClient], so they sync.
    const val MASTER_KEY = CfaAiClient.MASTER_KEY
    const val GRADING_KEY = CfaAiClient.GRADING_KEY
    const val TABFILL_KEY = CfaAiClient.TABFILL_KEY

    /** The only keys [setToggle] will write — arbitrary JS bridge input cannot poke other config. */
    val TOGGLE_KEYS = setOf(MASTER_KEY, GRADING_KEY, TABFILL_KEY)

    /**
     * The payload the WebView asset reads as `window.CFA_AI_SETTINGS`. All three
     * DEFAULT ON when unset (null), matching the desktop AI-first `get_ai_toggles`
     * so the switches show the same starting state on both platforms.
     */
    fun buildPayload(
        master: Boolean?,
        grading: Boolean?,
        tabfill: Boolean?,
    ): String =
        JSONObject()
            .put("master", master ?: true)
            .put("grading", grading ?: true)
            .put("tabfill", tabfill ?: true)
            .toString()

    /** Read the three synced toggles into the WebView payload. */
    fun read(col: Collection): String =
        buildPayload(
            col.config.get<Boolean>(MASTER_KEY),
            col.config.get<Boolean>(GRADING_KEY),
            col.config.get<Boolean>(TABFILL_KEY),
        )

    /** Whether the MASTER switch is on (default ON) — drives the Home AI-state chip. */
    fun masterEnabled(col: Collection): Boolean = col.config.get<Boolean>(MASTER_KEY) ?: true

    /**
     * Persist the small Home AI/no-AI toggle. Turning AI on also turns semantic
     * ethics grading back on so Home cannot display "AI On" while ethics cards
     * remain pinned to the deterministic fallback by a stale per-feature switch.
     */
    fun setMasterFromHome(
        col: Collection,
        on: Boolean,
    ) {
        col.config.set(MASTER_KEY, on)
        if (on) {
            col.config.set(GRADING_KEY, true)
            col.config.set(TABFILL_KEY, true)
        }
    }

    /**
     * Persist one toggle to `col.conf` (which syncs). Ignores any key that is not
     * one of the three known toggles and returns whether it wrote, so the bridge
     * can refuse arbitrary input. Never touches scheduling or the proxy URL/token.
     */
    fun setToggle(
        col: Collection,
        key: String,
        on: Boolean,
    ): Boolean {
        if (key !in TOGGLE_KEYS) return false
        col.config.set(key, on)
        return true
    }
}
