// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Exam Readiness screen (Increment 2 / D6).
//
// Shows the honest Memory / Performance / Readiness scores WITH RANGES and the
// give-up (abstain) rule, plus per-topic recall, obtained from
// [CfaScoresProvider]. That provider returns the shared engine's numbers when
// the `computeCfaScores` RPC is available and a deterministic, NO-NETWORK
// on-device fallback otherwise (which is what the abstain/empty state renders).
//
// This is additive: it opens from the CFA nav-drawer entry and never touches
// FSRS / scheduling / sync state. It also hosts the entry points for the
// exam-priority study action and the exam-config editor.

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.cfa.CfaScores
import com.ichi2.anki.cfa.CfaScoresProvider
import com.ichi2.anki.cfa.HonestScore
import com.ichi2.anki.cfa.TopicRecall
import com.ichi2.anki.cfa.scoreCardContentDescription
import com.ichi2.anki.cfa.topicRowContentDescription
import kotlin.math.roundToInt

class CfaExamReadinessActivity : AnkiActivity(R.layout.activity_cfa_exam_readiness) {
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

        findViewById<MaterialButton>(R.id.cfa_study_priority_button).setOnClickListener {
            startActivity(Intent(this, CfaExamPriorityActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.cfa_exam_config_button).setOnClickListener {
            startActivity(Intent(this, CfaExamConfigActivity::class.java))
        }

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

    private fun loadScores() {
        launchCatchingTask {
            val scores = withCol { CfaScoresProvider.scores(this) }
            render(scores)
        }
    }

    private fun render(scores: CfaScores) {
        val source = findViewById<TextView>(R.id.cfa_source)
        source.text =
            if (scores.source == CfaScores.SOURCE_RPC) {
                getString(R.string.cfa_readiness_source_rpc)
            } else {
                getString(R.string.cfa_readiness_source_fallback)
            }

        val container = findViewById<LinearLayout>(R.id.cfa_scores_container)
        container.removeAllViews()
        // Lead with the Bayesian pass/fail VERDICT hero (the signature Readiness
        // call, matching the desktop page) — but ABSTAIN when either Memory or
        // Performance gives up, the exact same gate the desktop uses
        // (memory.abstain || performance.abstain). Below it sit the three honest
        // StatCards (Memory / Performance / Readiness).
        container.addView(verdictHero(scores))
        container.addView(scoreCard(getString(R.string.cfa_readiness_memory), scores.memory))
        container.addView(scoreCard(getString(R.string.cfa_readiness_performance), scores.performance))
        container.addView(scoreCard(getString(R.string.cfa_readiness_readiness), scores.readiness))

        // Evidence caption under the score cards.
        val evidence =
            TextView(this).apply {
                text =
                    "${scores.gradedReviews} graded reviews · " +
                    "${scores.topicsCovered}/${scores.topicsTotal} topics covered " +
                    "(${(scores.coveragePct * 100).roundToInt()}%) · " +
                    "${scores.firstExposures} first exposures"
                setTextColor(getColor(R.color.cfa_muted))
                textSize = 12f
                setPadding(0, dp(8), 0, 0)
            }
        container.addView(evidence)

        renderTopics(scores.topics)
    }

    private fun renderTopics(topics: List<TopicRecall>) {
        val container = findViewById<LinearLayout>(R.id.cfa_topics_container)
        container.removeAllViews()
        for (topic in topics) {
            val row =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(6), 0, dp(6))
                }
            val name =
                TextView(this).apply {
                    text = topic.displayName
                    setTextColor(getColor(R.color.cfa_ink))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            val value =
                TextView(this).apply {
                    text =
                        if (topic.covered && topic.avgR != null) {
                            "${pct(topic.avgR)} recall · ${topic.gradedReviews} reviews"
                        } else {
                            "no data"
                        }
                    setTextColor(if (topic.covered) getColor(R.color.cfa_navy) else getColor(R.color.cfa_muted))
                    textSize = 14f
                    gravity = Gravity.END
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            // WCAG 1.3.1 / 4.1.2 (Phase B Pass-3 M-P3-2): announce the topic name
            // together with its recall as ONE screen-reader node, not two swipes.
            row.contentDescription = topicRowContentDescription(topic)
            ViewCompat.setScreenReaderFocusable(row, true)
            row.addView(name)
            row.addView(value)
            container.addView(row)
        }
    }

    /**
     * The verdict hero card: the Bayesian pass/fail call (never abstains) when
     * there is enough Memory + Performance evidence, or a calm "keep studying"
     * abstain state otherwise — mirroring the desktop Exam Readiness hero.
     */
    private fun verdictHero(scores: CfaScores): View {
        val card =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16))
                setBackgroundResource(R.drawable.cfa_score_card_bg)
                layoutParams =
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { bottomMargin = dp(12) }
            }

