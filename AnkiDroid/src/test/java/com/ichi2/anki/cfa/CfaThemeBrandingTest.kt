// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Phase B theme-branding guard.
//
// The desktop objective demands "no visibly un-themed stock-Anki screens" and
// calls out the Android colouring as ugly ("copy the coloring scheme on
// desktop"). A prior shell refactor branded the LIGHT theme's primary chrome
// (colorPrimary = cfa_navy, FAB = cfa_accent), but a set of SECONDARY roles were
// still shipping stock-AnkiDroid light-blue and leaked through the navy shell:
//   * the Material3 tonal containers (colorSecondaryContainer / the surface
//     containers) — selected chips, sheets and elevated surfaces,
//   * the Settings category headers (preferenceCategoryTitleTextColor),
//   * the editor text-selection highlight, the in-app-browser nav bar, the
//     snackbar/dialog action-button text and the stepper button background,
//   * and the DARK theme's FAB, still light-blue while the light-mode FAB is the
//     warm CFA accent.
//
// This guard parses the theme XMLs as text and asserts each retoned attribute
// now references a CFA token (and NOT the specific stock light-blue it used to),
// so a regression that re-introduces a stock-blue leak fails the build. The
// semantic learned colours (ease buttons, flags) are intentionally NOT asserted
// here — they keep their learned Anki affordance.

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
class CfaThemeBrandingTest {
    private fun themeXml(name: String): String {
        val candidates =
            listOf(
                File("src/main/res/values/$name"),
                File("AnkiDroid/src/main/res/values/$name"),
            )
        val file = candidates.firstOrNull { it.exists() }
        assertThat("$name must exist for this guard", file != null, equalTo(true))
        return file!!.readText()
    }

    /** Read the value of a single `<item name="attr">value</item>` line. */
    private fun attrValue(
        xml: String,
        attr: String,
    ): String {
        val match = Regex("""<item name="$attr">([^<]*)</item>""").find(xml)
        assertThat("attr $attr must be present", match != null, equalTo(true))
        return match!!.groupValues[1].trim()
    }

    @Test
    fun light_theme_secondary_roles_are_cfa_branded_not_stock_blue() {
        val xml = themeXml("theme_light.xml")

        // Legacy AppCompat accent (cursor / selection / checkbox tints).
        assertThat(attrValue(xml, "colorAccent"), equalTo("@color/cfa_navy"))

        // Material3 tonal containers — selected states + elevated surfaces.
        assertThat(attrValue(xml, "colorSecondaryContainer"), equalTo("@color/cfa_accent_soft"))
        assertThat(attrValue(xml, "colorSurfaceContainerHigh"), equalTo("@color/cfa_surface"))
        assertThat(attrValue(xml, "colorSurfaceContainer"), equalTo("@color/cfa_surface"))

        // Settings category headers → AA-safe warm accent-ink (a CFA eyebrow).
        assertThat(attrValue(xml, "preferenceCategoryTitleTextColor"), equalTo("@color/cfa_accent_ink"))

        // Editor selection highlight, in-app browser nav bar.
        assertThat(attrValue(xml, "editTextHighlightColor"), equalTo("@color/cfa_accent_soft"))
        assertThat(attrValue(xml, "customTabNavBarColor"), equalTo("@color/cfa_navy"))

        // Action-button text on snackbars / dialogs, stepper button background.
        assertThat(attrValue(xml, "snackbarButtonTextColor"), equalTo("@color/cfa_accent_on_navy"))
        assertThat(attrValue(xml, "progressDialogButtonTextColor"), equalTo("@color/cfa_navy"))
        assertThat(attrValue(xml, "incrementerButtonBackground"), equalTo("@color/cfa_navy"))

        // The specific stock light-blue references these replaced must be gone
        // (material_light_blue_500 is intentionally kept for the "Easy" ease
        // button, a learned semantic colour, so it is not asserted absent here).
        assertThat(xml, not(containsString("material_light_blue_800")))
        assertThat(xml, not(containsString("material_light_blue_300")))
        assertThat(xml, not(containsString("material_light_blue_200")))
        assertThat(xml, not(containsString("#0F03A9F4")))
    }

