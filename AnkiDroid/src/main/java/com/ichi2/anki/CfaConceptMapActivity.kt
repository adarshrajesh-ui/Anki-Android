// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Concept Map screen (headline Feature 5, mobile side).
//
// The Concept Map is the radial "mastery map" tab, meant to be IDENTICAL on
// phone and desktop. It renders the SAME self-contained SVG/JS asset the desktop
// concept-map page is built from (assets/cfa/concept_map.html, matching the
// approved spec .lavish/concept-map-spec.html), in a WebView. The learner's REAL
// per-topic mastery is computed by the shared readiness engine
// ([CfaScoresProvider]) and injected as `window.CFA_MAP_DATA` before the map
// draws, so node FILL reflects the same numbers the Exam Readiness screen shows
// (and the honest give-up/abstain rule stays intact — uncovered topics render
// gray). Pinch-zoom is enabled via the WebView's built-in zoom controls.
//
// Additive: it opens from the CFA nav-drawer entry and never touches
// FSRS / scheduling / sync state.

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.Toolbar
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.cfa.CfaAiClient
import com.ichi2.anki.cfa.CfaConceptMap
import com.ichi2.anki.cfa.CfaMapExplainResult
import com.ichi2.anki.cfa.CfaScoresProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

class CfaConceptMapActivity : AnkiActivity(R.layout.activity_cfa_concept_map) {
    private lateinit var webView: WebView

    /** Skip reloading on the first resume: onCreate already loaded the payload. */
    private var reloadOnResume = false

    /** How many times the map payload has been (re)loaded. Test seam for the
     *  reload-on-resume guarantee. */
    @VisibleForTesting
    internal var payloadLoadCount = 0
        private set

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
            title = getString(R.string.cfa_title_concept_map)
        }
        CfaShell.install(this, CfaShell.Tab.MAP)

        webView = findViewById(R.id.cfa_concept_web)
        webView.settings.apply {
            javaScriptEnabled = true
            // Pinch-zoom on phone (objective: "pinch-zoom on phone"), without the
            // legacy on-screen zoom buttons.
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        // The SINGLE batched AI explanation call (parity with the desktop pycmd
        // cfaExplainMap bridge): the map JS, once built, hands us its node list and
        // we answer with an id -> plain-English "why" map from ONE proxy call.
        webView.addJavascriptInterface(CfaMapBridge(this), "AndroidCfaMap")
        loadMap()
    }

    override fun onResume() {
        super.onResume()
        // A sync or study session finished while this screen was backgrounded can
        // change per-topic mastery; re-read so the map reflects it on return
        // without an app restart (parity with CfaExamReadinessActivity). The first
        // resume is skipped because onCreate already loaded the payload, avoiding a
        // double-load flash on first open.
        if (reloadOnResume) {
            loadMap()
        }
        reloadOnResume = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Compute the real per-topic mastery off the main thread, then load the
     * self-contained asset with the data injected as `window.CFA_MAP_DATA` so the
     * map draws from it on first paint (no flash of the empty/abstain state).
     */
    private fun loadMap() {
        payloadLoadCount++
        launchCatchingTask {
            val payload =
                try {
                    // Score + per-concept due/new counts in ONE collection pass so the
                    // detail chip shows real "<n> cards due" figures, not a placeholder.
                    withCol {
                        val scores = CfaScoresProvider.scores(this)
                        val counts = CfaConceptMap.conceptCounts(this)
                        CfaConceptMap.buildPayload(scores, counts)
                    }
                } catch (e: Exception) {
                    // Honest empty state: an all-gray (abstaining) map still renders.
                    Timber.w(e, "CFA concept-map score load failed; rendering abstain state")
                    JSONObject()
                        .put("slugs", JSONObject())
                        .put("counts", JSONObject())
                        .put("overall", JSONObject.NULL)
                        .put("source", "unavailable")
                        .toString()
                }
            // The asset reads window.CFA_MAP_DATA synchronously when its <script>
            // runs, so inject the data as a page-level global BEFORE that script by
            // prepending a tiny setter to the first <script> tag. loadDataWithBaseURL
            // keeps the file:// base so any relative asset refs still resolve.
            val html = assets.open("cfa/concept_map.html").bufferedReader().use { it.readText() }
            val injected = html.replaceFirst("<script>", "<script>window.CFA_MAP_DATA=$payload;</script>\n<script>")
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
     * Answer one batched-explanation request from the map JS. Reads the proxy
     * config + synced master AI toggle off the collection thread (fast), then does
     * the network POST off the collection thread so a slow/absent proxy never
     * blocks the collection. Marshals the result back into the WebView via
     * `window.cfaApplyMapExplain(...)`. Never throws — on any failure the map keeps
     * its deterministic templated wording and shows honest "AI failed" provenance.
     */
    private fun requestExplain(nodesJson: String) {
        launchCatchingTask {
            val result: CfaMapExplainResult =
                try {
                    val cfg =
                        withCol {
                            Triple(
                                CfaAiClient.mapExplainEnabled(this),
                                CfaAiClient.proxyUrl(this),
                                CfaAiClient.proxyToken(this),
                            )
                        }
                    if (!cfg.first) {
                        CfaAiClient.aiOffMapExplain()
                    } else {
                        withContext(Dispatchers.IO) {
                            CfaAiClient.explainMap(cfg.second, cfg.third, nodesJson, CfaAiClient.DEFAULT_POST)
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "CFA concept-map explain failed")
                    CfaMapExplainResult(false, aiOn = true, emptyMap(), "fallback", null, "client_error")
                }
            val json =
                JSONObject()
                    .put("ok", result.ok)
                    .put("aiOn", result.aiOn)
                    .put("error", result.error ?: JSONObject.NULL)
                    .put("explanations", JSONObject(result.explanations as Map<*, *>))
                    .toString()
            webView.evaluateJavascript("window.cfaApplyMapExplain($json)", null)
        }
    }

    /**
     * The @JavascriptInterface the map asset calls once, on load, with its node
     * list. Marshals onto the UI thread (it runs on a private WebView thread) and
     * kicks off the single batched call.
     */
    class CfaMapBridge(
        private val activity: CfaConceptMapActivity,
    ) {
        @JavascriptInterface
        fun explainMap(nodesJson: String) {
            activity.runOnUiThread { activity.requestExplain(nodesJson) }
        }
    }

    companion object {
        fun getIntent(from: android.content.Context): Intent = Intent(from, CfaConceptMapActivity::class.java)
    }
}
