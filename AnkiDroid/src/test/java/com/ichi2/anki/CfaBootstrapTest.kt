// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.import_export.importAnkiPackageOptions
import com.ichi2.anki.libanki.DeckId
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Increment 5 — the bundled bootstrap ships the desktop-identical minimal-pairs
 * ethics deck, not the retired one-passage deck.
 */
@RunWith(AndroidJUnit4::class)
class CfaBootstrapTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun bundledApkgImportsEthicsPairsDeck() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val apkg = File.createTempFile("cfa-bootstrap", ".apkg")
        context.assets.open("cfa/cfa-bootstrap.apkg").use { input ->
            apkg.outputStream().use { output -> input.copyTo(output) }
        }
        try {
            val options =
                importAnkiPackageOptions {
                    withDeckConfigs = true
                }
            col.importAnkiPackage(apkg.absolutePath, options)
            val pairsId: DeckId? = col.decks.idForName(CFA_ETHICS_PAIRS_DECK)
            assertThat(pairsId, notNullValue())
            assertThat(col.decks.idForName("CFA::Ethics Passages"), nullValue())
        } finally {
            apkg.delete()
        }
    }
}
