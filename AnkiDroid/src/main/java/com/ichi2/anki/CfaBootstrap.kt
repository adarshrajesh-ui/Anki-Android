// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Feature F7: bundle + auto-import the CFA study decks on first launch.

package com.ichi2.anki

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

/** The bundled study package shipped as an app asset (CFA Level II + Ethics Pairs). */
private const val CFA_BOOTSTRAP_ASSET = "cfa/cfa-bootstrap.apkg"

/** Deck name for the bundled two-vignette minimal-pairs ethics flagship. */
const val CFA_ETHICS_PAIRS_DECK = "CFA::Ethics Pairs"

// --- CFA Home landing --------------------------------------------------------
//
// The desktop app OPENS INTO the native CFA Home dashboard instead of the raw
// deck list; the phone mirrors that by handing off from the launcher DeckPicker
// to [CfaHomeActivity] exactly once per process, and ONLY on a genuine cold
// launch (an ACTION_MAIN + CATEGORY_LAUNCHER intent). In-app navigation to the
// deck list (Home's "Browse decks" CTA, the nav-drawer "Decks" entry) uses a
// plain component Intent with no action/category, so it never triggers the
// hand-off — which is what stops a Home <-> DeckPicker bounce loop.

/** Set once per process the first time the launcher hands off to CFA Home. */
@Volatile
var cfaHomeOpenedThisProcess = false

/**
 * Pure decision for whether a DeckPicker start should hand off to the CFA Home
 * landing screen. Kept free of Android framework calls so the guard (which is
 * the whole point — it must NOT fire on in-app navigation or activity
 * recreation) is unit-testable without a device.
 *
 * Fires only on a fresh cold launch from the launcher: not on a config-change
 * recreation ([isFreshStart] = savedInstanceState == null), not on in-app
 * navigation (only ACTION_MAIN + CATEGORY_LAUNCHER), and at most once per
 * process ([alreadyOpenedThisProcess]).
 */
fun shouldOpenCfaHomeOnLaunch(
    intentAction: String?,
    isLauncherCategory: Boolean,
    isFreshStart: Boolean,
    alreadyOpenedThisProcess: Boolean,
): Boolean =
    !alreadyOpenedThisProcess &&
        isFreshStart &&
        intentAction == Intent.ACTION_MAIN &&
        isLauncherCategory

/**
 * On a genuine cold launch, hand off from the launcher DeckPicker to the native
 * CFA Home dashboard (the desktop-parity landing), leaving DeckPicker on the
 * back stack so Back from Home returns to the deck list. A no-op on in-app
 * navigation, recreation, or any subsequent launch this process (see
 * [shouldOpenCfaHomeOnLaunch]).
 */
fun DeckPicker.maybeOpenCfaHome(savedInstanceState: Bundle?) {
    val launcher = intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true
    if (!shouldOpenCfaHomeOnLaunch(intent?.action, launcher, savedInstanceState == null, cfaHomeOpenedThisProcess)) {
        return
    }
    cfaHomeOpenedThisProcess = true
    Timber.i("CFA: cold launch — opening CFA Home landing over the deck list")
    startActivity(CfaHomeActivity.getIntent(this))
}

/**
 * On the very first launch of a fresh profile, seed the collection with the two
 * bundled CFA study decks — ``CFA Level II`` and the minimal-pairs
 * ``CFA::Ethics Pairs`` deck — shipped as an app asset. This is the mobile
 * mirror of the desktop first-run seeder.
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

        showSnackbar("CFA decks ready — CFA Level II + Ethics Pairs imported")
        updateDeckList()
    } catch (e: Exception) {
        // Never let a bad bundle block startup; the user can still import manually.
        Timber.w(e, "CFA bootstrap import failed")
    }
}
