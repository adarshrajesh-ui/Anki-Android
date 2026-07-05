// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Phase B Pass-3 (ruthless) scientific WCAG AA contrast guard.
//
// Mirrors the desktop Pass-3 contrast audit (ts/lib/cfa/contrast.test.ts): the
// ruthless pass opens with a *measured* WCAG 2.1 audit of the CFA colour tokens
// (ratios computed from the real compiled resource values, not a by-eye
// critique) rather than a subjective critique. It found a genuine accessibility
// defect: the warm brand accent (cfa_accent #DA5C01) was colouring small
// readable TEXT — the Readiness/Config eyebrows (11sp), the exam countdown
// (13sp) and the nav-drawer tagline (12sp) — yet only reaches ~3.8:1, which
// FAILS WCAG AA (>=4.5:1 for normal text; it does not even clear the 3:1
// large-text floor comfortably). accent is safe only for large fills / the FAB
// tint / ripples.
//
// The fix adds two AA-safe accent-TEXT tokens (cfa_accent_ink for light
// backgrounds, cfa_accent_on_navy for the navy drawer) and repoints the three
// text usages. This test computes the contrast of every CFA text token on every
// background it renders on and asserts AA, documents WHY the raw accent fails,
// and guards against a regression (accent creeping back onto readable text).

package com.ichi2.anki.cfa

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThan
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.pow