    @Test
    fun dark_theme_fab_matches_the_warm_cfa_accent() {
        val xml = themeXml("theme_dark.xml")
        assertThat(attrValue(xml, "fab_normal"), equalTo("@color/cfa_accent"))
        assertThat(attrValue(xml, "fab_pressed"), equalTo("@color/cfa_accent_hover"))
        // The stock light-blue FAB the dark theme used to ship must be gone.
        assertThat(xml, not(containsString("material_light_blue_700")))
    }

    // Dark mode uses the brand's warm accent-on-navy as the interactive tint
    // (navy is too dark to read on a #303030 / black surface); it clears WCAG AA
    // there (5.25:1 on #303030, 8.4:1 on black). The DARK-surface roles use the
    // bright accent-on-navy; the LIGHT-surface snackbar uses the brand navy.
    @Test
    fun dark_theme_interactive_tints_are_cfa_branded_not_stock_blue() {
        val xml = themeXml("theme_dark.xml")

        // Primary/accent tints (switches, checkboxes, activated widgets, links).
        assertThat(attrValue(xml, "colorPrimary"), equalTo("@color/cfa_accent_on_navy"))
        assertThat(attrValue(xml, "colorAccent"), equalTo("@color/cfa_accent_on_navy"))

        // Tonal + elevated surfaces (selected chips, sheets) — navy container and a
        // 6% navy elevated tint replacing the stock blue-grey / 6% blue.
        assertThat(attrValue(xml, "colorSecondaryContainer"), equalTo("@color/cfa_navy"))
        assertThat(attrValue(xml, "colorSurfaceContainer"), equalTo("#0F122B46"))

        // Tab / deck-list / count accents on the grey surface.
        assertThat(attrValue(xml, "tabActiveIconColor"), equalTo("@color/cfa_accent_on_navy"))
        assertThat(attrValue(xml, "dynDeckColor"), equalTo("@color/cfa_accent_on_navy"))
        assertThat(attrValue(xml, "newCountColor"), equalTo("@color/cfa_accent_on_navy"))

        // Editor selection (navy on dark), and the action-button text keyed to its
        // own surface: navy on the LIGHT snackbar, warm accent on the DARK dialog.
        assertThat(attrValue(xml, "editTextHighlightColor"), equalTo("@color/cfa_navy"))
        assertThat(attrValue(xml, "snackbarButtonTextColor"), equalTo("@color/cfa_navy"))
        assertThat(attrValue(xml, "progressDialogButtonTextColor"), equalTo("@color/cfa_accent_on_navy"))

        // The specific stock blues these replaced must be gone. material_blue_400
        // (colorPrimary/accent) and the light-blue tints are asserted absent; the
        // semantic ease-button blues are NOT referenced by this theme, so a blanket
        // check is safe here for the ones we retoned.
        assertThat(xml, not(containsString("material_blue_400")))
        assertThat(xml, not(containsString("material_indigo_200")))
        assertThat(xml, not(containsString("material_light_blue_500")))
    }

    @Test
    fun black_theme_interactive_tints_are_cfa_branded_not_stock_blue() {
        val xml = themeXml("theme_black.xml")

        assertThat(attrValue(xml, "colorPrimary"), equalTo("@color/cfa_accent_on_navy"))
        assertThat(attrValue(xml, "colorAccent"), equalTo("@color/cfa_accent_on_navy"))
        assertThat(attrValue(xml, "tabActiveIconColor"), equalTo("@color/cfa_accent_on_navy"))
        assertThat(attrValue(xml, "dynDeckColor"), equalTo("@color/cfa_accent_on_navy"))
        assertThat(attrValue(xml, "newCountColor"), equalTo("@color/cfa_accent_on_navy"))
        assertThat(attrValue(xml, "editTextHighlightColor"), equalTo("@color/cfa_navy"))
        // Both the black-theme snackbar and dialog are dark, so both take the light
        // warm accent.
        assertThat(attrValue(xml, "snackbarButtonTextColor"), equalTo("@color/cfa_accent_on_navy"))
        assertThat(attrValue(xml, "progressDialogButtonTextColor"), equalTo("@color/cfa_accent_on_navy"))

        assertThat(xml, not(containsString("material_blue_400")))
        assertThat(xml, not(containsString("material_blue_500")))
        assertThat(xml, not(containsString("material_blue_600")))
    }
}
