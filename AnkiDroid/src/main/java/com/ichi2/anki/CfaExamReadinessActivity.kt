// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Exam Readiness screen (mobile).
//
// Shows the honest Memory / Performance / Readiness scores WITH RANGES and the
// give-up (abstain) rule, the Bayesian pass/fail VERDICT hero, and per-topic
// recall, obtained from [CfaScoresProvider]. That provider returns the shared
// engine's numbers when the `computeCfaScores` RPC is available and a
// deterministic, NO-NETWORK on-device fallback otherwise.
//
// This screen renders the self-contained assets/cfa/readiness.html asset
// (styled with the shared CFA design tokens, so it reads as ONE product with the
// CFA Home + Concept Map screens — all three are the SAME WebView-asset pattern
// the desktop uses), fed the real scores by [CfaReadiness.buildPayload] via
// `window.CFA_READINESS_DATA`. Study CTAs call the [CfaReadinessBridge]
// @JavascriptInterface to launch the native priority-study / exam-config
// screens.
//
// This is additive: it opens from the CFA nav-drawer entry and never touches
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
import com.ichi2.anki.cfa.CfaReadiness
import com.ichi2.anki.cfa.CfaScoresProvider
import org.json.JSONObject
import timber.log.Timber

class CfaExamReadinessActivity : AnkiActivity(R.layout.activity_cfa_exam_readiness) {
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
            title = getString(R.string.cfa_title_exam_readiness)
        }

        webView = findViewById(R.id.cfa_readiness_web)
        webView.settings.apply {
            javaScriptEnabled = true
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.addJavascriptInterface(CfaReadinessBridge(this), "AndroidCfaReadiness")
        loadScores()
    }

    override fun onResume() {
        super.onResume()
        // Re-read after the user may have changed the exam config / studied.
        loadScores()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Compute the real scores off the main thread, then load the asset with the
     * payload injected as `window.CFA_READINESS_DATA` so the page draws from it
     * on first paint (no flash of the empty/abstain state).
     */
    private fun loadScores() {
        launchCatchingTask {
            val payload =
                try {
                    withCol { CfaReadiness.buildPayload(CfaScoresProvider.scores(this)) }
                } catch (e: Exception) {
                    // Honest empty state: the page still renders an abstaining view.
                    Timber.w(e, "CFA readiness score load failed; rendering abstain state")
                    JSONObject().put("source", "unavailable").toString()
                }
            val html = assets.open("cfa/readiness.html").bufferedReader().use { it.readText() }
            val injected = html.replaceFirst("<script>", "<script>window.CFA_READINESS_DATA=$payload;</script>\n<script>")
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
     * The @JavascriptInterface the Readiness asset's Study CTAs call to launch
     * the native CFA study / config screens. Only a fixed set of known targets is
     * honoured — arbitrary JS cannot start arbitrary activities.
     */
    class CfaReadinessBridge(
        private val activity: CfaExamReadinessActivity,
    ) {
        @JavascriptInterface
        fun open(target: String) {
            // Marshal back onto the UI thread — @JavascriptInterface runs on a
            // private WebView thread.
            activity.runOnUiThread {
                when (target) {
                    "priority" -> activity.startActivity(Intent(activity, CfaExamPriorityActivity::class.java))
                    "config" -> activity.startActivity(Intent(activity, CfaExamConfigActivity::class.java))
                    else -> Timber.w("CFA readiness: unknown CTA target %s", target)
                }
            }
        }
    }

    companion object {
        fun getIntent(from: Context): Intent = Intent(from, CfaExamReadinessActivity::class.java)
    }
}