@RunWith(AndroidJUnit4::class)
class CfaContrastTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    // WCAG 2.1 acceptance bars.
    private val aaNormal = 4.5
    private val aaLarge = 3.0

    private fun color(id: Int): Int = ContextCompat.getColor(context, id)

    private fun channel(c8: Int): Double {
        val s = c8 / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG relative luminance of an ARGB colour int (alpha ignored). */
    private fun luminance(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    /** WCAG contrast ratio between two ARGB colours (order-independent). */
    private fun contrast(
        fg: Int,
        bg: Int,
    ): Double {
        val a = luminance(fg)
        val b = luminance(bg)
        val hi = maxOf(a, b)
        val lo = minOf(a, b)
        return (hi + 0.05) / (lo + 0.05)
    }

    // The two backgrounds the CFA text actually renders on.
    private val white by lazy { color(android.R.color.white) }
    private val navy by lazy { color(R.color.cfa_navy) }
    private val surface by lazy { color(R.color.cfa_surface) }

    // ---- The scientific audit: every text token on every background it uses ----

    @Test
    fun `secondary text token cfa_muted clears AA on white and surface`() {
        val muted = color(R.color.cfa_muted)
        assertThat(contrast(muted, white), greaterThanOrEqualTo(aaNormal))
        assertThat(contrast(muted, surface), greaterThanOrEqualTo(aaNormal))
    }

    @Test
    fun `primary text token cfa_ink clears AA on white and surface`() {
        val ink = color(R.color.cfa_ink)
        assertThat(contrast(ink, white), greaterThanOrEqualTo(aaNormal))
        assertThat(contrast(ink, surface), greaterThanOrEqualTo(aaNormal))
    }

    @Test
    fun `covered-topic text token cfa_navy clears AA on the surface card`() {
        // Per-topic recall + the exam-date value render cfa_navy on cfa_surface.
        assertThat(contrast(color(R.color.cfa_navy), surface), greaterThanOrEqualTo(aaNormal))
    }

    @Test
    fun `white toolbar and wordmark text clears AA on navy`() {
        assertThat(contrast(white, navy), greaterThanOrEqualTo(aaNormal))
    }

    // ---- The finding: the raw warm accent FAILS AA as small text ----

    @Test
    fun `cfa_accent FAILS AA as small text on white — documents why accent_ink exists`() {
        // ~3.8:1 — below the 4.5:1 normal-text bar. This is the defect the Pass-3
        // audit caught; the assertion pins the finding so the token can never be
        // "fixed" by quietly bumping accent brighter (which would break the FAB).
        assertThat(contrast(color(R.color.cfa_accent), white), lessThan(aaNormal))
    }

    @Test
    fun `cfa_accent FAILS AA as small text on navy — documents why accent_on_navy exists`() {
        assertThat(contrast(color(R.color.cfa_accent), navy), lessThan(aaNormal))
    }

    // ---- The fix: the two AA-safe accent-text tokens clear AA on their bg ----

    @Test
    fun `cfa_accent_ink clears AA as text on white and surface`() {
        val ink = color(R.color.cfa_accent_ink)
        assertThat(contrast(ink, white), greaterThanOrEqualTo(aaNormal))
        assertThat(contrast(ink, surface), greaterThanOrEqualTo(aaNormal))
    }

    @Test
    fun `cfa_accent_on_navy clears AA as text on the navy drawer header`() {
        assertThat(contrast(color(R.color.cfa_accent_on_navy), navy), greaterThanOrEqualTo(aaNormal))
    }

    @Test
    fun `accent-text tokens keep the warm-orange identity (redder than muted grey)`() {
        // Guard that the AA fix stayed warm rather than collapsing to a grey: the
        // red channel must dominate the blue channel on both new tokens.
        for (id in intArrayOf(R.color.cfa_accent_ink, R.color.cfa_accent_on_navy)) {
            val c = color(id)
            val r = (c shr 16) and 0xFF
            val b = c and 0xFF
            assertThat("token ${Integer.toHexString(c)} must stay warm (R>B)", (r - b).toDouble(), greaterThanOrEqualTo(60.0))
        }
    }

    // ---- Regression guard: the three repointed text views use AA-safe tokens ----

    @Test
    fun `config eyebrows, countdown and tagline use AA-safe accent-text tokens`() {
        val res = findResDir()

        fun read(rel: String) = java.io.File(res, rel).readText()

        val config = read("layout/activity_cfa_exam_config.xml")
        val drawer = read("layout/view_navdrawer_header.xml")

        // The eyebrow / countdown TextViews on the LIGHT config screen must use the
        // dark AA-safe accent-ink; neither native screen may bind the raw accent.
        assertThat("config uses accent_ink (eyebrow + countdown)", config.split("@color/cfa_accent_ink").size - 1, greaterThanOrEqualTo(2))
        assertThat("drawer tagline must use accent_on_navy", drawer.contains("@color/cfa_accent_on_navy"), equalToTrue())

        // No CFA text view may bind the raw (AA-failing) accent as its textColor.
        for ((name, xml) in listOf("config" to config, "drawer" to drawer)) {
            val bad = Regex("android:textColor=\"@color/cfa_accent\"").containsMatchIn(xml)
            assertThat("$name must not use raw cfa_accent as textColor", bad, equalToFalse())
        }
    }

    @Test
    fun `readiness webview asset never uses the raw AA-failing accent as text colour`() {
        // The Exam Readiness eyebrow moved from a native TextView into the
        // assets/cfa/readiness.html WebView (matching the desktop page). Its
        // eyebrow uses the AA-safe green (#007E56); the raw warm accent (#DA5C01)
        // is used ONLY for borders/spines there, never as a text `color:`.
        val html =
            listOf(
                java.io.File("src/main/assets/cfa/readiness.html"),
                java.io.File("AnkiDroid/src/main/assets/cfa/readiness.html"),
            ).firstOrNull { it.exists() }
        assertThat("readiness.html asset must exist", html != null, equalToTrue())
        val css = html!!.readText()
        // The eyebrow reads in the AA-safe green, not the raw accent.
        assertThat(css, containsString("color:var(--green)"))
        // The raw warm accent may back a border/spine (`border-color:var(--accent)`)
        // but must never be a TEXT `color:` — match the property only when it is
        // NOT the tail of `border-color` etc. (i.e. preceded by `;`, `{` or space).
        val rawAccentText = Regex("[;{ ]color:var\\(--accent\\)").containsMatchIn(css)
        assertThat("raw --accent must not be a text colour", rawAccentText, equalToFalse())
    }

    private fun equalToTrue() = org.hamcrest.Matchers.equalTo(true)

    private fun equalToFalse() = org.hamcrest.Matchers.equalTo(false)

    /** Locate AnkiDroid/src/main/res regardless of the test working directory. */
    private fun findResDir(): java.io.File {
        val candidates =
            listOf(
                "src/main/res",
                "AnkiDroid/src/main/res",
                "../AnkiDroid/src/main/res",
            )
        var dir = java.io.File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            for (c in candidates) {
                val f = java.io.File(dir, c)
                if (java.io.File(f, "values/cfa.xml").exists()) return f
            }
            dir = dir.parentFile ?: return@repeat
        }
        error("could not locate AnkiDroid/src/main/res from ${System.getProperty("user.dir")}")
    }
}
