// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — the mobile AI Settings screen, the phone equivalent of the desktop
// AI Settings dialog (qt/aqt/cfa_ai_settings.py).
//
// It renders the self-contained assets/cfa/ai_settings.html asset (styled with
// the shared CFA design tokens, so it reads as one product with the Home /
// Concept Map / Readiness screens), fed the learner's three SYNCED AI toggles by
// [CfaAiSettings.read] via `window.CFA_AI_SETTINGS`. Each switch persists through
// the [CfaAiSettingsBridge] @JavascriptInterface, writing the SAME col.conf keys
// the desktop uses — so a toggle flipped here is honoured on the desktop after a
// sync, and vice-versa. It never touches scheduling, sync state, or the proxy
// URL/token; it only reads/writes the three feature switches.

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.widget.Toolbar
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.cfa.CfaAiSettings
import timber.log.Timber

class CfaAiSettingsActivity : AnkiActivity(R.layout.activity_cfa_ai_settings) {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        val toolbar = findViewById<Toolbar>(R.id.cfa_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.cfa_title_ai_settings)
        }

        webView = findViewById(R.id.cfa_ai_settings_web)
        webView.settings.apply {
            javaScriptEnabled = true
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.addJavascriptInterface(CfaAiSettingsBridge(), "AndroidCfaAiSettings")
        loadSettings()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** Read the synced toggles off the main thread, then inject them into the asset. */
    private fun loadSettings() {
        launchCatchingTask {
            val payload =
                try {
                    withCol { CfaAiSettings.read(this) }
                } catch (e: Exception) {
                    // Honest default: render the AI-first (all-on) starting state.
                    Timber.w(e, "CFA AI settings load failed; rendering defaults")
                    CfaAiSettings.buildPayload(null, null, null)
                }
            val html = assets.open("cfa/ai_settings.html").bufferedReader().use { it.readText() }
            val injected =
                html.replaceFirst("<script>", "<script>window.CFA_AI_SETTINGS=$payload;</script>\n<script>")
            webView.loadDataWithBaseURL(
                "file:///android_asset/cfa/",
                injected,
                "text/html",
                "utf-8",
                null,
            )
        }
    }

    /**
     * The @JavascriptInterface the toggles call to persist. Only the three known
     * AI-toggle keys are honoured ([CfaAiSettings.setToggle] refuses others), so
     * arbitrary JS cannot write to col.conf through this bridge.
     */
    inner class CfaAiSettingsBridge {
        @JavascriptInterface
        fun setToggle(
            key: String,
            on: Boolean,
        ) {
            launchCatchingTask {
                withCol { CfaAiSettings.setToggle(this, key, on) }
            }
        }
    }

    companion object {
        fun getIntent(from: Context): Intent = Intent(from, CfaAiSettingsActivity::class.java)
    }
}
