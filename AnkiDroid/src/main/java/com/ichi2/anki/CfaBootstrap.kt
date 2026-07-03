// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Feature F7: bundle + auto-import the CFA study decks on first launch.

package com.ichi2.anki

import android.net.Uri
import androidx.core.content.edit
import anki.import_export.importAnkiPackageOptions
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber
import java.io.File

/** Pref flag: the bundled CFA decks have been imported (or intentionally skipped). */
const val CFA_BOOTSTRAP_PREF = "cfa_bootstrap_imported"

/** The bundled study package shipped as an app asset (CFA Level II + Ethics Passages). */
private const val CFA_BOOTSTRAP_ASSET = "cfa/cfa-bootstrap.apkg"

/**
 * On the very first launch of a fresh profile, seed the collection with the two
 * bundled CFA study decks — ``CFA Level II`` and the one-passage ``CFA::Ethics
 * Passages`` deck — shipped as an app asset. This is the mobile mirror of the
 * desktop first-run seeder.
 *
 * Guarantees (matching the fork's "additive only / never clobber" rule):
 *  - runs at most once, guarded by [CFA_BOOTSTRAP_PREF];
 *  - only seeds an EMPTY collection, so it never overwrites a user's own data
 *    (e.g. a collection restored from sync); on a non-empty collection it simply
 *    records the flag and returns;
 *  - imports additively through the shared fork Rust engine
 *    ([anki.import_export] ``importAnkiPackage``), the same code path the desktop
 *    fork and the AnkiDroid import UI use.
 *
 * Any failure is swallowed (logged) so a bad bundle can never block startup.
 */
suspend fun DeckPicker.maybeImportCfaBootstrapDeck() {
    val prefs = sharedPrefs()
    if (prefs.getBoolean(CFA_BOOTSTRAP_PREF, false)) {
        return
    }

    // Never overwrite an existing collection — only bootstrap a fresh, empty one.
    val collectionIsEmpty = withCol { isEmpty }
    if (!collectionIsEmpty) {
        Timber.i("CFA bootstrap: collection not empty; marking done without import")
        prefs.edit { putBoolean(CFA_BOOTSTRAP_PREF, true) }
        return
    }

    try {
        // Copy the bundled .apkg out of read-only assets to a real file path.
        val target = File(cacheDir, "cfa-bootstrap.apkg")
        assets.open(CFA_BOOTSTRAP_ASSET).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }

        val path = Uri.encode(target.absolutePath, "/")
        // Additive import of the shipped decks + their deck configs; no scheduling
        // (a fresh profile starts every card as new).
        val options =
            importAnkiPackageOptions {
                withDeckConfigs = true
            }
        val output = withCol { importAnkiPackage(path, options) }
        undoableOp { output.changes }

        prefs.edit { putBoolean(CFA_BOOTSTRAP_PREF, true) }
        target.delete()
        Timber.i("CFA bootstrap: imported bundled CFA decks from asset")

        showSnackbar("CFA decks ready — CFA Level II + Ethics Passages imported")
        updateDeckList()
    } catch (e: Exception) {
        // Never let a bad bundle block startup; the user can still import manually.
        Timber.w(e, "CFA bootstrap import failed")
    }
}
