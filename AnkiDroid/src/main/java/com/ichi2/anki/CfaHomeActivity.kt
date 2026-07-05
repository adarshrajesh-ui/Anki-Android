// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — CFA Home / Today dashboard, the native CFA landing screen (mobile).
//
// The desktop app opens into a native CFA Home dashboard rather than a raw deck
// list; this is the phone's equivalent. It renders the self-contained
// assets/cfa/home.html asset (styled with the shared CFA design tokens, so it
// reads as one product with the Concept Map + Readiness screens), fed the
// learner's REAL Memory / Performance / Readiness scores + the exam countdown by
// [CfaHome.buildPayload] via `window.CFA_HOME_DATA`. The Study CTAs call the
// [CfaHomeBridge] @JavascriptInterface to launch the corresponding native CFA
// screens, so Home is the phone's CFA navigation hub.
//
// Additive: it opens from the CFA nav-drawer "Home" entry and never touches
// FSRS / scheduling / sync state.

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
import com.ichi2.anki.cfa.CfaExamConfig
import com.ichi2.anki.cfa.CfaHome
import com.ichi2.anki.cfa.CfaScoresProvider
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneOffset

class CfaHomeActivity : AnkiActivity(R.layout.activity_cfa_home) {
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
            title = getString(R.string.cfa_title_home)
        }

        webView = findViewById(R.id.cfa_home_web)
        webView.settings.apply {
            javaScriptEnabled = true
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.addJavascriptInterface(CfaHomeBridge(this), "AndroidCfaHome")
        loadHome()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Compute the real scores + exam countdown off the main thread, then load the
     * asset with the payload injected as `window.CFA_HOME_DATA` so the dashboard
     * draws from it on first paint (no flash of the empty/abstain state).
     */
    private fun loadHome() {
        launchCatchingTask {
            // The master AI state drives the Home footer AI chip (default ON,
            // AI-first) — read alongside the scores so the chip matches the
            // synced toggle without a second collection round-trip.
            var aiEnabled = true
            val payload =
                try {
                    withCol {
                        aiEnabled = CfaAiSettings.masterEnabled(this)
                        val scores = CfaScoresProvider.scores(this)
                        val examDate = CfaExamConfig.read(this)?.examDate
                        val days = CfaExamConfig.daysUntil(examDate, LocalDate.now(ZoneOffset.UTC))
                        CfaHome.buildPayload(scores, days, examDate)
                    }
                } catch (e: Exception) {
                    // Honest empty state: the dashboard still renders an abstaining view.
                    Timber.w(e, "CFA home score load failed; rendering abstain state")
                    JSONObject().put("scores", org.json.JSONArray()).put("source", "unavailable").toString()
                }
            val html = assets.open("cfa/home.html").bufferedReader().use { it.readText() }
            val injected =
                html.replaceFirst(
                    "<script>",
                    "<script>window.CFA_HOME_DATA=$payload;window.CFA_AI_ENABLED=$aiEnabled;</script>\n<script>",
                )
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
     * The @JavascriptInterface the Home asset's Study CTAs call to launch the
     * corresponding native CFA screens. Only a fixed set of known targets is
     * honoured — arbitrary JS cannot start arbitrary activities.
     */
    class CfaHomeBridge(
        private val activity: CfaHomeActivity,
    ) {
        @JavascriptInterface
        fun open(target: String) {
            // Marshal back onto the UI thread — @JavascriptInterface runs on a
            // private WebView thread.
            activity.runOnUiThread {
                when (target) {
                    "ethics" -> activity.startActivity(CfaEthicsStudyActivity.getIntent(activity))
                    "readiness" -> activity.startActivity(CfaExamReadinessActivity.getIntent(activity))
                    "conceptmap" -> activity.startActivity(CfaConceptMapActivity.getIntent(activity))
                    "aiSettings" -> activity.startActivity(CfaAiSettingsActivity.getIntent(activity))
                    "priority" -> activity.startActivity(Intent(activity, CfaExamPriorityActivity::class.java))
                    "decks" -> {
                        val deckPicker = Intent(activity, DeckPicker::class.java)
                        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        activity.startActivity(deckPicker)
                    }
                    else -> Timber.w("CFA home: unknown CTA target %s", target)
                }
            }
        }
    }

    companion object {
        fun getIntent(from: Context): Intent = Intent(from, CfaHomeActivity::class.java)
    }
}