        val eyebrow =
            TextView(this).apply {
                text = getString(R.string.cfa_readiness_verdict_eyebrow).uppercase()
                setTextColor(getColor(R.color.cfa_muted))
                textSize = 12f
                letterSpacing = 0.08f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        card.addView(eyebrow)

        val bayes = scores.bayesian
        // Same abstain gate as the desktop hero (memory.abstain || performance.abstain).
        val heroAbstain = scores.memory.abstain || scores.performance.abstain

        if (heroAbstain || bayes == null) {
            val reason =
                if (scores.memory.abstain && scores.performance.abstain) {
                    getString(R.string.cfa_readiness_abstain_hint)
                } else if (scores.memory.abstain) {
                    scores.memory.reason
                } else {
                    scores.performance.reason
                }
            card.addView(
                TextView(this).apply {
                    text = getString(R.string.cfa_readiness_verdict_abstain)
                    setTextColor(getColor(R.color.cfa_warn))
                    textSize = 22f
                    setPadding(0, dp(4), 0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
            )
            card.addView(
                TextView(this).apply {
                    text = getString(R.string.cfa_readiness_verdict_abstain_note, reason, getString(R.string.cfa_readiness_not_validated))
                    setTextColor(getColor(R.color.cfa_muted))
                    textSize = 13f
                    setPadding(0, dp(6), 0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
            )
            card.contentDescription =
                getString(R.string.cfa_readiness_verdict_abstain) + ". " +
                getString(R.string.cfa_readiness_verdict_abstain_note, reason, getString(R.string.cfa_readiness_not_validated))
            ViewCompat.setScreenReaderFocusable(card, true)
            return card
        }

        // Bayesian call: headline + supporting probability + accuracy band.
        val call = bayes.call.replaceFirstChar { it.uppercase() }
        val callColor = if (bayes.passed) R.color.cfa_pass else R.color.cfa_fail
        card.addView(
            TextView(this).apply {
                text = call
                setTextColor(getColor(callColor))
                textSize = 28f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(4), 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
        )
        card.addView(
            TextView(this).apply {
                text = "p = ${"%.2f".format(bayes.callProb)}"
                setTextColor(getColor(R.color.cfa_muted))
                textSize = 14f
                setPadding(0, dp(2), 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
        )
        val recallText =
            if (bayes.recall != null) {
                " · est. recall ${pct(bayes.recall)}"
            } else {
                ""
            }
        val lead =
            "Estimated exam-weighted accuracy ${pct(bayes.accuracy)} " +
                "(95% CI ${pct(bayes.ciLow)}–${pct(bayes.ciHigh)}) vs ~${pct(bayes.mps)} MPS proxy$recallText"
        card.addView(
            TextView(this).apply {
                text = lead
                setTextColor(getColor(R.color.cfa_ink))
                textSize = 14f
                setPadding(0, dp(10), 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
        )
        val note =
            "${bayes.firstExposures} first-seen · " +
                "${bayes.topicsCovered}/${bayes.topicsTotal} topics studied · ${bayes.label}"
        card.addView(
            TextView(this).apply {
                text = note
                setTextColor(getColor(R.color.cfa_muted))
                textSize = 12f
                setPadding(0, dp(8), 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
        )
        card.contentDescription = "$call. p ${"%.2f".format(bayes.callProb)}. $lead. $note"
        ViewCompat.setScreenReaderFocusable(card, true)
        return card
    }

    /** A card showing one honest score: its range (or abstain text) + a bar. */
    private fun scoreCard(
        label: String,
        score: HonestScore,
        hero: Boolean = false,
        abstainOverride: String? = null,
    ): View {
        val card =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16))
                // All three cards share one container (surface + hairline + radius)
                // so Memory / Performance / Readiness read as consistent cards
                // (Phase B M3-3); the hero is emphasised by its larger value text.
                setBackgroundResource(R.drawable.cfa_score_card_bg)
                layoutParams =
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { bottomMargin = dp(8) }
            }

        // WCAG 1.3.1 / 4.1.2 (Phase B Pass-3 M-P3-2): the label / value / range
        // are separate TextViews, so announce the card as ONE screen-reader node
        // with a coherent contentDescription instead of three fragments.
        card.contentDescription = scoreCardContentDescription(label, score, abstainOverride)
        ViewCompat.setScreenReaderFocusable(card, true)

        card.addView(
            TextView(this).apply {
                text = label.uppercase()
                setTextColor(getColor(R.color.cfa_muted))
                textSize = 12f
                letterSpacing = 0.08f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
        )

        if (score.abstain) {
            // Abstain is a calm, quiet muted-grey line (Phase B M3-1/M3-2):
            // the *absence* of data must never shout in warn-orange nor collide
            // with the warm brand accent — mirrors the desktop StatCard fix.
            card.addView(
                TextView(this).apply {
                    text = "Awaiting reviews"
                    setTextColor(getColor(R.color.cfa_muted))
                    textSize = if (hero) 22f else 18f
                    setPadding(0, dp(2), 0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
            )
            card.addView(
                TextView(this).apply {
                    text = abstainOverride ?: score.reason
                    setTextColor(getColor(R.color.cfa_muted))
                    textSize = 13f
                    setPadding(0, dp(4), 0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
            )
        } else {
            card.addView(
                TextView(this).apply {
                    text = pct(score.point!!)
                    setTextColor(getColor(R.color.cfa_navy))
                    textSize = if (hero) 32f else 22f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(2), 0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
            )
            card.addView(
                TextView(this).apply {
                    text = "range ${pct(score.rangeLow!!)} – ${pct(score.rangeHigh!!)}"
                    setTextColor(getColor(R.color.cfa_muted))
                    textSize = 13f
                    setPadding(0, dp(2), 0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
            )
        }
        return card
    }

    private fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun LinearLayout.setPadding(value: Int) = setPadding(value, value, value, value)

    companion object {
        fun getIntent(from: android.content.Context): Intent = Intent(from, CfaExamReadinessActivity::class.java)
    }
}
