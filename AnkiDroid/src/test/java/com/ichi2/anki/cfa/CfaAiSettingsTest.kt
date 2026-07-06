// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — mobile AI Settings tests. Pure (no Collection / network): they
// exercise the toggle payload builder (AI-first default-ON semantics mirroring
// the desktop `get_ai_toggles`), the write-key allow-list (the bridge may only
// touch the three shared toggle keys), and source guards keeping the WebView +
// bridge + Home chip wired and the Activity registered.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CfaAiSettingsTest {
    // --- Payload builder (AI-first defaults, mirroring the desktop) -----------

    @Test
    fun unset_toggles_default_on() {
        val o = JSONObject(CfaAiSettings.buildPayload(null, null, null))
        assertThat(o.getBoolean("master"), equalTo(true))
        assertThat(o.getBoolean("grading"), equalTo(true))
        assertThat(o.getBoolean("tabfill"), equalTo(true))
    }

    @Test
    fun explicit_values_are_preserved() {
        val o = JSONObject(CfaAiSettings.buildPayload(master = false, grading = true, tabfill = false))
        assertThat(o.getBoolean("master"), equalTo(false))
        assertThat(o.getBoolean("grading"), equalTo(true))
        assertThat(o.getBoolean("tabfill"), equalTo(false))
    }

    // --- Write allow-list + shared-key contract -------------------------------

    @Test
    fun toggle_keys_are_the_three_shared_desktop_keys() {
        // Keying the phone toggles to the SAME col.conf keys the desktop writes is
        // what makes them SYNC — assert they match CfaAiClient's constants exactly.
        assertThat(CfaAiSettings.MASTER_KEY, equalTo(CfaAiClient.MASTER_KEY))
        assertThat(CfaAiSettings.GRADING_KEY, equalTo(CfaAiClient.GRADING_KEY))
        assertThat(CfaAiSettings.TABFILL_KEY, equalTo(CfaAiClient.TABFILL_KEY))
        assertThat(
            CfaAiSettings.TOGGLE_KEYS,
            equalTo(setOf(CfaAiClient.MASTER_KEY, CfaAiClient.GRADING_KEY, CfaAiClient.TABFILL_KEY)),
        )
        // The bridge only writes keys in the allow-list; an unrelated config key
        // is not in it (setToggle refuses it).
        assertThat(CfaAiSettings.TOGGLE_KEYS.contains("sortBackwards"), equalTo(false))
    }

    // --- Asset / wiring source guards -----------------------------------------

    private fun repoFile(vararg candidates: String): String {
        val f = candidates.map { File(it) }.firstOrNull { it.exists() }
        assertThat("one of $candidates must exist for this guard", f != null, equalTo(true))
        return f!!.readText()
    }

    @Test
    fun ai_settings_asset_wires_the_bridge_and_all_three_toggles() {
        val html =
            repoFile(
                "src/main/assets/cfa/ai_settings.html",
                "AnkiDroid/src/main/assets/cfa/ai_settings.html",
            )
        // Reads the injected synced toggles + persists through the native bridge.
        assertThat(html, containsString("window.CFA_AI_SETTINGS"))
        assertThat(html, containsString("AndroidCfaAiSettings"))
        assertThat(html, containsString("setToggle"))
        // All three toggle keys are present.
        assertThat(html, containsString("cfa_ai_enabled"))
        assertThat(html, containsString("cfa_ai_grading_enabled"))
        assertThat(html, containsString("cfa_ai_tabfill_enabled"))
        // Honest copy: the phone never holds the key + AI-off degrades deterministically.
        assertThat(html, containsString("never holds the OpenAI key"))
        assertThat(html, containsString("deterministic fallback"))
    }

    @Test
    fun home_footer_chip_toggles_ai_directly() {
        val html =
            repoFile(
                "src/main/assets/cfa/home.html",
                "AnkiDroid/src/main/assets/cfa/home.html",
            )
        // The AI-state chip reads and writes the synced master state directly.
        assertThat(html, containsString("ai-chip"))
        assertThat(html, containsString("window.CFA_AI_ENABLED"))
        assertThat(html, containsString("setAiMaster(aiOn)"))
        assertThat(html, containsString("No AI"))
    }

    @Test
    fun home_sync_card_opens_connect_and_sync() {
        val html =
            repoFile(
                "src/main/assets/cfa/home.html",
                "AnkiDroid/src/main/assets/cfa/home.html",
            )
        assertThat(html, containsString("sync-card"))
        assertThat(html, containsString("Settings &amp; sync"))
        assertThat(html, containsString("Connect & Sync"))
        assertThat(html, containsString("window.AndroidCfaHome.open(\"sync\")"))
    }

    @Test
    fun home_activity_routes_and_injects_ai_settings() {
        val activity =
            repoFile(
                "src/main/java/com/ichi2/anki/CfaHomeActivity.kt",
                "AnkiDroid/src/main/java/com/ichi2/anki/CfaHomeActivity.kt",
            )
        // The bridge launches AI Settings and the master state is injected as a window global.
        assertThat(activity, containsString("setAiMaster(on: Boolean)"))
        assertThat(activity, containsString("CfaAiSettings.setMasterFromHome"))
        assertThat(activity, containsString("window.CFA_AI_ENABLED"))
        assertThat(activity, containsString("buildSyncPayload"))
        assertThat(activity, containsString("DeckPicker.getIntent(this, autoSync = true)"))
        assertThat(activity, containsString("AccountActivity.getIntent(this)"))
    }

    @Test
    fun nav_drawer_relabels_logout_to_connect_and_sync() {
        val cfaStrings =
            repoFile(
                "src/main/res/values/cfa.xml",
                "AnkiDroid/src/main/res/values/cfa.xml",
            )
        val drawer =
            repoFile(
                "src/main/java/com/ichi2/anki/NavigationDrawerActivity.kt",
                "AnkiDroid/src/main/java/com/ichi2/anki/NavigationDrawerActivity.kt",
            )
        assertThat(cfaStrings, containsString("Connect &amp; Sync"))
        assertThat(cfaStrings.contains(">Log out<"), equalTo(false))
        assertThat(drawer, containsString("DeckPicker.getIntent(this@NavigationDrawerActivity, autoSync = true)"))
        assertThat(drawer, containsString("AccountActivity.getIntent(this@NavigationDrawerActivity)"))
    }

    @Test
    fun ai_settings_activity_is_registered() {
        val manifest =
            repoFile(
                "src/main/AndroidManifest.xml",
                "AnkiDroid/src/main/AndroidManifest.xml",
            )
        assertThat(manifest, containsString("com.ichi2.anki.CfaAiSettingsActivity"))
    }
}
