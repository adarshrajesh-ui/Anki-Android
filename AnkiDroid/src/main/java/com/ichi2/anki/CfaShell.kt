// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — persistent mobile shell for the flagship CFA surfaces.

package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import android.widget.TextView

/**
 * Installs the Lane 4 CFA bottom navigation without changing the underlying
 * AnkiDroid flows. Every tab routes to an existing safe activity/surface.
 */
object CfaShell {
    enum class Tab(
        val itemId: Int,
    ) {
        TODAY(R.id.cfa_tab_today),
        STUDY(R.id.cfa_tab_study),
        READINESS(R.id.cfa_tab_readiness),
        MAP(R.id.cfa_tab_map),
        LIBRARY(R.id.cfa_tab_library),
        MORE(R.id.cfa_tab_more),
    }

    fun install(
        activity: Activity,
        selected: Tab,
    ) {
        Tab.entries.forEach { tab ->
            val item = activity.findViewById<TextView>(tab.itemId) ?: return@forEach
            val isSelected = tab == selected
            item.isSelected = isSelected
            item.alpha = if (isSelected) 1f else 0.72f
            item.setTextColor(activity.getColor(if (isSelected) R.color.cfa_gold_light else R.color.cfa_night_muted))
            item.setOnClickListener {
                if (tab != selected) {
                    activity.startActivity(intentFor(activity, tab))
                }
            }
        }
    }

    private fun intentFor(
        activity: Activity,
        tab: Tab,
    ): Intent =
        when (tab) {
            Tab.TODAY -> CfaHomeActivity.getIntent(activity)
            Tab.STUDY -> CfaExamPriorityActivity.getIntent(activity)
            Tab.READINESS -> CfaExamReadinessActivity.getIntent(activity)
            Tab.MAP -> CfaConceptMapActivity.getIntent(activity)
            Tab.LIBRARY ->
                DeckPicker
                    .getIntent(activity)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            Tab.MORE -> CfaAiSettingsActivity.getIntent(activity)
        }
}
