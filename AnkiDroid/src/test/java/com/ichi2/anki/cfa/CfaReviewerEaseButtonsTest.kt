// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Phase B mobile Reviewer answer-bar branding guard.
//
// The four ease-answer buttons (Again / Hard / Good / Easy) are the
// highest-time-on-screen surface in a spaced-repetition app, yet they shipped in
// raw stock Material colours — again=material_red, hard=material_blue_grey,
// good=material_green, and easy=material_light_blue_500 (the exact stock
// light-blue the objective flags as "ugly, copy the desktop coloring scheme").
// The desktop reviewer answer bar (qt/aqt/cfa_chrome.py, iter 21) already reads
// as CFA; this closes the mobile parity gap by retoning the four fills to one
// on-brand four-tier scheme (fail-red / neutral-slate / pass-green / navy) shared
// by the light AND dark reviewer drawables.
//
// This guard parses each footer-button drawable as text and asserts (a) it now
// references its CFA ease token, and (b) the specific stock Material colour it
// used to carry is gone — so a regression re-introducing a stock traffic-light
// (or the light-blue Easy leak) fails the build. It also pins the token values
// in cfa.xml so the AA-safe fills cannot silently drift.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CfaReviewerEaseButtonsTest {
    private fun resFile(relative: String): String {
        val file =
            listOf(
                File("src/main/res/$relative"),
                File("AnkiDroid/src/main/res/$relative"),
            ).firstOrNull { it.exists() }
        assertThat("$relative must exist for this guard", file != null, equalTo(true))
        return file!!.readText()
    }

    private data class Tier(
        val drawable: String,
        val token: String,
        val stockColor: String,
    )

    // The four ease drawables in BOTH themes point at the SAME shared CFA tokens.
    private val tiers =
        listOf(
            Tier("footer_button_again", "cfa_ease_again", "material_red"),
            Tier("footer_button_hard", "cfa_ease_hard", "material_blue_grey"),
            Tier("footer_button_good", "cfa_ease_good", "material_green"),
            Tier("footer_button_easy", "cfa_ease_easy", "material_light_blue"),
        )

    @Test
    fun light_ease_buttons_use_cfa_tokens_not_stock_material() {
        for (t in tiers) {
            val xml = resFile("drawable/${t.drawable}.xml")
            assertThat(
                "${t.drawable} must fill with @color/${t.token}",
                xml,
                containsString("@color/${t.token}"),
            )
            assertThat(
                "${t.drawable} must not keep the stock ${t.stockColor} fill",
                xml,
                not(containsString("@color/${t.stockColor}")),
            )
        }
    }

    @Test
    fun dark_ease_buttons_use_cfa_tokens_not_stock_material() {
        for (t in tiers) {
            val xml = resFile("drawable/${t.drawable}_dark.xml")
            assertThat(
                "${t.drawable}_dark must fill with @color/${t.token}",
                xml,
                containsString("@color/${t.token}"),
            )
            assertThat(
                "${t.drawable}_dark must not keep the stock ${t.stockColor} fill",
                xml,
                not(containsString("@color/${t.stockColor}")),
            )
        }
    }

    @Test
    fun ease_tokens_are_defined_at_their_aa_safe_values() {
        val cfa = resFile("values/cfa.xml")
        // Base fills — each pinned to its exact AA-safe value (all clear >=4.5:1
        // against the white answerButtonTextColor).
        val expected =
            mapOf(
                "cfa_ease_again" to "#B91C1C",
                "cfa_ease_hard" to "#4D5C6D",
                "cfa_ease_good" to "#0F6E33",
                "cfa_ease_easy" to "#122B46",
            )
        for ((name, value) in expected) {
            val match = Regex("""<color name="$name">([^<]*)</color>""").find(cfa)
            assertThat("$name must be defined", match != null, equalTo(true))
            assertThat("$name value", match!!.groupValues[1].trim(), equalTo(value))
        }
        // The hover shades that back the pressed/focused states must exist.
        for (name in listOf("cfa_ease_again_hover", "cfa_ease_hard_hover", "cfa_ease_good_hover", "cfa_ease_easy_hover")) {
            assertThat("$name must be defined", cfa, containsString("""<color name="$name">"""))
        }
    }
}
